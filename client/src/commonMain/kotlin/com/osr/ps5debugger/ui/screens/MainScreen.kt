package com.osr.ps5debugger.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.key.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.Ps5DebuggerTheme
import com.osr.ps5debugger.ui.icons.PS5Icons
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.*
import com.osr.ps5debugger.ui.components.Tooltip
import com.osr.ps5debugger.ui.components.ConsoleToggleButton
import com.osr.ps5debugger.ui.components.TabItem
import com.osr.ps5debugger.ui.components.TopMenuBar
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.ui.state.rememberMainState
import kotlinx.coroutines.launch
import com.osr.ps5debugger.util.DefaultIpHelper
import com.osr.ps5debugger.util.ShortcutManager

@Composable
fun MainScreen(state: MainState) {
    val isConnected by state.isConnected.collectAsState()

    // Global Hooks
    LaunchedEffect(Unit) {
        AppContainer.onNavigateToMemory = { addr ->
            val vmMaps = AppContainer.debuggerUseCase.vmMaps.value
            val map = vmMaps.firstOrNull { addr >= it.start && addr < it.end }
            if (map != null) {
                state.activeMap = map
                state.jumpToAddress = addr
                state.selectedTab = 0
            } else {
                state.jumpToAddress = addr
                state.selectedTab = 0
            }
        }
        
        AppContainer.onCreateCheatRequested = { cheat ->
            state.pendingCheatToCreate = cheat
            state.showAddCheatDialog = true
        }
    }

    Ps5DebuggerTheme {
        if (!isConnected && !AppContainer.debugMockEnabled) {
            ConnectionScreen(
                onSettingsClick = { state.isSettingsOpen = true },
                onLoadEboot = { state.handleFileAction("Load eboot") }
            )
        } else {
            MainLayout(state)
        }

        if (state.isSettingsOpen) {
            SettingsDialog(onClose = { state.isSettingsOpen = false })
        }

        if (state.showGotoDialog) {
            GotoDialog(
                onDismiss = { state.showGotoDialog = false },
                onGoto = { addr: Long ->
                    val vmMaps = AppContainer.debuggerUseCase.vmMaps.value
                    val map = vmMaps.firstOrNull { m -> addr >= m.start && addr < m.end }
                    if (map != null) {
                        state.activeMap = map
                    }
                    state.jumpToAddress = addr
                    state.selectedTab = 0
                }
            )
        }
    }
}

