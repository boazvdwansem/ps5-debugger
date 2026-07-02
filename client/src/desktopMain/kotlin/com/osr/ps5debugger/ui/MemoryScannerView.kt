package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.ProtocolConstants
import com.osr.ps5debugger.protocol.Ps5ScanResult
import com.osr.ps5debugger.protocol.Ps5VmMapEntry
import com.osr.ps5debugger.service.DebuggerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val ScanReadTimeoutMs = 5 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScannerView(
    activeMap: Ps5VmMapEntry?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val client = DebuggerService.client
    val pid = DebuggerService.activePid
    val isConnected by DebuggerService.isConnected.collectAsState()

    var scanValue by remember { mutableStateOf("100") }
    var scanValueType by remember { mutableStateOf("Int32") }
    var scanCompareType by remember { mutableStateOf("ExactValue") }
    var alignment by remember { mutableStateOf(4) }
    
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val scanResults = remember { mutableStateListOf<Ps5ScanResult>() }
    var totalMatchesCount by remember { mutableStateOf(0L) }
    
    var isRescanMode by remember { mutableStateOf(false) }

    val valueTypes = listOf("Byte", "Int16", "Int32", "Int64", "Float", "Double")
    val compareTypes = listOf("ExactValue", "BiggerThan", "SmallerThan")

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Memory Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = scanValue,
                    onValueChange = { scanValue = it },
                    label = { Text("Value to Search") },
                    modifier = Modifier.width(180.dp),
                    singleLine = true
                )

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
                                    alignment = when (vt) {
                                        "Byte" -> 1
                                        "Int16" -> 2
                                        "Int32" -> 4
                                        "Int64" -> 8
                                        "Float" -> 4
                                        "Double" -> 8
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
                    onClick = {
                        if (activeMap == null || pid == null) return@Button
                        coroutineScope.launch {
                            isScanning = true
                            progress = 0.1f
                            scanResults.clear()
                            
                            val vtVal = when (scanValueType) {
                                "Byte" -> 0
                                "Int16" -> 2
                                "Int32" -> 4
                                "Int64" -> 6
                                "Float" -> 8
                                "Double" -> 9
                                else -> 4
                            }
                            
                            val ctVal = when (scanCompareType) {
                                "ExactValue" -> 0
                                "BiggerThan" -> 2
                                "SmallerThan" -> 3
                                else -> 0
                            }

                            val bytes = scanValueToBytes(scanValue, scanValueType)
                            if (bytes == null) {
                                DebuggerService.log("SCAN", "Invalid value format", DebuggerService.LogEntry.Level.ERROR)
                                isScanning = false
                                return@launch
                            }

                            withContext(Dispatchers.IO) {
                                try {
                                    DebuggerService.log("SCAN", "Starting scan in range 0x${activeMap.start.toString(16)} to 0x${activeMap.end.toString(16)}", DebuggerService.LogEntry.Level.INFO)
                                    
                                    val turboFlags = ProtocolConstants.TS_SERVER_RESIDENT
                                    val startPayload = BinaryBuffer(27 + bytes.size).apply {
                                        writeInt(pid)
                                        writeLong(activeMap.start)
                                        writeInt((activeMap.end - activeMap.start).toInt())
                                        writeByte(vtVal.toByte())
                                        writeByte(ctVal.toByte())
                                        writeByte(alignment.toByte())
                                        writeInt(bytes.size)
                                        writeInt(turboFlags)
                                        writeBytes(bytes)
                                    }.bytes

                                    client.connection.execute(readTimeoutMs = ScanReadTimeoutMs) { inStr, outStr ->
                                        DebuggerService.log("SCAN", "Using turbo server-resident scan", DebuggerService.LogEntry.Level.INFO)
                                        client.connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_TURBOSCAN_START, startPayload)
                                        var status = client.connection.receiveStatus(inStr)
                                        if (status != ProtocolConstants.CMD_SUCCESS) {
                                            throw java.io.IOException("Turbo scan start failed status: 0x${status.toString(16)}")
                                        }

                                        // Read ack of data
                                        status = client.connection.receiveStatus(inStr)
                                        if (status != ProtocolConstants.CMD_SUCCESS) {
                                            throw java.io.IOException("Turbo scan start ack failed")
                                        }

                                        val valSize = bytes.size
                                        val summaryBytes = client.connection.readExactly(inStr, 12)
                                        val summaryBuf = BinaryBuffer(summaryBytes)
                                        val residentStored = summaryBuf.readInt()
                                        val residentCount = summaryBuf.readLong()

                                        if (residentStored == 1) {
                                            totalMatchesCount = residentCount
                                            status = client.connection.receiveStatus(inStr)
                                            if (status != ProtocolConstants.CMD_SUCCESS) {
                                                throw java.io.IOException("Turbo scan final status failed")
                                            }

                                            val getCount = residentCount.coerceAtMost(1000L).toInt()
                                            if (getCount > 0) {
                                                val getPayload = BinaryBuffer(12).apply {
                                                    writeInt(0)
                                                    writeInt(getCount)
                                                    writeInt(0)
                                                }.bytes
                                                client.connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_TURBOSCAN_GET, getPayload)
                                                status = client.connection.receiveStatus(inStr)
                                                if (status != ProtocolConstants.CMD_SUCCESS) {
                                                    throw java.io.IOException("Turbo scan get failed")
                                                }

                                                val header = BinaryBuffer(client.connection.readExactly(inStr, 4)).readInt()
                                                val actualCount = header and 0x7FFFFFFF
                                                val hasFirstValue = (header and Int.MIN_VALUE) != 0
                                                val recordSize = 8 + valSize * if (hasFirstValue) 3 else 2
                                                val records = client.connection.readExactly(inStr, actualCount * recordSize)
                                                val recordsBuf = BinaryBuffer(records)
                                                repeat(actualCount) {
                                                    val absoluteAddress = recordsBuf.readLong()
                                                    val currentValue = recordsBuf.readBytes(valSize)
                                                    recordsBuf.readBytes(valSize)
                                                    if (hasFirstValue) recordsBuf.readBytes(valSize)
                                                    scanResults.add(Ps5ScanResult(absoluteAddress - activeMap.start, currentValue))
                                                }

                                                status = client.connection.receiveStatus(inStr)
                                                if (status != ProtocolConstants.CMD_SUCCESS) {
                                                    throw java.io.IOException("Turbo scan get final status failed")
                                                }
                                            }
                                        } else {
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
                                                }
                                            }
                                            totalMatchesCount = count
                                            client.connection.receiveStatus(inStr)
                                        }
                                        DebuggerService.log("SCAN", "Scan finished. Found $totalMatchesCount matches.", DebuggerService.LogEntry.Level.INFO)
                                    }
                                    isRescanMode = true
                                } catch (e: Exception) {
                                    DebuggerService.log("SCAN", "Scan failed: ${e.message}", DebuggerService.LogEntry.Level.ERROR)
                                }
                            }
                            isScanning = false
                        }
                    },
                    enabled = isConnected && activeMap != null && !isScanning
                ) {
                    Text("First Scan")
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

        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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
            Text("Offset Address", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f).padding(start = 8.dp))
            Text("Value", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(scanResults) { res ->
                val absoluteAddress = (activeMap?.start ?: 0L) + res.offset
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(String.format("0x%X (+0x%X)", absoluteAddress, res.offset), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(2f).padding(start = 8.dp))
                    Text(parseValueBytes(res.value, scanValueType), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(2f))
                }
            }
        }
    }
}

private fun scanValueToBytes(valueStr: String, type: String): ByteArray? {
    val buf = BinaryBuffer(8)
    try {
        when (type) {
            "Byte" -> buf.writeByte(valueStr.toByte())
            "Int16" -> buf.writeShort(valueStr.toShort())
            "Int32" -> buf.writeInt(valueStr.toInt())
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
