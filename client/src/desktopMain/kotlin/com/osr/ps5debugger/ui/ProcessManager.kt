package com.osr.ps5debugger.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.network.Ps5Discovery
import com.osr.ps5debugger.protocol.Ps5Process
import com.osr.ps5debugger.protocol.Ps5VmMapEntry
import com.osr.ps5debugger.service.DebuggerService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManager(
    onMapSelected: (Ps5VmMapEntry) -> Unit,
    activeMap: Ps5VmMapEntry?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val processes by DebuggerService.processes.collectAsState()
    val activeProcess by DebuggerService.activeProcess.collectAsState()
    val activeProcessInfo by DebuggerService.activeProcessInfo.collectAsState()
    
    var processSearchText by remember { mutableStateOf("") }
    
    var maps by remember { mutableStateOf<List<Ps5VmMapEntry>>(emptyList()) }
    var isLoadingMaps by remember { mutableStateOf(false) }

    // Load maps when active process changes
    LaunchedEffect(activeProcess) {
        if (activeProcess != null) {
            isLoadingMaps = true
            try {
                maps = DebuggerService.client.getMaps(activeProcess!!.pid)
                DebuggerService.vmMaps.clear()
                DebuggerService.vmMaps.addAll(maps)
            } catch (_: Exception) {
                maps = emptyList()
                DebuggerService.vmMaps.clear()
            } finally {
                isLoadingMaps = false
            }
        } else {
            maps = emptyList()
            DebuggerService.vmMaps.clear()
        }
    }

    Column(modifier = modifier.fillMaxHeight().width(320.dp).background(PS5ThemeColors.DarkBg).padding(8.dp)) {
        // Process list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Processes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                coroutineScope.launch { DebuggerService.refreshProcesses() }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh processes")
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
                            coroutineScope.launch { DebuggerService.selectProcess(proc) }
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
