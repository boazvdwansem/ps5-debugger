package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.hex.*
import com.osr.ps5debugger.util.copyToClipboard
import com.osr.ps5debugger.util.getFromClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HexViewer(
    state: HexState,
    modifier: Modifier = Modifier,
    showAddress: Boolean = true,
    columns: Int = 16
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val isMobile = maxWidth < 600.dp
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(columns) {
            if (state.bytesPerRow != columns) {
                state.bytesPerRow = columns
                state.memoryCache.clear()
                state.updateScrollPosition(0L)
            }
        }

        LaunchedEffect(maxHeight, density, state) {
            // Account for region banner (24dp), header (26dp), and inspector (28dp)
            val nonGridHeight = 78.dp
            state.visibleRowsCount = ((maxHeight - nonGridHeight) / HexLayoutMetrics.rowHeightDp).toInt().coerceAtLeast(1) + 1
        }

        LaunchedEffect(state.scrollPosition, state.bytesPerRow, state.startAddress, state.endAddress, state.visibleRowsCount) {
            state.loadMemory()
        }

        // NAVIGATION SYNC
        val currentJumpAddr = state.currentJumpAddress
        var lastJumpAddr by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(currentJumpAddr) {
            if (currentJumpAddr != null && currentJumpAddr != lastJumpAddr) {
                lastJumpAddr = currentJumpAddr
                val targetRow = state.getRowForAddress(currentJumpAddr)
                state.updateScrollPosition(targetRow.coerceIn(0L, state.getMaxScrollPosition()))
                state.onSelectionChanged?.invoke(currentJumpAddr, currentJumpAddr)
                state.goToAddressText = currentJumpAddr.toString(16).uppercase()
            }
        }

        val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()

        // AUTO-POLLING LOOP
        LaunchedEffect(state.refreshRateMs, isConnected, state.activeMap, state.activeMaps.size) {
            if (state.refreshRateMs <= 0) return@LaunchedEffect
            while (true) {
                delay(state.refreshRateMs)
                if (isConnected || (state.activeMap?.localData != null)) {
                    state.loadMemory(forceRefresh = true)
                }
            }
        }

        // CHANGE HIGHLIGHT CLEANUP
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val toRemove = state.changedBytes.filter { now - it.value > 5000 }.keys
                toRemove.forEach { state.changedBytes.remove(it) }
            }
        }

        // Hidden TextField for keyboard input
        Box(Modifier.size(1.dp).alpha(0f)) {
            BasicTextField(
                value = state.keyboardInputText,
                onValueChange = { text ->
                    if (text.isNotEmpty()) {
                        val char = text.last()
                        if (state.clickedArea == ClickedArea.ASCII && state.isEditingUnlocked) {
                            if (char.code in 32..126) {
                                state.selectionEnd?.let { cursor ->
                                    state.pendingEdits[cursor] = char.code.toByte()
                                    state.advanceCursor()
                                }
                            }
                        } else {
                            state.handleHexInput(char)
                        }
                    }
                    state.keyboardInputText = ""
                },
                modifier = Modifier.focusRequester(state.keyboardFocusRequester).onKeyEvent(state::handleKeyEvent)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(state.focusRequester)
                .focusable()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!isMobile) try { state.focusRequester.requestFocus() } catch (_: Exception) {}
                    })
                }
                .onKeyEvent(state::handleKeyEvent)
        ) {
            if (state.activeMap == null && state.activeMaps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Select a Process and Virtual Memory Map to view hex memory",
                        color = PS5ThemeColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                // Sleek Region status banner
                val targets = if (state.activeMap != null) listOf(state.activeMap!!) else state.activeMaps.toList()
                val currentAddress = state.selectionEnd ?: state.getAddressForRow(state.scrollPosition)
                val activeRegion = targets.firstOrNull { currentAddress >= it.start && currentAddress < it.end }

                if (activeRegion != null) {
                    HexRegionBanner(activeRegion)
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    HexGrid(state, isMobile, showAddress, modifier = Modifier.fillMaxSize())

                    if (state.isLoading && state.memoryCache.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
                                Text(
                                    "Loading Memory...",
                                    color = PS5ThemeColors.AccentCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Compact Data Inspector at bottom
                HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.4f))
                HexDataInspector(state)
            }
        }
    }
}

