package com.osr.ps5debugger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.ProtocolConstants
import com.osr.ps5debugger.protocol.Ps5ScanResult
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.ui.watchlist.parseValueBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val ScanReadTimeoutMs = 5 * 60 * 1000

object MemoryScannerState {
    val scanValueState = mutableStateOf("")
    val scanValueExtraState = mutableStateOf("200")
    val scanValueTypeState = mutableStateOf("Int32")
    val scanCompareTypeState = mutableStateOf("ExactValue")
    val alignmentState = mutableStateOf(4)
    val isScanningState = mutableStateOf(false)
    val progressState = mutableStateOf(0f)
    val scanResults = mutableStateListOf<Ps5ScanResult>()
    val totalMatchesCountState = mutableStateOf(0L)
    val isRescanModeState = mutableStateOf(false)
    val timeRemainingTextState = mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MemoryScannerView(
    activeMap: MemoryRange?,
    modifier: Modifier = Modifier,
    onJumpToAddress: ((Long) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
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
        val scanResults = MemoryScannerState.scanResults
        var totalMatchesCount by MemoryScannerState.totalMatchesCountState
        
        var isRescanMode by MemoryScannerState.isRescanModeState

        val valueTypes = listOf(
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

        var timeRemainingText by MemoryScannerState.timeRemainingTextState

        val performScan = {
            if (activeMap != null && pid != null) {
                coroutineScope.launch {
                    isScanning = true
                    progress = 0f
                    timeRemainingText = "Starting scan..."
                    scanResults.clear()
                    
                    val vtVal = when (scanValueType) {
                        "Byte (UInt8)", "Byte" -> 0
                        "SByte (Int8)" -> 1
                        "UInt16" -> 2
                        "Int16" -> 3
                        "UInt32" -> 4
                        "Int32" -> 5
                        "UInt64" -> 6
                        "Int64" -> 7
                        "Float" -> 8
                        "Double" -> 9
                        "ASCII String", "Hex Mask (e.g. 00 11 ?? 33)" -> 10
                        else -> 4
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

                    val totalRegionBytes = activeMap.end - activeMap.start
                    val scanStartTime = System.currentTimeMillis()
                    val progressJob = launch {
                        val estSpeedBytesPerSec = 200 * 1024 * 1024L // 200 MB/s
                        val estTotalSec = totalRegionBytes.toDouble() / estSpeedBytesPerSec
                        while (isScanning) {
                            val elapsed = (System.currentTimeMillis() - scanStartTime) / 1000.0
                            val estProgress = (elapsed / estTotalSec).toFloat().coerceIn(0f, 0.99f)
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
                            AppContainer.debuggerUseCase.log("SCAN", "Starting scan: vtVal=$vtVal, ctVal=$ctVal, alignment=$alignment, lenData=${bytes.size}, hasMask=${maskBytes != null}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                            
                            val startPayload = BinaryBuffer(23).apply {
                                writeInt(pid)
                                writeLong(activeMap.start)
                                writeInt((activeMap.end - activeMap.start).toInt())
                                writeByte(vtVal.toByte())
                                writeByte(ctVal.toByte())
                                writeByte(alignment.toByte())
                                writeInt(bytes.size)
                            }.bytes

                            client.connection.execute(readTimeoutMs = ScanReadTimeoutMs) { inStr, outStr ->
                                AppContainer.debuggerUseCase.log("SCAN", "Using streaming scan to prevent OOM/crash", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                client.connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_SCAN_START, startPayload)
                                var status = client.connection.receiveStatus(inStr)
                                if (status != ProtocolConstants.CMD_SUCCESS) {
                                    throw java.io.IOException("Scan start failed status: 0x${status.toString(16)}")
                                }

                                // Send the actual search value trailing data after first CMD_SUCCESS
                                outStr.write(bytes)
                                maskBytes?.let {
                                    outStr.write(it)
                                }
                                outStr.flush()

                                // Read second ack of data
                                status = client.connection.receiveStatus(inStr)
                                if (status != ProtocolConstants.CMD_SUCCESS) {
                                    throw java.io.IOException("Scan start ack failed")
                                }

                                val valSize = bytes.size
                                var count = 0L
                                while (true) {
                                    val lenBytes = client.connection.readExactly(inStr, 8)
                                    val blockLen = BinaryBuffer(lenBytes).readLong()
                                    if (blockLen == -1L) break

                                    val blockBytes = client.connection.readExactly(inStr, blockLen.toInt())
                                    val blockBuf = BinaryBuffer(blockBytes)
                                    while (blockBuf.hasRemaining()) {
                                        val offset = blockBuf.readInt().toLong()
                                        val valBytes = blockBuf.readBytes(valSize)
                                        count++
                                        if (count <= 1000) {
                                            scanResults.add(Ps5ScanResult(offset, valBytes))
                                        }
                                        if (totalRegionBytes > 0) {
                                            val prog = (offset.toFloat() / totalRegionBytes.toFloat()).coerceIn(0f, 1f)
                                            if (prog > progress) {
                                                progress = prog
                                                val elapsed = (System.currentTimeMillis() - scanStartTime) / 1000.0
                                                if (prog > 0.01f && elapsed > 1.0) {
                                                    val remaining = ((elapsed / prog) - elapsed).toInt()
                                                    timeRemainingText = "Estimated: ${remaining}s remaining (${(prog * 100).toInt()}%)"
                                                } else {
                                                    timeRemainingText = "Scanning... (${(prog * 100).toInt()}%)"
                                                }
                                            }
                                        }
                                    }
                                }
                                totalMatchesCount = count
                                client.connection.receiveStatus(inStr)
                                AppContainer.debuggerUseCase.log("SCAN", "Scan finished. Found $totalMatchesCount matches.", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
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
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text("Memory Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            ) {
                if (isMobile) {
                    // Mobile responsive layout
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val needsValue = scanCompareType != "UnknownInitial" &&
                                         scanCompareType != "ValueChanged" &&
                                         scanCompareType != "ValueUnchanged" &&
                                         scanCompareType != "BiggerThanLast" &&
                                         scanCompareType != "SmallerThanLast"

                        if (needsValue) {
                            OutlinedTextField(
                                value = scanValue,
                                onValueChange = { scanValue = it },
                                label = { Text(if (scanCompareType == "Between") "Low Value" else "Value to Search") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        if (scanCompareType == "Between") {
                            OutlinedTextField(
                                value = scanValueExtra,
                                onValueChange = { scanValueExtra = it },
                                label = { Text("High Value") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Value Type dropdown
                            var vtExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { vtExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Type: $scanValueType", maxLines = 1)
                                }
                                DropdownMenu(expanded = vtExpanded, onDismissRequest = { vtExpanded = false }) {
                                    valueTypes.forEach { vt ->
                                        DropdownMenuItem(
                                            text = { Text(vt) },
                                            onClick = {
                                                scanValueType = vt
                                                alignment = when {
                                                    vt.contains("Byte") -> 1
                                                    vt.contains("16") -> 2
                                                    vt.contains("32") -> 4
                                                    vt.contains("64") -> 8
                                                    vt.contains("Float") -> 4
                                                    vt.contains("Double") -> 8
                                                    else -> 4
                                                }
                                                vtExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Compare Type dropdown
                            var ctExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { ctExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Comp: $scanCompareType", maxLines = 1)
                                }
                                DropdownMenu(expanded = ctExpanded, onDismissRequest = { ctExpanded = false }) {
                                    compareTypes.forEach { ct ->
                                        DropdownMenuItem(text = { Text(ct) }, onClick = { scanCompareType = ct; ctExpanded = false })
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { performScan() },
                                enabled = isConnected && activeMap != null && !isScanning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isRescanMode) "Next Scan" else "Scan")
                            }

                            if (isRescanMode) {
                                Button(
                                    onClick = {
                                        isRescanMode = false
                                        scanResults.clear()
                                        totalMatchesCount = 0L
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val needsValue = scanCompareType != "UnknownInitial" &&
                                         scanCompareType != "ValueChanged" &&
                                         scanCompareType != "ValueUnchanged" &&
                                         scanCompareType != "BiggerThanLast" &&
                                         scanCompareType != "SmallerThanLast"

                        if (needsValue) {
                            OutlinedTextField(
                                value = scanValue,
                                onValueChange = { scanValue = it },
                                label = { Text(if (scanCompareType == "Between") "Low Value" else "Value to Search") },
                                modifier = Modifier.width(180.dp),
                                singleLine = true
                            )
                        }

                        if (scanCompareType == "Between") {
                            OutlinedTextField(
                                value = scanValueExtra,
                                onValueChange = { scanValueExtra = it },
                                label = { Text("High Value") },
                                modifier = Modifier.width(180.dp),
                                singleLine = true
                            )
                        }

                        // Value Type dropdown
                        var vtExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { vtExpanded = true }) {
                                Text("Type: $scanValueType")
                            }
                            DropdownMenu(expanded = vtExpanded, onDismissRequest = { vtExpanded = false }) {
                                valueTypes.forEach { vt ->
                                    DropdownMenuItem(
                                        text = { Text(vt) },
                                        onClick = {
                                            scanValueType = vt
                                            alignment = when {
                                                vt.contains("Byte") -> 1
                                                vt.contains("16") -> 2
                                                vt.contains("32") -> 4
                                                vt.contains("64") -> 8
                                                vt.contains("Float") -> 4
                                                vt.contains("Double") -> 8
                                                else -> 4
                                            }
                                            vtExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Compare Type dropdown
                        var ctExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { ctExpanded = true }) {
                                Text("Compare: $scanCompareType")
                            }
                            DropdownMenu(expanded = ctExpanded, onDismissRequest = { ctExpanded = false }) {
                                compareTypes.forEach { ct ->
                                    DropdownMenuItem(text = { Text(ct) }, onClick = { scanCompareType = ct; ctExpanded = false })
                                }
                            }
                        }

                        Button(
                            onClick = { performScan() },
                            enabled = isConnected && activeMap != null && !isScanning
                        ) {
                            Text(if (isRescanMode) "Next Scan" else "First Scan")
                        }

                        if (isRescanMode) {
                            Button(
                                onClick = {
                                    isRescanMode = false
                                    scanResults.clear()
                                    totalMatchesCount = 0L
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            if (isScanning || progress > 0f) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (timeRemainingText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(timeRemainingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Scan Results (showing up to 1000)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Total Matches: $totalMatchesCount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }

            // Table headers
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Offset Address", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(if (isMobile) 1.5f else 2f).padding(start = 8.dp))
                Text("Value", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(scanResults) { res ->
                    val absoluteAddress = (activeMap?.start ?: 0L) + res.offset
                    var showItemMenu by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onDoubleClick = {
                                        onJumpToAddress?.invoke(absoluteAddress)
                                    },
                                    onLongClick = {
                                        showItemMenu = true
                                    }
                                )
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                                if (event.buttons.isSecondaryPressed) {
                                                    showItemMenu = true
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMobile) {
                                Column(modifier = Modifier.weight(1.5f).padding(start = 8.dp)) {
                                    Text(String.format("0x%X", absoluteAddress), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("+0x${res.offset.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            } else {
                                Text(String.format("0x%X (+0x%X)", absoluteAddress, res.offset), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(2f).padding(start = 8.dp))
                            }
                            Text(parseValueBytes(res.value, scanValueType), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }

                        DropdownMenu(
                            expanded = showItemMenu,
                            onDismissRequest = { showItemMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Jump to in Memory View") },
                                onClick = {
                                    onJumpToAddress?.invoke(absoluteAddress)
                                    showItemMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Watch List") },
                                onClick = {
                                    val wlType = when {
                                        scanValueType.contains("Byte") -> "Byte"
                                        scanValueType.contains("16") -> "Int16"
                                        scanValueType.contains("32") -> "Int32"
                                        scanValueType.contains("64") -> "Int64"
                                        scanValueType.contains("Float") -> "Float"
                                        scanValueType.contains("Double") -> "Double"
                                        else -> "Int32"
                                    }
                                    AppContainer.debuggerUseCase.addToWatchlist(absoluteAddress, wlType)
                                    AppContainer.debuggerUseCase.log("SCAN", "Added 0x${absoluteAddress.toString(16).uppercase()} to watch list", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                    showItemMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun scanValueToBytes(valueStr: String, type: String): ByteArray? {
    val buf = BinaryBuffer(8)
    try {
        when (type) {
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
