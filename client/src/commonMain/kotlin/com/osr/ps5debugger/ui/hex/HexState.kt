package com.osr.ps5debugger.ui.hex

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClickedArea { ADDRESS, HEX, ASCII }

private val hexViewerScrollPositions = mutableMapOf<Long, Long>()

class HexState(
    activeMapInitial: MemoryRange?,
    activeMapsInitial: List<MemoryRange> = emptyList(),
    val jumpToAddressInitial: Long?,
    val selectionStartParamInitial: Long?,
    val selectionEndParamInitial: Long?,
    onSelectionChangedInitial: ((Long?, Long?) -> Unit)?,
    val scope: CoroutineScope
) {
    val pageSize = 65536
    val memoryCache = mutableStateMapOf<Long, ByteArray>()
    val pendingEdits = mutableStateMapOf<Long, Byte>()
    
    var activeMap by mutableStateOf(activeMapInitial)
    val activeMaps = mutableStateListOf<MemoryRange>().apply { addAll(activeMapsInitial) }
    var onSelectionChanged by mutableStateOf(onSelectionChangedInitial)
    
    var startAddress by mutableStateOf(activeMapInitial?.start ?: 0L)
    var endAddress by mutableStateOf(activeMapInitial?.end ?: 0L)
    var bytesPerRow by mutableIntStateOf(16)
    var visibleRowsCount by mutableIntStateOf(1)
    
    var scrollPosition by mutableStateOf(activeMapInitial?.let { hexViewerScrollPositions[it.start] } ?: 0L)
    var selectionStart by mutableStateOf<Long?>(selectionStartParamInitial)
    var selectionEnd by mutableStateOf<Long?>(selectionEndParamInitial)
    
    var isEditingUnlocked by mutableStateOf(false)
    var hexInputBuffer by mutableStateOf("")
    var goToAddressText by mutableStateOf("")
    
    var clickedArea by mutableStateOf<ClickedArea?>(null)
    var contextMenuAddr by mutableStateOf<Long?>(null)
    var showContextMenu by mutableStateOf(false)
    var contextMenuOffset by mutableStateOf(DpOffset.Zero)
    
    val focusRequester = FocusRequester()
    val keyboardFocusRequester = FocusRequester()
    var keyboardInputText by mutableStateOf("")

    var isMouseDown by mutableStateOf(false)
    var touchStartPos by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var touchStartScroll by mutableStateOf(0L)
    var isDraggingToScroll by mutableStateOf(false)
    var isDraggingToSelect by mutableStateOf(false)
    var isLongPressSelection by mutableStateOf(false)
    var isSecondaryClick by mutableStateOf(false)
    var lastTapTime by mutableStateOf(0L)
    var isLongPressActive by mutableStateOf(false)
    var hasTriggeredLongPress by mutableStateOf(false)

    fun updateScrollPosition(newPos: Long) {
        scrollPosition = newPos
        // Store scroll position based on the first map's start to maintain context
        val sorted = if (activeMaps.isNotEmpty()) activeMaps.sortedBy { it.start } else listOfNotNull(activeMap)
        sorted.firstOrNull()?.let { hexViewerScrollPositions[it.start] = newPos }
    }

    fun getAddressForRow(row: Long): Long {
        var remainingRows = row
        val sorted = if (activeMaps.isNotEmpty()) activeMaps.sortedBy { it.start } else listOfNotNull(activeMap)
        for (map in sorted) {
            val rowsInMap = (map.end - map.start + bytesPerRow - 1) / bytesPerRow
            if (remainingRows < rowsInMap) {
                return map.start + remainingRows * bytesPerRow
            }
            remainingRows -= rowsInMap
        }
        return sorted.lastOrNull()?.end ?: 0L
    }

    fun getRowForAddress(address: Long): Long {
        var rowCount = 0L
        val sorted = if (activeMaps.isNotEmpty()) activeMaps.sortedBy { it.start } else listOfNotNull(activeMap)
        for (map in sorted) {
            if (address >= map.start && address < map.end) {
                return rowCount + (address - map.start) / bytesPerRow
            }
            rowCount += (map.end - map.start + bytesPerRow - 1) / bytesPerRow
        }
        return rowCount
    }

    fun getMaxScrollPosition(): Long {
        val sorted = if (activeMaps.isNotEmpty()) activeMaps.sortedBy { it.start } else listOfNotNull(activeMap)
        val rowCount = sorted.sumOf { (it.end - it.start + bytesPerRow - 1) / bytesPerRow }
        return maxOf(0L, rowCount - visibleRowsCount)
    }

    fun handleKeyEvent(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown || selectionEnd == null) return false
        
        val cursor = selectionEnd!!
        val shiftPressed = keyEvent.isShiftPressed
        
        val step = when (keyEvent.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            Key.DirectionUp -> -bytesPerRow
            Key.DirectionDown -> bytesPerRow
            else -> 0
        }
        
        if (step != 0) {
            // Find current row and try to move
            val currentRow = getRowForAddress(cursor)
            val nextCursor = if (kotlin.math.abs(step) == 1) {
                // Horizontal move: try simple add but check if it's still in a valid map
                val simpleNext = cursor + step
                val targets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
                if (targets.any { simpleNext >= it.start && simpleNext < it.end }) {
                    simpleNext
                } else {
                    // Jump to start/end of next/prev map if we cross a gap
                    getAddressForRow(getRowForAddress(simpleNext)) 
                }
            } else {
                // Vertical move: jump by row index
                val nextRow = (currentRow + (step / bytesPerRow)).coerceIn(0L, (if (activeMaps.isNotEmpty()) activeMaps.sortedBy { it.start }.sumOf { (it.end - it.start + bytesPerRow - 1) / bytesPerRow } else 0L) - 1)
                getAddressForRow(nextRow) + (cursor % bytesPerRow) // Try to maintain column
            }

            selectionEnd = nextCursor
            if (!shiftPressed) {
                selectionStart = nextCursor
            }
            hexInputBuffer = ""
            
            // Auto scroll
            val nextCursorRow = getRowForAddress(nextCursor).toInt()
            if (nextCursorRow < scrollPosition) {
                updateScrollPosition(nextCursorRow.toLong().coerceIn(0L, getMaxScrollPosition()))
            } else if (nextCursorRow >= scrollPosition + visibleRowsCount - 1) {
                updateScrollPosition((nextCursorRow - visibleRowsCount + 2).toLong().coerceIn(0L, getMaxScrollPosition()))
            }
            return true
        } else if (isEditingUnlocked) {
            val keyChar = keyEvent.utf16CodePoint.toChar()
            if (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) {
                pendingEdits.remove(cursor)
                hexInputBuffer = ""
                return true
            } else if (keyChar.isDigit() || keyChar.uppercaseChar() in 'A'..'F') {
                handleHexInput(keyChar)
                return true
            } else if (keyChar.code in 32..126) {
                pendingEdits[cursor] = keyChar.code.toByte()
                hexInputBuffer = ""
                advanceCursor()
                return true
            }
        }
        return false
    }

    fun handleHexInput(char: Char) {
        if (!isEditingUnlocked || selectionEnd == null) return
        val cursor = selectionEnd!!
        if (char.isDigit() || char.uppercaseChar() in 'A'..'F') {
            hexInputBuffer += char.uppercaseChar()
            if (hexInputBuffer.length == 2) {
                val b = hexInputBuffer.toIntOrNull(16)?.toByte()
                if (b != null) {
                    pendingEdits[cursor] = b
                }
                hexInputBuffer = ""
                advanceCursor()
            }
        }
    }

    fun advanceCursor() {
        val cursor = selectionEnd ?: return
        val nextCursor = cursor + 1
        val targets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        if (targets.any { nextCursor >= it.start && nextCursor < it.end }) {
            selectionEnd = nextCursor
            if (selectionStart == cursor) {
                selectionStart = nextCursor
            }
        }
    }

    fun loadMemory() {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid ?: return
        val targets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        if (targets.isEmpty()) return
        
        scope.launch {
            delay(100)
            val visibleStart = getAddressForRow(scrollPosition)
            val visibleEnd = getAddressForRow(scrollPosition + visibleRowsCount)
            
            withContext(Dispatchers.IO) {
                // Use absolute page alignment to ensure consistency with getByteAt and rendering
                var page = (visibleStart / pageSize) * pageSize
                while (page <= visibleEnd) {
                    if (!memoryCache.containsKey(page)) {
                        try {
                            val pageData = ByteArray(pageSize)
                            var hasData = false
                            
                            // Find all maps that overlap with this page [page, page + pageSize)
                            val overlappingMaps = targets.filter { it.start < page + pageSize && it.end > page }
                            
                            for (map in overlappingMaps) {
                                val readStart = maxOf(page, map.start)
                                val readEnd = minOf(page + pageSize, map.end)
                                if (readStart < readEnd) {
                                    val readLen = (readEnd - readStart).toInt()
                                    try {
                                        val data = AppContainer.clientAdapter.client.readMemory(pid, readStart, readLen)
                                        val destOffset = (readStart - page).toInt()
                                        System.arraycopy(data, 0, pageData, destOffset, data.size)
                                        hasData = true
                                    } catch (_: Exception) {
                                        // Individual read failure (e.g. guard page), skip this map segment
                                    }
                                }
                            }
                            
                            if (hasData || overlappingMaps.isEmpty()) {
                                memoryCache[page] = pageData
                            }
                        } catch (e: Exception) {
                            // Page failed entirely, insert empty to prevent infinite retry loop
                            memoryCache[page] = ByteArray(pageSize)
                        }
                    }
                    page += pageSize.toLong()
                }
            }
        }
    }

    fun getAddressAtOffset(x: Float, y: Float, density: Float, isMobile: Boolean, showAddress: Boolean): Pair<Long, ClickedArea>? {
        val addressWidthPx = if (showAddress) (if (isMobile) 80f * density else 120f * density) else 0f
        val hexCellWidthPx = if (isMobile) 20f * density else 24f * density
        val asciiCellWidthPx = if (isMobile) 9f * density else 12f * density
        val spacerAddressToHexPx = if (showAddress) (if (isMobile) 6f * density else 8f * density) else 0f
        val spacerHexToAsciiPx = if (isMobile) 12f * density else 16f * density

        val rowHeightPx = 24f * density
        // Subtract vertical header height from y before dividing by row height
        val adjustedY = y - (28f * density)
        val rowIndex = if (adjustedY < 0) 0 else (adjustedY / rowHeightPx).toInt()
        
        val rowAddress = getAddressForRow(scrollPosition + rowIndex)
        
        val sorted = if (activeMaps.isNotEmpty()) activeMaps.sortedBy { it.start } else listOfNotNull(activeMap)
        if (sorted.isEmpty()) return null
        if (rowAddress >= sorted.last().end) return null
        
        val startHexX = addressWidthPx + spacerAddressToHexPx
        val endHexX = startHexX + bytesPerRow * hexCellWidthPx
        val startAsciiX = endHexX + spacerHexToAsciiPx
        
        val midAddressHex = addressWidthPx + spacerAddressToHexPx / 2f
        val midHexAscii = endHexX + spacerHexToAsciiPx / 2f
        
        return if (showAddress && x < midAddressHex) {
            Pair(rowAddress, ClickedArea.ADDRESS)
        } else if (x < midHexAscii) {
            val offsetInHex = (x - startHexX).coerceIn(0f, (bytesPerRow * hexCellWidthPx) - 0.1f)
            val col = (offsetInHex / hexCellWidthPx).toInt().coerceIn(0, bytesPerRow - 1)
            Pair(rowAddress + col, ClickedArea.HEX)
        } else {
            val offsetInAscii = (x - startAsciiX).coerceIn(0f, (bytesPerRow * asciiCellWidthPx) - 0.1f)
            val col = (offsetInAscii / asciiCellWidthPx).toInt().coerceIn(0, bytesPerRow - 1)
            Pair(rowAddress + col, ClickedArea.ASCII)
        }
    }

    fun isAddressSelected(addr: Long): Boolean {
        val start = selectionStart ?: return false
        val end = selectionEnd ?: return false
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        return addr in lo..hi
    }

    fun getSelectedBytesText(): String {
        val start = selectionStart ?: return ""
        val end = selectionEnd ?: return ""
        val s = minOf(start, end)
        val e = maxOf(start, end)
        return buildString {
            for (addr in s..e) {
                val byteVal = getByteAt(addr)
                append(String.format("%02X ", byteVal))
            }
        }.trim()
    }

    fun getSelectedAsciiText(): String {
        val start = selectionStart ?: return ""
        val end = selectionEnd ?: return ""
        val s = minOf(start, end)
        val e = maxOf(start, end)
        return buildString {
            for (addr in s..e) {
                val byteVal = getByteAt(addr)
                val b = byteVal.toInt() and 0xFF
                if (b in 32..126) append(b.toChar()) else append(".")
            }
        }
    }

    fun getByteAt(addr: Long): Byte {
        pendingEdits[addr]?.let { return it }
        val pageStart = (addr / pageSize) * pageSize
        val offset = (addr - pageStart).toInt()
        val page = memoryCache[pageStart]
        return if (page != null && offset in page.indices) page[offset] else 0.toByte()
    }
}

@Composable
fun rememberHexState(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    jumpToAddress: Long?,
    selectionStartParam: Long?,
    selectionEndParam: Long?,
    onSelectionChanged: ((Long?, Long?) -> Unit)?,
    scope: CoroutineScope = rememberCoroutineScope()
): HexState {
    val state = remember(activeMap, activeMaps.toList()) {
        HexState(activeMap, activeMaps, jumpToAddress, selectionStartParam, selectionEndParam, onSelectionChanged, scope)
    }
    
    state.selectionStart = selectionStartParam
    state.selectionEnd = selectionEndParam

    SideEffect {
        val mapsChanged = state.activeMap != activeMap || state.activeMaps.size != activeMaps.size || !state.activeMaps.containsAll(activeMaps)
        state.activeMap = activeMap
        state.activeMaps.clear()
        state.activeMaps.addAll(activeMaps)
        state.onSelectionChanged = onSelectionChanged
        if (mapsChanged) {
            state.memoryCache.clear()
        }
    }
    
    return state
}
