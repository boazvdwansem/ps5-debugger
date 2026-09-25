package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.osr.ps5debugger.ui.icons.PS5Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.network.Ps5FtpClient
import com.osr.ps5debugger.util.DefaultIpHelper
import com.osr.ps5debugger.util.decodeImage
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

@Composable
fun FileBrowserView() {
    val coroutineScope = rememberCoroutineScope()
    val consoleIp = remember { AppContainer.clientAdapter.connection.ipAddress ?: DefaultIpHelper.getDefaultIp() ?: "" }
    
    var currentPath by remember { mutableStateOf("/") }
    var fileList by remember { mutableStateOf<List<Ps5FtpClient.FtpFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Selection Management
    val selectedFiles = remember { mutableStateListOf<Ps5FtpClient.FtpFile>() }
    var lastSelectedIndex by remember { mutableStateOf(-1) }
    
    // Multi-Item Clipboard for copy/paste
    val clipboardFiles = remember { mutableStateListOf<String>() }
    
    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var showRenameDialog by remember { mutableStateOf<Ps5FtpClient.FtpFile?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    
    var showChmodDialog by remember { mutableStateOf<Ps5FtpClient.FtpFile?>(null) }
    var chmodValue by remember { mutableStateOf("777") }
    
    var fileToDelete by remember { mutableStateOf<Ps5FtpClient.FtpFile?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Viewing / Editing
    var editingFile by remember { mutableStateOf<Ps5FtpClient.FtpFile?>(null) }
    var editingContent by remember { mutableStateOf("") }
    
    var viewingImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var viewingImageName by remember { mutableStateOf("") }

    // Right Click Context Menu State
    var contextMenuFile by remember { mutableStateOf<Ps5FtpClient.FtpFile?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    // Path Editing State
    var isEditingPath by remember { mutableStateOf(false) }
    var editedPathText by remember { mutableStateOf("") }
    val pathFocusRequester = remember { FocusRequester() }

    // Async Image Icons Cache for real thumbnails
    val imageThumbnails = remember { mutableStateMapOf<String, ImageBitmap?>() }

    val ftpClient = remember(consoleIp) { Ps5FtpClient(consoleIp) }
    val focusRequester = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    
    val refreshFiles = {
        if (consoleIp.isNotEmpty()) {
            isLoading = true
            errorMessage = null
            selectedFiles.clear()
            lastSelectedIndex = -1
            coroutineScope.launch {
                try {
                    fileList = ftpClient.listFiles(currentPath)
                    
                    // Trigger asynchronous lazy loading for picture file previews
                    fileList.forEach { f ->
                        val ext = f.name.substringAfterLast(".", "").lowercase()
                        if (ext in listOf("png", "jpg", "jpeg", "bmp", "webp")) {
                            if (!imageThumbnails.containsKey(f.name)) {
                                launch {
                                    try {
                                        val target = if (currentPath == "/") "/${f.name}" else "$currentPath/${f.name}"
                                        val baos = ByteArrayOutputStream()
                                        ftpClient.downloadFile(target, baos)
                                        imageThumbnails[f.name] = decodeImage(baos.toByteArray())
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = "${e::class.simpleName}: ${e.message ?: "Connection timed out"}\n"
                    AppContainer.debuggerUseCase.log("FTP", "Error loading $currentPath: ${e.message ?: "Connection timed out"}", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                } finally {
                    isLoading = false
                }
            }
        } else {
            errorMessage = "Console IP is not set. Please connect first."
        }
    }

    LaunchedEffect(currentPath, consoleIp) {
        refreshFiles()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(currentPath, consoleIp) {
        FileBrowserDragDropHelper.currentPath = currentPath
        FileBrowserDragDropHelper.consoleIp = consoleIp
        FileBrowserDragDropHelper.isFileBrowserActive = true
        FileBrowserDragDropHelper.onUploadStarted = {
            isLoading = true
        }
        FileBrowserDragDropHelper.onUploadFinished = { err ->
            isLoading = false
            if (err != null) {
                errorMessage = err
            }
            refreshFiles()
        }
        onDispose {
            FileBrowserDragDropHelper.isFileBrowserActive = false
            FileBrowserDragDropHelper.onUploadStarted = null
            FileBrowserDragDropHelper.onUploadFinished = null
        }
    }

    val openInDefaultApp = { file: Ps5FtpClient.FtpFile ->
        val target = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
        isLoading = true
        coroutineScope.launch {
            try {
                val baos = ByteArrayOutputStream()
                ftpClient.downloadFile(target, baos)
                val tempFile = File.createTempFile("ps5_", "_${file.name}")
                tempFile.deleteOnExit()
                tempFile.writeBytes(baos.toByteArray())
                
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", tempFile.absolutePath))
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(arrayOf("open", tempFile.absolutePath))
                } else {
                    Runtime.getRuntime().exec(arrayOf("xdg-open", tempFile.absolutePath))
                }
            } catch (e: Exception) {
                errorMessage = "Failed to trigger default application: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (isEditingPath) return@onKeyEvent false

                // Ctrl + C Action
                if (event.key == Key.C && event.isCtrlPressed && event.type == KeyEventType.KeyDown) {
                    if (selectedFiles.isNotEmpty()) {
                        clipboardFiles.clear()
                        selectedFiles.forEach { f ->
                            val fullP = if (currentPath == "/") "/${f.name}" else "$currentPath/${f.name}"
                            clipboardFiles.add(fullP)
                        }
                    }
                    return@onKeyEvent true
                }
                
                // Ctrl + V Action
                if (event.key == Key.V && event.isCtrlPressed && event.type == KeyEventType.KeyDown) {
                    if (clipboardFiles.isNotEmpty()) {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                clipboardFiles.forEach { sourcePath ->
                                    val fileName = sourcePath.substringAfterLast("/")
                                    val destPath = if (currentPath.endsWith("/")) "$currentPath$fileName" else "$currentPath/$fileName"
                                    val baos = ByteArrayOutputStream()
                                    ftpClient.downloadFile(sourcePath, baos)
                                    val bais = ByteArrayInputStream(baos.toByteArray())
                                    ftpClient.uploadFile(destPath, bais)
                                }
                                refreshFiles()
                            } catch (e: Exception) {
                                errorMessage = "Paste failed: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                    return@onKeyEvent true
                }
                false
            }
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(PS5ThemeColors.SecondaryBg).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (currentPath != "/") {
                        val parts = currentPath.split("/").filter { it.isNotEmpty() }
                        currentPath = "/" + parts.dropLast(1).joinToString("/")
                    }
                },
                enabled = currentPath != "/"
            ) {
                Icon(PS5Icons.ArrowBack, contentDescription = "Back", tint = if (currentPath != "/") PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted)
            }

            if (isEditingPath) {
                var hasGainedFocus by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f).padding(end = 16.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = editedPathText,
                        onValueChange = { editedPathText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(pathFocusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter) {
                                    var cleanedPath = editedPathText.trim()
                                    if (!cleanedPath.startsWith("/")) {
                                        cleanedPath = "/$cleanedPath"
                                    }
                                    currentPath = cleanedPath
                                    isEditingPath = false
                                    true
                                } else if (keyEvent.key == Key.Escape) {
                                    isEditingPath = false
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasGainedFocus = true
                                }
                                if (!focusState.isFocused && hasGainedFocus && isEditingPath) {
                                    var cleanedPath = editedPathText.trim()
                                    if (cleanedPath.isNotEmpty()) {
                                        if (!cleanedPath.startsWith("/")) {
                                            cleanedPath = "/$cleanedPath"
                                        }
                                        currentPath = cleanedPath
                                    }
                                    isEditingPath = false
                                }
                            },
                        textStyle = TextStyle(
                            color = PS5ThemeColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(PS5ThemeColors.AccentCyan),
                        singleLine = true
                    )
                }
                LaunchedEffect(Unit) {
                    pathFocusRequester.requestFocus()
                }
            } else {
                Text(
                    text = currentPath,
                    color = PS5ThemeColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            editedPathText = currentPath
                            isEditingPath = true
                        }
                )
            }

            // Actions
            Button(
                onClick = { showNewFolderDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg),
                modifier = Modifier.border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
            ) {
                Icon(PS5Icons.NewFolder, contentDescription = "New Folder", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Folder", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    try {
                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select File to Upload", java.awt.FileDialog.LOAD)
                        dialog.isVisible = true
                        if (dialog.directory != null && dialog.file != null) {
                            val localFile = File(dialog.directory, dialog.file)
                            if (localFile.exists()) {
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        val remotePath = if (currentPath.endsWith("/")) "$currentPath${localFile.name}" else "$currentPath/${localFile.name}"
                                        localFile.inputStream().use { ftpClient.uploadFile(remotePath, it) }
                                        refreshFiles()
                                    } catch (e: Exception) {
                                        errorMessage = "Upload failed: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "File picker failed: ${e.message}"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg),
                modifier = Modifier.border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
            ) {
                Icon(PS5Icons.Upload, contentDescription = "Upload", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Upload File", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
            }

            if (clipboardFiles.isNotEmpty()) {
                Button(
                    onClick = {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                clipboardFiles.forEach { sourcePath ->
                                    val fileName = sourcePath.substringAfterLast("/")
                                    val destPath = if (currentPath.endsWith("/")) "$currentPath$fileName" else "$currentPath/$fileName"
                                    val baos = ByteArrayOutputStream()
                                    ftpClient.downloadFile(sourcePath, baos)
                                    val bais = ByteArrayInputStream(baos.toByteArray())
                                    ftpClient.uploadFile(destPath, bais)
                                }
                                refreshFiles()
                            } catch (e: Exception) {
                                errorMessage = "Paste failed: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
                ) {
                    Icon(PS5Icons.Paste, contentDescription = "Paste", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste (${clipboardFiles.size})", color = Color.Black, fontSize = 12.sp)
                }
            }

            IconButton(onClick = { refreshFiles() }) {
                Icon(PS5Icons.Refresh, contentDescription = "Refresh", tint = PS5ThemeColors.TextMain)
            }
        }

        HorizontalDivider(color = PS5ThemeColors.BorderColor)

        Row(
            modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.Surface).padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(PS5Icons.Info, contentDescription = "Context Hint", tint = PS5ThemeColors.AccentAmber, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tip: Right click any item to see full option context menus (Copy, Paste, Rename, Chmod, Open In, etc.)", color = PS5ThemeColors.TextMuted, fontSize = 11.sp)
        }

        HorizontalDivider(color = PS5ThemeColors.BorderColor)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage ?: "", color = PS5ThemeColors.StatusRed, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { refreshFiles() }, colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)) {
                        Text("Retry", color = Color.Black)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(fileList) { index, file ->
                        val isSelected = selectedFiles.contains(file)
                        @OptIn(ExperimentalFoundationApi::class)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.15f) else PS5ThemeColors.DarkBg)
                                .pointerInput(file) {
                                    var isPrimaryPressed = false
                                    var pressPosition = androidx.compose.ui.geometry.Offset.Zero
                                    awaitPointerEventScope {
                                        while (true) {
                                            val pointerEvent = awaitPointerEvent()
                                            val km = pointerEvent.keyboardModifiers

                                            if (pointerEvent.type == PointerEventType.Press) {
                                                if (pointerEvent.buttons.isSecondaryPressed) {
                                                    val pos = pointerEvent.changes.first().position
                                                    contextMenuOffset = DpOffset(pos.x.toDp(), pos.y.toDp())
                                                    contextMenuFile = file
                                                    showContextMenu = true
                                                } else if (pointerEvent.buttons.isPrimaryPressed) {
                                                    isPrimaryPressed = true
                                                    pressPosition = pointerEvent.changes.first().position
                                                    
                                                    if (km.isCtrlPressed) {
                                                        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                                                        lastSelectedIndex = index
                                                    } else if (km.isShiftPressed && lastSelectedIndex != -1) {
                                                        selectedFiles.clear()
                                                        val start = minOf(lastSelectedIndex, index)
                                                        val end = maxOf(lastSelectedIndex, index)
                                                        for (i in start..end) {
                                                            selectedFiles.add(fileList[i])
                                                        }
                                                    } else {
                                                        if (!selectedFiles.contains(file)) {
                                                            selectedFiles.clear()
                                                            selectedFiles.add(file)
                                                            lastSelectedIndex = index
                                                        }
                                                    }

                                                    FileBrowserDragDropHelper.activeDragFiles = selectedFiles.map {
                                                        FileBrowserDragDropHelper.DragItem(
                                                            name = it.name,
                                                            remotePath = if (currentPath == "/") "/${it.name}" else "$currentPath/${it.name}",
                                                            isDirectory = it.isDirectory
                                                        )
                                                    }
                                                }
                                            } else if (pointerEvent.type == PointerEventType.Move && isPrimaryPressed) {
                                                val currentPos = pointerEvent.changes.first().position
                                                val diff = currentPos - pressPosition
                                                if (diff.getDistance() > 5) {
                                                    isPrimaryPressed = false
                                                    FileBrowserDragDropHelper.startDragOut?.invoke(pointerEvent)
                                                }
                                            } else if (pointerEvent.type == PointerEventType.Release) {
                                                isPrimaryPressed = false
                                            }
                                        }
                                    }
                                }
                                .clickable(
                                    onClick = {
                                        val km = windowInfo.keyboardModifiers
                                        if (!km.isCtrlPressed && !km.isShiftPressed) {
                                            if (selectedFiles.size > 1 && selectedFiles.contains(file)) {
                                                selectedFiles.clear()
                                                selectedFiles.add(file)
                                                lastSelectedIndex = index
                                            }

                                            val target = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                                            if (file.isDirectory) {
                                                currentPath = target
                                            } else {
                                                val ext = file.name.substringAfterLast(".", "").lowercase()
                                                if (ext in listOf("png", "jpg", "jpeg", "bmp", "webp")) {
                                                    isLoading = true
                                                    coroutineScope.launch {
                                                        try {
                                                            val baos = ByteArrayOutputStream()
                                                            ftpClient.downloadFile(target, baos)
                                                            viewingImage = decodeImage(baos.toByteArray())
                                                            viewingImageName = file.name
                                                        } catch (e: Exception) {
                                                            errorMessage = "Failed to load image: ${e.message}"
                                                        } finally {
                                                            isLoading = false
                                                        }
                                                    }
                                                } else {
                                                    isLoading = true
                                                    coroutineScope.launch {
                                                        try {
                                                            val baos = ByteArrayOutputStream()
                                                            ftpClient.downloadFile(target, baos)
                                                            editingContent = baos.toString("UTF-8")
                                                            editingFile = file
                                                        } catch (e: Exception) {
                                                            errorMessage = "Failed to load text: ${e.message}"
                                                        } finally {
                                                            isLoading = false
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Specialized Type Specific Vector Graphics & Preview Rendering
                                val ext = file.name.substringAfterLast(".", "").lowercase()
                                val thumbnail = imageThumbnails[file.name]
                                
                                if (thumbnail != null) {
                                    Image(
                                        bitmap = thumbnail,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp).border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(2.dp))
                                    )
                                } else {
                                    val (iconVector, iconColor) = when {
                                        file.isDirectory -> Pair(PS5Icons.Folder, PS5ThemeColors.AccentAmber)
                                        ext in listOf("txt", "log", "ini") -> Pair(PS5Icons.FileDocument, Color(0xFFC9D1D9))
                                        ext == "json" -> Pair(PS5Icons.FileJson, Color(0xFFFEC260))
                                        ext in listOf("bin", "elf", "pkg") -> Pair(PS5Icons.FileExecutable, Color(0xFF00D2FF))
                                        ext in listOf("prx", "sprx") -> Pair(PS5Icons.FileLibrary, Color(0xFF39D353))
                                        ext == "db" -> Pair(PS5Icons.FileDatabase, Color(0xFFFEC260))
                                        else -> Pair(PS5Icons.FileGeneric, PS5ThemeColors.TextMuted)
                                    }
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, color = PS5ThemeColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("${file.permissions}  •  ${formatSize(file.size)}", color = PS5ThemeColors.TextMuted, fontSize = 10.sp)
                                }

                                IconButton(onClick = {
                                    clipboardFiles.clear()
                                    val fullP = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                                    clipboardFiles.add(fullP)
                                }) {
                                    Icon(PS5Icons.Copy, contentDescription = "Copy", tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(16.dp))
                                }

                                IconButton(onClick = {
                                    fileToDelete = file
                                    showDeleteConfirmation = true
                                }) {
                                    Icon(PS5Icons.Delete, contentDescription = "Delete", tint = PS5ThemeColors.StatusRed, modifier = Modifier.size(16.dp))
                                }
                            }

                            if (showContextMenu && contextMenuFile == file) {
                                Box(modifier = Modifier.offset(contextMenuOffset.x, contextMenuOffset.y).size(0.dp)) {
                                    DropdownMenu(
                                        expanded = true,
                                        onDismissRequest = { showContextMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Copy") },
                                            leadingIcon = { Icon(PS5Icons.Copy, contentDescription = null) },
                                            onClick = {
                                                clipboardFiles.clear()
                                                val fullP = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                                                clipboardFiles.add(fullP)
                                                showContextMenu = false
                                            }
                                        )
                                        if (clipboardFiles.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Paste") },
                                                leadingIcon = { Icon(PS5Icons.Paste, contentDescription = null) },
                                                onClick = {
                                                    showContextMenu = false
                                                    isLoading = true
                                                    coroutineScope.launch {
                                                        try {
                                                            clipboardFiles.forEach { sourcePath ->
                                                                val fileName = sourcePath.substringAfterLast("/")
                                                                val destPath = if (currentPath.endsWith("/")) "$currentPath$fileName" else "$currentPath/$fileName"
                                                                val baos = ByteArrayOutputStream()
                                                                ftpClient.downloadFile(sourcePath, baos)
                                                                val bais = ByteArrayInputStream(baos.toByteArray())
                                                                ftpClient.uploadFile(destPath, bais)
                                                            }
                                                            refreshFiles()
                                                        } catch (e: Exception) {
                                                            errorMessage = "Paste failed: ${e.message}"
                                                        } finally {
                                                            isLoading = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        if (!file.isDirectory) {
                                            DropdownMenuItem(
                                                text = { Text("Open In (Default App OS)") },
                                                leadingIcon = { Icon(PS5Icons.OpenIn, contentDescription = null) },
                                                onClick = {
                                                    showContextMenu = false
                                                    openInDefaultApp(file)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            leadingIcon = { Icon(PS5Icons.Edit, contentDescription = null) },
                                            onClick = {
                                                showContextMenu = false
                                                renameNewName = file.name
                                                showRenameDialog = file
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Change Permissions (Chmod)") },
                                            leadingIcon = { Icon(PS5Icons.Lock, contentDescription = null) },
                                            onClick = {
                                                showContextMenu = false
                                                chmodValue = "777"
                                                showChmodDialog = file
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = PS5ThemeColors.StatusRed) },
                                            leadingIcon = { Icon(PS5Icons.Delete, contentDescription = null, tint = PS5ThemeColors.StatusRed) },
                                            onClick = {
                                                showContextMenu = false
                                                fileToDelete = file
                                                showDeleteConfirmation = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // Dynamic Transfer Progress Overlay Bar on the bottom
    if (FileBrowserDragDropHelper.isTransferring) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.SecondaryBg),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(FileBrowserDragDropHelper.transferStatusText, color = PS5ThemeColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("${(FileBrowserDragDropHelper.transferProgress * 100).toInt()}%", color = PS5ThemeColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { FileBrowserDragDropHelper.transferProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = PS5ThemeColors.AccentCyan,
                    trackColor = PS5ThemeColors.DarkBg
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Confirm Deletion", color = PS5ThemeColors.TextMain) },
            text = { Text("Are you sure you want to permanently delete '${fileToDelete?.name}' from the PS5 file system?", color = PS5ThemeColors.TextMain) },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let { file ->
                        val target = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                if (file.isDirectory) ftpClient.deleteDirectory(target) else ftpClient.deleteFile(target)
                                showDeleteConfirmation = false
                                fileToDelete = null
                                refreshFiles()
                            } catch (e: Exception) {
                                errorMessage = "Delete failed: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }) {
                    Text("Delete", color = PS5ThemeColors.StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = PS5ThemeColors.TextMuted)
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder", color = PS5ThemeColors.TextMain) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PS5ThemeColors.AccentCyan)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotEmpty()) {
                        val path = if (currentPath == "/") "/$newFolderName" else "$currentPath/$newFolderName"
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                ftpClient.createDirectory(path)
                                showNewFolderDialog = false
                                newFolderName = ""
                                refreshFiles()
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }) {
                    Text("Create", color = PS5ThemeColors.AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = PS5ThemeColors.TextMuted)
                }
            }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename File / Folder", color = PS5ThemeColors.TextMain) },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PS5ThemeColors.AccentCyan)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameNewName.isNotEmpty() && renameNewName != file.name) {
                        val oldP = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                        val newP = if (currentPath == "/") "/$renameNewName" else "$currentPath/$renameNewName"
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                ftpClient.rename(oldP, newP)
                                showRenameDialog = null
                                refreshFiles()
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }) {
                    Text("Rename", color = PS5ThemeColors.AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel", color = PS5ThemeColors.TextMuted)
                }
            }
        )
    }

    // Chmod Dialog
    showChmodDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showChmodDialog = null },
            title = { Text("Change Permissions (Chmod)", color = PS5ThemeColors.TextMain) },
            text = {
                OutlinedTextField(
                    value = chmodValue,
                    onValueChange = { chmodValue = it },
                    label = { Text("Octal Permissions (e.g. 777, 755)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PS5ThemeColors.AccentCyan)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (chmodValue.isNotEmpty()) {
                        val p = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                ftpClient.chmod(p, chmodValue)
                                showChmodDialog = null
                                refreshFiles()
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }) {
                    Text("Apply", color = PS5ThemeColors.AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChmodDialog = null }) {
                    Text("Cancel", color = PS5ThemeColors.TextMuted)
                }
            }
        )
    }

    // Full Screen Text Editor View
    editingFile?.let { file ->
        Box(modifier = Modifier.fillMaxSize().background(PS5ThemeColors.DarkBg).padding(16.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editing: ${file.name}", color = PS5ThemeColors.TextMain, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val target = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        val bais = ByteArrayInputStream(editingContent.toByteArray(Charsets.UTF_8))
                                        ftpClient.uploadFile(target, bais)
                                        editingFile = null
                                        refreshFiles()
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.StatusGreen)
                        ) {
                            Text("Save & Write Back", color = Color.Black)
                        }
                        Button(
                            onClick = { editingFile = null },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                        ) {
                            Text("Cancel", color = PS5ThemeColors.TextMain)
                        }
                    }
                }
                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PS5ThemeColors.AccentCyan)
                )
            }
        }
    }

    // ImageViewer Dialog
    viewingImage?.let { img ->
        AlertDialog(
            onDismissRequest = { viewingImage = null },
            title = { Text(viewingImageName, color = PS5ThemeColors.TextMain) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    Image(bitmap = img, contentDescription = viewingImageName, modifier = Modifier.fillMaxSize())
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingImage = null }) {
                    Text("Close", color = PS5ThemeColors.AccentCyan)
                }
            }
        )
    }

    // Overwrite Conflict Dialog for Drag & Drop Uploads
    if (FileBrowserDragDropHelper.showConflictDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("File Conflict Detected", color = PS5ThemeColors.TextMain) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "A file named \"${FileBrowserDragDropHelper.conflictFileName}\" already exists in the destination folder. What would you like to do?",
                        color = PS5ThemeColors.TextMain,
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { FileBrowserDragDropHelper.rememberConflictChoice = !FileBrowserDragDropHelper.rememberConflictChoice }
                    ) {
                        Checkbox(
                            checked = FileBrowserDragDropHelper.rememberConflictChoice,
                            onCheckedChange = { FileBrowserDragDropHelper.rememberConflictChoice = it },
                            colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Apply to all remaining conflicts in this transfer", color = PS5ThemeColors.TextMuted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        FileBrowserDragDropHelper.conflictResolution = FileBrowserDragDropHelper.OverwriteAction.OVERWRITE
                        FileBrowserDragDropHelper.showConflictDialog = false
                        synchronized(FileBrowserDragDropHelper.conflictLock) {
                            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                            (FileBrowserDragDropHelper.conflictLock as Object).notifyAll()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
                ) {
                    Text("Overwrite", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        FileBrowserDragDropHelper.conflictResolution = FileBrowserDragDropHelper.OverwriteAction.SKIP
                        FileBrowserDragDropHelper.showConflictDialog = false
                        synchronized(FileBrowserDragDropHelper.conflictLock) {
                            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                            (FileBrowserDragDropHelper.conflictLock as Object).notifyAll()
                        }
                    }
                ) {
                    Text("Skip", color = PS5ThemeColors.TextMuted)
                }
            }
        )
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
