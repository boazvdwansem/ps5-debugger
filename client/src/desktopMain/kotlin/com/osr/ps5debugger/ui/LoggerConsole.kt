package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.DpOffset
import com.osr.ps5debugger.service.DebuggerService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val EmptyContextMenuRepresentation = object : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        // Do nothing to disable the default Swing selection context menu
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerConsole(
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null
) {
    val logs by DebuggerService.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<DebuggerService.LogEntry.Level?>(null) }
    var filterText by remember { mutableStateOf("") }
    
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val scrollState = rememberScrollState()

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    val filteredLogs = remember(logs, filterLevel, filterText) {
        logs.filter { entry ->
            (filterLevel == null || entry.level == filterLevel) &&
            (filterText.isEmpty() || entry.message.contains(filterText, ignoreCase = true) || entry.tag.contains(filterText, ignoreCase = true))
        }
    }

    // Build single formatted and styled AnnotatedString for the console logs
    val logsText = remember(filteredLogs) {
        buildAnnotatedString {
            filteredLogs.forEachIndexed { index, entry ->
                val color = when (entry.level) {
                    DebuggerService.LogEntry.Level.ERROR -> Color(0xFFE06C75) // red
                    DebuggerService.LogEntry.Level.WARN -> Color(0xFFD19A66)  // orange
                    DebuggerService.LogEntry.Level.INFO -> Color(0xFFABB2BF)  // white/gray
                    DebuggerService.LogEntry.Level.DEBUG -> Color(0xFF5C6370) // gray
                    DebuggerService.LogEntry.Level.PROTOCOL -> Color(0xFF98C379) // green
                }
                
                withStyle(SpanStyle(color = Color.Gray)) {
                    append("[${dateFormat.format(Date(entry.timestamp))}] ")
                }
                withStyle(SpanStyle(color = PS5ThemeColors.AccentCyan)) {
                    append("[${entry.tag}] ")
                }
                withStyle(SpanStyle(color = color)) {
                    append(entry.message)
                }
                if (index < filteredLogs.lastIndex) {
                    append("\n")
                }
            }
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }

    // Sync textFieldValue content when logsText changes
    LaunchedEffect(logsText) {
        textFieldValue = textFieldValue.copy(annotatedString = logsText)
        // Auto scroll to bottom
        delay(50) // Brief delay to let text field layout update
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Get currently selected text range
    val selectedText = remember(textFieldValue) {
        val range = textFieldValue.selection
        if (!range.collapsed && range.min >= 0 && range.max <= textFieldValue.text.length) {
            textFieldValue.text.substring(range.min, range.max)
        } else {
            ""
        }
    }

    CompositionLocalProvider(
        LocalContextMenuRepresentation provides EmptyContextMenuRepresentation
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Console Logs", style = MaterialTheme.typography.titleSmall)
                
                BasicTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                    singleLine = true,
                    modifier = Modifier
                        .width(200.dp)
                        .height(28.dp)
                        .background(PS5ThemeColors.Surface, RoundedCornerShape(4.dp))
                        .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (filterText.isEmpty()) {
                                Text("Filter logs...", color = PS5ThemeColors.TextMuted, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    }
                )

                Row {
                    DebuggerService.LogEntry.Level.values().forEach { level ->
                        FilterChip(
                            selected = filterLevel == level,
                            onClick = { filterLevel = if (filterLevel == level) null else level },
                            label = { Text(level.name, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                if (actionButton != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    actionButton()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(4.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    contextMenuOffset = DpOffset(change.position.x.dp / density, change.position.y.dp / density)
                                    showContextMenu = true
                                }
                            }
                        }
                    }
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PS5ThemeColors.TextMain),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = contextMenuOffset
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (selectedText.isNotEmpty()) "Copy Selection" else "Copy All Logs",
                                fontSize = 12.sp
                            )
                        },
                        onClick = {
                            if (selectedText.isNotEmpty()) {
                                copyToClipboard(selectedText)
                            } else {
                                copyToClipboard(textFieldValue.text)
                            }
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select All", fontSize = 12.sp) },
                        onClick = {
                            textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
                            showContextMenu = false
                        }
                    )
                    HorizontalDivider(color = PS5ThemeColors.BorderColor)
                    DropdownMenuItem(
                        text = { Text("Clear Console Logs", fontSize = 12.sp) },
                        onClick = {
                            DebuggerService.clearLogs()
                            showContextMenu = false
                        }
                    )
                }
            }
        }
    }
}
