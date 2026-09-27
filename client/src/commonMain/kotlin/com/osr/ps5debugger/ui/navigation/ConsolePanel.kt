package com.osr.ps5debugger.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.ui.common.PS5Icons
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.ui.logger.LoggerConsole
import com.osr.ps5debugger.di.AppContainer

@Composable
internal fun ConsolePanel(state: MainState) {
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


