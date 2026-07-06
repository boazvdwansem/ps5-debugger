package com.osr.ps5debugger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.ui.disasm.DisasmFormatter

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
    modifier: Modifier = Modifier,
    onAddressClicked: (Long) -> Unit = {}
) {
    val cfg = remember(instructions) { buildCfg(instructions) }
    val nodes = cfg.first
    val edges = cfg.second

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize().background(PS5ThemeColors.DarkBg)) {
        if (nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No disassembly loaded for graph generation", color = PS5ThemeColors.TextMuted)
            }
            return@Box
        }

        // Panning & Zooming Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.15f, 3.0f)
                        offset += pan
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    )
            ) {
                // 1. Draw Edges on Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (edge in edges) {
                        val nodeFrom = nodes.firstOrNull { it.id == edge.from }
                        val nodeTo = nodes.firstOrNull { it.id == edge.to }
                        if (nodeFrom != null && nodeTo != null) {
                            val startX = nodeFrom.x + nodeFrom.width / 2f
                            val startY = nodeFrom.y + nodeFrom.height
                            val endX = nodeTo.x + nodeTo.width / 2f
                            val endY = nodeTo.y

                            val color = when (edge.type) {
                                EdgeType.TRUE -> Color(0xFF4CAF50) // Green
                                EdgeType.FALSE -> Color(0xFFF44336) // Red
                                EdgeType.UNCONDITIONAL -> Color(0xFF2196F3) // Blue
                            }

                            drawCfgArrow(Offset(startX, startY), Offset(endX, endY), color)
                        }
                    }
                }

                // 2. Render Nodes as Interactive Boxes
                for (node in nodes) {
                    Box(
                        modifier = Modifier
                            .offset(x = node.x.dp, y = node.y.dp)
                            .width(node.width.dp)
                            .height(node.height.dp)
                            .background(PS5ThemeColors.SecondaryBg, shape = RoundedCornerShape(6.dp))
                            .border(1.dp, PS5ThemeColors.BorderColor, shape = RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            // Header showing block range
                            Text(
                                text = String.format("Block_%d (0x%X - 0x%X)", node.id, node.startAddr, node.endAddr),
                                fontSize = 10.sp,
                                color = PS5ThemeColors.TextMuted,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            HorizontalDivider(color = PS5ThemeColors.BorderColor, modifier = Modifier.padding(bottom = 6.dp))

                            // Render instruction lines
                            for (line in node.instructions) {
                                val instr = line.instr
                                val mnemonic = DisasmFormatter.getMnemonic(instr)
                                val operands = DisasmFormatter.formatOperands(instr)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAddressClicked(instr.addr) }
                                        .padding(vertical = 1.dp)
                                ) {
                                    Text(
                                        text = String.format("%08X", instr.addr),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF90A4AE),
                                        modifier = Modifier.width(75.dp)
                                    )
                                    
                                    Text(
                                        text = mnemonic,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            instr.isCall || instr.isRet -> Color(0xFFF50057)
                                            instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081)
                                            else -> Color(0xFFFFB74D)
                                        },
                                        modifier = Modifier.width(55.dp)
                                    )
                                    
                                    val annotatedOps = buildAnnotatedString {
                                        val regex = Regex("(\\[|\\]|\\+|\\-|\\*|,|\\s+)|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
                                        val matches = regex.findAll(operands)
                                        for (match in matches) {
                                            val token = match.value
                                            val isReg = DisasmFormatter.regNames.contains(token.lowercase())
                                            val isNumber = token.startsWith("0x") || token.all { it.isDigit() }
                                            
                                            when {
                                                isReg -> withStyle(SpanStyle(color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)) { append(token) }
                                                isNumber -> withStyle(SpanStyle(color = Color(0xFFFF8A65))) { append(token) }
                                                token == "[" || token == "]" -> withStyle(SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)) { append(token) }
                                                else -> withStyle(SpanStyle(color = Color(0xFFECEFF1))) { append(token) }
                                            }
                                        }
                                        if (matches.none()) append(operands)
                                    }

                                    Text(
                                        text = annotatedOps,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Control Overlay Toolbar
        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { scale = (scale + 0.1f).coerceAtMost(3.0f) }) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = PS5ThemeColors.TextMain)
                }
                IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.15f) }) {
                    Text("-", fontSize = 24.sp, color = PS5ThemeColors.TextMain, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom", tint = PS5ThemeColors.TextMain)
                }
            }
        }
    }
}

