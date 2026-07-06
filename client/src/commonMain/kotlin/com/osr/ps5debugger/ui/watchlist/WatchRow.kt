package com.osr.ps5debugger.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.WatchItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun WatchRow(
    item: WatchItem,
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
            MobileWatchCard(
                item = item,
                isEditingLabel = isEditingLabel,
                labelDraft = labelDraft,
                onLabelChange = { labelDraft = it },
                onLabelEditToggle = { isEditingLabel = it },
                onUpdateLabel = onUpdateLabel,
                showTypeDropdown = showTypeDropdown,
                onTypeDropdownToggle = { showTypeDropdown = it },
                onUpdateType = onUpdateType,
                isEditingComment = isEditingComment,
                commentDraft = commentDraft,
                onCommentChange = { commentDraft = it },
                onCommentEditToggle = { isEditingComment = it },
                onUpdateComment = onUpdateComment,
                onEditValue = { editValueText = item.valueStr; showEditDialog = true }
            )
        } else {
            DesktopWatchRow(
                item = item,
                isEditingLabel = isEditingLabel,
                labelDraft = labelDraft,
                onLabelChange = { labelDraft = it },
                onLabelEditToggle = { isEditingLabel = it },
                onUpdateLabel = onUpdateLabel,
                showTypeDropdown = showTypeDropdown,
                onTypeDropdownToggle = { showTypeDropdown = it },
                onUpdateType = onUpdateType,
                isEditingComment = isEditingComment,
                commentDraft = commentDraft,
                onCommentChange = { commentDraft = it },
                onCommentEditToggle = { isEditingComment = it },
                onUpdateComment = onUpdateComment,
                onEditValue = { editValueText = item.valueStr; showEditDialog = true }
            )
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

@Composable
private fun MobileWatchCard(
    item: WatchItem,
    isEditingLabel: Boolean,
    labelDraft: String,
    onLabelChange: (String) -> Unit,
    onLabelEditToggle: (Boolean) -> Unit,
    onUpdateLabel: (String) -> Unit,
    showTypeDropdown: Boolean,
    onTypeDropdownToggle: (Boolean) -> Unit,
    onUpdateType: (String) -> Unit,
    isEditingComment: Boolean,
    commentDraft: String,
    onCommentChange: (String) -> Unit,
    onCommentEditToggle: (Boolean) -> Unit,
    onUpdateComment: (String) -> Unit,
    onEditValue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (isEditingLabel) {
                    BasicTextField(
                        value = labelDraft,
                        onValueChange = onLabelChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMain),
                        modifier = Modifier.weight(1f).background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.Enter -> { onUpdateLabel(labelDraft); onLabelEditToggle(false); true }
                                    Key.Escape -> { onLabelEditToggle(false); true }
                                    else -> false
                                } else false
                            }
                    )
                } else {
                    Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onLabelEditToggle(true) })
                }
                Text(String.format("0x%X", item.address), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1.1f)) {
                    Text(item.type, fontSize = 13.sp, color = PS5ThemeColors.AccentCyan, modifier = Modifier.clickable { onTypeDropdownToggle(true) })
                    DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { onTypeDropdownToggle(false) }) {
                        typeOptions.forEach { t ->
                            DropdownMenuItem(text = { Text(t, fontSize = 13.sp) }, onClick = { onUpdateType(t); onTypeDropdownToggle(false) })
                        }
                    }
                }
                Text(text = item.valueStr, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.5f).clickable { onEditValue() })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    Text("Freeze", fontSize = 11.sp, modifier = Modifier.padding(end = 2.dp))
                    Checkbox(checked = item.isFrozen, onCheckedChange = { AppContainer.debuggerUseCase.toggleFreezeWatchItem(item) })
                }
            }

            if (isEditingComment) {
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = commentDraft,
                    onValueChange = onCommentChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                    modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) when (e.key) {
                                Key.Enter -> { onUpdateComment(commentDraft); onCommentEditToggle(false); true }
                                Key.Escape -> { onCommentEditToggle(false); true }
                                else -> false
                            } else false
                        }
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(text = item.comment.ifEmpty { "Tap to add comment…" }, fontSize = 12.sp, color = if (item.comment.isEmpty()) PS5ThemeColors.TextMuted else PS5ThemeColors.TextMain, modifier = Modifier.fillMaxWidth().clickable { onCommentEditToggle(true) })
            }
        }
    }
}

@Composable
private fun DesktopWatchRow(
    item: WatchItem,
    isEditingLabel: Boolean,
    labelDraft: String,
    onLabelChange: (String) -> Unit,
    onLabelEditToggle: (Boolean) -> Unit,
    onUpdateLabel: (String) -> Unit,
    showTypeDropdown: Boolean,
    onTypeDropdownToggle: (Boolean) -> Unit,
    onUpdateType: (String) -> Unit,
    isEditingComment: Boolean,
    commentDraft: String,
    onCommentChange: (String) -> Unit,
    onCommentEditToggle: (Boolean) -> Unit,
    onUpdateComment: (String) -> Unit,
    onEditValue: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isEditingLabel) {
            BasicTextField(
                value = labelDraft,
                onValueChange = onLabelChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = PS5ThemeColors.TextMain),
                modifier = Modifier.weight(1.5f).padding(start = 8.dp, end = 4.dp).background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                            Key.Enter -> { onUpdateLabel(labelDraft); onLabelEditToggle(false); true }
                            Key.Escape -> { onLabelEditToggle(false); true }
                            else -> false
                        } else false
                    }
            )
        } else {
            Text(item.label, fontSize = 13.sp, modifier = Modifier.weight(1.5f).padding(start = 8.dp).clickable { onLabelEditToggle(true) })
        }
        Text(String.format("0x%X", item.address), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
        Box(modifier = Modifier.weight(1f)) {
            Text(item.type, fontSize = 13.sp, color = PS5ThemeColors.AccentCyan, modifier = Modifier.clickable { onTypeDropdownToggle(true) })
            DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { onTypeDropdownToggle(false) }) {
                typeOptions.forEach { t ->
                    DropdownMenuItem(text = { Text(t, fontSize = 13.sp) }, onClick = { onUpdateType(t); onTypeDropdownToggle(false) })
                }
            }
        }
        Text(text = item.valueStr, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.5f).clickable { onEditValue() })
        Checkbox(checked = item.isFrozen, onCheckedChange = { AppContainer.debuggerUseCase.toggleFreezeWatchItem(item) }, modifier = Modifier.width(60.dp))
        if (isEditingComment) {
            BasicTextField(
                value = commentDraft,
                onValueChange = onCommentChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = PS5ThemeColors.TextMain),
                modifier = Modifier.weight(2f).background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                            Key.Enter -> { onUpdateComment(commentDraft); onCommentEditToggle(false); true }
                            Key.Escape -> { onCommentEditToggle(false); true }
                            else -> false
                        } else false
                    }
            )
        } else {
            Text(text = item.comment.ifEmpty { "click to add…" }, fontSize = 13.sp, color = if (item.comment.isEmpty()) PS5ThemeColors.TextMuted else PS5ThemeColors.TextMain, modifier = Modifier.weight(2f).clickable { onCommentEditToggle(true) })
        }
    }
}
