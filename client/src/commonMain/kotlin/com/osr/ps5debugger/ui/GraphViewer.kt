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
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import com.osr.ps5debugger.util.copyToClipboard

data class CfgNode(
    val id: Int,
    val instructions: List<DisasmLine>,
    val startAddr: Long,
    val endAddr: Long,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f
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
            val minX = nodes.minOf { it.x }
            val maxX = nodes.maxOf { it.x + it.width }
            val minY = nodes.minOf { it.y }
            val graphWidth = maxX - minX
            
            scale = 0.7f
            offset = Offset(-((minX + graphWidth / 2f) * scale * density) + (400f * density), -(minY * scale * density) + 100f)
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
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.05f, 4.0f)
                            offset += pan
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
    var blockWindowPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    Box(
        Modifier
            .offset(x = node.x.dp, y = node.y.dp)
            .width(node.width.dp)
            .height(node.height.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
            .padding(8.dp)
            .onGloballyPositioned { blockWindowPosition = it.localToWindow(Offset.Zero) }
    ) {
        Column {
            Text("sub_${node.startAddr.toString(16).uppercase()}:", color = Color(0xFF64FFDA), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFF333333))
            for (line: DisasmLine in node.instructions) {
                val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                val hasBp = activeBreakpoints.values.contains(line.instr.addr) || activeWatchpoints.values.contains(line.instr.addr)
                
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.3f) else Color.Transparent)
                        .pointerInput(line.instr.addr) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press) {
                                        val change = event.changes.first()
                                        if (event.buttons.isSecondaryPressed) {
                                            onLineRightClicked(line.instr.addr, blockWindowPosition + change.position)
                                        } else {
                                            val isShift = event.keyboardModifiers.isShiftPressed
                                            onLineClicked(line.instr.addr, line.instr.length, isShift)
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
                        Spacer(Modifier.width(10.dp))
                    }
                    
                    val instr = line.instr
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
                    
                    Text(annotatedOps, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
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
        EdgeType.TRUE -> Color(0xFF00FF00) // GREEN
        EdgeType.FALSE -> Color(0xFFFF0000) // RED
        EdgeType.UNCONDITIONAL -> Color(0xFF00BFFF) // BLUE
    }
    
    val xOff = when(type) {
        EdgeType.TRUE -> -50f * d
        EdgeType.FALSE -> 50f * d
        else -> 0f
    }
    
    val start = Offset((from.x + from.width/2f) * d + xOff, (from.y + from.height) * d)
    val end = Offset((to.x + to.width/2f) * d, to.y * d)
    
    val path = Path().apply {
        moveTo(start.x, start.y)
        if (from.id == to.id) {
            cubicTo(start.x + 80f*d, start.y + 80f*d, start.x + 80f*d, start.y - 80f*d, start.x + 15f*d, start.y - 5f*d)
        } else if (end.y > start.y) {
            val midY = start.y + (end.y - start.y) / 2f
            lineTo(start.x, midY)
            lineTo(end.x, midY)
            lineTo(end.x, end.y)
        } else {
            val side = if (start.x < end.x) -160f*d else 160f*d
            lineTo(start.x, start.y + 30f*d)
            lineTo(start.x + side, start.y + 30f*d)
            lineTo(start.x + side, end.y - 30f*d)
            lineTo(end.x, end.y - 30f*d)
            lineTo(end.x, end.y)
        }
    }
    drawPath(path, color, style = Stroke(3f * d))
    
    val sz = 12f * d
    val arrow = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - sz/1.5f, end.y - sz)
        lineTo(end.x + sz/1.5f, end.y - sz)
        close()
    }
    drawPath(arrow, color)
}

