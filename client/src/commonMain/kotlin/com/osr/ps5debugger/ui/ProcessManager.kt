package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.osr.ps5debugger.ui.icons.PS5Icons
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
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.DumpRegionEntry
import com.osr.ps5debugger.service.MemoryDumper
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import kotlinx.coroutines.launch

private data class MemoryRangeUiKey(
    val start: Long,
    val end: Long
)

private fun MemoryRange.uiKey() = MemoryRangeUiKey(start, end)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManager(
    onMapSelected: (MemoryRange) -> Unit,
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    onMapsSelected: ((List<MemoryRange>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val processes by AppContainer.debuggerUseCase.processes.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val activeProcessInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
    
    var processSearchText by remember { mutableStateOf("") }
    var mapSearchText by remember { mutableStateOf("") }
    val maps by AppContainer.debuggerUseCase.vmMaps.collectAsState()
    val isLoadingMaps = false

    var activeTab by remember { mutableStateOf(if (activeMap != null || activeProcess != null) 1 else 0) }
    val isProcessSelected = activeProcess != null

    LaunchedEffect(activeProcess) {
        if (activeProcess == null) {
            activeTab = 0
        }
    }

    Column(modifier = modifier.fillMaxHeight().width(320.dp).background(PS5ThemeColors.DarkBg).padding(8.dp)) {
        // Sidebar header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (activeTab == 0) "Processes" else "Memory Regions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    coroutineScope.launch {
                        if (activeTab == 0) {
                            AppContainer.debuggerUseCase.refreshProcesses()
                        } else {
                            val proc = activeProcess
                            if (proc != null) {
                                AppContainer.debuggerUseCase.selectProcess(proc)
                            }
                        }
                    }
                }) {
                    Icon(PS5Icons.Refresh, contentDescription = "Refresh")
                }
                if (onCollapse != null) {
                    IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = PS5Icons.Close,
                            contentDescription = "Collapse",
                            tint = PS5ThemeColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Selection Tiles
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Processes Tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (activeTab == 0) PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else PS5ThemeColors.Surface)
                    .border(1.dp, if (activeTab == 0) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                    .clickable { activeTab = 0 },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Processes",
                    color = if (activeTab == 0) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Memory Regions Tile (disabled if no process is selected)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            !isProcessSelected -> PS5ThemeColors.Surface.copy(alpha = 0.5f)
                            activeTab == 1 -> PS5ThemeColors.AccentCyan.copy(alpha = 0.2f)
                            else -> PS5ThemeColors.Surface
                        }
                    )
                    .border(
                        1.dp,
                        if (activeTab == 1 && isProcessSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor.copy(alpha = if (isProcessSelected) 1f else 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable(enabled = isProcessSelected) {
                        if (isProcessSelected) activeTab = 1
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Regions",
                    color = when {
                        !isProcessSelected -> PS5ThemeColors.TextMuted.copy(alpha = 0.4f)
                        activeTab == 1 -> PS5ThemeColors.AccentCyan
                        else -> PS5ThemeColors.TextMain
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (activeTab == 0) {
            // Search Bar
            OutlinedTextField(
                value = processSearchText,
                onValueChange = { processSearchText = it },
                placeholder = { Text("Filter processes...") },
                leadingIcon = { Icon(PS5Icons.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
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
                                coroutineScope.launch { 
                                    AppContainer.debuggerUseCase.selectProcess(proc)
                                    // Auto switch to memory regions tab when a process is selected
                                    activeTab = 1
                                }
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(proc.name, fontSize = 13.sp, color = PS5ThemeColors.TextMain)
                        Text("PID: ${proc.pid}", fontSize = 12.sp, color = PS5ThemeColors.TextMuted, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // VM Map / Regions list
        if (activeTab == 1) {
            if (activeProcess == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Select a process first to view memory regions", fontSize = 13.sp, color = PS5ThemeColors.TextMuted)
                }
            } else {
                // Search Bar for Regions
                OutlinedTextField(
                    value = mapSearchText,
                    onValueChange = { mapSearchText = it },
                    placeholder = { Text("Search regions (name or address)...") },
                    leadingIcon = { Icon(PS5Icons.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                // Merge multiple segments into unified library and unnamed entries, matching MemoryDumperView
                val displayEntries = remember(maps) { MemoryDumper.mergeLibraryMaps(maps) }

                // Memoize filtered entries — recomputed only when the source list or search text changes
                val filteredEntries = remember(displayEntries, mapSearchText) {
                    if (mapSearchText.isEmpty()) displayEntries
                    else displayEntries.filter {
                        it.name.contains(mapSearchText, ignoreCase = true) ||
                        it.start.toString(16).contains(mapSearchText, ignoreCase = true) ||
                        it.end.toString(16).contains(mapSearchText, ignoreCase = true)
                    }
                }

                // Select All / Select None Button
                if (onMapsSelected != null) {
                    val isAnySelected = activeMaps.isNotEmpty()
                    val buttonText = if (isAnySelected) "Select None" else "Select All"
                    
                    Button(
                        onClick = {
                            if (isAnySelected) {
                                onMapsSelected(emptyList())
                            } else {
                                // Add unified modules instead of individual segments
                                onMapsSelected(filteredEntries.map { it.toMemoryRange() }.distinctBy { it.uiKey() }.sortedBy { it.start })
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PS5ThemeColors.SecondaryBg,
                            contentColor = PS5ThemeColors.AccentCyan
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Derive selected map keys from SnapshotStateList content to stay in sync on in-place mutations.
                val activeMapKeys by remember {
                    derivedStateOf { activeMaps.asSequence().map { it.uiKey() }.toHashSet() }
                }
                val activeMapKey = activeMap?.uiKey()
                val processInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
                val currentTitleId = processInfo?.titleId

                if (isLoadingMaps) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
                        items(
                            filteredEntries,
                            key = { it.id }
                        ) { entry ->
                            val entryMergedRange = entry.toMemoryRange(currentTitleId)
                            val entryKey = entryMergedRange.uiKey()
                            
                            // Check if this module is selected (either as a unified module or via its segments)
                            val isSelectedEntry = (activeMapKey != null && (entryKey == activeMapKey || entry.subRanges.any { it.uiKey() == activeMapKey })) ||
                                                  entryKey in activeMapKeys ||
                                                  entry.subRanges.any { it.uiKey() in activeMapKeys }
                                                  
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelectedEntry) PS5ThemeColors.AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { 
                                        if (onMapsSelected != null) {
                                            val newList = if (isSelectedEntry) {
                                                // Remove this module or any of its sub-ranges from the workspace
                                                activeMaps.filterNot { 
                                                    it.uiKey() == entryKey || entry.subRanges.any { sub -> sub.uiKey() == it.uiKey() }
                                                }
                                            } else {
                                                // Add as a unified module
                                                (activeMaps + entryMergedRange).distinctBy { it.uiKey() }.sortedBy { it.start }
                                            }
                                            onMapsSelected(newList)
                                        } else {
                                            onMapSelected(entryMergedRange)
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (entry.name.isEmpty()) "unnamed" else entry.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PS5ThemeColors.TextMain
                                        )
                                        if (entry.isMergedLibrary) {
                                            Text(
                                                text = "(${entry.subRanges.size} segs)",
                                                fontSize = 10.sp,
                                                color = PS5ThemeColors.TextMuted
                                            )
                                        }
                                    }
                                    Text(
                                        text = entry.getProtString(),
                                        fontSize = 11.sp,
                                        color = PS5ThemeColors.AccentCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = String.format("0x%X - 0x%X", entry.start, entry.end),
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = String.format("%.2f MB", entry.totalSize.toDouble() / (1024 * 1024)),
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
}
