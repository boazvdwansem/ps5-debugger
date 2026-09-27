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
import kotlinx.coroutines.NonCancellable
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
    val memoryCache: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, ByteArray> get() = AppContainer.hexCache
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

    private val isInitialLocal = (activeMapInitial?.localData != null) || (activeMapsInitial.isNotEmpty() && activeMapsInitial.all { it.localData != null })
    var loadingProgress by mutableFloatStateOf(if (isInitialLocal) 1f else 0f)
    var isCurrentTargetReady by mutableStateOf(isInitialLocal)
    var isHexComplete by mutableStateOf(isInitialLocal)

    var loadedBytes by mutableLongStateOf(0L)
    var currentTargetRegionKey by mutableStateOf<String?>(null)

    private val fallbackRegionProgress = AppContainer.HexRegionProgress()

    val currentRegionProgress: AppContainer.HexRegionProgress
        get() {
            val key = currentTargetRegionKey ?: getRegionKey()
            return if (key.isNotEmpty()) {
                AppContainer.hexProgressCache.getOrPut(key) { AppContainer.HexRegionProgress() }
            } else {
                fallbackRegionProgress
            }
        }

    val completedChunks: MutableSet<Long>
        get() = currentRegionProgress.completedChunks

    fun getRegionKey(): String {
        val targets = getEffectiveTargets()
        return targets.joinToString(";") { "${it.start}-${it.end}" }
    }

    fun restoreRegionProgress() {
        val currentEffective = getEffectiveTargets()
        val allLocal = currentEffective.isNotEmpty() && currentEffective.all { it.localData != null }
        val currentIsLocal = (activeMap?.localData != null) || allLocal

        if (currentEffective.isEmpty()) {
            currentTargetRegionKey = null
            loadingProgress = 0f
            isCurrentTargetReady = false
            isHexComplete = false
            loadedBytes = 0L
            return
        }

        val regionKey = currentEffective.joinToString(";") { "${it.start}-${it.end}" }
        currentTargetRegionKey = regionKey

        if (allLocal || currentIsLocal || AppContainer.isOfflineSession) {
            loadingProgress = 1f
            isCurrentTargetReady = true
            isHexComplete = true
            return
        }

        val cached = AppContainer.hexProgressCache[regionKey]
        if (cached != null) {
            loadedBytes = cached.loadedBytes
            loadingProgress = cached.progress
            isHexComplete = cached.isComplete
            isCurrentTargetReady = cached.isComplete || cached.progress > 0f
        } else {
            loadedBytes = 0L
            loadingProgress = 0f
            isHexComplete = false
            isCurrentTargetReady = false
        }
    }

    init {
        restoreRegionProgress()
    }

    fun pruneCacheIfNeeded() {
        if (AppContainer.isOfflineSession) return
        val maxPages = 8192 // 512MB cache limit (8192 * 64KB)
        val runtime = Runtime.getRuntime()
        val freeMem = runtime.freeMemory()
        
        // Only prune if we exceed 512MB OR if JVM free memory drops dangerously low (< 80MB)
        val needsPrune = memoryCache.size > maxPages || (freeMem < 80 * 1024 * 1024L && memoryCache.size > 2048)
        if (!needsPrune) return

        val targetPages = if (freeMem < 80 * 1024 * 1024L) maxOf(1024, memoryCache.size - 256) else maxPages
        val countToRemove = memoryCache.size - targetPages
        if (countToRemove <= 0) return

        val centerAddr = getAddressForRow(scrollPosition)
        val centerPage = (centerAddr / pageSize) * pageSize

        val keysToRemove = memoryCache.keys
            .sortedByDescending { kotlin.math.abs(it - centerPage) }
            .take(countToRemove)

        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            for (p in keysToRemove) {
                memoryCache.remove(p)
            }
        }
    }

    fun getEffectiveTargets(): List<MemoryRange> {
        val base = if (activeMap != null) listOf(activeMap!!) else activeMaps.toList()
        return base.flatMap { map ->
            if (map.subRanges.isNotEmpty()) {
                map.subRanges
            } else {
                listOf(map)
            }
        }.sortedBy { it.start }
    }

    fun getAllOpenTargets(): List<MemoryRange> {
        val base = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        return base.flatMap { map ->
            if (map.subRanges.isNotEmpty()) {
                map.subRanges
            } else {
                listOf(map)
            }
        }
    }

    fun updateScrollPosition(newPos: Long) {
        scrollPosition = newPos
        // Store scroll position based on the first map's start to maintain context
        val sorted = getEffectiveTargets()
        sorted.firstOrNull()?.let { hexViewerScrollPositions[it.start] = newPos }
    }

    fun restoreScrollForActiveMap() {
        val saved = activeMap?.let { hexViewerScrollPositions[it.start] } ?: 0L
        scrollPosition = saved.coerceIn(0L, getMaxScrollPosition())
    }

    fun cancelJobs() {
        loadJob?.cancel()
        greedyJob?.cancel()
        isLoading = false
        isGreedyLoading = false
    }

    fun changeSelection(start: Long?, end: Long?) {
        selectionStart = start
        selectionEnd = end
        onSelectionChanged?.invoke(start, end)
    }

    fun getAddressForRow(row: Long): Long {
        var remainingRows = row
        val sorted = getEffectiveTargets()
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
        val sorted = getEffectiveTargets()
        for (map in sorted) {
            if (address >= map.start && address < map.end) {
                return rowCount + (address - map.start) / bytesPerRow
            }
            rowCount += (map.end - map.start + bytesPerRow - 1) / bytesPerRow
        }
        return rowCount
    }

    fun getTotalRows(): Long {
        return getEffectiveTargets().sumOf { (it.end - it.start + bytesPerRow - 1) / bytesPerRow }
    }

    fun getMaxScrollPosition(): Long {
        return maxOf(0L, getTotalRows() - visibleRowsCount)
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
                val targets = getEffectiveTargets()
                if (targets.any { simpleNext >= it.start && simpleNext < it.end }) {
                    simpleNext
                } else {
                    // Jump to start/end of next/prev map if we cross a gap
                    getAddressForRow(getRowForAddress(simpleNext)) 
                }
            } else {
                // Vertical move: jump by row index
                val nextRow = (currentRow + (step / bytesPerRow)).coerceIn(0L, (getEffectiveTargets().sumOf { (it.end - it.start + bytesPerRow - 1) / bytesPerRow }) - 1)
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
        val targets = getEffectiveTargets()
        if (targets.any { nextCursor >= it.start && nextCursor < it.end }) {
            val nextStart = if (selectionStart == cursor) nextCursor else selectionStart
            changeSelection(nextStart, nextCursor)
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var greedyJob: kotlinx.coroutines.Job? = null

    fun loadMemory(forceRefresh: Boolean = false) {
        if (AppContainer.isOfflineSession) return
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val targets = getEffectiveTargets()
        if (targets.isEmpty()) return
        
        val remoteTargets = targets.filter { it.localData == null }
        if (remoteTargets.isEmpty() || pid == null) return

        if (!forceRefresh) {
            val viewAddr = getAddressForRow(scrollPosition)
            val viewPage = (viewAddr / pageSize) * pageSize
            if (memoryCache.containsKey(viewPage)) {
                return
            }
            if (!isGreedyLoading) {
                startGreedyLoader()
            }
            return
        }

        val totalRows = getTotalRows()
        val neededPages = LinkedHashSet<Long>()
        for (r in 0 until visibleRowsCount) {
            val row = scrollPosition + r
            if (row in 0 until totalRows) {
                val rowAddr = getAddressForRow(row)
                neededPages.add((rowAddr / pageSize) * pageSize)
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for (page in neededPages) {
                        if (!isActive) break
                        loadPage(pid, page, remoteTargets)
                    }
                }
            } finally {
            }
        }
    }

    private suspend fun loadPage(pid: Int?, page: Long, targets: List<MemoryRange>, skipChangeTracking: Boolean = false): Boolean {
        try {
            val overlappingMaps = targets.filter { it.localData == null && it.start < page + pageSize && it.end > page }
            if (overlappingMaps.isEmpty() || pid == null) return false

            val pageData = memoryCache[page]?.copyOf() ?: ByteArray(pageSize)
            var hasData = false

            for (map in overlappingMaps) {
                val readStart = maxOf(page, map.start)
                val readEnd = minOf(page + pageSize, map.end)
                if (readStart < readEnd) {
                    val readLen = (readEnd - readStart).toInt()
                    try {
                        val data = AppContainer.clientAdapter.client.readMemory(pid, readStart, readLen)
                        if (data.isNotEmpty()) {
                            val destOffset = (readStart - page).toInt()
                            withContext(NonCancellable) {
                                if (!skipChangeTracking) {
                                    val oldData = memoryCache[page]
                                    if (oldData != null) {
                                        var changedCount = 0
                                        for (i in 0 until minOf(data.size, pageSize - destOffset)) {
                                            val absAddr = readStart + i
                                            val oldVal = oldData[destOffset + i]
                                            val newVal = data[i]
                                            if (newVal != oldVal) {
                                                changedBytes[absAddr] = System.currentTimeMillis()
                                                changedCount++
                                                if (changedCount <= 5) {
                                                    AppContainer.debuggerUseCase.log(
                                                        "MEMORY",
                                                        "Value at 0x${absAddr.toString(16).uppercase()} changed: 0x${(oldVal.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()} -> 0x${(newVal.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()}",
                                                        com.osr.ps5debugger.domain.model.LogEntry.Level.INFO
                                                    )
                                                }
                                            }
                                        }
                                        if (changedBytes.size > 2000) {
                                            val cutoff = System.currentTimeMillis() - 5000L
                                            val expired = changedBytes.filter { it.value < cutoff }.keys
                                            expired.forEach { changedBytes.remove(it) }
                                        }
                                    }
                                }

                                val copyLen = minOf(data.size, pageSize - destOffset)
                                if (copyLen > 0) {
                                    System.arraycopy(data, 0, pageData, destOffset, copyLen)
                                }
                                hasData = true
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (hasData) {
                withContext(Dispatchers.Main) {
                    memoryCache[page] = pageData
                    pruneCacheIfNeeded()
                }
            }
            return hasData
        } catch (_: Exception) {
            return false
        }
    }

    fun startGreedyLoader() {
        greedyJob?.cancel()
        if (AppContainer.isOfflineSession) {
            loadingProgress = 1f
            isCurrentTargetReady = true
            isHexComplete = true
            isGreedyLoading = false
            return
        }
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        val allOpenTargets = getAllOpenTargets()
        if (allOpenTargets.isEmpty()) {
            totalPagesCount = 0
            loadedPagesCount = 0
            loadingProgress = 1f
            isCurrentTargetReady = true
            isHexComplete = true
            isGreedyLoading = false
            return
        }

        val allLocal = allOpenTargets.all { it.localData != null }
        if (allLocal) {
            totalPagesCount = 1
            loadedPagesCount = 1
            loadingProgress = 1f
            isCurrentTargetReady = true
            isHexComplete = true
            isGreedyLoading = false
            return
        }

        val currentEffective = getEffectiveTargets()
        val currentIsLocal = currentEffective.isNotEmpty() && currentEffective.all { it.localData != null }

        if (pid == null) {
            isCurrentTargetReady = currentIsLocal
            isHexComplete = allLocal
            loadingProgress = if (allLocal || currentIsLocal) 1f else 0f
            isGreedyLoading = false
            return
        }

        // Only load readable subranges for the active memory region.
        // We strictly ignore background/inactive tabs to prevent network bottlenecks.
        val remoteTargetsToLoad = currentEffective.filter { 
            it.localData == null && it.end > it.start && (it.protections == 0 || (it.protections and 1) != 0)
        }

        if (remoteTargetsToLoad.isEmpty()) {
            loadingProgress = 1f
            isCurrentTargetReady = true
            isHexComplete = true
            isGreedyLoading = false
            return
        }

        val totalRemoteBytes = currentEffective.filter { it.localData == null && it.end > it.start }.sumOf { it.end - it.start }
        val totalToLoad = totalRemoteBytes

        val regionKey = currentEffective.joinToString(";") { "${it.start}-${it.end}" }
        currentTargetRegionKey = regionKey

        val regionProgress = AppContainer.hexProgressCache.getOrPut(regionKey) {
            AppContainer.HexRegionProgress()
        }

        if (regionProgress.isComplete) {
            loadingProgress = 1f
            isHexComplete = true
            isCurrentTargetReady = true
            isGreedyLoading = false
            return
        }

        // Restore current state from persistent cache
        loadingProgress = regionProgress.progress
        loadedBytes = regionProgress.loadedBytes
        isHexComplete = regionProgress.isComplete
        isCurrentTargetReady = regionProgress.isComplete || regionProgress.progress > 0f
        isGreedyLoading = true

        greedyJob = scope.launch(Dispatchers.IO) {
            try {
                val chunkSize = 1024 * 1024L // 1MB chunks for high throughput memory streaming
                val activeTargetStarts = currentEffective.map { it.start }.toSet()
                var lastProgressUpdateTime = 0L
                var lastReportedProgress = regionProgress.progress

                suspend fun updateProgress(force: Boolean = false) {
                    val now = System.currentTimeMillis()
                    val progress = if (totalRemoteBytes > 0) (regionProgress.loadedBytes.toFloat() / totalRemoteBytes.toFloat()).coerceIn(0f, 1f) else 1f
                    regionProgress.progress = progress
                    if (force || now - lastProgressUpdateTime >= 100L || progress - lastReportedProgress >= 0.02f) {
                        lastProgressUpdateTime = now
                        lastReportedProgress = progress
                        withContext(Dispatchers.Main) {
                            loadedBytes = regionProgress.loadedBytes
                            loadingProgress = progress
                            if (progress >= 1f) {
                                regionProgress.isComplete = true
                                isHexComplete = true
                                isCurrentTargetReady = true
                            }
                        }
                    }
                }

                suspend fun loadChunkAt(map: MemoryRange, chunkStart: Long, mapProgress: AppContainer.HexRegionProgress = regionProgress): Boolean {
                    val chunkEnd = minOf(chunkStart + chunkSize, map.end)
                    val readLen = (chunkEnd - chunkStart).toInt()
                    if (readLen <= 0) {
                        mapProgress.completedChunks.add(chunkStart)
                        return false
                    }

                    // If connection dropped, wait patiently for background auto-reconnect without losing progress
                    while (isActive && (!AppContainer.debuggerUseCase.isConnected.value || !AppContainer.clientAdapter.isConnected)) {
                        delay(1000)
                    }
                    if (!isActive) return false

                    val chunkPages = mutableMapOf<Long, ByteArray>()
                    try {
                        val data = AppContainer.clientAdapter.client.readMemory(pid, chunkStart, readLen)
                        if (data.isNotEmpty()) {
                            var pageAddr = (chunkStart / pageSize) * pageSize
                            while (pageAddr < chunkEnd) {
                                val copyStart = maxOf(pageAddr, chunkStart)
                                val copyEnd = minOf(pageAddr + pageSize, chunkEnd)
                                val srcOffset = (copyStart - chunkStart).toInt()
                                val destOffset = (copyStart - pageAddr).toInt()
                                val copyLen = (copyEnd - copyStart).toInt()
                                if (copyLen > 0 && srcOffset + copyLen <= data.size) {
                                    val pageData = chunkPages[pageAddr]
                                        ?: (if (copyLen == pageSize && destOffset == 0) ByteArray(pageSize) else memoryCache[pageAddr]?.copyOf() ?: ByteArray(pageSize))
                                    System.arraycopy(data, srcOffset, pageData, destOffset, copyLen)
                                    chunkPages[pageAddr] = pageData
                                }
                                pageAddr += pageSize
                            }
                        }
                    } catch (_: Exception) {
                        // If this error occurred because connection was lost, wait for reconnect and retry this chunk!
                        if (!AppContainer.clientAdapter.isConnected) {
                            while (isActive && (!AppContainer.debuggerUseCase.isConnected.value || !AppContainer.clientAdapter.isConnected)) {
                                delay(1000)
                            }
                            if (!isActive) return false
                            return loadChunkAt(map, chunkStart)
                        }

                        var fallbackPage = (chunkStart / pageSize) * pageSize
                        while (fallbackPage < chunkEnd) {
                            if (!isActive) return false
                            val fStart = maxOf(fallbackPage, chunkStart)
                            val fEnd = minOf(fallbackPage + pageSize, chunkEnd)
                            val fLen = (fEnd - fStart).toInt()
                            if (fLen > 0) {
                                val pageData = chunkPages[fallbackPage]
                                    ?: memoryCache[fallbackPage]?.copyOf()
                                    ?: ByteArray(pageSize)
                                try {
                                    val pData = AppContainer.clientAdapter.client.readMemory(pid, fStart, fLen)
                                    if (pData.isNotEmpty()) {
                                        System.arraycopy(pData, 0, pageData, (fStart - fallbackPage).toInt(), pData.size)
                                        chunkPages[fallbackPage] = pageData
                                    }
                                } catch (_: Exception) {
                                    if (!AppContainer.clientAdapter.isConnected) {
                                        while (isActive && (!AppContainer.debuggerUseCase.isConnected.value || !AppContainer.clientAdapter.isConnected)) {
                                            delay(1000)
                                        }
                                        if (!isActive) return false
                                        return loadChunkAt(map, chunkStart, mapProgress)
                                    }
                                    if (!memoryCache.containsKey(fallbackPage)) {
                                        chunkPages[fallbackPage] = pageData
                                    }
                                }
                            }
                            fallbackPage += pageSize
                        }
                    }

                    if (mapProgress.completedChunks.add(chunkStart)) {
                        mapProgress.loadedBytes += readLen
                    }

                    if (chunkPages.isNotEmpty()) {
                        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                            for ((pAddr, pData) in chunkPages) {
                                memoryCache[pAddr] = pData
                            }
                        }
                        if (memoryCache.size > 8192) {
                            pruneCacheIfNeeded()
                        }
                    }
                    return true
                }

                suspend fun checkAndLoadViewport(): Boolean {
                    val currentView = getAddressForRow(scrollPosition)
                    val activeMapForView = remoteTargetsToLoad.firstOrNull { currentView >= it.start && currentView < it.end } ?: return false
                    val viewChunkStart = ((currentView - activeMapForView.start) / chunkSize) * chunkSize + activeMapForView.start
                    val viewPage = (currentView / pageSize) * pageSize
                    var didLoad = false
                    if (!memoryCache.containsKey(viewPage) || !regionProgress.completedChunks.contains(viewChunkStart)) {
                        val chunkEnd = minOf(viewChunkStart + chunkSize, activeMapForView.end)
                        val rLen = (chunkEnd - viewChunkStart).toInt()
                        if (regionProgress.completedChunks.remove(viewChunkStart)) {
                            regionProgress.loadedBytes = maxOf(0L, regionProgress.loadedBytes - rLen)
                        }
                        if (loadChunkAt(activeMapForView, viewChunkStart)) {
                            didLoad = true
                            withContext(Dispatchers.Main) {
                                isCurrentTargetReady = true
                            }
                            val nextViewChunk = viewChunkStart + chunkSize
                            val nextPage = viewPage + pageSize
                            if (nextViewChunk < activeMapForView.end && (!memoryCache.containsKey(nextPage) || !regionProgress.completedChunks.contains(nextViewChunk))) {
                                val nextEnd = minOf(nextViewChunk + chunkSize, activeMapForView.end)
                                val nextLen = (nextEnd - nextViewChunk).toInt()
                                if (regionProgress.completedChunks.remove(nextViewChunk)) {
                                    regionProgress.loadedBytes = maxOf(0L, regionProgress.loadedBytes - nextLen)
                                }
                                loadChunkAt(activeMapForView, nextViewChunk, regionProgress)
                            }
                            val prevViewChunk = viewChunkStart - chunkSize
                            val prevPage = viewPage - pageSize
                            if (prevViewChunk >= activeMapForView.start && (!memoryCache.containsKey(prevPage) || !regionProgress.completedChunks.contains(prevViewChunk))) {
                                val prevEnd = minOf(prevViewChunk + chunkSize, activeMapForView.end)
                                val prevLen = (prevEnd - prevViewChunk).toInt()
                                if (regionProgress.completedChunks.remove(prevViewChunk)) {
                                    regionProgress.loadedBytes = maxOf(0L, regionProgress.loadedBytes - prevLen)
                                }
                                loadChunkAt(activeMapForView, prevViewChunk, regionProgress)
                            }
                            updateProgress()
                        }
                    }
                    return didLoad
                }

                // 1. Initial viewport priority load: user sees bytes right away
                checkAndLoadViewport()
                updateProgress(force = true)

                // 2. Sequential pass through the active remote targets
                for (map in remoteTargetsToLoad) {
                    if (!isActive) break
                    
                    val mapKey = "${map.start}-${map.end}"
                    val isCurrentMap = currentEffective.any { it.start == map.start }
                    val mapProgress = if (isCurrentMap) regionProgress else AppContainer.hexProgressCache.getOrPut(mapKey) { AppContainer.HexRegionProgress() }
                    val mapBytes = map.end - map.start
                    
                    var chunk = map.start
                    while (chunk < map.end) {
                        if (!isActive) break

                        // Before reading each chunk, satisfy any new scroll viewport location
                        checkAndLoadViewport()

                        // Only load chunk if not already completed
                        if (!mapProgress.completedChunks.contains(chunk)) {
                            loadChunkAt(map, chunk, mapProgress)
                            
                            if (isCurrentMap) {
                                updateProgress(force = false)
                                if (map.start in activeTargetStarts) {
                                    withContext(Dispatchers.Main) {
                                        isCurrentTargetReady = true
                                    }
                                }
                            } else {
                                val prog = if (mapBytes > 0) (mapProgress.loadedBytes.toFloat() / mapBytes.toFloat()).coerceIn(0f, 1f) else 1f
                                mapProgress.progress = prog
                                if (prog >= 1f) mapProgress.isComplete = true
                            }
                            delay(15)
                        }

                        chunk += chunkSize
                    }
                    if (!isCurrentMap) {
                        mapProgress.progress = 1f
                        mapProgress.isComplete = true
                    }
                }

                updateProgress(force = true)
                withContext(Dispatchers.Main) {
                    regionProgress.isComplete = true
                    regionProgress.progress = 1f
                    loadingProgress = 1f
                    isCurrentTargetReady = true
                    isHexComplete = true
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
        
        val sorted = getEffectiveTargets()
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
        
        val targets = getEffectiveTargets()
        val localMap = targets.firstOrNull { it.localData != null && addr >= it.start && addr < it.end }
            ?: (if (activeMap?.localData != null && addr >= activeMap!!.start && addr < activeMap!!.end) activeMap else null)
            ?: activeMaps.firstOrNull { it.localData != null && addr >= it.start && addr < it.end }
        if (localMap != null) {
            val offset = (addr - localMap.start).toInt()
            val data = localMap.localData
            if (data != null && offset >= 0 && offset < data.size) {
                return data[offset]
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
        
        val targets = getEffectiveTargets()
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
    
    val activeMapsSnapshot = activeMaps.toList()
    LaunchedEffect(activeMap, activeMapsSnapshot, selectionStartParam, selectionEndParam, jumpToAddress) {
        val mapsChanged = state.activeMap != activeMap || state.activeMaps.size != activeMaps.size || !state.activeMaps.containsAll(activeMaps)
        state.activeMap = activeMap
        if (mapsChanged) {
            state.activeMaps.clear()
            state.activeMaps.addAll(activeMaps)
            val targets = state.getEffectiveTargets()
            state.startAddress = targets.firstOrNull()?.start ?: 0L
            state.endAddress = targets.lastOrNull()?.end ?: 0L
            state.pendingEdits.clear()
        }
        state.selectionStart = selectionStartParam
        state.selectionEnd = selectionEndParam
        state.onSelectionChanged = onSelectionChanged
    }
    
    return state
}