@Composable
private fun HexRegionBanner(region: MemoryRange) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "REGION",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = PS5ThemeColors.AccentCyan,
            letterSpacing = 0.5.sp
        )
        Text(
            text = if (region.name.isEmpty()) "unnamed" else region.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PS5ThemeColors.TextMain,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "[0x${region.start.toString(16).uppercase()} - 0x${region.end.toString(16).uppercase()}]",
            fontSize = 11.sp,
            color = PS5ThemeColors.TextMuted,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = region.getProtString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (region.getProtString().contains("x")) PS5ThemeColors.AccentAmber else PS5ThemeColors.TextMuted,
            fontFamily = FontFamily.Monospace
        )
    }
    HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.4f))
}

@Composable
private fun HexGrid(state: HexState, isMobile: Boolean, showAddress: Boolean, modifier: Modifier = Modifier) {
    val horizontalScrollState = rememberScrollState()
    val gridWidth = HexLayoutMetrics.calculateTotalGridWidthDp(isMobile, showAddress, state.bytesPerRow)

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .widthIn(max = gridWidth)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState, enabled = !state.isMouseDown)
        ) {
            Column(modifier = Modifier.width(gridWidth).fillMaxHeight()) {
                HexGridHeader(state, isMobile, showAddress)
                HexGridBody(state, isMobile, showAddress, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun HexGridHeader(state: HexState, isMobile: Boolean, showAddress: Boolean) {
    val addressWidthDp = HexLayoutMetrics.addressWidthDp(isMobile, showAddress)
    val hexCellWidthDp = HexLayoutMetrics.hexCellWidthDp(isMobile)
    val midGapDp = HexLayoutMetrics.midGapDp(isMobile, state.bytesPerRow)
    val spacerAddressToHexDp = HexLayoutMetrics.spacerAddressToHexDp(isMobile, showAddress)
    val spacerHexToAsciiDp = HexLayoutMetrics.spacerHexToAsciiDp(isMobile)

    // Current cursor column to highlight in header
    val cursorCol = state.selectionEnd?.let { cursor ->
        val rowAddr = state.getAddressForRow(state.getRowForAddress(cursor))
        val col = (cursor - rowAddr).toInt()
        if (col in 0 until state.bytesPerRow) col else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HexLayoutMetrics.headerHeightDp)
            .background(PS5ThemeColors.SecondaryBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAddress) {
            Box(
                modifier = Modifier
                    .width(addressWidthDp)
                    .padding(start = if (isMobile) 4.dp else 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "OFFSET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMuted,
                    letterSpacing = 1.sp
                )
            }

            // Gutter divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
            )

            Spacer(Modifier.width(spacerAddressToHexDp - 1.dp))
        }

        // Hex column headers: 00 01 02 ... 0F
        Row(
            modifier = Modifier.width(HexLayoutMetrics.calculateHexWidthDp(isMobile, state.bytesPerRow)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (col in 0 until state.bytesPerRow) {
                if (col == 8 && state.bytesPerRow >= 16) {
                    Spacer(Modifier.width(midGapDp))
                }
                val isCurrentCol = cursorCol == col
                Box(
                    modifier = Modifier
                        .width(hexCellWidthDp)
                        .fillMaxHeight()
                        .background(if (isCurrentCol) PS5ThemeColors.AccentCyan.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02X", col),
                        fontSize = if (isMobile) 10.sp else 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isCurrentCol) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrentCol) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted
                    )
                }
            }
        }

        // Divider between Hex and ASCII
        Spacer(Modifier.width(spacerHexToAsciiDp / 2))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width((spacerHexToAsciiDp / 2) - 1.dp))

        // ASCII header
        Box(
            modifier = Modifier
                .width(HexLayoutMetrics.calculateAsciiWidthDp(isMobile, state.bytesPerRow))
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "DECODED TEXT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PS5ThemeColors.TextMuted,
                letterSpacing = 0.5.sp
            )
        }
    }
    HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
}

