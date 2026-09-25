package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.components.Tooltip
import com.osr.ps5debugger.ui.hex.HexState
import com.osr.ps5debugger.ui.state.rememberMemoryViewerState
import com.osr.ps5debugger.util.DefaultIpHelper
import com.osr.ps5debugger.util.ShortcutManager
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch

@Composable
fun MemoryViewerLayout(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    viewModeParam: Int? = null,
    onViewModeChanged: ((Int) -> Unit)? = null,
    selectionStartParam: Long? = null,
    selectionEndParam: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null,
    onCopySelection: (() -> Unit)? = null,
    activeBreakpoints: MutableMap<Int, Long> = remember { mutableStateOf(mutableStateMapOf<Int, Long>()).value },
    activeWatchpoints: MutableMap<Int, Long> = remember { mutableStateOf(mutableStateMapOf<Int, Long>()).value },
    onShowXrefs: ((Long) -> Unit)? = null
) {
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()

    if (activeProcess == null) {
        Box(
            modifier = modifier.fillMaxSize().background(PS5ThemeColors.DarkBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = PS5Icons.MemoryMap,
                    contentDescription = null,
                    tint = PS5ThemeColors.TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No Process Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain
                )
                Text(
                    text = "Select a process from the Process Manager to view and inspect memory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PS5ThemeColors.TextMuted
                )
            }
        }
        return
    }

    if (activeMap == null && activeMaps.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(PS5ThemeColors.DarkBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = PS5Icons.MemoryMap,
                    contentDescription = null,
                    tint = PS5ThemeColors.TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No Memory Region Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain
                )
                Text(
                    text = "Select a memory region from the sidebar to inspect memory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PS5ThemeColors.TextMuted
                )
            }
        }
        return
    }

    val state = rememberMemoryViewerState(
        activeMap, activeMaps, jumpToAddress, viewModeParam, onViewModeChanged,
        selectionStartParam, selectionEndParam, onSelectionChanged
    )
    
    val isAttached by state.isAttached.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val client = AppContainer.clientAdapter.client

    // Logic to extract functions from current instructions
    var selectedGraphFunction by remember(state.activeMap) { mutableStateOf<Long?>(null) }
    val mapRanges = if (activeMaps.isNotEmpty()) activeMaps else listOfNotNull(activeMap)
    val functions = (
        AppContainer.discoveredFunctions.filter { address ->
            mapRanges.any { address >= it.start && address < it.end }
        } + state.functions
    ).distinct().sorted()

    // PERSISTENT VIEW STATES (Hoisted to remember block to outlive tab switch)
    // Function discovery completes with disassembly. Include the function snapshot in the
    // remembered state key so the listing's function plates update in the same composition as
    // the 100% progress state.
    val disasmState = remember(state.activeMap, state.activeMaps.size, functions) {
        com.osr.ps5debugger.ui.disasm.DisassemblyState(
            activeMap = state.activeMap,
            activeMaps = state.activeMaps,
            instructions = state.instructions,
            selectionStartInitial = state.selectionStart,
            selectionEndInitial = state.selectionEnd,
            onSelectionChanged = { start, end -> state.updateSelection(start, end) },
            activeBreakpoints = activeBreakpoints,
            activeWatchpoints = activeWatchpoints,
            functionAddresses = functions.toSet(),
            activeJumps = state.activeJumps,
            jumpTracks = state.jumpTracks,
            jumpColors = state.jumpColors,
            jumpTargets = state.jumpTargets,
            onMetadataUpdateRequested = { state.updateMetadata() }
        )
    }
    
    // Sync current navigation/selection into disasmState
    SideEffect {
        disasmState.selectionStart = state.selectionStart
        disasmState.selectionEnd = state.selectionEnd
        disasmState.goToAddressText = state.currentJumpAddress?.toString(16)?.uppercase() ?: ""
    }

    val hexState = remember(state.activeMap, state.activeMaps.size) {
        HexState(
            activeMapInitial = state.activeMap,
            activeMapsInitial = state.activeMaps,
            jumpToAddressInitial = state.currentJumpAddress,
            selectionStartParamInitial = state.selectionStart,
            selectionEndParamInitial = state.selectionEnd,
            onSelectionChangedInitial = { start, end -> state.updateSelection(start, end) },
            scope = coroutineScope
        )
    }
    
    SideEffect {
        hexState.selectionStart = state.selectionStart
        hexState.selectionEnd = state.selectionEnd
        hexState.currentJumpAddress = state.currentJumpAddress
    }

    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val shortcuts = remember { DefaultIpHelper.getShortcuts() }
    
    val performInject: suspend () -> Unit = {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        if (pid != null && hexState.pendingEdits.isNotEmpty()) {
            val writes = hexState.pendingEdits.entries.map { (addr, byte) ->
                addr to byteArrayOf(byte)
            }
            try {
                val success = client.writeMemoryMulti(pid, writes, withStatusReport = false)
                if (!success) {
                    throw IllegalStateException("PS5 rejected the memory injection")
                }
                hexState.pendingEdits.clear()
                hexState.memoryCache.clear()
                hexState.loadMemory()
                AppContainer.debuggerUseCase.log(
                    "MEMORY",
                    "Injected ${writes.size} byte(s) successfully",
                    com.osr.ps5debugger.domain.model.LogEntry.Level.INFO
                )
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log(
                    "MEMORY",
                    "Memory injection failed: ${e.message}",
                    com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR
                )
            }
        }
    }

    LaunchedEffect(state.activeMap, state.activeMaps.size, isConnected) {
        hexState.startGreedyLoader()
    }

    LaunchedEffect(functions.size) {
        if (selectedGraphFunction == null && functions.isNotEmpty()) {
            selectedGraphFunction = functions.first()
        }
    }

    LaunchedEffect(state.viewMode, state.selectionStart, state.currentJumpAddress, functions.size) {
        val targetAddr = state.currentJumpAddress ?: state.selectionStart
        if (targetAddr != null) {
            val targetFunc = functions.filter { it <= targetAddr }.maxOrNull()
            if (targetFunc != null) {
                selectedGraphFunction = targetFunc
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 800.dp
        var hexColumns by remember { mutableIntStateOf(16) }
        val onJumpToGraph: (Long) -> Unit = { addr ->
            val targetFunc = functions.filter { it <= addr }.maxOrNull()
            if (targetFunc != null) {
                selectedGraphFunction = targetFunc
                state.setViewMode(1)
                state.currentJumpAddress = addr
            }
        }
        Column(modifier = Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            
            when {
                ShortcutManager.isMatch(event, shortcuts["lock_edit"]) -> {
                    hexState.isEditingUnlocked = !hexState.isEditingUnlocked
                    true
                }
                ShortcutManager.isMatch(event, shortcuts["inject"]) -> {
                    coroutineScope.launch { performInject() }
                    true
                }
                ShortcutManager.isMatch(event, shortcuts["undo"]) -> {
                    hexState.pendingEdits.clear()
                    true
                }
                ShortcutManager.isMatch(event, shortcuts["copy"]) -> {
                    onCopySelection?.invoke()
                    true
                }
                ShortcutManager.isMatch(event, shortcuts["paste"]) -> {
                    if (state.viewMode == 2) {
                        val text = com.osr.ps5debugger.util.getFromClipboard()
                        if (text.isNotEmpty()) hexState.pasteHex(text)
                    }
                    true
                }
                else -> false
            }
        }.focusable()) {
            ViewModeToolbar(
                state = state,
                hexState = hexState,
                functions = functions,
                selectedFunction = selectedGraphFunction,
                hexColumns = hexColumns,
                onHexColumnsChanged = { hexColumns = it },
                onFunctionSelected = { selectedGraphFunction = it },
                onInject = { coroutineScope.launch { performInject() } }
            )
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (state.viewMode) {
                    0 -> DisassemblyView(
                        disasmState = disasmState,
                        parentState = state,
                        isMobile = isMobile,
                        onJumpToGraph = onJumpToGraph,
                        onShowXrefs = onShowXrefs
                    )
                    1 -> GraphViewer(
                        instructions = state.instructions,
                        isLoading = state.isLoading,
                        vmMaps = (AppContainer.debuggerUseCase.vmMaps.collectAsState().value + mapRanges).distinctBy { it.start },
                        filterFunctionAddr = selectedGraphFunction,
                        jumpToAddress = state.currentJumpAddress,
                        modifier = Modifier.fillMaxSize(),
                        selectionStart = state.selectionStart,
                        selectionEnd = state.selectionEnd,
                        onSelectionChanged = { start, end -> state.updateSelection(start, end) },
                        onAddressClicked = { addr ->
                            state.currentJumpAddress = addr
                            state.setViewMode(0)
                        },
                        onJumpToHex = { addr ->
                            state.currentJumpAddress = addr
                            state.setViewMode(2)
                        },
                        onJumpToGraph = onJumpToGraph,
                        onShowXrefs = onShowXrefs,
                        isAttached = isAttached,
                        activeBreakpoints = activeBreakpoints,
                        activeWatchpoints = activeWatchpoints,
                        onSetBreakpoint = { addr ->
                            coroutineScope.launch {
                                try {
                                    val activeBpIndex = activeBreakpoints.entries.firstOrNull { it.value == addr }?.key
                                    if (activeBpIndex != null) {
                                        if (!client.setBreakpoint(activeBpIndex, false, addr)) {
                                            throw IllegalStateException("PS5 rejected breakpoint removal at 0x${addr.toString(16)}")
                                        }
                                        activeBreakpoints.remove(activeBpIndex)
                                    } else {
                                        val freeSlot = (0..29).firstOrNull { !activeBreakpoints.containsKey(it) }
                                        if (freeSlot == null) {
                                            throw IllegalStateException("No software breakpoint slots are available")
                                        }
                                        if (!client.setBreakpoint(freeSlot, true, addr)) {
                                            throw IllegalStateException("PS5 rejected breakpoint at 0x${addr.toString(16)}")
                                        }
                                        activeBreakpoints[freeSlot] = addr
                                    }
                                } catch (e: Exception) {
                                    AppContainer.debuggerUseCase.log(
                                        "DEBUGGER",
                                        "Breakpoint operation failed: ${e.message}",
                                        com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR
                                    )
                                }
                            }
                        },
                        onSetWatchpoint = { addr ->
                            coroutineScope.launch {
                                try {
                                    val activeWpSlot = activeWatchpoints.entries.firstOrNull { it.value == addr }?.key
                                    if (activeWpSlot != null) {
                                        if (!client.setWatchpoint(activeWpSlot, false, 1, 1, addr)) {
                                            throw IllegalStateException("PS5 rejected watchpoint removal at 0x${addr.toString(16)}")
                                        }
                                        activeWatchpoints.remove(activeWpSlot)
                                    } else {
                                        val freeSlot = (0..3).firstOrNull { !activeWatchpoints.containsKey(it) }
                                        if (freeSlot == null) {
                                            throw IllegalStateException("No hardware watchpoint slots are available")
                                        }
                                        if (!client.setWatchpoint(freeSlot, true, 1, 1, addr)) {
                                            throw IllegalStateException("PS5 rejected watchpoint at 0x${addr.toString(16)}")
                                        }
                                        activeWatchpoints[freeSlot] = addr
                                    }
                                } catch (e: Exception) {
                                    AppContainer.debuggerUseCase.log(
                                        "DEBUGGER",
                                        "Watchpoint operation failed: ${e.message}",
                                        com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR
                                    )
                                }
                            }
                        }
                    )
                    2 -> HexViewer(
                        state = hexState,
                        columns = hexColumns,
                        modifier = Modifier.fillMaxSize()
                    )
                    3 -> StringsView(
                        activeMap = state.activeMap,
                        activeMaps = state.activeMaps,
                        instructions = state.instructions,
                        onJumpToAddress = { addr ->
                            state.currentJumpAddress = addr
                            state.setViewMode(0)
                        },
                        onJumpToHex = { addr ->
                            state.currentJumpAddress = addr
                            state.setViewMode(2)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            // Memory Viewer Status Bar
            StatusBar(state, hexState)
        }
    }
}

@Composable
private fun StatusBar(
    parentState: com.osr.ps5debugger.ui.state.MemoryViewerState,
    hexState: HexState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Selection info
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (parentState.selectionStart != null) {
                val start = parentState.selectionStart!!
                val end = parentState.selectionEnd ?: start
                val s = minOf(start, end)
                val e = maxOf(start, end)
                val size = e - s + 1
                
                Text(
                    text = "Selection: 0x${s.toString(16).uppercase()} - 0x${e.toString(16).uppercase()} (${size} bytes)",
                    fontSize = 11.sp,
                    color = PS5ThemeColors.TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "No selection",
                    fontSize = 11.sp,
                    color = PS5ThemeColors.TextMuted
                )
            }
        }
        
        // Right side: Progress Bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (parentState.isLoading) {
                Text(
                    "${parentState.disassemblyProgressLabel} ${(parentState.disassemblyProgress * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = PS5ThemeColors.AccentCyan,
                    fontFamily = FontFamily.Monospace
                )
                Box(modifier = Modifier.width(100.dp).height(4.dp)) {
                    LinearProgressIndicator(
                        progress = { parentState.disassemblyProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = PS5ThemeColors.AccentCyan,
                        trackColor = Color.Gray.copy(alpha = 0.3f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            if (hexState.loadingProgress > 0f && hexState.loadingProgress < 1f) {
                Text(
                    "Processing: ${(hexState.loadingProgress * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = PS5ThemeColors.AccentCyan,
                    fontFamily = FontFamily.Monospace
                )
                Box(modifier = Modifier.width(100.dp).height(4.dp)) {
                    LinearProgressIndicator(
                        progress = { hexState.loadingProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = PS5ThemeColors.AccentCyan,
                        trackColor = Color.Gray.copy(alpha = 0.3f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            } else if (hexState.loadingProgress >= 1f) {
                Text(
                    "Cache Ready",
                    fontSize = 10.sp,
                    color = PS5ThemeColors.StatusGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ViewModeToolbar(
    state: com.osr.ps5debugger.ui.state.MemoryViewerState,
    hexState: HexState,
    functions: List<Long>,
    selectedFunction: Long?,
    hexColumns: Int,
    onHexColumnsChanged: (Int) -> Unit,
    onFunctionSelected: (Long) -> Unit,
    onInject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("View Layout:", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        
        var expanded by remember { mutableStateOf(false) }
        val options = listOf("Linear", "Graph", "Hex Viewer", "Strings")
        
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(options[state.viewMode], fontSize = 11.sp, color = PS5ThemeColors.TextMain)
                    Text("▼", fontSize = 8.sp, color = PS5ThemeColors.TextMuted)
                }
            }
            
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor)) {
                options.forEachIndexed { index, title ->
                    DropdownMenuItem(
                        text = { Text(title, fontSize = 11.sp, color = PS5ThemeColors.TextMain) },
                        onClick = { state.setViewMode(index); expanded = false }
                    )
                }
            }
        }

        if (state.viewMode == 2) {
            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = PS5ThemeColors.BorderColor)
            Text("Columns:", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            var columnsExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { columnsExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("$hexColumns", fontSize = 11.sp, color = PS5ThemeColors.TextMain)
                }
                DropdownMenu(
                    expanded = columnsExpanded,
                    onDismissRequest = { columnsExpanded = false },
                    modifier = Modifier.background(PS5ThemeColors.SecondaryBg)
                ) {
                    listOf(8, 16, 32).forEach { columns ->
                        DropdownMenuItem(
                            text = { Text("$columns", fontSize = 11.sp, color = PS5ThemeColors.TextMain) },
                            onClick = {
                                onHexColumnsChanged(columns)
                                columnsExpanded = false
                            }
                        )
                    }
                }
            }
        }

        VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = PS5ThemeColors.BorderColor)
        Text("Refresh:", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        var refreshExpanded by remember { mutableStateOf(false) }
        Box {
            Button(
                onClick = { refreshExpanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val label = when (hexState.refreshRateMs) {
                    0L -> "Off"
                    500L -> "500ms"
                    1000L -> "1s"
                    2000L -> "2s"
                    5000L -> "5s"
                    else -> "${hexState.refreshRateMs}ms"
                }
                Text(label, fontSize = 11.sp, color = PS5ThemeColors.TextMain)
            }
            DropdownMenu(
                expanded = refreshExpanded,
                onDismissRequest = { refreshExpanded = false },
                modifier = Modifier.background(PS5ThemeColors.SecondaryBg)
            ) {
                listOf(0L, 500L, 1000L, 2000L, 5000L).forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(if (rate == 0L) "Off" else if (rate < 1000) "${rate}ms" else "${rate/1000}s", fontSize = 11.sp, color = PS5ThemeColors.TextMain) },
                        onClick = {
                            hexState.refreshRateMs = rate
                            refreshExpanded = false
                        }
                    )
                }
            }
        }

        if (state.viewMode == 2 || state.viewMode == 0) {
            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = PS5ThemeColors.BorderColor)
            
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(28.dp)
                    .background(PS5ThemeColors.Surface, RoundedCornerShape(4.dp))
                    .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (hexState.goToAddressText.isEmpty()) {
                    Text("Address...", color = PS5ThemeColors.TextMuted, fontSize = 11.sp)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = hexState.goToAddressText,
                    onValueChange = { hexState.goToAddressText = it },
                    textStyle = TextStyle(color = PS5ThemeColors.TextMain, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(PS5ThemeColors.AccentCyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Button(
                onClick = {
                    val addr = hexState.goToAddressText.trim().toLongOrNull(16)
                    if (addr != null) {
                        state.currentJumpAddress = addr
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Go", fontSize = 11.sp, color = PS5ThemeColors.TextMain)
            }
        }

        if (state.viewMode == 2) {
            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = PS5ThemeColors.BorderColor)
            EditActions(hexState, onInject)
        }

        if (state.viewMode == 1 && functions.isNotEmpty()) {
            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = PS5ThemeColors.BorderColor)
            Text("Function:", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            
            var funcExpanded by remember { mutableStateOf(false) }
            
            Box {
                Button(
                    onClick = { funcExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val label = if (selectedFunction != null) {
                            AppContainer.getSymbolName(selectedFunction, true)
                        } else "Select"
                        
                        Text(label, fontSize = 11.sp, color = PS5ThemeColors.TextMain, fontFamily = FontFamily.Monospace)
                        Text("▼", fontSize = 8.sp, color = PS5ThemeColors.TextMuted)
                    }
                }
                
                if (funcExpanded) {
                    Dialog(onDismissRequest = { funcExpanded = false }) {
                        Surface(
                            modifier = Modifier.width(420.dp).height(520.dp),
                            color = PS5ThemeColors.SecondaryBg,
                            shape = RoundedCornerShape(6.dp),
                            tonalElevation = 6.dp
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    "Select function (${functions.size})",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = PS5ThemeColors.TextMain,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(functions, key = { it }) { addr ->
                                        val label = remember(addr) { AppContainer.getSymbolName(addr, true) }
                                        DropdownMenuItem(
                                            text = { Text(label, fontSize = 11.sp, color = PS5ThemeColors.TextMain, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                onFunctionSelected(addr)
                                                state.currentJumpAddress = addr
                                                funcExpanded = false
                                            }
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
}

@Composable
private fun EditActions(state: HexState, onInject: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val pid = activeProcess?.pid
    val client = AppContainer.clientAdapter.client

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tooltip(if (state.isEditingUnlocked) "Lock Editing" else "Unlock Editing") {
            IconButton(onClick = { state.isEditingUnlocked = !state.isEditingUnlocked }, modifier = Modifier.size(28.dp)) {
                Icon(if (state.isEditingUnlocked) PS5Icons.LockOpen else PS5Icons.Lock, null, tint = if (state.isEditingUnlocked) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        Tooltip("Undo changes") {
            IconButton(onClick = { state.pendingEdits.clear() }, enabled = state.pendingEdits.isNotEmpty(), modifier = Modifier.size(28.dp)) {
                Icon(PS5Icons.Undo, null, modifier = Modifier.size(16.dp))
            }
        }
        Tooltip("Inject overrides to memory") {
            Button(
                onClick = { onInject() },
                enabled = state.isEditingUnlocked && state.pendingEdits.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Inject (${state.pendingEdits.size})", fontSize = 11.sp, color = PS5ThemeColors.TextMain) }
        }
    }
}

@Composable
private fun DisassemblyView(
    disasmState: com.osr.ps5debugger.ui.disasm.DisassemblyState,
    parentState: com.osr.ps5debugger.ui.state.MemoryViewerState,
    isMobile: Boolean,
    onJumpToGraph: (Long) -> Unit,
    onShowXrefs: ((Long) -> Unit)? = null
) {
    DisassemblyViewer(
        state = disasmState,
        jumpToAddress = parentState.currentJumpAddress,
        onJumpToAddress = { addr -> parentState.currentJumpAddress = addr },
        onJumpToHex = { addr -> parentState.currentJumpAddress = addr; parentState.setViewMode(2) },
        onJumpToGraph = onJumpToGraph,
        onShowXrefs = onShowXrefs,
        isAttached = parentState.isAttached.collectAsState().value,
        isLoading = parentState.isLoading,
        modifier = Modifier.fillMaxSize(),
        showHexDetails = !isMobile
    )
}
