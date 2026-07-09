@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.state.MainState
import kotlinx.coroutines.launch

@Composable
fun BreakpointsSidebar(
    state: MainState,
    onCollapse: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val client = AppContainer.clientAdapter.client
    val isConnected by state.isConnected.collectAsState()
    val density = LocalDensity.current.density
    
    val softBps = state.activeBreakpoints.toList().sortedBy { it.first }
    val hardBps = state.activeWatchpoints.toList().sortedBy { it.first }
    val totalCount = softBps.size + hardBps.size
    
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuIndex by remember { mutableStateOf<Int?>(null) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuIsHardware by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(PS5ThemeColors.DarkBg)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Breakpoints ($totalCount)",
                color = PS5ThemeColors.TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Collapse",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        if (totalCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No breakpoints set",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (softBps.isNotEmpty()) {
                        item {
                            Text(
                                text = "Software Breakpoints",
                                    color = PS5ThemeColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(softBps) { (slot, address) ->
                            BreakpointRow(
                                label = "Slot $slot",
                                address = address,
                                density = density,
                                onClick = {
                                    state.selectionStart = address
                                    state.selectionEnd = address
                                },
                                onDoubleClick = {
                                    state.jumpToAddress = address
                                    state.selectionStart = address
                                    state.selectionEnd = address
                                },
                                onRightClick = { offset ->
                                    contextMenuIndex = slot
                                    contextMenuAddr = address
                                    contextMenuIsHardware = false
                                    contextMenuOffset = offset
                                    showContextMenu = true
                                }
                            )
                        }
                    }

                    if (hardBps.isNotEmpty()) {
                        item {
                            Text(
                                text = "Hardware Breakpoints",
                                color = PS5ThemeColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(hardBps) { (slot, address) ->
                            BreakpointRow(
                                label = "DR$slot",
                                address = address,
                                color = PS5ThemeColors.AccentAmber,
                                density = density,
                                onClick = {
                                    state.selectionStart = address
                                    state.selectionEnd = address
                                },
                                onDoubleClick = {
                                    state.jumpToAddress = address
                                    state.selectionStart = address
                                    state.selectionEnd = address
                                },
                                onRightClick = { offset ->
                                    contextMenuIndex = slot
                                    contextMenuAddr = address
                                    contextMenuIsHardware = true
                                    contextMenuOffset = offset
                                    showContextMenu = true
                                }
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = contextMenuOffset,
                    modifier = Modifier.background(PS5ThemeColors.Surface)
                ) {
                    val slot = contextMenuIndex
                    val addr = contextMenuAddr
                    val isHardware = contextMenuIsHardware
                    if (slot != null && addr != null) {
                        DropdownMenuItem(
                            text = { Text(if (isHardware) "Unset Hardware Breakpoint" else "Unset Breakpoint", color = Color.Red, fontSize = 12.sp) },
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        if (isHardware) {
                                            state.activeWatchpoints.remove(slot)
                                            if (isConnected) {
                                                val ok = client.setWatchpoint(slot, false, 1, 1, addr)
                                                if (!ok) {
                                                    state.activeWatchpoints[slot] = addr
                                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove hardware breakpoint on PS5", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                } else {
                                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Removed hardware breakpoint at 0x${addr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                }
                                            }
                                        } else {
                                            state.activeBreakpoints.remove(slot)
                                            if (isConnected) {
                                                val ok = client.setBreakpoint(slot, false, addr)
                                                if (!ok) {
                                                    state.activeBreakpoints[slot] = addr
                                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove breakpoint on PS5", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                } else {
                                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Removed breakpoint at 0x${addr.toString(16)}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (isHardware) {
                                            state.activeWatchpoints[slot] = addr
                                        } else {
                                            state.activeBreakpoints[slot] = addr
                                        }
                                        AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to remove breakpoint: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                    }
                                }
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Jump to Breakpoint in Hex", fontSize = 12.sp, color = PS5ThemeColors.TextMain) },
                            onClick = {
                                state.jumpToAddress = addr
                                state.selectionStart = addr
                                state.selectionEnd = addr
                                state.viewMode = 2
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Jump to Breakpoint in Disassembly", fontSize = 12.sp, color = PS5ThemeColors.TextMain) },
                            onClick = {
                                state.jumpToAddress = addr
                                state.selectionStart = addr
                                state.selectionEnd = addr
                                state.viewMode = 0
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Jump to Breakpoint in Graph", fontSize = 12.sp, color = PS5ThemeColors.TextMain) },
                            onClick = {
                                state.jumpToAddress = addr
                                state.selectionStart = addr
                                state.selectionEnd = addr
                                state.viewMode = 1
                                showContextMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakpointRow(
    label: String,
    address: Long,
    color: Color = Color.Red,
    density: Float,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onRightClick: (DpOffset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(6.dp))
            .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick
            )
            .pointerInput(label, address) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            val isSecondary = event.buttons.isSecondaryPressed
                            if (isSecondary) {
                                val change = event.changes.first()
                                val xDp = (change.position.x / density).dp
                                val yDp = (change.position.y / density).dp
                                onRightClick(DpOffset(xDp, yDp))
                            }
                        }
                    }
                }
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = PS5ThemeColors.TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "0x${address.toString(16).uppercase()}",
                color = PS5ThemeColors.AccentCyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
