package com.osr.ps5debugger.ui.state

import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange

class MemoryViewerState(
    val activeMap: MemoryRange?,
    val jumpToAddress: Long?,
    val viewModeParam: Int?,
    val onViewModeChanged: ((Int) -> Unit)?,
    val selectionStartParam: Long?,
    val selectionEndParam: Long?,
    val onSelectionChanged: ((Long?, Long?) -> Unit)?
) {
    var internalViewMode by mutableIntStateOf(0)
    val viewMode get() = viewModeParam ?: internalViewMode
    
    fun setViewMode(mode: Int) {
        if (onViewModeChanged != null) onViewModeChanged.invoke(mode)
        else internalViewMode = mode
    }

    var currentJumpAddress by mutableStateOf(jumpToAddress)

    var internalSelectionStart by mutableStateOf<Long?>(null)
    var internalSelectionEnd by mutableStateOf<Long?>(null)
    
    val selectionStart get() = selectionStartParam ?: internalSelectionStart
    val selectionEnd get() = selectionEndParam ?: internalSelectionEnd
    
    fun updateSelection(start: Long?, end: Long?) {
        if (onSelectionChanged != null) onSelectionChanged.invoke(start, end)
        else {
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
) = remember(activeMap) {
    MemoryViewerState(activeMap, jumpToAddress, viewModeParam, onViewModeChanged, selectionStartParam, selectionEndParam, onSelectionChanged)
}
