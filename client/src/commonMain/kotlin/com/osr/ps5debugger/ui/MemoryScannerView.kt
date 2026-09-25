package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.ProtocolConstants
import com.osr.ps5debugger.protocol.Ps5ScanResult
import com.osr.ps5debugger.ui.icons.PS5Icons
import com.osr.ps5debugger.ui.watchlist.parseValueBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ScanReadTimeoutMs = 5 * 60 * 1000

class CompactScanResults(initialCapacity: Int = 10000) {
    private val lock = Any()

    @Volatile
    var size: Int = 0
        private set

    private var offsets: LongArray = LongArray(initialCapacity)
    private var valueBytes: ByteArray = ByteArray(initialCapacity * 4)
    private var valueOffsets: IntArray = IntArray(0)
    private var valueLengths: IntArray = IntArray(0)
    private var totalBytesStored: Int = 0
    private var uniformLen: Int = -1

    fun clear() {
        synchronized(lock) {
            size = 0
            totalBytesStored = 0
            uniformLen = -1
        }
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > offsets.size) {
            val newCap = maxOf(offsets.size * 2, minCapacity)
            offsets = offsets.copyOf(newCap)
            if (uniformLen <= 0 && valueOffsets.isNotEmpty()) {
                valueOffsets = valueOffsets.copyOf(newCap)
                valueLengths = valueLengths.copyOf(newCap)
            }
        }
    }

    fun add(offset: Long, value: ByteArray) {
        synchronized(lock) {
            val vLen = value.size
            if (size == 0) {
                uniformLen = vLen
            } else if (uniformLen > 0 && uniformLen != vLen) {
                val prevLen = uniformLen
                uniformLen = -1
                valueOffsets = IntArray(offsets.size)
                valueLengths = IntArray(offsets.size)
                for (i in 0 until size) {
                    valueOffsets[i] = i * prevLen
                    valueLengths[i] = prevLen
                }
            }

            ensureCapacity(size + 1)
            offsets[size] = offset

            if (uniformLen > 0) {
                val destPos = size * uniformLen
                if (destPos + vLen > valueBytes.size) {
                    valueBytes = valueBytes.copyOf(maxOf(valueBytes.size * 2, destPos + vLen + 4096))
                }
                System.arraycopy(value, 0, valueBytes, destPos, vLen)
                totalBytesStored = destPos + vLen
            } else {
                valueOffsets[size] = totalBytesStored
                valueLengths[size] = vLen
                if (totalBytesStored + vLen > valueBytes.size) {
                    valueBytes = valueBytes.copyOf(maxOf(valueBytes.size * 2, totalBytesStored + vLen + 4096))
                }
                System.arraycopy(value, 0, valueBytes, totalBytesStored, vLen)
                totalBytesStored += vLen
            }
            size++
        }
    }

    fun addAll(other: CompactScanResults) {
        synchronized(other.lock) {
            val otherSize = other.size
            for (i in 0 until otherSize) {
                add(other.getOffset(i), other.getValueBytes(i))
            }
        }
    }

    fun getOffset(index: Int): Long {
        synchronized(lock) {
            if (index !in 0 until size) return 0L
            return offsets[index]
        }
    }

    fun getValueBytes(index: Int): ByteArray {
        synchronized(lock) {
            if (index !in 0 until size) return ByteArray(0)
            return if (uniformLen > 0) {
                val srcPos = index * uniformLen
                valueBytes.copyOfRange(srcPos, srcPos + uniformLen)
            } else {
                val srcPos = valueOffsets[index]
                val vLen = valueLengths[index]
                valueBytes.copyOfRange(srcPos, srcPos + vLen)
            }
        }
    }

    fun snapshot(): CompactScanResults {
        synchronized(lock) {
            val copy = CompactScanResults(size.coerceAtLeast(100))
            copy.size = size
            copy.uniformLen = uniformLen
            copy.offsets = offsets.copyOf(size)
            copy.valueBytes = valueBytes.copyOf(totalBytesStored)
            copy.totalBytesStored = totalBytesStored
            if (uniformLen <= 0 && valueOffsets.isNotEmpty()) {
                copy.valueOffsets = valueOffsets.copyOf(size)
                copy.valueLengths = valueLengths.copyOf(size)
            }
            return copy
        }
    }
}

object MemoryScannerState {
    val scanValueState = mutableStateOf("")
    val scanValueExtraState = mutableStateOf("200")
    val scanValueTypeState = mutableStateOf("Int32")
    val scanCompareTypeState = mutableStateOf("ExactValue")
    val alignmentState = mutableStateOf(4)
    val isScanningState = mutableStateOf(false)
    val progressState = mutableStateOf(0f)
    val totalMatchesCountState = mutableStateOf(0L)
    val isRescanModeState = mutableStateOf(false)
    val timeRemainingTextState = mutableStateOf("")
    val useCustomRangeState = mutableStateOf(false)
    val customRangeStartState = mutableStateOf("")
    val customRangeEndState = mutableStateOf("")

    /** Full candidate list for rescan stored in memory using primitive contiguous arrays. */
    val allCandidates = CompactScanResults()

