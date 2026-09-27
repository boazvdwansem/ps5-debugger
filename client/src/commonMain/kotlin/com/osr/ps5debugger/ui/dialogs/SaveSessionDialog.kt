package com.osr.ps5debugger.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.icons.PS5Icons
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SaveSessionDialog(
    state: MainState,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val activeProcessInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
    val vmMaps by AppContainer.debuggerUseCase.vmMaps.collectAsState()

    val defaultName = remember {
        val procName = (activeProcessInfo?.name?.takeIf { it.isNotBlank() && it != "Unknown" }
            ?: activeProcess?.name
            ?: "session").replace(".bin", "").replace(".elf", "")
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        "${procName}_$dateStr"
    }

    var sessionName by remember { mutableStateOf(defaultName) }
    var description by remember { mutableStateOf("") }
    var saveLocation by remember { mutableStateOf(SessionManager.getDefaultSessionsDir().absolutePath) }

    var excludeHex by remember { mutableStateOf(false) }
    var excludeDisassembly by remember { mutableStateOf(false) }

    // Calculate which regions have cached memory or disassembly
    val pageSize = 65536L
    val cachedRegions = remember(vmMaps, AppContainer.hexCache.size, AppContainer.instructionsCache.values.sumOf { it.size }, AppContainer.hexProgressCache.size) {
        vmMaps.mapNotNull { map ->
            var cachedBytes = 0L
            var p = (map.start / pageSize) * pageSize
            while (p < map.end) {
                val page = AppContainer.hexCache[p]
                if (page != null) {
                    cachedBytes += page.size
                }
                p += pageSize
            }
            if (cachedBytes == 0L && map.localData != null && map.localData.isNotEmpty()) {
                cachedBytes = map.localData.size.toLong()
            }
            var disasmCount = 0
            AppContainer.instructionsCache.forEach { (key, lines) ->
                if (key.startsWith("${map.start}_${map.end}")) {
                    disasmCount += lines.size
                }
            }
            val progressObj = AppContainer.hexProgressCache["${map.start}-${map.end}"]
            val hasHexProgress = cachedBytes > 0L || (progressObj != null && (progressObj.loadedBytes > 0L || progressObj.progress > 0f))
            val hasDisasmProgress = disasmCount > 0

            if (hasHexProgress || hasDisasmProgress) {
                Triple(map, cachedBytes, disasmCount)
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<Triple<MemoryRange, Long, Int>> { it.second > 0 }
                .thenByDescending { it.third > 0 }
                .thenBy { it.first.start }
        )
    }

    // Default: include all regions that have progress
    val includedRegionStarts = remember(cachedRegions) {
        val set = mutableStateSetOf<Long>()
        for (item in cachedRegions) {
            set.add(item.first.start)
        }
        set
    }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(640.dp)
                .heightIn(max = 700.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = PS5ThemeColors.Surface,
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Save Debugger Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PS5ThemeColors.TextMain
                        )
                        Text(
                            text = "Save memory, disassembly, and annotations for offline debugging",
                            fontSize = 12.sp,
                            color = PS5ThemeColors.TextMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving
                    ) {
                        Icon(PS5Icons.Close, contentDescription = "Close", tint = PS5ThemeColors.TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Session Name
                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = { sessionName = it },
                        label = { Text("Session Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PS5ThemeColors.AccentCyan,
                            unfocusedBorderColor = PS5ThemeColors.BorderColor
                        )
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PS5ThemeColors.AccentCyan,
                            unfocusedBorderColor = PS5ThemeColors.BorderColor
                        )
                    )

                    // Storage Location
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = saveLocation,
                            onValueChange = { saveLocation = it },
                            label = { Text("Save Location") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PS5ThemeColors.AccentCyan,
                                unfocusedBorderColor = PS5ThemeColors.BorderColor
                            )
                        )
                        Button(
                            onClick = {
                                AppContainer.filePicker?.pickDirectory { dir ->
                                    if (dir != null) {
                                        saveLocation = dir
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text("Browse...", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                        }
                    }

                    // Exclude Options (Hex / Disassembly)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { excludeHex = !excludeHex }
                        ) {
                            Checkbox(
                                checked = excludeHex,
                                onCheckedChange = { excludeHex = it },
                                colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Exclude Hex Memory", fontSize = 13.sp, color = PS5ThemeColors.TextMain)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { excludeDisassembly = !excludeDisassembly }
                        ) {
                            Checkbox(
                                checked = excludeDisassembly,
                                onCheckedChange = { excludeDisassembly = it },
                                colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Exclude Disassembly", fontSize = 13.sp, color = PS5ThemeColors.TextMain)
                        }
                    }

                    // Memory Regions Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PS5ThemeColors.DarkBg, RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, PS5ThemeColors.BorderColor), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Memory Regions (${includedRegionStarts.size}/${cachedRegions.size} selected)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PS5ThemeColors.AccentCyan
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Select All",
                                    fontSize = 11.sp,
                                    color = PS5ThemeColors.AccentCyan,
                                    modifier = Modifier.clickable {
                                        includedRegionStarts.clear()
                                        includedRegionStarts.addAll(cachedRegions.map { it.first.start })
                                    }
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = PS5ThemeColors.TextMuted
                                )
                                Text(
                                    text = "Deselect All",
                                    fontSize = 11.sp,
                                    color = PS5ThemeColors.AccentCyan,
                                    modifier = Modifier.clickable {
                                        includedRegionStarts.clear()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        if (cachedRegions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No memory regions have been processed yet.\nView or disassemble memory regions in the viewer first.",
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = PS5ThemeColors.TextMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cachedRegions, key = { it.first.start }) { (map, cachedBytes, disasmCount) ->
                                val isChecked = includedRegionStarts.contains(map.start)
                                val protStr = buildString {
                                    append(if ((map.protections and 1) != 0) "r" else "-")
                                    append(if ((map.protections and 2) != 0) "w" else "-")
                                    append(if ((map.protections and 4) != 0) "x" else "-")
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isChecked) PS5ThemeColors.SecondaryBg else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            if (isChecked) {
                                                includedRegionStarts.remove(map.start)
                                            } else {
                                                includedRegionStarts.add(map.start)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) includedRegionStarts.add(map.start) else includedRegionStarts.remove(map.start)
                                        },
                                        modifier = Modifier.size(24.dp),
                                        colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = map.name.ifEmpty { "unnamed" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PS5ThemeColors.TextMain
                                        )
                                        Text(
                                            text = "0x${map.start.toString(16).uppercase()} - 0x${map.end.toString(16).uppercase()} ($protStr)",
                                            fontSize = 10.sp,
                                            color = PS5ThemeColors.TextMuted
                                        )
                                    }

                                    // Badges
                                    if (cachedBytes > 0) {
                                        Surface(
                                            color = PS5ThemeColors.StatusGreen.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text(
                                                text = "${cachedBytes / 1024} KB",
                                                color = PS5ThemeColors.StatusGreen,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (disasmCount > 0) {
                                        Surface(
                                            color = PS5ThemeColors.AccentCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "$disasmCount instrs",
                                                color = PS5ThemeColors.AccentCyan,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = PS5ThemeColors.StatusRed,
                        fontSize = 12.sp
                    )
                }

                if (isSaving) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PS5ThemeColors.AccentCyan,
                        trackColor = PS5ThemeColors.SecondaryBg
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving
                    ) {
                        Text("Cancel", color = PS5ThemeColors.TextMuted)
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val trimmedName = sessionName.trim()
                            if (trimmedName.isEmpty()) {
                                errorMessage = "Session name cannot be empty."
                                return@Button
                            }
                            val targetDirectory = File(saveLocation.trim())
                            val selected = vmMaps.filter { it.start in includedRegionStarts }
                            if (selected.isEmpty()) {
                                errorMessage = "Please select at least one memory region to save."
                                return@Button
                            }
                            isSaving = true
                            errorMessage = null

                            coroutineScope.launch(Dispatchers.IO) {
                                val result = SessionManager.saveSession(
                                    name = trimmedName,
                                    description = description.trim(),
                                    targetDir = targetDirectory,
                                    excludeHex = excludeHex,
                                    excludeDisassembly = excludeDisassembly,
                                    selectedRegions = selected,
                                    activeProcess = activeProcess,
                                    activeProcessInfo = activeProcessInfo,
                                    allVmMaps = vmMaps,
                                    watchlist = AppContainer.debuggerUseCase.watchlist.value,
                                    symbols = AppContainer.symbolNames.toMap(),
                                    discoveredFunctions = AppContainer.discoveredFunctions.toList(),
                                    discoveredJumpTargets = AppContainer.discoveredJumpTargets.toList(),
                                    elfEntryPoint = AppContainer.elfEntryPoint
                                )

                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    result.onSuccess { savedFile ->
                                        AppContainer.debuggerUseCase.log(
                                            "SESSION",
                                            "Saved debugger session to ${savedFile.absolutePath} (${savedFile.length() / 1024} KB)",
                                            com.osr.ps5debugger.domain.model.LogEntry.Level.INFO
                                        )
                                        onDismiss()
                                    }.onFailure { ex ->
                                        errorMessage = "Failed to save session: ${ex.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && sessionName.isNotBlank() && cachedRegions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
                    ) {
                        Text(if (isSaving) "Saving..." else "Save Session", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun <T> mutableStateSetOf(): MutableSet<T> = mutableStateListOf<T>().let { list ->
    object : MutableSet<T> {
        override val size: Int get() = list.size
        override fun clear() = list.clear()
        override fun isEmpty(): Boolean = list.isEmpty()
        override fun iterator(): MutableIterator<T> = list.iterator()
        override fun add(element: T): Boolean = if (!list.contains(element)) list.add(element) else false
        override fun addAll(elements: Collection<T>): Boolean {
            var modified = false
            for (e in elements) if (add(e)) modified = true
            return modified
        }
        override fun contains(element: T): Boolean = list.contains(element)
        override fun containsAll(elements: Collection<T>): Boolean = elements.all { list.contains(it) }
        override fun remove(element: T): Boolean = list.remove(element)
        override fun removeAll(elements: Collection<T>): Boolean = list.removeAll(elements)
        override fun retainAll(elements: Collection<T>): Boolean = list.retainAll(elements)
    }
}
