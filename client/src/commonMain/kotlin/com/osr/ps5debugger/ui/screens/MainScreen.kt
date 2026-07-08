package com.osr.ps5debugger.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.Ps5DebuggerTheme
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.*
import com.osr.ps5debugger.ui.components.Tooltip
import com.osr.ps5debugger.ui.components.ConsoleToggleButton
import com.osr.ps5debugger.ui.components.TabItem
import com.osr.ps5debugger.ui.components.TopMenuBar
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.ui.state.rememberMainState
import kotlinx.coroutines.launch

@Composable
fun MainScreen(onExit: () -> Unit = {}) {
    val state = rememberMainState(onExit = onExit)
    val isConnected by state.isConnected.collectAsState()
    
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    
    LaunchedEffect(activeProcess) {
        state.activeMap = null
        state.activeMaps.clear()
        state.selectionStart = null
        state.selectionEnd = null
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            try {
                AppContainer.debuggerUseCase.refreshProcesses()
            } catch (_: Exception) {}
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
                MainContent(state)
            }
        }
    }
}

@Composable
private fun MainContent(state: MainState) {
    val coroutineScope = rememberCoroutineScope()
    var isSettingsOpen by remember { mutableStateOf(false) }
    
    // Track active tool window ids. We use "connections" for left, "debugger" for right.
    var activeLeftTab by remember { mutableStateOf<String?>("connections") }
    var activeRightTab by remember { mutableStateOf<String?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isMobile = maxWidth < 800.dp
                TopBar(state, isMobile = isMobile, onSettingsClick = { isSettingsOpen = true })
            }
            HorizontalDivider(color = PS5ThemeColors.BorderColor)

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // LEFT STRIP BAR (50px)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(50.dp)
                        .background(PS5ThemeColors.SecondaryBg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    // Connections Icon Tab
                    Tooltip("Connection Manager") {
                        IconButton(
                            onClick = {
                                activeLeftTab = if (activeLeftTab == "connections") null else "connections"
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (activeLeftTab == "connections") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Connection Manager",
                                tint = if (activeLeftTab == "connections") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Memory Map Icon Tab
                    Tooltip("Memory Map") {
                        IconButton(
                            onClick = {
                                activeLeftTab = if (activeLeftTab == "map") null else "map"
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (activeLeftTab == "map") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Memory Map",
                                tint = if (activeLeftTab == "map") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Place future left sidebar tab icons here...
                }
                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)

                // LEFT PANEL (EXPANDED CONTAINER)
                AnimatedVisibility(
                    visible = activeLeftTab != null,
                    enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(200)) + fadeIn(tween(200)),
                    exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(200)) + fadeOut(tween(200))
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        when (activeLeftTab) {
                            "connections" -> ProcessManager(
                                onMapSelected = {
                                    state.activeMap = it
                                    state.selectedTab = 0
                                },
                                activeMap = state.activeMap,
                                activeMaps = state.activeMaps,
                                onMapsSelected = { maps ->
                                    state.activeMaps.clear()
                                    state.activeMaps.addAll(maps)
                                    if (maps.isEmpty()) {
                                        state.activeMap = null
                                    } else if (state.activeMap == null || !maps.any { it.start == state.activeMap?.start }) {
                                        state.activeMap = maps.first()
                                    }
                                },
                                onCollapse = { activeLeftTab = null }
                            )
                            "map" -> MemoryMapView(
                                onJumpToAddress = { addr ->
                                    val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
                                    if (map != null) {
                                        state.activeMap = map
                                        state.jumpToAddress = addr
                                        state.selectedTab = 0
                                    }
                                },
                                onCollapse = { activeLeftTab = null }
                            )
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                    }
                }

                // MAIN CONTENT AREA
                MainArea(state, modifier = Modifier.fillMaxHeight().weight(1f))

                // RIGHT PANEL (EXPANDED CONTAINER)
                AnimatedVisibility(
                    visible = activeRightTab != null,
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) + fadeIn(tween(200)),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                        when (activeRightTab) {
                            "debugger" -> DebugSidebar(
                                state = state,
                                onCollapse = { activeRightTab = null }
                            )
                        }
                    }
                }

                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                // RIGHT STRIP BAR (50px)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(50.dp)
                        .background(PS5ThemeColors.SecondaryBg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    // Debugger Icon Tab
                    Tooltip("Debugger") {
                        IconButton(
                            onClick = {
                                activeRightTab = if (activeRightTab == "debugger") null else "debugger"
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (activeRightTab == "debugger") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Debugger",
                                tint = if (activeRightTab == "debugger") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Place future right sidebar tab icons here...
                }
            }

            ConsolePanel(state)
        }

        if (!state.isConsoleVisible) {
            ConsoleToggleButton(
                onClick = { state.isConsoleVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
            )
        }

        if (isSettingsOpen) {
            SettingsMenuOverlay(onClose = { isSettingsOpen = false })
        }
    }
}

@Composable
private fun MainArea(state: MainState, modifier: Modifier = Modifier) {
    val tabs = listOf("Memory Viewer", "Memory Search", "Watch List", "Memory Dumper")
    
    Column(modifier = modifier) {
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
                    isSelected = state.selectedTab == index,
                    onClick = { state.selectedTab = index }
                )
            }
        }
        HorizontalDivider(color = PS5ThemeColors.BorderColor)

        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
            TabContent(state)
        }
    }
}

