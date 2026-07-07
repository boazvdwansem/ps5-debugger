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
    jumpToAddressInitial: Long?,
    viewModeParamInitial: Int?,
    onViewModeChangedInitial: ((Int) -> Unit)?,
    selectionStartParamInitial: Long?,
    selectionEndParamInitial: Long?,
    onSelectionChangedInitial: ((Long?, Long?) -> Unit)?
) {
    var activeMap by mutableStateOf(activeMapInitial)
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
        val map = activeMap ?: return
        val isConnected = AppContainer.debuggerUseCase.isConnected.value
        if (!isConnected) return

        val startAddr = currentJumpAddress ?: map.start
        
        // Prevent reloading if the address is already within the loaded range.
        // This avoids list-clearing jumps during simple selection.
        if (instructions.isNotEmpty()) {
            val firstAddr = instructions.first().instr.addr
            val lastLine = instructions.last()
            val lastAddr = lastLine.instr.addr + lastLine.instr.length
            if (startAddr in firstAddr until lastAddr) {
                return
            }
        }

        isLoading = true
        
        try {
            val client = AppContainer.clientAdapter.client
            val len = minOf(65536L, map.end - startAddr).toInt()
            if (len > 0) {
                val rawInstrs = client.disassembleRegion(pid, startAddr, len, 500)
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
                
                instructions.clear()
                instructions.addAll(lines)
            }
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
    jumpToAddress: Long?,
    viewModeParam: Int?,
    onViewModeChanged: ((Int) -> Unit)?,
    selectionStartParam: Long?,
    selectionEndParam: Long?,
    onSelectionChanged: ((Long?, Long?) -> Unit)?
): MemoryViewerState {
    val state = remember(activeMap) {
        MemoryViewerState(activeMap, jumpToAddress, viewModeParam, onViewModeChanged, selectionStartParam, selectionEndParam, onSelectionChanged)
    }
    
    SideEffect {
        state.activeMap = activeMap
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
    LaunchedEffect(state.activeMap, state.currentJumpAddress, isConnected) {
        if (state.activeMap != null && isConnected) {
            state.loadInitialInstructions()
        }
    }
    
    return state
}