@Composable
private fun HexGridBody(state: HexState, isMobile: Boolean, showAddress: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = state.focusRequester
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state, density) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            val position = change.position

                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val delta = change.scrollDelta.y
                                    val rows = if (delta > 0f) 3L else if (delta < 0f) -3L else 0L
                                    state.updateScrollPosition((state.scrollPosition + rows).coerceIn(0L, state.getMaxScrollPosition()))
                                }
                                PointerEventType.Press -> {
                                    state.isMouseDown = true
                                    if (!isMobile) {
                                        try { focusRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                    state.touchStartPos = position
                                    state.touchStartScroll = state.scrollPosition
                                    state.isDraggingToScroll = false
                                    state.isDraggingToSelect = false
                                    state.isLongPressSelection = false
                                    state.hasTriggeredLongPress = false
                                    state.isSecondaryClick = event.buttons.isSecondaryPressed

                                    val clickResult = state.getAddressAtOffset(position.x, position.y, density, isMobile, showAddress)

                                    if (isMobile) {
                                        state.isLongPressActive = true
                                        coroutineScope.launch {
                                            delay(500)
                                            if (state.isLongPressActive && state.touchStartPos != null && !state.isDraggingToScroll && !state.isDraggingToSelect) {
                                                if (clickResult != null) {
                                                    state.hasTriggeredLongPress = true
                                                    val (addr, area) = clickResult
                                                    state.clickedArea = area
                                                    state.contextMenuAddr = addr
                                                    state.touchStartAddr = addr
                                                    if (!state.isAddressSelected(addr)) {
                                                        state.changeSelection(addr, addr)
                                                    }
                                                    state.contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                                    state.showContextMenu = true
                                                }
                                                state.isLongPressActive = false
                                            }
                                        }
                                    }

                                    if (state.isSecondaryClick) {
                                        if (clickResult != null) {
                                            val (addr, area) = clickResult
                                            state.clickedArea = area
                                            state.contextMenuAddr = addr
                                            state.touchStartAddr = addr
                                            if (!state.isAddressSelected(addr)) {
                                                state.changeSelection(addr, addr)
                                            }
                                            state.contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                            state.showContextMenu = true
                                        }
                                    } else if (clickResult != null) {
                                        val (addr, area) = clickResult
                                        state.clickedArea = area
                                        state.touchStartAddr = addr
                                        if (!isMobile && (area == ClickedArea.HEX || area == ClickedArea.ASCII)) {
                                            state.changeSelection(addr, addr)
                                            state.hexInputBuffer = ""
                                        }
                                    }
                                }
                                PointerEventType.Move -> {
                                    val startPos = state.touchStartPos
                                    val startAddr = state.touchStartAddr
                                    if (startPos != null) {
                                        val dragY = position.y - startPos.y
                                        val dragX = position.x - startPos.x
                                        val dragDistance = kotlin.math.sqrt(dragX * dragX + dragY * dragY)
                                        val dragThreshold = if (isMobile) 24f * density else 4f * density

                                        if (dragDistance > dragThreshold) {
                                            state.isLongPressActive = false
                                        }

                                        if (!state.isDraggingToScroll && !state.isDraggingToSelect && dragDistance > dragThreshold) {
                                            val isSelectionArea = state.clickedArea == ClickedArea.HEX || state.clickedArea == ClickedArea.ASCII
                                            if (isSelectionArea && (!isMobile || kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.5f)) {
                                                state.isDraggingToSelect = true
                                            } else {
                                                state.isDraggingToScroll = true
                                            }
                                        }

                                        if (state.isDraggingToSelect && startAddr != null) {
                                            change.consume()
                                            state.getAddressAtOffset(position.x, position.y, density, isMobile, showAddress)?.let { (addr, _) ->
                                                state.changeSelection(startAddr, addr)
                                            }
                                        } else if (state.isDraggingToScroll) {
                                            change.consume()
                                            val rowDelta = (-dragY / (HexLayoutMetrics.rowHeightDp.value * density)).toLong()
                                            state.updateScrollPosition((state.touchStartScroll + rowDelta).coerceIn(0L, state.getMaxScrollPosition()))
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    state.isLongPressActive = false
                                    if (state.touchStartPos != null && !state.isSecondaryClick && !state.isDraggingToScroll && !state.isDraggingToSelect && !state.hasTriggeredLongPress) {
                                        state.getAddressAtOffset(position.x, position.y, density, isMobile, showAddress)?.let { (addr, area) ->
                                            if (area == ClickedArea.HEX || area == ClickedArea.ASCII) {
                                                state.clickedArea = area
                                                state.changeSelection(addr, addr)
                                                state.hexInputBuffer = ""
                                                try { state.focusRequester.requestFocus() } catch (_: Exception) {}

                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - state.lastTapTime < 500) {
                                                    if (isMobile && state.isEditingUnlocked) {
                                                        coroutineScope.launch {
                                                            state.keyboardFocusRequester.requestFocus()
                                                            delay(100)
                                                            keyboardController?.show()
                                                        }
                                                    }
                                                }
                                                state.lastTapTime = currentTime
                                            }
                                        }
                                    }
                                    state.touchStartPos = null
                                    state.touchStartAddr = null
                                    state.isMouseDown = false
                                    state.isDraggingToScroll = false
                                    state.isDraggingToSelect = false
                                    state.isSecondaryClick = false
                                }
                            }
                        }
                    }
                }
        ) {
            val totalRows = if (state.activeMap != null) {
                (state.activeMap!!.end - state.activeMap!!.start + state.bytesPerRow - 1) / state.bytesPerRow
            } else if (state.activeMaps.isNotEmpty()) {
                state.activeMaps.sumOf { (it.end - it.start + state.bytesPerRow - 1) / state.bytesPerRow }
            } else 0L

            for (rowIndex in 0 until state.visibleRowsCount) {
                if (state.scrollPosition + rowIndex >= totalRows) break

                val rowAddress = state.getAddressForRow(state.scrollPosition + rowIndex)
                val pageStart = (rowAddress / state.pageSize) * state.pageSize
                val cachedPage = state.memoryCache[pageStart]
                val offsetInPage = (rowAddress - pageStart).toInt()

                val stableRowBytes = remember(rowAddress, cachedPage, state.activeMap, state.activeMaps.size) {
                    val bytes = ByteArray(state.bytesPerRow)
                    if (cachedPage != null && offsetInPage >= 0 && offsetInPage < cachedPage.size) {
                        val toCopy = minOf(state.bytesPerRow, cachedPage.size - offsetInPage)
                        if (toCopy > 0) {
                            System.arraycopy(cachedPage, offsetInPage, bytes, 0, toCopy)
                        }
                    } else {
                        val targets = if (state.activeMap != null) listOf(state.activeMap!!) else state.activeMaps.toList()
                        val localMap = targets.firstOrNull { it.localData != null && rowAddress >= it.start && rowAddress < it.end }
                        if (localMap != null) {
                            val localOffset = (rowAddress - localMap.start).toInt()
                            val data = localMap.localData
                            if (data != null && localOffset >= 0 && localOffset < data.size) {
                                val toCopy = minOf(state.bytesPerRow, data.size - localOffset)
                                if (toCopy > 0) {
                                    System.arraycopy(data, localOffset, bytes, 0, toCopy)
                                }
                            }
                        }
                    }
                    StableRowBytes(bytes)
                }

                HexRowView(
                    address = rowAddress,
                    stableBytes = stableRowBytes,
                    columns = state.bytesPerRow,
                    selectionMin = if (state.selectionStart != null && state.selectionEnd != null) minOf(state.selectionStart!!, state.selectionEnd!!) else null,
                    selectionMax = if (state.selectionStart != null && state.selectionEnd != null) maxOf(state.selectionStart!!, state.selectionEnd!!) else null,
                    cursorAddress = state.selectionEnd,
                    pendingEdits = state.pendingEdits,
                    changedBytes = state.changedBytes,
                    hexInputBuffer = state.hexInputBuffer,
                    isMobile = isMobile,
                    showAddress = showAddress
                )
            }
        }

        HexScrollbar(state, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp))
        HexContextMenu(state)
    }
}