@Composable
private fun TabContent(state: MainState) {
    when (state.selectedTab) {
        0 -> MemoryViewerLayout(
            activeMap = state.activeMap,
            activeMaps = state.activeMaps,
            jumpToAddress = state.jumpToAddress,
            viewModeParam = state.viewMode,
            onViewModeChanged = { state.viewMode = it },
            selectionStartParam = state.selectionStart,
            selectionEndParam = state.selectionEnd,
            onSelectionChanged = { start, end ->
                state.selectionStart = start
                state.selectionEnd = end
            },
            activeBreakpoints = state.activeBreakpoints,
            activeWatchpoints = state.activeWatchpoints
        )
        1 -> MemoryScannerView(
            activeMap = state.activeMap,
            onJumpToAddress = { addr ->
                val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
                if (map != null) {
                    state.activeMap = map
                    state.jumpToAddress = addr
                    state.selectedTab = 0
                }
            }
        )
        2 -> WatchList(onJumpToAddress = { addr ->
            val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
            if (map != null) {
                state.activeMap = map
                state.jumpToAddress = addr
                state.selectedTab = 0
            }
        })
        3 -> MemoryDumperView()
    }
}

@Composable
private fun ConsolePanel(state: MainState) {
    AnimatedVisibility(
        visible = state.isConsoleVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        Column {
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            LoggerConsole(
                modifier = Modifier.padding(top = 4.dp),
                actionButton = {
                    ConsoleToggleButton(
                        onClick = { state.isConsoleVisible = false },
                        isLarge = false,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun TopBar(state: MainState, isMobile: Boolean, onSettingsClick: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isMobile) {
            TopMenuBar(
                onFileAction = state::handleFileAction,
                onEditAction = { action ->
                    if (action == "Preferences") {
                        onSettingsClick()
                    } else {
                        state.handleEditAction(action)
                    }
                },
                onViewAction = state::handleViewAction
            )
        } else {
            Text(
                text = "PS5 Debugger",
                color = PS5ThemeColors.AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isMobile) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
                    .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { state.isConsoleVisible = !state.isConsoleVisible },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
                    .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Console",
                    tint = if (state.isConsoleVisible) PS5ThemeColors.AccentCyan else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

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
}

@Composable
fun SettingsMenuOverlay(
    onClose: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("main") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Dark native background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentScreen == "main") {
                            onClose()
                        } else {
                            currentScreen = "main"
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = when (currentScreen) {
                        "appearance" -> "Appearance Settings"
                        "connection" -> "Connection Settings"
                        "accessibility" -> "Accessibility Settings"
                        "support" -> "Support"
                        else -> "Settings"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = Color(0xFF2C2C2E))

            // Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (currentScreen) {
                    "main" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsItem(
                                title = "Appearance",
                                icon = Icons.Default.Settings,
                                iconBg = Color(0xFF0A84FF),
                                onClick = { currentScreen = "appearance" }
                            )
                            SettingsItem(
                                title = "Connection",
                                icon = Icons.Default.Share,
                                iconBg = Color(0xFF30D158),
                                onClick = { currentScreen = "connection" }
                            )
                            SettingsItem(
                                title = "Accessibility",
                                icon = Icons.Default.Info,
                                iconBg = Color(0xFFBF5AF2),
                                onClick = { currentScreen = "accessibility" }
                            )
                            SettingsItem(
                                title = "Support",
                                icon = Icons.Default.Info,
                                iconBg = Color(0xFFFF9F0A),
                                onClick = { currentScreen = "support" }
                            )
                        }
                    }
                    "appearance" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Theme Preference", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)) {
                                    Text("Dark Mode (Active)")
                                }
                                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))) {
                                    Text("Ghidra Dark")
                                }
                            }
                        }
                    }
                    "connection" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Connection Preferences", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Auto-Reconnect: Enabled", color = Color.Gray, fontSize = 12.sp)
                            Text("Timeout: 5000ms", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    "accessibility" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Accessibility Options", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Text Size: Medium", color = Color.Gray, fontSize = 12.sp)
                            Text("High Contrast: Off", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    "support" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Support & Version Info", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("PS5 Debugger Client v2.0.1", color = Color.Gray, fontSize = 12.sp)
                            Text("Developed by Google DeepMind team.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}
