package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val typeOptions = listOf("Byte", "Int16", "Int32", "Int64", "Float", "Double", "String")
private const val WatchStringMaxBytes = 32

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchList(
    modifier: Modifier = Modifier,
    onJumpToAddress: (Long) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
        val coroutineScope = rememberCoroutineScope()
        val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
        val activePid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val itemsList by AppContainer.debuggerUseCase.watchlist.collectAsState()

        var addLabel by remember { mutableStateOf("") }
        var addAddressStr by remember { mutableStateOf("") }
        var addType by remember { mutableStateOf("Int32") }
        var addComment by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text("Watch List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            ) {
                if (isMobile) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(value = addLabel, onValueChange = { addLabel = it }, label = { Text("Label") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = addAddressStr, onValueChange = { addAddressStr = it }, label = { Text("Address (Hex)") }, modifier = Modifier.weight(1.2f), singleLine = true, textStyle = TextStyle(fontFamily = FontFamily.Monospace))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var addTypeExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(onClick = { addTypeExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(addType, maxLines = 1) }
                                DropdownMenu(expanded = addTypeExpanded, onDismissRequest = { addTypeExpanded = false }) {
                                    typeOptions.forEach { t ->
                                        DropdownMenuItem(text = { Text(t) }, onClick = { addType = t; addTypeExpanded = false })
                                    }
                                }
                            }
                            OutlinedTextField(value = addComment, onValueChange = { addComment = it }, label = { Text("Comment") }, modifier = Modifier.weight(1.8f), singleLine = true)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val addr = addAddressStr.trim().toLongOrNull(16)
                                    if (addr != null && addLabel.isNotEmpty()) {
                                        AppContainer.debuggerUseCase.addWatchItem(WatchItem(addLabel, addr, addType, comment = addComment))
                                        addLabel = ""; addAddressStr = ""; addComment = ""
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Add") }

                            OutlinedButton(
                                onClick = {
                                    val picker = AppContainer.filePicker
                                    if (picker != null) {
                                        picker.saveJson("watchlist.json", watchListToJson(itemsList)) { success ->
                                            if (success) {
                                                AppContainer.debuggerUseCase.log("WATCHLIST", "Saved watch list successfully", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                            } else {
                                                AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to save watch list", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                            }
                                        }
                                    } else {
                                        chooseWatchListFile(mode = FileDialog.SAVE)?.let { file ->
                                            val target = if (file.extension.equals("json", ignoreCase = true)) file else File(file.parentFile, "${file.name}.json")
                                            runCatching {
                                                target.writeText(watchListToJson(itemsList), Charsets.UTF_8)
                                                AppContainer.debuggerUseCase.log("WATCHLIST", "Saved watch list to ${target.absolutePath}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                            }.onFailure {
                                                AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to save watch list: ${it.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }

                            OutlinedButton(
                                onClick = {
                                    val picker = AppContainer.filePicker
                                    if (picker != null) {
                                        picker.loadJson { json ->
                                            if (json != null) {
                                                runCatching {
                                                    val loaded = watchListFromJson(json)
                                                    AppContainer.debuggerUseCase.clearWatchlist()
                                                    loaded.forEach { AppContainer.debuggerUseCase.addWatchItem(it) }
                                                    AppContainer.debuggerUseCase.log("WATCHLIST", "Loaded ${loaded.size} watch entries", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                }.onFailure {
                                                    AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to parse loaded watch list: ${it.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                                }
                                            }
                                        }
                                    } else {
                                        chooseWatchListFile(mode = FileDialog.LOAD)?.let { file ->
                                            runCatching {
                                                val loaded = watchListFromJson(file.readText(Charsets.UTF_8))
                                                AppContainer.debuggerUseCase.clearWatchlist()
                                                loaded.forEach { AppContainer.debuggerUseCase.addWatchItem(it) }
                                                AppContainer.debuggerUseCase.log("WATCHLIST", "Loaded ${loaded.size} watch entries from ${file.absolutePath}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                            }.onFailure {
                                                AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to load watch list: ${it.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Load") }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(value = addLabel, onValueChange = { addLabel = it }, label = { Text("Label") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = addAddressStr, onValueChange = { addAddressStr = it }, label = { Text("Address (Hex)") }, modifier = Modifier.weight(1.2f), singleLine = true, textStyle = TextStyle(fontFamily = FontFamily.Monospace))

                        var addTypeExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { addTypeExpanded = true }) { Text(addType) }
                            DropdownMenu(expanded = addTypeExpanded, onDismissRequest = { addTypeExpanded = false }) {
                                typeOptions.forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = { addType = t; addTypeExpanded = false })
                                }
                            }
                        }

                        OutlinedTextField(value = addComment, onValueChange = { addComment = it }, label = { Text("Comment") }, modifier = Modifier.weight(1.5f), singleLine = true)

                        Button(onClick = {
                            val addr = addAddressStr.trim().toLongOrNull(16)
                            if (addr != null && addLabel.isNotEmpty()) {
                                AppContainer.debuggerUseCase.addWatchItem(WatchItem(addLabel, addr, addType, comment = addComment))
                                addLabel = ""; addAddressStr = ""; addComment = ""
                            }
                        }) { Text("Add") }

                        OutlinedButton(onClick = {
                            val picker = AppContainer.filePicker
                            if (picker != null) {
                                picker.saveJson("watchlist.json", watchListToJson(itemsList)) { success ->
                                    if (success) {
                                        AppContainer.debuggerUseCase.log("WATCHLIST", "Saved watch list successfully", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                    } else {
                                        AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to save watch list", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                    }
                                }
                            } else {
                                chooseWatchListFile(mode = FileDialog.SAVE)?.let { file ->
                                    val target = if (file.extension.equals("json", ignoreCase = true)) file else File(file.parentFile, "${file.name}.json")
                                    runCatching {
                                        target.writeText(watchListToJson(itemsList), Charsets.UTF_8)
                                        AppContainer.debuggerUseCase.log("WATCHLIST", "Saved watch list to ${target.absolutePath}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                    }.onFailure {
                                        AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to save watch list: ${it.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                    }
                                }
                            }
                        }) { Text("Save") }

                        OutlinedButton(onClick = {
                            val picker = AppContainer.filePicker
                            if (picker != null) {
                                picker.loadJson { json ->
                                    if (json != null) {
                                        runCatching {
                                            val loaded = watchListFromJson(json)
                                            AppContainer.debuggerUseCase.clearWatchlist()
                                            loaded.forEach { AppContainer.debuggerUseCase.addWatchItem(it) }
                                            AppContainer.debuggerUseCase.log("WATCHLIST", "Loaded ${loaded.size} watch entries", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                        }.onFailure {
                                            AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to parse loaded watch list: ${it.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                        }
                                    }
                                }
                            } else {
                                chooseWatchListFile(mode = FileDialog.LOAD)?.let { file ->
                                    runCatching {
                                        val loaded = watchListFromJson(file.readText(Charsets.UTF_8))
                                        AppContainer.debuggerUseCase.clearWatchlist()
                                        loaded.forEach { AppContainer.debuggerUseCase.addWatchItem(it) }
                                        AppContainer.debuggerUseCase.log("WATCHLIST", "Loaded ${loaded.size} watch entries from ${file.absolutePath}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                    }.onFailure {
                                        AppContainer.debuggerUseCase.log("WATCHLIST", "Failed to load watch list: ${it.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                    }
                                }
                            }
                        }) { Text("Load") }
                    }
                }
            }

            if (!isMobile) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Label",   fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f).padding(start = 8.dp))
                    Text("Address", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                    Text("Type",    fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Value",   fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                    Text("Freeze",  fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    Text("Comment", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(itemsList, key = { it.address }) { item ->
                    WatchRow(
                        item = item,
                        activePid = activePid,
                        coroutineScope = coroutineScope,
                        onDelete = {
                            AppContainer.debuggerUseCase.removeWatchItem(item)
                        },
                        onJumpToAddress = onJumpToAddress,
                        onUpdateLabel = { newLabel ->
                            AppContainer.debuggerUseCase.updateWatchItem(item.copy(label = newLabel))
                        },
                        onUpdateComment = { newComment ->
                            AppContainer.debuggerUseCase.updateWatchItem(item.copy(comment = newComment))
                        },
                        onUpdateType = { newType ->
                            AppContainer.debuggerUseCase.updateWatchItem(item.copy(type = newType, valueStr = "??"))
                        },
                        isMobile = isMobile
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchRow(
    item: WatchItem,
    activePid: Int?,
    coroutineScope: CoroutineScope,
    onDelete: () -> Unit,
    onJumpToAddress: (Long) -> Unit,
    onUpdateLabel: (String) -> Unit,
    onUpdateComment: (String) -> Unit,
    onUpdateType: (String) -> Unit,
    isMobile: Boolean
) {
    val density = LocalDensity.current.density

    var isEditingLabel   by remember(item.address) { mutableStateOf(false) }
    var labelDraft       by remember(item.address) { mutableStateOf(item.label) }
    var isEditingComment by remember(item.address) { mutableStateOf(false) }
    var commentDraft     by remember(item.address) { mutableStateOf(item.comment) }
    var showTypeDropdown by remember(item.address) { mutableStateOf(false) }
    var showContextMenu  by remember(item.address) { mutableStateOf(false) }
    var contextMenuOffset by remember(item.address) { mutableStateOf(DpOffset.Zero) }
    var showEditDialog   by remember(item.address) { mutableStateOf(false) }
    var editValueText    by remember(item.address) { mutableStateOf("") }

    LaunchedEffect(item.label)   { if (!isEditingLabel)   labelDraft   = item.label }
    LaunchedEffect(item.comment) { if (!isEditingComment) commentDraft = item.comment }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.address) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val pos = event.changes.first().position
                            contextMenuOffset = DpOffset((pos.x / density).dp, (pos.y / density).dp)
                            showContextMenu = true
                        }
                    }
                }
            }
            .pointerInput(item.address) {
                detectTapGestures(
                    onLongPress = { offset ->
                        contextMenuOffset = DpOffset((offset.x / density).dp, (offset.y / density).dp)
                        showContextMenu = true
                    }
                )
            }
    ) {
        if (isMobile) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditingLabel) {
                            BasicTextField(
                                value = labelDraft,
                                onValueChange = { labelDraft = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMain),
                                modifier = Modifier
                                    .weight(1f)
                                    .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .onKeyEvent { e ->
                                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                                            Key.Enter  -> { onUpdateLabel(labelDraft); isEditingLabel = false; true }
                                            Key.Escape -> { isEditingLabel = false; true }
                                            else -> false
                                        } else false
                                    }
                            )
                        } else {
                            Text(
                                item.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clickable { labelDraft = item.label; isEditingLabel = true }
                            )
                        }
                        Text(
                            String.format("0x%X", item.address),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.1f)) {
                            Text(
                                item.type,
                                fontSize = 13.sp,
                                color = PS5ThemeColors.AccentCyan,
                                modifier = Modifier.clickable { showTypeDropdown = true }
                            )
                            DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                                typeOptions.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t, fontSize = 13.sp) },
                                        onClick = { onUpdateType(t); showTypeDropdown = false }
                                    )
                                }
                            }
                        }

                        Text(
                            text = item.valueStr,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1.5f).clickable { editValueText = item.valueStr; showEditDialog = true }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Freeze", fontSize = 11.sp, modifier = Modifier.padding(end = 2.dp))
                            Checkbox(
                                checked = item.isFrozen,
                                onCheckedChange = { _ ->
                                    AppContainer.debuggerUseCase.toggleFreezeWatchItem(item)
                                }
                            )
                        }
                    }

                    if (isEditingComment) {
                        Spacer(Modifier.height(6.dp))
                        BasicTextField(
                            value = commentDraft,
                            onValueChange = { commentDraft = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .onKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown) when (e.key) {
                                        Key.Enter  -> { onUpdateComment(commentDraft); isEditingComment = false; true }
                                        Key.Escape -> { isEditingComment = false; true }
                                        else -> false
                                    } else false
                                }
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.comment.ifEmpty { "Tap to add comment…" },
                            fontSize = 12.sp,
                            color = if (item.comment.isEmpty()) PS5ThemeColors.TextMuted else PS5ThemeColors.TextMain,
                            modifier = Modifier.fillMaxWidth().clickable { commentDraft = item.comment; isEditingComment = true }
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label — inline editable
                if (isEditingLabel) {
                    BasicTextField(
                        value = labelDraft,
                        onValueChange = { labelDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = PS5ThemeColors.TextMain),
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(start = 8.dp, end = 4.dp)
                            .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.Enter  -> { onUpdateLabel(labelDraft); isEditingLabel = false; true }
                                    Key.Escape -> { isEditingLabel = false; true }
                                    else -> false
                                } else false
                            }
                    )
                } else {
                    Text(
                        item.label,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1.5f).padding(start = 8.dp).clickable { labelDraft = item.label; isEditingLabel = true }
                    )
                }

                // Address (read-only)
                Text(String.format("0x%X", item.address), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1.5f))

                // Type — click for dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        item.type,
                        fontSize = 13.sp,
                        color = PS5ThemeColors.AccentCyan,
                        modifier = Modifier.clickable { showTypeDropdown = true }
                    )
                    DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                        typeOptions.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t, fontSize = 13.sp) },
                                onClick = { onUpdateType(t); showTypeDropdown = false }
                            )
                        }
                    }
                }

                // Value — click to write dialog
                Text(
                    text = item.valueStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.5f).clickable { editValueText = item.valueStr; showEditDialog = true }
                )

                // Freeze checkbox
                Checkbox(
                    checked = item.isFrozen,
                    onCheckedChange = { _ ->
                        AppContainer.debuggerUseCase.toggleFreezeWatchItem(item)
                    },
                    modifier = Modifier.width(60.dp)
                )

                // Comment — inline editable
                if (isEditingComment) {
                    BasicTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = PS5ThemeColors.TextMain),
                        modifier = Modifier
                            .weight(2f)
                            .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.Enter  -> { onUpdateComment(commentDraft); isEditingComment = false; true }
                                    Key.Escape -> { isEditingComment = false; true }
                                    else -> false
                                } else false
                            }
                    )
                } else {
                    Text(
                        text = item.comment.ifEmpty { "click to add…" },
                        fontSize = 13.sp,
                        color = if (item.comment.isEmpty()) PS5ThemeColors.TextMuted else PS5ThemeColors.TextMain,
                        modifier = Modifier.weight(2f).clickable { commentDraft = item.comment; isEditingComment = true }
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = contextMenuOffset
        ) {
            DropdownMenuItem(
                text = { Text("Jump to Address in Memory Viewer", fontSize = 12.sp) },
                onClick = { onJumpToAddress(item.address); showContextMenu = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", fontSize = 12.sp, color = PS5ThemeColors.StatusRed) },
                onClick = { onDelete(); showContextMenu = false }
            )
        }
    }

    // Write value dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Value — ${item.label}") },
            text = {
                OutlinedTextField(
                    value = editValueText,
                    onValueChange = { editValueText = it },
                    label = { Text("New Value (${item.type})") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        val bytes = valueToBytes(editValueText, item)
                        if (bytes != null) {
                            val result = AppContainer.debuggerUseCase.writeMemory(item.address, bytes)
                            if (result.isSuccess && result.getOrDefault(false)) {
                                if (item.isFrozen) {
                                    AppContainer.debuggerUseCase.toggleFreezeWatchItem(item)
                                    AppContainer.debuggerUseCase.toggleFreezeWatchItem(item)
                                }
                            }
                        }
                        showEditDialog = false
                    }
                }) { Text("Write") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun typeSizeBytes(item: WatchItem): Int = item.byteLength?.coerceAtLeast(1) ?: when (item.type) {
    "Byte" -> 1
    "Int16" -> 2
    "Int32" -> 4
    "Int64" -> 8
    "Float" -> 4
    "Double" -> 8
    "String" -> WatchStringMaxBytes
    else -> 4
}

internal fun parseValueBytes(bytes: ByteArray, type: String): String {
    if (bytes.isEmpty()) return "??"
    val buf = com.osr.ps5debugger.protocol.BinaryBuffer(bytes)
    return when {
        type.contains("Byte (UInt8)") || type == "Byte" -> (buf.readByte().toInt() and 0xFF).toString()
        type.contains("SByte (Int8)") -> buf.readByte().toString()
        type.contains("UInt16") -> (buf.readShort().toInt() and 0xFFFF).toString()
        type.contains("Int16") -> buf.readShort().toString()
        type.contains("UInt32") -> (buf.readInt().toLong() and 0xFFFFFFFFL).toString()
        type.contains("Int32") -> buf.readInt().toString()
        type.contains("UInt64") -> buf.readLong().toULong().toString()
        type.contains("Int64") -> buf.readLong().toString()
        type.contains("Float") -> buf.readFloat().toString()
        type.contains("Double") -> buf.readDouble().toString()
        type == "String" || type.contains("ASCII") -> bytes
            .takeWhile { it != 0.toByte() }
            .map { byte ->
                val value = byte.toInt() and 0xFF
                if (value in 32..126) value.toChar() else '.'
            }
            .joinToString("")
        type.contains("Hex Mask") -> bytes.joinToString(" ") { String.format("%02X", it) }
        else -> "??"
    }
}

private fun valueToBytes(valueStr: String, item: WatchItem): ByteArray? {
    val buf = com.osr.ps5debugger.protocol.BinaryBuffer(8)
    return try {
        when (item.type) {
            "Byte"   -> buf.writeByte(valueStr.toByte())
            "Int16"  -> buf.writeShort(valueStr.toShort())
            "Int32"  -> buf.writeInt(valueStr.toInt())
            "Int64"  -> buf.writeLong(valueStr.toLong())
            "Float"  -> buf.writeFloat(valueStr.toFloat())
            "Double" -> buf.writeDouble(valueStr.toDouble())
            "String" -> return valueStr
                .take(item.byteLength ?: WatchStringMaxBytes)
                .map { if (it.code in 0..127) it.code.toByte() else '.'.code.toByte() }
                .toByteArray()
            else -> return null
        }
        val out = ByteArray(buf.position)
        System.arraycopy(buf.bytes, 0, out, 0, buf.position)
        out
    } catch (_: Exception) { null }
}

private fun chooseWatchListFile(mode: Int): File? {
    return try {
        val dialog = FileDialog(null as Frame?, if (mode == FileDialog.SAVE) "Save Watch List" else "Load Watch List", mode)
        dialog.file = "watchlist.json"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        File(directory, file)
    } catch (e: Throwable) {
        File("watchlist.json").absoluteFile
    }
}

fun watchListToJson(items: List<WatchItem>): String = buildString {
    appendLine("[")
    items.forEachIndexed { index, item ->
        append("  {")
        append("\"label\":\"").append(jsonEscape(item.label)).append("\",")
        append("\"address\":").append(item.address).append(",")
        append("\"addressHex\":\"0x").append(item.address.toString(16).uppercase()).append("\",")
        append("\"type\":\"").append(jsonEscape(item.type)).append("\",")
        append("\"value\":\"").append(jsonEscape(item.valueStr)).append("\",")
        append("\"isFrozen\":").append(item.isFrozen).append(",")
        append("\"comment\":\"").append(jsonEscape(item.comment)).append("\",")
        append("\"byteLength\":").append(item.byteLength?.toString() ?: "null")
        append("}")
        if (index != items.lastIndex) append(",")
        appendLine()
    }
    appendLine("]")
}

fun watchListFromJson(json: String): List<WatchItem> {
    return extractJsonObjects(json).mapNotNull { obj ->
        val label = readJsonStringField(obj, "label") ?: return@mapNotNull null
        val address = readJsonRawField(obj, "address")?.toLongOrNull()
            ?: readJsonStringField(obj, "addressHex")?.removePrefix("0x")?.removePrefix("0X")?.toLongOrNull(16)
            ?: return@mapNotNull null
        val type = readJsonStringField(obj, "type") ?: "Int32"
        val value = readJsonStringField(obj, "value") ?: "??"
        val frozen = readJsonRawField(obj, "isFrozen")?.equals("true", ignoreCase = true) == true
        val comment = readJsonStringField(obj, "comment") ?: ""
        val byteLength = readJsonRawField(obj, "byteLength")?.takeUnless { it == "null" }?.toIntOrNull()

        WatchItem(
            label = label,
            address = address,
            type = type,
            valueStr = value,
            isFrozen = frozen,
            comment = comment,
            byteLength = byteLength
        )
    }
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04X".format(ch.code)) else append(ch)
        }
    }
}

