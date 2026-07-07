package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.font.FontFamily
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.state.rememberMemoryViewerState
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
    activeBreakpoints: MutableMap<Int, Long> = remember { mutableStateMapOf() },
    activeWatchpoints: MutableMap<Int, Long> = remember { mutableStateMapOf() }
) {
    val state = rememberMemoryViewerState(
        activeMap, activeMaps, jumpToAddress, viewModeParam, onViewModeChanged,
        selectionStartParam, selectionEndParam, onSelectionChanged
    )
    
    val isAttached by state.isAttached.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val client = AppContainer.clientAdapter.client

    // Logic to extract functions from current instructions
    var selectedGraphFunction by remember(state.activeMap) { mutableStateOf<Long?>(null) }
    val functions = remember(state.instructions.size) {
        val list = mutableListOf<Long>()
        if (state.instructions.isNotEmpty()) {
            list.add(state.instructions.first().instr.addr)
            for (i in 0 until state.instructions.size - 1) {
                if (state.instructions[i].instr.isRet) {
                    list.add(state.instructions[i+1].instr.addr)
                }
            }
        }
        list.distinct().sorted()
    }

    LaunchedEffect(functions) {
        if (selectedGraphFunction == null && functions.isNotEmpty()) {
            selectedGraphFunction = functions.first()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 800.dp
        Column(modifier = Modifier.fillMaxSize()) {
            ViewModeToolbar(state, functions, selectedGraphFunction) { selectedGraphFunction = it }
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (state.viewMode) {
                    0 -> DisassemblyView(
                        state = state,
                        instructions = state.instructions,
                        activeBreakpoints = activeBreakpoints,
                        activeWatchpoints = activeWatchpoints,
                        functions = functions,
                        isMobile = isMobile,
                        onJumpToGraph = { addr ->
                            val targetFunc = functions.filter { it <= addr }.maxOrNull()
                            if (targetFunc != null) {
                                selectedGraphFunction = targetFunc
                                state.setViewMode(1)
                                state.currentJumpAddress = addr
                            }
                        }
                    )
                    1 -> GraphViewer(
                        instructions = state.instructions,
                        isLoading = state.isLoading,
                        vmMaps = AppContainer.debuggerUseCase.vmMaps.collectAsState().value,
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
                        isAttached = isAttached,
                        activeBreakpoints = activeBreakpoints,
                        activeWatchpoints = activeWatchpoints,
                        onSetBreakpoint = { addr ->
                            coroutineScope.launch {
                                val activeBpIndex = activeBreakpoints.entries.firstOrNull { it.value == addr }?.key
                                if (activeBpIndex != null) {
                                    activeBreakpoints.remove(activeBpIndex)
                                    client.setBreakpoint(activeBpIndex, false, addr)
                                } else {
                                    val freeSlot = (0..29).firstOrNull { !activeBreakpoints.containsKey(it) }
                                    if (freeSlot != null) {
                                        activeBreakpoints[freeSlot] = addr
                                        client.setBreakpoint(freeSlot, true, addr)
                                    }
                                }
                            }
                        },
                        onSetWatchpoint = { addr ->
                            // Hardware watchpoint dialog or default setup
                            coroutineScope.launch {
                                val freeSlot = (0..3).firstOrNull { !activeWatchpoints.containsKey(it) }
                                if (freeSlot != null) {
                                    activeWatchpoints[freeSlot] = addr
                                    client.setWatchpoint(freeSlot, true, 1, 1, addr) // Default 1B Write
                                }
                            }
                        }
                    )
                    2 -> HexViewer(
                        activeMap = state.activeMap,
                        activeMaps = state.activeMaps,
                        jumpToAddress = state.currentJumpAddress,
                        modifier = Modifier.fillMaxSize(),
                        selectionStartParam = state.selectionStart,
                        selectionEndParam = state.selectionEnd,
                        onSelectionChanged = { start, end -> state.updateSelection(start, end) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeToolbar(
    state: com.osr.ps5debugger.ui.state.MemoryViewerState,
    functions: List<Long>,
    selectedFunction: Long?,
    onFunctionSelected: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("View Layout:", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        
        var expanded by remember { mutableStateOf(false) }
        val options = listOf("Disassembly", "Graph", "Hex Viewer")
        
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
                            "sub_${selectedFunction.toString(16).uppercase()}"
                        } else "Select"
                        
                        Text(label, fontSize = 11.sp, color = PS5ThemeColors.TextMain, fontFamily = FontFamily.Monospace)
                        Text("▼", fontSize = 8.sp, color = PS5ThemeColors.TextMuted)
                    }
                }
                
                DropdownMenu(
                    expanded = funcExpanded, 
                    onDismissRequest = { funcExpanded = false }, 
                    modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor).width(240.dp)
                ) {
                    functions.forEach { addr ->
                        val label = "sub_${addr.toString(16).uppercase()}"
                                   
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

@Composable
private fun DisassemblyView(
    state: com.osr.ps5debugger.ui.state.MemoryViewerState,
    instructions: SnapshotStateList<DisasmLine>,
    activeBreakpoints: MutableMap<Int, Long>,
    activeWatchpoints: MutableMap<Int, Long>,
    functions: List<Long>,
    isMobile: Boolean,
    onJumpToGraph: (Long) -> Unit
) {
    val isAttached by state.isAttached.collectAsState()
    val functionSet = remember(functions) { functions.toSet() }

    if (isMobile) {
        Column(modifier = Modifier.fillMaxSize()) {
            DisassemblyViewer(
                activeMap = state.activeMap,
                activeMaps = state.activeMaps,
                instructions = instructions,
                jumpToAddress = state.currentJumpAddress,
                selectionStart = state.selectionStart,
                selectionEnd = state.selectionEnd,
                onSelectionChanged = { start, end ->
                    state.updateSelection(start, end)
                    if (start != null) state.currentJumpAddress = start
                },
                onJumpToAddress = { addr -> state.currentJumpAddress = addr },
                onJumpToHex = { addr -> state.currentJumpAddress = addr; state.setViewMode(2) },
                onJumpToGraph = onJumpToGraph,
                isAttached = isAttached,
                activeBreakpoints = activeBreakpoints,
                activeWatchpoints = activeWatchpoints,
                isLoading = state.isLoading,
                functionAddresses = functionSet,
                modifier = Modifier.weight(1f)
            )
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            HexViewer(
                activeMap = state.activeMap,
                activeMaps = state.activeMaps,
                jumpToAddress = state.currentJumpAddress,
                modifier = Modifier.weight(1f),
                showAddress = false,
                selectionStartParam = state.selectionStart,
                selectionEndParam = state.selectionEnd,
                onSelectionChanged = { start, end -> state.updateSelection(start, end) }
            )
        }
    } else {
        DisassemblyViewer(
            activeMap = state.activeMap,
            activeMaps = state.activeMaps,
            instructions = instructions,
            jumpToAddress = state.currentJumpAddress,
            selectionStart = state.selectionStart,
            selectionEnd = state.selectionEnd,
            onSelectionChanged = { start, end ->
                state.updateSelection(start, end)
                if (start != null) state.currentJumpAddress = start
            },
            onJumpToAddress = { addr -> state.currentJumpAddress = addr },
            onJumpToHex = { addr -> state.currentJumpAddress = addr; state.setViewMode(2) },
            onJumpToGraph = onJumpToGraph,
            isAttached = isAttached,
            activeBreakpoints = activeBreakpoints,
            activeWatchpoints = activeWatchpoints,
            isLoading = state.isLoading,
            functionAddresses = functionSet,
            modifier = Modifier.fillMaxSize(),
            showHexDetails = true
        )
    }
}
