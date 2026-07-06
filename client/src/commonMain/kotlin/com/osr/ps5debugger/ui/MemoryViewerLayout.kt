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
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.state.rememberMemoryViewerState

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
    val state = rememberMemoryViewerState(
        activeMap, jumpToAddress, viewModeParam, onViewModeChanged,
        selectionStartParam, selectionEndParam, onSelectionChanged
    )
    
    val instructions = remember { mutableStateListOf<DisasmLine>() }
    val activeBreakpoints = remember { mutableStateMapOf<Int, Long>() }
    val activeWatchpoints = remember { mutableStateMapOf<Int, Long>() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 800.dp
        Column(modifier = Modifier.fillMaxSize()) {
            ViewModeToolbar(state)
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (state.viewMode) {
                    0 -> DisassemblyView(state, instructions, activeBreakpoints, activeWatchpoints, isMobile)
                    1 -> GraphViewer(
                        instructions = instructions,
                        modifier = Modifier.fillMaxSize(),
                        onAddressClicked = { addr ->
                            state.currentJumpAddress = addr
                            state.setViewMode(0)
                        }
                    )
                    2 -> HexViewer(
                        activeMap = state.activeMap,
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
private fun ViewModeToolbar(state: com.osr.ps5debugger.ui.state.MemoryViewerState) {
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
    }
}

@Composable
private fun DisassemblyView(
    state: com.osr.ps5debugger.ui.state.MemoryViewerState,
    instructions: SnapshotStateList<DisasmLine>,
    activeBreakpoints: MutableMap<Int, Long>,
    activeWatchpoints: MutableMap<Int, Long>,
    isMobile: Boolean
) {
    val isAttached by state.isAttached.collectAsState()
    val threadList by state.threadList.collectAsState()
    val selectedLwpid by state.selectedLwpid.collectAsState()
    val selectedRegs by state.selectedRegs.collectAsState()
    val selectedDbRegs by state.selectedDbRegs.collectAsState()
    val selectedFsGs by state.selectedFsGs.collectAsState()

    if (isMobile) {
        Column(modifier = Modifier.fillMaxSize()) {
            DisassemblyViewer(
                activeMap = state.activeMap,
                instructions = instructions,
                jumpToAddress = state.currentJumpAddress,
                selectionStart = state.selectionStart,
                selectionEnd = state.selectionEnd,
                onSelectionChanged = { start, end ->
                    state.updateSelection(start, end)
                    if (start != null) state.currentJumpAddress = start
                },
                onJumpToHex = { addr -> state.currentJumpAddress = addr; state.setViewMode(2) },
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
                activeMap = state.activeMap,
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
            instructions = instructions,
            jumpToAddress = state.currentJumpAddress,
            selectionStart = state.selectionStart,
            selectionEnd = state.selectionEnd,
            onSelectionChanged = { start, end ->
                state.updateSelection(start, end)
                if (start != null) state.currentJumpAddress = start
            },
            onJumpToHex = { addr -> state.currentJumpAddress = addr; state.setViewMode(2) },
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
