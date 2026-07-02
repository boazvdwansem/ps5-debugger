package com.osr.ps5debugger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.osr.ps5debugger.ui.MainView
import com.osr.ps5debugger.ui.PS5ThemeColors
import com.osr.ps5debugger.ui.Ps5DebuggerTheme
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Timer

fun main() = application {
    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 860.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "PlayStation 5 Debugger NG Client",
        state = windowState,
        undecorated = true
    ) {
        val window = this.window
        val floatingBounds = remember { mutableStateOf<Rectangle?>(null) }
        var isCustomMaximized by remember { mutableStateOf(false) }
        var isAnimatingWindow by remember { mutableStateOf(false) }
        val usableScreenBounds = {
            val config = window.graphicsConfiguration
            val bounds = Rectangle(config.bounds)
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
            bounds.x += insets.left
            bounds.y += insets.top
            bounds.width -= insets.left + insets.right
            bounds.height -= insets.top + insets.bottom
            bounds
        }
        val clampToScreen: (Rectangle) -> Rectangle = { rect ->
            val screen = usableScreenBounds()
            val width = rect.width.coerceIn(640, screen.width)
            val height = rect.height.coerceIn(420, screen.height)
            val x = rect.x.coerceIn(screen.x, screen.x + screen.width - width)
            val y = rect.y.coerceIn(screen.y + 16, screen.y + screen.height - height)
            Rectangle(x, y, width, height)
        }
        val rememberFloatingBounds = {
            val screenTop = window.graphicsConfiguration?.bounds?.y ?: 0
            val bounds = window.bounds
            if (
                bounds.width >= 640 &&
                bounds.height >= 420 &&
                bounds.y > screenTop + 48
            ) {
                floatingBounds.value = clampToScreen(bounds)
            }
        }
        val animateBounds: (Rectangle, () -> Unit) -> Unit = { target, onDone ->
            val start = Rectangle(window.bounds)
            val frames = 18
            var frame = 0
            isAnimatingWindow = true
            val timerRef = arrayOfNulls<Timer>(1)
            timerRef[0] = Timer(10) {
                frame++
                val t = frame.toFloat() / frames.toFloat()
                val eased = if (t < 0.5f) {
                    4f * t * t * t
                } else {
                    1f - (-2f * t + 2f).let { it * it * it } / 2f
                }
                window.bounds = Rectangle(
                    kotlin.math.round(start.x + (target.x - start.x) * eased).toInt(),
                    kotlin.math.round(start.y + (target.y - start.y) * eased).toInt(),
                    kotlin.math.round(start.width + (target.width - start.width) * eased).toInt(),
                    kotlin.math.round(start.height + (target.height - start.height) * eased).toInt()
                )
                if (frame >= frames) {
                    timerRef[0]?.stop()
                    window.bounds = target
                    isAnimatingWindow = false
                    onDone()
                }
            }
            timerRef[0]?.isRepeats = true
            timerRef[0]?.start()
        }
        val isMaximized = { isCustomMaximized }
        val maximizeWindow = {
            if (!isMaximized()) {
                rememberFloatingBounds()
                if (floatingBounds.value == null) {
                    floatingBounds.value = Rectangle(window.bounds).also {
                        it.y = (window.graphicsConfiguration?.bounds?.y ?: 0) + 80
                    }
                }
                animateBounds(usableScreenBounds()) {
                    isCustomMaximized = true
                }
            }
        }
        val restoreWindow = {
            val bounds = floatingBounds.value?.let(clampToScreen)
            if (bounds != null) {
                isCustomMaximized = false
                animateBounds(bounds) {
                    isCustomMaximized = false
                }
            } else {
                isCustomMaximized = false
            }
            Unit
        }
        val restoreForDrag = {
            val pointer = MouseInfo.getPointerInfo()?.location
            val bounds = floatingBounds.value?.let(clampToScreen)
                ?: Rectangle(window.x, window.y + 80, 1280, 860).let(clampToScreen)
            val screen = usableScreenBounds()
            val targetX = if (pointer != null) {
                (pointer.x - bounds.width / 2).coerceIn(screen.x, screen.x + screen.width - bounds.width)
            } else {
                bounds.x
            }
            val targetY = if (pointer != null) {
                (pointer.y - 19).coerceIn(screen.y + 8, screen.y + screen.height - bounds.height)
            } else {
                bounds.y
            }
            isCustomMaximized = false
            window.bounds = Rectangle(targetX, targetY, bounds.width, bounds.height)
            Unit
        }
        val toggleMaximize = {
            if (isMaximized()) restoreWindow() else maximizeWindow()
            Unit
        }

        DisposableEffect(window, windowState) {
            val listener = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent?) {
                    if (!isMaximized() && !isAnimatingWindow) {
                        val screenTop = window.graphicsConfiguration?.bounds?.y ?: 0
                        if (window.y <= screenTop) {
                            maximizeWindow()
                        } else {
                            rememberFloatingBounds()
                        }
                    }
                }

                override fun componentResized(e: ComponentEvent?) {
                    if (!isMaximized() && !windowState.isMinimized && !isAnimatingWindow) {
                        rememberFloatingBounds()
                    }
                }
            }
            val mouseListener = object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2 && e.y in 0..38) {
                        toggleMaximize()
                    }
                }
            }
            window.addComponentListener(listener)
            window.addMouseListener(mouseListener)
            floatingBounds.value = window.bounds
            onDispose {
                window.removeComponentListener(listener)
                window.removeMouseListener(mouseListener)
            }
        }

        AppWindowFrame(
            isMaximized = isCustomMaximized,
            onMinimize = { windowState.isMinimized = true },
            onToggleMaximize = toggleMaximize,
            onRestoreForDrag = restoreForDrag,
            onClose = ::exitApplication
        )
    }
}

