package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.protocol.GpRegs
import com.osr.ps5debugger.protocol.DbRegs
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.util.copyToClipboard
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import kotlinx.coroutines.launch

data class DisasmLine(
    val instr: Ps5DisasmInstr,
    val bytes: ByteArray
)

@Composable
fun ByteHexCellCompact(byte: Byte) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(20.dp)
            .background(Color.Transparent)
            .border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%02X", byte),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.LightGray
        )
    }
}

@Composable
fun ByteAsciiCellCompact(byte: Byte) {
    val charStr = if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else "."
    Box(
        modifier = Modifier.width(9.dp).height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = charStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.Green
        )
    }
}

@Composable
fun DisassemblyViewer(
    activeMap: MemoryRange?,
    instructions: androidx.compose.runtime.snapshots.SnapshotStateList<DisasmLine>,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    selectionStart: Long? = null,
    selectionEnd: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null,
    onJumpToAddress: ((Long) -> Unit)? = null,
    onJumpToHex: ((Long) -> Unit)? = null,
    showHexDetails: Boolean = false,
    isLoading: Boolean = false,
    isAttached: Boolean,
    activeBreakpoints: MutableMap<Int, Long>,
    activeWatchpoints: MutableMap<Int, Long>,
    functionAddresses: Set<Long> = emptySet()
) {
    val coroutineScope = rememberCoroutineScope()
    val client = AppContainer.clientAdapter.client
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    
    var goToAddressText by remember { mutableStateOf("") }
    
    // Context Menu & Watchpoint Setup Dialog
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuBytes by remember { mutableStateOf(byteArrayOf()) }
    var contextMenuDisasm by remember { mutableStateOf("") }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    var showWatchpointDialog by remember { mutableStateOf(false) }
    var watchpointSlot by remember { mutableStateOf(0) }
    var watchpointType by remember { mutableStateOf(1) } // 1 = Write, 3 = Read/Write
    var watchpointSize by remember { mutableStateOf(1) } // 1, 2, 4, 8 bytes

    val listState = rememberLazyListState()
    
    var selectionAnchor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectionStart) {
        if (selectionStart != null && selectionAnchor != null) {
            val diff = kotlin.math.abs(selectionStart - selectionAnchor!!)
            if (diff > 15) {
                selectionAnchor = selectionStart
            }
        } else {
            selectionAnchor = selectionStart
        }
    }
    
    // Auto-update scrolling when jumpToAddress is requested
    LaunchedEffect(activeMap, jumpToAddress, selectionStart, selectionEnd) {
        if (activeMap != null) {
            val target = jumpToAddress
            if (target != null && target >= activeMap.start && target < activeMap.end) {
                // Determine if this jump is external or internal selection click
                val isLocalSelection = target == selectionStart || target == selectionEnd
                if (!isLocalSelection) {
                    goToAddressText = target.toString(16).uppercase()
                    
                    // Try to find the item in our loaded list and scroll to it
                    val index = instructions.indexOfFirst { it.instr.addr == target }
                    if (index != -1) {
                        try {
                            listState.animateScrollToItem(index)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    
    // Infinite Scroll & Bidirectional Loading
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    // Load next block (scrolling down)
    LaunchedEffect(firstVisibleIndex, instructions.size, isLoading, isConnected) {
        val pid = activeProcess?.pid
        if (!isLoading && activeMap != null && pid != null && instructions.isNotEmpty() && isConnected) {
            if (firstVisibleIndex + 50 >= instructions.size) {
                val lastLine = instructions.last()
                val nextStart = lastLine.instr.addr + lastLine.instr.length
                if (nextStart < activeMap.end) {
                    val nextLen = minOf(32768L, activeMap.end - nextStart).toInt()
                    if (nextLen > 0) {
                        try {
                            val newInstrs = client.disassembleRegion(pid, nextStart, nextLen, 200)
                            val rawBytes = try {
                                client.readMemory(pid, nextStart, nextLen)
                            } catch (_: Exception) {
                                ByteArray(0)
                            }
                            
                            val newLines = newInstrs.map { instr ->
                                val offset = (instr.addr - nextStart).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                    rawBytes.copyOfRange(offset, offset + instr.length)
                                } else {
                                    ByteArray(0)
                                }
                                DisasmLine(instr, instrBytes)
                            }
                            
                            instructions.addAll(newLines)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // Prepend previous block (scrolling up)
    LaunchedEffect(firstVisibleIndex, isLoading, isConnected) {
        val pid = activeProcess?.pid
        if (!isLoading && activeMap != null && pid != null && instructions.isNotEmpty() && isConnected) {
            if (firstVisibleIndex <= 5) {
                val firstLine = instructions.first()
                if (firstLine.instr.addr > activeMap.start) {
                    val prevStart = maxOf(activeMap.start, firstLine.instr.addr - 32768L)
                    val prevLen = (firstLine.instr.addr - prevStart).toInt()
                    if (prevLen > 0) {
                        try {
                            val newInstrs = client.disassembleRegion(pid, prevStart, prevLen, 200)
                            val rawBytes = try {
                                client.readMemory(pid, prevStart, prevLen)
                            } catch (_: Exception) {
                                ByteArray(0)
                            }
                            
                            val newLines = newInstrs.map { instr ->
                                val offset = (instr.addr - prevStart).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                    rawBytes.copyOfRange(offset, offset + instr.length)
                                } else {
                                    ByteArray(0)
                                }
                                DisasmLine(instr, instrBytes)
                            }
                            
                            if (newLines.isNotEmpty()) {
                                instructions.addAll(0, newLines)
                                // Offset listState index to prevent scrolling jumps
                                try {
                                    listState.scrollToItem(
                                        listState.firstVisibleItemIndex + newLines.size,
                                        listState.firstVisibleItemScrollOffset
                                    )
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Navigation Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = goToAddressText,
                onValueChange = { goToAddressText = it },
                label = { Text("Go to Address (Hex)") },
                modifier = Modifier.width(180.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PS5ThemeColors.AccentCyan,
                    unfocusedBorderColor = PS5ThemeColors.BorderColor,
                    focusedLabelColor = PS5ThemeColors.AccentCyan,
                    unfocusedLabelColor = PS5ThemeColors.TextMuted
                )
            )
            
            Button(
                onClick = {
                    val addr = goToAddressText.trim().toLongOrNull(16)
                    if (activeMap != null && addr != null && addr in activeMap.start..activeMap.end) {
                        onJumpToAddress?.invoke(addr)
                    } else {
                        AppContainer.debuggerUseCase.log("DISASM", "Address out of range or invalid", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PS5ThemeColors.AccentCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Go", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Spacer(Modifier.weight(1f))
            
            // Prev/Next page navigation (shifts the viewing window backwards or forwards)
            Button(
                onClick = {
                    if (activeMap != null) {
                        val currentTop = instructions.firstOrNull()?.instr?.addr ?: jumpToAddress ?: activeMap.start
                        val prevAddr = maxOf(activeMap.start, currentTop - 8192)
                        onJumpToAddress?.invoke(prevAddr)
                        goToAddressText = prevAddr.toString(16).uppercase()
                    }
                },
                enabled = activeMap != null && (instructions.firstOrNull()?.instr?.addr ?: activeMap.start) > activeMap.start,
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) {
                Text("Prev Page", color = PS5ThemeColors.TextMain)
            }
            
            Button(
                onClick = {
                    if (activeMap != null && instructions.isNotEmpty()) {
                        val lastLine = instructions.last()
                        val nextAddr = lastLine.instr.addr + lastLine.instr.length
                        if (nextAddr < activeMap.end) {
                            onJumpToAddress?.invoke(nextAddr)
                            goToAddressText = nextAddr.toString(16).uppercase()
                        }
                    }
                },
                enabled = activeMap != null && instructions.isNotEmpty() && (instructions.last().instr.addr + instructions.last().instr.length) < activeMap.end,
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) {
                Text("Next Page", color = PS5ThemeColors.TextMain)
            }
        }
        
        if (activeMap == null || activeProcess == null) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Select a Process and Virtual Memory Map to disassemble", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (isLoading && instructions.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else {
            // Main Disassembly List
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PS5ThemeColors.Surface, RoundedCornerShape(4.dp))
                        .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    itemsIndexed(instructions) { idx, line ->
                        val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                        val hasBreakpoint = activeBreakpoints.values.contains(line.instr.addr) || activeWatchpoints.values.contains(line.instr.addr)
                        val isFunctionStart = functionAddresses.contains(line.instr.addr)

                        Column {
                            if (isFunctionStart) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "sub_${line.instr.addr.toString(16).uppercase()}:",
                                    color = Color(0xFF64FFDA),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (hasBreakpoint) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(8.dp)
                                            .background(Color.Red, RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Spacer(Modifier.width(12.dp))
                                }

                                DisasmRow(
                                    line = line,
                                    isSelected = isSelected,
                                    isFunctionStart = isFunctionStart,
                                    onAddressClicked = { addr, len, isShift ->
                                        val firstAddr = instructions.firstOrNull()?.instr?.addr
                                        val lastAddr = instructions.lastOrNull()?.let { it.instr.addr + it.instr.length }
                                        val isAnchorInLoadedRange = selectionAnchor != null && firstAddr != null && lastAddr != null &&
                                                selectionAnchor!! >= firstAddr && selectionAnchor!! <= lastAddr

                                        if (isShift && isAnchorInLoadedRange) {
                                            val anchor = selectionAnchor!!
                                            if (addr >= anchor) {
                                                onSelectionChanged?.invoke(anchor, addr + len - 1)
                                            } else {
                                                val anchorInstr = instructions.firstOrNull { it.instr.addr == anchor }
                                                val anchorLen = anchorInstr?.instr?.length ?: 1
                                                onSelectionChanged?.invoke(anchor + anchorLen - 1, addr)
                                            }
                                        } else {
                                            selectionAnchor = addr
                                            onSelectionChanged?.invoke(addr, addr + len - 1)
                                        }
                                    },
                                    onAddressRightClicked = { addr, bytes, disasmText, offset ->
                                        contextMenuAddr = addr
                                        contextMenuBytes = bytes
                                        contextMenuDisasm = disasmText
                                        contextMenuOffset = offset
                                        showContextMenu = true
                                    },
                                    showHexDetails = showHexDetails
                                )
                            }
                        }
                        
                        val isMenuForThisRow = showContextMenu && contextMenuAddr == line.instr.addr
                            DropdownMenu(
                                expanded = isMenuForThisRow,
                                onDismissRequest = { showContextMenu = false },
                                offset = contextMenuOffset
                            ) {
                                val addr = contextMenuAddr
                                if (addr != null) {
                                    if (isAttached) { // Only show breakpoint options if debugger is attached!
                                        val activeBpIndex = activeBreakpoints.entries.firstOrNull { it.value == addr }?.key
                                        if (activeBpIndex != null) {
                                            DropdownMenuItem(
                                                text = { Text("Remove Breakpoint", color = Color.Red, fontSize = 12.sp) },
                                                onClick = {
                                                    coroutineScope.launch {
                                                        try {
                                                            activeBreakpoints.remove(activeBpIndex)
                                                            val ok = client.setBreakpoint(activeBpIndex, false, addr)
                                                            if (!ok) {
                                                                activeBreakpoints[activeBpIndex] = addr
                                                                AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove breakpoint on PS5", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                            } else {
                                                                AppContainer.debuggerUseCase.log("DEBUGGER", "Removed breakpoint at 0x${addr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                            }
                                                        } catch (e: Exception) {
                                                            activeBreakpoints[activeBpIndex] = addr
                                                            AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove breakpoint: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                        }
                                                    }
                                                    showContextMenu = false
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Set Software Breakpoint", fontSize = 12.sp) },
                                                onClick = {
                                                    coroutineScope.launch {
                                                        try {
                                                            val freeIndex = (0..29).firstOrNull { !activeBreakpoints.containsKey(it) }
                                                            if (freeIndex != null) {
                                                                activeBreakpoints[freeIndex] = addr
                                                                val ok = client.setBreakpoint(freeIndex, true, addr)
                                                                if (!ok) {
                                                                    activeBreakpoints.remove(freeIndex)
                                                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to set breakpoint on PS5", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                                } else {
                                                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Set Software Breakpoint in slot $freeIndex at 0x${addr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                                }
                                                            } else {
                                                                AppContainer.debuggerUseCase.log("DEBUGGER", "No software breakpoint slots remaining! (Max 30)", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                                                            }
                                                        } catch (e: Exception) {
                                                            val indexToRemove = activeBreakpoints.entries.firstOrNull { it.value == addr }?.key
                                                            if (indexToRemove != null) activeBreakpoints.remove(indexToRemove)
                                                            AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to set breakpoint: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                        }
                                                    }
                                                    showContextMenu = false
                                                }
                                            )
                                        }
                                        
                                        val activeWpSlot = activeWatchpoints.entries.firstOrNull { it.value == addr }?.key
                                        if (activeWpSlot != null) {
                                            DropdownMenuItem(
                                                text = { Text("Remove Watchpoint", color = Color.Red, fontSize = 12.sp) },
                                                onClick = {
                                                    coroutineScope.launch {
                                                        try {
                                                            activeWatchpoints.remove(activeWpSlot)
                                                            val ok = client.setWatchpoint(activeWpSlot, false, 1, 1, addr)
                                                            if (!ok) {
                                                                activeWatchpoints[activeWpSlot] = addr
                                                                AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove watchpoint on PS5", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                            } else {
                                                                AppContainer.debuggerUseCase.log("DEBUGGER", "Removed hardware watchpoint at 0x${addr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                            }
                                                        } catch (e: Exception) {
                                                            activeWatchpoints[activeWpSlot] = addr
                                                            AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove watchpoint: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                        }
                                                    }
                                                    showContextMenu = false
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Set Hardware Watchpoint...", fontSize = 12.sp) },
                                                onClick = {
                                                    showWatchpointDialog = true
                                                    showContextMenu = false
                                                }
                                            )
                                        }
                                        HorizontalDivider(color = PS5ThemeColors.BorderColor)
                                    }
                                }
                                
                                DropdownMenuItem(
                                    text = { Text("Copy Address", fontSize = 12.sp) },
                                    onClick = {
                                        if (contextMenuAddr != null) {
                                            copyToClipboard(String.format("0x%012X", contextMenuAddr))
                                        }
                                        showContextMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy Hex Bytes", fontSize = 12.sp) },
                                    onClick = {
                                        if (contextMenuBytes.isNotEmpty()) {
                                            copyToClipboard(contextMenuBytes.joinToString(" ") { String.format("%02X", it) })
                                        }
                                        showContextMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy Disassembly", fontSize = 12.sp) },
                                    onClick = {
                                        if (contextMenuDisasm.isNotEmpty()) {
                                            copyToClipboard(contextMenuDisasm)
                                        }
                                        showContextMenu = false
                                    }
                                )
                                if (onJumpToHex != null && contextMenuAddr != null) {
                                    HorizontalDivider(color = PS5ThemeColors.BorderColor)
                                    DropdownMenuItem(
                                        text = { Text("Jump to Hex Viewer", fontSize = 12.sp, color = PS5ThemeColors.AccentCyan) },
                                        onClick = {
                                            onJumpToHex(contextMenuAddr!!)
                                            showContextMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Set Hardware Watchpoint Configuration Modal
        if (showWatchpointDialog) {
            val addr = contextMenuAddr
            if (addr != null) {
                AlertDialog(
                    onDismissRequest = { showWatchpointDialog = false },
                    title = { Text("Configure Hardware Watchpoint", color = PS5ThemeColors.AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Address: 0x${addr.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            
                            // Slot selection
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("DR Slot: ", modifier = Modifier.width(60.dp), fontSize = 12.sp)
                                (0..3).forEach { slot ->
                                    FilterChip(
                                        selected = watchpointSlot == slot,
                                        onClick = { watchpointSlot = slot },
                                        label = { Text("DR$slot", fontSize = 11.sp) },
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                            
                            // Watch Type selection
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Type: ", modifier = Modifier.width(60.dp), fontSize = 12.sp)
                                FilterChip(
                                    selected = watchpointType == 1,
                                    onClick = { watchpointType = 1 },
                                    label = { Text("Write", fontSize = 11.sp) },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                  )
                                FilterChip(
                                    selected = watchpointType == 3,
                                    onClick = { watchpointType = 3 },
                                    label = { Text("Read/Write", fontSize = 11.sp) },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                            
                            // Granularity selection
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size: ", modifier = Modifier.width(60.dp), fontSize = 12.sp)
                                listOf(1, 2, 4, 8).forEach { sz ->
                                    FilterChip(
                                        selected = watchpointSize == sz,
                                        onClick = { watchpointSize = sz },
                                        label = { Text("${sz}B", fontSize = 11.sp) },
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val lenField = when (watchpointSize) {
                                            1 -> 0
                                            2 -> 1
                                            8 -> 2
                                            else -> 3 // 4 bytes
                                        }
                                        activeWatchpoints[watchpointSlot] = addr
                                        val ok = client.setWatchpoint(watchpointSlot, true, lenField, watchpointType, addr)
                                        if (!ok) {
                                            activeWatchpoints.remove(watchpointSlot)
                                            AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to set watchpoint on PS5", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                        } else {
                                            AppContainer.debuggerUseCase.log("DEBUGGER", "Set Hardware Watchpoint in slot DR$watchpointSlot at 0x${addr.toString(16)} (size: ${watchpointSize}B, type: $watchpointType)", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                        }
                                    } catch (e: Exception) {
                                        activeWatchpoints.remove(watchpointSlot)
                                        AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to set watchpoint: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                    }
                                }
                                showWatchpointDialog = false
                            }
                        ) {
                            Text("Apply", color = PS5ThemeColors.AccentCyan, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWatchpointDialog = false }) {
                            Text("Cancel", color = PS5ThemeColors.TextMuted)
                        }
                    },
                    containerColor = PS5ThemeColors.SecondaryBg
                )
            }
        }
    }
private fun isInstructionSelected(instr: Ps5DisasmInstr, start: Long?, end: Long?): Boolean {
    if (start == null || end == null) return false
    val lo = minOf(start, end)
    val hi = maxOf(start, end)
    val instrEnd = instr.addr + instr.length - 1
    return instrEnd >= lo && instr.addr <= hi
}

@Composable
fun DisasmRow(
    line: DisasmLine,
    isSelected: Boolean,
    isFunctionStart: Boolean = false,
    onAddressClicked: (Long, Int, Boolean) -> Unit,
    onAddressRightClicked: (Long, ByteArray, String, DpOffset) -> Unit,
    showHexDetails: Boolean = false
) {
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val instr = line.instr
    val mnemonic = DisasmFormatter.getMnemonic(instr)
    val operands = DisasmFormatter.formatOperands(instr)
    val infoText = DisasmFormatter.getInfoText(instr)
    
    // Ghidra Dark Theme Colors
    val addressColor = Color(0xFF90A4AE)      // Gray-blue lavender
    val byteColor = Color(0xFF808080)         // Dark Gray
    val mnemonicColor = when {
        instr.isCall || instr.isRet -> Color(0xFFF50057) // Bright Pink-red for call/ret
        instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081) // Pink for branches
        else -> Color(0xFFFFB74D) // Ghidra Orange-Gold for standard instructions
    }
    val commentColor = Color(0xFF78909C)       // Muted gray-green comment color
    
    val bytesStr = line.bytes.joinToString(" ") { String.format("%02X", it) }
    val fullDisasmString = "$mnemonic $operands"
 
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> PS5ThemeColors.AccentCyan.copy(alpha = 0.35f)
                    isFunctionStart -> Color(0xFF2D2D2D)
                    else -> Color.Transparent
                }
            )
            .pointerInput(instr.addr) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            val change = event.changes.first()
                            val isShift = event.keyboardModifiers.isShiftPressed || windowInfo.keyboardModifiers.isShiftPressed
                            val isSecondary = event.buttons.isSecondaryPressed
                            
                            if (isSecondary) {
                                val density = this.density
                                val offset = DpOffset(change.position.x.dp / density, change.position.y.dp / density)
                                onAddressRightClicked(instr.addr, line.bytes, fullDisasmString, offset)
                            } else {
                                onAddressClicked(instr.addr, instr.length, isShift)
                            }
                        }
                    }
                }
            }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Address
        Text(
            text = String.format("%012X", instr.addr),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = addressColor,
            modifier = Modifier.width(110.dp)
        )
        
        if (!showHexDetails) {
            // Raw hex bytes string (standard mode)
            Text(
                text = bytesStr,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = byteColor,
                modifier = Modifier.width(130.dp),
                maxLines = 1
            )
        }
        
        // Mnemonic
        Text(
            text = mnemonic,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = mnemonicColor,
            modifier = Modifier.width(75.dp)
        )
        
        // Operands with Ghidra syntax highlight
        Box(modifier = Modifier.width(180.dp)) {
            val annotatedOps = buildAnnotatedString {
                val regex = Regex("([\\[\\]\\+\\-\\*\\,\\s+])|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
                val matches = regex.findAll(operands)
                for (match in matches) {
                    val token = match.value
                    val isReg = DisasmFormatter.regNames.contains(token.lowercase())
                    val isNumber = token.startsWith("0x") || token.all { it.isDigit() }
                    
                    when {
                        isReg -> {
                            withStyle(style = SpanStyle(color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)) { // Teal/cyan for registers
                                append(token)
                            }
                        }
                        isNumber -> {
                            withStyle(style = SpanStyle(color = Color(0xFFFF8A65))) { // Orange/Red for numbers
                                append(token)
                            }
                        }
                        token == "[" || token == "]" -> {
                            withStyle(style = SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)) { // Yellow brackets
                                append(token)
                            }
                        }
                        else -> {
                            withStyle(style = SpanStyle(color = Color(0xFFECEFF1))) { // Muted off-white
                                append(token)
                            }
                        }
                    }
                }
                if (matches.none()) {
                    append(operands)
                }
            }
            Text(
                text = annotatedOps,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        
        if (showHexDetails) {
            Spacer(Modifier.width(16.dp))
            
            // Unified Hex cells (interactive block styling)
            Row(
                modifier = Modifier.width(180.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (i in 0 until 8) { // show up to 8 bytes in cells on desktop split view
                    if (i < line.bytes.size) {
                        ByteHexCellCompact(byte = line.bytes[i])
                    } else {
                        Spacer(Modifier.width(20.dp))
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Unified ASCII cells
            Row(
                modifier = Modifier.width(80.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (i in 0 until 8) {
                    if (i < line.bytes.size) {
                        ByteAsciiCellCompact(byte = line.bytes[i])
                    } else {
                        Spacer(Modifier.width(9.dp))
                    }
                }
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        // Info / Comments (target, xref etc)
        if (infoText.isNotEmpty()) {
            Text(
                text = " ; $infoText",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = commentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
