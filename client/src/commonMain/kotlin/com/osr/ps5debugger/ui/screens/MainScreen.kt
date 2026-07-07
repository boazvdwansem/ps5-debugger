package com.osr.ps5debugger.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(state)
            HorizontalDivider(color = PS5ThemeColors.BorderColor)

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Sidebar(state)
                MainArea(state, modifier = Modifier.fillMaxHeight().weight(1f))
                DebugSidebarContainer(state)
            }

            ConsolePanel(state)
        }

        if (!state.isConsoleVisible) {
            ConsoleToggleButton(
                onClick = { state.isConsoleVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun TopBar(state: MainState) {
    val coroutineScope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopMenuBar(
            onFileAction = state::handleFileAction,
            onEditAction = state::handleEditAction,
            onViewAction = state::handleViewAction
        )

        Spacer(modifier = Modifier.weight(1f))

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
private fun Sidebar(state: MainState) {
    AnimatedVisibility(
        visible = state.isSidebarVisible,
        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(200)) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            ProcessManager(
                onMapSelected = {
                    state.activeMap = it
                    state.selectedTab = 0
                    state.isSidebarVisible = false
                },
                activeMap = state.activeMap,
                onCollapse = { state.isSidebarVisible = false }
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
        }
    }

    if (!state.isSidebarVisible) {
        SidebarToggle(onClick = { state.isSidebarVisible = true })
        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
    }
}

@Composable
private fun SidebarToggle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .clickable { onClick() },
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
}

@Composable
private fun DebugSidebarToggle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current.density
        Canvas(modifier = Modifier.size(24.dp)) {
            val tintColor = PS5ThemeColors.AccentCyan
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(15f * density, 6f * density)
                lineTo(9f * density, 12f * density)
                lineTo(15f * density, 18f * density)
            }
            drawPath(
                path = path,
                color = tintColor,
                style = Stroke(width = 2f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )
        }
    }
}

@Composable
private fun DebugSidebarContainer(state: MainState) {
    AnimatedVisibility(
        visible = state.isDebugSidebarVisible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
            DebugSidebar(
                state = state,
                onCollapse = { state.isDebugSidebarVisible = false }
            )
        }
    }

    if (!state.isDebugSidebarVisible) {
        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = PS5ThemeColors.BorderColor)
        DebugSidebarToggle(onClick = { state.isDebugSidebarVisible = true })
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
