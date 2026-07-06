package com.osr.ps5debugger.ui.state

import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange

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
    
    return state
}
