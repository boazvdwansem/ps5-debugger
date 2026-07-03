package com.osr.ps5debugger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.service.MemoryDumper
import com.osr.ps5debugger.PS5ThemeColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import java.awt.Frame
import java.io.File

@Composable
fun MemoryDumperView(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    
    val maps by AppContainer.debuggerUseCase.vmMaps.collectAsState()
    val isLoadingMaps = false
    
    val selectedMaps = remember { mutableStateMapOf<Long, Boolean>() }
    
    var isDumping by remember { mutableStateOf(false) }
    var currentDumpRegionName by remember { mutableStateOf("") }
    var dumpProgress by remember { mutableStateOf(0f) }
    var dumpJob by remember { mutableStateOf<Job?>(null) }
    
    var outputDirPath by remember { mutableStateOf((System.getProperty("user.home") ?: "") + java.io.File.separator + "ps5_dumps") }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        maps.forEach { selectedMaps[it.start] = true }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                ) {
                    Text("Select All", color = PS5ThemeColors.TextMain)
                }
                
                Button(
                    onClick = {
                        selectedMaps.clear()
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
                
                val targets = maps.filter { selectedMaps[it.start] == true }
                
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

        // Regions list header
        Row(
            modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(48.dp))
            Text("Name", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
            Text("Range", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(3f))
            Text("Size", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
            Text("Flags", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }

        if (isLoadingMaps) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(maps) { map ->
                    val isSelected = selectedMaps[map.start] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { selectedMaps[map.start] = !isSelected },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { selectedMaps[map.start] = it }
                        )
                        
                        Text(
                            text = map.name.ifEmpty { "unnamed" },
                            fontSize = 13.sp,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = String.format("0x%012X - 0x%012X", map.start, map.end),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = PS5ThemeColors.TextMuted,
                            modifier = Modifier.weight(3f)
                        )
                        Text(
                            text = String.format("%.2f MB", map.size.toDouble() / (1024 * 1024)),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = map.getProtString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = PS5ThemeColors.AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
