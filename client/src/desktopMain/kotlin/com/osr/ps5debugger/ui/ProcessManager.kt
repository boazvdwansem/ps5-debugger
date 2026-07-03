package com.osr.ps5debugger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.di.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManager(
    onMapSelected: (MemoryRange) -> Unit,
    activeMap: MemoryRange?,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val processes by AppContainer.debuggerUseCase.processes.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val activeProcessInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
    
    var processSearchText by remember { mutableStateOf("") }
    val maps by AppContainer.debuggerUseCase.vmMaps.collectAsState()
    val isLoadingMaps = false

    Column(modifier = modifier.fillMaxHeight().width(320.dp).background(PS5ThemeColors.DarkBg).padding(8.dp)) {
        // Process list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Processes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    coroutineScope.launch { AppContainer.debuggerUseCase.refreshProcesses() }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh processes")
                }
                if (onCollapse != null) {
                    val density = LocalDensity.current.density
                    IconButton(onClick = onCollapse) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val tintColor = PS5ThemeColors.AccentCyan
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(15f * density, 6f * density)
                                lineTo(9f * density, 12f * density)
                                lineTo(15f * density, 18f * density)
                            }
                            drawPath(
                                path = path,
                                color = tintColor,
                                style = Stroke(width = 2f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = processSearchText,
            onValueChange = { processSearchText = it },
            placeholder = { Text("Filter processes...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        // Processes list
        val filteredProcesses = processes.filter { it.name.contains(processSearchText, ignoreCase = true) }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(PS5ThemeColors.Surface)
                .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
        ) {
            items(filteredProcesses) { proc ->
                val isSelected = activeProcess?.pid == proc.pid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            coroutineScope.launch { AppContainer.debuggerUseCase.selectProcess(proc) }
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(proc.name, fontSize = 13.sp, color = PS5ThemeColors.TextMain)
                    Text("PID: ${proc.pid}", fontSize = 12.sp, color = PS5ThemeColors.TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // VM Map / Regions list
        if (activeProcess != null) {
            Spacer(Modifier.height(8.dp))
            Text("Memory Regions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (isLoadingMaps) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PS5ThemeColors.AccentCyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PS5ThemeColors.Surface)
                        .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                ) {
                    items(maps) { map ->
                        val isSelectedMap = activeMap?.start == map.start
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelectedMap) PS5ThemeColors.AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onMapSelected(map) }
                                .padding(8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = if (map.name.isEmpty()) "unnamed" else map.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PS5ThemeColors.TextMain
                                )
                                Text(
                                    text = map.getProtString(),
                                    fontSize = 11.sp,
                                    color = PS5ThemeColors.AccentCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                  Text(
                                      text = String.format("0x%X - 0x%X", map.start, map.end),
                                      fontSize = 10.sp,
                                      color = Color.Gray,
                                      fontFamily = FontFamily.Monospace
                                  )
                                  Text(
                                      text = String.format("%.2f MB", map.size.toDouble() / (1024 * 1024)),
                                      fontSize = 10.sp,
                                      color = Color.Gray
                                  )
                            }
                        }
                    }
                }
            }
        }
    }
}