private fun extractJsonObjects(json: String): List<String> {
    val objects = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false

    json.forEachIndexed { index, ch ->
        if (escaped) {
            escaped = false
            return@forEachIndexed
        }
        if (inString && ch == '\\') {
            escaped = true
            return@forEachIndexed
        }
        if (ch == '"') {
            inString = !inString
            return@forEachIndexed
        }
        if (inString) return@forEachIndexed

        when (ch) {
            '{' -> {
                if (depth == 0) start = index
                depth++
            }
            '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    objects.add(json.substring(start, index + 1))
                    start = -1
                }
            }
        }
    }

    return objects
}

private fun readJsonStringField(obj: String, name: String): String? {
    val marker = "\"$name\""
    val markerIndex = obj.indexOf(marker)
    if (markerIndex < 0) return null
    val colonIndex = obj.indexOf(':', markerIndex + marker.length)
    if (colonIndex < 0) return null
    var index = colonIndex + 1
    while (index < obj.length && obj[index].isWhitespace()) index++
    if (index >= obj.length || obj[index] != '"') return null
    index++

    val out = StringBuilder()
    while (index < obj.length) {
        val ch = obj[index++]
        if (ch == '"') return out.toString()
        if (ch != '\\') {
            out.append(ch)
            continue
        }
        if (index >= obj.length) return null
        when (val escaped = obj[index++]) {
            '"' -> out.append('"')
            '\\' -> out.append('\\')
            '/' -> out.append('/')
            'b' -> out.append('\b')
            'f' -> out.append('\u000C')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            'u' -> {
                if (index + 4 > obj.length) return null
                val code = obj.substring(index, index + 4).toIntOrNull(16) ?: return null
                out.append(code.toChar())
                index += 4
            }
            else -> out.append(escaped)
        }
    }
    return null
}

private fun readJsonRawField(obj: String, name: String): String? {
    val marker = "\"$name\""
    val markerIndex = obj.indexOf(marker)
    if (markerIndex < 0) return null
    val colonIndex = obj.indexOf(':', markerIndex + marker.length)
    if (colonIndex < 0) return null
    var index = colonIndex + 1
    while (index < obj.length && obj[index].isWhitespace()) index++
    val start = index
    while (index < obj.length && obj[index] != ',' && obj[index] != '}') index++
    return obj.substring(start, index).trim()
}

