package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.protocol.Ps5VmMapEntry
import com.osr.ps5debugger.service.DebuggerService
import kotlinx.coroutines.launch

@Composable
fun MainView() {
    var activeMap by remember { mutableStateOf<Ps5VmMapEntry?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var jumpToAddress by remember { mutableStateOf<Long?>(null) }
    
    val isConnected by DebuggerService.isConnected.collectAsState()
    var isConsoleVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Memory Hex Viewer", "Memory Search", "Watch List", "Memory Dumper")
    
    LaunchedEffect(isConnected) {
        if (isConnected) {
            try {
                DebuggerService.refreshProcesses()
            } catch (e: Exception) {
                DebuggerService.log("MAIN", "Failed to retrieve process list: ${e.message}", DebuggerService.LogEntry.Level.ERROR)
            }
        }
    }

    Ps5DebuggerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!isConnected) {
                ConnectionScreen()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top header / title bar area
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PS5 DEBUGGER NG",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Disconnect button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        DebuggerService.disconnect()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.StatusRed)
                            ) {
                                Text("Disconnect", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = PS5ThemeColors.BorderColor)

                        // Middle split area
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // Left processes and discovery bar
                            ProcessManager(
                                onMapSelected = {
                                    activeMap = it
                                    selectedTab = 0 // Auto switch to hex viewer tab when map region is clicked
                                },
                                activeMap = activeMap
                            )

                            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)

                            // Right main controls area
                            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                                // Tabs selector
                                TabRow(
                                    selectedTabIndex = selectedTab,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            text = { Text(title) }
                                        )
                                    }
                                }

                                // Tab content
                                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
                                    when (selectedTab) {
                                        0 -> HexViewer(activeMap = activeMap, jumpToAddress = jumpToAddress)
                                        1 -> MemoryScannerView(activeMap = activeMap)
                                        2 -> WatchList(onJumpToAddress = { addr ->
                                            val map = DebuggerService.vmMaps.firstOrNull { addr >= it.start && addr < it.end }
                                            if (map != null) {
                                                activeMap = map
                                                jumpToAddress = addr
                                                selectedTab = 0
                                            }
                                        })
                                        3 -> MemoryDumperView()
                                    }
                                }
                            }
                        }

                        if (isConsoleVisible) {
                            HorizontalDivider(color = PS5ThemeColors.BorderColor)

                            // Bottom Console Log panel
                            Box {
                                LoggerConsole(modifier = Modifier.padding(top = 4.dp))
                                ConsoleToggleButton(
                                    onClick = { isConsoleVisible = false },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp)
                                )
                            }
                        }
                    }

                    if (!isConsoleVisible) {
                        ConsoleToggleButton(
                            onClick = { isConsoleVisible = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleToggleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(64.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconColor = PS5ThemeColors.AccentCyan
            Canvas(modifier = Modifier.size(width = 24.dp, height = 18.dp)) {
                drawRoundRect(
                    color = iconColor,
                    style = Stroke(width = 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(6f, 7f),
                    end = androidx.compose.ui.geometry.Offset(10f, 10f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(10f, 10f),
                    end = androidx.compose.ui.geometry.Offset(6f, 13f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(14f, 13f),
                    end = androidx.compose.ui.geometry.Offset(19f, 13f),
                    strokeWidth = 2f
                )
            }
            Spacer(Modifier.height(3.dp))
            Text("Console", color = PS5ThemeColors.TextMain, fontSize = 10.sp, maxLines = 1)
        }
    }
}
