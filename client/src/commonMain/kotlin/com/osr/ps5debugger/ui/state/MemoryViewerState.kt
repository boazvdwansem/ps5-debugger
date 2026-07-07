package com.osr.ps5debugger.ui.state

import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.DisasmLine
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MemoryViewerState(
    activeMapInitial: MemoryRange?,
    activeMapsInitial: List<MemoryRange> = emptyList(),
    jumpToAddressInitial: Long?,
    viewModeParamInitial: Int?,
    onViewModeChangedInitial: ((Int) -> Unit)?,
    selectionStartParamInitial: Long?,
    selectionEndParamInitial: Long?,
    onSelectionChangedInitial: ((Long?, Long?) -> Unit)?
) {
    var activeMap by mutableStateOf(activeMapInitial)
    val activeMaps = mutableStateListOf<MemoryRange>().apply { addAll(activeMapsInitial) }
    var jumpToAddress by mutableStateOf(jumpToAddressInitial)
    var viewModeParam by mutableStateOf(viewModeParamInitial)
    var onViewModeChanged by mutableStateOf(onViewModeChangedInitial)
    var selectionStartParam by mutableStateOf(selectionStartParamInitial)
    var selectionEndParam by mutableStateOf(selectionEndParamInitial)
    var onSelectionChanged by mutableStateOf(onSelectionChangedInitial)

    var internalViewMode by mutableIntStateOf(0)
    val viewMode get() = viewModeParam ?: internalViewMode
    
    fun setViewMode(mode: Int) {
        onViewModeChanged?.invoke(mode) ?: run { internalViewMode = mode }
    }

    var currentJumpAddress by mutableStateOf(jumpToAddressInitial)
    var instructions = mutableStateListOf<DisasmLine>()
    var isLoading by mutableStateOf(false)

    var internalSelectionStart by mutableStateOf<Long?>(null)
    var internalSelectionEnd by mutableStateOf<Long?>(null)
    
    val selectionStart get() = selectionStartParam ?: internalSelectionStart
    val selectionEnd get() = selectionEndParam ?: internalSelectionEnd
    
    fun updateSelection(start: Long?, end: Long?) {
        onSelectionChanged?.invoke(start, end) ?: run {
            internalSelectionStart = start
            internalSelectionEnd = end
        }
    }
    
    val isAttached = AppContainer.debuggerUseCase.isAttached
    val threadList = AppContainer.debuggerUseCase.threadList
    val selectedLwpid = AppContainer.debuggerUseCase.selectedLwpid
    val selectedRegs = AppContainer.debuggerUseCase.selectedRegs
    val selectedDbRegs = AppContainer.debuggerUseCase.selectedDbRegs
    val selectedFsGs = AppContainer.debuggerUseCase.selectedFsGs

    suspend fun loadInitialInstructions() {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid ?: return
        val isConnected = AppContainer.debuggerUseCase.isConnected.value
        if (!isConnected) return

        val targets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        if (targets.isEmpty()) return

        isLoading = true
        
        try {
            val client = AppContainer.clientAdapter.client
            val allLines = mutableListOf<DisasmLine>()
            
            for (map in targets) {
                // If this is the map containing the current jump address, start disassemble from there
                val startAddr = if (currentJumpAddress != null && currentJumpAddress!! >= map.start && currentJumpAddress!! < map.end) {
                    currentJumpAddress!!
                } else {
                    map.start
                }
                
                val len = minOf(65536L, map.end - startAddr).toInt()
                if (len > 0) {
                    val rawInstrs = client.disassembleRegion(pid, startAddr, len, 2000)
                    val rawBytes = try {
                        client.readMemory(pid, startAddr, len)
                    } catch (_: Exception) {
                        ByteArray(0)
                    }
                    
                    val lines = rawInstrs.map { instr ->
                        val offset = (instr.addr - startAddr).toInt()
                        val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                            rawBytes.copyOfRange(offset, offset + instr.length)
                        } else {
                            ByteArray(0)
                        }
                        DisasmLine(instr, instrBytes)
                    }
                    allLines.addAll(lines)

                    // NEW: Call Target Discovery
                    // Scan for unique call targets that aren't already loaded
                    val loadedAddresses = allLines.map { it.instr.addr }.toSet()
                    val callTargets = rawInstrs
                        .filter { it.isCall && it.ripRelTarget != 0L && !loadedAddresses.contains(it.ripRelTarget) }
                        .map { it.ripRelTarget }
                        .distinct()
                        .take(5) // Limit to 5 sub-calls to prevent OOM/Performance issues

                    for (target in callTargets) {
                        try {
                            val subInstrs = client.disassembleRegion(pid, target, 4096, 200)
                            val subBytes = try { client.readMemory(pid, target, 1024) } catch(_: Exception) { ByteArray(0) }
                            
                            val subLines = subInstrs.map { instr ->
                                val offset = (instr.addr - target).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= subBytes.size) {
                                    subBytes.copyOfRange(offset, offset + instr.length)
                                } else ByteArray(0)
                                DisasmLine(instr, instrBytes)
                            }
                            allLines.addAll(subLines)
                        } catch (_: Exception) {}
                    }
                }
            }
            
            // Final deduplication before updating UI
            val finalLines = allLines.distinctBy { it.instr.addr }.sortedBy { it.instr.addr }
            instructions.clear()
            instructions.addAll(finalLines)
        } catch (e: Exception) {
            AppContainer.debuggerUseCase.log("DISASM", "Initial load failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
        } finally {
            isLoading = false
        }
    }
}

@Composable
fun rememberMemoryViewerState(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    jumpToAddress: Long?,
    viewModeParam: Int?,
    onViewModeChanged: ((Int) -> Unit)?,
    selectionStartParam: Long?,
    selectionEndParam: Long?,
    onSelectionChanged: ((Long?, Long?) -> Unit)?
): MemoryViewerState {
    val state = remember(activeMap, activeMaps.toList()) {
        MemoryViewerState(activeMap, activeMaps, jumpToAddress, viewModeParam, onViewModeChanged, selectionStartParam, selectionEndParam, onSelectionChanged)
    }
    
    SideEffect {
        state.activeMap = activeMap
        state.activeMaps.clear()
        state.activeMaps.addAll(activeMaps)
        state.jumpToAddress = jumpToAddress
        state.viewModeParam = viewModeParam
        state.onViewModeChanged = onViewModeChanged
        state.selectionStartParam = selectionStartParam
        state.selectionEndParam = selectionEndParam
        state.onSelectionChanged = onSelectionChanged
    }

    LaunchedEffect(jumpToAddress) {
        if (jumpToAddress != null) {
            state.currentJumpAddress = jumpToAddress
        }
    }

    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    LaunchedEffect(state.activeMap, state.activeMaps.size, state.currentJumpAddress, isConnected) {
        if ((state.activeMap != null || state.activeMaps.isNotEmpty()) && isConnected) {
            state.loadInitialInstructions()
        }
    }
    
    return state
}
