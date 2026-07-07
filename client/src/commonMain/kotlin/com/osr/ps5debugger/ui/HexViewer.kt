package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.components.Tooltip
import com.osr.ps5debugger.ui.hex.*
import com.osr.ps5debugger.util.copyToClipboard
import com.osr.ps5debugger.util.getFromClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HexViewer(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    showAddress: Boolean = true,
    selectionStartParam: Long? = null,
    selectionEndParam: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val state = rememberHexState(activeMap, activeMaps, jumpToAddress, selectionStartParam, selectionEndParam, onSelectionChanged)
        val density = LocalDensity.current.density
        val isMobile = maxWidth < 600.dp
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(isMobile) {
            state.bytesPerRow = if (isMobile) 8 else 16
        }

        LaunchedEffect(maxHeight, density, state) {
            val nonGridHeight = 100.dp
            state.visibleRowsCount = ((maxHeight - nonGridHeight) / 24.dp).toInt().coerceAtLeast(1)
        }

        LaunchedEffect(state.selectionStart, state.selectionEnd) {
            state.onSelectionChanged?.invoke(state.selectionStart, state.selectionEnd)
        }

        LaunchedEffect(state.scrollPosition, state.bytesPerRow, state.startAddress, state.endAddress, state.visibleRowsCount) {
            state.loadMemory()
        }

        LaunchedEffect(activeMap, activeMaps.toList(), jumpToAddress, state.bytesPerRow, state.visibleRowsCount) {
            val targets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
            if (targets.isNotEmpty()) {
                val minStart = targets.minOf { it.start }
                val maxEnd = targets.maxOf { it.end }
                state.startAddress = minStart
                state.endAddress = maxEnd
                
                val currentTarget = activeMap ?: targets.first()
                if (jumpToAddress != null && jumpToAddress >= currentTarget.start && jumpToAddress < currentTarget.end) {
                    val targetRow = (jumpToAddress - minStart) / state.bytesPerRow
                    state.updateScrollPosition(targetRow.coerceIn(0L, state.getMaxScrollPosition()))
                    state.selectionStart = jumpToAddress
                    state.selectionEnd = jumpToAddress
                    state.goToAddressText = jumpToAddress.toString(16).uppercase()
                }
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
                                    if (cursor + 1 < state.endAddress) {
                                        state.selectionEnd = cursor + 1
                                        if (state.selectionStart == cursor) state.selectionStart = cursor + 1
                                    }
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
            modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = if (isMobile) 0.dp else 8.dp)
                .focusRequester(state.focusRequester).focusable()
                .pointerInput(Unit) { detectTapGestures(onTap = { if (!isMobile) try { state.focusRequester.requestFocus() } catch (_: Exception) {} }) }
                .onKeyEvent(state::handleKeyEvent)
        ) {
            HexToolbar(state, isMobile)
            
            if (activeMap == null && state.activeMaps.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Select a Process and Virtual Memory Map to view hex memory")
                }
            } else {
                HexGrid(state, isMobile, showAddress, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun HexToolbar(state: HexState, isMobile: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
    val client = AppContainer.clientAdapter.client

    if (isMobile) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.goToAddressText,
                    onValueChange = { state.goToAddressText = it },
                    label = { Text("Go to Address (Hex)") },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val addr = state.goToAddressText.trim().toLongOrNull(16)
                        if (addr != null && addr in state.startAddress..state.endAddress) {
                            val rowIdx = (addr - state.startAddress) / state.bytesPerRow
                            state.updateScrollPosition(rowIdx.coerceIn(0L, state.getMaxScrollPosition()))
                        }
                    },
                    modifier = Modifier.height(40.dp)
                ) { Text("Go") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditActions(state)
                }
                ColumnChips(state)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.goToAddressText,
                onValueChange = { state.goToAddressText = it },
                label = { Text("Go to Address (Hex)") },
                modifier = Modifier.width(200.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true
            )
            Button(onClick = {
                val addr = state.goToAddressText.trim().toLongOrNull(16)
                if (addr != null && addr in state.startAddress..state.endAddress) {
                    val rowIdx = (addr - state.startAddress) / state.bytesPerRow
                    state.updateScrollPosition(rowIdx.coerceIn(0L, state.getMaxScrollPosition()))
                }
            }) { Text("Go") }
            Spacer(Modifier.weight(1f))
            EditActions(state)
            VerticalDivider(modifier = Modifier.height(32.dp), color = PS5ThemeColors.BorderColor)
            Text("Columns:")
            ColumnChips(state)
        }
    }
}

