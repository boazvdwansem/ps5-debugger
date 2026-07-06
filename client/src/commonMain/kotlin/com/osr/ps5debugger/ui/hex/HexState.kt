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
private var lastActiveMapStart: Long? = null

class HexState(
    val activeMap: MemoryRange?,
    val jumpToAddress: Long?,
    val selectionStartParam: Long?,
    val selectionEndParam: Long?,
    val onSelectionChanged: ((Long?, Long?) -> Unit)?,
    val scope: CoroutineScope
) {
    val pageSize = 65536
    val memoryCache = mutableStateMapOf<Long, ByteArray>()
    val pendingEdits = mutableStateMapOf<Long, Byte>()
    
    var startAddress by mutableStateOf(activeMap?.start ?: 0L)
    var endAddress by mutableStateOf(activeMap?.end ?: 0L)
    var bytesPerRow by mutableIntStateOf(16)
    var visibleRowsCount by mutableIntStateOf(1)
    
    var scrollPosition by mutableStateOf(activeMap?.let { hexViewerScrollPositions[it.start] } ?: 0L)
    var selectionStart by mutableStateOf<Long?>(selectionStartParam)
    var selectionEnd by mutableStateOf<Long?>(selectionEndParam)
    
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

    fun updateScrollPosition(newPos: Long) {
        scrollPosition = newPos
        activeMap?.let { hexViewerScrollPositions[it.start] = newPos }
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
            val nextCursor = (cursor + step).coerceIn(startAddress, endAddress - 1)
            selectionEnd = nextCursor
            if (!shiftPressed) {
                selectionStart = nextCursor
            }
            hexInputBuffer = ""
            
            // Auto scroll
            val nextCursorRow = ((nextCursor - startAddress) / bytesPerRow).toInt()
            if (nextCursorRow < scrollPosition) {
                updateScrollPosition(nextCursorRow.toLong().coerceIn(0L, getMaxScrollPosition()))
            } else if (nextCursorRow >= scrollPosition + visibleRowsCount) {
                updateScrollPosition((nextCursorRow - visibleRowsCount + 1).toLong().coerceIn(0L, getMaxScrollPosition()))
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

    private fun advanceCursor() {
        val cursor = selectionEnd ?: return
        if (cursor + 1 < endAddress) {
            selectionEnd = cursor + 1
            if (selectionStart == cursor) {
                selectionStart = cursor + 1
            }
        }
    }

    fun getMaxScrollPosition(): Long {
        val totalBytes = maxOf(0L, endAddress - startAddress)
        val rowCount = ((totalBytes + bytesPerRow - 1) / bytesPerRow).toInt()
        return maxOf(0L, (rowCount - visibleRowsCount).toLong())
    }

    fun loadMemory() {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid ?: return
        if (activeMap == null) return
        
        scope.launch {
            delay(100)
            val visibleStart = startAddress + scrollPosition * bytesPerRow
            val visibleEnd = minOf(endAddress, visibleStart + visibleRowsCount * bytesPerRow)
            
            withContext(Dispatchers.IO) {
                var page = (visibleStart / pageSize) * pageSize
                while (page <= visibleEnd) {
                    if (!memoryCache.containsKey(page)) {
                        try {
                            val readStart = maxOf(page, startAddress)
                            val readEnd = minOf(page + pageSize, endAddress)
                            if (readStart < readEnd) {
                                val readLen = (readEnd - readStart).toInt()
                                val data = AppContainer.clientAdapter.client.readMemory(pid, readStart, readLen)
                                val pageData = ByteArray(pageSize)
                                val destOffset = (readStart - page).toInt()
                                System.arraycopy(data, 0, pageData, destOffset, data.size)
                                memoryCache[page] = pageData
                            } else {
                                memoryCache[page] = ByteArray(pageSize)
                            }
                        } catch (_: Exception) {
                            memoryCache[page] = ByteArray(pageSize)
                        }
                    }
                    page += pageSize
                }
            }
        }
    }

    fun getAddressAtOffset(x: Float, y: Float, density: Float, isMobile: Boolean): Pair<Long, ClickedArea>? {
        val addressWidthPx = if (isMobile) 80f * density else 120f * density
        val hexCellWidthPx = if (isMobile) 20f * density else 24f * density
        val asciiCellWidthPx = if (isMobile) 9f * density else 12f * density
        val spacerAddressToHexPx = if (isMobile) 6f * density else 8f * density
        val spacerHexToAsciiPx = if (isMobile) 12f * density else 16f * density

        val rowHeightPx = 24f * density
        val rowIndex = (y / rowHeightPx).toInt()
        
        val currentViewStart = startAddress + scrollPosition * bytesPerRow
        val rowAddress = currentViewStart + rowIndex * bytesPerRow
        if (rowAddress >= endAddress) return null
        
        val startHexX = addressWidthPx + spacerAddressToHexPx
        val endHexX = startHexX + bytesPerRow * hexCellWidthPx
        val startAsciiX = endHexX + spacerHexToAsciiPx
        
        val midAddressHex = addressWidthPx + spacerAddressToHexPx / 2f
        val midHexAscii = endHexX + spacerHexToAsciiPx / 2f
        
        return if (x < midAddressHex) {
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
    jumpToAddress: Long?,
    selectionStartParam: Long?,
    selectionEndParam: Long?,
    onSelectionChanged: ((Long?, Long?) -> Unit)?,
    scope: CoroutineScope = rememberCoroutineScope()
) = remember(activeMap) {
    HexState(activeMap, jumpToAddress, selectionStartParam, selectionEndParam, onSelectionChanged, scope).also {
        lastActiveMapStart = activeMap?.start
    }
}