@Composable
private fun MainLayout(state: MainState) {
    var activeLeftTab by remember { mutableStateOf<String?>(null) }
    val shortcuts = remember { DefaultIpHelper.getShortcuts() }
    val focusRequester = remember { FocusRequester() }
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val activeProcessInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
    val cheatProfiles by AppContainer.debuggerUseCase.gameCheatProfiles.collectAsState()
    val isProcessSelected = activeProcess != null

    val activeGameProfiles = remember(cheatProfiles, activeProcess, activeProcessInfo) {
        if (activeProcess == null) emptyList()
        else {
            val titleId = activeProcessInfo?.titleId?.takeIf { it.isNotBlank() && it != "0" }
            val name = activeProcessInfo?.name?.takeIf { it.isNotBlank() && it != "Unknown" && !it.contains("eboot.bin") }
                ?: activeProcess?.name?.takeIf { it.isNotBlank() && !it.contains("eboot.bin") }

            cheatProfiles.filter { profile ->
                profile.cheats.isNotEmpty() && (
                    (titleId != null && profile.titleId.equals(titleId, ignoreCase = true)) ||
                    (name != null && profile.name.equals(name, ignoreCase = true))
                )
            }
        }
    }
    val hasActiveCheats = activeGameProfiles.isNotEmpty()

    LaunchedEffect(hasActiveCheats) {
        if (!hasActiveCheats && state.activeRightTab == "cheats") {
            state.activeRightTab = null
        }
    }

    LaunchedEffect(activeProcess) {
        if (activeProcess == null) {
            state.activeMap = null
            state.activeMaps.clear()
            if (activeLeftTab == "map" || activeLeftTab == "symbols") {
                activeLeftTab = "connections"
            }
        }
    }

    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val activeMapsSnapshot = state.activeMaps.toList()
    val allOpenMaps = remember(state.activeMap, activeMapsSnapshot) {
        (listOfNotNull(state.activeMap) + activeMapsSnapshot).distinctBy { it.start }
    }

    // Continuous background processing for ALL open tabs/regions regardless of view or tab
    LaunchedEffect(allOpenMaps, isConnected, activeProcess, state.jumpToAddress) {
        if (allOpenMaps.isEmpty()) return@LaunchedEffect
        val hasLocal = allOpenMaps.any { it.localData != null }
        val hasRemote = allOpenMaps.any { it.localData == null }
        if (!hasLocal && (!hasRemote || !isConnected || activeProcess == null)) return@LaunchedEffect

        val pid = activeProcess?.pid
        val jumpAddr = state.jumpToAddress
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.coroutineScope {
                allOpenMaps.forEach { map ->
                    launch {
                        AppContainer.preloadRegionInBackground(map, pid, jumpAddr)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                
                try {
                    when {
                        ShortcutManager.isMatch(event, shortcuts["goto"]) -> {
                            state.showGotoDialog = true
                            true
                        }
                        ShortcutManager.isMatch(event, shortcuts["search"]) -> {
                            state.selectedTab = 1
                            true
                        }
                        ShortcutManager.isMatch(event, shortcuts["watchlist"]) -> {
                            state.selectedTab = 2
                            true
                        }
                        ShortcutManager.isMatch(event, shortcuts["memory"]) -> {
                            state.selectedTab = 0
                            true
                        }
                        else -> false
                    }
                } catch (_: Throwable) {
                    false
                }
            }
            .focusable()
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT SIDEBAR (ICONS)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .background(PS5ThemeColors.SecondaryBg)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Tooltip("Connections / Processes") {
                        IconButton(
                            onClick = { activeLeftTab = if (activeLeftTab == "connections") null else "connections" },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (activeLeftTab == "connections") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = PS5Icons.Connections,
                                contentDescription = "Connections",
                                tint = if (activeLeftTab == "connections") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Tooltip(if (isProcessSelected) "Memory Map" else "Memory Map (Select a process first)") {
                        IconButton(
                            onClick = { if (isProcessSelected) activeLeftTab = if (activeLeftTab == "map") null else "map" },
                            enabled = isProcessSelected,
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (activeLeftTab == "map") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = PS5Icons.MemoryMap,
                                contentDescription = "Memory Map",
                                tint = when {
                                    !isProcessSelected -> PS5ThemeColors.TextMuted.copy(alpha = 0.3f)
                                    activeLeftTab == "map" -> PS5ThemeColors.AccentCyan
                                    else -> PS5ThemeColors.TextMuted
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Tooltip(if (isProcessSelected) "Symbols" else "Symbols (Select a process first)") {
                        IconButton(
                            onClick = { if (isProcessSelected) activeLeftTab = if (activeLeftTab == "symbols") null else "symbols" },
                            enabled = isProcessSelected,
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (activeLeftTab == "symbols") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = PS5Icons.Symbols,
                                contentDescription = "Symbols",
                                tint = when {
                                    !isProcessSelected -> PS5ThemeColors.TextMuted.copy(alpha = 0.3f)
                                    activeLeftTab == "symbols" -> PS5ThemeColors.AccentCyan
                                    else -> PS5ThemeColors.TextMuted
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Console Toggle
                    Tooltip("Console Logs") {
                        IconButton(
                            onClick = { state.isConsoleVisible = !state.isConsoleVisible },
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .size(38.dp)
                                .background(
                                    if (state.isConsoleVisible) PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            val tint = if (state.isConsoleVisible) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted
                            Canvas(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
                                drawRoundRect(
                                    color = tint,
                                    style = Stroke(width = 2f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                                )
                                drawLine(
                                    color = tint,
                                    start = androidx.compose.ui.geometry.Offset(4f, 4.5f),
                                    end = androidx.compose.ui.geometry.Offset(7f, 6.5f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = tint,
                                    start = androidx.compose.ui.geometry.Offset(7f, 6.5f),
                                    end = androidx.compose.ui.geometry.Offset(4f, 8.5f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = tint,
                                    start = androidx.compose.ui.geometry.Offset(9.5f, 8.5f),
                                    end = androidx.compose.ui.geometry.Offset(13f, 8.5f),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }
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
                                onMapsSelected = { maps ->
                                    state.activeMaps.clear()
                                    state.activeMaps.addAll(maps)
                                    if (state.activeMap == null || !maps.any { it.start == state.activeMap?.start }) {
                                        state.activeMap = maps.firstOrNull()
                                    }
                                    state.selectedTab = 0
                                },
                                activeMap = state.activeMap,
                                activeMaps = state.activeMaps
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
                            "symbols" -> SymbolsView(
                                onJumpToAddress = { addr ->
                                    state.jumpToAddress = addr
                                    state.selectedTab = 0
                                },
                                onCollapse = { activeLeftTab = null }
                            )
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                    }
                }

                // MAIN CONTENT
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isMobileToolbar = maxWidth < 800.dp
                        TopBar(state, isMobile = isMobileToolbar, onSettingsClick = { state.isSettingsOpen = true })
                    }
                    HorizontalDivider(color = PS5ThemeColors.BorderColor)
                    MainArea(state, modifier = Modifier.weight(1f).fillMaxWidth())
                }

                // RIGHT PANEL (EXPANDED CONTAINER)
                AnimatedVisibility(
                    visible = state.activeRightTab != null,
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) + fadeIn(tween(200)),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
                        when (state.activeRightTab) {
                            "debugger" -> DebugSidebar(state = state, onCollapse = { state.activeRightTab = null })
                            "breakpoints" -> BreakpointsSidebar(state = state, onCollapse = { state.activeRightTab = null })
                            "references" -> ReferencesSidebar(state = state, targetAddress = state.xrefTargetAddress, onCollapse = { state.activeRightTab = null })
                            "cheats" -> CheatsSidebar(state = state, profiles = activeGameProfiles, onCollapse = { state.activeRightTab = null })
                        }
                    }
                }

                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)

                // RIGHT SIDEBAR (ICONS)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .background(PS5ThemeColors.SecondaryBg)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Tooltip("Debugger Control") {
                        IconButton(
                            onClick = {
                                state.activeRightTab = if (state.activeRightTab == "debugger") null else "debugger"
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (state.activeRightTab == "debugger") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = PS5Icons.DebuggerControl,
                                contentDescription = "Debugger",
                                tint = if (state.activeRightTab == "debugger") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Breakpoints Icon Tab
                    Tooltip("Breakpoints") {
                        IconButton(
                            onClick = {
                                state.activeRightTab = if (state.activeRightTab == "breakpoints") null else "breakpoints"
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (state.activeRightTab == "breakpoints") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = PS5Icons.Breakpoints,
                                contentDescription = "Breakpoints",
                                tint = if (state.activeRightTab == "breakpoints") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // References Icon Tab
                    Tooltip("References") {
                        IconButton(
                            onClick = {
                                state.activeRightTab = if (state.activeRightTab == "references") null else "references"
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (state.activeRightTab == "references") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Icon(
                                imageVector = PS5Icons.References,
                                contentDescription = "References",
                                tint = if (state.activeRightTab == "references") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Cheats Icon Tab (only shown when a process has been selected for which cheats exist)
                    if (hasActiveCheats) {
                        Tooltip("Active Process Cheats") {
                            IconButton(
                                onClick = {
                                    state.activeRightTab = if (state.activeRightTab == "cheats") null else "cheats"
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (state.activeRightTab == "cheats") PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = PS5Icons.Cheats,
                                    contentDescription = "Active Cheats",
                                    tint = if (state.activeRightTab == "cheats") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        ConsolePanel(state)

        if (state.showAddCheatDialog) {
            val procInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
            AddCheatDialog(
                titleId = procInfo?.titleId ?: "Unknown",
                version = "1.00",
                gameName = procInfo?.name ?: "Unknown",
                existingCheat = state.pendingCheatToCreate,
                onDismiss = { 
                    state.showAddCheatDialog = false
                    state.pendingCheatToCreate = null
                }
            )
        }
    }
}

@Composable
private fun MainArea(state: MainState, modifier: Modifier = Modifier) {
    val tabs = listOf("Memory Viewer", "Memory Search", "Watch List", "Memory Dumper", "Cheats", "File Browser")
    
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
                    0 -> PS5Icons.MemoryViewer
                    1 -> PS5Icons.MemoryScan
                    2 -> PS5Icons.WatchList
                    3 -> PS5Icons.MemoryDumper
                    4 -> PS5Icons.Cheats
                    else -> PS5Icons.FileBrowser
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

        if (state.selectedTab == 0 && state.activeMaps.isNotEmpty()) {
            OpenedRegionsBar(state)
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
        }

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
            activeMaps = emptyList(), // Only show activeMap, don't stack them
            jumpToAddress = state.jumpToAddress,
            viewModeParam = state.viewMode,
            onViewModeChanged = { state.viewMode = it },
            selectionStartParam = state.selectionStart,
            selectionEndParam = state.selectionEnd,
            onSelectionChanged = { start, end ->
                state.selectionStart = start
                state.selectionEnd = end
                state.xrefTargetAddress = null
            },
            activeBreakpoints = state.activeBreakpoints,
            activeWatchpoints = state.activeWatchpoints,
            onCopySelection = { state.handleEditAction("Copy") },
            onShowXrefs = { address ->
                state.xrefTargetAddress = address
                state.activeRightTab = "references"
            }
        )
        1 -> MemoryScannerView(
            activeMap = state.activeMap,
            activeMaps = state.activeMaps,
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
        4 -> CheatsView()
        5 -> FileBrowserView()
    }
}

@Composable
private fun ConsolePanel(state: MainState) {
    val coroutineScope = rememberCoroutineScope()
    var isDraggingSplitter by remember { mutableStateOf(false) }

    // Docked Panel layout at the bottom with resizable height splitter drag bar
    val height = if (state.isConsoleMaximized) 800.dp else state.consoleDockHeight.dp
    
    AnimatedVisibility(
        visible = state.isConsoleVisible && !state.isConsoleFloating,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            // Splitter Resizer Handle (Only if not maximized)
            if (!state.isConsoleMaximized) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(if (isDraggingSplitter) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    if (changes.isNotEmpty()) {
                                        val change = changes.first()
                                        if (event.type == PointerEventType.Press) {
                                            isDraggingSplitter = true
                                        }
                                        if (event.type == PointerEventType.Move && isDraggingSplitter) {
                                            val deltaY = change.previousPosition.y - change.position.y
                                            state.consoleDockHeight = (state.consoleDockHeight + deltaY).coerceIn(100f, 800f)
                                            change.consume()
                                        }
                                        if (event.type == PointerEventType.Release) {
                                            isDraggingSplitter = false
                                        }
                                    }
                                }
                            }
                        }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(PS5ThemeColors.SecondaryBg)
                    .padding(horizontal = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            if (dragAmount.y < -15) { 
                                state.isConsoleFloating = true
                                change.consume()
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isConsoleMaximized) "Maximized Console Logs" else "Docked Console Logs",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { state.isConsoleMaximized = !state.isConsoleMaximized }, modifier = Modifier.size(22.dp)) {
                    Icon(
                        imageVector = if (state.isConsoleMaximized) PS5Icons.Dock else PS5Icons.ResetView,
                        contentDescription = "Toggle Maximize",
                        tint = PS5ThemeColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(onClick = { state.isConsoleFloating = true }, modifier = Modifier.size(22.dp)) {
                    Icon(PS5Icons.Undock, contentDescription = "Undock", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(14.dp))
                }

                IconButton(onClick = { state.isConsoleVisible = false }, modifier = Modifier.size(22.dp)) {
                    Icon(PS5Icons.Close, contentDescription = "Close", tint = PS5ThemeColors.StatusRed, modifier = Modifier.size(14.dp))
                }
            }

            LoggerConsole(
                modifier = Modifier.fillMaxWidth().weight(1f),
                onAddressClick = { addr ->
                    AppContainer.onNavigateToMemory?.invoke(addr)
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
                onFileAction = { action ->
                    if (action == "Preferences") {
                        onSettingsClick()
                    } else {
                        state.handleFileAction(action)
                    }
                },
                onEditAction = { state.handleEditAction(it) },
                onViewAction = { state.handleViewAction(it) }
            )
        } else {
            IconButton(onClick = { /* Open mobile menu */ }) {
                Icon(PS5Icons.FileBrowser, null)
            }
        }

        Spacer(Modifier.weight(1f))

        // Connection Status Indicator
        Surface(
            color = if (state.isConnected.collectAsState().value) Color(0xFF43A047).copy(alpha = 0.1f) else Color(0xFFE53935).copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (state.isConnected.collectAsState().value) Color(0xFF43A047) else Color(0xFFE53935),
                            RoundedCornerShape(4.dp)
                        )
                )
                Text(
                    text = if (state.isConnected.collectAsState().value) "CONNECTED" else "DISCONNECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isConnected.collectAsState().value) Color(0xFF43A047) else Color(0xFFE53935)
                )
            }
        }

        IconButton(onClick = onSettingsClick) {
            Icon(PS5Icons.Settings, null, tint = PS5ThemeColors.TextMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDialog(onClose: () -> Unit) {
    var activeCategory by remember { mutableStateOf("general") }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = PS5ThemeColors.DarkBg,
                border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preferences", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onClose) {
                            Icon(PS5Icons.Close, null, tint = Color.Gray)
                        }
                    }

                    HorizontalDivider(color = PS5ThemeColors.BorderColor)

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Settings Sidebar
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .background(PS5ThemeColors.SecondaryBg)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SettingsTabItem("General", PS5Icons.SettingsGeneral, activeCategory == "general") { activeCategory = "general" }
                            SettingsTabItem("Shortcuts", PS5Icons.Keyboard, activeCategory == "shortcuts") { activeCategory = "shortcuts" }
                            SettingsTabItem("Connection", PS5Icons.SettingsNetwork, activeCategory == "network") { activeCategory = "network" }
                            SettingsTabItem("Simulation", PS5Icons.SettingsSimulation, activeCategory == "mock") { activeCategory = "mock" }
                            SettingsTabItem("About", PS5Icons.Info, activeCategory == "support") { activeCategory = "support" }
                        }

                        VerticalDivider(color = PS5ThemeColors.BorderColor)

                        // Settings Content
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                            when (activeCategory) {
                                "general" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("General Settings", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        
                                        var autoReconnect by remember { mutableStateOf(DefaultIpHelper.isAutoReconnectEnabled()) }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = autoReconnect,
                                                onCheckedChange = { 
                                                    autoReconnect = it
                                                    DefaultIpHelper.setAutoReconnectEnabled(it)
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Auto-reconnect on socket loss", color = Color.LightGray, fontSize = 13.sp)
                                        }

                                        var showTooltips by remember { mutableStateOf(true) }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = showTooltips,
                                                onCheckedChange = { showTooltips = it },
                                                colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Show tooltips", color = Color.LightGray, fontSize = 13.sp)
                                        }

                                        var mcpEnabled by remember { mutableStateOf(DefaultIpHelper.isMcpEnabled()) }
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = mcpEnabled,
                                                    onCheckedChange = {
                                                        mcpEnabled = it
                                                        DefaultIpHelper.setMcpEnabled(it)
                                                        AppContainer.onMcpServerToggled?.invoke(it)
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Enable Model Context Protocol (MCP) Server", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text(
                                                text = "Exposes debugger REST bridge on 127.0.0.1:8585 for AI assistant interaction (Antigravity, Claude Desktop, Cursor).",
                                                color = PS5ThemeColors.TextMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }
                                "shortcuts" -> {
                                    ShortcutsPage()
                                }
                                "network" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("Network Configuration", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        
                                        var timeout by remember { mutableStateOf(DefaultIpHelper.getConnectionTimeoutMs().toString()) }
                                        OutlinedTextField(
                                            value = timeout,
                                            onValueChange = { 
                                                if (it.all { c -> c.isDigit() }) {
                                                    timeout = it
                                                    it.toIntOrNull()?.let { ms -> DefaultIpHelper.setConnectionTimeoutMs(ms) }
                                                }
                                            },
                                            label = { Text("Connection Timeout (ms)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = TextStyle(color = Color.White)
                                        )
                                    }
                                }
                                "mock" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("Simulation / Mock Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = AppContainer.debugMockEnabled,
                                                onCheckedChange = { 
                                                    AppContainer.debugMockEnabled = it
                                                    DefaultIpHelper.setMockEnabled(it)
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = PS5ThemeColors.AccentCyan)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text("Enable Connection Simulation", color = Color.LightGray, fontSize = 13.sp)
                                        }
                                        Text(
                                            text = "When enabled, the client will simulate a console connection, bypassing network sockets and generating mock disassembly/subroutine CFG graphs.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                "support" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("Support & Version Info", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("PS5 Debugger Client v1.0.3", color = Color.Gray, fontSize = 12.sp)
                                        Text("Developed by Boaz.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingsTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() },
        color = if (selected) PS5ThemeColors.Surface else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = if (selected) PS5ThemeColors.TextMain else PS5ThemeColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun OpenedRegionsBar(state: MainState) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.activeMaps.forEach { map ->
            MemoryRegionTab(
                map = map,
                isSelected = state.activeMap?.start == map.start,
                onClick = { state.activeMap = map },
                onClose = {
                    val index = state.activeMaps.indexOf(map)
                    state.activeMaps.remove(map)
                    if (state.activeMap?.start == map.start) {
                        state.activeMap = if (state.activeMaps.isNotEmpty()) {
                            if (index < state.activeMaps.size) state.activeMaps[index] else state.activeMaps.last()
                        } else {
                            null
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MemoryRegionTab(
    map: com.osr.ps5debugger.domain.model.MemoryRange,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = if (isSelected) PS5ThemeColors.Surface else Color.Transparent
    val contentColor = if (isSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted
    val borderColor = if (isSelected) PS5ThemeColors.BorderColor else Color.Transparent

    Box(
        modifier = Modifier
            .height(26.dp)
            .background(backgroundColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val titleIdSuffix = if (!map.titleId.isNullOrEmpty()) " [${map.titleId}]" else ""
            Text(
                text = (if (map.name.isEmpty()) "0x${map.start.toString(16).uppercase()}" else map.name) + titleIdSuffix,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Icon(
                imageVector = PS5Icons.Close,
                contentDescription = "Close",
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(12.dp)
                    .clickable { onClose() }
            )
        }
    }
}

@Composable
private fun ShortcutsPage() {
    val shortcutsMap = remember { mutableStateMapOf<String, String>().apply { putAll(DefaultIpHelper.getShortcuts()) } }
    var recordingKey by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
        Text("Keyboard Shortcuts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            val items = listOf(
                "lock_edit" to "Toggle Editing Lock",
                "copy" to "Copy Selection",
                "paste" to "Paste Hex",
                "inject" to "Inject Changes",
                "goto" to "Go to Address",
                "undo" to "Undo Changes",
                "search" to "Memory Search",
                "watchlist" to "Watch List",
                "memory" to "Memory View"
            )
            
            items(items) { (key, label) ->
                ShortcutItem(
                    label = label,
                    shortcut = shortcutsMap[key] ?: "",
                    isRecording = recordingKey == key,
                    onStartRecording = { recordingKey = key },
                    onStopRecording = { recordingKey = null },
                    onShortcutChanged = { newShortcut ->
                        shortcutsMap[key] = newShortcut
                        DefaultIpHelper.setShortcuts(shortcutsMap)
                    }
                )
            }
        }
        
        Text(
            "Click a shortcut box and press your desired key combination. Global shortcuts (Search, Watchlist, Memory) work from anywhere. Contextual shortcuts work in their respective views.",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ShortcutItem(
    label: String,
    shortcut: String,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onShortcutChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        
        Surface(
            color = if (isRecording) PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else PS5ThemeColors.Surface,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, if (isRecording) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor),
            modifier = Modifier
                .width(150.dp)
                .height(32.dp)
                .clickable { onStartRecording() }
                .onKeyEvent { event ->
                    if (isRecording && event.type == KeyEventType.KeyDown) {
                        val key = event.key
                        if (key != Key.CtrlLeft && key != Key.CtrlRight && 
                            key != Key.ShiftLeft && key != Key.ShiftRight && 
                            key != Key.AltLeft && key != Key.AltRight && 
                            key != Key.MetaLeft && key != Key.MetaRight) {
                            
                            val sb = StringBuilder()
                            if (event.isCtrlPressed) sb.append("Ctrl+")
                            if (event.isShiftPressed) sb.append("Shift+")
                            if (event.isAltPressed) sb.append("Alt+")
                            
                            val keyStr = when(key) {
                                Key.A -> "A"; Key.B -> "B"; Key.C -> "C"; Key.D -> "D"; Key.E -> "E"
                                Key.F -> "F"; Key.G -> "G"; Key.H -> "H"; Key.I -> "I"; Key.J -> "J"
                                Key.K -> "K"; Key.L -> "L"; Key.M -> "M"; Key.N -> "N"; Key.O -> "O"
                                Key.P -> "P"; Key.Q -> "Q"; Key.R -> "R"; Key.S -> "S"; Key.T -> "T"
                                Key.U -> "U"; Key.V -> "V"; Key.W -> "W"; Key.X -> "X"; Key.Y -> "Y"
                                Key.Z -> "Z"; Key.Zero -> "0"; Key.One -> "1"; Key.Two -> "2"
                                Key.Three -> "3"; Key.Four -> "4"; Key.Five -> "5"; Key.Six -> "6"
                                Key.Seven -> "7"; Key.Eight -> "8"; Key.Nine -> "9"
                                else -> key.toString().replace("Key: ", "")
                            }
                            sb.append(keyStr)
                            onShortcutChanged(sb.toString())
                            onStopRecording()
                            return@onKeyEvent true
                        }
                    }
                    false
                }
                .focusable(true)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (isRecording) "Press Keys..." else shortcut,
                    color = if (isRecording) PS5ThemeColors.AccentCyan else Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun GotoDialog(
    onDismiss: () -> Unit,
    onGoto: (Long) -> Unit
) {
    var addressText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Address", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter hexadecimal address or function name", color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PS5ThemeColors.AccentCyan,
                        unfocusedBorderColor = PS5ThemeColors.BorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val addr = addressText.trim().removePrefix("0x").toLongOrNull(16)
                    if (addr != null) {
                        onGoto(addr)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
            ) {
                Text("Go", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = PS5ThemeColors.DarkBg
    )
}
