package com.osr.ps5debugger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.protocol.ProtocolConstants
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import com.osr.ps5debugger.util.copyToClipboard

data class CfgNode(
    val id: Int,
    val instructions: List<DisasmLine>,
    val startAddr: Long,
    val endAddr: Long,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 360f,
    var height: Float = 0f,
    var borderColor: Color = Color(0xFF444444),
    val isExternal: Boolean = false
)

data class CfgEdge(
    val from: Int,
    val to: Int,
    val type: EdgeType
)

enum class EdgeType {
    UNCONDITIONAL,
    TRUE,
    FALSE
}

@Composable
fun GraphViewer(
    instructions: List<DisasmLine>,
    isLoading: Boolean = false,
    vmMaps: List<MemoryRange> = emptyList(),
    filterFunctionAddr: Long? = null,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    selectionStart: Long? = null,
    selectionEnd: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null,
    onAddressClicked: (Long) -> Unit = {},
    onJumpToHex: (Long) -> Unit = {},
    isAttached: Boolean = false,
    activeBreakpoints: Map<Int, Long> = emptyMap(),
    activeWatchpoints: Map<Int, Long> = emptyMap(),
    onSetBreakpoint: (Long) -> Unit = {},
    onSetWatchpoint: (Long) -> Unit = {}
) {
    val density = LocalDensity.current.density
    
    var cfg by remember { mutableStateOf<Pair<List<CfgNode>, List<CfgEdge>>>(Pair(emptyList(), emptyList())) }
    var isBuildingCfg by remember { mutableStateOf(false) }

    LaunchedEffect(instructions.size, filterFunctionAddr, instructions.firstOrNull()?.instr?.addr) {
        if (instructions.isEmpty()) {
            cfg = Pair(emptyList(), emptyList())
            return@LaunchedEffect
        }
        
        isBuildingCfg = true
        val snapshot = instructions.toList()
        val result = withContext(Dispatchers.Default) {
            buildCfg(snapshot, filterFunctionAddr)
        }
        cfg = result
        isBuildingCfg = false
    }

    val nodes = cfg.first
    val edges = cfg.second
    val nodeHeights = remember(nodes) { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    var selectedEdge by remember { mutableStateOf<CfgEdge?>(null) }

    var scale by remember(filterFunctionAddr) { mutableStateOf(0.7f) }
    var offset by remember(filterFunctionAddr) { mutableStateOf(Offset.Zero) }
    
    // Auto-centering only when the selected function changes or a specific jump is requested
    var lastCenteredId by remember { mutableStateOf<Long?>(null) }
    var lastCenteredAddr by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(nodes.size, filterFunctionAddr, jumpToAddress) {
        if (nodes.isNotEmpty() && (lastCenteredId != filterFunctionAddr || (jumpToAddress != null && lastCenteredAddr != jumpToAddress))) {
            val targetAddr = jumpToAddress ?: filterFunctionAddr ?: instructions.firstOrNull()?.instr?.addr
            val targetNode = if (targetAddr != null) {
                nodes.firstOrNull { targetAddr >= it.startAddr && targetAddr <= it.endAddr }
            } else nodes.firstOrNull()

            if (targetNode != null) {
                scale = 0.7f
                offset = Offset(-(targetNode.x * scale * density) + (400f * density), -(targetNode.y * scale * density) + 100f)
            }
            lastCenteredId = filterFunctionAddr
            lastCenteredAddr = jumpToAddress
        }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    var containerPosition by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg)
            .clipToBounds()
            .onGloballyPositioned { 
                containerPosition = it.localToWindow(Offset.Zero) 
                containerSize = it.size
            }
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
                            onSelectionChanged?.invoke(targetAddr, targetAddr + targetInstr.length - 1)
                        }
                        
                        // Auto center on selected instruction node
                        val targetNode = nodes.firstOrNull { targetAddr >= it.startAddr && targetAddr <= it.endAddr }
                        if (targetNode != null) {
                            offset = Offset(-(targetNode.x * scale * density) + (containerSize.width / 2f), -(targetNode.y * scale * density) + (containerSize.height / 2f))
                        }
                        true
                    } else false
                } else false
            }
    ) {
        if ((isLoading || isBuildingCfg) && nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = PS5ThemeColors.AccentCyan) }
        } else if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No reachable logic blocks found for this function.", color = PS5ThemeColors.TextMuted) }
        } else {
            // Pan gesture layer
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(edges, nodes, nodeHeights, scale, offset) {
                        detectTapGestures { screenClickOffset ->
                            val canvasClickX = (screenClickOffset.x - offset.x) / scale
                            val canvasClickY = (screenClickOffset.y - offset.y) / scale
                            val clickOffsetInCanvas = Offset(canvasClickX, canvasClickY)
                            val minX = nodes.minOfOrNull { it.x } ?: 0f
                            val maxX = nodes.maxOfOrNull { it.x + it.width } ?: 1000f
                            selectedEdge = findClickedEdge(clickOffsetInCanvas, edges, nodes, nodeHeights, density, minX, maxX)
                        }
                    }
                    .pointerInput(filterFunctionAddr) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.05f, 4.0f)
                            val scaleRatio = newScale / oldScale
                            scale = newScale
                            offset = centroid - (centroid - offset) * scaleRatio + pan
                        }
                    }
                    .pointerInput(filterFunctionAddr) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val change = event.changes.first()
                                    val centroid = change.position
                                    val delta = change.scrollDelta.y
                                    val zoom = if (delta > 0) 0.9f else 1.1f
                                    val oldScale = scale
                                    val newScale = (scale * zoom).coerceIn(0.05f, 4.0f)
                                    val scaleRatio = newScale / oldScale
                                    scale = newScale
                                    offset = centroid - (centroid - offset) * scaleRatio
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            ) {
                Box(
                    Modifier
                        .width(20000.dp)
                        .height(20000.dp)
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y,
                            transformOrigin = TransformOrigin(0f, 0f)
                        )
                ) {
                    val minX = remember(nodes) { nodes.minOfOrNull { it.x } ?: 0f }
                    val maxX = remember(nodes) { nodes.maxOfOrNull { it.x + it.width } ?: 1000f }
                    Canvas(Modifier.fillMaxSize()) {
                        for (edge: CfgEdge in edges) {
                            val fromNode = nodes.firstOrNull { it.id == edge.from }
                            val nodeTo = nodes.firstOrNull { it.id == edge.to }
                            if (fromNode != null && nodeTo != null) {
                                drawNinjaEdge(fromNode, nodeTo, edge.type, nodeHeights, selectedEdge == edge, minX, maxX)
                            }
                        }
                    }

                    for (node: CfgNode in nodes) {
                        key(node.id) {
                            NodeBlock(
                                node = node,
                                nodeHeights = nodeHeights,
                                selectionStart = selectionStart,
                                selectionEnd = selectionEnd,
                                activeBreakpoints = activeBreakpoints,
                                activeWatchpoints = activeWatchpoints,
                                isEntry = node.startAddr == filterFunctionAddr,
                                 onLineClicked = { addr, len, isShift ->
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                    if (isShift && selectionStart != null) {
                                        val startIdx = instructions.indexOfFirst { it.instr.addr == selectionStart }
                                        val endIdx = instructions.indexOfFirst { it.instr.addr == addr }
                                        if (startIdx != -1 && endIdx != -1) {
                                            val lo = minOf(startIdx, endIdx)
                                            val hi = maxOf(startIdx, endIdx)
                                            onSelectionChanged?.invoke(instructions[lo].instr.addr, instructions[hi].instr.addr + instructions[hi].instr.length - 1)
                                        } else {
                                            onSelectionChanged?.invoke(selectionStart, addr + len - 1)
                                        }
                                    } else {
                                        onSelectionChanged?.invoke(addr, addr + len - 1)
                                    }
                                },
                                onLineRightClicked = { addr, screenPos ->
                                    contextMenuAddr = addr
                                    contextMenuOffset = DpOffset(
                                        (screenPos.x - containerPosition.x).dp / density,
                                        (screenPos.y - containerPosition.y).dp / density
                                    )
                                    showContextMenu = true
                                },
                                onDragSelection = { lineAddr, rowsOffset ->
                                    val startIdx = instructions.indexOfFirst { it.instr.addr == lineAddr }
                                    if (startIdx != -1) {
                                        val targetIdx = (startIdx + rowsOffset).coerceIn(0, instructions.size - 1)
                                        val targetLine = instructions[targetIdx]
                                        val anchor = selectionStart ?: lineAddr
                                        if (targetLine.instr.addr >= anchor) {
                                            onSelectionChanged?.invoke(anchor, targetLine.instr.addr + targetLine.instr.length - 1)
                                        } else {
                                            val lineLen = instructions.firstOrNull { it.instr.addr == lineAddr }?.instr?.length ?: 1
                                            onSelectionChanged?.invoke(anchor + lineLen - 1, targetLine.instr.addr)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }, offset = contextMenuOffset) {
            val addr = contextMenuAddr
            if (addr != null) {
                val contextLine = instructions.firstOrNull { it.instr.addr == addr }
                val start = selectionStart
                val end = selectionEnd
                val selectedLines = if (start != null && end != null) {
                    val lo = minOf(start, end)
                    val hi = maxOf(start, end)
                    instructions.filter { it.instr.addr in lo..hi }
                } else emptyList()

                DropdownMenuItem(
                    text = { Text("Copy Address(es)") },
                    onClick = {
                        val textToCopy = if (selectedLines.isNotEmpty()) {
                            selectedLines.joinToString("\n") { "0x" + it.instr.addr.toString(16).uppercase() }
                        } else {
                            "0x" + addr.toString(16).uppercase()
                        }
                        copyToClipboard(textToCopy)
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Copy Disassembly") },
                    onClick = {
                        val textToCopy = if (selectedLines.isNotEmpty()) {
                            selectedLines.joinToString("\n") { 
                                DisasmFormatter.getMnemonic(it.instr, it.bytes) + " " + DisasmFormatter.formatOperands(it.instr, it.bytes)
                            }
                        } else if (contextLine != null) {
                            DisasmFormatter.getMnemonic(contextLine.instr, contextLine.bytes) + " " + DisasmFormatter.formatOperands(contextLine.instr, contextLine.bytes)
                        } else ""
                        if (textToCopy.isNotEmpty()) {
                            copyToClipboard(textToCopy)
                        }
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Copy All") },
                    onClick = {
                        val textToCopy = if (selectedLines.isNotEmpty()) {
                            selectedLines.joinToString("\n") { 
                                String.format("0x%012X: %-10s %s", it.instr.addr, DisasmFormatter.getMnemonic(it.instr, it.bytes), DisasmFormatter.formatOperands(it.instr, it.bytes))
                            }
                        } else if (contextLine != null) {
                            String.format("0x%012X: %-10s %s", contextLine.instr.addr, DisasmFormatter.getMnemonic(contextLine.instr, contextLine.bytes), DisasmFormatter.formatOperands(contextLine.instr, contextLine.bytes))
                        } else ""
                        if (textToCopy.isNotEmpty()) {
                            copyToClipboard(textToCopy)
                        }
                        showContextMenu = false
                    }
                )

                HorizontalDivider(color = PS5ThemeColors.BorderColor)

                DropdownMenuItem(
                    text = { Text("Jump to Disassembly View", color = PS5ThemeColors.AccentCyan) },
                    onClick = {
                        onAddressClicked(addr)
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Jump to Hex View", color = PS5ThemeColors.AccentCyan) },
                    onClick = {
                        onJumpToHex(addr)
                        showContextMenu = false
                    }
                )

                if (isAttached) {
                    HorizontalDivider(color = PS5ThemeColors.BorderColor)
                    DropdownMenuItem(text = { Text("Set Software Breakpoint") }, onClick = {
                        onSetBreakpoint(addr)
                        showContextMenu = false
                    })
                    DropdownMenuItem(text = { Text("Set Hardware Watchpoint...") }, onClick = {
                        onSetWatchpoint(addr)
                        showContextMenu = false
                    })
                }
            }
        }

        Card(
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 260.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525).copy(alpha = 0.9f))
        ) {
            Row(Modifier.padding(4.dp), Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    val oldScale = scale
                    val newScale = (scale * 1.2f).coerceIn(0.05f, 4.0f)
                    val viewportCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                    val scaleRatio = newScale / oldScale
                    scale = newScale
                    offset = viewportCenter - (viewportCenter - offset) * scaleRatio
                }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                
                IconButton(onClick = {
                    val oldScale = scale
                    val newScale = (scale * 0.8f).coerceIn(0.05f, 4.0f)
                    val viewportCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                    val scaleRatio = newScale / oldScale
                    scale = newScale
                    offset = viewportCenter - (viewportCenter - offset) * scaleRatio
                }) { Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                
                IconButton(onClick = { 
                    scale = 0.7f
                    val topNode = nodes.minByOrNull { it.y }
                    if (topNode != null) offset = Offset(-(topNode.x * scale * density) + (400f * density), -(topNode.y * scale * density) + 100f)
                }) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
            }
        }
    }
}

@Composable
fun NodeBlock(
    node: CfgNode,
    nodeHeights: MutableMap<Int, Float>,
    selectionStart: Long?,
    selectionEnd: Long?,
    activeBreakpoints: Map<Int, Long>,
    activeWatchpoints: Map<Int, Long>,
    isEntry: Boolean,
    onLineClicked: (Long, Int, Boolean) -> Unit,
    onLineRightClicked: (Long, Offset) -> Unit,
    onDragSelection: ((Long, Int) -> Unit)? = null
) {
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    Box(
        Modifier
            .offset(x = node.x.dp, y = node.y.dp)
            .width(node.width.dp)
            .onGloballyPositioned { coordinates ->
                val h = coordinates.size.height / density
                if (nodeHeights[node.id] != h) {
                    nodeHeights[node.id] = h
                }
            }
            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
            .border(1.5.dp, node.borderColor, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = when {
                    node.isExternal -> "ext_${node.startAddr.toString(16).uppercase()}"
                    isEntry -> "entry_sub_${node.startAddr.toString(16).uppercase()}"
                    else -> "loc_${node.startAddr.toString(16).uppercase()}"
                },
                color = when {
                    node.isExternal -> Color(0xFF78909C)
                    isEntry -> Color(0xFF39D353)
                    else -> Color(0xFF64FFDA)
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFF333333))
            for (idx in node.instructions.indices) {
                val line = node.instructions[idx]
                val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                val hasBp = activeBreakpoints.values.contains(line.instr.addr) || activeWatchpoints.values.contains(line.instr.addr)
                
                var rowLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.3f) else Color.Transparent)
                        .onGloballyPositioned { rowLayoutCoords = it }
                        .pointerInput(line.instr.addr, isSelected, onLineClicked, onLineRightClicked) {
                            awaitPointerEventScope {
                                var touchStartPos: Offset? = null
                                var isLongPressActive = false
                                var hasTriggeredLongPress = false
                                var isDragging = false
                                var isSecondaryClick = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.first()
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            touchStartPos = change.position
                                            isDragging = false
                                            hasTriggeredLongPress = false
                                            val isSecondary = event.buttons.isSecondaryPressed
                                            isSecondaryClick = isSecondary
                                            
                                            if (isSecondary) {
                                                if (!isSelected) {
                                                    onLineClicked(line.instr.addr, line.instr.length, false)
                                                }
                                                rowLayoutCoords?.let { coords ->
                                                    onLineRightClicked(line.instr.addr, coords.localToWindow(change.position))
                                                }
                                            } else {
                                                isLongPressActive = true
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(500)
                                                    if (isLongPressActive) {
                                                        hasTriggeredLongPress = true
                                                        if (!isSelected) {
                                                            onLineClicked(line.instr.addr, line.instr.length, false)
                                                        }
                                                        rowLayoutCoords?.let { coords ->
                                                            onLineRightClicked(line.instr.addr, coords.localToWindow(change.position))
                                                        }
                                                        isLongPressActive = false
                                                    }
                                                }
                                            }
                                            change.consume()
                                        }
                                        PointerEventType.Move -> {
                                            val start = touchStartPos
                                            if (start != null) {
                                                val dragY = change.position.y - start.y
                                                val dragX = change.position.x - start.x
                                                val dragDistance = kotlin.math.sqrt(dragX * dragX + dragY * dragY)
                                                val dragThreshold = 16f
                                                if (dragDistance > dragThreshold) {
                                                    isLongPressActive = false
                                                    isDragging = true
                                                    
                                                    val rowHeightPx = 16f * density
                                                    val rowsOffset = (change.position.y / rowHeightPx).toInt()
                                                    onDragSelection?.invoke(line.instr.addr, rowsOffset)
                                                }
                                            }
                                        }
                                        PointerEventType.Release -> {
                                            isLongPressActive = false
                                            if (!hasTriggeredLongPress && !isDragging && !isSecondaryClick) {
                                                val isShift = event.keyboardModifiers.isShiftPressed
                                                onLineClicked(line.instr.addr, line.instr.length, isShift)
                                            }
                                            touchStartPos = null
                                        }
                                    }
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasBp) {
                        Box(Modifier.size(6.dp).background(Color.Red, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(4.dp))
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    val instr = line.instr
                    
                    val addrText = String.format("%012X", instr.addr)
                    Text(
                        text = addrText, 
                        color = Color(0xFF858585), 
                        modifier = Modifier.width(85.dp), 
                        fontSize = 10.sp, 
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )

                    val mnemonic = DisasmFormatter.getMnemonic(instr, line.bytes)
                    val operands = DisasmFormatter.formatOperands(instr, line.bytes)
                    
                    val mnemonicColor = when {
                        instr.isCall || instr.isRet -> Color(0xFFF50057)
                        instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081)
                        else -> Color(0xFFFFB74D)
                    }

                    Text(
                        text = mnemonic, 
                        color = mnemonicColor, 
                        modifier = Modifier.width(85.dp), 
                        fontSize = 10.sp, 
                        fontFamily = FontFamily.Monospace, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    
                    Spacer(Modifier.width(4.dp))
                    
                    val annotatedOps = buildAnnotatedString {
                        val regex = Regex("([\\[\\]\\+\\-\\*\\,\\s+])|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
                        val matches = regex.findAll(operands)
                        for (match in matches) {
                            val token = match.value
                            val isReg = DisasmFormatter.regNames.contains(token.lowercase())
                            val isNumber = token.startsWith("0x") || token.all { it.isDigit() }
                            
                            when {
                                isReg -> withStyle(style = SpanStyle(color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)) { append(token) }
                                isNumber -> withStyle(style = SpanStyle(color = Color(0xFFFF8A65))) { append(token) }
                                token == "[" || token == "]" -> withStyle(style = SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)) { append(token) }
                                else -> withStyle(style = SpanStyle(color = Color(0xFFECEFF1))) { append(token) }
                            }
                        }
                        if (matches.none()) append(operands)
                    }
                    
                    Text(
                        text = annotatedOps, 
                        fontSize = 10.sp, 
                        fontFamily = FontFamily.Monospace, 
                        maxLines = 1, 
                        softWrap = false,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun isInstructionSelected(instr: Ps5DisasmInstr, start: Long?, end: Long?): Boolean {
    if (start == null || end == null) return false
    val lo = minOf(start, end)
    val hi = maxOf(start, end)
    val instrEnd = instr.addr + instr.length - 1
    return (instr.addr in lo..hi) || (instrEnd in lo..hi) || (lo in instr.addr..instrEnd)
}

fun DrawScope.drawNinjaEdge(
    from: CfgNode,
    to: CfgNode,
    type: EdgeType,
    nodeHeights: Map<Int, Float>,
    isSelected: Boolean,
    minX: Float,
    maxX: Float
) {
    val d = density
    val color = if (isSelected) {
        Color(0xFFFFD54F) // Gold/Yellow highlight
    } else {
        when(type) {
            EdgeType.TRUE -> Color(0xFF4CAF50) // Green
            EdgeType.FALSE -> Color(0xFFF44336) // Red
            else -> Color(0xFF2196F3) // Blue
        }
    }
    val strokeWidth = if (isSelected) 4f * d else 2f * d
    
    // Distribute exit and entry points across the width to prevent overlaps and clashing arrowheads
    val exitOffset = (0.2f + (to.id % 7) * 0.1f) * from.width
    val startX = (from.x + exitOffset) * d
    
    val fromHeight = nodeHeights[from.id] ?: from.height
    val startY = (from.y + fromHeight) * d
    
    val entryOffset = (0.2f + (from.id % 7) * 0.1f) * to.width
    val endX = (to.x + entryOffset) * d
    val endY = to.y * d - 4f * d
    
    val path = Path().apply {
        moveTo(startX, startY)
        val edgeHash = from.id * 31 + to.id
        if (from.id == to.id) { // Self loop
            val loopOffset = (60f + (edgeHash % 3) * 15f) * d
            val vOffset = 20f * d
            lineTo(startX, startY + vOffset)
            lineTo(startX + loopOffset, startY + vOffset)
            lineTo(startX + loopOffset, endY - vOffset)
            lineTo(endX, endY - vOffset)
            lineTo(endX, endY)
        } else if (endY <= startY || endY > startY + 250f * d) { // Back edge, same-level, or long cross-layer forward edge
            val routeOffset = (40f + (edgeHash % 6) * 15f) * d
            val graphCenter = (minX + maxX) / 2f * d
            val edgeCenter = (startX + endX) / 2f
            val routeX = if (edgeCenter < graphCenter) minX * d - routeOffset else maxX * d + routeOffset
            val vOffset = (15f + (edgeHash % 5) * 6f) * d
            lineTo(startX, startY + vOffset)
            lineTo(routeX, startY + vOffset)
            lineTo(routeX, endY - vOffset)
            lineTo(endX, endY - vOffset)
            lineTo(endX, endY)
        } else { // Short forward branch (consecutive layer): route horizontally strictly in the gap below the source node
            val midY = startY + (15f + (edgeHash % 5) * 10f) * d
            lineTo(startX, midY)
            lineTo(endX, midY)
            lineTo(endX, endY)
        }
    }
    
    drawPath(path, color, style = Stroke(width = strokeWidth))
    
    // Arrow head
    val arrowSize = 10f * d
    val arrowPath = Path().apply {
        moveTo(endX, endY)
        lineTo(endX - arrowSize/2, endY - arrowSize)
        lineTo(endX + arrowSize/2, endY - arrowSize)
        close()
    }
    drawPath(arrowPath, color)
}

fun buildCfg(instructions: List<DisasmLine>, filterAddr: Long?): Pair<List<CfgNode>, List<CfgEdge>> {
    if (instructions.isEmpty()) return Pair(emptyList(), emptyList())
    
    // Pass 1: Strict Deduplication and Filtering
    val instrs = if (filterAddr != null) {
        val startIndex = instructions.indexOfFirst { it.instr.addr >= filterAddr }
        if (startIndex == -1) return Pair(emptyList(), emptyList())
        
        val subList = mutableListOf<DisasmLine>()
        for (i in startIndex until instructions.size) {
            val line = instructions[i]
            subList.add(line)
            if (line.instr.isRet) break
        }
        subList
    } else {
        instructions.distinctBy { it.instr.addr }.sortedBy { it.instr.addr }
    }
    
    if (instrs.isEmpty()) return Pair(emptyList(), emptyList())
    
    val addrToInstr = instrs.associateBy { it.instr.addr }
    
    // Pass 2: Identify Leaders strictly by Basic Block rules (jump targets + instructions after branch)
    val leaders = mutableSetOf<Long>()
    val entryPoint = filterAddr ?: instrs.first().instr.addr
    leaders.add(entryPoint)
    
    for (i in instrs.indices) {
        val line = instrs[i]
        val instr = line.instr
        if (instr.isJmp || instr.isCondJmp) {
            val target = DisasmFormatter.getJumpTarget(instr, line.bytes)
            if (target != 0L && addrToInstr.containsKey(target)) {
                leaders.add(target)
            } else if (instr.ripRelTarget != 0L && addrToInstr.containsKey(instr.ripRelTarget)) {
                leaders.add(instr.ripRelTarget)
            }
            if (i + 1 < instrs.size) {
                leaders.add(instrs[i+1].instr.addr)
            }
        } else if (instr.isRet) {
            if (i + 1 < instrs.size) {
                leaders.add(instrs[i+1].instr.addr)
            }
        }
    }
    
    // Pass 3: Build Basic Blocks (Nodes)
    val allNodes = mutableListOf<CfgNode>()
    var currentBlock = mutableListOf<DisasmLine>()
    for (line in instrs) {
        if (currentBlock.isNotEmpty() && leaders.contains(line.instr.addr)) {
            allNodes.add(CfgNode(allNodes.size, currentBlock, currentBlock.first().instr.addr, currentBlock.last().instr.addr))
            currentBlock = mutableListOf()
        }
        currentBlock.add(line)
    }
    if (currentBlock.isNotEmpty()) {
        allNodes.add(CfgNode(allNodes.size, currentBlock, currentBlock.first().instr.addr, currentBlock.last().instr.addr))
    }
    
    val startAddrToNodeId = allNodes.associateBy { it.startAddr }.mapValues { it.value.id }
    val allEdges = mutableListOf<CfgEdge>()
    val externalNodes = mutableListOf<CfgNode>()
    val externalNodesMap = mutableMapOf<Long, Int>()
    // Helper to resolve targets, creating stubs for external targets to ensure branches are visible
    fun getOrCreateTarget(addr: Long): Int? {
        startAddrToNodeId[addr]?.let { return it }
        if (addr == 0L) return null
        externalNodesMap[addr]?.let { return it }
        val id = allNodes.size + externalNodes.size
        externalNodesMap[addr] = id
        externalNodes.add(CfgNode(id, emptyList(), addr, addr, isExternal = true))
        return id
    }

    // Pass 4: Resolve Edges (Accurate Jcc logic)
    for (node in allNodes) {
        val lastLine = node.instructions.last()
        val last = lastLine.instr
        when {
            last.isCondJmp -> {
                val target = DisasmFormatter.getJumpTarget(last, lastLine.bytes)
                if (target != 0L) {
                    getOrCreateTarget(target)?.let { allEdges.add(CfgEdge(node.id, it, EdgeType.TRUE)) }
                } else if (last.ripRelTarget != 0L) {
                    getOrCreateTarget(last.ripRelTarget)?.let { allEdges.add(CfgEdge(node.id, it, EdgeType.TRUE)) }
                }
                startAddrToNodeId[last.addr + last.length]?.let { allEdges.add(CfgEdge(node.id, it, EdgeType.FALSE)) }
            }
            last.isJmp -> {
                val target = DisasmFormatter.getJumpTarget(last, lastLine.bytes)
                if (target != 0L) {
                    getOrCreateTarget(target)?.let { allEdges.add(CfgEdge(node.id, it, EdgeType.UNCONDITIONAL)) }
                } else if (last.ripRelTarget != 0L) {
                    getOrCreateTarget(last.ripRelTarget)?.let { allEdges.add(CfgEdge(node.id, it, EdgeType.UNCONDITIONAL)) }
                }
            }
            !last.isRet -> {
                // Sequential fallthrough
                startAddrToNodeId[last.addr + last.length]?.let { if (it != node.id) allEdges.add(CfgEdge(node.id, it, EdgeType.UNCONDITIONAL)) }
            }
        }
    }
    
    // Pass 5: Reachability Filtering from Entry
    val totalNodes = allNodes + externalNodes
    val startNode = allNodes.firstOrNull { it.startAddr == entryPoint } ?: allNodes.firstOrNull()
    val finalNodes: List<CfgNode>
    val finalEdges: List<CfgEdge>
    
    if (startNode != null) {
        val seen = mutableSetOf(startNode.id)
        val q = java.util.ArrayDeque<Int>().apply { add(startNode.id) }
        val edgesByFrom = allEdges.groupBy { it.from }
        while (q.isNotEmpty()) {
            val u = q.poll()
            edgesByFrom[u]?.forEach { if (seen.add(it.to)) q.add(it.to) }
        }
        finalNodes = totalNodes.filter { it.id in seen }
        finalEdges = allEdges.filter { it.from in seen && it.to in seen }
    } else {
        finalNodes = totalNodes
        finalEdges = allEdges
    }

    for (n in finalNodes) {
        val last = n.instructions.lastOrNull()?.instr
        n.borderColor = when {
            n.isExternal -> Color(0xFF78909C)
            n.startAddr == entryPoint -> Color(0xFF39D353)
            last?.isRet == true -> Color(0xFF00BFFF)
            else -> Color(0xFF444444)
        }
        n.width = 420f
        // Refined height calculation: 
        // 16 (Box padding) + 16 (Label) + 10 (Divider + padding) = 42.
        // We use 60 to be safe against sub-pixel scaling and larger system fonts.
        val instructionHeight = n.instructions.size * 16f
        n.height = (instructionHeight + 60f).coerceIn(44f, 15000f)
    }
    
    layoutNinja(finalNodes, finalEdges, entryPoint)
    return Pair(finalNodes, finalEdges)
}

fun layoutNinja(nodes: List<CfgNode>, edges: List<CfgEdge>, entryAddr: Long) {
    if (nodes.isEmpty()) return
    
    val layers = mutableMapOf<Int, Int>()
    nodes.forEach { layers[it.id] = 0 }

    val entryNode = nodes.firstOrNull { it.startAddr == entryAddr } ?: nodes[0]
    val worklist = java.util.ArrayDeque<Int>().apply { add(entryNode.id) }
    
    // Step 1: Compute depth ranking (BFS with visited set to handle loops/back-edges correctly)
    val visited = mutableSetOf<Int>()
    visited.add(entryNode.id)
    layers[entryNode.id] = 0
    val edgesByFrom = edges.groupBy { it.from }
    
    while (worklist.isNotEmpty()) {
        val u = worklist.poll()
        val currentRank = layers[u]!!
        edgesByFrom[u]?.forEach { edge ->
            if (edge.to !in visited) {
                visited.add(edge.to)
                layers[edge.to] = currentRank + 1
                worklist.add(edge.to)
            }
        }
    }

    // Step 2: Barycenter Sort for horizontal distribution
    val nodesByLayer = nodes.groupBy { layers[it.id] ?: 0 }.toSortedMap()
    val nodeX = mutableMapOf<Int, Float>()
    val edgesByTo = edges.groupBy { it.to }
    
    nodesByLayer.forEach { (layerIdx, layerNodes) ->
        val sortedNodes = if (layerIdx == 0) {
            layerNodes.sortedBy { it.startAddr }
        } else {
            // IDA-style: sort by average parent position to separate branches
            layerNodes.sortedBy { node ->
                val parents = edgesByTo[node.id] ?: emptyList()
                if (parents.isEmpty()) node.startAddr.toFloat()
                else parents.map { nodeX[it.from] ?: 0f }.average().toFloat()
            }
        }
        
        val totalWidth = sortedNodes.size * 420f + (sortedNodes.size - 1) * 150f
        var startX = -totalWidth / 2f
        sortedNodes.forEach { node ->
            node.x = startX
            nodeX[node.id] = startX
            startX += 420f + 150f
        }
    }

    // Step 2.5: Shift all nodes to the positive coordinate space
    val minX = nodes.minOfOrNull { it.x } ?: 0f
    val shiftX = 100f - minX
    nodes.forEach { node ->
        node.x += shiftX
        nodeX[node.id] = node.x
    }

    // Step 3: Vertical coord assignment
    var currentY = 100f
    nodesByLayer.forEach { (_, layerNodes) ->
        val maxHeight = layerNodes.maxOf { it.height }
        layerNodes.forEach { it.y = currentY }
        val spacing = 200f
        currentY = (currentY + maxHeight + spacing).coerceAtMost(200000f)
    }
}

private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val abx = bx - ax
    val aby = by - ay
    val apx = px - ax
    val apy = py - ay
    val ab2 = abx * abx + aby * aby
    if (ab2 == 0f) return kotlin.math.sqrt(apx * apx + apy * apy)
    var t = (apx * abx + apy * aby) / ab2
    t = t.coerceIn(0f, 1f)
    val qx = ax + t * abx
    val qy = ay + t * aby
    val dx = px - qx
    val dy = py - qy
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun findClickedEdge(
    clickPos: Offset,
    edges: List<CfgEdge>,
    nodes: List<CfgNode>,
    nodeHeights: Map<Int, Float>,
    d: Float,
    minX: Float,
    maxX: Float
): CfgEdge? {
    var bestEdge: CfgEdge? = null
    var bestDist = Float.MAX_VALUE
    
    for (edge in edges) {
        val from = nodes.firstOrNull { it.id == edge.from } ?: continue
        val to = nodes.firstOrNull { it.id == edge.to } ?: continue
        
        val exitOffset = (0.2f + (to.id % 7) * 0.1f) * from.width
        val startX = (from.x + exitOffset) * d
        
        val fromHeight = nodeHeights[from.id] ?: from.height
        val startY = (from.y + fromHeight) * d
        
        val entryOffset = (0.2f + (from.id % 7) * 0.1f) * to.width
        val endX = (to.x + entryOffset) * d
        val endY = to.y * d - 4f * d
        
        val edgeHash = from.id * 31 + to.id
        val dist = when {
            from.id == to.id -> {
                val loopOffset = (60f + (edgeHash % 3) * 15f) * d
                val vOffset = 20f * d
                val d1 = distanceToSegment(clickPos.x, clickPos.y, startX, startY, startX, startY + vOffset)
                val d2 = distanceToSegment(clickPos.x, clickPos.y, startX, startY + vOffset, startX + loopOffset, startY + vOffset)
                val d3 = distanceToSegment(clickPos.x, clickPos.y, startX + loopOffset, startY + vOffset, startX + loopOffset, endY - vOffset)
                val d4 = distanceToSegment(clickPos.x, clickPos.y, startX + loopOffset, endY - vOffset, endX, endY - vOffset)
                val d5 = distanceToSegment(clickPos.x, clickPos.y, endX, endY - vOffset, endX, endY)
                minOf(d1, d2, d3, d4, d5)
            }
            endY <= startY || endY > startY + 250f * d -> {
                val routeOffset = (40f + (edgeHash % 6) * 15f) * d
                val graphCenter = (minX + maxX) / 2f * d
                val edgeCenter = (startX + endX) / 2f
                val routeX = if (edgeCenter < graphCenter) minX * d - routeOffset else maxX * d + routeOffset
                val vOffset = (15f + (edgeHash % 5) * 6f) * d
                val d1 = distanceToSegment(clickPos.x, clickPos.y, startX, startY, startX, startY + vOffset)
                val d2 = distanceToSegment(clickPos.x, clickPos.y, startX, startY + vOffset, routeX, startY + vOffset)
                val d3 = distanceToSegment(clickPos.x, clickPos.y, routeX, startY + vOffset, routeX, endY - vOffset)
                val d4 = distanceToSegment(clickPos.x, clickPos.y, routeX, endY - vOffset, endX, endY - vOffset)
                val d5 = distanceToSegment(clickPos.x, clickPos.y, endX, endY - vOffset, endX, endY)
                minOf(d1, d2, d3, d4, d5)
            }
            else -> {
                val midY = startY + (15f + (edgeHash % 5) * 10f) * d
                val d1 = distanceToSegment(clickPos.x, clickPos.y, startX, startY, startX, midY)
                val d2 = distanceToSegment(clickPos.x, clickPos.y, startX, midY, endX, midY)
                val d3 = distanceToSegment(clickPos.x, clickPos.y, endX, midY, endX, endY)
                minOf(d1, d2, d3)
            }
        }
        
        if (dist < 15f * d && dist < bestDist) {
            bestDist = dist
            bestEdge = edge
        }
    }
    return bestEdge
}
