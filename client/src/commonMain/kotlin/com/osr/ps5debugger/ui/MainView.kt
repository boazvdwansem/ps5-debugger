package com.osr.ps5debugger.ui

import androidx.compose.runtime.Composable
import com.osr.ps5debugger.ui.screens.MainScreen

@Composable
fun MainView(onExit: () -> Unit = {}) {
    MainScreen(onExit = onExit)
}