@Composable
private fun BoxScope.HexScrollbar(state: HexState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val trackHeight = maxHeight

            val totalRows = if (state.activeMap != null) {
                (state.activeMap!!.end - state.activeMap!!.start + state.bytesPerRow - 1) / state.bytesPerRow
            } else if (state.activeMaps.isNotEmpty()) {
                state.activeMaps.sumOf { (it.end - it.start + state.bytesPerRow - 1) / state.bytesPerRow }
            } else 0L

            val thumbHeight = if (totalRows > 0L) {
                val ratio = state.visibleRowsCount.toDouble() / totalRows.toDouble()
                val calcHeight = (trackHeight.value * ratio).dp
                calcHeight.coerceIn(24.dp, trackHeight)
            } else {
                trackHeight
            }

            val maxOffset = trackHeight - thumbHeight
            val maxScroll = state.getMaxScrollPosition()
            val thumbOffset = if (maxScroll > 0L) {
                val ratio = state.scrollPosition.toDouble() / maxScroll.toDouble()
                (maxOffset.value * ratio).dp
            } else {
                0.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state, maxScroll) {
                        detectTapGestures { pressOffset ->
                            if (pressOffset.y < thumbOffset.toPx()) state.updateScrollPosition((state.scrollPosition - state.visibleRowsCount).coerceIn(0L, maxScroll))
                            else if (pressOffset.y > (thumbOffset + thumbHeight).toPx()) state.updateScrollPosition((state.scrollPosition + state.visibleRowsCount).coerceIn(0L, maxScroll))
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .fillMaxWidth()
                        .height(thumbHeight)
                        .background(PS5ThemeColors.AccentCyan.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .pointerInput(state, maxOffset, maxScroll) {
                            detectDragGestures { _, dragAmount ->
                                val maxOffsetPx = maxOffset.toPx()
                                if (maxOffsetPx > 0f) {
                                    val deltaRows = (dragAmount.y / maxOffsetPx * maxScroll).toLong()
                                    state.updateScrollPosition((state.scrollPosition + deltaRows).coerceIn(0L, maxScroll))
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun HexContextMenu(state: HexState) {
    DropdownMenu(
        expanded = state.showContextMenu,
        onDismissRequest = { state.showContextMenu = false },
        offset = state.contextMenuOffset
    ) {
        if (state.clickedArea == ClickedArea.ADDRESS) {
            DropdownMenuItem(
                text = { Text("Copy Address") },
                onClick = {
                    state.contextMenuAddr?.let { copyToClipboard(String.format("0x%016X", it)) }
                    state.showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Add to Watchlist") },
                onClick = {
                    state.contextMenuAddr?.let { AppContainer.debuggerUseCase.addToWatchlist(it, "Int32", 4) }
                    state.showContextMenu = false
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Add Selection to Watchlist") },
                onClick = {
                    val start = (if (state.selectionStart != null && state.selectionEnd != null) minOf(state.selectionStart!!, state.selectionEnd!!) else state.contextMenuAddr) ?: return@DropdownMenuItem
                    val len = if (state.selectionStart != null && state.selectionEnd != null) (maxOf(state.selectionStart!!, state.selectionEnd!!) - start + 1).toInt() else 4
                    AppContainer.debuggerUseCase.addToWatchlist(start, "ByteArray", len)
                    state.showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Copy Hex") },
                onClick = {
                    copyToClipboard(state.getSelectedBytesText())
                    state.showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Copy C-Array") },
                onClick = {
                    copyToClipboard(state.getSelectedCArrayText())
                    state.showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Copy ASCII") },
                onClick = {
                    copyToClipboard(state.getSelectedAsciiText())
                    state.showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Paste Hex") },
                onClick = {
                    val clipboardText = getFromClipboard()
                    if (clipboardText.isNotEmpty()) {
                        state.pasteHex(clipboardText, state.contextMenuAddr)
                    }
                    state.showContextMenu = false
                },
                enabled = state.isEditingUnlocked
            )
            DropdownMenuItem(
                text = { Text("Add to Cheats", color = PS5ThemeColors.AccentCyan) },
                onClick = {
                    val procInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                    val titleId = procInfo?.titleId ?: state.activeMap?.titleId ?: "Unknown"

                    val start = if (state.selectionStart != null && state.selectionEnd != null) minOf(state.selectionStart!!, state.selectionEnd!!) else state.contextMenuAddr ?: 0L
                    val end = if (state.selectionStart != null && state.selectionEnd != null) maxOf(state.selectionStart!!, state.selectionEnd!!) else start

                    val originalBytes = buildString {
                        for (addr in start..end) {
                            append("%02X".format(state.getByteAt(addr)))
                        }
                    }

                    AppContainer.onCreateCheatRequested?.invoke(
                        com.osr.ps5debugger.domain.model.Cheat(
                            id = "",
                            name = "New Cheat",
                            type = com.osr.ps5debugger.domain.model.CheatType.Toggle,
                            address = start,
                            hexOnValue = "",
                            hexOffValue = originalBytes,
                            titleId = titleId
                        )
                    )
                    state.showContextMenu = false
                }
            )
        }
    }
}
