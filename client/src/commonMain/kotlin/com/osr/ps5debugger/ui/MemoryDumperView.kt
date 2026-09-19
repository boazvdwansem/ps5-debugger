package com.osr.ps5debugger.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.DumpRegionEntry
import com.osr.ps5debugger.service.MemoryDumper
import com.osr.ps5debugger.ui.icons.PS5Icons
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
    val selectedEntries = remember { mutableStateMapOf<String, Boolean>() }

    var searchText by remember { mutableStateOf("") }
    var rangeStartText by remember { mutableStateOf("") }
    var rangeEndText by remember { mutableStateOf("") }
    var requireReadable by remember { mutableStateOf(false) }
    var requireWritable by remember { mutableStateOf(false) }
    var requireExecutable by remember { mutableStateOf(false) }
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
                    AppContainer.defaultDumpPath + File.separator + "ps5_dumps"
                } else {
                    "ps5_dumps"
                }
            } else {
                (System.getProperty("user.home") ?: "") + File.separator + "ps5_dumps"
            }
        )
    }

    LaunchedEffect(activeProcess?.pid) {
        selectedEntries.clear()
        searchText = ""
        rangeStartText = ""
        rangeEndText = ""
    }

    val rangeStart = remember(rangeStartText) { parseHexAddress(rangeStartText) }
    val rangeEnd = remember(rangeEndText) { parseHexAddress(rangeEndText) }
    val hasInvalidRange = remember(rangeStartText, rangeEndText, rangeStart, rangeEnd) {
        (rangeStartText.isNotBlank() && rangeStart == null) ||
            (rangeEndText.isNotBlank() && rangeEnd == null) ||
            (rangeStart != null && rangeEnd != null && rangeStart > rangeEnd)
    }

    val filteredEntries = remember(
        displayEntries,
        searchText,
        rangeStart,
        rangeEnd,
        hasInvalidRange,
        requireReadable,
        requireWritable,
        requireExecutable
    ) {
        if (hasInvalidRange) {
            emptyList()
        } else {
            displayEntries.filter { entry ->
                entry.matchesSearch(searchText) &&
                    entry.overlapsRange(rangeStart, rangeEnd) &&
                    entry.matchesProtections(requireReadable, requireWritable, requireExecutable)
            }
        }
    }

    val selectedTargets = remember(displayEntries, selectedEntries.toMap()) {
        displayEntries.filter { selectedEntries[it.id] == true }
    }
    val selectedBytes = remember(selectedTargets) { selectedTargets.sumOf { it.totalSize } }
    val filteredBytes = remember(filteredEntries) { filteredEntries.sumOf { it.totalSize } }

    fun startDump() {
        val process = activeProcess ?: return
        val outputDir = File(outputDirPath)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        isDumping = true
        dumpProgress = 0f
        dumpJob = coroutineScope.launch {
            MemoryDumper.dumpRegions(
                pid = process.pid,
                regions = selectedTargets,
                outputDir = outputDir,
                clientPort = AppContainer.clientAdapter,
                useCase = AppContainer.debuggerUseCase,
                onProgress = { regionName, progress ->
                    currentDumpRegionName = regionName
                    dumpProgress = progress
                }
            )
            isDumping = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DumperHeader(
            isConnected = isConnected,
            processName = activeProcess?.name,
            pid = activeProcess?.pid,
            regionCount = displayEntries.size,
            selectedCount = selectedTargets.size,
            selectedBytes = selectedBytes
        )

        if (activeProcess == null) {
            EmptyDumperState()
            return
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 900.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DumpDestinationPanel(
                        outputDirPath = outputDirPath,
                        onOutputDirChange = { outputDirPath = it },
                        selectedCount = selectedTargets.size,
                        isDumping = isDumping,
                        canDump = selectedTargets.isNotEmpty() && outputDirPath.isNotBlank(),
                        onDump = { startDump() },
                        onCancel = {
                            dumpJob?.cancel()
                            isDumping = false
                        }
                    )
                    FilterPanel(
                        searchText = searchText,
                        onSearchTextChange = { searchText = it },
                        rangeStartText = rangeStartText,
                        onRangeStartChange = { rangeStartText = it },
                        rangeEndText = rangeEndText,
                        onRangeEndChange = { rangeEndText = it },
                        requireReadable = requireReadable,
                        onRequireReadableChange = { requireReadable = it },
                        requireWritable = requireWritable,
                        onRequireWritableChange = { requireWritable = it },
                        requireExecutable = requireExecutable,
                        onRequireExecutableChange = { requireExecutable = it },
                        hasInvalidRange = hasInvalidRange,
                        filteredCount = filteredEntries.size,
                        totalCount = displayEntries.size,
                        filteredBytes = filteredBytes,
                        onClear = {
                            searchText = ""
                            rangeStartText = ""
                            rangeEndText = ""
                            requireReadable = false
                            requireWritable = false
                            requireExecutable = false
                        }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterPanel(
                        searchText = searchText,
                        onSearchTextChange = { searchText = it },
                        rangeStartText = rangeStartText,
                        onRangeStartChange = { rangeStartText = it },
                        rangeEndText = rangeEndText,
                        onRangeEndChange = { rangeEndText = it },
                        requireReadable = requireReadable,
                        onRequireReadableChange = { requireReadable = it },
                        requireWritable = requireWritable,
                        onRequireWritableChange = { requireWritable = it },
                        requireExecutable = requireExecutable,
                        onRequireExecutableChange = { requireExecutable = it },
                        hasInvalidRange = hasInvalidRange,
                        filteredCount = filteredEntries.size,
                        totalCount = displayEntries.size,
                        filteredBytes = filteredBytes,
                        onClear = {
                            searchText = ""
                            rangeStartText = ""
                            rangeEndText = ""
                            requireReadable = false
                            requireWritable = false
                            requireExecutable = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DumpDestinationPanel(
                        outputDirPath = outputDirPath,
                        onOutputDirChange = { outputDirPath = it },
                        selectedCount = selectedTargets.size,
                        isDumping = isDumping,
                        canDump = selectedTargets.isNotEmpty() && outputDirPath.isNotBlank(),
                        onDump = { startDump() },
                        onCancel = {
                            dumpJob?.cancel()
                            isDumping = false
                        },
                        modifier = Modifier.width(380.dp)
                    )
                }
            }
        }

        if (isDumping) {
            DumpProgressPanel(currentDumpRegionName, dumpProgress)
        }

        RegionTable(
            entries = filteredEntries,
            selectedEntries = selectedEntries,
            isMobile = isMobile,
            onSelectVisible = { filteredEntries.forEach { selectedEntries[it.id] = true } },
            onSelectReadable = {
                filteredEntries
                    .filter { (it.protections and 1) != 0 }
                    .forEach { selectedEntries[it.id] = true }
            },
            onClearSelection = { selectedEntries.clear() },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DumperHeader(
    isConnected: Boolean,
    processName: String?,
    pid: Int?,
    regionCount: Int,
    selectedCount: Int,
    selectedBytes: Long
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PS5ThemeColors.SecondaryBg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(PS5Icons.MemoryDumper, null, tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Memory Dumper", color = PS5ThemeColors.TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = processName?.let { "$it  |  PID $pid" } ?: "No process selected",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 12.sp,
                    fontFamily = if (processName == null) FontFamily.Default else FontFamily.Monospace
                )
            }
            HeaderMetric("CONSOLE", if (isConnected) "ONLINE" else "OFFLINE", if (isConnected) PS5ThemeColors.StatusGreen else PS5ThemeColors.StatusRed)
            HeaderMetric("REGIONS", regionCount.toString(), PS5ThemeColors.TextMain)
            HeaderMetric("SELECTED", "$selectedCount / ${formatBytes(selectedBytes)}", PS5ThemeColors.AccentCyan)
        }
    }
}

@Composable
private fun HeaderMetric(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FilterPanel(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    rangeStartText: String,
    onRangeStartChange: (String) -> Unit,
    rangeEndText: String,
    onRangeEndChange: (String) -> Unit,
    requireReadable: Boolean,
    onRequireReadableChange: (Boolean) -> Unit,
    requireWritable: Boolean,
    onRequireWritableChange: (Boolean) -> Unit,
    requireExecutable: Boolean,
    onRequireExecutableChange: (Boolean) -> Unit,
    hasInvalidRange: Boolean,
    filteredCount: Int,
    totalCount: Int,
    filteredBytes: Long,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelTitle("Search Scope", "$filteredCount of $totalCount | ${formatBytes(filteredBytes)}")
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                placeholder = { Text("Name, module, address, or protection...", fontSize = 12.sp) },
                leadingIcon = { Icon(PS5Icons.Search, null, modifier = Modifier.size(16.dp), tint = PS5ThemeColors.TextMuted) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { onSearchTextChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(PS5Icons.Clear, "Clear search", modifier = Modifier.size(14.dp), tint = PS5ThemeColors.TextMuted)
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                modifier = Modifier.fillMaxWidth(),
                colors = dumperTextFieldColors()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AddressField("Start", rangeStartText, onRangeStartChange, hasInvalidRange, Modifier.weight(1f))
                AddressField("End", rangeEndText, onRangeEndChange, hasInvalidRange, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProtectionChip("R", requireReadable, onRequireReadableChange)
                ProtectionChip("W", requireWritable, onRequireWritableChange)
                ProtectionChip("X", requireExecutable, onRequireExecutableChange)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Reset", color = PS5ThemeColors.TextMuted, fontSize = 12.sp)
                }
            }
            if (hasInvalidRange) {
                Text("Enter valid hex addresses with start less than or equal to end.", color = PS5ThemeColors.StatusRed, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DumpDestinationPanel(
    outputDirPath: String,
    onOutputDirChange: (String) -> Unit,
    selectedCount: Int,
    isDumping: Boolean,
    canDump: Boolean,
    onDump: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelTitle("Destination", "$selectedCount selected")
            OutlinedTextField(
                value = outputDirPath,
                onValueChange = onOutputDirChange,
                label = { Text("Output folder") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                modifier = Modifier.fillMaxWidth(),
                colors = dumperTextFieldColors()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        AppContainer.filePicker?.pickDirectory { path ->
                            if (path != null) onOutputDirChange(path)
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PS5ThemeColors.TextMain),
                    border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
                ) {
                    Icon(PS5Icons.Folder, null, modifier = Modifier.size(16.dp), tint = PS5ThemeColors.AccentCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Browse", fontSize = 12.sp)
                }
                Button(
                    onClick = onDump,
                    enabled = canDump && !isDumping,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan, contentColor = Color.Black)
                ) {
                    Icon(PS5Icons.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isDumping) "Dumping" else "Dump", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (isDumping) {
                    Button(
                        onClick = onCancel,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.StatusRed, contentColor = Color.White)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionTable(
    entries: List<DumpRegionEntry>,
    selectedEntries: MutableMap<String, Boolean>,
    isMobile: Boolean,
    onSelectVisible: () -> Unit,
    onSelectReadable: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp).background(PS5ThemeColors.SecondaryBg).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Regions", color = PS5ThemeColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ToolbarButton("Visible", PS5Icons.Check, onSelectVisible)
                ToolbarButton("Readable", PS5Icons.Add, onSelectReadable)
                ToolbarButton("Clear", PS5Icons.Clear, onClearSelection)
            }
            RegionHeader(isMobile)
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No regions match the current filters.", color = PS5ThemeColors.TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { entry ->
                        RegionRow(
                            entry = entry,
                            checked = selectedEntries[entry.id] == true,
                            isMobile = isMobile,
                            onCheckedChange = { selectedEntries[entry.id] = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionHeader(isMobile: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp).background(PS5ThemeColors.DarkBg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(34.dp))
        HeaderCell("Name", Modifier.weight(if (isMobile) 1.7f else 2.2f))
        HeaderCell("Range", Modifier.weight(2.6f))
        HeaderCell("Size", Modifier.weight(0.9f))
        HeaderCell("Prot", Modifier.weight(0.55f))
        HeaderCell("Segs", Modifier.weight(0.55f))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun RegionRow(
    entry: DumpRegionEntry,
    checked: Boolean,
    isMobile: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (checked) PS5ThemeColors.AccentCyan.copy(alpha = 0.11f) else Color.Transparent)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan),
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(if (isMobile) 1.7f else 2.2f)) {
            Text(
                text = entry.name.ifBlank { "unnamed" },
                color = PS5ThemeColors.TextMain,
                fontSize = if (isMobile) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (entry.isMergedLibrary) {
                Text("merged module", color = PS5ThemeColors.TextMuted, fontSize = 10.sp, maxLines = 1)
            }
        }
        Text(
            text = "0x%012X - 0x%012X".format(entry.start, entry.end),
            color = PS5ThemeColors.TextMuted,
            fontSize = if (isMobile) 9.sp else 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(2.6f)
        )
        Text(formatBytes(entry.totalSize), color = PS5ThemeColors.TextMain, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
        Text(entry.getProtString(), color = PS5ThemeColors.AccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.55f))
        Text(entry.subRanges.size.toString(), color = PS5ThemeColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.55f))
    }
    HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
}

@Composable
private fun DumpProgressPanel(regionName: String, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PS5ThemeColors.SecondaryBg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.AccentCyan.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Active Dump", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("%.1f%%".format(progress * 100), color = PS5ThemeColors.AccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Text(regionName.ifBlank { "Preparing..." }, color = PS5ThemeColors.TextMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = PS5ThemeColors.AccentCyan, trackColor = PS5ThemeColors.DarkBg)
        }
    }
}

@Composable
private fun EmptyDumperState() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PS5Icons.Process, null, tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(30.dp))
                Text("Select a process to inspect dumpable memory regions.", color = PS5ThemeColors.TextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = PS5ThemeColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(meta, color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ProtectionChip(label: String, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) PS5ThemeColors.AccentCyan.copy(alpha = 0.18f) else PS5ThemeColors.SecondaryBg)
            .border(1.dp, if (selected) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
            .clickable { onSelectedChange(!selected) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddressField(label: String, value: String, onValueChange: (String) -> Unit, isError: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("0x00000000", fontSize = 11.sp) },
        singleLine = true,
        isError = isError && value.isNotBlank(),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = PS5ThemeColors.TextMain),
        modifier = modifier,
        colors = dumperTextFieldColors()
    )
}

@Composable
private fun ToolbarButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PS5ThemeColors.TextMain),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(28.dp)
    ) {
        Icon(icon, null, tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 11.sp)
    }
}

@Composable
private fun dumperTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PS5ThemeColors.AccentCyan,
    unfocusedBorderColor = PS5ThemeColors.BorderColor,
    errorBorderColor = PS5ThemeColors.StatusRed,
    focusedLabelColor = PS5ThemeColors.AccentCyan,
    unfocusedLabelColor = PS5ThemeColors.TextMuted,
    focusedTextColor = PS5ThemeColors.TextMain,
    unfocusedTextColor = PS5ThemeColors.TextMain,
    focusedContainerColor = PS5ThemeColors.SecondaryBg,
    unfocusedContainerColor = PS5ThemeColors.SecondaryBg,
    cursorColor = PS5ThemeColors.AccentCyan
)

private fun DumpRegionEntry.matchesSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isBlank()) return true
    val hexQuery = query.removePrefix("0x").removePrefix("0X")
    return name.contains(query, ignoreCase = true) ||
        getProtString().contains(query, ignoreCase = true) ||
        start.toString(16).contains(hexQuery, ignoreCase = true) ||
        end.toString(16).contains(hexQuery, ignoreCase = true)
}

private fun DumpRegionEntry.overlapsRange(startFilter: Long?, endFilter: Long?): Boolean {
    val requestedStart = startFilter ?: Long.MIN_VALUE
    val requestedEnd = endFilter ?: Long.MAX_VALUE
    return start <= requestedEnd && end >= requestedStart
}

private fun DumpRegionEntry.matchesProtections(readable: Boolean, writable: Boolean, executable: Boolean): Boolean {
    if (readable && (protections and 1) == 0) return false
    if (writable && (protections and 2) == 0) return false
    if (executable && (protections and 4) == 0) return false
    return true
}

private fun parseHexAddress(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val normalized = trimmed.removePrefix("0x").removePrefix("0X").replace("_", "")
    return normalized.toULongOrNull(16)?.toLong()
}

private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.2f MB".format(bytes / mb)
        bytes >= kb -> "%.1f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
