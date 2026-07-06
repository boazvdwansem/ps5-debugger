package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.protocol.GpRegs
import com.osr.ps5debugger.protocol.DbRegs
import com.osr.ps5debugger.di.AppContainer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryViewerLayout(
    activeMap: MemoryRange?,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    viewModeParam: Int? = null,
    onViewModeChanged: ((Int) -> Unit)? = null,
    selectionStartParam: Long? = null,
    selectionEndParam: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null
) {
    var internalViewMode by remember { mutableStateOf(0) }
    val viewMode = viewModeParam ?: internalViewMode
    val setViewMode: (Int) -> Unit = {
        if (onViewModeChanged != null) onViewModeChanged(it)
        else internalViewMode = it
    }

    var currentJumpAddress by remember(jumpToAddress) { mutableStateOf(jumpToAddress) }

    // Hoisted Selection State
    var internalSelectionStart by remember { mutableStateOf<Long?>(null) }
    var internalSelectionEnd by remember { mutableStateOf<Long?>(null) }
    
    val hoistedSelectionStart = selectionStartParam ?: internalSelectionStart
    val hoistedSelectionEnd = selectionEndParam ?: internalSelectionEnd
    
    val updateSelection: (Long?, Long?) -> Unit = { start, end ->
        if (onSelectionChanged != null) onSelectionChanged(start, end)
        else {
            internalSelectionStart = start
            internalSelectionEnd = end
        }
    }
    val instructions = remember { mutableStateListOf<DisasmLine>() }

    // Hoisted Debugger Session State
    val isAttached by AppContainer.debuggerUseCase.isAttached.collectAsState()
    val activeBreakpoints = remember { mutableStateMapOf<Int, Long>() } // index -> address
    val activeWatchpoints = remember { mutableStateMapOf<Int, Long>() } // slot -> address
    
    val threadList by AppContainer.debuggerUseCase.threadList.collectAsState()
    val selectedLwpid by AppContainer.debuggerUseCase.selectedLwpid.collectAsState()
    val selectedRegs by AppContainer.debuggerUseCase.selectedRegs.collectAsState()
    val selectedDbRegs by AppContainer.debuggerUseCase.selectedDbRegs.collectAsState()
    val selectedFsGs by AppContainer.debuggerUseCase.selectedFsGs.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 800.dp
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar for choosing view mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PS5ThemeColors.SecondaryBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "View Layout:",
                    color = PS5ThemeColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf("Disassembly", "Graph", "Hex Viewer")
                    
                    Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(options[viewMode], fontSize = 11.sp, color = PS5ThemeColors.TextMain)
                            Text("▼", fontSize = 8.sp, color = PS5ThemeColors.TextMuted)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor)
                    ) {
                        options.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(title, fontSize = 11.sp, color = PS5ThemeColors.TextMain) },
                                onClick = {
                                    setViewMode(index)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (viewMode) {
                    0 -> {
                        // Disassembly Mode (Unified Split / Both view)
                        if (isMobile) {
                            // On mobile: disassembly on top and hex below it (without duplicating address)
                            Column(modifier = Modifier.fillMaxSize()) {
                                DisassemblyViewer(
                                    activeMap = activeMap,
                                    instructions = instructions,
                                    jumpToAddress = currentJumpAddress,
                                    selectionStart = hoistedSelectionStart,
                                    selectionEnd = hoistedSelectionEnd,
                                    onSelectionChanged = { start, end ->
                                        updateSelection(start, end)
                                        if (start != null) {
                                            currentJumpAddress = start
                                        }
                                    },
                                    onJumpToHex = { addr ->
                                        currentJumpAddress = addr
                                        setViewMode(2)
                                    },
                                    isAttached = isAttached,
                                    onAttachedChanged = { AppContainer.debuggerUseCase.setAttached(it) },
                                    activeBreakpoints = activeBreakpoints,
                                    activeWatchpoints = activeWatchpoints,
                                    threadList = threadList,
                                    onThreadListChanged = { AppContainer.debuggerUseCase.setThreadList(it) },
                                    selectedLwpid = selectedLwpid,
                                    onSelectedLwpidChanged = { AppContainer.debuggerUseCase.setSelectedLwpid(it) },
                                    selectedRegs = selectedRegs,
                                    onSelectedRegsChanged = { AppContainer.debuggerUseCase.setSelectedRegs(it) },
                                    selectedDbRegs = selectedDbRegs,
                                    onSelectedDbRegsChanged = { AppContainer.debuggerUseCase.setSelectedDbRegs(it) },
                                    selectedFsGs = selectedFsGs,
                                    onSelectedFsGsChanged = { AppContainer.debuggerUseCase.setSelectedFsGs(it) },
                                    modifier = Modifier.weight(1f)
                                )
                                HorizontalDivider(color = PS5ThemeColors.BorderColor)
                                HexViewer(
                                    activeMap = activeMap,
                                    jumpToAddress = currentJumpAddress,
                                    modifier = Modifier.weight(1f),
                                    showAddress = false,
                                    selectionStartParam = hoistedSelectionStart,
                                    selectionEndParam = hoistedSelectionEnd,
                                    onSelectionChanged = { start, end ->
                                        updateSelection(start, end)
                                    }
                                )
                            }
                        } else {
                            // On desktop: single unified view containing address, Ghidra disassembly, and aligned hex/ascii cells!
                            DisassemblyViewer(
                                activeMap = activeMap,
                                instructions = instructions,
                                jumpToAddress = currentJumpAddress,
                                selectionStart = hoistedSelectionStart,
                                selectionEnd = hoistedSelectionEnd,
                                onSelectionChanged = { start, end ->
                                    updateSelection(start, end)
                                    if (start != null) {
                                        currentJumpAddress = start
                                    }
                                },
                                onJumpToHex = { addr ->
                                    currentJumpAddress = addr
                                    setViewMode(2)
                                },
                                isAttached = isAttached,
                                onAttachedChanged = { AppContainer.debuggerUseCase.setAttached(it) },
                                activeBreakpoints = activeBreakpoints,
                                activeWatchpoints = activeWatchpoints,
                                threadList = threadList,
                                onThreadListChanged = { AppContainer.debuggerUseCase.setThreadList(it) },
                                selectedLwpid = selectedLwpid,
                                onSelectedLwpidChanged = { AppContainer.debuggerUseCase.setSelectedLwpid(it) },
                                selectedRegs = selectedRegs,
                                onSelectedRegsChanged = { AppContainer.debuggerUseCase.setSelectedRegs(it) },
                                selectedDbRegs = selectedDbRegs,
                                onSelectedDbRegsChanged = { AppContainer.debuggerUseCase.setSelectedDbRegs(it) },
                                selectedFsGs = selectedFsGs,
                                onSelectedFsGsChanged = { AppContainer.debuggerUseCase.setSelectedFsGs(it) },
                                modifier = Modifier.fillMaxSize(),
                                showHexDetails = true
                            )
                        }
                    }
                    1 -> {
                        // Graph Viewer (Binary Ninja style Flow Chart CFG)
                        GraphViewer(
                            instructions = instructions,
                            modifier = Modifier.fillMaxSize(),
                            onAddressClicked = { addr ->
                                currentJumpAddress = addr
                                setViewMode(0) // jump back to disassembly
                            }
                        )
                    }
                    2 -> {
                        // Classic Hex Viewer
                        HexViewer(
                            activeMap = activeMap,
                            jumpToAddress = currentJumpAddress,
                            modifier = Modifier.fillMaxSize(),
                            selectionStartParam = hoistedSelectionStart,
                            selectionEndParam = hoistedSelectionEnd,
                            onSelectionChanged = { start, end ->
                                updateSelection(start, end)
                            }
                        )
                    }
                }
            }
        }
    }
}
