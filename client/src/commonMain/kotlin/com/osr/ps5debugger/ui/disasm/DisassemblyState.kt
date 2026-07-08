package com.osr.ps5debugger.ui.disasm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.DpOffset
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.DisasmLine

class DisassemblyState(
    val activeMap: MemoryRange?,
    val activeMaps: List<MemoryRange>,
    val instructions: List<DisasmLine>,
    val selectionStart: Long?,
    val selectionEnd: Long?,
    val onSelectionChanged: ((Long?, Long?) -> Unit)?,
    val activeBreakpoints: Map<Int, Long>,
    val activeWatchpoints: Map<Int, Long>,
    val functionAddresses: Set<Long>
) {
    var goToAddressText by mutableStateOf("")
    var showContextMenu by mutableStateOf(false)
    var contextMenuAddr by mutableStateOf<Long?>(null)
    var contextMenuBytes by mutableStateOf(byteArrayOf())
    var contextMenuDisasm by mutableStateOf("")
    var contextMenuOffset by mutableStateOf(DpOffset.Zero)
    
    var showWatchpointDialog by mutableStateOf(false)
    var watchpointSlot by mutableStateOf(0)
    var watchpointType by mutableStateOf(1) // 1 = Write, 3 = Read/Write
    var watchpointSize by mutableStateOf(1) // 1, 2, 4, 8 bytes

    val focusRequester = FocusRequester()
    val listState = LazyListState()
    
    var selectionAnchor by mutableStateOf<Long?>(null)
}

@Composable
fun rememberDisassemblyState(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange>,
    instructions: List<DisasmLine>,
    selectionStart: Long?,
    selectionEnd: Long?,
    onSelectionChanged: ((Long?, Long?) -> Unit)?,
    activeBreakpoints: Map<Int, Long>,
    activeWatchpoints: Map<Int, Long>,
    functionAddresses: Set<Long>
): DisassemblyState {
    return remember(activeMap, activeMaps, instructions, selectionStart, selectionEnd, activeBreakpoints, activeWatchpoints, functionAddresses) {
        DisassemblyState(
            activeMap = activeMap,
            activeMaps = activeMaps,
            instructions = instructions,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            onSelectionChanged = onSelectionChanged,
            activeBreakpoints = activeBreakpoints,
            activeWatchpoints = activeWatchpoints,
            functionAddresses = functionAddresses
        )
    }
}