    /** Base address used for the initial scan (needed by CMD_PROC_SCAN_COUNT). */
    val scanBaseAddressState = mutableStateOf(0L)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemoryScannerView(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    modifier: Modifier = Modifier,
    onJumpToAddress: ((Long) -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg)
    ) {
        val isMobile = maxWidth < 800.dp
        val coroutineScope = rememberCoroutineScope()
        val client = AppContainer.clientAdapter.client
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()

        var scanValue by MemoryScannerState.scanValueState
        var scanValueExtra by MemoryScannerState.scanValueExtraState
        var scanValueType by MemoryScannerState.scanValueTypeState
        var scanCompareType by MemoryScannerState.scanCompareTypeState
        var alignment by MemoryScannerState.alignmentState

        var isScanning by MemoryScannerState.isScanningState
        var progress by MemoryScannerState.progressState
        val allCandidates = MemoryScannerState.allCandidates
        var totalMatchesCount by MemoryScannerState.totalMatchesCountState

        var isRescanMode by MemoryScannerState.isRescanModeState
        var timeRemainingText by MemoryScannerState.timeRemainingTextState

        var useCustomRange by MemoryScannerState.useCustomRangeState
        var customRangeStart by MemoryScannerState.customRangeStartState
        var customRangeEnd by MemoryScannerState.customRangeEndState

        val valueTypes = listOf(
            "Unknown",
            "Byte (UInt8)", "SByte (Int8)",
            "UInt16", "Int16",
            "UInt32", "Int32",
            "UInt64", "Int64",
            "Float", "Double",
            "ASCII String",
            "Hex Mask (e.g. 00 11 ?? 33)"
        )
        val compareTypes = listOf(
            "ExactValue", "Fuzzy", "BiggerThan", "SmallerThan", "Between",
            "BiggerThanLast", "IncreasedBy", "SmallerThanLast", "DecreasedBy",
            "ValueChanged", "ValueUnchanged", "UnknownInitial", "UnknownInitialMax"
        )

        val performScan: () -> Unit = {
            val startAddr: Long
            val endAddr: Long

            if (useCustomRange) {
                startAddr = customRangeStart.replace("0x", "", ignoreCase = true).toLongOrNull(16) ?: 0L
                endAddr = customRangeEnd.replace("0x", "", ignoreCase = true).toLongOrNull(16) ?: 0L
            } else {
                startAddr = activeMap?.start ?: 0L
                endAddr = activeMap?.end ?: 0L
            }

            if (pid != null && endAddr > startAddr) {
                coroutineScope.launch {
                    isScanning = true
                    progress = 0f
                    timeRemainingText = "Starting scan..."

                    // Snapshot candidates before clearing UI list
                    val previousCandidates = if (isRescanMode) {
                        MemoryScannerState.allCandidates.snapshot()
                    } else {
                        CompactScanResults()
                    }

                    val vtVal = when (scanValueType) {
                        "Byte (UInt8)", "Byte" -> 0
                        "SByte (Int8)" -> 1
                        "UInt16" -> 2
                        "Int16" -> 3
                        "UInt32" -> 4
                        "Int32", "Unknown" -> 5
                        "UInt64" -> 6
                        "Int64" -> 7
                        "Float" -> 8
                        "Double" -> 9
                        "ASCII String", "Hex Mask (e.g. 00 11 ?? 33)" -> 10
                        else -> 5
                    }

                    val ctVal = when (scanCompareType) {
                        "ExactValue" -> 0
                        "Fuzzy" -> 1
                        "BiggerThan" -> 2
                        "SmallerThan" -> 3
                        "Between" -> 4
                        "BiggerThanLast" -> 5
                        "IncreasedBy" -> 6
                        "SmallerThanLast" -> 7
                        "DecreasedBy" -> 8
                        "ValueChanged" -> 9
                        "ValueUnchanged" -> 10
                        "UnknownInitial" -> 11
                        "UnknownInitialMax" -> 12
                        else -> 0
                    }

                    val noValueNeeded = scanCompareType == "UnknownInitial" ||
                            scanCompareType == "ValueChanged" ||
                            scanCompareType == "ValueUnchanged" ||
                            scanCompareType == "BiggerThanLast" ||
                            scanCompareType == "SmallerThanLast"

                    var maskBytes: ByteArray? = null
                    val typeSize = when {
                        scanValueType == "Unknown" -> 4
                        scanValueType.contains("Byte") -> 1
                        scanValueType.contains("16") -> 2
                        scanValueType.contains("32") -> 4
                        scanValueType.contains("64") -> 8
                        scanValueType.contains("Float") -> 4
                        scanValueType.contains("Double") -> 8
                        else -> 4
                    }
                    val bytes = if (noValueNeeded) {
                        ByteArray(typeSize)
                    } else if (scanValueType == "ASCII String") {
                        val pattern = scanValue.encodeToByteArray()
                        maskBytes = ByteArray(pattern.size) { 1 }
                        pattern
                    } else if (scanValueType == "Hex Mask (e.g. 00 11 ?? 33)") {
                        val pair = parseHexMask(scanValue)
                        if (pair != null) {
                            maskBytes = pair.second
                            pair.first
                        } else null
                    } else if (scanCompareType == "Between") {
                        val b1 = scanValueToBytes(scanValue, scanValueType)
                        val b2 = scanValueToBytes(scanValueExtra, scanValueType)
                        if (b1 == null || b2 == null) null else b1 + b2
                    } else {
                        scanValueToBytes(scanValue, scanValueType)
                    }

                    if (bytes == null) {
                        AppContainer.debuggerUseCase.log("SCAN", "Invalid value format", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                        isScanning = false
                        return@launch
                    }

                    val totalRegionBytes = endAddr - startAddr
                    val scanStartTime = System.currentTimeMillis()
                    val progressJob = launch {
                        val estSpeedBytesPerSec = 200 * 1024 * 1024L // 200 MB/s
                        val estTotalSec = if (isRescanMode) {
                            (previousCandidates.size * typeSize).toDouble() / estSpeedBytesPerSec
                        } else {
                            totalRegionBytes.toDouble() / estSpeedBytesPerSec
                        }
                        while (isScanning) {
                            val elapsed = (System.currentTimeMillis() - scanStartTime) / 1000.0
                            val estProgress = (elapsed / maxOf(estTotalSec, 0.1)).toFloat().coerceIn(0f, 0.99f)
                            if (estProgress > progress) {
                                progress = estProgress
                                val remaining = maxOf(0, (estTotalSec - elapsed).toInt())
                                timeRemainingText = "Estimated: ${remaining}s remaining (${(estProgress * 100).toInt()}%)"
                            }
                            kotlinx.coroutines.delay(200)
                        }
                    }

                    withContext(Dispatchers.IO) {
                        try {
                            if (isRescanMode && previousCandidates.size > 0) {
                                // --- RESCAN: CMD_PROC_SCAN_COUNT ---
                                val baseAddr = MemoryScannerState.scanBaseAddressState.value
                                AppContainer.debuggerUseCase.log("SCAN", "Rescan: vtVal=$vtVal, ctVal=$ctVal, ${previousCandidates.size} candidates, base=0x${baseAddr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)

                                // Compare types 5-10 need previous values sent alongside offsets
                                val needsPrevious = ctVal in 5..10
                                val valSize = if (noValueNeeded && !needsPrevious) typeSize else bytes.size

                                val countPayload = BinaryBuffer(18).apply {
                                    writeInt(pid)
                                    writeLong(baseAddr)
                                    writeByte(vtVal.toByte())
                                    writeByte(ctVal.toByte())
                                    writeInt(bytes.size)
                                }.bytes

                                client.connection.execute(readTimeoutMs = ScanReadTimeoutMs) { inStr, outStr ->
                                    client.connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_SCAN_COUNT, countPayload)
                                    var status = client.connection.receiveStatus(inStr)
                                    if (status != ProtocolConstants.CMD_SUCCESS) {
                                        throw java.io.IOException("Scan count failed status: 0x${status.toString(16)}")
                                    }

                                    // Send value/mask
                                    outStr.write(bytes)
                                    maskBytes?.let { outStr.write(it) }
                                    outStr.flush()

                                    // Send candidate chunks
                                    val entrySize = if (needsPrevious) 4 + typeSize else 4
                                    val maxChunkDataSize = 0x3FFF0 // ~256KB chunks
                                    val entriesPerChunk = maxChunkDataSize / entrySize

                                    val survivorResults = CompactScanResults(previousCandidates.size)
                                    var idx = 0
                                    val prevTotal = previousCandidates.size

                                    while (idx < prevTotal) {
                                        val batchEnd = minOf(idx + entriesPerChunk, prevTotal)
                                        val batchSize = batchEnd - idx
                                        val chunkDataSize = batchSize * entrySize

                                        // Write chunk_len (uint32)
                                        val chunkLenBuf = BinaryBuffer(4).apply { writeInt(chunkDataSize) }.bytes
                                        outStr.write(chunkLenBuf)

                                        // Write entries
                                        val chunkBuf = BinaryBuffer(chunkDataSize)
                                        for (i in idx until batchEnd) {
                                            val candidateOffset = previousCandidates.getOffset(i)
                                            chunkBuf.writeInt(candidateOffset.toInt())
                                            if (needsPrevious) {
                                                val prevVal = previousCandidates.getValueBytes(i)
                                                if (prevVal.size >= typeSize) {
                                                    chunkBuf.writeBytes(prevVal.copyOf(typeSize))
                                                } else {
                                                    chunkBuf.writeBytes(prevVal)
                                                    chunkBuf.writeBytes(ByteArray(typeSize - prevVal.size))
                                                }
                                            }
                                        }
                                        outStr.write(chunkBuf.bytes)
                                        outStr.flush()

                                        // Read survivors for this chunk
                                        while (true) {
                                            val lenBytes = client.connection.readExactly(inStr, 8)
                                            val blockLen = BinaryBuffer(lenBytes).readLong()
                                            if (blockLen == -1L) break // per-chunk sentinel

                                            val blockBytes = client.connection.readExactly(inStr, blockLen.toInt())
                                            val blockBuf = BinaryBuffer(blockBytes)
                                            while (blockBuf.hasRemaining()) {
                                                val offset = blockBuf.readInt().toLong()
                                                val valBytes = blockBuf.readBytes(typeSize)
                                                survivorResults.add(offset, valBytes)
                                            }
                                        }

                                        idx = batchEnd
                                        if (prevTotal > 0) {
                                            progress = idx.toFloat() / prevTotal.toFloat()
                                        }
                                    }

                                    // Send end-of-chunks sentinel (0xFFFFFFFF)
                                    val sentinelBuf = BinaryBuffer(4).apply { writeInt(-1) }.bytes
                                    outStr.write(sentinelBuf)
                                    outStr.flush()

                                    MemoryScannerState.allCandidates.clear()
                                    MemoryScannerState.allCandidates.addAll(survivorResults)
                                    totalMatchesCount = MemoryScannerState.allCandidates.size.toLong()

                                    // Final CMD_SUCCESS from server
                                    client.connection.receiveStatus(inStr)
                                }
                            } else {
                                // --- FIRST SCAN: CMD_PROC_SCAN_START ---
                                AppContainer.debuggerUseCase.log("SCAN", "Starting scan: vtVal=$vtVal, ctVal=$ctVal, alignment=$alignment, range=0x${startAddr.toString(16)}-0x${endAddr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)

                                MemoryScannerState.scanBaseAddressState.value = startAddr
                                MemoryScannerState.allCandidates.clear()

                                val scanLength = (endAddr - startAddr).coerceIn(0L, 0xFFFFFFFFL)
                                val startPayload = BinaryBuffer(23).apply {
                                    writeInt(pid)
                                    writeLong(startAddr)
                                    writeInt(scanLength.toInt())
                                    writeByte(vtVal.toByte())
                                    writeByte(ctVal.toByte())
                                    writeByte(alignment.toByte())
                                    writeInt(bytes.size)
                                }.bytes

                                client.connection.execute(readTimeoutMs = ScanReadTimeoutMs) { inStr, outStr ->
                                    client.connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_SCAN_START, startPayload)
                                    var status = client.connection.receiveStatus(inStr)
                                    if (status != ProtocolConstants.CMD_SUCCESS) {
                                        throw java.io.IOException("Scan start failed status: 0x${status.toString(16)}")
                                    }

                                    outStr.write(bytes)
                                    maskBytes?.let { outStr.write(it) }
                                    outStr.flush()

                                    status = client.connection.receiveStatus(inStr)
                                    if (status != ProtocolConstants.CMD_SUCCESS) {
                                        throw java.io.IOException("Scan start ack failed")
                                    }

                                    val valSize = bytes.size
                                    val newCandidates = CompactScanResults(10000)

                                    while (true) {
                                        val lenBytes = client.connection.readExactly(inStr, 8)
                                        val blockLen = BinaryBuffer(lenBytes).readLong()
                                        if (blockLen == -1L) break

                                        val blockBytes = client.connection.readExactly(inStr, blockLen.toInt())
                                        val blockBuf = BinaryBuffer(blockBytes)
                                        while (blockBuf.hasRemaining()) {
                                            val offset = blockBuf.readInt().toLong()
                                            val valBytes = blockBuf.readBytes(valSize)
                                            newCandidates.add(offset, valBytes)

                                            if (totalRegionBytes > 0) {
                                                val prog = (offset.toFloat() / totalRegionBytes.toFloat()).coerceIn(0f, 1f)
                                                if (prog > progress) {
                                                    progress = prog
                                                }
                                            }
                                        }
                                    }

                                    MemoryScannerState.allCandidates.clear()
                                    MemoryScannerState.allCandidates.addAll(newCandidates)
                                    totalMatchesCount = MemoryScannerState.allCandidates.size.toLong()

                                    client.connection.receiveStatus(inStr)
                                }
                            }
                            isRescanMode = true
                        } catch (e: Exception) {
                            AppContainer.debuggerUseCase.log("SCAN", "Scan failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                        } finally {
                            progressJob.cancel()
                            progress = 1.0f
                            timeRemainingText = "Finished"
                        }
                    }
                    isScanning = false
                }
            } else {
                AppContainer.debuggerUseCase.log("SCAN", "No active process or invalid range", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
            }
        }

        val resetScan: () -> Unit = {
            isRescanMode = false
            MemoryScannerState.allCandidates.clear()
            totalMatchesCount = 0L
            progress = 0f
        }

        if (isMobile) {
            var selectedMobileTab by remember { mutableStateOf(0) }
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = selectedMobileTab,
                    containerColor = PS5ThemeColors.SecondaryBg,
                    contentColor = PS5ThemeColors.AccentCyan,
                    divider = { HorizontalDivider(color = PS5ThemeColors.BorderColor) }
                ) {
                    Tab(selected = selectedMobileTab == 0, onClick = { selectedMobileTab = 0 }) {
                        Text("Settings", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedMobileTab == 1, onClick = { selectedMobileTab = 1 }) {
                        BadgedBox(badge = { if (totalMatchesCount > 0) Badge { Text(totalMatchesCount.toString()) } }) {
                            Text("Results", modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                if (selectedMobileTab == 0) {
                    ScannerSettings(
                        modifier = Modifier.weight(1f),
                        scanValue = scanValue,
                        onScanValueChange = { scanValue = it },
                        scanValueExtra = scanValueExtra,
                        onScanValueExtraChange = { scanValueExtra = it },
                        scanValueType = scanValueType,
                        onScanValueTypeChange = {
                            scanValueType = it
                            alignment = when {
                                it == "Unknown" -> 4
                                it.contains("Byte") -> 1
                                it.contains("16") -> 2
                                it.contains("32") -> 4
                                it.contains("64") -> 8
                                it.contains("Float") -> 4
                                it.contains("Double") -> 8
                                else -> 4
                            }
                        },
                        scanCompareType = scanCompareType,
                        onScanCompareTypeChange = { scanCompareType = it },
                        alignment = alignment,
                        onAlignmentChange = { alignment = it },
                        useCustomRange = useCustomRange,
                        onUseCustomRangeChange = { useCustomRange = it },
                        customRangeStart = customRangeStart,
                        onCustomRangeStartChange = { customRangeStart = it },
                        customRangeEnd = customRangeEnd,
                        onCustomRangeEndChange = { customRangeEnd = it },
                        activeMap = activeMap,
                        activeMaps = activeMaps,
                        isConnected = isConnected,
                        isScanning = isScanning,
                        isRescanMode = isRescanMode,
                        onPerformScan = performScan,
                        onResetScan = resetScan,
                        valueTypes = valueTypes,
                        compareTypes = compareTypes
                    )
                } else {
                    ScannerResults(
                        modifier = Modifier.weight(1f),
                        isScanning = isScanning,
                        progress = progress,
                        timeRemainingText = timeRemainingText,
                        totalMatchesCount = totalMatchesCount,
                        useCustomRange = useCustomRange,
                        customRangeStart = customRangeStart,
                        activeMap = activeMap,
                        scanValueType = scanValueType,
                        isRescanMode = isRescanMode,
                        onJumpToAddress = onJumpToAddress
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                ScannerSettings(
                    modifier = Modifier.width(340.dp),
                    scanValue = scanValue,
                    onScanValueChange = { scanValue = it },
                    scanValueExtra = scanValueExtra,
                    onScanValueExtraChange = { scanValueExtra = it },
                    scanValueType = scanValueType,
                    onScanValueTypeChange = {
                        scanValueType = it
                        alignment = when {
                            it == "Unknown" -> 4
                            it.contains("Byte") -> 1
                            it.contains("16") -> 2
                            it.contains("32") -> 4
                            it.contains("64") -> 8
                            it.contains("Float") -> 4
                            it.contains("Double") -> 8
                            else -> 4
                        }
                    },
                    scanCompareType = scanCompareType,
                    onScanCompareTypeChange = { scanCompareType = it },
                    alignment = alignment,
                    onAlignmentChange = { alignment = it },
                    useCustomRange = useCustomRange,
                    onUseCustomRangeChange = { useCustomRange = it },
                    customRangeStart = customRangeStart,
                    onCustomRangeStartChange = { customRangeStart = it },
                    customRangeEnd = customRangeEnd,
                    onCustomRangeEndChange = { customRangeEnd = it },
                    activeMap = activeMap,
                    activeMaps = activeMaps,
                    isConnected = isConnected,
                    isScanning = isScanning,
                    isRescanMode = isRescanMode,
                    onPerformScan = performScan,
                    onResetScan = resetScan,
                    valueTypes = valueTypes,
                    compareTypes = compareTypes
                )
                ScannerResults(
                    modifier = Modifier.weight(1f),
                    isScanning = isScanning,
                    progress = progress,
                    timeRemainingText = timeRemainingText,
                    totalMatchesCount = totalMatchesCount,
                    useCustomRange = useCustomRange,
                    customRangeStart = customRangeStart,
                    activeMap = activeMap,
                    scanValueType = scanValueType,
                    isRescanMode = isRescanMode,
                    onJumpToAddress = onJumpToAddress
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerSettings(
    modifier: Modifier = Modifier,
    scanValue: String,
    onScanValueChange: (String) -> Unit,
    scanValueExtra: String,
    onScanValueExtraChange: (String) -> Unit,
    scanValueType: String,
    onScanValueTypeChange: (String) -> Unit,
    scanCompareType: String,
    onScanCompareTypeChange: (String) -> Unit,
    alignment: Int,
    onAlignmentChange: (Int) -> Unit,
    useCustomRange: Boolean,
    onUseCustomRangeChange: (Boolean) -> Unit,
    customRangeStart: String,
    onCustomRangeStartChange: (String) -> Unit,
    customRangeEnd: String,
    onCustomRangeEndChange: (String) -> Unit,
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    isConnected: Boolean,
    isScanning: Boolean,
    isRescanMode: Boolean,
    onPerformScan: () -> Unit,
    onResetScan: () -> Unit,
    valueTypes: List<String>,
    compareTypes: List<String>
) {
    val currentStart = activeMap?.start ?: 0L
    val currentEnd = activeMap?.end ?: 0L
    val openTargets = if (activeMaps.isNotEmpty()) activeMaps else listOfNotNull(activeMap)
    val openMinStart = openTargets.minOfOrNull { it.start } ?: currentStart
    val openMaxEnd = openTargets.maxOfOrNull { it.end } ?: currentEnd

    val customStart = parseHexAddress(customRangeStart)
    val customEnd = parseHexAddress(customRangeEnd)
    val selectedStart = if (useCustomRange) customStart else activeMap?.start
    val selectedEnd = if (useCustomRange) customEnd else activeMap?.end
    val selectedSize = if (selectedStart != null && selectedEnd != null && selectedEnd > selectedStart) selectedEnd - selectedStart else 0L
    val rangeIsValid = selectedSize > 0
    val canScan = isConnected && rangeIsValid && !isScanning

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PS5ThemeColors.DarkBg)
            .border(BorderStroke(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.65f)))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = PS5ThemeColors.AccentCyan.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, PS5ThemeColors.AccentCyan.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = PS5Icons.MemoryScan,
                    contentDescription = null,
                    tint = PS5ThemeColors.AccentCyan,
                    modifier = Modifier.padding(7.dp).size(18.dp)
                )
            }
            Column {
                Text("Memory Search", style = MaterialTheme.typography.titleMedium, color = PS5ThemeColors.TextMain)
                Text(
                    if (isRescanMode) "Refine active result set" else "Create a new scan over the selected address scope",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PS5ThemeColors.Surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScannerStat("Mode", if (isRescanMode) "Rescan" else "First", Modifier.weight(1f))
                ScannerStat("Scope", if (selectedSize > 0) formatBytes(selectedSize) else "None", Modifier.weight(1f))
                ScannerStat("Align", alignment.toString(), Modifier.weight(1f))
            }
        }

        ScannerSection(title = "VALUE") {
            val needsValue = scanCompareType != "UnknownInitial" &&
                    scanCompareType != "ValueChanged" &&
                    scanCompareType != "ValueUnchanged" &&
                    scanCompareType != "BiggerThanLast" &&
                    scanCompareType != "SmallerThanLast"

            if (needsValue) {
                OutlinedTextField(
                    value = scanValue,
                    onValueChange = onScanValueChange,
                    label = { Text(if (scanCompareType == "Between") "Low value" else "Search value") },
                    placeholder = { Text(if (scanValueType == "Hex Mask (e.g. 00 11 ?? 33)") "48 8B ?? ?? 89" else "Enter value") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = scannerTextFieldColors()
                )
            }

            if (scanCompareType == "Between") {
                OutlinedTextField(
                    value = scanValueExtra,
                    onValueChange = onScanValueExtraChange,
                    label = { Text("High value") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = scannerTextFieldColors()
                )
            }

            ScannerDropdown(
                label = "Value Type",
                currentValue = scanValueType,
                options = valueTypes,
                onSelected = onScanValueTypeChange
            )

            ScannerDropdown(
                label = "Compare Type",
                currentValue = scanCompareType,
                options = compareTypes,
                onSelected = onScanCompareTypeChange
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2, 4, 8).forEach { option ->
                    FilterChip(
                        selected = alignment == option,
                        onClick = { onAlignmentChange(option) },
                        label = { Text(option.toString(), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = PS5ThemeColors.Surface,
                            selectedContainerColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.18f),
                            labelColor = PS5ThemeColors.TextMuted,
                            selectedLabelColor = PS5ThemeColors.AccentCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = alignment == option,
                            borderColor = PS5ThemeColors.BorderColor,
                            selectedBorderColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        ScannerSection(title = "SCOPE") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = !useCustomRange,
                    onClick = { onUseCustomRangeChange(false) },
                    enabled = activeMap != null,
                    label = { Text("Active region", fontSize = 11.sp) },
                    colors = scannerChipColors(),
                    border = scannerChipBorder(!useCustomRange),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = useCustomRange,
                    onClick = { onUseCustomRangeChange(true) },
                    label = { Text("Address range", fontSize = 11.sp) },
                    colors = scannerChipColors(),
                    border = scannerChipBorder(useCustomRange),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    if (openTargets.isNotEmpty()) {
                        onUseCustomRangeChange(true)
                        onCustomRangeStartChange(hexAddress(openMinStart))
                        onCustomRangeEndChange(hexAddress(openMaxEnd))
                    }
                },
                enabled = openTargets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, PS5ThemeColors.BorderColor),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = PS5Icons.MemoryMap,
                        contentDescription = null,
                        tint = PS5ThemeColors.AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    val countStr = if (openTargets.size > 1) " (${openTargets.size} regions)" else ""
                    Text(
                        "Search All Opened Regions$countStr",
                        fontSize = 11.sp,
                        color = PS5ThemeColors.TextMain,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                color = PS5ThemeColors.Surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (rangeIsValid) PS5ThemeColors.BorderColor else PS5ThemeColors.StatusRed.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (useCustomRange) "Custom Address Scope" else (activeMap?.name?.ifEmpty { "unnamed region" } ?: "No active region selected"),
                        color = if (useCustomRange) PS5ThemeColors.AccentCyan else if (activeMap != null) PS5ThemeColors.AccentAmber else PS5ThemeColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        selectedStart?.let { start ->
                            selectedEnd?.let { end -> "0x${start.toString(16).uppercase()} - 0x${end.toString(16).uppercase()}" }
                        } ?: "Choose a region or enter a range",
                        color = PS5ThemeColors.TextMain,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        if (rangeIsValid) "${formatBytes(selectedSize)} searchable" else "Invalid or empty range",
                        color = if (rangeIsValid) PS5ThemeColors.TextMuted else PS5ThemeColors.StatusRed,
                        fontSize = 11.sp
                    )
                }
            }

            if (useCustomRange) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customRangeStart,
                        onValueChange = onCustomRangeStartChange,
                        label = { Text("Start") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = scannerTextFieldColors()
                    )
                    OutlinedTextField(
                        value = customRangeEnd,
                        onValueChange = onCustomRangeEndChange,
                        label = { Text("End") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = scannerTextFieldColors()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    if (openTargets.size > 1) {
                        SmallRangeButton("All Opened") {
                            onCustomRangeStartChange(hexAddress(openMinStart))
                            onCustomRangeEndChange(hexAddress(openMaxEnd))
                        }
                    }
                    if (activeMap != null) {
                        SmallRangeButton("Full") {
                            onCustomRangeStartChange(hexAddress(currentStart))
                            onCustomRangeEndChange(hexAddress(currentEnd))
                        }
                        SmallRangeButton("First 1MB") {
                            onCustomRangeStartChange(hexAddress(currentStart))
                            onCustomRangeEndChange(hexAddress(minOf(currentEnd, currentStart + 0x100000L)))
                        }
                        SmallRangeButton("Last 1MB") {
                            onCustomRangeStartChange(hexAddress(maxOf(currentStart, currentEnd - 0x100000L)))
                            onCustomRangeEndChange(hexAddress(currentEnd))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPerformScan,
                enabled = canScan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(PS5Icons.MemoryScan, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isRescanMode) "NEXT SCAN" else "FIRST SCAN", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            if (isRescanMode) {
                OutlinedButton(
                    onClick = onResetScan,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, PS5ThemeColors.StatusRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PS5ThemeColors.StatusRed),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("RESET SCAN")
                }
            }
        }
    }
}

@Composable
fun ScannerResults(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    progress: Float,
    timeRemainingText: String,
    totalMatchesCount: Long,
    useCustomRange: Boolean,
    customRangeStart: String,
    activeMap: MemoryRange?,
    scanValueType: String,
    isRescanMode: Boolean,
    onJumpToAddress: ((Long) -> Unit)?
) {
    val baseAddress = if (useCustomRange) {
        parseHexAddress(customRangeStart) ?: 0L
    } else {
        activeMap?.start ?: 0L
    }
    val allCandidates = MemoryScannerState.allCandidates
    val totalHits = allCandidates.size

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PS5ThemeColors.DarkBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PS5ThemeColors.Surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Results", style = MaterialTheme.typography.titleMedium, color = PS5ThemeColors.TextMain)
                        Text(
                            if (isScanning) timeRemainingText else "$totalHits matches kept in memory",
                            color = if (isScanning) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        color = if (isScanning) PS5ThemeColors.AccentCyan.copy(alpha = 0.16f) else PS5ThemeColors.SecondaryBg,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isScanning) PS5ThemeColors.AccentCyan.copy(alpha = 0.55f) else PS5ThemeColors.BorderColor)
                    ) {
                        Text(
                            if (isScanning) "${(progress * 100).toInt()}%" else if (isRescanMode) "READY" else "IDLE",
                            color = if (isScanning) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { if (isScanning) progress else if (isRescanMode) 1f else 0f },
                    color = PS5ThemeColors.AccentCyan,
                    trackColor = PS5ThemeColors.SecondaryBg,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ScannerStat("Base", hexAddress(baseAddress), Modifier.weight(1f))
                    ScannerStat("Value type", scanValueType, Modifier.weight(1f))
                    ScannerStat("Hits Kept", totalHits.toString(), Modifier.weight(1f))
                }
            }
        }

        Surface(
            color = PS5ThemeColors.Surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("ADDRESS", color = PS5ThemeColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(2f))
                Text("OFFSET", color = PS5ThemeColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("VALUE", color = PS5ThemeColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.5f))
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(totalHits, key = { idx -> allCandidates.getOffset(idx) }) { idx ->
                    val offset = allCandidates.getOffset(idx)
                    val valueBytes = allCandidates.getValueBytes(idx)
                    val absAddr = baseAddress + offset
                    ScanResultRow(
                        absAddr = absAddr,
                        offset = offset,
                        value = parseValueBytes(valueBytes, scanValueType),
                        onJump = { onJumpToAddress?.invoke(absAddr) },
                        onAddWatch = {
                            val wlType = when {
                                scanValueType.contains("Byte") -> "Byte"
                                scanValueType.contains("16") -> "Int16"
                                scanValueType.contains("32") -> "Int32"
                                scanValueType.contains("64") -> "Int64"
                                scanValueType.contains("Float") -> "Float"
                                scanValueType.contains("Double") -> "Double"
                                else -> "Int32"
                            }
                            AppContainer.debuggerUseCase.addToWatchlist(absAddr, wlType)
                        },
                        onAddCheat = {
                            val procInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                            AppContainer.onCreateCheatRequested?.invoke(
                                com.osr.ps5debugger.domain.model.Cheat(
                                    id = "",
                                    name = "Scan Result",
                                    type = com.osr.ps5debugger.domain.model.CheatType.Toggle,
                                    address = absAddr,
                                    hexOnValue = "",
                                    hexOffValue = "",
                                    titleId = procInfo?.titleId ?: "Unknown"
                                )
                            )
                        }
                    )
                }
            }

            if (totalHits == 0 && !isScanning && isRescanMode) {
                EmptyScannerState("No matches found")
            } else if (totalHits == 0 && !isScanning) {
                EmptyScannerState("Run a first scan to populate this table")
            }
        }
    }
}

@Composable
private fun ScannerSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = PS5ThemeColors.AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ScannerStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = PS5ThemeColors.TextMuted, fontSize = 10.sp)
        Text(value, color = PS5ThemeColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BoxScope.EmptyScannerState(message: String) {
    Surface(
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor),
        modifier = Modifier.align(Alignment.Center)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(PS5Icons.MemoryScan, contentDescription = null, tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(24.dp))
            Text(message, color = PS5ThemeColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SmallRangeButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PS5ThemeColors.TextMain)
    ) {
        Text(text, fontSize = 10.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun scannerChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = PS5ThemeColors.Surface,
    selectedContainerColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.18f),
    labelColor = PS5ThemeColors.TextMuted,
    selectedLabelColor = PS5ThemeColors.AccentCyan,
    disabledContainerColor = PS5ThemeColors.Surface.copy(alpha = 0.45f),
    disabledLabelColor = PS5ThemeColors.TextMuted.copy(alpha = 0.55f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun scannerChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = PS5ThemeColors.BorderColor,
    selectedBorderColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.6f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerDropdown(
    label: String,
    currentValue: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = scannerTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PS5ThemeColors.Surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = PS5ThemeColors.TextMain) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanResultRow(
    absAddr: Long,
    offset: Long,
    value: String,
    onJump: () -> Unit,
    onAddWatch: () -> Unit,
    onAddCheat: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = PS5ThemeColors.Surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            if (event.buttons.isSecondaryPressed) {
                                showMenu = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = {},
                onDoubleClick = onJump,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                String.format("0x%X", absAddr),
                color = PS5ThemeColors.AccentCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(2f)
            )
            Text(
                String.format("+0x%X", offset),
                color = PS5ThemeColors.TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                color = PS5ThemeColors.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.weight(1.5f)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(PS5ThemeColors.Surface)
        ) {
            DropdownMenuItem(
                text = { Text("Jump to in Memory View", color = PS5ThemeColors.TextMain) },
                onClick = { onJump(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Add to Watch List", color = PS5ThemeColors.TextMain) },
                onClick = { onAddWatch(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Add to Cheats", color = PS5ThemeColors.AccentCyan) },
                onClick = { onAddCheat(); showMenu = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun scannerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PS5ThemeColors.AccentCyan,
    unfocusedBorderColor = PS5ThemeColors.BorderColor,
    focusedLabelColor = PS5ThemeColors.AccentCyan,
    unfocusedLabelColor = PS5ThemeColors.TextMuted,
    focusedTextColor = PS5ThemeColors.TextMain,
    unfocusedTextColor = PS5ThemeColors.TextMain,
    cursorColor = PS5ThemeColors.AccentCyan
)

private fun scanValueToBytes(valueStr: String, type: String): ByteArray? {
    val buf = BinaryBuffer(8)
    try {
        when (type) {
            "Unknown" -> {
                val parsedInt = valueStr.toIntOrNull()
                    ?: valueStr.toUIntOrNull()?.toInt()
                    ?: valueStr.toFloatOrNull()?.let { java.lang.Float.floatToIntBits(it) }
                    ?: valueStr.toDoubleOrNull()?.let { java.lang.Float.floatToIntBits(it.toFloat()) }
                if (parsedInt != null) {
                    buf.writeInt(parsedInt)
                } else return null
            }
            "Byte (UInt8)", "Byte" -> buf.writeByte(valueStr.toUByte().toByte())
            "SByte (Int8)" -> buf.writeByte(valueStr.toByte())
            "UInt16" -> buf.writeShort(valueStr.toUShort().toShort())
            "Int16" -> buf.writeShort(valueStr.toShort())
            "UInt32" -> buf.writeInt(valueStr.toUInt().toInt())
            "Int32" -> buf.writeInt(valueStr.toInt())
            "UInt64" -> buf.writeLong(valueStr.toULong().toLong())
            "Int64" -> buf.writeLong(valueStr.toLong())
            "Float" -> buf.writeFloat(valueStr.toFloat())
            "Double" -> buf.writeDouble(valueStr.toDouble())
            else -> return null
        }
        val out = ByteArray(buf.position)
        System.arraycopy(buf.bytes, 0, out, 0, buf.position)
        return out
    } catch (_: Exception) {
        return null
    }
}

private fun parseHexMask(hexStr: String): Pair<ByteArray, ByteArray>? {
    val tokens = hexStr.trim().split(Regex("\\s+"))
    if (tokens.isEmpty()) return null
    val pattern = ByteArray(tokens.size)
    val mask = ByteArray(tokens.size)
    try {
        for (i in tokens.indices) {
            val token = tokens[i]
            if (token == "??" || token == "?") {
                pattern[i] = 0
                mask[i] = 0
            } else {
                pattern[i] = token.toInt(16).toByte()
                mask[i] = 1
            }
        }
        return Pair(pattern, mask)
    } catch (_: Exception) {
        return null
    }
}

private fun parseHexAddress(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
}

private fun hexAddress(value: Long): String = "0x${value.toString(16).uppercase()}"

private fun formatBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble() / 1024.0
    var unitIndex = 0
    while (amount >= 1024.0 && unitIndex < units.lastIndex) {
        amount /= 1024.0
        unitIndex++
    }
    return if (amount >= 100.0) {
        "${amount.toInt()} ${units[unitIndex]}"
    } else {
        "${(amount * 10).toInt() / 10.0} ${units[unitIndex]}"
    }
}