@Composable
private fun EditActions(state: HexState) {
    val coroutineScope = rememberCoroutineScope()
    val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
    val client = AppContainer.clientAdapter.client

    Tooltip(if (state.isEditingUnlocked) "Lock Editing" else "Unlock Editing") {
        IconButton(onClick = { state.isEditingUnlocked = !state.isEditingUnlocked }) {
            Icon(if (state.isEditingUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, null)
        }
    }
    Tooltip("Undo changes") {
        IconButton(onClick = { state.pendingEdits.clear() }, enabled = state.pendingEdits.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Undo, null)
        }
    }
    Tooltip("Inject overrides to memory") {
        Button(
            onClick = {
                if (pid != null) {
                    coroutineScope.launch {
                        state.pendingEdits.forEach { (addr, b) ->
                            client.writeMemory(pid, addr, byteArrayOf(b))
                        }
                        state.pendingEdits.clear()
                        state.loadMemory()
                    }
                }
            },
            enabled = state.isEditingUnlocked && state.pendingEdits.isNotEmpty()
        ) { Text("Inject (${state.pendingEdits.size})") }
    }
}

@Composable
private fun ColumnChips(state: HexState) {
    Row {
        listOf(8, 16, 32).forEach { cols ->
            FilterChip(
                selected = state.bytesPerRow == cols,
                onClick = {
                    state.bytesPerRow = cols
                    state.memoryCache.clear()
                    state.selectionStart = null
                    state.selectionEnd = null
                    state.pendingEdits.clear()
                    state.updateScrollPosition(0L)
                },
                label = { Text("$cols") },
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun HexGrid(state: HexState, isMobile: Boolean, showAddress: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val horizontalScrollState = rememberScrollState()
    
    val addressWidthDp = if (isMobile) 80.dp else 120.dp
    val hexCellWidthDp = if (isMobile) 20.dp else 24.dp
    val asciiCellWidthDp = if (isMobile) 9.dp else 12.dp
    
    val gridWidth = (addressWidthDp + (state.bytesPerRow * hexCellWidthDp.value).dp + (state.bytesPerRow * asciiCellWidthDp.value).dp + 64.dp)

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = gridWidth).fillMaxHeight().horizontalScroll(horizontalScrollState, enabled = !state.isMouseDown)) {
            Column(modifier = Modifier.width(gridWidth).fillMaxHeight()) {
                HexGridHeader(state, isMobile, showAddress)
                HexGridBody(state, isMobile, showAddress, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun HexGridHeader(state: HexState, isMobile: Boolean, showAddress: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(vertical = 4.dp)) {
        if (showAddress) {
            Text("Address", modifier = Modifier.width(if (isMobile) 80.dp else 120.dp).padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
        Text("Hexadecimal Data", modifier = Modifier.width((state.bytesPerRow * (if (isMobile) 20 else 24)).dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("ASCII", modifier = Modifier.width((state.bytesPerRow * (if (isMobile) 9 else 12)).dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun HexGridBody(state: HexState, isMobile: Boolean, showAddress: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = state.focusRequester
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().pointerInput(state, density) {
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
                                    try {
                                        focusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                                state.touchStartPos = position
                                state.touchStartScroll = state.scrollPosition
                                state.isDraggingToScroll = false
                                state.isDraggingToSelect = false
                                state.isLongPressSelection = false
                                state.isSecondaryClick = event.buttons.isSecondaryPressed
                                
                                val clickResult = state.getAddressAtOffset(position.x, position.y, density, isMobile)
                                if (state.isSecondaryClick) {
                                    if (clickResult != null) {
                                        val (addr, area) = clickResult
                                        state.clickedArea = area
                                        state.contextMenuAddr = addr
                                        if (!state.isAddressSelected(addr)) {
                                            state.selectionStart = addr
                                            state.selectionEnd = addr
                                        }
                                        state.contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                        state.showContextMenu = true
                                    }
                                } else if (clickResult != null) {
                                    val (addr, area) = clickResult
                                    if (!isMobile && (area == ClickedArea.HEX || area == ClickedArea.ASCII)) {
                                        state.clickedArea = area
                                        state.selectionStart = addr
                                        state.selectionEnd = addr
                                        state.hexInputBuffer = ""
                                    }
                                }
                            }
                            PointerEventType.Move -> {
                                val startPos = state.touchStartPos
                                if (startPos != null) {
                                    val dragY = position.y - startPos.y
                                    val dragX = position.x - startPos.x
                                    val dragDistance = kotlin.math.sqrt(dragX * dragX + dragY * dragY)
                                    val dragThreshold = if (isMobile) 24f * density else 8f * density
                                    
                                    if (!state.isDraggingToScroll && !state.isDraggingToSelect && dragDistance > dragThreshold) {
                                        val pressClickResult = state.getAddressAtOffset(startPos.x, startPos.y, density, isMobile)
                                        val isSelectionArea = pressClickResult != null && (pressClickResult.second == ClickedArea.HEX || pressClickResult.second == ClickedArea.ASCII)
                                        
                                        if (isSelectionArea && (!isMobile || kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.5f)) {
                                            state.isDraggingToSelect = true
                                        } else {
                                            state.isDraggingToScroll = true
                                        }
                                    }
                                    
                                    if (state.isDraggingToSelect) {
                                        change.consume()
                                        state.getAddressAtOffset(position.x, position.y, density, isMobile)?.let { (addr, area) ->
                                            if (area == ClickedArea.HEX || area == ClickedArea.ASCII) state.selectionEnd = addr
                                        }
                                    } else if (state.isDraggingToScroll) {
                                        change.consume()
                                        val rowDelta = (-dragY / (24f * density)).toLong()
                                        state.updateScrollPosition((state.touchStartScroll + rowDelta).coerceIn(0L, state.getMaxScrollPosition()))
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                if (state.touchStartPos != null && !state.isSecondaryClick && !state.isDraggingToScroll && !state.isDraggingToSelect) {
                                    state.getAddressAtOffset(position.x, position.y, density, isMobile)?.let { (addr, area) ->
                                        if (area == ClickedArea.HEX || area == ClickedArea.ASCII) {
                                            state.clickedArea = area
                                            state.selectionStart = addr
                                            state.selectionEnd = addr
                                            state.hexInputBuffer = ""
                                            
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
            val viewStartAddress = state.startAddress + state.scrollPosition * state.bytesPerRow
            for (rowIndex in 0 until state.visibleRowsCount) {
                val rowAddress = viewStartAddress + (rowIndex * state.bytesPerRow)
                if (rowAddress < state.endAddress) {
                    val targets = if (state.activeMaps.isNotEmpty()) state.activeMaps.toList() else listOfNotNull(state.activeMap)
                    val currentMap = targets.firstOrNull { rowAddress >= it.start && rowAddress < it.end }
                    val prevRowAddress = rowAddress - state.bytesPerRow
                    val prevMap = targets.firstOrNull { prevRowAddress >= it.start && prevRowAddress < it.end }
                    
                    // Render separator if this row starts a new map or transition
                    val isNewRegion = currentMap != null && (rowIndex == 0 || prevMap == null || currentMap.start != prevMap.start)
                    
                    if (isNewRegion && currentMap != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.5f))
                                .border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Region: ${if (currentMap.name.isEmpty()) "unnamed" else currentMap.name} [0x${currentMap.start.toString(16).uppercase()} - 0x${currentMap.end.toString(16).uppercase()}] (${currentMap.getProtString()})",
                                color = PS5ThemeColors.AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    val pageStart = ((rowAddress - state.startAddress) / state.pageSize) * state.pageSize + state.startAddress
                    val cachedPage = state.memoryCache[pageStart]
                    val offsetInPage = (rowAddress - pageStart).toInt()
                    
                    val stableRowBytes = remember(rowAddress, cachedPage) {
                        val bytes = ByteArray(state.bytesPerRow)
                        if (cachedPage != null && offsetInPage >= 0 && offsetInPage < cachedPage.size) {
                            val toCopy = minOf(state.bytesPerRow, cachedPage.size - offsetInPage)
                            if (toCopy > 0) {
                                System.arraycopy(cachedPage, offsetInPage, bytes, 0, toCopy)
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
                        hexInputBuffer = state.hexInputBuffer,
                        isMobile = isMobile,
                        showAddress = showAddress
                    )
                } else {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        
        HexScrollbar(state, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp))
        HexContextMenu(state)
    }
}

@Composable
private fun BoxScope.HexScrollbar(state: HexState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val totalBytes = maxOf(0L, state.endAddress - state.startAddress)
            val rowCount = if (totalBytes <= 0L) 0L else (totalBytes + state.bytesPerRow - 1) / state.bytesPerRow
            val trackHeight = maxHeight
            
            // Calculate thumbHeight carefully using Long math to prevent float overflow/infinity
            val thumbHeight = if (rowCount > 0L) {
                val ratio = state.visibleRowsCount.toDouble() / rowCount.toDouble()
                val calcHeight = (trackHeight.value * ratio).dp
                calcHeight.coerceIn(30.dp, trackHeight)
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
            
            Box(modifier = Modifier.fillMaxSize().pointerInput(state, maxScroll) {
                detectTapGestures { pressOffset ->
                    if (pressOffset.y < thumbOffset.toPx()) state.updateScrollPosition((state.scrollPosition - state.visibleRowsCount).coerceIn(0L, maxScroll))
                    else if (pressOffset.y > (thumbOffset + thumbHeight).toPx()) state.updateScrollPosition((state.scrollPosition + state.visibleRowsCount).coerceIn(0L, maxScroll))
                }
            }) {
                Box(modifier = Modifier.offset(y = thumbOffset).fillMaxWidth().height(thumbHeight).background(PS5ThemeColors.AccentCyan, RoundedCornerShape(4.dp))
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
    DropdownMenu(expanded = state.showContextMenu, onDismissRequest = { state.showContextMenu = false }, offset = state.contextMenuOffset) {
        if (state.clickedArea == ClickedArea.ADDRESS) {
            DropdownMenuItem(text = { Text("Copy Address") }, onClick = {
                state.contextMenuAddr?.let { copyToClipboard(String.format("0x%016X", it)) }
                state.showContextMenu = false
            })
            DropdownMenuItem(text = { Text("Add to Watchlist") }, onClick = {
                state.contextMenuAddr?.let { AppContainer.debuggerUseCase.addToWatchlist(it, "Int32", 4) }
                state.showContextMenu = false
            })
        } else {
            DropdownMenuItem(text = { Text("Add Selection to Watchlist") }, onClick = {
                val start = (if (state.selectionStart != null && state.selectionEnd != null) minOf(state.selectionStart!!, state.selectionEnd!!) else state.contextMenuAddr) ?: return@DropdownMenuItem
                val len = if (state.selectionStart != null && state.selectionEnd != null) (maxOf(state.selectionStart!!, state.selectionEnd!!) - start + 1).toInt() else 4
                AppContainer.debuggerUseCase.addToWatchlist(start, "ByteArray", len)
                state.showContextMenu = false
            })
            DropdownMenuItem(text = { Text("Copy Hex") }, onClick = {
                copyToClipboard(state.getSelectedBytesText())
                state.showContextMenu = false
            })
            DropdownMenuItem(text = { Text("Copy ASCII") }, onClick = {
                copyToClipboard(state.getSelectedAsciiText())
                state.showContextMenu = false
            })
        }
    }
}
