package com.osr.ps5debugger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import com.osr.ps5debugger.ui.Ps5DebuggerTheme
import com.osr.ps5debugger.ui.PS5ThemeColors
import com.osr.ps5debugger.MobileMainView

fun main() = application {
    // Standard phone resolution simulator (e.g. 390x844 dp is common for modern iPhone/Android portrait size)
    val windowState = rememberWindowState(
        width = 410.dp,
        height = 840.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "PS5 Debugger Mobile Simulator",
        state = windowState,
        resizable = true
    ) {
        Ps5DebuggerTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = PS5ThemeColors.DarkBg
            ) {
                MobileMainView()
            }
        }
    }
}
