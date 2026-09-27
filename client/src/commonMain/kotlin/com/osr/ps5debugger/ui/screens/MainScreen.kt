package com.osr.ps5debugger.ui.screens

import com.osr.ps5debugger.di.MetadataResolver
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
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.ui.theme.Ps5DebuggerTheme
import com.osr.ps5debugger.ui.common.PS5Icons
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.*
import com.osr.ps5debugger.ui.connection.ConnectionScreen
import com.osr.ps5debugger.ui.dialogs.SaveSessionDialog
import com.osr.ps5debugger.ui.process.ProcessManager
import com.osr.ps5debugger.ui.memory.map.MemoryMapView
import com.osr.ps5debugger.ui.symbols.SymbolsView
import com.osr.ps5debugger.ui.debugger.DebugSidebar
import com.osr.ps5debugger.ui.debugger.BreakpointsSidebar
import com.osr.ps5debugger.ui.debugger.ReferencesSidebar
import com.osr.ps5debugger.ui.cheats.CheatsSidebar
import com.osr.ps5debugger.ui.cheats.AddCheatDialog
import com.osr.ps5debugger.ui.cheats.CheatsView
import com.osr.ps5debugger.ui.memory.MemoryViewerLayout
import com.osr.ps5debugger.ui.scanner.MemoryScannerView
import com.osr.ps5debugger.ui.watchlist.WatchList
import com.osr.ps5debugger.ui.dumper.MemoryDumperView
import com.osr.ps5debugger.ui.filebrowser.FileBrowserView
import com.osr.ps5debugger.ui.common.Tooltip
import com.osr.ps5debugger.ui.common.ConsoleToggleButton
import com.osr.ps5debugger.ui.common.TabItem
import com.osr.ps5debugger.ui.common.TopMenuBar
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.ui.state.rememberMainState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.osr.ps5debugger.util.DefaultIpHelper
import com.osr.ps5debugger.util.ShortcutManager
import com.osr.ps5debugger.ui.settings.SettingsDialog
import com.osr.ps5debugger.ui.dialogs.GotoDialog
import com.osr.ps5debugger.ui.navigation.ConsolePanel
import com.osr.ps5debugger.ui.navigation.TopBar
import com.osr.ps5debugger.ui.navigation.OpenedRegionsBar

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
        if (!isConnected && !AppContainer.debugMockEnabled && !state.isOfflineSession) {
            ConnectionScreen(
                onSettingsClick = { state.isSettingsOpen = true },
                onLoadEboot = { state.handleFileAction("Load eboot") },
                onLoadSession = { file -> state.loadSessionFile(file) }
            )
        } else {
            MainLayout(state)
        }

        if (state.showSaveSessionDialog) {
            com.osr.ps5debugger.ui.dialogs.SaveSessionDialog(
                state = state,
                onDismiss = { state.showSaveSessionDialog = false }
            )
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

    LaunchedEffect(activeProcess?.pid) {
        if (!AppContainer.isOfflineSession) {
            state.closeAllOpenedRegions()
        }
        if (activeProcess == null) {
            if (activeLeftTab == "map" || activeLeftTab == "symbols") {
                activeLeftTab = "connections"
            }
        }
    }

    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val vmMaps by AppContainer.debuggerUseCase.vmMaps.collectAsState()
    val activeMapsSnapshot = state.activeMaps.toList()
    val allOpenMaps = remember(state.activeMap, activeMapsSnapshot) {
        (listOfNotNull(state.activeMap) + activeMapsSnapshot).distinctBy { it.start }
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
        // TOP BAR (File Edit View, CONNECTED, Settings) - Full width
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isMobileToolbar = maxWidth < 800.dp
            TopBar(state, isMobile = isMobileToolbar, onSettingsClick = { state.isSettingsOpen = true })
        }
        HorizontalDivider(color = PS5ThemeColors.BorderColor)

        // MAIN TABS (Memory Viewer, Memory Search, ...) - Full width
        val tabs = listOf("Memory Viewer", "Memory Search", "Watch List", "Memory Dumper", "Cheats", "File Browser")
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
                    3 -> PS5Icons.MemoryDumperService
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

        // MAIN WORKSPACE (Below the full-width bars)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
            TabContent(
                state = state,
                sidebarsWrapper = { centerContent ->
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
                                        },
                                        onMapsSelected = { maps ->
                                            state.activeMaps.clear()
                                            state.activeMaps.addAll(maps)
                                            if (state.activeMap == null || !maps.any { it.start == state.activeMap?.start }) {
                                                state.activeMap = maps.lastOrNull() ?: maps.firstOrNull()
                                            }
                                        },
                                        onProcessSelected = {
                                            state.closeAllOpenedRegions()
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

                        // CENTER CONTENT
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            centerContent()
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
            )
        }

        ConsolePanel(state)

        if (state.showAddCheatDialog) {
            val procInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
            val titleId = procInfo?.titleId ?: "Unknown"
            val resolvedVersion = MetadataResolver.titleIdToVersion[titleId] ?: "1.00"
            AddCheatDialog(
                titleId = titleId,
                version = resolvedVersion,
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
private fun TabContent(
    state: MainState,
    sidebarsWrapper: @Composable (@Composable () -> Unit) -> Unit
) {
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
                state.xrefTargetAddress = null
            },
            activeBreakpoints = state.activeBreakpoints,
            activeWatchpoints = state.activeWatchpoints,
            onCopySelection = { state.handleEditAction("Copy") },
            onShowXrefs = { address ->
                state.xrefTargetAddress = address
                state.activeRightTab = "references"
            },
            sidebarsWrapper = sidebarsWrapper
        )
        1 -> sidebarsWrapper {
            MemoryScannerView(
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
        }
        2 -> sidebarsWrapper {
            WatchList(onJumpToAddress = { addr ->
                val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
                if (map != null) {
                    state.activeMap = map
                    state.jumpToAddress = addr
                    state.selectedTab = 0
                }
            })
        }
        3 -> sidebarsWrapper { MemoryDumperView() }
        4 -> CheatsView(sidebarsWrapper = sidebarsWrapper)
        5 -> sidebarsWrapper { FileBrowserView() }
    }
}



