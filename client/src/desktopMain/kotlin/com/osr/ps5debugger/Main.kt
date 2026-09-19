package com.osr.ps5debugger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.painterResource
import com.osr.ps5debugger.ui.MainView
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.Ps5DebuggerTheme
import com.osr.ps5debugger.di.AppContainer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.awt.awtEventOrNull
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Timer

fun main() {
    AppContainer.filePicker = object : com.osr.ps5debugger.ports.inbound.FilePicker {
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

        override fun saveText(defaultName: String, content: String, onResult: (Boolean) -> Unit) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Export Memory Viewer", java.awt.FileDialog.SAVE)
                dialog.file = defaultName
                dialog.isVisible = true
                val directory = dialog.directory
                val file = dialog.file
                if (directory != null && file != null) {
                    java.io.File(directory, file).writeText(content, Charsets.UTF_8)
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

        override fun loadEboot(onResult: (ByteArray?, String?) -> Unit) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Load eboot", java.awt.FileDialog.LOAD)
                dialog.isVisible = true
                val directory = dialog.directory
                val file = dialog.file
                if (directory != null && file != null) {
                    val targetFile = java.io.File(directory, file)
                    onResult(targetFile.readBytes(), file)
                } else {
                    onResult(null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null, null)
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
        val scope = rememberCoroutineScope()
        val state = com.osr.ps5debugger.ui.state.rememberMainState(scope = scope, onExit = ::exitApplication)

        val windowState = rememberWindowState(
        width = 1280.dp,
        height = 860.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "PlayStation 5 Debugger NG Client",
        state = windowState,
        undecorated = true,
        icon = painterResource("logo.png")
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
            isAnimatingWindow = true
            window.bounds = target
            isAnimatingWindow = false
            onDone()
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

        DisposableEffect(window) {
            com.osr.ps5debugger.ui.FileBrowserDragDropHelper.mainWindow = window
            com.osr.ps5debugger.ui.FileBrowserDragDropHelper.startDragOut = { pointerEvent ->
                val pe = pointerEvent as? androidx.compose.ui.input.pointer.PointerEvent
                val awtEvent = pe?.awtEventOrNull
                if (awtEvent != null) {
                    val comp = window.contentPane as javax.swing.JComponent
                    comp.transferHandler = com.osr.ps5debugger.ui.FileBrowserTransferHandler()
                    comp.transferHandler.exportAsDrag(comp, awtEvent, javax.swing.TransferHandler.COPY)
                }
            }

            val dropTarget = java.awt.dnd.DropTarget(window, object : java.awt.dnd.DropTargetListener {
                override fun dragEnter(dtde: java.awt.dnd.DropTargetDragEvent) {
                    if (com.osr.ps5debugger.ui.FileBrowserDragDropHelper.isFileBrowserActive && 
                        dtde.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dragOver(dtde: java.awt.dnd.DropTargetDragEvent) {
                    if (com.osr.ps5debugger.ui.FileBrowserDragDropHelper.isFileBrowserActive && 
                        dtde.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dropActionChanged(dtde: java.awt.dnd.DropTargetDragEvent) {}
                override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {}

                override fun drop(dtde: java.awt.dnd.DropTargetDropEvent) {
                    if (!com.osr.ps5debugger.ui.FileBrowserDragDropHelper.isFileBrowserActive || 
                        !dtde.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        dtde.rejectDrop()
                        return
                    }

                    dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                    try {
                        val transferable = dtde.transferable
                        val files = transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File>
                        
                        val consoleIp = com.osr.ps5debugger.ui.FileBrowserDragDropHelper.consoleIp
                        val currentPath = com.osr.ps5debugger.ui.FileBrowserDragDropHelper.currentPath
                        
                        if (consoleIp.isNotEmpty() && files.isNotEmpty()) {
                            com.osr.ps5debugger.ui.FileBrowserDragDropHelper.onUploadStarted?.invoke()
                            
                            Thread {
                                try {
                                    val ftpClient = com.osr.ps5debugger.network.Ps5FtpClient(consoleIp)
                                    
                                    // Reset global choices for this operation batch
                                    com.osr.ps5debugger.ui.FileBrowserDragDropHelper.rememberConflictChoice = false
                                    com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictResolution = null

                                    var currentItem = 0
                                    val totalItems = files.size

                                    javax.swing.SwingUtilities.invokeLater {
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.isTransferring = true
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.transferProgress = 0f
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.transferStatusText = "Initializing uploads..."
                                    }

                                    fun checkConflictAndProceed(fileName: String, targetPath: String): Boolean {
                                        if (com.osr.ps5debugger.ui.FileBrowserDragDropHelper.rememberConflictChoice && 
                                            com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictResolution != null) {
                                            return com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictResolution == com.osr.ps5debugger.ui.FileBrowserDragDropHelper.OverwriteAction.OVERWRITE
                                        }

                                        // Check if remote file exists
                                        var exists = false
                                        try {
                                            val parentPath = targetPath.substringBeforeLast("/", "/")
                                            val existingFiles = kotlinx.coroutines.runBlocking { ftpClient.listFiles(parentPath) }
                                            exists = existingFiles.any { it.name.equals(fileName, ignoreCase = true) }
                                        } catch (_: Exception) {}

                                        if (!exists) return true

                                        // We have a conflict! Ask the user
                                        synchronized(com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictLock) {
                                            javax.swing.SwingUtilities.invokeLater {
                                                com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictFileName = fileName
                                                com.osr.ps5debugger.ui.FileBrowserDragDropHelper.showConflictDialog = true
                                            }

                                            // Block thread until resolved
                                            while (com.osr.ps5debugger.ui.FileBrowserDragDropHelper.showConflictDialog) {
                                                try {
                                                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                                                    (com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictLock as Object).wait(100)
                                                } catch (_: Exception) {}
                                            }
                                        }

                                        return com.osr.ps5debugger.ui.FileBrowserDragDropHelper.conflictResolution == com.osr.ps5debugger.ui.FileBrowserDragDropHelper.OverwriteAction.OVERWRITE
                                    }
                                    
                                    fun uploadRecursive(localFile: java.io.File, remoteDir: String) {
                                        val targetPath = if (remoteDir.endsWith("/")) "$remoteDir${localFile.name}" else "$remoteDir/${localFile.name}"
                                        
                                        if (localFile.isDirectory) {
                                            kotlinx.coroutines.runBlocking {
                                                try { ftpClient.createDirectory(targetPath) } catch (_: Exception) {}
                                            }
                                            localFile.listFiles()?.forEach { subFile ->
                                                uploadRecursive(subFile, targetPath)
                                            }
                                        } else {
                                            if (checkConflictAndProceed(localFile.name, targetPath)) {
                                                kotlinx.coroutines.runBlocking {
                                                    localFile.inputStream().use { ftpClient.uploadFile(targetPath, it) }
                                                }
                                            }
                                        }
                                    }

                                    files.forEach { file ->
                                        currentItem++
                                        val idx = currentItem
                                        javax.swing.SwingUtilities.invokeLater {
                                            com.osr.ps5debugger.ui.FileBrowserDragDropHelper.transferStatusText = "Uploading ${file.name} ($idx/$totalItems)..."
                                            com.osr.ps5debugger.ui.FileBrowserDragDropHelper.transferProgress = (idx - 1).toFloat() / totalItems
                                        }
                                        uploadRecursive(file, currentPath)
                                    }
                                    
                                    javax.swing.SwingUtilities.invokeLater {
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.isTransferring = false
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.transferProgress = 1f
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.onUploadFinished?.invoke(null)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    javax.swing.SwingUtilities.invokeLater {
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.isTransferring = false
                                        com.osr.ps5debugger.ui.FileBrowserDragDropHelper.onUploadFinished?.invoke("Upload failed: ${e.message}")
                                    }
                                }
                            }.start()
                        }
                        dtde.dropComplete(true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        dtde.dropComplete(false)
                    }
                }
            })
            window.dropTarget = dropTarget

            onDispose {
                com.osr.ps5debugger.ui.FileBrowserDragDropHelper.mainWindow = null
                com.osr.ps5debugger.ui.FileBrowserDragDropHelper.startDragOut = null
                window.dropTarget = null
            }
        }

        AppWindowFrame(
            state = state,
            isMaximized = isCustomMaximized,
            onMinimize = { windowState.isMinimized = true },
            onToggleMaximize = toggleMaximize,
            onRestoreForDrag = restoreForDrag,
            onClose = ::exitApplication
        )
    }

    if (state.isConsoleFloating && state.isConsoleVisible) {
        Window(
            onCloseRequest = { state.isConsoleFloating = false },
            title = "PS5 Debugger - Console Logs",
            state = rememberWindowState(width = 900.dp, height = 500.dp),
            icon = painterResource("logo.png")
        ) {
            Ps5DebuggerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PS5ThemeColors.DarkBg
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(PS5ThemeColors.SecondaryBg)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "External Console Logs",
                                color = PS5ThemeColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { state.isConsoleFloating = false }, modifier = Modifier.size(24.dp)) {
                                Icon(com.osr.ps5debugger.ui.icons.PS5Icons.Dock, contentDescription = "Dock", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { state.isConsoleVisible = false }, modifier = Modifier.size(24.dp)) {
                                Icon(com.osr.ps5debugger.ui.icons.PS5Icons.Close, contentDescription = "Close", tint = PS5ThemeColors.StatusRed, modifier = Modifier.size(16.dp))
                            }
                        }
                        com.osr.ps5debugger.ui.LoggerConsole(
                            modifier = Modifier.fillMaxSize(),
                            onAddressClick = { addr ->
                                AppContainer.onNavigateToMemory?.invoke(addr)
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun WindowScope.AppWindowFrame(
    state: com.osr.ps5debugger.ui.state.MainState,
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
                MainView(state = state)
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
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val dotColor = if (isConnected) PS5ThemeColors.AccentCyan else PS5ThemeColors.StatusRed

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
                    drawCircle(color = dotColor.copy(alpha = 0.18f), radius = size.minDimension / 2f)
                    drawCircle(color = dotColor, radius = size.minDimension / 4.5f)
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
