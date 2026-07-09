package com.osr.ps5debugger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import com.osr.ps5debugger.util.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StringItem(
    val address: Long,
    val type: String,
    val length: Int,
    val value: String,
    val refs: List<Long>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StringsView(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    instructions: List<DisasmLine> = emptyList(),
    onJumpToAddress: (Long) -> Unit,
    onJumpToHex: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val pid = activeProcess?.pid

    var minLengthText by remember { mutableStateOf("4") }
    var searchText by remember { mutableStateOf("") }
    
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    
    val stringItems = remember { mutableStateListOf<StringItem>() }

    val filteredItems = remember(stringItems.size, searchText) {
        if (searchText.isEmpty()) {
            stringItems.toList()
        } else {
            stringItems.filter { item ->
                item.value.contains(searchText, ignoreCase = true) ||
                item.address.toString(16).contains(searchText, ignoreCase = true) ||
                "0x${item.address.toString(16)}".contains(searchText, ignoreCase = true)
            }
        }
    }

    // Auto-trigger scan reactively
    LaunchedEffect(activeMap, activeMaps.size, isConnected, pid, minLengthText) {
        val targets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        if (isConnected && pid != null && targets.isNotEmpty()) {
            isScanning = true
            scanProgress = 0f
            statusMessage = "Reading memory..."
            stringItems.clear()
            
            val minLen = minLengthText.toIntOrNull()?.coerceAtLeast(1) ?: 4
            
            withContext(Dispatchers.Default) {
                val client = AppContainer.clientAdapter.client
                val allItems = mutableListOf<StringItem>()
                
                var totalSize = targets.sumOf { it.size }
                var processedSize = 0L
                
                for (map in targets) {
                    if (map.size <= 0) continue
                    val readChunkSize = 256 * 1024 // Read in 256KB chunks
                    var offset = 0L
                    
                    while (offset < map.size) {
                        val remaining = map.size - offset
                        val toRead = minOf(readChunkSize.toLong(), remaining).toInt()
                        
                        val startAddr = map.start + offset
                        try {
                            val chunkBytes = client.readMemory(pid, startAddr, toRead)
                            if (chunkBytes.isNotEmpty()) {
                                // Parse ASCII strings
                                var i = 0
                                val n = chunkBytes.size
                                while (i < n) {
                                    var len = 0
                                    while (i + len < n && chunkBytes[i + len].toInt() and 0xFF in 32..126) {
                                        len++
                                    }
                                    if (len >= minLen) {
                                        val strVal = String(chunkBytes, i, len, Charsets.US_ASCII)
                                        val addr = startAddr + i
                                        
                                        // Calculate refs
                                        val refs = mutableListOf<Long>()
                                        for (line in instructions) {
                                            if (line.instr.ripRelTarget == addr) {
                                                refs.add(line.instr.addr)
                                                continue
                                            }
                                            val target = DisasmFormatter.getJumpTarget(line.instr, line.bytes)
                                            if (target == addr) {
                                                refs.add(line.instr.addr)
                                            }
                                        }
                                        
                                        allItems.add(StringItem(addr, "ASCII", len, strVal, refs))
                                        i += len
                                    } else {
                                        i++
                                    }
                                }
                                
                                // Parse UTF-16 (Unicode) strings
                                i = 0
                                while (i < n - 1) {
                                    var len = 0
                                    while (i + len * 2 + 1 < n) {
                                        val b1 = chunkBytes[i + len * 2].toInt() and 0xFF
                                        val b2 = chunkBytes[i + len * 2 + 1].toInt() and 0xFF
                                        if (b2 == 0 && b1 in 32..126) {
                                            len++
                                        } else {
                                            break
                                        }
                                    }
                                    if (len >= minLen) {
                                        val strBytes = ByteArray(len * 2)
                                        System.arraycopy(chunkBytes, i, strBytes, 0, len * 2)
                                        val strVal = String(strBytes, Charsets.UTF_16LE)
                                        val addr = startAddr + i
                                        
                                        // Calculate refs
                                        val refs = mutableListOf<Long>()
                                        for (line in instructions) {
                                            if (line.instr.ripRelTarget == addr) {
                                                refs.add(line.instr.addr)
                                                continue
                                            }
                                            val target = DisasmFormatter.getJumpTarget(line.instr, line.bytes)
                                            if (target == addr) {
                                                refs.add(line.instr.addr)
                                            }
                                        }
                                        
                                        allItems.add(StringItem(addr, "UTF-16", len, strVal, refs))
                                        i += len * 2
                                    } else {
                                        i++
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        
                        offset += toRead
                        processedSize += toRead
                        scanProgress = (processedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                    }
                }
                
                allItems.sortBy { it.address }
                withContext(Dispatchers.Main) {
                    stringItems.addAll(allItems)
                    statusMessage = "Found ${stringItems.size} strings."
                }
            }
            isScanning = false
        } else {
            stringItems.clear()
            statusMessage = "Not connected or no memory region selected."
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = minLengthText,
                onValueChange = { minLengthText = it },
                label = { Text("Min Length", fontSize = 11.sp, color = PS5ThemeColors.TextMuted) },
                modifier = Modifier.width(120.dp).height(48.dp),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = PS5ThemeColors.AccentCyan,
                    unfocusedBorderColor = PS5ThemeColors.BorderColor,
                    containerColor = PS5ThemeColors.Surface,
                    focusedTextColor = PS5ThemeColors.TextMain,
                    unfocusedTextColor = PS5ThemeColors.TextMain
                ),
                shape = RoundedCornerShape(4.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search / filter strings instantly...", fontSize = 11.sp, color = PS5ThemeColors.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Filter", tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = PS5ThemeColors.AccentCyan,
                    unfocusedBorderColor = PS5ThemeColors.BorderColor,
                    containerColor = PS5ThemeColors.Surface,
                    focusedTextColor = PS5ThemeColors.TextMain,
                    unfocusedTextColor = PS5ThemeColors.TextMain
                ),
                shape = RoundedCornerShape(4.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )
        }

        if (isScanning) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = PS5ThemeColors.AccentCyan
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${statusMessage} (${(scanProgress * 100).toInt()}%)",
                    fontSize = 11.sp,
                    color = PS5ThemeColors.TextMuted
                )
            }
        } else if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                fontSize = 11.sp,
                color = PS5ThemeColors.TextMuted,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Address", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PS5ThemeColors.TextMain, modifier = Modifier.width(140.dp))
            Text("Type", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PS5ThemeColors.TextMain, modifier = Modifier.width(80.dp))
            Text("Length", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PS5ThemeColors.TextMain, modifier = Modifier.width(70.dp))
            Text("Refs", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PS5ThemeColors.TextMain, modifier = Modifier.width(180.dp))
            Text("Value", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PS5ThemeColors.TextMain, modifier = Modifier.weight(1f))
        }

        // Table Body
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isScanning) "Scanning..." else "No strings found.",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredItems) { item ->
                    var showMenu by remember { mutableStateOf(false) }
                    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onDoubleClick = { onJumpToHex(item.address) }
                                )
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Press) {
                                                if (event.buttons.isSecondaryPressed) {
                                                    menuOffset = DpOffset(event.changes.first().position.x.dp / 2f, event.changes.first().position.y.dp / 2f)
                                                    showMenu = true
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "0x${item.address.toString(16).uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = PS5ThemeColors.TextMain,
                                modifier = Modifier.width(140.dp)
                            )
                            Text(
                                text = item.type,
                                fontSize = 11.sp,
                                color = PS5ThemeColors.AccentCyan,
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                text = item.length.toString(),
                                fontSize = 11.sp,
                                color = PS5ThemeColors.TextMain,
                                modifier = Modifier.width(70.dp)
                            )
                            
                            val refsText = when {
                                item.refs.isEmpty() -> "--"
                                item.refs.size == 1 -> "0x${item.refs[0].toString(16).uppercase()}"
                                else -> "${item.refs.size} refs: " + item.refs.joinToString(", ") { "0x${it.toString(16).uppercase()}" }
                            }
                            Text(
                                text = refsText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (item.refs.isNotEmpty()) Color(0xFFFFD54F) else PS5ThemeColors.TextMuted,
                                modifier = Modifier.width(180.dp),
                                maxLines = 1
                            )
                            Text(
                                text = item.value,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = PS5ThemeColors.TextMain,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Jump to in Hex Viewer", color = PS5ThemeColors.TextMain) },
                                onClick = {
                                    onJumpToHex(item.address)
                                    showMenu = false
                                }
                            )
                            if (item.refs.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Jump to Reference in Disassembly", color = PS5ThemeColors.TextMain) },
                                    onClick = {
                                        onJumpToAddress(item.refs.first())
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Copy Address", color = PS5ThemeColors.TextMain) },
                                onClick = {
                                    copyToClipboard(String.format("0x%X", item.address))
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Value", color = PS5ThemeColors.TextMain) },
                                onClick = {
                                    copyToClipboard(item.value)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
