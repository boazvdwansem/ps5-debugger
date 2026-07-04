package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryViewerLayout(
    activeMap: MemoryRange?,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(0) } // 0 = Disassembly, 1 = Hex Viewer
    var currentJumpAddress by remember(jumpToAddress) { mutableStateOf(jumpToAddress) }

    // Hoisted Selection State
    var hoistedSelectionStart by remember { mutableStateOf<Long?>(null) }
    var hoistedSelectionEnd by remember { mutableStateOf<Long?>(null) }

    // Hoisted Debugger Session State
    var isAttached by remember { mutableStateOf(false) }
    val activeBreakpoints = remember { mutableStateMapOf<Int, Long>() } // index -> address
    val activeWatchpoints = remember { mutableStateMapOf<Int, Long>() } // slot -> address
    
    var threadList by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedLwpid by remember { mutableStateOf<Int?>(null) }
    var selectedRegs by remember { mutableStateOf<GpRegs?>(null) }
    var selectedDbRegs by remember { mutableStateOf<DbRegs?>(null) }
    var selectedFsGs by remember { mutableStateOf<Pair<Long, Long>?>(null) }

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
                
                FilterChip(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    label = { Text("Disassembly", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PS5ThemeColors.AccentCyan,
                        selectedLabelColor = Color.Black,
                        containerColor = PS5ThemeColors.Surface,
                        labelColor = PS5ThemeColors.TextMain
                    )
                )
                
                FilterChip(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    label = { Text("Hex Viewer", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PS5ThemeColors.AccentCyan,
                        selectedLabelColor = Color.Black,
                        containerColor = PS5ThemeColors.Surface,
                        labelColor = PS5ThemeColors.TextMain
                    )
                )
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
                                    jumpToAddress = currentJumpAddress,
                                    selectionStart = hoistedSelectionStart,
                                    selectionEnd = hoistedSelectionEnd,
                                    onSelectionChanged = { start, end ->
                                        hoistedSelectionStart = start
                                        hoistedSelectionEnd = end
                                        if (start != null) {
                                            currentJumpAddress = start
                                        }
                                    },
                                    onJumpToHex = { addr ->
                                        currentJumpAddress = addr
                                        viewMode = 1
                                    },
                                    isAttached = isAttached,
                                    onAttachedChanged = { isAttached = it },
                                    activeBreakpoints = activeBreakpoints,
                                    activeWatchpoints = activeWatchpoints,
                                    threadList = threadList,
                                    onThreadListChanged = { threadList = it },
                                    selectedLwpid = selectedLwpid,
                                    onSelectedLwpidChanged = { selectedLwpid = it },
                                    selectedRegs = selectedRegs,
                                    onSelectedRegsChanged = { selectedRegs = it },
                                    selectedDbRegs = selectedDbRegs,
                                    onSelectedDbRegsChanged = { selectedDbRegs = it },
                                    selectedFsGs = selectedFsGs,
                                    onSelectedFsGsChanged = { selectedFsGs = it },
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
                                        hoistedSelectionStart = start
                                        hoistedSelectionEnd = end
                                    }
                                )
                            }
                        } else {
                            // On desktop: single unified view containing address, Ghidra disassembly, and aligned hex/ascii cells!
                            DisassemblyViewer(
                                activeMap = activeMap,
                                jumpToAddress = currentJumpAddress,
                                selectionStart = hoistedSelectionStart,
                                selectionEnd = hoistedSelectionEnd,
                                onSelectionChanged = { start, end ->
                                    hoistedSelectionStart = start
                                    hoistedSelectionEnd = end
                                    if (start != null) {
                                        currentJumpAddress = start
                                    }
                                },
                                onJumpToHex = { addr ->
                                    currentJumpAddress = addr
                                    viewMode = 1
                                },
                                isAttached = isAttached,
                                onAttachedChanged = { isAttached = it },
                                activeBreakpoints = activeBreakpoints,
                                activeWatchpoints = activeWatchpoints,
                                threadList = threadList,
                                onThreadListChanged = { threadList = it },
                                selectedLwpid = selectedLwpid,
                                onSelectedLwpidChanged = { selectedLwpid = it },
                                selectedRegs = selectedRegs,
                                onSelectedRegsChanged = { selectedRegs = it },
                                selectedDbRegs = selectedDbRegs,
                                onSelectedDbRegsChanged = { selectedDbRegs = it },
                                selectedFsGs = selectedFsGs,
                                onSelectedFsGsChanged = { selectedFsGs = it },
                                modifier = Modifier.fillMaxSize(),
                                showHexDetails = true
                            )
                        }
                    }
                    1 -> {
                        // Classic Hex Viewer
                        HexViewer(
                            activeMap = activeMap,
                            jumpToAddress = currentJumpAddress,
                            modifier = Modifier.fillMaxSize(),
                            selectionStartParam = hoistedSelectionStart,
                            selectionEndParam = hoistedSelectionEnd,
                            onSelectionChanged = { start, end ->
                                hoistedSelectionStart = start
                                hoistedSelectionEnd = end
                            }
                        )
                    }
                }
            }
        }
    }
}