@Composable
private fun WindowScope.AppWindowFrame(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onRestoreForDrag: () -> Unit,
    onClose: () -> Unit
) {
    Ps5DebuggerTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(PS5ThemeColors.DarkBg)
                .border(1.dp, PS5ThemeColors.BorderColor),
            color = PS5ThemeColors.DarkBg
        ) {
            Column(Modifier.fillMaxSize()) {
                CustomTitleBar(
                    isMaximized = isMaximized,
                    onMinimize = onMinimize,
                    onToggleMaximize = onToggleMaximize,
                    onRestoreForDrag = onRestoreForDrag,
                    onClose = onClose
                )
                MainView()
            }
        }
    }
}

@Composable
private fun WindowScope.CustomTitleBar(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onRestoreForDrag: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(PS5ThemeColors.Surface)
            .border(width = 0.dp, color = Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TitleDragArea(
            isMaximized = isMaximized,
            onRestoreForDrag = onRestoreForDrag,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    drawCircle(color = PS5ThemeColors.AccentCyan.copy(alpha = 0.18f), radius = size.minDimension / 2f)
                    drawCircle(color = PS5ThemeColors.AccentCyan, radius = size.minDimension / 4.5f)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "PlayStation 5 Debugger NG",
                    color = PS5ThemeColors.TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Client",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp
                )
            }
        }
        WindowControlButton(kind = WindowControlKind.Minimize, onClick = onMinimize)
        WindowControlButton(
            kind = if (isMaximized) WindowControlKind.Restore else WindowControlKind.Maximize,
            onClick = onToggleMaximize
        )
        WindowControlButton(kind = WindowControlKind.Close, onClick = onClose)
    }
}

@Composable
private fun WindowScope.TitleDragArea(
    isMaximized: Boolean,
    onRestoreForDrag: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isMaximized) {
        Box(
            modifier = modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onRestoreForDrag() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        window.setLocation(
                            window.x + dragAmount.x.toInt(),
                            window.y + dragAmount.y.toInt()
                        )
                    }
                )
            }
        ) {
            content()
        }
    } else {
        WindowDraggableArea(modifier = modifier) {
            content()
        }
    }
}

private enum class WindowControlKind { Minimize, Maximize, Restore, Close }

@Composable
private fun WindowControlButton(
    kind: WindowControlKind,
    onClick: () -> Unit
) {
    val isClose = kind == WindowControlKind.Close
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 46.dp, height = 38.dp)
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val color = if (isClose) PS5ThemeColors.StatusRed else PS5ThemeColors.TextMuted
            val stroke = Stroke(width = 2f)
            when (kind) {
                WindowControlKind.Minimize -> {
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(2f, size.height - 3f),
                        end = androidx.compose.ui.geometry.Offset(size.width - 2f, size.height - 3f),
                        strokeWidth = 2f
                    )
                }
                WindowControlKind.Maximize -> {
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(2f, 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - 4f, size.height - 4f),
                        style = stroke
                    )
                }
                WindowControlKind.Restore -> {
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(5f, 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - 6f, size.height - 6f),
                        style = stroke
                    )
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(2f, 5f),
                        size = androidx.compose.ui.geometry.Size(size.width - 6f, size.height - 6f),
                        style = stroke
                    )
                }
                WindowControlKind.Close -> {
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(3f, 3f),
                        end = androidx.compose.ui.geometry.Offset(size.width - 3f, size.height - 3f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width - 3f, 3f),
                        end = androidx.compose.ui.geometry.Offset(3f, size.height - 3f),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}
