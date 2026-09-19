package com.osr.ps5debugger.ui.hex

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
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
    val memoryCache: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, ByteArray> get() {
        val map = activeMap ?: activeMaps.firstOrNull()
        val key = map?.let { "${it.start}_${it.end}_${it.name}" } ?: "default"
        return AppContainer.getHexCache(key)
    }
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
    var currentJumpAddress by mutableStateOf<Long?>(null)
    
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
    var isLoading by mutableStateOf(false)
    var isGreedyLoading by mutableStateOf(false)
    
    var refreshRateMs by mutableLongStateOf(2000L)
    val changedBytes = mutableStateMapOf<Long, Long>() // address -> timestamp of change

    var loadedPagesCount by mutableIntStateOf(0)
    var totalPagesCount by mutableIntStateOf(0)
    val loadingProgress by derivedStateOf { 
        if (totalPagesCount > 0) loadedPagesCount.toFloat() / totalPagesCount.toFloat() else 0f 
    }

    fun updateScrollPosition(newPos: Long) {
        scrollPosition = newPos
        // Store scroll position based on the first map's start to maintain context
        val sorted = if (activeMap != null) listOf(activeMap!!) else activeMaps.sortedBy { it.start }
        sorted.firstOrNull()?.let { hexViewerScrollPositions[it.start] = newPos }
    }

    fun changeSelection(start: Long?, end: Long?) {
        selectionStart = start
        selectionEnd = end
        onSelectionChanged?.invoke(start, end)
    }

    fun getAddressForRow(row: Long): Long {
        var remainingRows = row
        val sorted = if (activeMap != null) listOf(activeMap!!) else activeMaps.sortedBy { it.start }
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
        val sorted = if (activeMap != null) listOf(activeMap!!) else activeMaps.sortedBy { it.start }
        for (map in sorted) {
            if (address >= map.start && address < map.end) {
                return rowCount + (address - map.start) / bytesPerRow
            }
            rowCount += (map.end - map.start + bytesPerRow - 1) / bytesPerRow
        }
        return rowCount
    }

    fun getMaxScrollPosition(): Long {
        val sorted = if (activeMap != null) listOf(activeMap!!) else activeMaps.sortedBy { it.start }
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
                val targets = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
                if (targets.any { simpleNext >= it.start && simpleNext < it.end }) {
                    simpleNext
                } else {
                    // Jump to start/end of next/prev map if we cross a gap
                    getAddressForRow(getRowForAddress(simpleNext)) 
                }
            } else {
                // Vertical move: jump by row index
                val nextRow = (currentRow + (step / bytesPerRow)).coerceIn(0L, ((if (activeMap != null) listOf(activeMap!!) else activeMaps).sumOf { (it.end - it.start + bytesPerRow - 1) / bytesPerRow }) - 1)
                getAddressForRow(nextRow) + (cursor % bytesPerRow) // Try to maintain column
            }

            val nextStart = if (!shiftPressed) nextCursor else selectionStart
            changeSelection(nextStart, nextCursor)
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
            if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.V) {
                val clipboardText = com.osr.ps5debugger.util.getFromClipboard()
                if (clipboardText.isNotEmpty()) {
                    pasteHex(clipboardText)
                    return true
                }
            }
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
        val targets = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
        if (targets.any { nextCursor >= it.start && nextCursor < it.end }) {
            val nextStart = if (selectionStart == cursor) nextCursor else selectionStart
            changeSelection(nextStart, nextCursor)
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var greedyJob: kotlinx.coroutines.Job? = null

    fun loadMemory(forceRefresh: Boolean = false) {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val targets = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
        if (targets.isEmpty()) return
        
        val allLocal = targets.all { it.localData != null }
        if (!allLocal && pid == null) return
        
        loadJob?.cancel()
        loadJob = scope.launch {
            if (!allLocal && !forceRefresh) delay(10) // Small delay to prevent jitter
            
            isLoading = true
            try {
                val visibleStart = getAddressForRow(scrollPosition)
                // Use visibleRowsCount + some buffer to pre-load slightly ahead
                val bufferRows = 10
                val visibleEnd = getAddressForRow(scrollPosition + visibleRowsCount + bufferRows)
                
                withContext(Dispatchers.IO) {
                    var page = (visibleStart / pageSize) * pageSize
                    while (page <= visibleEnd) {
                        if (forceRefresh || !memoryCache.containsKey(page)) {
                            loadPage(pid, page, targets)
                            withContext(Dispatchers.Main) { 
                                loadedPagesCount = memoryCache.size
                            }
                        }
                        page += pageSize.toLong()
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun loadPage(pid: Int?, page: Long, targets: List<MemoryRange>) {
        try {
            val pageData = ByteArray(pageSize)
            var hasData = false
            val overlappingMaps = targets.filter { it.start < page + pageSize && it.end > page }
            
            for (map in overlappingMaps) {
                val readStart = maxOf(page, map.start)
                val readEnd = minOf(page + pageSize, map.end)
                if (readStart < readEnd) {
                    val readLen = (readEnd - readStart).toInt()
                    try {
                        val data = if (map.localData != null) {
                            val localOffset = (readStart - map.start).toInt()
                            map.localData.copyOfRange(localOffset, localOffset + readLen)
                        } else if (pid != null) {
                            AppContainer.clientAdapter.client.readMemory(pid, readStart, readLen)
                        } else ByteArray(readLen)
                        
                        val destOffset = (readStart - page).toInt()
                        
                        // CHANGE TRACKING LOGIC
                        val oldData = memoryCache[page]
                        if (oldData != null) {
                            for (i in 0 until data.size) {
                                val absAddr = readStart + i
                                val oldVal = oldData[destOffset + i]
                                val newVal = data[i]
                                if (newVal != oldVal) {
                                    changedBytes[absAddr] = System.currentTimeMillis()
                                    
                                    // Log the change
                                    AppContainer.debuggerUseCase.log(
                                        "MEMORY",
                                        "Value at 0x${absAddr.toString(16).uppercase()} changed: 0x${(oldVal.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()} -> 0x${(newVal.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()}",
                                        com.osr.ps5debugger.domain.model.LogEntry.Level.INFO
                                    )
                                }
                            }
                        }

                        System.arraycopy(data, 0, pageData, destOffset, data.size)
                        hasData = true
                    } catch (_: Exception) {}
                }
            }
            if (hasData || overlappingMaps.isEmpty()) {
                memoryCache[page] = pageData
            }
        } catch (e: Exception) {
            memoryCache[page] = ByteArray(pageSize)
        }
    }

    fun startGreedyLoader() {
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val targets = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
        if (targets.isEmpty()) return
        
        greedyJob?.cancel()
        greedyJob = scope.launch(Dispatchers.IO) {
            isGreedyLoading = true
            try {
                // 1. Calculate total pages
                var total = 0
                for (map in targets) {
                    total += ((map.end - map.start + pageSize - 1) / pageSize).toInt()
                }
                withContext(Dispatchers.Main) { 
                    totalPagesCount = total
                    loadedPagesCount = memoryCache.size
                }

                val allLocal = targets.all { it.localData != null }

                if (allLocal) {
                    // FAST PATH: Parallel local processing
                    targets.forEach { map ->
                        val mapData = map.localData ?: return@forEach
                        val start = map.start
                        val end = map.end
                        
                        var current = start
                        while (current < end) {
                            if (!isActive) return@launch
                            val page = (current / pageSize) * pageSize
                            if (!memoryCache.containsKey(page)) {
                                val pageData = ByteArray(pageSize)
                                val readStart = maxOf(page, start)
                                val readEnd = minOf(page + pageSize, end)
                                val offsetInMap = (readStart - start).toInt()
                                val len = (readEnd - readStart).toInt()
                                mapData.copyInto(pageData, (readStart - page).toInt(), offsetInMap, offsetInMap + len)
                                memoryCache[page] = pageData
                                withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                            }
                            current = page + pageSize
                            // Allow UI thread to breathe every 100 pages
                            if ((current / pageSize) % 100 == 0L) yield()
                        }
                    }
                } else {
                    // CONSOLE PATH: Optimized parallel fetching
                    // Use a worker pool to fetch pages without overwhelming the console
                    val pageChannel = kotlinx.coroutines.channels.Channel<Long>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                    
                    // Populate channel with missing pages
                    for (map in targets) {
                        var current = map.start
                        while (current < map.end) {
                            val page = (current / pageSize) * pageSize
                            if (!memoryCache.containsKey(page)) {
                                pageChannel.trySend(page)
                            }
                            current = page + pageSize
                        }
                    }
                    pageChannel.close()

                    // Launch 4 parallel fetchers
                    val workers = List(4) {
                        launch {
                            for (page in pageChannel) {
                                if (!isActive) break
                                while (isLoading) delay(200) // Yield to visible area
                                loadPage(pid, page, targets)
                                withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                                yield()
                            }
                        }
                    }
                    workers.forEach { it.join() }
                }
                
                withContext(Dispatchers.Main) { 
                    loadedPagesCount = totalPagesCount 
                }
            } finally {
                isGreedyLoading = false
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
        
        val sorted = if (activeMap != null) listOf(activeMap!!) else activeMaps.sortedBy { it.start }
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
        
        // Fast path for local files: read directly from source data if cache is missing
        val targets = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
        val localMap = targets.firstOrNull { it.localData != null && addr >= it.start && addr < it.end }
        if (localMap != null) {
            val offset = (addr - localMap.start).toInt()
            if (offset >= 0 && offset < (localMap.localData?.size ?: 0)) {
                return localMap.localData!![offset]
            }
        }

        val pageStart = (addr / pageSize) * pageSize
        val offset = (addr - pageStart).toInt()
        val page = memoryCache[pageStart]
        return if (page != null && offset in page.indices) page[offset] else 0.toByte()
    }

    fun pasteHex(hexStr: String, targetAddr: Long? = null) {
        if (!isEditingUnlocked) return
        val baseAddr = targetAddr ?: selectionEnd ?: return
        
        val sanitized = hexStr.replace("0x", " ")
            .replace("\\x", " ")
            .replace(",", " ")
            .replace("\n", " ")
            .replace("\r", " ")
        val tokens = sanitized.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val bytes = mutableListOf<Byte>()
        for (token in tokens) {
            if (token.length > 2 && token.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
                var i = 0
                while (i < token.length) {
                    if (i + 1 < token.length) {
                        token.substring(i, i + 2).toIntOrNull(16)?.toByte()?.let { bytes.add(it) }
                        i += 2
                    } else {
                        token.substring(i, i + 1).toIntOrNull(16)?.toByte()?.let { bytes.add(it) }
                        i += 1
                    }
                }
            } else {
                token.toIntOrNull(16)?.toByte()?.let { bytes.add(it) }
            }
        }
        
        if (bytes.isEmpty()) return
        
        val targets = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
        var currentAddr = baseAddr
        for (b in bytes) {
            if (!targets.any { currentAddr >= it.start && currentAddr < it.end }) {
                val nextValid = targets.filter { it.start > currentAddr }.minByOrNull { it.start }?.start
                if (nextValid != null) {
                    currentAddr = nextValid
                } else {
                    break
                }
            }
            pendingEdits[currentAddr] = b
            currentAddr++
        }
        
        val lastAddr = currentAddr - 1
        if (targets.any { lastAddr >= it.start && lastAddr < it.end }) {
            changeSelection(baseAddr, lastAddr)
        }
        hexInputBuffer = ""
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
