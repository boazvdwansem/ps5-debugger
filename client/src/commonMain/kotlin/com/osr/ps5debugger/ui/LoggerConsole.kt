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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Context menu representation removed for Android multiplatform compatibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerConsole(
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null,
    onAddressClick: ((Long) -> Unit)? = null
) {
    val logs by AppContainer.debuggerUseCase.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<com.osr.ps5debugger.domain.model.LogEntry.Level?>(null) }
    var filterText by remember { mutableStateOf("") }
    
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, filterLevel, filterText) {
        logs.filter { entry ->
            (filterLevel == null || entry.level == filterLevel) &&
            (filterText.isEmpty() || entry.message.contains(filterText, ignoreCase = true) || entry.tag.contains(filterText, ignoreCase = true))
        }
    }

    // Auto scroll to bottom
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }



    val isMobile = remember {
        try {
            Class.forName("java.awt.Frame")
            false
        } catch (_: Throwable) {
            true
        }
    }
    
    // Standard dynamic fill configuration
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        if (isMobile) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Console Logs", style = MaterialTheme.typography.titleSmall)
                    if (actionButton != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        actionButton()
                    }
                }
                
                BasicTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.osr.ps5debugger.domain.model.LogEntry.Level.values().forEach { level ->
                        FilterChip(
                            selected = filterLevel == level,
                            onClick = { filterLevel = if (filterLevel == level) null else level },
                            label = { Text(level.name, fontSize = 10.sp) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        } else {
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
                    com.osr.ps5debugger.domain.model.LogEntry.Level.values().forEach { level ->
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
        }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(4.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredLogs) { entry ->
                        val color = when (entry.level) {
                            com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR -> Color(0xFFE06C75)
                            com.osr.ps5debugger.domain.model.LogEntry.Level.WARN -> Color(0xFFD19A66)
                            com.osr.ps5debugger.domain.model.LogEntry.Level.INFO -> Color(0xFFABB2BF)
                            com.osr.ps5debugger.domain.model.LogEntry.Level.DEBUG -> Color(0xFF5C6370)
                            com.osr.ps5debugger.domain.model.LogEntry.Level.PROTOCOL -> Color(0xFF98C379)
                        }

                        val annotatedMessage = buildAnnotatedString {
                            val msg = entry.message
                            val regex = "0x[0-9A-Fa-f]+".toRegex()
                            var lastMatchEnd = 0
                            
                            regex.findAll(msg).forEach { match ->
                                append(msg.substring(lastMatchEnd, match.range.first))
                                pushStringAnnotation(tag = "address", annotation = match.value)
                                withStyle(SpanStyle(color = PS5ThemeColors.AccentCyan, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                                    append(match.value)
                                }
                                pop()
                                lastMatchEnd = match.range.last + 1
                            }
                            append(msg.substring(lastMatchEnd))
                        }

                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "[${dateFormat.format(Date(entry.timestamp))}] ",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "[${entry.tag}] ",
                                color = PS5ThemeColors.AccentCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(80.dp)
                            )
                            androidx.compose.foundation.text.ClickableText(
                                text = annotatedMessage,
                                style = TextStyle(color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                onClick = { offset ->
                                    annotatedMessage.getStringAnnotations(tag = "address", start = offset, end = offset)
                                        .firstOrNull()?.let { annotation ->
                                            val addr = annotation.item.removePrefix("0x").toLongOrNull(16)
                                            if (addr != null) {
                                                onAddressClick?.invoke(addr)
                                            }
                                        }
                                }
                            )
                        }
                    }
                }
            }
        }
}
