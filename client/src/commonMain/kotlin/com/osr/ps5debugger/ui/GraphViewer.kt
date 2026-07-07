package com.osr.ps5debugger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.unit.dp
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
    var width: Float = 400f,
    var height: Float = 0f,
    var borderColor: Color = Color(0xFF444444)
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
    modifier: Modifier = Modifier,
    selectionStart: Long? = null,
    selectionEnd: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null,
    onAddressClicked: (Long) -> Unit = {},
    isAttached: Boolean = false,
    activeBreakpoints: Map<Int, Long> = emptyMap(),
    activeWatchpoints: Map<Int, Long> = emptyMap(),
    onSetBreakpoint: (Long) -> Unit = {},
    onSetWatchpoint: (Long) -> Unit = {}
) {
    val density = LocalDensity.current.density
    
    val cfg = remember(instructions.size, filterFunctionAddr, instructions.firstOrNull()?.instr?.addr) {
        buildCfg(instructions, filterFunctionAddr)
    }
    val nodes = cfg.first
    val edges = cfg.second

    var scale by remember(filterFunctionAddr) { mutableStateOf(0.7f) }
    var offset by remember(filterFunctionAddr) { mutableStateOf(Offset.Zero) }
    
    // Auto-centering only when the selected function changes
    var lastCenteredId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(nodes.size, filterFunctionAddr) {
        if (nodes.isNotEmpty() && lastCenteredId != filterFunctionAddr) {
            val entryNode = if (filterFunctionAddr != null) nodes.firstOrNull { it.startAddr == filterFunctionAddr } else nodes.firstOrNull()
            if (entryNode != null) {
                scale = 0.7f
                offset = Offset(-(entryNode.x * scale * density) + (400f * density), -(entryNode.y * scale * density) + 100f)
            }
            lastCenteredId = filterFunctionAddr
        }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    var containerPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg)
            .clipToBounds()
            .onGloballyPositioned { containerPosition = it.localToWindow(Offset.Zero) }
    ) {
        if (isLoading && nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = PS5ThemeColors.AccentCyan) }
        } else if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No logic blocks found", color = PS5ThemeColors.TextMuted) }
        } else {
            // Pan gesture layer
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(filterFunctionAddr) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.05f, 4.0f)
                            offset += pan
                        }
                    }
                    .pointerInput(filterFunctionAddr) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val delta = event.changes.first().scrollDelta.y
                                    val zoom = if (delta > 0) 0.9f else 1.1f
                                    scale = (scale * zoom).coerceIn(0.05f, 4.0f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y,
                            transformOrigin = TransformOrigin(0f, 0f)
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (edge: CfgEdge in edges) {
                            val fromNode = nodes.firstOrNull { it.id == edge.from }
                            val nodeTo = nodes.firstOrNull { it.id == edge.to }
                            if (fromNode != null && nodeTo != null) {
                                drawNinjaEdge(fromNode, nodeTo, edge.type)
                            }
                        }
                    }

                    for (node: CfgNode in nodes) {
                        key(node.id) {
                            NodeBlock(
                                node = node,
                                selectionStart = selectionStart,
                                selectionEnd = selectionEnd,
                                activeBreakpoints = activeBreakpoints,
                                activeWatchpoints = activeWatchpoints,
                                onLineClicked = { addr, len, isShift ->
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
                                }
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }, offset = contextMenuOffset) {
            DropdownMenuItem(text = { Text("Copy Address") }, onClick = {
                contextMenuAddr?.let { copyToClipboard(String.format("0x%012X", it)) }
                showContextMenu = false
            })
            if (isAttached) {
                DropdownMenuItem(text = { Text("Set Software Breakpoint") }, onClick = {
                    contextMenuAddr?.let { onSetBreakpoint(it) }
                    showContextMenu = false
                })
                DropdownMenuItem(text = { Text("Set Hardware Watchpoint...") }, onClick = {
                    contextMenuAddr?.let { onSetWatchpoint(it) }
                    showContextMenu = false
                })
            }
        }

        Card(
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 260.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525).copy(alpha = 0.9f))
        ) {
            Row(Modifier.padding(4.dp), Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { scale *= 1.2f }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                IconButton(onClick = { scale *= 0.8f }) { Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                IconButton(onClick = { 
                    scale = 0.7f
                    val topNode = nodes.minByOrNull { it.y }
                    if (topNode != null) offset = Offset(-(topNode.x * scale * density) + 400f, -(topNode.y * scale * density) + 100f)
                }) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
            }
        }
    }
}

