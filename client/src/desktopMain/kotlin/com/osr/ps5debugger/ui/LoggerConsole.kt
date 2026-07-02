package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.service.DebuggerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoggerConsole(modifier: Modifier = Modifier) {
    val logs by DebuggerService.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<DebuggerService.LogEntry.Level?>(null) }
    var filterText by remember { mutableStateOf("") }
    
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    val filteredLogs = remember(logs, filterLevel, filterText) {
        logs.filter { entry ->
            (filterLevel == null || entry.level == filterLevel) &&
            (filterText.isEmpty() || entry.message.contains(filterText, ignoreCase = true) || entry.tag.contains(filterText, ignoreCase = true))
        }
    }

    // Auto scroll to bottom when new log entries arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
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
        }

        // Logs list
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).padding(4.dp)
        ) {
            items(filteredLogs) { entry ->
                val color = when (entry.level) {
                    DebuggerService.LogEntry.Level.ERROR -> Color(0xFFE06C75) // red
                    DebuggerService.LogEntry.Level.WARN -> Color(0xFFD19A66)  // orange
                    DebuggerService.LogEntry.Level.INFO -> Color(0xFFABB2BF)  // white/gray
                    DebuggerService.LogEntry.Level.DEBUG -> Color(0xFF5C6370) // gray
                    DebuggerService.LogEntry.Level.PROTOCOL -> Color(0xFF98C379) // green
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        text = "[${dateFormat.format(Date(entry.timestamp))}]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "[${entry.tag}]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = entry.message,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = color
                    )
                }
            }
        }
    }
}