fun DrawScope.drawCfgArrow(start: Offset, end: Offset, color: Color) {
    // We draw edges in Dp units on nodes, but Canvas works in pixels.
    // Convert dp values from nodes coordinates to pixels by multiplying by density.
    val startPx = Offset(start.x * density, start.y * density)
    val endPx = Offset(end.x * density, end.y * density)

    // Draw straight line or smooth orthagonal connection
    if (startPx.x == endPx.x) {
        drawLine(color = color, start = startPx, end = endPx, strokeWidth = 2f)
    } else {
        // Draw standard cubic bezier path or structured corner line
        val midY = startPx.y + (endPx.y - startPx.y) / 2f
        val p1 = Offset(startPx.x, midY)
        val p2 = Offset(endPx.x, midY)
        drawLine(color = color, start = startPx, end = p1, strokeWidth = 2f)
        drawLine(color = color, start = p1, end = p2, strokeWidth = 2f)
        drawLine(color = color, start = p2, end = endPx, strokeWidth = 2f)
    }

    // Draw arrowhead at endPx
    val arrowLen = 12f
    val arrowAngle = Math.PI / 6 // 30 degrees
    val angle = Math.PI / 2 // Downward pointing arrow

    val p1 = Offset(
        endPx.x - arrowLen * kotlin.math.cos(angle - arrowAngle).toFloat(),
        endPx.y - arrowLen * kotlin.math.sin(angle - arrowAngle).toFloat()
    )
    val p2 = Offset(
        endPx.x - arrowLen * kotlin.math.cos(angle + arrowAngle).toFloat(),
        endPx.y - arrowLen * kotlin.math.sin(angle + arrowAngle).toFloat()
    )

    drawLine(color = color, start = endPx, end = p1, strokeWidth = 2f)
    drawLine(color = color, start = endPx, end = p2, strokeWidth = 2f)
}

fun buildCfg(instructions: List<DisasmLine>): Pair<List<CfgNode>, List<CfgEdge>> {
    if (instructions.isEmpty()) return Pair(emptyList(), emptyList())
    
    val instrs = instructions.sortedBy { it.instr.addr }
    
    // 1. Find all leaders (starting address of basic blocks)
    val leaders = mutableSetOf<Long>()
    leaders.add(instrs.first().instr.addr) // First instruction is always a leader
    
    for (i in instrs.indices) {
        val line = instrs[i]
        val instr = line.instr
        
        if (instr.isJmp || instr.isCondJmp) {
            if (instr.ripRelTarget != 0L) {
                leaders.add(instr.ripRelTarget)
            }
            if (i + 1 < instrs.size) {
                leaders.add(instrs[i + 1].instr.addr)
            }
        } else if (instr.isRet) {
            if (i + 1 < instrs.size) {
                leaders.add(instrs[i + 1].instr.addr)
            }
        }
    }
    
    // 2. Build blocks (nodes)
    val nodes = mutableListOf<CfgNode>()
    var currentBlockInstrs = mutableListOf<DisasmLine>()
    var nodeId = 0
    
    for (i in instrs.indices) {
        val line = instrs[i]
        val addr = line.instr.addr
        
        if (i > 0 && leaders.contains(addr)) {
            if (currentBlockInstrs.isNotEmpty()) {
                nodes.add(CfgNode(
                    id = nodeId++,
                    instructions = currentBlockInstrs,
                    startAddr = currentBlockInstrs.first().instr.addr,
                    endAddr = currentBlockInstrs.last().instr.addr
                ))
                currentBlockInstrs = mutableListOf()
            }
        }
        currentBlockInstrs.add(line)
    }
    
    if (currentBlockInstrs.isNotEmpty()) {
        nodes.add(CfgNode(
            id = nodeId++,
            instructions = currentBlockInstrs,
            startAddr = currentBlockInstrs.first().instr.addr,
            endAddr = currentBlockInstrs.last().instr.addr
        ))
    }
    
    // 3. Build edges
    val edges = mutableListOf<CfgEdge>()
    val addrToNodeId = mutableMapOf<Long, Int>()
    for (node in nodes) {
        for (line in node.instructions) {
            addrToNodeId[line.instr.addr] = node.id
        }
    }
    
    for (node in nodes) {
        val lastLine = node.instructions.last()
        val lastInstr = lastLine.instr
        
        if (lastInstr.isJmp) {
            val targetId = addrToNodeId[lastInstr.ripRelTarget]
            if (targetId != null) {
                edges.add(CfgEdge(node.id, targetId, EdgeType.UNCONDITIONAL))
            }
        } else if (lastInstr.isCondJmp) {
            // True target
            val trueTargetId = addrToNodeId[lastInstr.ripRelTarget]
            if (trueTargetId != null) {
                edges.add(CfgEdge(node.id, trueTargetId, EdgeType.TRUE))
            }
            // False target (fall-through)
            val nextInstrIndex = instrs.indexOfFirst { it.instr.addr == lastInstr.addr } + 1
            if (nextInstrIndex < instrs.size) {
                val falseTargetId = addrToNodeId[instrs[nextInstrIndex].instr.addr]
                if (falseTargetId != null) {
                    edges.add(CfgEdge(node.id, falseTargetId, EdgeType.FALSE))
                }
            }
        } else if (!lastInstr.isRet) {
            val nextInstrIndex = instrs.indexOfFirst { it.instr.addr == lastInstr.addr } + 1
            if (nextInstrIndex < instrs.size) {
                val nextNodeId = addrToNodeId[instrs[nextInstrIndex].instr.addr]
                if (nextNodeId != null && nextNodeId != node.id) {
                    edges.add(CfgEdge(node.id, nextNodeId, EdgeType.UNCONDITIONAL))
                }
            }
        }
    }
    
    layoutHierarchical(nodes, edges)
    
    return Pair(nodes, edges)
}

