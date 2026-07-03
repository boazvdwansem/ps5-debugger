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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    actionButton: @Composable (() -> Unit)? = null
) {
    val logs by AppContainer.debuggerUseCase.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<com.osr.ps5debugger.domain.model.LogEntry.Level?>(null) }
    var filterText by remember { mutableStateOf("") }
    
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val scrollState = rememberScrollState()



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
                    com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR -> Color(0xFFE06C75) // red
                    com.osr.ps5debugger.domain.model.LogEntry.Level.WARN -> Color(0xFFD19A66)  // orange
                    com.osr.ps5debugger.domain.model.LogEntry.Level.INFO -> Color(0xFFABB2BF)  // white/gray
                    com.osr.ps5debugger.domain.model.LogEntry.Level.DEBUG -> Color(0xFF5C6370) // gray
                    com.osr.ps5debugger.domain.model.LogEntry.Level.PROTOCOL -> Color(0xFF98C379) // green
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(4.dp)
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
            }
        }
}