fun buildCfg(instructions: List<DisasmLine>, filterAddr: Long?): Pair<List<CfgNode>, List<CfgEdge>> {
    if (instructions.isEmpty()) return Pair(emptyList<CfgNode>(), emptyList<CfgEdge>())
    val instrs = instructions.sortedBy { it.instr.addr }
    
    val leaders = mutableSetOf<Long>()
    leaders.add(instrs.first().instr.addr)
    for (i in instrs.indices) {
        val instr = instrs[i].instr
        if (instr.isJmp || instr.isCondJmp || instr.isRet) {
            if (instr.ripRelTarget != 0L) leaders.add(instr.ripRelTarget)
            if (i + 1 < instrs.size) leaders.add(instrs[i+1].instr.addr)
        }
    }
    
    val allNodes = mutableListOf<CfgNode>()
    var cur = mutableListOf<DisasmLine>()
    for (line in instrs) {
        if (cur.isNotEmpty() && leaders.contains(line.instr.addr)) {
            allNodes.add(CfgNode(allNodes.size, cur, cur.first().instr.addr, cur.last().instr.addr))
            cur = mutableListOf()
        }
        cur.add(line)
    }
    if (cur.isNotEmpty()) allNodes.add(CfgNode(allNodes.size, cur, cur.first().instr.addr, cur.last().instr.addr))
    
    val addrToNodeId = mutableMapOf<Long, Int>()
    for (n: CfgNode in allNodes) {
        for (l: DisasmLine in n.instructions) addrToNodeId[l.instr.addr] = n.id
    }
    
    val allEdges = mutableListOf<CfgEdge>()
    for (n: CfgNode in allNodes) {
        val lastLine = n.instructions.lastOrNull() ?: continue
        val last = lastLine.instr
        if (last.isCondJmp) {
            val tId = addrToNodeId[last.ripRelTarget]
            if (tId != null) allEdges.add(CfgEdge(n.id, tId, EdgeType.TRUE))
            val fId = addrToNodeId[last.addr + last.length]
            if (fId != null) allEdges.add(CfgEdge(n.id, fId, EdgeType.FALSE))
        } else if (last.isJmp) {
            val tId = addrToNodeId[last.ripRelTarget]
            if (tId != null) allEdges.add(CfgEdge(n.id, tId, EdgeType.UNCONDITIONAL))
        } else if (!last.isRet) {
            val nId = addrToNodeId[last.addr + last.length]
            if (nId != null && nId != n.id) allEdges.add(CfgEdge(n.id, nId, EdgeType.UNCONDITIONAL))
        }
    }
    
    val filtered = if (filterAddr != null) {
        val root = allNodes.firstOrNull { it.startAddr == filterAddr }
        if (root != null) {
            val seen = mutableSetOf(root.id)
            val q = java.util.ArrayDeque<Int>()
            q.add(root.id)
            while(q.isNotEmpty()) {
                val u = q.poll()
                for (edge in allEdges) {
                    if (edge.from == u && seen.add(edge.to)) q.add(edge.to)
                }
            }
            Pair(allNodes.filter { it.id in seen }, allEdges.filter { it.from in seen })
        } else Pair(allNodes, allEdges)
    } else Pair(allNodes, allEdges)
    
    layoutNinja(filtered.first, filtered.second)
    return filtered
}

fun layoutNinja(nodes: List<CfgNode>, edges: List<CfgEdge>) {
    if (nodes.isEmpty()) return
    val layers = mutableMapOf<Int, Int>()
    val adj = nodes.associate { n: CfgNode -> n.id to edges.filter { e: CfgEdge -> e.from == n.id }.map { it.to } }
    val inDegree = mutableMapOf<Int, Int>().withDefault { 0 }
    for (edge in edges) inDegree[edge.to] = inDegree.getValue(edge.to) + 1
    
    val q = java.util.ArrayDeque<Int>()
    for (node in nodes) {
        if (inDegree.getValue(node.id) == 0) {
            q.add(node.id)
            layers[node.id] = 0
        }
    }
    if (q.isEmpty() && nodes.isNotEmpty()) { q.add(nodes[0].id); layers[nodes[0].id] = 0 }
    
    while(q.isNotEmpty()) {
        val u = q.poll()
        val l = layers[u] ?: 0
        adj[u]?.forEach { v -> if ((layers[v] ?: -1) < l + 1) { layers[v] = l + 1; q.add(v) } }
    }
    
    val nByL = nodes.groupBy { layers[it.id] ?: 0 }.toSortedMap()
    for (node in nodes) {
        node.width = 340f
        node.height = node.instructions.size * 18f + 55f
    }
    
    var curY = 50f
    for (entry in nByL) {
        val sorted = entry.value.sortedBy { it.startAddr }
        val layerW = sorted.size * 340f + (sorted.size - 1) * 220f
        var curX = -layerW / 2f
        var maxH = 0f
        for (node in sorted) {
            node.x = curX; node.y = curY
            curX += 340f + 220f
            maxH = maxOf(maxH, node.height)
        }
        curY += maxH + 160f
    }
}
