package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.Ps5DebuggerTheme
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState

@Composable
fun MainView() {
    var activeMap by remember { mutableStateOf<MemoryRange?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var jumpToAddress by remember { mutableStateOf<Long?>(null) }
    
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    var isConsoleVisible by remember { mutableStateOf(true) }
    var isSidebarVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Memory Viewer", "Memory Search", "Watch List", "Memory Dumper")
    
    LaunchedEffect(isConnected) {
        if (isConnected) {
            try {
                AppContainer.debuggerUseCase.refreshProcesses()
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log("MAIN", "Failed to retrieve process list: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
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
                                text = "PS5 DEBUGGER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Disconnect button
                            Tooltip("Disconnect") {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            AppContainer.debuggerUseCase.disconnect()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.StatusRed),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Disconnect",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = PS5ThemeColors.BorderColor)

                        // Middle split area
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            AnimatedVisibility(
                                visible = isSidebarVisible,
                                enter = slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(durationMillis = 200)
                                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                                exit = slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(durationMillis = 200)
                                ) + fadeOut(animationSpec = tween(durationMillis = 200))
                            ) {
                                Row(modifier = Modifier.fillMaxHeight()) {
                                    // Left processes and discovery bar
                                    ProcessManager(
                                        onMapSelected = {
                                            activeMap = it
                                            selectedTab = 0 // Auto switch to hex viewer tab when map region is clicked
                                        },
                                        activeMap = activeMap,
                                        onCollapse = { isSidebarVisible = false }
                                    )

                                    VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                                }
                            }

                            if (!isSidebarVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(28.dp)
                                        .background(PS5ThemeColors.SecondaryBg)
                                        .clickable { isSidebarVisible = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val density = LocalDensity.current.density
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        val tintColor = PS5ThemeColors.AccentCyan
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(9f * density, 6f * density)
                                            lineTo(15f * density, 12f * density)
                                            lineTo(9f * density, 18f * density)
                                        }
                                        drawPath(
                                            path = path,
                                            color = tintColor,
                                            style = Stroke(width = 2f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                                        )
                                    }
                                }
                                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                            }

                            // Right main controls area
                            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                                // Tabs selector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(PS5ThemeColors.SecondaryBg)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        val icon = when (index) {
                                            0 -> Icons.AutoMirrored.Filled.List
                                            1 -> Icons.Default.Search
                                            2 -> Icons.Default.Star
                                            else -> Icons.Default.Build
                                        }
                                        TabItem(
                                            title = title,
                                            icon = icon,
                                            isSelected = selectedTab == index,
                                            onClick = { selectedTab = index }
                                        )
                                    }
                                }
                                HorizontalDivider(color = PS5ThemeColors.BorderColor)

                                // Tab content
                                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
                                    when (selectedTab) {
                                        0 -> HexViewer(activeMap = activeMap, jumpToAddress = jumpToAddress)
                                        1 -> MemoryScannerView(activeMap = activeMap)
                                        2 -> WatchList(onJumpToAddress = { addr ->
                                            val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
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

                        AnimatedVisibility(
                            visible = isConsoleVisible,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(durationMillis = 200)
                            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(durationMillis = 200)
                            ) + fadeOut(animationSpec = tween(durationMillis = 200))
                        ) {
                            Column {
                                HorizontalDivider(color = PS5ThemeColors.BorderColor)

                                // Bottom Console Log panel
                                LoggerConsole(
                                    modifier = Modifier.padding(top = 4.dp),
                                    actionButton = {
                                        ConsoleToggleButton(
                                            onClick = { isConsoleVisible = false },
                                            isLarge = false,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
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
    modifier: Modifier = Modifier,
    isLarge: Boolean = true
) {
    val size = if (isLarge) 64.dp else 32.dp
    Button(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = if (isLarge) 7.dp else 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconColor = PS5ThemeColors.AccentCyan
            val width = if (isLarge) 24.dp else 16.dp
            val height = if (isLarge) 18.dp else 12.dp
            Canvas(modifier = Modifier.size(width = width, height = height)) {
                drawRoundRect(
                    color = iconColor,
                    style = Stroke(width = 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(if (isLarge) 6f else 4f, if (isLarge) 7f else 4.5f),
                    end = androidx.compose.ui.geometry.Offset(if (isLarge) 10f else 7f, if (isLarge) 10f else 6.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(if (isLarge) 10f else 7f, if (isLarge) 10f else 6.5f),
                    end = androidx.compose.ui.geometry.Offset(if (isLarge) 6f else 4f, if (isLarge) 13f else 8.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(if (isLarge) 14f else 9.5f, if (isLarge) 13f else 8.5f),
                    end = androidx.compose.ui.geometry.Offset(if (isLarge) 19f else 13f, if (isLarge) 13f else 8.5f),
                    strokeWidth = 2f
                )
            }
            if (isLarge) {
                Spacer(Modifier.height(3.dp))
                Text("Console", color = PS5ThemeColors.TextMain, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) PS5ThemeColors.Surface else Color.Transparent
    val contentColor = if (isSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted
    val borderColor = if (isSelected) PS5ThemeColors.BorderColor else Color.Transparent
    
    Box(
        modifier = modifier
            .height(28.dp)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
