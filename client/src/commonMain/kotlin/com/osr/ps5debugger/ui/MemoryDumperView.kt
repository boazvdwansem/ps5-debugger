package com.osr.ps5debugger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.osr.ps5debugger.ui.icons.PS5Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.DumpRegionEntry
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.service.MemoryDumper
import com.osr.ps5debugger.PS5ThemeColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MemoryDumperView(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    
    val maps by AppContainer.debuggerUseCase.vmMaps.collectAsState()
    val displayEntries = remember(maps) { MemoryDumper.mergeLibraryMaps(maps) }
    var searchText by remember { mutableStateOf("") }

    val filteredEntries = remember(displayEntries, searchText) {
        if (searchText.isBlank()) {
            displayEntries
        } else {
            val query = searchText.trim()
            displayEntries.filter { entry ->
                entry.name.contains(query, ignoreCase = true) ||
                entry.start.toString(16).contains(query, ignoreCase = true) ||
                entry.end.toString(16).contains(query, ignoreCase = true)
            }
        }
    }

    val isLoadingMaps = false
    val selectedEntries = remember { mutableStateMapOf<String, Boolean>() }
    
    LaunchedEffect(activeProcess?.pid) {
        selectedEntries.clear()
        searchText = ""
    }
    
    var isDumping by remember { mutableStateOf(false) }
    var currentDumpRegionName by remember { mutableStateOf("") }
    var dumpProgress by remember { mutableStateOf(0f) }
    var dumpJob by remember { mutableStateOf<Job?>(null) }
    
    val isMobile = remember {
        try {
            Class.forName("java.awt.Frame")
            false
        } catch (_: Throwable) {
            true
        }
    }
    
    var outputDirPath by remember { 
        mutableStateOf(
            if (isMobile) {
                if (AppContainer.defaultDumpPath.isNotEmpty()) {
                    AppContainer.defaultDumpPath + java.io.File.separator + "ps5_dumps"
                } else {
                    "ps5_dumps"
                }
            } else {
                (System.getProperty("user.home") ?: "") + java.io.File.separator + "ps5_dumps"
            }
        ) 
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Memory Region Dumper",
            style = MaterialTheme.typography.titleMedium,
            color = PS5ThemeColors.AccentCyan,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        if (activeProcess == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a process first to dump its memory maps", color = PS5ThemeColors.TextMuted)
            }
            return
        }

        // Action Toolbar
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            if (isMobile) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                filteredEntries.forEach { selectedEntries[it.id] = true }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select All", color = PS5ThemeColors.TextMain)
                        }
                        
                        Button(
                            onClick = {
                                selectedEntries.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Selection", color = PS5ThemeColors.TextMain)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = outputDirPath,
                            onValueChange = { outputDirPath = it },
                            label = { Text("Destination Folder") },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PS5ThemeColors.AccentCyan,
                                unfocusedBorderColor = PS5ThemeColors.BorderColor,
                                focusedLabelColor = PS5ThemeColors.AccentCyan,
                                unfocusedLabelColor = PS5ThemeColors.TextMuted
                            )
                        )
                        
                        OutlinedButton(
                            onClick = {
                                AppContainer.filePicker?.pickDirectory { path ->
                                    if (path != null) {
                                        outputDirPath = path
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                        ) {
                            Text("Browse", color = PS5ThemeColors.TextMain)
                        }
                    }
                    
                    val targets = displayEntries.filter { selectedEntries[it.id] == true }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val outputDir = File(outputDirPath)
                                println("Dump button clicked! Target regions count: ${targets.size}, outputDir: ${outputDir.absolutePath}")
                                if (!outputDir.exists()) {
                                    val created = outputDir.mkdirs()
                                    println("Created output directory: $created")
                                }
                                isDumping = true
                                dumpProgress = 0f
                                dumpJob = coroutineScope.launch {
                                     println("Launching dump coroutine...")
                                     MemoryDumper.dumpRegions(
                                         pid = activeProcess!!.pid,
                                         regions = targets,
                                         outputDir = outputDir,
                                         clientPort = AppContainer.clientAdapter,
                                         useCase = AppContainer.debuggerUseCase,
                                         onProgress = { regionName, progress ->
                                             currentDumpRegionName = regionName
                                             dumpProgress = progress
                                         }
                                     )
                                    isDumping = false
                                    println("Dump coroutine finished.")
                                }
                            },
                            enabled = targets.isNotEmpty() && !isDumping && outputDirPath.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isDumping) "Dumping..." else "Dump Selected (${targets.size})", color = Color.Black)
                        }
                        
                        if (isDumping) {
                            Button(
                                onClick = {
                                    dumpJob?.cancel()
                                    isDumping = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.StatusRed),
                                modifier = Modifier.weight(0.5f)
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            filteredEntries.forEach { selectedEntries[it.id] = true }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                    ) {
                        Text("Select All", color = PS5ThemeColors.TextMain)
                    }
                    
                    Button(
                        onClick = {
                            selectedEntries.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                    ) {
                        Text("Clear Selection", color = PS5ThemeColors.TextMain)
                    }
                    
                    OutlinedTextField(
                        value = outputDirPath,
                        onValueChange = { outputDirPath = it },
                        label = { Text("Destination Folder") },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PS5ThemeColors.AccentCyan,
                            unfocusedBorderColor = PS5ThemeColors.BorderColor,
                            focusedLabelColor = PS5ThemeColors.AccentCyan,
                            unfocusedLabelColor = PS5ThemeColors.TextMuted
                        )
                    )
                    
                    OutlinedButton(
                        onClick = {
                            AppContainer.filePicker?.pickDirectory { path ->
                                if (path != null) {
                                    outputDirPath = path
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                    ) {
                        Text("Browse", color = PS5ThemeColors.TextMain)
                    }
                    
                    val targets = displayEntries.filter { selectedEntries[it.id] == true }
                    
                    Button(
                        onClick = {
                            val outputDir = File(outputDirPath)
                            println("Dump button clicked! Target regions count: ${targets.size}, outputDir: ${outputDir.absolutePath}")
                            if (!outputDir.exists()) {
                                val created = outputDir.mkdirs()
                                println("Created output directory: $created")
                            }
                            isDumping = true
                            dumpProgress = 0f
                            dumpJob = coroutineScope.launch {
                                 println("Launching dump coroutine...")
                                 MemoryDumper.dumpRegions(
                                     pid = activeProcess!!.pid,
                                     regions = targets,
                                     outputDir = outputDir,
                                     clientPort = AppContainer.clientAdapter,
                                     useCase = AppContainer.debuggerUseCase,
                                     onProgress = { regionName, progress ->
                                         currentDumpRegionName = regionName
                                         dumpProgress = progress
                                     }
                                 )
                                isDumping = false
                                println("Dump coroutine finished.")
                            }
                        },
                        enabled = targets.isNotEmpty() && !isDumping && outputDirPath.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
                    ) {
                        Text(if (isDumping) "Dumping..." else "Dump Selected (${targets.size})", color = Color.Black)
                    }
                    
                    if (isDumping) {
                        Button(
                            onClick = {
                                dumpJob?.cancel()
                                isDumping = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.StatusRed)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                }
            }
        }

        if (isDumping) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.SecondaryBg),
                border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Active Dump: $currentDumpRegionName", fontWeight = FontWeight.Bold)
                        Text(String.format("%.1f %%", dumpProgress * 100))
                    }
                    LinearProgressIndicator(progress = { dumpProgress }, modifier = Modifier.fillMaxWidth(), color = PS5ThemeColors.AccentCyan)
                }
            }
        }

        // Search Bar for filtering regions by name or address
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Filter regions by name or address (e.g. libc, eboot, 0x8000)...", fontSize = 12.sp, color = PS5ThemeColors.TextMuted) },
            leadingIcon = {
                Icon(
                    imageVector = PS5Icons.Search,
                    contentDescription = "Search",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }) {
                        Icon(
                            imageVector = PS5Icons.Clear,
                            contentDescription = "Clear search",
                            tint = PS5ThemeColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PS5ThemeColors.AccentCyan,
                unfocusedBorderColor = PS5ThemeColors.BorderColor,
                focusedTextColor = PS5ThemeColors.TextMain,
                unfocusedTextColor = PS5ThemeColors.TextMain
            )
        )

        val headerFontSize = if (isMobile) 10.sp else 12.sp
        val itemFontSize = if (isMobile) 11.sp else 13.sp
        val fontMonospace = FontFamily.Monospace

        // Regions list header
        Row(
            modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(if (isMobile) 32.dp else 48.dp))
            val nameColTitle = if (searchText.isNotBlank()) "Name (${filteredEntries.size} of ${displayEntries.size})" else "Name"
            Text(nameColTitle, fontWeight = FontWeight.Bold, fontSize = headerFontSize, modifier = Modifier.weight(2f))
            Text("Range", fontWeight = FontWeight.Bold, fontSize = headerFontSize, modifier = Modifier.weight(3f))
            Text("Size", fontWeight = FontWeight.Bold, fontSize = headerFontSize, modifier = Modifier.weight(1.2f))
            Text("Flags", fontWeight = FontWeight.Bold, fontSize = headerFontSize, modifier = Modifier.weight(1f))
        }

        if (isLoadingMaps) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredEntries, key = { it.id }) { entry ->
                    val isSelected = selectedEntries[entry.id] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { selectedEntries[entry.id] = !isSelected },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { selectedEntries[entry.id] = it }
                        )
                        
                        Row(
                            modifier = Modifier.weight(2f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = entry.name,
                                fontSize = itemFontSize,
                                maxLines = 1
                            )
                            if (entry.isMergedLibrary) {
                                Text(
                                    text = "(${entry.subRanges.size} segs)",
                                    fontSize = (headerFontSize.value - 1).sp,
                                    color = PS5ThemeColors.TextMuted
                                )
                            }
                        }
                        
                        Text(
                            text = String.format("0x%012X - 0x%012X", entry.start, entry.end),
                            fontFamily = fontMonospace,
                            fontSize = headerFontSize,
                            color = PS5ThemeColors.TextMuted,
                            modifier = Modifier.weight(3f)
                        )
                        Text(
                            text = String.format("%.2f MB", entry.totalSize.toDouble() / (1024 * 1024)),
                            fontSize = headerFontSize,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = entry.getProtString(),
                            fontFamily = fontMonospace,
                            fontSize = headerFontSize,
                            color = PS5ThemeColors.AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
