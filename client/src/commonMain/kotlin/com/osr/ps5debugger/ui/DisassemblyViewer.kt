package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import com.osr.ps5debugger.ui.disasm.DisassemblyState
import com.osr.ps5debugger.ui.disasm.DisasmField

data class DisasmLine(
    val instr: Ps5DisasmInstr,
    val bytes: ByteArray,
    val region: MemoryRange? = null,
    val symbolName: String? = null,
    val xrefs: List<Long> = emptyList(),
)

@Composable
private fun LinearDisassemblyScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        val coroutineScope = rememberCoroutineScope()
        val totalItems = listState.layoutInfo.totalItemsCount
        val visibleItems = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
        val thumbHeight = (this.maxHeight * (visibleItems.toFloat() / totalItems.coerceAtLeast(1).toFloat()))
            .coerceIn(30.dp, this.maxHeight)
        val travel = (this.maxHeight - thumbHeight)
        val thumbOffset = if (maxIndex == 0) 0.dp
        else travel * (listState.firstVisibleItemIndex.toFloat() / maxIndex.toFloat())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxIndex, visibleItems) {
                    detectTapGestures { offset ->
                        val target = if (offset.y < thumbOffset.toPx()) {
                            listState.firstVisibleItemIndex - visibleItems
                        } else {
                            listState.firstVisibleItemIndex + visibleItems
                        }
                        coroutineScope.launch { listState.scrollToItem(target.coerceIn(0, maxIndex)) }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .fillMaxWidth()
                    .height(thumbHeight)
                    .background(PS5ThemeColors.AccentCyan.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .pointerInput(maxIndex, travel) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (maxIndex > 0 && travel > 0.dp) {
                                val delta = (dragAmount.y / travel.toPx() * maxIndex).toInt()
                                coroutineScope.launch {
                                    listState.scrollToItem(
                                        (listState.firstVisibleItemIndex + delta).coerceIn(0, maxIndex)
                                    )
                                }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun HorizontalDisassemblyScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .height(12.dp)
            .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val viewportWidth = maxWidth
        if (scrollState.maxValue <= 0) return@BoxWithConstraints

        val totalContentWidth = viewportWidth + with(density) { scrollState.maxValue.toDp() }
        val thumbWidth = (viewportWidth.value / totalContentWidth.value * viewportWidth.value).dp.coerceAtLeast(40.dp)
        val travel = (viewportWidth - thumbWidth)
        val thumbOffset = travel * (scrollState.value.toFloat() / scrollState.maxValue.coerceAtLeast(1).toFloat())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val target = if (offset.x < with(density) { thumbOffset.toPx() }) {
                            scrollState.value - with(density) { viewportWidth.toPx() }.toInt()
                        } else {
                            scrollState.value + with(density) { viewportWidth.toPx() }.toInt()
                        }
                        coroutineScope.launch { scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue)) }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .fillMaxHeight()
                    .width(thumbWidth)
                    .background(PS5ThemeColors.AccentCyan.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .pointerInput(scrollState.maxValue) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (scrollState.maxValue > 0 && with(density) { travel.toPx() } > 0f) {
                                val delta = (dragAmount.x / with(density) { travel.toPx() } * scrollState.maxValue).toInt()
                                coroutineScope.launch {
                                    scrollState.scrollTo(
                                        (scrollState.value + delta).coerceIn(0, scrollState.maxValue)
                                    )
                                }
                            }
                        }
                    }
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
fun DisassemblyViewer(
    state: DisassemblyState,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    onJumpToAddress: ((Long) -> Unit)? = null,
    onJumpToHex: ((Long) -> Unit)? = null,
    onJumpToGraph: ((Long) -> Unit)? = null,
    onShowXrefs: ((Long) -> Unit)? = null,
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
        selectionField = state.selectionField,
        onSelectionChanged = { start, end, field ->
            state.selectionField = field
            state.onSelectionChanged?.invoke(start, end)
        },
        onJumpToAddress = onJumpToAddress,
        onJumpToHex = onJumpToHex,
        onJumpToGraph = onJumpToGraph,
        onShowXrefs = onShowXrefs,
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
    selectionField: DisasmField? = null,
    onSelectionChanged: ((Long?, Long?, DisasmField?) -> Unit)? = null,
    onJumpToAddress: ((Long) -> Unit)? = null,
    onJumpToHex: ((Long) -> Unit)? = null,
    onJumpToGraph: ((Long) -> Unit)? = null,
    onShowXrefs: ((Long) -> Unit)? = null,
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
    
    var goToAddressText by remember { mutableStateOf("") }
    
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuBytes by remember { mutableStateOf(byteArrayOf()) }
    var contextMenuDisasm by remember { mutableStateOf("") }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    var showWatchpointDialog by remember { mutableStateOf(false) }
    var selectedJump by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    val listState = rememberLazyListState()

    var selectionAnchor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectionStart) {
        val start = selectionStart
        val anchor = selectionAnchor
        if (start != null && anchor != null) {
            if (kotlin.math.abs(start - anchor) > 15) selectionAnchor = start
        } else {
            selectionAnchor = start
        }
    }
    
    var targetToScroll by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(jumpToAddress) {
        if (jumpToAddress != null) {
            targetToScroll = jumpToAddress
        }
    }

    LaunchedEffect(activeMap, targetToScroll, instructions.size) {
        val target = targetToScroll
        if (activeMap != null && target != null) {
            val index = instructions.indexOfFirst { it.instr.addr == target }
            if (index != -1) {
                goToAddressText = target.toString(16).uppercase()
                try {
                    listState.animateScrollToItem(index)
                    targetToScroll = null // Mark as finished
                } catch (_: Exception) {}
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PS5ThemeColors.AccentCyan, unfocusedBorderColor = PS5ThemeColors.BorderColor)
            )
            Button(
                onClick = {
                    val addr = goToAddressText.trim().toLongOrNull(16)
                    if (addr != null) {
                        targetToScroll = addr
                        onJumpToAddress?.invoke(addr)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan, contentColor = Color.Black),
                modifier = Modifier.height(40.dp)
            ) { Text("Go", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Button(
                onClick = { com.osr.ps5debugger.util.OrbisSymbolResolver.autoResolve(instructions) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg, contentColor = PS5ThemeColors.TextMain),
                modifier = Modifier.height(40.dp)
            ) { Text("Resolve Orbis Symbols", fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val currentTop = instructions.firstOrNull()?.instr?.addr ?: jumpToAddress
                    if (currentTop != null) {
                        val prevFunc = functionAddresses.filter { it < currentTop }.maxOrNull() ?: (currentTop - 8192)
                        onJumpToAddress?.invoke(prevFunc)
                        goToAddressText = prevFunc.toString(16).uppercase()
                    }
                },
                enabled = activeMap != null || activeMaps.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) { Text("Previous", color = PS5ThemeColors.TextMain) }
            Button(
                onClick = {
                    val currentTop = instructions.firstOrNull()?.instr?.addr ?: jumpToAddress
                    if (currentTop != null) {
                        val nextFunc = functionAddresses.filter { it > currentTop }.minOrNull() ?: (currentTop + 8192)
                        onJumpToAddress?.invoke(nextFunc)
                        goToAddressText = nextFunc.toString(16).uppercase()
                    }
                },
                enabled = activeMap != null || activeMaps.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) { Text("Next", color = PS5ThemeColors.TextMain) }
        }
        
        val allTargets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        val hasLocal = allTargets.any { it.localData != null }
        
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if ((activeMap == null && activeMaps.isEmpty()) || (activeProcess == null && !hasLocal)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a Process and Virtual Memory Map to disassemble", style = MaterialTheme.typography.bodyLarge, color = PS5ThemeColors.TextMuted)
                }
            } else if (isLoading && instructions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
                        Text("Processing Disassembly...", color = PS5ThemeColors.AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val focusRequester = remember { FocusRequester() }
                    val viewportWidth = maxWidth
                    val isCompact = viewportWidth < 600.dp
                    val horizontalScrollState = rememberScrollState()

                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxSize().then(if (isCompact) Modifier else Modifier.horizontalScroll(horizontalScrollState))) {
                                Column(modifier = Modifier.width(if (isCompact) viewportWidth else 2000.dp).fillMaxHeight()) {
                                Row(modifier = Modifier.fillMaxWidth().height(28.dp).background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("ADDRESS", modifier = Modifier.width(110.dp), color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("BYTES", modifier = Modifier.width(220.dp).padding(horizontal = 12.dp), color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("ASCII", modifier = Modifier.width(180.dp).padding(horizontal = 8.dp), color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("MNEMONIC", modifier = Modifier.width(90.dp), color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("OPERANDS", modifier = Modifier.width(420.dp), color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("COMMENTS / XREFS", color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                val visibleJumpTracks = remember(jumpTracks) { jumpTracks.filterValues { it < 8 } }
                                val visibleMaxTrack = visibleJumpTracks.values.maxOrNull() ?: -1
                                val visibleCanvasWidth = if (visibleMaxTrack >= 0) (20 + (visibleMaxTrack + 1) * 8).dp.coerceAtMost(40.dp) else 0.dp
                                SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .width(if (isCompact) viewportWidth else 2000.dp)
                                        .weight(1f)
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
                                                        onSelectionChanged?.invoke(start, targetAddr + targetInstr.length - 1, null)
                                                    } else {
                                                        selectionAnchor = targetAddr
                                                        onSelectionChanged?.invoke(targetAddr, targetAddr + targetInstr.length - 1, null)
                                                    }
                                                    coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                                    true
                                                } else false
                                            } else false
                                        }
                                ) {
                                    itemsIndexed(items = instructions, key = { _, line -> line.instr.addr }) { idx, line ->
                                        val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                                        val hasBreakpoint = activeBreakpoints.values.contains(line.instr.addr) || activeWatchpoints.values.contains(line.instr.addr)
                                        val prevLine = if (idx > 0) instructions[idx - 1] else null
                                        val currentMap = line.region ?: activeMap
                                        val prevMap = prevLine?.region ?: activeMap
                                        val isNewRegionStart = idx == 0 || (currentMap != null && prevMap != null && currentMap.start != prevMap.start)
                                        val isFunctionStart = functionAddresses.contains(line.instr.addr)
                                        val label = line.symbolName ?: AppContainer.symbolNames[line.instr.addr]

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (isNewRegionStart && currentMap != null) {
                                                Spacer(Modifier.height(16.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.5f)).border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Region: ${currentMap.name.ifEmpty { "unnamed" }} [0x${currentMap.start.toString(16).uppercase()} - 0x${currentMap.end.toString(16).uppercase()}] (${currentMap.getProtString()})", color = PS5ThemeColors.AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                                Spacer(Modifier.height(8.dp))
                                            }

                                            if (isFunctionStart) {
                                                // Ghidra-style function plate: a named, selectable
                                                // comment block anchored to the function entry.
                                                val functionName = label ?: AppContainer.getSymbolName(line.instr.addr, true)
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 290.dp, top = 14.dp, bottom = 4.dp)
                                                        .clickable {
                                                            onSelectionChanged?.invoke(line.instr.addr, line.instr.addr + line.instr.length - 1, DisasmField.COMMENT)
                                                        }
                                                ) {
                                                    Text(
                                                        "/******************************************************************************/",
                                                        color = PS5ThemeColors.TextMuted,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        "/* ${functionName ?: "FUNCTION"} @ 0x${line.instr.addr.toString(16).uppercase()} */",
                                                        color = PS5ThemeColors.AccentCyan,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        "/* Function entry point; click to select comment */",
                                                        color = PS5ThemeColors.TextMuted,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        "/******************************************************************************/",
                                                        color = PS5ThemeColors.TextMuted,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            if (label != null || line.xrefs.isNotEmpty()) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(start = 290.dp, top = 4.dp).clickable {
                                                    onSelectionChanged?.invoke(line.instr.addr, line.instr.addr + line.instr.length - 1, DisasmField.COMMENT)
                                                }, verticalAlignment = Alignment.CenterVertically) {
                                                    if (label != null) {
                                                        Text(text = label, color = if (isFunctionStart) Color(0xFF64FFDA) else Color(0xFF90A4AE), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(300.dp))
                                                    } else {
                                                        Spacer(Modifier.width(300.dp))
                                                    }
                                                    if (line.xrefs.isNotEmpty()) {
                                                        Text(text = "XREF[${line.xrefs.size}]: " + line.xrefs.take(3).joinToString(", ") { "FUN_${it.toString(16).padStart(8, '0')}" } + if (line.xrefs.size > 3) "..." else "", color = Color(0xFF78909C), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                                if (hasBreakpoint) {
                                                    Box(modifier = Modifier.padding(end = 4.dp).size(8.dp).background(Color.Red, RoundedCornerShape(4.dp)))
                                                } else {
                                                    Spacer(Modifier.width(12.dp))
                                                }

                                                if (visibleCanvasWidth > 0.dp) {
                                                    Canvas(modifier = Modifier.width(visibleCanvasWidth).height(20.dp).padding(end = 8.dp)) {
                                                        val density = this.density
                                                        val addr = line.instr.addr
                                                        for ((jump, track) in visibleJumpTracks) {
                                                            val color = (jumpColors[jump] ?: Color.Gray).copy(alpha = if (selectedJump == jump) 1f else 0.2f)
                                                            val src = jump.first
                                                            val target = jump.second
                                                            val lineX = 6f * density + track * 8f * density
                                                            val lineWidth = if (selectedJump == jump) 2.4f else 1.2f
                                                            when {
                                                                addr > minOf(src, target) && addr < maxOf(src, target) -> drawLine(color, start = Offset(lineX, 0f), end = Offset(lineX, size.height), strokeWidth = lineWidth)
                                                                addr == src -> {
                                                                    val startY = size.height / 2f
                                                                    drawLine(color, start = Offset(lineX, startY), end = Offset(lineX, if (target > src) size.height else 0f), strokeWidth = lineWidth)
                                                                    drawLine(color, start = Offset(lineX, startY), end = Offset(size.width, startY), strokeWidth = lineWidth)
                                                                }
                                                                addr == target -> {
                                                                    val endY = size.height / 2f
                                                                    drawLine(color, start = Offset(lineX, if (src < target) 0f else size.height), end = Offset(lineX, endY), strokeWidth = lineWidth)
                                                                    drawLine(color, start = Offset(lineX, endY), end = Offset(size.width, endY), strokeWidth = lineWidth)
                                                                    drawPath(Path().apply { moveTo(size.width, endY); lineTo(size.width - 5f * density, endY - 3f * density); lineTo(size.width - 5f * density, endY + 3f * density); close() }, color)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                DisasmRow(
                                                    line = line,
                                                    isSelected = isSelected,
                                                    selectionField = selectionField,
                                                    isMultiLineSelection = (selectionStart != selectionEnd && selectionStart != null && selectionEnd != null),
                                                    isCompact = isCompact,
                                                    onAddressClicked = { addr, len, field, isShift ->
                                                        if (isShift && selectionAnchor != null) {
                                                            val anchor = selectionAnchor!!
                                                            if (addr >= anchor) onSelectionChanged?.invoke(anchor, addr + len - 1, null)
                                                            else onSelectionChanged?.invoke(anchor + (instructions.firstOrNull { it.instr.addr == anchor }?.instr?.length ?: 1) - 1, addr, null)
                                                        } else {
                                                            selectionAnchor = addr
                                                            onSelectionChanged?.invoke(addr, addr + len - 1, field)
                                                        }
                                                    },
                                                    onAddressRightClicked = { addr, bytes, disasm, offset ->
                                                        contextMenuAddr = addr; contextMenuBytes = bytes; contextMenuDisasm = disasm; contextMenuOffset = offset; showContextMenu = true
                                                    },
                                                    onDragSelection = { rowsOffset ->
                                                        val startIdx = instructions.indexOfFirst { it.instr.addr == line.instr.addr }
                                                        if (startIdx != -1) {
                                                            val targetLine = instructions[(startIdx + rowsOffset).coerceIn(0, instructions.size - 1)]
                                                            val anchor = selectionAnchor ?: line.instr.addr
                                                            if (targetLine.instr.addr >= anchor) onSelectionChanged?.invoke(anchor, targetLine.instr.addr + targetLine.instr.length - 1, null)
                                                            else onSelectionChanged?.invoke(anchor + line.instr.length - 1, targetLine.instr.addr, null)
                                                        }
                                                    }
                                                )

                                                if (showContextMenu && contextMenuAddr == line.instr.addr) {
                                                    DropdownMenu(expanded = true, onDismissRequest = { showContextMenu = false }, offset = contextMenuOffset) {
                                                        val addr = contextMenuAddr!!
                                                        val selectedLines = if (selectionStart != null && selectionEnd != null) instructions.filter { it.instr.addr in minOf(selectionStart, selectionEnd)..maxOf(selectionStart, selectionEnd) } else emptyList()
                                                        if (isAttached) {
                                                            val activeBpIndex = activeBreakpoints.entries.firstOrNull { it.value == addr }?.key
                                                            DropdownMenuItem(text = { Text(if (activeBpIndex != null) "Remove Breakpoint" else "Set Software Breakpoint", color = if (activeBpIndex != null) Color.Red else Color.Unspecified) }, onClick = {
                                                                coroutineScope.launch {
                                                                    if (activeBpIndex != null) {
                                                                        activeBreakpoints.remove(activeBpIndex)
                                                                        client.setBreakpoint(activeBpIndex, false, addr)
                                                                    } else {
                                                                        val freeIndex = (0..29).firstOrNull { !activeBreakpoints.containsKey(it) }
                                                                        if (freeIndex != null) { activeBreakpoints[freeIndex] = addr; client.setBreakpoint(freeIndex, true, addr) }
                                                                    }
                                                                }
                                                                showContextMenu = false
                                                            })
                                                            HorizontalDivider()
                                                        }
                                                        DropdownMenuItem(text = { Text("Copy Address") }, onClick = { copyToClipboard(if (selectedLines.isNotEmpty()) selectedLines.joinToString("\n") { "0x" + it.instr.addr.toString(16).uppercase() } else "0x" + addr.toString(16).uppercase()); showContextMenu = false })
                                                        DropdownMenuItem(text = { Text("Copy Hex Bytes") }, onClick = { copyToClipboard(if (selectedLines.isNotEmpty()) selectedLines.joinToString("\n") { it.bytes.joinToString("") { b -> "%02X".format(b) } } else contextMenuBytes.joinToString("") { "%02X".format(it) }); showContextMenu = false })
                                                        DropdownMenuItem(text = { Text("Copy All") }, onClick = {
                                                            val textToCopy = (if (selectedLines.isNotEmpty()) selectedLines else listOf(line)).joinToString("\n") { l ->
                                                                val hex = l.bytes.joinToString("") { "%02X".format(it) }.padEnd(20)
                                                                val ascii = l.bytes.joinToString("") { b -> if (b.toInt() in 32..126) b.toInt().toChar().toString() else "." }
                                                                val lbl = l.symbolName ?: AppContainer.symbolNames[l.instr.addr] ?: ""
                                                                String.format("0x%012X  %s  %-16s %-10s %-30s ; %s", l.instr.addr, hex, lbl, DisasmFormatter.getMnemonic(l.instr, l.bytes), DisasmFormatter.formatOperands(l.instr, l.bytes), ascii).trimEnd()
                                                            }
                                                            copyToClipboard(textToCopy); showContextMenu = false
                                                        })
                                                        if (onJumpToHex != null) DropdownMenuItem(text = { Text("Jump to Hex Viewer", color = PS5ThemeColors.AccentCyan) }, onClick = { onJumpToHex(addr); showContextMenu = false })
                                                        if (onJumpToGraph != null) DropdownMenuItem(text = { Text("Jump to Graph", color = PS5ThemeColors.AccentCyan) }, onClick = { onJumpToGraph(addr); showContextMenu = false })
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Cheats", color = PS5ThemeColors.AccentCyan) },
                                                            onClick = {
                                                                val procInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                                                                val tId = procInfo?.titleId ?: activeMap?.titleId ?: "Unknown"
                                                                
                                                                val start = if (selectionStart != null && selectionEnd != null) minOf(selectionStart!!, selectionEnd!!) else addr
                                                                val end = if (selectionStart != null && selectionEnd != null) maxOf(selectionStart!!, selectionEnd!!) else addr
                                                                
                                                                val selectedLines = instructions.filter { it.instr.addr in start..end }
                                                                val originalBytes = selectedLines.joinToString("") { l ->
                                                                    l.bytes.joinToString("") { "%02X".format(it) }
                                                                }

                                                                AppContainer.onCreateCheatRequested?.invoke(
                                                                    com.osr.ps5debugger.domain.model.Cheat(
                                                                        id = "",
                                                                        name = "New Cheat",
                                                                        type = com.osr.ps5debugger.domain.model.CheatType.Toggle,
                                                                        address = start,
                                                                        hexOnValue = "",
                                                                        hexOffValue = originalBytes,
                                                                        titleId = tId
                                                                    )
                                                                )
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

                            LinearDisassemblyScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp))
                        }
                        
                        if (!isCompact) {
                            HorizontalDisassemblyScrollbar(scrollState = horizontalScrollState, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
                                Text("Processing Disassembly...", color = PS5ThemeColors.AccentCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        
        if (showWatchpointDialog) {
            val addr = contextMenuAddr
            if (addr != null) {
                var watchpointSlot by remember { mutableStateOf(0) }
                var watchpointType by remember { mutableStateOf(1) } // 1 = Write, 3 = Read/Write
                var watchpointSize by remember { mutableStateOf(1) } // 1, 2, 4, 8 bytes
                AlertDialog(
                    onDismissRequest = { showWatchpointDialog = false },
                    title = { Text("Configure Hardware Watchpoint", color = PS5ThemeColors.AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Address: 0x${addr.toString(16).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("DR Slot: ", modifier = Modifier.width(60.dp), fontSize = 12.sp)
                                (0..3).forEach { slot -> FilterChip(selected = watchpointSlot == slot, onClick = { watchpointSlot = slot }, label = { Text("DR$slot", fontSize = 11.sp) }, modifier = Modifier.padding(horizontal = 2.dp)) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Type: ", modifier = Modifier.width(60.dp), fontSize = 12.sp)
                                FilterChip(selected = watchpointType == 1, onClick = { watchpointType = 1 }, label = { Text("Write", fontSize = 11.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                                FilterChip(selected = watchpointType == 3, onClick = { watchpointType = 3 }, label = { Text("Read/Write", fontSize = 11.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size: ", modifier = Modifier.width(60.dp), fontSize = 12.sp)
                                listOf(1, 2, 4, 8).forEach { sz -> FilterChip(selected = watchpointSize == sz, onClick = { watchpointSize = sz }, label = { Text("${sz}B", fontSize = 11.sp) }, modifier = Modifier.padding(horizontal = 2.dp)) }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                try {
                                    val lenField = when (watchpointSize) { 1 -> 0; 2 -> 1; 8 -> 2; else -> 3 }
                                    activeWatchpoints[watchpointSlot] = addr
                                    client.setWatchpoint(watchpointSlot, true, lenField, watchpointType, addr)
                                } catch (_: Exception) {}
                            }
                            showWatchpointDialog = false
                        }) { Text("Apply", color = PS5ThemeColors.AccentCyan, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = { TextButton(onClick = { showWatchpointDialog = false }) { Text("Cancel", color = PS5ThemeColors.TextMuted) } },
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
    selectionField: DisasmField?,
    isMultiLineSelection: Boolean,
    isCompact: Boolean = false,
    onAddressClicked: (Long, Int, DisasmField, Boolean) -> Unit,
    onAddressRightClicked: (Long, ByteArray, String, DpOffset) -> Unit,
    onDragSelection: ((Int) -> Unit)? = null
) {
    val instr = line.instr
    val isLight = PS5ThemeColors.activeTheme == "Light"

    val formattedData = remember(instr.addr, line.bytes, PS5ThemeColors.activeTheme) {
        val mnemonic = DisasmFormatter.getMnemonic(instr, line.bytes)
        val operands = DisasmFormatter.formatOperands(instr, line.bytes)
        val infoText = DisasmFormatter.getInfoText(instr, line.bytes)
        val bytesStr = line.bytes.joinToString(" ") { String.format("%02X", it) }
        val regColor = if (isLight) Color(0xFF005A9C) else Color(0xFF64FFDA)
        val numberColor = if (isLight) Color(0xFFD03A00) else Color(0xFFFF8A65)
        val bracketColor = if (isLight) Color(0xFF795548) else Color(0xFFFFD54F)
        val defaultOpColor = if (isLight) Color(0xFF1F2328) else Color(0xFFECEFF1)
        
        val annotatedOps = buildAnnotatedString {
            if (instr.isDataString) withStyle(style = SpanStyle(color = Color(0xFF4CAF50))) { append(operands) }
            else {
                val regex = Regex("([\\[\\]\\+\\-\\*\\,\\.\\/\\:\\\\\\s+])|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
                var lastIndex = 0
                regex.findAll(operands).forEach { match ->
                    if (match.range.first > lastIndex) append(operands.substring(lastIndex, match.range.first))
                    val token = match.value
                    val isReg = DisasmFormatter.regNames.contains(token.lowercase())
                    val isNumber = token.startsWith("0x") || token.all { it.isDigit() }
                    when {
                        isReg -> withStyle(style = SpanStyle(color = regColor, fontWeight = FontWeight.Bold)) { append(token) }
                        isNumber -> withStyle(style = SpanStyle(color = numberColor)) { append(token) }
                        token == "[" || token == "]" -> withStyle(style = SpanStyle(color = bracketColor, fontWeight = FontWeight.Bold)) { append(token) }
                        else -> withStyle(style = SpanStyle(color = defaultOpColor)) { append(token) }
                    }
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < operands.length) append(operands.substring(lastIndex))
            }
        }
        val mnemonicColor = when {
            instr.isCall || instr.isRet -> if (isLight) Color(0xFF9C27B0) else Color(0xFFF50057)
            instr.isJmp || instr.isCondJmp -> if (isLight) Color(0xFFD11149) else Color(0xFFFF4081)
            else -> if (isLight) Color(0xFF006666) else Color(0xFFFFB74D)
        }
        object { val mnemonic = mnemonic; val operands = annotatedOps; val infoText = infoText; val bytesStr = bytesStr; val mnemonicColor = mnemonicColor; val fullDisasm = "$mnemonic $operands" }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val addressColor = if (isLight) Color(0xFF57606A) else Color(0xFF90A4AE)
    val byteColor = if (isLight) Color(0xFF6E7781) else Color(0xFF808080)
    val commentColor = if (isLight) Color(0xFF0969DA) else Color(0xFF78909C)
    val selBg = PS5ThemeColors.AccentCyan.copy(alpha = 0.25f)
 
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.10f) else Color.Transparent)
            .pointerInput(instr.addr, isSelected) {
                awaitPointerEventScope {
                    var touchStartPos: Offset? = null
                    var isDragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (change.isConsumed) isDragging = true
                        when (event.type) {
                            PointerEventType.Press -> { 
                                touchStartPos = change.position
                                isDragging = false
                                if (event.buttons.isSecondaryPressed) { 
                                    onAddressClicked(instr.addr, instr.length, DisasmField.ADDRESS, false)
                                    onAddressRightClicked(instr.addr, line.bytes, formattedData.fullDisasm, DpOffset(change.position.x.toDp(), change.position.y.toDp())) 
                                } 
                            }
                            PointerEventType.Move -> { 
                                val start = touchStartPos
                                if (start != null) { 
                                    val dx = change.position.x - start.x
                                    val dy = change.position.y - start.y
                                    if (kotlin.math.sqrt(dx * dx + dy * dy) > 10f) { 
                                        isDragging = true
                                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 0.5f && !change.isConsumed) onDragSelection?.invoke((dy / (22f * this.density)).toInt())
                                    } 
                                } 
                            }
                            PointerEventType.Release -> { touchStartPos = null }
                        }
                    }
                }
            }
            .padding(vertical = 1.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isShift = windowInfo.keyboardModifiers.isShiftPressed
        
        // 1. Address
        Box(Modifier.width(110.dp)
            .background(if (isSelected && (selectionField == DisasmField.ADDRESS || isMultiLineSelection)) selBg else Color.Transparent)
            .pointerInput(instr.addr) { detectTapGestures { onAddressClicked(instr.addr, instr.length, DisasmField.ADDRESS, isShift) } }) { 
            Text(text = String.format("%012X", instr.addr), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = addressColor) 
        }
        
        // 2. Hex Bytes
        Box(Modifier.width(220.dp).padding(horizontal = 12.dp)
            .background(if (isSelected && (selectionField == DisasmField.BYTES || isMultiLineSelection)) selBg else Color.Transparent)
            .pointerInput(instr.addr) { detectTapGestures { onAddressClicked(instr.addr, instr.length, DisasmField.BYTES, isShift) } }) { 
            Text(text = formattedData.bytesStr, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = byteColor, maxLines = 1, softWrap = false) 
        }

        // Ghidra keeps the byte view paired with a printable representation. Keep this
        // selectable as its own column so strings can be copied without selecting the opcode.
        Box(Modifier.width(180.dp).padding(horizontal = 8.dp)
            .background(if (isSelected && (selectionField == DisasmField.BYTES || isMultiLineSelection)) selBg else Color.Transparent)
            .pointerInput(instr.addr) { detectTapGestures { onAddressClicked(instr.addr, instr.length, DisasmField.BYTES, isShift) } }) {
            val ascii = line.bytes.joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                if (value in 0x20..0x7E) value.toChar().toString() else "."
            }
            Text(text = "|$ascii|", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = commentColor, maxLines = 1, softWrap = false)
        }

        // 3. Mnemonic
        Box(Modifier.width(90.dp)
            .background(if (isSelected && (selectionField == DisasmField.MNEMONIC || isMultiLineSelection)) selBg else Color.Transparent)
            .pointerInput(instr.addr) { detectTapGestures { onAddressClicked(instr.addr, instr.length, DisasmField.MNEMONIC, isShift) } }) { 
            Text(text = formattedData.mnemonic, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = formattedData.mnemonicColor) 
        }
        
        // 4. Operands
        Box(modifier = Modifier.width(420.dp)
            .background(if (isSelected && (selectionField == DisasmField.OPERANDS || isMultiLineSelection)) selBg else Color.Transparent)
            .pointerInput(instr.addr) { detectTapGestures { onAddressClicked(instr.addr, instr.length, DisasmField.OPERANDS, isShift) } }) { 
            Text(text = formattedData.operands, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, softWrap = false) 
        }
        
        // 5. Comment
        Box(modifier = Modifier.weight(1f).padding(start = 24.dp)
            .background(if (isSelected && (selectionField == DisasmField.COMMENT || isMultiLineSelection)) selBg else Color.Transparent)
            .pointerInput(instr.addr) { detectTapGestures { onAddressClicked(instr.addr, instr.length, DisasmField.COMMENT, isShift) } }) {
            val info = formattedData.infoText
            if (info.isNotEmpty() && !isCompact) Text(text = " ; $info", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = commentColor, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun XRefsDialog(
    targetAddr: Long,
    xrefs: List<Long>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onJumpToHex: (Long) -> Unit,
    onJumpToDisassembly: (Long) -> Unit,
    onJumpToGraph: (Long) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "XRefs to ${AppContainer.getSymbolName(targetAddr, true)} (0x${targetAddr.toString(16).uppercase()})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMain) },
        text = {
            Box(modifier = Modifier.width(400.dp).height(300.dp)) {
                if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PS5ThemeColors.AccentCyan) }
                else if (xrefs.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No references found.", color = PS5ThemeColors.TextMuted, fontSize = 14.sp) }
                else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(xrefs) { addr ->
                            val funcName = AppContainer.getSymbolName(addr, false)
                            Row(modifier = Modifier.fillMaxWidth().clickable { }.pointerInput(addr) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                            contextMenuAddr = addr; contextMenuOffset = DpOffset(event.changes.first().position.x.toDp(), event.changes.first().position.y.toDp()); showContextMenu = true
                                        }
                                    }
                                }
                            }.padding(vertical = 8.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = String.format("0x%012X", addr), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = PS5ThemeColors.AccentCyan, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(text = funcName, fontSize = 12.sp, color = PS5ThemeColors.TextMuted, maxLines = 1)
                            }
                            HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
                        }
                    }
                }
                DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }, offset = contextMenuOffset) {
                    val addr = contextMenuAddr ?: 0L
                    DropdownMenuItem(text = { Text("Jump to in Hex") }, onClick = { onJumpToHex(addr); showContextMenu = false; onDismiss() })
                    DropdownMenuItem(text = { Text("Jump to in Disassembly") }, onClick = { onJumpToDisassembly(addr); showContextMenu = false; onDismiss() })
                    DropdownMenuItem(text = { Text("Jump to in Graph") }, onClick = { onJumpToGraph(addr); showContextMenu = false; onDismiss() })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = PS5ThemeColors.TextMain) } },
        containerColor = PS5ThemeColors.DarkBg,
        textContentColor = PS5ThemeColors.TextMain
    )
}