fun layoutHierarchical(nodes: List<CfgNode>, edges: List<CfgEdge>) {
    if (nodes.isEmpty()) return
    
    val graph = nodes.associate { it.id to mutableListOf<Int>() }
    val inDegree = mutableMapOf<Int, Int>().withDefault { 0 }
    
    for (edge in edges) {
        graph[edge.from]?.add(edge.to)
        inDegree[edge.to] = inDegree.getValue(edge.to) + 1
    }
    
    val depths = mutableMapOf<Int, Int>()
    val visited = mutableSetOf<Int>()
    val queue = java.util.ArrayDeque<Pair<Int, Int>>()
    
    val startNodes = nodes.filter { inDegree.getValue(it.id) == 0 }
    if (startNodes.isNotEmpty()) {
        for (node in startNodes) {
            queue.add(Pair(node.id, 0))
            visited.add(node.id)
        }
    } else {
        val firstId = nodes.first().id
        queue.add(Pair(firstId, 0))
        visited.add(firstId)
    }
    
    while (queue.isNotEmpty()) {
        val (current, depth) = queue.poll()
        depths[current] = depth
        for (neighbor in graph[current] ?: emptyList()) {
            if (!visited.contains(neighbor)) {
                visited.add(neighbor)
                queue.add(Pair(neighbor, depth + 1))
            }
        }
    }
    
    val depthToNodes = mutableMapOf<Int, MutableList<CfgNode>>()
    for (node in nodes) {
        val depth = depths[node.id] ?: 0
        depthToNodes.getOrPut(depth) { mutableListOf() }.add(node)
    }
    
    val nodeWidth = 260f
    val horizSpacing = 60f
    val vertSpacing = 80f
    
    var maxDepthWidth = 0f
    for ((_, layerNodes) in depthToNodes) {
        val w = layerNodes.size * (nodeWidth + horizSpacing)
        if (w > maxDepthWidth) maxDepthWidth = w
    }

    val layerHeights = mutableMapOf<Int, Float>()
    for ((depth, layerNodes) in depthToNodes) {
        for (node in layerNodes) {
            node.width = nodeWidth
            node.height = node.instructions.size * 18f + 48f
        }
        val maxH = layerNodes.maxOfOrNull { it.height } ?: 120f
        layerHeights[depth] = maxH
    }

    val maxDepth = depthToNodes.keys.maxOrNull() ?: 0
    var currentY = 40f
    for (depth in 0..maxDepth) {
        val layerNodes = depthToNodes[depth] ?: continue
        val N = layerNodes.size
        
        for (i in layerNodes.indices) {
            val node = layerNodes[i]
            node.y = currentY
            node.x = (i - (N - 1) / 2.0f) * (nodeWidth + horizSpacing) + (maxDepthWidth / 2f) - (nodeWidth / 2f)
        }
        
        val layerHeight = layerHeights[depth] ?: 120f
        currentY += layerHeight + vertSpacing
    }
}