@Composable
fun NodeBlock(
    node: CfgNode,
    selectionStart: Long?,
    selectionEnd: Long?,
    activeBreakpoints: Map<Int, Long>,
    activeWatchpoints: Map<Int, Long>,
    onLineClicked: (Long, Int, Boolean) -> Unit,
    onLineRightClicked: (Long, Offset) -> Unit
) {
    Box(
        Modifier
            .offset(x = node.x.dp, y = node.y.dp)
            .width(node.width.dp)
            .height(node.height.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
            .border(1.5.dp, node.borderColor, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Column {
            Text("sub_${node.startAddr.toString(16).uppercase()}:", color = Color(0xFF64FFDA), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFF333333))
            for (line: DisasmLine in node.instructions) {
                val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                val hasBp = activeBreakpoints.values.contains(line.instr.addr) || activeWatchpoints.values.contains(line.instr.addr)
                
                var rowLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.3f) else Color.Transparent)
                        .onGloballyPositioned { rowLayoutCoords = it }
                        .pointerInput(line.instr.addr, selectionStart, selectionEnd, onLineClicked, onLineRightClicked) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press) {
                                        val change = event.changes.first()
                                        val isShift = event.keyboardModifiers.isShiftPressed
                                        val isSecondary = event.buttons.isSecondaryPressed
                                        
                                        if (isSecondary) {
                                            rowLayoutCoords?.let { coords ->
                                                onLineRightClicked(line.instr.addr, coords.localToWindow(change.position))
                                            }
                                        } else {
                                            onLineClicked(line.instr.addr, line.instr.length, isShift)
                                        }
                                        change.consume()
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
                        Spacer(Modifier.width(10.dp))
                    }
                    
                    val instr = line.instr
                    val addrHex = instr.addr.toString(16).uppercase()
                    val bytesHex = line.bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    
                    Text(addrHex, color = Color(0xFF858585), modifier = Modifier.width(90.dp), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(bytesHex, color = Color(0xFF606060), modifier = Modifier.width(110.dp), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)

                    val mnemonic = DisasmFormatter.getMnemonic(instr)
                    val operands = DisasmFormatter.formatOperands(instr)
                    
                    val mnemonicColor = when {
                        instr.isCall || instr.isRet -> Color(0xFFF50057)
                        instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081)
                        else -> Color(0xFFFFB74D)
                    }

                    Text(mnemonic, color = mnemonicColor, modifier = Modifier.width(60.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    
                    val annotatedOps = buildAnnotatedString {
                        val regex = Regex("(\\[|\\]|\\+|\\-|\\*|,|\\s+)|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
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
                    
                    Text(annotatedOps, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, color = Color.White)
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

fun getMnemonicColor(instr: Ps5DisasmInstr) = when {
    instr.isCall || instr.isRet -> Color(0xFFF50057)
    instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081)
    else -> Color(0xFFE0E0E0)
}

fun DrawScope.drawNinjaEdge(from: CfgNode, to: CfgNode, type: EdgeType) {
    val d = density
    val color = when(type) {
        EdgeType.TRUE -> Color(0xFF4CAF50) // Green
        EdgeType.FALSE -> Color(0xFFF44336) // Red
        else -> Color(0xFF2196F3) // Blue
    }
    
    val startX = (from.x + from.width / 2f) * d
    val startY = (from.y + from.height) * d
    val endX = (to.x + to.width / 2f) * d
    val endY = to.y * d
    
    val path = Path().apply {
        moveTo(startX, startY)
        if (from.id == to.id) { // Self loop
            cubicTo(startX + 60*d, startY + 60*d, startX + 60*d, startY - 60*d, startX + 10*d, startY - 5*d)
        } else if (endY > startY) { // Forward edge
            val midY = startY + (endY - startY) / 2f
            cubicTo(startX, midY, endX, midY, endX, endY)
        } else if (kotlin.math.abs(endY - startY) < 10f) { // Same level
            val midX = (startX + endX) / 2f
            val curveHeight = 40f * d
            cubicTo(startX, startY + curveHeight, endX, endY + curveHeight, endX, endY)
        } else { // Back edge
            val curveOffset = if (startX <= endX) -100f * d else 100f * d
            val verticalOffset = 40f * d
            cubicTo(startX, startY + verticalOffset, startX + curveOffset, startY + verticalOffset, startX + curveOffset, (startY + endY) / 2f)
            cubicTo(startX + curveOffset, endY - verticalOffset, endX, endY - verticalOffset, endX, endY)
        }
    }
    
    drawPath(path, color, style = Stroke(width = 2.5f * d))
    
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
    val instrs = instructions.sortedBy { it.instr.addr }
    
    // 1. Identify leaders (start of basic blocks)
    val leaders = mutableSetOf<Long>()
    leaders.add(instrs.first().instr.addr)
    for (i in instrs.indices) {
        val instr = instrs[i].instr
        val mnemonic = DisasmFormatter.getMnemonic(instr).uppercase().trim()
        
        if (instr.isJmp || instr.isCondJmp || instr.isRet || mnemonic == "JMP" || (mnemonic.startsWith("J") && mnemonic != "JNOP")) {
            if (instr.ripRelTarget != 0L) {
                leaders.add(instr.ripRelTarget)
            }
            if (i + 1 < instrs.size) {
                leaders.add(instrs[i+1].instr.addr)
            }
        }
    }
    
    // 2. Build Basic Blocks (Nodes)
    val allNodes = mutableListOf<CfgNode>()
    var currentBlockInstrs = mutableListOf<DisasmLine>()
    for (line in instrs) {
        if (currentBlockInstrs.isNotEmpty() && leaders.contains(line.instr.addr)) {
            allNodes.add(CfgNode(allNodes.size, currentBlockInstrs, currentBlockInstrs.first().instr.addr, currentBlockInstrs.last().instr.addr))
            currentBlockInstrs = mutableListOf()
        }
        currentBlockInstrs.add(line)
    }
    if (currentBlockInstrs.isNotEmpty()) {
        allNodes.add(CfgNode(allNodes.size, currentBlockInstrs, currentBlockInstrs.first().instr.addr, currentBlockInstrs.last().instr.addr))
    }
    
    val addrToNodeId = mutableMapOf<Long, Int>()
    for (n in allNodes) {
        for (l in n.instructions) {
            addrToNodeId[l.instr.addr] = n.id
        }
    }
    
    // 3. Resolve Edges
    val allEdges = mutableListOf<CfgEdge>()
    for (n in allNodes) {
        val lastLine = n.instructions.lastOrNull() ?: continue
        val last = lastLine.instr
        val mnemonic = DisasmFormatter.getMnemonic(last).uppercase().trim()
        
        val isUnconditional = last.isJmp || mnemonic == "JMP"
        val isConditional = last.isCondJmp || (mnemonic.startsWith("J") && !isUnconditional && mnemonic != "JNOP")
        val isReturn = last.isRet || mnemonic == "RET"

        if (isConditional) {
            addrToNodeId[last.ripRelTarget]?.let { allEdges.add(CfgEdge(n.id, it, EdgeType.TRUE)) }
            addrToNodeId[last.addr + last.length]?.let { allEdges.add(CfgEdge(n.id, it, EdgeType.FALSE)) }
        } else if (isUnconditional) {
            addrToNodeId[last.ripRelTarget]?.let { allEdges.add(CfgEdge(n.id, it, EdgeType.UNCONDITIONAL)) }
        } else if (!isReturn) {
            // Fallthrough to next block
            addrToNodeId[last.addr + last.length]?.let { if (it != n.id) allEdges.add(CfgEdge(n.id, it, EdgeType.UNCONDITIONAL)) }
        }
    }
    
    // 4. Filter Reachability
    val reachableNodes: List<CfgNode>
    val reachableEdges: List<CfgEdge>
    if (filterAddr != null) {
        val root = allNodes.firstOrNull { it.startAddr == filterAddr }
        if (root != null) {
            val seen = mutableSetOf(root.id)
            val q = java.util.ArrayDeque<Int>().apply { add(root.id) }
            while (q.isNotEmpty()) {
                val u = q.poll()
                allEdges.filter { it.from == u }.forEach { if (seen.add(it.to)) q.add(it.to) }
            }
            reachableNodes = allNodes.filter { it.id in seen }
            reachableEdges = allEdges.filter { it.from in seen && it.to in seen }
        } else {
            reachableNodes = allNodes
            reachableEdges = allEdges
        }
    } else {
        reachableNodes = allNodes
        reachableEdges = allEdges
    }

    for (n in reachableNodes) {
        val last = n.instructions.last().instr
        val isRet = last.isRet || DisasmFormatter.getMnemonic(last).uppercase() == "RET"
        n.borderColor = when {
            n.startAddr == filterAddr -> Color(0xFF39D353)
            isRet -> Color(0xFF00BFFF)
            reachableEdges.none { it.from == n.id } -> Color(0xFFF85149)
            else -> Color(0xFF444444)
        }
        // Set fixed dimensions for layout
        n.width = 400f
        n.height = n.instructions.size * 16f + 40f
    }
    
    layoutNinja(reachableNodes, reachableEdges, filterAddr)
    return Pair(reachableNodes, reachableEdges)
}

fun layoutNinja(nodes: List<CfgNode>, edges: List<CfgEdge>, entryAddr: Long?) {
    if (nodes.isEmpty()) return
    
    val layers = mutableMapOf<Int, Int>()
    nodes.forEach { layers[it.id] = 0 }

    // Topological Ranking
    val inDegree = mutableMapOf<Int, Int>().withDefault { 0 }
    edges.forEach { inDegree[it.to] = inDegree.getValue(it.to) + 1 }
    
    val roots = nodes.filter { inDegree.getValue(it.id) == 0 }
    val queue = java.util.ArrayDeque<Int>()
    
    // Prioritize entryAddr node
    val entryNode = nodes.firstOrNull { it.startAddr == entryAddr }
    if (entryNode != null) {
        queue.add(entryNode.id)
    }
    roots.forEach { if (it.id != entryNode?.id) queue.add(it.id) }
    
    if (queue.isEmpty()) queue.add(nodes[0].id)

    val maxIters = nodes.size * nodes.size
    var iters = 0
    while (queue.isNotEmpty() && iters < maxIters) {
        val u = queue.poll()
        val currentRank = layers[u] ?: 0
        iters++
        
        edges.filter { it.from == u }.forEach { edge ->
            if (layers[edge.to]!! < currentRank + 1) {
                layers[edge.to] = currentRank + 1
                queue.add(edge.to)
            }
        }
    }

    // Horizontal Alignment
    val nodesByLayer = nodes.groupBy { layers[it.id] ?: 0 }.toSortedMap()
    var currentY = 50f
    
    for (layer in nodesByLayer.values) {
        val sortedLayer = layer.sortedBy { it.startAddr }
        val layerWidth = sortedLayer.size * 400f + (sortedLayer.size - 1) * 80f
        var currentX = -layerWidth / 2f
        var maxHeight = 0f
        
        for (node in sortedLayer) {
            node.x = currentX
            node.y = currentY
            currentX += 400f + 80f
            maxHeight = maxOf(maxHeight, node.height)
        }
        currentY += maxHeight + 120f
    }
}
