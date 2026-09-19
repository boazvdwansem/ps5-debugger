package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.osr.ps5debugger.ui.icons.PS5Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.util.copyToClipboard
import com.osr.ps5debugger.ui.state.MainState
import kotlinx.coroutines.launch

@Composable
fun ReferencesSidebar(
    state: MainState,
    targetAddress: Long? = null,
    onCollapse: () -> Unit
) {
    val isConnected by state.isConnected.collectAsState()
    
    val selection = state.selectionStart
    val activeMap = state.activeMap
    
    // Find the currently selected function
    val selectedFuncAddr = remember(targetAddress, selection, activeMap, state.activeMaps.toList(), AppContainer.discoveredFunctions.size) {
        if (targetAddress != null) {
            targetAddress
        } else if (selection != null && activeMap != null && selection >= activeMap.start && selection < activeMap.end) {
            AppContainer.discoveredFunctions.filter { it <= selection && it >= activeMap.start }.maxOrNull()
        } else {
            null
        }
    }

    var xrefsList by remember { mutableStateOf<List<Long>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFuncAddr, isConnected) {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val scanMap = activeMap ?: state.activeMaps.firstOrNull { selectedFuncAddr != null && selectedFuncAddr >= it.start && selectedFuncAddr < it.end }
        if (selectedFuncAddr != null && pid != null && scanMap != null && isConnected) {
            isLoading = true
            try {
                val scanStart = scanMap.start
                val scanLen = (scanMap.end - scanMap.start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                xrefsList = AppContainer.clientAdapter.client.findXrefs(pid, scanStart, scanLen, selectedFuncAddr)
            } catch (e: Exception) {
                xrefsList = emptyList()
            } finally {
                isLoading = false
            }
        } else {
            xrefsList = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(PS5ThemeColors.DarkBg)
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "References",
                style = MaterialTheme.typography.titleMedium,
                color = PS5ThemeColors.TextMain,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = PS5Icons.Close,
                    contentDescription = "Collapse",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))

        if (selectedFuncAddr == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing selected",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                text = "XRefs to ${AppContainer.getSymbolName(selectedFuncAddr, true)}",
                color = PS5ThemeColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
                    }
                } else if (xrefsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No references found",
                            color = PS5ThemeColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    var showContextMenu by remember { mutableStateOf(false) }
                    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
                    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(xrefsList) { addr ->
                            val funcName = AppContainer.getSymbolName(addr, false)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PS5ThemeColors.Surface, RoundedCornerShape(4.dp))
                                    .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                                    .clickable { }
                                    .pointerInput(addr) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.type == PointerEventType.Press) {
                                                    val change = event.changes.first()
                                                    if (event.buttons.isSecondaryPressed) {
                                                        contextMenuAddr = addr
                                                        contextMenuOffset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                                        showContextMenu = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = String.format("0x%012X", addr),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = PS5ThemeColors.AccentCyan
                                    )
                                    Text(
                                        text = funcName,
                                        fontSize = 11.sp,
                                        color = PS5ThemeColors.TextMuted,
                                        maxLines = 1
                                    )
                                }

                                if (showContextMenu && contextMenuAddr == addr) {
                                    Box(modifier = Modifier.offset(contextMenuOffset.x, contextMenuOffset.y).size(0.dp)) {
                                        DropdownMenu(
                                            expanded = true,
                                            onDismissRequest = { showContextMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Jump to in Hex", fontSize = 12.sp) },
                                                onClick = {
                                                    state.viewMode = 2
                                                    state.jumpToAddress = addr
                                                    state.selectionStart = addr
                                                    state.selectionEnd = addr
                                                    state.selectedTab = 0
                                                    showContextMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Jump to in Disassembly", fontSize = 12.sp) },
                                                onClick = {
                                                    state.viewMode = 0
                                                    state.jumpToAddress = addr
                                                    state.selectionStart = addr
                                                    state.selectionEnd = addr
                                                    state.selectedTab = 0
                                                    showContextMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Jump to in Graph", fontSize = 12.sp) },
                                                onClick = {
                                                    state.viewMode = 1
                                                    state.jumpToAddress = addr
                                                    state.selectedTab = 0
                                                    showContextMenu = false
                                                }
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Copy Address", fontSize = 12.sp) },
                                                onClick = {
                                                    copyToClipboard(String.format("0x%012X", addr))
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
    }
}
