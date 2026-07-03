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

fun main() {
    com.osr.ps5debugger.di.AppContainer.filePicker = object : com.osr.ps5debugger.ports.inbound.FilePicker {
        override fun saveJson(defaultName: String, content: String, onResult: (Boolean) -> Unit) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save Watch List", java.awt.FileDialog.SAVE)
                dialog.file = defaultName
                dialog.isVisible = true
                val directory = dialog.directory
                val file = dialog.file
                if (directory != null && file != null) {
                    val targetFile = java.io.File(directory, file)
                    val finalFile = if (targetFile.extension.equals("json", ignoreCase = true)) targetFile else java.io.File(targetFile.parentFile, "${targetFile.name}.json")
                    finalFile.writeText(content, Charsets.UTF_8)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }

        override fun loadJson(onResult: (String?) -> Unit) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Load Watch List", java.awt.FileDialog.LOAD)
                dialog.file = "*.json"
                dialog.isVisible = true
                val directory = dialog.directory
                val file = dialog.file
                if (directory != null && file != null) {
                    val targetFile = java.io.File(directory, file)
                    onResult(targetFile.readText(Charsets.UTF_8))
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }

        override fun pickDirectory(onResult: (String?) -> Unit) {
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                val result = chooser.showOpenDialog(null)
                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                    onResult(chooser.selectedFile.absolutePath)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    application {
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
}
