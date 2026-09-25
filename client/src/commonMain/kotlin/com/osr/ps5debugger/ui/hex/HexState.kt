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
    var touchStartAddr by mutableStateOf<Long?>(null)
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
        if (keyEvent.type != KeyEventType.KeyDown) return false
        
        val shortcuts = com.osr.ps5debugger.util.DefaultIpHelper.getShortcuts()
        
        if (com.osr.ps5debugger.util.ShortcutManager.isMatch(keyEvent, shortcuts["lock_edit"])) {
            isEditingUnlocked = !isEditingUnlocked
            return true
        }
        
        if (com.osr.ps5debugger.util.ShortcutManager.isMatch(keyEvent, shortcuts["undo"])) {
            pendingEdits.clear()
            return true
        }
        
        if (com.osr.ps5debugger.util.ShortcutManager.isMatch(keyEvent, shortcuts["copy"])) {
            val text = getSelectedBytesText()
            if (text.isNotEmpty()) com.osr.ps5debugger.util.copyToClipboard(text)
            return true
        }

        if (com.osr.ps5debugger.util.ShortcutManager.isMatch(keyEvent, shortcuts["paste"])) {
            if (isEditingUnlocked) {
                val clipboardText = com.osr.ps5debugger.util.getFromClipboard()
                if (clipboardText.isNotEmpty()) {
                    pasteHex(clipboardText)
                    return true
                }
            }
        }

        if (selectionEnd == null) return false
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
                    val currentVisiblePage = (visibleStart / pageSize) * pageSize
                    if (memoryCache.size > 512) {
                        val pagesToRemove = memoryCache.keys.filter { pageAddr ->
                            kotlin.math.abs(pageAddr - currentVisiblePage) > 256 * pageSize
                        }
                        for (p in pagesToRemove) {
                            memoryCache.remove(p)
                        }
                    }

                    var page = currentVisiblePage
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

    private suspend fun loadPage(pid: Int?, page: Long, targets: List<MemoryRange>, skipChangeTracking: Boolean = false) {
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
                        
                        // CHANGE TRACKING LOGIC — skip on first load and during greedy pre-fetching
                        if (!skipChangeTracking) {
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

        val totalBytes = targets.sumOf { it.end - it.start }
        val allLocal = targets.all { it.localData != null }

        // For large regions (>32MB, e.g. 1GB+), skip full-region greedy downloading.
        // HexViewer is virtualized and loads visible pages on demand via loadMemory().
        if (totalBytes > 32 * 1024 * 1024L) {
            totalPagesCount = 0
            loadedPagesCount = 0
            return
        }
        
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
                    // FAST PATH: Local processing with batched progress updates
                    var pagesSinceUpdate = 0
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
                                pagesSinceUpdate++
                            }
                            current = page + pageSize
                            // Batch progress updates and yield every 100 pages
                            if (pagesSinceUpdate >= 100) {
                                withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                                pagesSinceUpdate = 0
                                yield()
                            }
                        }
                    }
                    // Final progress update
                    if (pagesSinceUpdate > 0) {
                        withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                    }
                } else {
                    // CONSOLE PATH: Large-chunk reads to minimize network round-trips
                    // Read 4MB at a time (64 pages) instead of 64KB per call
                    val largeChunkSize = 4L * 1024 * 1024 // 4MB
                    var pagesSinceUpdate = 0

                    for (map in targets) {
                        if (!isActive) break
                        val mapStart = map.start
                        val mapEnd = map.end

                        if (map.localData != null) {
                            // Local data within a mixed target set — process locally
                            val mapData = map.localData
                            var current = mapStart
                            while (current < mapEnd) {
                                if (!isActive) return@launch
                                val page = (current / pageSize) * pageSize
                                if (!memoryCache.containsKey(page)) {
                                    val pageData = ByteArray(pageSize)
                                    val readStart = maxOf(page, mapStart)
                                    val readEnd = minOf(page + pageSize, mapEnd)
                                    val offsetInMap = (readStart - mapStart).toInt()
                                    val len = (readEnd - readStart).toInt()
                                    mapData.copyInto(pageData, (readStart - page).toInt(), offsetInMap, offsetInMap + len)
                                    memoryCache[page] = pageData
                                    pagesSinceUpdate++
                                }
                                current = page + pageSize
                                if (pagesSinceUpdate >= 100) {
                                    withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                                    pagesSinceUpdate = 0
                                    yield()
                                }
                            }
                            continue
                        }

                        if (pid == null) continue

                        // Network reads in large chunks
                        var chunkAddr = (mapStart / pageSize) * pageSize
                        while (chunkAddr < mapEnd) {
                            if (!isActive) return@launch
                            // Yield to visible-area loading
                            while (isLoading) delay(200)

                            // Find how many consecutive uncached pages we can batch
                            val chunkEnd = minOf(chunkAddr + largeChunkSize, mapEnd)
                            val readStart = maxOf(chunkAddr, mapStart)
                            val readEnd = minOf(chunkEnd, mapEnd)
                            val readLen = (readEnd - readStart).toInt()

                            if (readLen <= 0) {
                                chunkAddr = chunkEnd
                                continue
                            }

                            // Check if we actually need any pages in this range
                            var needsAnyPage = false
                            var checkAddr = (readStart / pageSize) * pageSize
                            while (checkAddr < readEnd) {
                                if (!memoryCache.containsKey(checkAddr)) {
                                    needsAnyPage = true
                                    break
                                }
                                checkAddr += pageSize
                            }

                            if (!needsAnyPage) {
                                chunkAddr = chunkEnd
                                continue
                            }

                            try {
                                // Single large network read
                                val data = AppContainer.clientAdapter.client.readMemory(pid, readStart, readLen)

                                // Split into page-sized entries in the cache
                                var pageAddr = (readStart / pageSize) * pageSize
                                while (pageAddr < readEnd) {
                                    if (!memoryCache.containsKey(pageAddr)) {
                                        val pageData = ByteArray(pageSize)
                                        val copyStart = maxOf(pageAddr, readStart)
                                        val copyEnd = minOf(pageAddr + pageSize, readEnd)
                                        val srcOffset = (copyStart - readStart).toInt()
                                        val destOffset = (copyStart - pageAddr).toInt()
                                        val copyLen = (copyEnd - copyStart).toInt()
                                        if (copyLen > 0 && srcOffset + copyLen <= data.size) {
                                            System.arraycopy(data, srcOffset, pageData, destOffset, copyLen)
                                        }
                                        memoryCache[pageAddr] = pageData
                                        pagesSinceUpdate++
                                    }
                                    pageAddr += pageSize
                                }
                            } catch (_: Exception) {
                                // On failure, fall back to individual page loading for this chunk
                                var fallbackPage = (readStart / pageSize) * pageSize
                                while (fallbackPage < readEnd) {
                                    if (!isActive) return@launch
                                    if (!memoryCache.containsKey(fallbackPage)) {
                                        loadPage(pid, fallbackPage, targets, skipChangeTracking = true)
                                        pagesSinceUpdate++
                                    }
                                    fallbackPage += pageSize
                                }
                            }

                            // Batched progress update
                            if (pagesSinceUpdate >= 50) {
                                withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                                pagesSinceUpdate = 0
                                yield()
                            }

                            chunkAddr = chunkEnd
                        }
                    }

                    // Final progress update
                    if (pagesSinceUpdate > 0) {
                        withContext(Dispatchers.Main) { loadedPagesCount = memoryCache.size }
                    }
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
        val addressWidthPx = HexLayoutMetrics.addressWidthDp(isMobile, showAddress).value * density
        val hexCellWidthPx = HexLayoutMetrics.hexCellWidthDp(isMobile).value * density
        val midGapPx = HexLayoutMetrics.midGapDp(isMobile, bytesPerRow).value * density
        val asciiCellWidthPx = HexLayoutMetrics.asciiCellWidthDp(isMobile).value * density
        val asciiMidGapPx = if (bytesPerRow >= 16) 4f * density else 0f
        val spacerAddressToHexPx = HexLayoutMetrics.spacerAddressToHexDp(isMobile, showAddress).value * density
        val spacerHexToAsciiPx = HexLayoutMetrics.spacerHexToAsciiDp(isMobile).value * density

        val rowHeightPx = HexLayoutMetrics.rowHeightDp.value * density

        // y is local to HexGridBody where row 0 starts at y = 0
        val rowIndex = if (y < 0f) 0 else (y / rowHeightPx).toInt()
        
        val sorted = if (activeMap != null) listOf(activeMap!!) else activeMaps.sortedBy { it.start }
        if (sorted.isEmpty()) return null

        val targetRow = scrollPosition + rowIndex
        val rowAddress = getAddressForRow(targetRow)
        
        if (rowAddress >= sorted.last().end) {
            val lastMap = sorted.last()
            return Pair(maxOf(lastMap.start, lastMap.end - 1), ClickedArea.HEX)
        }
        
        val startHexX = addressWidthPx + spacerAddressToHexPx
        val totalHexWidthPx = (bytesPerRow * hexCellWidthPx) + midGapPx
        val endHexX = startHexX + totalHexWidthPx
        val startAsciiX = endHexX + spacerHexToAsciiPx
        
        val midAddressHex = addressWidthPx + spacerAddressToHexPx / 2f
        val midHexAscii = endHexX + spacerHexToAsciiPx / 2f
        
        return if (showAddress && x < midAddressHex) {
            Pair(rowAddress, ClickedArea.ADDRESS)
        } else if (x < midHexAscii) {
            val col = if (bytesPerRow == 16) {
                if (x < startHexX + 8 * hexCellWidthPx) {
                    ((x - startHexX) / hexCellWidthPx).toInt().coerceIn(0, 7)
                } else {
                    (((x - startHexX - midGapPx) / hexCellWidthPx).toInt()).coerceIn(8, 15)
                }
            } else {
                val offsetInHex = (x - startHexX).coerceIn(0f, totalHexWidthPx - 0.1f)
                (offsetInHex / hexCellWidthPx).toInt().coerceIn(0, bytesPerRow - 1)
            }
            Pair(minOf(rowAddress + col, sorted.last().end - 1), ClickedArea.HEX)
        } else {
            val col = if (bytesPerRow == 16) {
                if (x < startAsciiX + 8 * asciiCellWidthPx) {
                    ((x - startAsciiX) / asciiCellWidthPx).toInt().coerceIn(0, 7)
                } else {
                    (((x - startAsciiX - asciiMidGapPx) / asciiCellWidthPx).toInt()).coerceIn(8, 15)
                }
            } else {
                val totalAsciiWidthPx = (bytesPerRow * asciiCellWidthPx) + asciiMidGapPx
                val offsetInAscii = (x - startAsciiX).coerceIn(0f, totalAsciiWidthPx - 0.1f)
                (offsetInAscii / asciiCellWidthPx).toInt().coerceIn(0, bytesPerRow - 1)
            }
            Pair(minOf(rowAddress + col, sorted.last().end - 1), ClickedArea.ASCII)
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

    fun getSelectedCArrayText(): String {
        val start = selectionStart ?: return ""
        val end = selectionEnd ?: return ""
        val s = minOf(start, end)
        val e = maxOf(start, end)
        return buildString {
            append("{ ")
            for (addr in s..e) {
                val byteVal = getByteAt(addr).toInt() and 0xFF
                append(String.format("0x%02X", byteVal))
                if (addr < e) append(", ")
            }
            append(" }")
        }
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

    fun getInt8(addr: Long): Byte = getByteAt(addr)
    fun getUInt8(addr: Long): Int = getByteAt(addr).toInt() and 0xFF

    fun getInt16(addr: Long): Short {
        val b0 = getByteAt(addr).toInt() and 0xFF
        val b1 = getByteAt(addr + 1).toInt() and 0xFF
        return (b0 or (b1 shl 8)).toShort()
    }
    fun getUInt16(addr: Long): Int = getInt16(addr).toInt() and 0xFFFF

    fun getInt32(addr: Long): Int {
        val b0 = getByteAt(addr).toLong() and 0xFF
        val b1 = getByteAt(addr + 1).toLong() and 0xFF
        val b2 = getByteAt(addr + 2).toLong() and 0xFF
        val b3 = getByteAt(addr + 3).toLong() and 0xFF
        return (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)).toInt()
    }
    fun getUInt32(addr: Long): Long = getInt32(addr).toLong() and 0xFFFFFFFFL

    fun getInt64(addr: Long): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = v or ((getByteAt(addr + i).toLong() and 0xFF) shl (i * 8))
        }
        return v
    }
    fun getUInt64(addr: Long): Long = getInt64(addr)

    fun getFloat(addr: Long): Float = java.lang.Float.intBitsToFloat(getInt32(addr))
    fun getDouble(addr: Long): Double = java.lang.Double.longBitsToDouble(getInt64(addr))

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
