package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.util.copyToClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import com.osr.ps5debugger.ui.disasm.DisassemblyState

data class DisasmLine(
    val instr: Ps5DisasmInstr,
    val bytes: ByteArray,
    val region: MemoryRange? = null
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

private fun isInstructionSelected(instr: Ps5DisasmInstr, start: Long?, end: Long?): Boolean {
    if (start == null || end == null) return false
    val lo = minOf(start, end)
    val hi = maxOf(start, end)
    val instrEnd = instr.addr + instr.length - 1
    return instrEnd >= lo && instr.addr <= hi
}

@Composable
fun DisassemblyViewer(
    state: DisassemblyState,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    onJumpToAddress: ((Long) -> Unit)? = null,
    onJumpToHex: ((Long) -> Unit)? = null,
    onJumpToGraph: ((Long) -> Unit)? = null,
    showHexDetails: Boolean = false,
    isLoading: Boolean = false,
    isAttached: Boolean
) {
    DisassemblyViewer(
        activeMap = state.activeMap,
        activeMaps = state.activeMaps,
        instructions = state.instructions as androidx.compose.runtime.snapshots.SnapshotStateList<DisasmLine>,
        jumpToAddress = jumpToAddress,
        modifier = modifier,
        selectionStart = state.selectionStart,
        selectionEnd = state.selectionEnd,
        onSelectionChanged = state.onSelectionChanged,
        onJumpToAddress = onJumpToAddress,
        onJumpToHex = onJumpToHex,
        onJumpToGraph = onJumpToGraph,
        showHexDetails = showHexDetails,
        isLoading = isLoading,
        isAttached = isAttached,
        activeBreakpoints = state.activeBreakpoints as MutableMap<Int, Long>,
        activeWatchpoints = state.activeWatchpoints as MutableMap<Int, Long>,
        functionAddresses = state.functionAddresses,
        activeJumps = state.activeJumps,
        jumpTracks = state.jumpTracks,
        jumpColors = state.jumpColors,
        jumpTargets = state.jumpTargets,
        onMetadataUpdateRequested = state.onMetadataUpdateRequested
    )
}

@Composable
fun DisassemblyViewer(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    instructions: androidx.compose.runtime.snapshots.SnapshotStateList<DisasmLine>,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    selectionStart: Long? = null,
    selectionEnd: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null,
    onJumpToAddress: ((Long) -> Unit)? = null,
    onJumpToHex: ((Long) -> Unit)? = null,
    onJumpToGraph: ((Long) -> Unit)? = null,
    showHexDetails: Boolean = false,
    isLoading: Boolean = false,
    isAttached: Boolean,
    activeBreakpoints: MutableMap<Int, Long>,
    activeWatchpoints: MutableMap<Int, Long>,
    functionAddresses: Set<Long> = emptySet(),
    activeJumps: List<Pair<Long, Long>> = emptyList(),
    jumpTracks: Map<Pair<Long, Long>, Int> = emptyMap(),
    jumpColors: Map<Pair<Long, Long>, Color> = emptyMap(),
    jumpTargets: Set<Long> = emptySet(),
    onMetadataUpdateRequested: (suspend () -> Unit)? = null
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
    
    LaunchedEffect(Unit) {
        val target = selectionStart
        if (target != null) {
            val index = instructions.indexOfFirst { it.instr.addr == target }
            if (index != -1) {
                try {
                    listState.scrollToItem(index)
                } catch (_: Exception) {}
            }
        }
    }
    
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
    
    var lastJumpAddress by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeMap, jumpToAddress) {
        if (activeMap != null && jumpToAddress != null && jumpToAddress != lastJumpAddress) {
            lastJumpAddress = jumpToAddress
            goToAddressText = jumpToAddress.toString(16).uppercase()
            val index = instructions.indexOfFirst { it.instr.addr == jumpToAddress }
            if (index != -1) {
                try {
                    listState.animateScrollToItem(index)
                } catch (_: Exception) {}
            }
        }
    }
    
    // Infinite Scroll & Bidirectional Loading
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    // Load next block (scrolling down)
    LaunchedEffect(firstVisibleIndex, instructions.size, isLoading, isConnected) {
        val pid = activeProcess?.pid
        if (!isLoading && pid != null && instructions.isNotEmpty() && isConnected) {
            if (firstVisibleIndex + 50 >= instructions.size) {
                val lastLine = instructions.last()
                val nextStart = lastLine.instr.addr + lastLine.instr.length
                val targetMap = if (activeMap != null && nextStart >= activeMap.start && nextStart < activeMap.end) activeMap
                               else activeMaps.firstOrNull { nextStart >= it.start && nextStart < it.end }
                
                if (targetMap != null && nextStart < targetMap.end) {
                    val nextLen = minOf(32768L, targetMap.end - nextStart).toInt()
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
                                DisasmLine(instr, instrBytes, targetMap)
                            }
                            
                            instructions.addAll(newLines)
                            onMetadataUpdateRequested?.invoke()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // Prepend previous block (scrolling up)
    LaunchedEffect(firstVisibleIndex, isLoading, isConnected) {
        val pid = activeProcess?.pid
        if (!isLoading && pid != null && instructions.isNotEmpty() && isConnected) {
            if (firstVisibleIndex <= 5) {
                val firstLine = instructions.first()
                val targetMap = if (activeMap != null && firstLine.instr.addr > activeMap.start && firstLine.instr.addr <= activeMap.end) activeMap
                               else activeMaps.firstOrNull { firstLine.instr.addr > it.start && firstLine.instr.addr <= it.end }
                               
                if (targetMap != null && firstLine.instr.addr > targetMap.start) {
                    val prevStart = maxOf(targetMap.start, firstLine.instr.addr - 32768L)
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
                                DisasmLine(instr, instrBytes, targetMap)
                            }
                            
                            if (newLines.isNotEmpty()) {
                                instructions.addAll(0, newLines)
                                onMetadataUpdateRequested?.invoke()
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
                    val targetMap = if (activeMap != null && addr != null && addr in activeMap.start..activeMap.end) activeMap
                                   else if (addr != null) activeMaps.firstOrNull { addr in it.start..it.end }
                                   else null
                                   
                    if (targetMap != null && addr != null) {
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
            
            // Prev/Next function navigation
            Button(
                onClick = {
                    val currentTop = instructions.firstOrNull()?.instr?.addr ?: jumpToAddress
                    if (currentTop != null) {
                        val prevFunc = functionAddresses.filter { it < currentTop }.maxOrNull()
                        val targetAddr = prevFunc ?: (currentTop - 8192)
                        onJumpToAddress?.invoke(targetAddr)
                        goToAddressText = targetAddr.toString(16).uppercase()
                    }
                },
                enabled = activeMap != null || activeMaps.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) {
                Text("Previous", color = PS5ThemeColors.TextMain)
            }
            
            Button(
                onClick = {
                    val currentTop = instructions.firstOrNull()?.instr?.addr ?: jumpToAddress
                    if (currentTop != null) {
                        val nextFunc = functionAddresses.filter { it > currentTop }.minOrNull()
                        val targetAddr = if (nextFunc != null) {
                            nextFunc
                        } else if (instructions.isNotEmpty()) {
                            val lastLine = instructions.last()
                            lastLine.instr.addr + lastLine.instr.length
                        } else {
                            currentTop + 8192
                        }
                        
                        onJumpToAddress?.invoke(targetAddr)
                        goToAddressText = targetAddr.toString(16).uppercase()
                    }
                },
                enabled = activeMap != null || activeMaps.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) {
                Text("Next", color = PS5ThemeColors.TextMain)
            }
        }
        
        if ((activeMap == null && activeMaps.isEmpty()) || activeProcess == null) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Select a Process and Virtual Memory Map to disassemble", style = MaterialTheme.typography.bodyLarge, color = PS5ThemeColors.TextMuted)
            }
        } else if (isLoading && instructions.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else {
            // Main Disassembly List
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val focusRequester = remember { FocusRequester() }
                val viewportWidth = maxWidth
                val isCompact = viewportWidth < 600.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isCompact) Modifier else Modifier.horizontalScroll(rememberScrollState())
                        )
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .width(if (isCompact) viewportWidth else maxOf(900.dp, viewportWidth))
                            .fillMaxHeight()
                            .background(PS5ThemeColors.Surface, RoundedCornerShape(4.dp))
                            .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                            .padding(vertical = 8.dp)
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    val currentAddr = selectionEnd ?: selectionStart
                                    val currentIndex = if (currentAddr != null) instructions.indexOfFirst { it.instr.addr == currentAddr } else -1
                                    
                                    val targetIndex = when (event.key) {
                                        Key.DirectionUp -> if (currentIndex > 0) currentIndex - 1 else -1
                                        Key.DirectionDown -> if (currentIndex < instructions.size - 1) currentIndex + 1 else -1
                                        else -> -1
                                    }
                                    
                                    if (targetIndex != -1) {
                                        val targetInstr = instructions[targetIndex].instr
                                        val targetAddr = targetInstr.addr
                                        if (event.isShiftPressed) {
                                            val start = selectionStart ?: targetAddr
                                            onSelectionChanged?.invoke(start, targetAddr + targetInstr.length - 1)
                                        } else {
                                            selectionAnchor = targetAddr
                                            onSelectionChanged?.invoke(targetAddr, targetAddr + targetInstr.length - 1)
                                        }
                                        
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                        true
                                    } else false
                                } else false
                             }
                    ) {
                    itemsIndexed(instructions) { idx, line ->
                        val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                        val hasBreakpoint = activeBreakpoints.values.contains(line.instr.addr) || activeWatchpoints.values.contains(line.instr.addr)
                        val isFunctionStart = functionAddresses.contains(line.instr.addr)

                        val prevLine = if (idx > 0) instructions[idx - 1] else null
                        val currentMap = line.region ?: activeMap
                        val prevMap = prevLine?.region ?: activeMap
                        val isNewRegionStart = idx == 0 || (currentMap != null && prevMap != null && currentMap.start != prevMap.start)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isNewRegionStart && currentMap != null) {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.5f))
                                        .border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Region: ${if (currentMap.name.isEmpty()) "unnamed" else currentMap.name} [0x${currentMap.start.toString(16).uppercase()} - 0x${currentMap.end.toString(16).uppercase()}] (${currentMap.getProtString()})",
                                        color = PS5ThemeColors.AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            val isLocalLabel = jumpTargets.contains(line.instr.addr) && !isFunctionStart

                             if (isFunctionStart) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(PS5ThemeColors.SecondaryBg)
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.osr.ps5debugger.di.AppContainer.getSymbolName(line.instr.addr, true),
                                        color = Color(0xFF64FFDA),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            } else if (isLocalLabel) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(PS5ThemeColors.SecondaryBg)
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.osr.ps5debugger.di.AppContainer.getSymbolName(line.instr.addr, false),
                                        color = Color(0xFF90A4AE),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
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

                                val maxTrack = jumpTracks.values.maxOrNull() ?: -1
                                val canvasWidth = if (maxTrack >= 0) (20 + (maxTrack + 1) * 8).dp.coerceAtMost(40.dp) else 0.dp
                                
                                if (canvasWidth > 0.dp) {
                                    Canvas(
                                        modifier = Modifier
                                            .width(canvasWidth)
                                            .height(20.dp)
                                            .padding(end = 8.dp) // Generous padding to prevent clashing with DisasmRow
                                    ) {
                                        val density = this.density
                                        val addr = line.instr.addr
                                        for ((jump, track) in jumpTracks) {
                                            val color = jumpColors[jump] ?: Color.Gray
                                            val src = jump.first
                                            val target = jump.second
                                            val minAddr = minOf(src, target)
                                            val maxAddr = maxOf(src, target)
                                            
                                            // Hybrid centered spacing: 8dp fixed spacing if there is room, otherwise scale down
                                            val startPad = 6f * density
                                            val endPad = 8f * density
                                            val availableSpace = size.width - startPad - endPad
                                            val desiredSpacing = 8f * density
                                            val totalDesiredSpace = maxTrack * desiredSpacing
                                            
                                            val lineX = if (totalDesiredSpace <= availableSpace) {
                                                val leftOffset = startPad + (availableSpace - totalDesiredSpace) / 2f
                                                leftOffset + track * desiredSpacing
                                            } else {
                                                val step = if (maxTrack > 0) availableSpace / maxTrack else 0f
                                                startPad + track * step
                                            }
                                            
                                            val lineWidth = 1.2f * density
                                            when {
                                                addr > minAddr && addr < maxAddr -> {
                                                    drawLine(color, start = Offset(lineX, 0f), end = Offset(lineX, size.height), strokeWidth = lineWidth)
                                                }
                                                addr == src -> {
                                                    val startY = size.height / 2f
                                                    val endY = if (target > src) size.height else 0f
                                                    drawLine(color, start = Offset(lineX, startY), end = Offset(lineX, endY), strokeWidth = lineWidth)
                                                    drawLine(color, start = Offset(lineX, startY), end = Offset(size.width, startY), strokeWidth = lineWidth)
                                                }
                                                addr == target -> {
                                                    val startY = if (src < target) 0f else size.height
                                                    val endY = size.height / 2f
                                                    drawLine(color, start = Offset(lineX, startY), end = Offset(lineX, endY), strokeWidth = lineWidth)
                                                    drawLine(color, start = Offset(lineX, endY), end = Offset(size.width, endY), strokeWidth = lineWidth)
                                                    
                                                    val rightX = size.width
                                                    val arrowPath = Path().apply {
                                                        moveTo(rightX, endY)
                                                        lineTo(rightX - 5f * density, endY - 3f * density)
                                                        lineTo(rightX - 5f * density, endY + 3f * density)
                                                        close()
                                                    }
                                                    drawPath(arrowPath, color)
                                                }
                                            }
                                        }
                                    }
                                }

                                DisasmRow(
                                    line = line,
                                    isSelected = isSelected,
                                    isFunctionStart = isFunctionStart,
                                    isCompact = isCompact,
                                    onAddressClicked = { addr: Long, len: Int, isShift: Boolean ->
                                        try { focusRequester.requestFocus() } catch (_: Exception) {}
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
                                                onSelectionChanged?.invoke(anchor + anchorLen - 1.toLong(), addr)
                                            }
                                        } else {
                                            selectionAnchor = addr
                                            onSelectionChanged?.invoke(addr, addr + len - 1)
                                        }
                                    },
                                    onAddressRightClicked = { addr: Long, bytes: ByteArray, disasmText: String, offset: DpOffset ->
                                        contextMenuAddr = addr
                                        contextMenuBytes = bytes
                                        contextMenuDisasm = disasmText
                                        contextMenuOffset = offset
                                        showContextMenu = true
                                    },
                                    showHexDetails = showHexDetails,
                                    onDragSelection = { rowsOffset ->
                                        val startIdx = instructions.indexOfFirst { it.instr.addr == line.instr.addr }
                                        if (startIdx != -1) {
                                            val targetIdx = (startIdx + rowsOffset).coerceIn(0, instructions.size - 1)
                                            val targetLine = instructions[targetIdx]
                                            val anchor = selectionAnchor ?: line.instr.addr
                                            if (targetLine.instr.addr >= anchor) {
                                                onSelectionChanged?.invoke(anchor, targetLine.instr.addr + targetLine.instr.length - 1)
                                            } else {
                                                onSelectionChanged?.invoke(anchor + line.instr.length - 1, targetLine.instr.addr)
                                            }
                                        }
                                    }
                                )

                                val isMenuForThisRow = showContextMenu && contextMenuAddr == line.instr.addr
                                DropdownMenu(
                                    expanded = isMenuForThisRow,
                                    onDismissRequest = { showContextMenu = false },
                                    offset = contextMenuOffset
                                ) {
                                    val addr = contextMenuAddr
                                    if (addr != null) {
                                        if (isAttached) {
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
                                    if (onJumpToGraph != null && contextMenuAddr != null) {
                                        DropdownMenuItem(
                                            text = { Text("Jump to Graph", fontSize = 12.sp, color = PS5ThemeColors.AccentCyan) },
                                            onClick = {
                                                onJumpToGraph(contextMenuAddr!!)
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
}

@Composable
fun DisasmRow(
    line: DisasmLine,
    isSelected: Boolean,
    isFunctionStart: Boolean = false,
    isCompact: Boolean = false,
    onAddressClicked: (Long, Int, Boolean) -> Unit,
    onAddressRightClicked: (Long, ByteArray, String, DpOffset) -> Unit,
    showHexDetails: Boolean = false,
    onDragSelection: ((Int) -> Unit)? = null
) {
    val instr = line.instr
    
    val jumpTarget = if (line.bytes.isNotEmpty()) com.osr.ps5debugger.ui.disasm.DisasmFormatter.getJumpTarget(instr, line.bytes) else 0L
    val targetAddr = if (jumpTarget != 0L) jumpTarget else if (instr.ripRelTarget != 0L) instr.ripRelTarget else 0L

    val ownSymbolName = AppContainer.symbolNames[instr.addr]
    val targetSymbolName = if (targetAddr != 0L) AppContainer.symbolNames[targetAddr] else null

    // Memoize static formatting logic to prevent heavy CPU work during every scroll frame
    val formattedData = remember(instr.addr, line.bytes, ownSymbolName, targetSymbolName) {
        val mnemonic = DisasmFormatter.getMnemonic(instr, line.bytes)
        val operands = DisasmFormatter.formatOperands(instr, line.bytes)
        val infoText = DisasmFormatter.getInfoText(instr, line.bytes)
        val bytesStr = line.bytes.joinToString(" ") { String.format("%02X", it) }
        
        // Pre-build the annotated operands string
        val annotatedOps = buildAnnotatedString {
            val regex = Regex("([\\[\\]\\+\\-\\*\\,\\s+])|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
            val matches = regex.findAll(operands)
            for (match in matches) {
                val token = match.value
                val isReg = DisasmFormatter.regNames.contains(token.lowercase())
                val isNumber = token.startsWith("0x") || token.all { it.isDigit() }
                
                when {
                    isReg -> {
                        withStyle(style = SpanStyle(color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)) {
                            append(token)
                        }
                    }
                    isNumber -> {
                        withStyle(style = SpanStyle(color = Color(0xFFFF8A65))) {
                            append(token)
                        }
                    }
                    token == "[" || token == "]" -> {
                        withStyle(style = SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)) {
                            append(token)
                        }
                    }
                    else -> {
                        withStyle(style = SpanStyle(color = Color(0xFFECEFF1))) {
                            append(token)
                        }
                    }
                }
            }
            if (matches.none()) {
                append(operands)
            }
        }
        
        // Mnemonic color logic
        val mnemonicColor = when {
            instr.isCall || instr.isRet -> Color(0xFFF50057)
            instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081)
            else -> Color(0xFFFFB74D)
        }
        
        object {
            val mnemonic = mnemonic
            val operands = annotatedOps
            val infoText = infoText
            val bytesStr = bytesStr
            val mnemonicColor = mnemonicColor
            val fullDisasm = "$mnemonic $operands"
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()
    
    // Ghidra Dark Theme Colors
    val addressColor = Color(0xFF90A4AE)      // Gray-blue lavender
    val byteColor = Color(0xFF808080)         // Dark Gray
    val commentColor = Color(0xFF78909C)       // Muted gray-green comment color
 
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> PS5ThemeColors.AccentCyan.copy(alpha = 0.35f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(instr.addr, isSelected, onAddressClicked, onAddressRightClicked, onDragSelection) {
                awaitPointerEventScope {
                    var touchStartPos: Offset? = null
                    var isLongPressActive = false
                    var hasTriggeredLongPress = false
                    var isDragging = false
                    var isSecondaryClick = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        
                        // If the move was consumed by the parent (e.g. scrolling the list), 
                        // we must prevent this from being counted as a tap selection.
                        if (change.isConsumed) {
                            isDragging = true
                            isLongPressActive = false
                        }
                        
                        when (event.type) {
                            PointerEventType.Press -> {
                                touchStartPos = change.position
                                isDragging = false
                                hasTriggeredLongPress = false
                                val isSecondary = event.buttons.isSecondaryPressed
                                isSecondaryClick = isSecondary
                                
                                if (isSecondary) {
                                    val offset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                    if (!isSelected) {
                                        onAddressClicked(instr.addr, instr.length, false)
                                    }
                                    onAddressRightClicked(instr.addr, line.bytes, formattedData.fullDisasm, offset)
                                } else {
                                    isLongPressActive = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(500)
                                        if (isLongPressActive) {
                                            hasTriggeredLongPress = true
                                            val offset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                            if (!isSelected) {
                                                onAddressClicked(instr.addr, instr.length, false)
                                            }
                                            onAddressRightClicked(instr.addr, line.bytes, formattedData.fullDisasm, offset)
                                            isLongPressActive = false
                                        }
                                    }
                                }
                            }
                            PointerEventType.Move -> {
                                val start = touchStartPos
                                if (start != null) {
                                    val dragY = change.position.y - start.y
                                    val dragX = change.position.x - start.x
                                    val dragDistance = kotlin.math.sqrt(dragX * dragX + dragY * dragY)
                                    val dragThreshold = 10f // Threshold for distinguishing tap from scroll/drag
                                    
                                    if (dragDistance > dragThreshold) {
                                        isLongPressActive = false
                                        isDragging = true // Mark as dragging for ANY significant movement (scroll or swipe)
                                        
                                        // Only trigger the specific "Drag Selection" logic (side-swipe) 
                                        // if it's primarily horizontal and not consumed by scrolling.
                                        if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.5f && !change.isConsumed) {
                                            val density = this.density
                                            val rowHeightPx = 22f * density
                                            val rowsOffset = (change.position.y / rowHeightPx).toInt()
                                            onDragSelection?.invoke(rowsOffset)
                                        }
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                isLongPressActive = false
                                if (!hasTriggeredLongPress && !isDragging && !isSecondaryClick) {
                                    val isShift = event.keyboardModifiers.isShiftPressed || windowInfo.keyboardModifiers.isShiftPressed
                                    onAddressClicked(instr.addr, instr.length, isShift)
                                }
                                touchStartPos = null
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
            modifier = Modifier.width(if (isCompact) 80.dp else 110.dp)
        )
        
        if (!showHexDetails && !isCompact) {
            // Raw hex bytes string (standard mode)
            Text(
                text = formattedData.bytesStr,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = byteColor,
                modifier = Modifier.width(130.dp),
                maxLines = 1
            )
        }
        
        // Mnemonic
        Text(
            text = formattedData.mnemonic,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = formattedData.mnemonicColor,
            modifier = Modifier.width(if (isCompact) 60.dp else 90.dp),
            maxLines = 1,
            softWrap = false
        )
        
        Spacer(Modifier.width(8.dp))
        
        // Operands with Ghidra syntax highlight
        Box(modifier = if (isCompact) Modifier.weight(1f) else Modifier.width(280.dp)) {
            Text(
                text = formattedData.operands,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        
        if (showHexDetails && !isCompact) {
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
        if (formattedData.infoText.isNotEmpty() && !isCompact) {
            Text(
                text = " ; ${formattedData.infoText}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = commentColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
