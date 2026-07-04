package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.Ps5DebuggerTheme
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

enum class ClickedArea { ADDRESS, HEX, ASCII }

// Clipboard Helpers
fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    } catch (_: Exception) {}
}

fun getFromClipboard(): String {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getData(DataFlavor.stringFlavor) as String
    } catch (_: Exception) {
        ""
    }
}

private val hexViewerScrollPositions = mutableMapOf<Long, Long>()
private var lastActiveMapStart: Long? = null

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HexViewer(
    activeMap: MemoryRange?,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    showAddress: Boolean = true,
    selectionStartParam: Long? = null,
    selectionEndParam: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val coroutineScope = rememberCoroutineScope()
        val client = AppContainer.clientAdapter.client
        val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
        
        var startAddress by remember { mutableStateOf(activeMap?.start ?: 0L) }
        var endAddress by remember { mutableStateOf(activeMap?.end ?: 0L) }
        
        val density = LocalDensity.current.density
        val isMobile = maxWidth < 600.dp
        
        var bytesPerRow by remember { mutableStateOf(if (isMobile) 8 else 16) }
        var goToAddressText by remember { mutableStateOf("") }
        
        // Page caching (64KB)
        val pageSize = 65536
        val memoryCache = remember { mutableStateMapOf<Long, ByteArray>() }
        
        // Scrolling viewport state (dynamically calculated based on actual height)
        val visibleRowsCount = remember(maxHeight, density) {
            val nonGridHeight = 100.dp
            ((maxHeight - nonGridHeight) / 24.dp).toInt().coerceAtLeast(1)
        }
        val mapKey = activeMap?.start ?: 0L
        var scrollPosition by remember(mapKey) { 
            mutableStateOf(hexViewerScrollPositions[mapKey] ?: 0L) 
        } // row index offset
        
        LaunchedEffect(scrollPosition, mapKey) {
            hexViewerScrollPositions[mapKey] = scrollPosition
        }
        
        // Selection range
        var selectionStart by remember { mutableStateOf<Long?>(selectionStartParam) }
        var selectionEnd by remember { mutableStateOf<Long?>(selectionEndParam) }

        LaunchedEffect(selectionStartParam, selectionEndParam) {
            selectionStart = selectionStartParam
            selectionEnd = selectionEndParam
        }

        LaunchedEffect(selectionStart, selectionEnd) {
            onSelectionChanged?.invoke(selectionStart, selectionEnd)
        }
        
        // Lock/Unlock editing state
        var isEditingUnlocked by remember { mutableStateOf(false) }
        
        // Keystroke inputs buffer
        var hexInputBuffer by remember { mutableStateOf("") }
        val pendingEdits = remember { mutableStateMapOf<Long, Byte>() }
        
        val focusRequester = remember { FocusRequester() }
        
        var isMouseDown by remember { mutableStateOf(false) }

        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val keyboardFocusRequester = remember { FocusRequester() }
        var keyboardInputText by remember { mutableStateOf("") }
        var lastTapTime by remember { mutableStateOf(0L) }

        // Touch gesture scrolling and long-press selection state
        var touchStartPos by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
        var touchStartScroll by remember { mutableStateOf(0L) }
        var isDraggingToScroll by remember { mutableStateOf(false) }
        var isDraggingToSelect by remember { mutableStateOf(false) }
        var isLongPressSelection by remember { mutableStateOf(false) }
        var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var isSecondaryClick by remember { mutableStateOf(false) }

        // Global context menu popup positions
        var clickedArea by remember { mutableStateOf<ClickedArea?>(null) }
        var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
        var showContextMenu by remember { mutableStateOf(false) }
        var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

        LaunchedEffect(activeMap, jumpToAddress, bytesPerRow, visibleRowsCount) {
            if (activeMap != null) {
                val mapChanged = activeMap.start != lastActiveMapStart
                if (mapChanged) {
                    startAddress = activeMap.start
                    endAddress = activeMap.end
                    memoryCache.clear()
                    pendingEdits.clear()
                    isEditingUnlocked = false
                    hexInputBuffer = ""
                }

                val target = jumpToAddress
                if (target != null && target >= activeMap.start && target < activeMap.end) {
                    val targetRow = ((target - activeMap.start) / bytesPerRow)
                    val totalRows = ((activeMap.end - activeMap.start + bytesPerRow - 1) / bytesPerRow).toInt()
                    val maxTargetScroll = maxOf(0L, (totalRows - visibleRowsCount).toLong())
                    scrollPosition = targetRow.coerceIn(0L, maxTargetScroll)
                    selectionStart = target
                    selectionEnd = target
                    goToAddressText = target.toString(16).uppercase()
                } else if (mapChanged) {
                    scrollPosition = 0L
                    selectionStart = null
                    selectionEnd = null
                }

                lastActiveMapStart = activeMap.start
            }
        }

        val totalBytes = maxOf(0L, endAddress - startAddress)
        val rowCount = ((totalBytes + bytesPerRow - 1) / bytesPerRow).toInt()
        val maxScrollPosition = maxOf(0L, (rowCount - visibleRowsCount).toLong())
        val viewStartAddress = startAddress + scrollPosition * bytesPerRow

        // Load current viewport pages asynchronously
        LaunchedEffect(scrollPosition, bytesPerRow, startAddress, endAddress, pid, visibleRowsCount) {
            if (pid == null || activeMap == null) return@LaunchedEffect
            delay(100) // Debounce loading during fast scrolling
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
                                val data = client.readMemory(pid, readStart, readLen)
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

    // Cache selection range bounds
    val selMin = if (selectionStart != null && selectionEnd != null) minOf(selectionStart!!, selectionEnd!!) else null
    val selMax = if (selectionStart != null && selectionEnd != null) maxOf(selectionStart!!, selectionEnd!!) else null

    fun isAddressSelected(addr: Long): Boolean {
        val start = selectionStart ?: return false
        val end = selectionEnd ?: return false
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        return addr in lo..hi
    }

    fun getSelectedBytesText(): String {
        if (selMin == null || selMax == null) return ""
        return buildString {
            for (addr in selMin..selMax) {
                val pageStart = (addr / pageSize) * pageSize
                val offset = (addr - pageStart).toInt()
                val page = memoryCache[pageStart]
                val byteVal = pendingEdits[addr] ?: if (page != null && offset in page.indices) page[offset] else 0.toByte()
                append(String.format("%02X ", byteVal))
            }
        }.trim()
    }

    fun getSelectedAsciiText(): String {
        if (selMin == null || selMax == null) return ""
        return buildString {
            for (addr in selMin..selMax) {
                val pageStart = (addr / pageSize) * pageSize
                val offset = (addr - pageStart).toInt()
                val page = memoryCache[pageStart]
                val byteVal = pendingEdits[addr] ?: if (page != null && offset in page.indices) page[offset] else 0.toByte()
                val b = byteVal.toInt()
                if (b in 32..126) append(b.toChar()) else append(".")
            }
        }
    }

    val addressWidthDp = if (isMobile) 80.dp else 120.dp
    val addressWidthPx = if (isMobile) 80f * density else 120f * density
    
    val hexCellWidthDp = if (isMobile) 20.dp else 24.dp
    val hexCellWidthPx = if (isMobile) 20f * density else 24f * density
    
    val asciiCellWidthDp = if (isMobile) 9.dp else 12.dp
    val asciiCellWidthPx = if (isMobile) 9f * density else 12f * density
    
    val spacerAddressToHexDp = if (isMobile) 6.dp else 8.dp
    val spacerAddressToHexPx = if (isMobile) 6f * density else 8f * density
    
    val spacerHexToAsciiDp = if (isMobile) 12.dp else 16.dp
    val spacerHexToAsciiPx = if (isMobile) 12f * density else 16f * density

    val rowWidthPx = addressWidthPx + spacerAddressToHexPx + bytesPerRow * hexCellWidthPx + spacerHexToAsciiPx + bytesPerRow * asciiCellWidthPx

    fun getAddressAtOffset(x: Float, y: Float, maxHeight: Float, componentWidth: Float): Pair<Long, ClickedArea>? {
        val rowHeightPx = 24f * density
        val clampedY = y.coerceIn(0f, maxHeight - 1f)
        val rowIndex = (clampedY / rowHeightPx).toInt()
        
        val currentViewStart = startAddress + scrollPosition * bytesPerRow
        val rowAddress = currentViewStart + rowIndex * bytesPerRow
        if (rowAddress >= endAddress) return null
        // Left-aligned row content layout starting at 0
        val leftOffset = 0f
        
        // Adjust x by the left offset
        val adjustedX = x - leftOffset
        
        val startHexX = addressWidthPx + spacerAddressToHexPx
        val endHexX = startHexX + bytesPerRow * hexCellWidthPx
        
        val startAsciiX = endHexX + spacerHexToAsciiPx
        val endAsciiX = startAsciiX + bytesPerRow * asciiCellWidthPx
        
        val midAddressHex = addressWidthPx + spacerAddressToHexPx / 2f
        val midHexAscii = endHexX + spacerHexToAsciiPx / 2f
        
        return if (adjustedX < midAddressHex) {
            Pair(rowAddress, ClickedArea.ADDRESS)
        } else if (adjustedX < midHexAscii) {
            val offsetInHex = (adjustedX - startHexX).coerceIn(0f, (bytesPerRow * hexCellWidthPx) - 0.1f)
            val col = (offsetInHex / hexCellWidthPx).toInt().coerceIn(0, bytesPerRow - 1)
            Pair(rowAddress + col, ClickedArea.HEX)
        } else {
            val offsetInAscii = (adjustedX - startAsciiX).coerceIn(0f, (bytesPerRow * asciiCellWidthPx) - 0.1f)
            val col = (offsetInAscii / asciiCellWidthPx).toInt().coerceIn(0, bytesPerRow - 1)
            Pair(rowAddress + col, ClickedArea.ASCII)
        }
    }
    val handleKeyEvent = { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && selectionEnd != null) {
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
                
                // Auto scroll view area if keys move past visible limits
                val nextCursorRow = ((nextCursor - startAddress) / bytesPerRow).toInt()
                if (nextCursorRow < scrollPosition) {
                    scrollPosition = nextCursorRow.toLong().coerceIn(0L, maxScrollPosition)
                } else if (nextCursorRow >= scrollPosition + visibleRowsCount) {
                    scrollPosition = (nextCursorRow - visibleRowsCount + 1).toLong().coerceIn(0L, maxScrollPosition)
                }
                true
            } else if (isEditingUnlocked) {
                val keyChar = keyEvent.utf16CodePoint.toChar()
                if (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) {
                    pendingEdits.remove(cursor)
                    hexInputBuffer = ""
                    true
                } else if (keyChar.isDigit() || keyChar.uppercaseChar() in 'A'..'F') {
                    hexInputBuffer += keyChar.uppercaseChar()
                    if (hexInputBuffer.length == 2) {
                        val b = hexInputBuffer.toIntOrNull(16)?.toByte()
                        if (b != null) {
                            pendingEdits[cursor] = b
                        }
                        hexInputBuffer = ""
                        if (cursor + 1 < endAddress) {
                            selectionEnd = cursor + 1
                            if (selectionStart == cursor) {
                                selectionStart = cursor + 1
                            }
                        }
                    }
                    true
                } else if (keyChar.code in 32..126) {
                    pendingEdits[cursor] = keyChar.code.toByte()
                    hexInputBuffer = ""
                    if (cursor + 1 < endAddress) {
                        selectionEnd = cursor + 1
                        if (selectionStart == cursor) {
                            selectionStart = cursor + 1
                        }
                    }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } else {
            false
        }
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
                if (cursor + 1 < endAddress) {
                    selectionEnd = cursor + 1
                    if (selectionStart == cursor) {
                        selectionStart = cursor + 1
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .offset(y = (-1000).dp)
            .size(100.dp)
    ) {
        BasicTextField(
            value = keyboardInputText,
            onValueChange = { text ->
                if (text.isNotEmpty()) {
                    val char = text.last()
                    if (clickedArea == ClickedArea.ASCII && isEditingUnlocked) {
                        if (char.code in 32..126) {
                            val cursor = selectionEnd
                            if (cursor != null) {
                                pendingEdits[cursor] = char.code.toByte()
                                if (cursor + 1 < endAddress) {
                                    selectionEnd = cursor + 1
                                    if (selectionStart == cursor) {
                                        selectionStart = cursor + 1
                                    }
                                }
                            }
                        }
                    } else {
                        handleHexInput(char)
                    }
                }
                keyboardInputText = ""
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .focusRequester(keyboardFocusRequester)
                .focusable()
                .fillMaxSize()
                .onKeyEvent(handleKeyEvent)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isMobile) 0.dp else 8.dp,
                bottom = 8.dp
            )
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { 
                    if (!isMobile) {
                        focusRequester.requestFocus() 
                    }
                })
            }
            .onKeyEvent(handleKeyEvent)
    ) {
        // Toolbar
        if (isMobile) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Go Address OutlinedTextField and Go Button
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = goToAddressText,
                        onValueChange = { goToAddressText = it },
                        label = { Text("Go to Address (Hex)") },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val addr = goToAddressText.trim().toLongOrNull(16)
                            if (addr != null && addr in startAddress..endAddress) {
                                val rowIdx = ((addr - startAddress) / bytesPerRow).toInt()
                                scrollPosition = rowIdx.toLong().coerceIn(0L, maxScrollPosition)
                            } else {
                                AppContainer.debuggerUseCase.log("HEX", "Address out of range or invalid", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PS5ThemeColors.AccentCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically).height(40.dp).width(64.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Go", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Row 2: Action Tools (Lock, Undo, Inject) and Column Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Tooltip(if (isEditingUnlocked) "Lock Editing" else "Unlock Editing") {
                            IconButton(
                                onClick = { isEditingUnlocked = !isEditingUnlocked },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isEditingUnlocked) PS5ThemeColors.AccentAmber else PS5ThemeColors.SecondaryBg
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditingUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Toggle Edit Lock",
                                    tint = if (isEditingUnlocked) Color.Black else PS5ThemeColors.TextMain,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Tooltip("Undo changes") {
                            IconButton(
                                onClick = { pendingEdits.clear() },
                                enabled = pendingEdits.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = PS5ThemeColors.SecondaryBg
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo changes",
                                    tint = if (pendingEdits.isNotEmpty()) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Tooltip("Inject overrides to memory") {
                            Button(
                                onClick = {
                                    if (pid != null) {
                                        coroutineScope.launch {
                                            var success = true
                                            pendingEdits.forEach { (addr, b) ->
                                                val ok = client.writeMemory(pid, addr, byteArrayOf(b))
                                                if (!ok) success = false
                                            }
                                            if (success) {
                                                AppContainer.debuggerUseCase.log("HEX", "Successfully injected ${pendingEdits.size} bytes overwrites", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                                pendingEdits.keys.map { (it / pageSize) * pageSize }.distinct().forEach {
                                                    memoryCache.remove(it)
                                                }
                                                pendingEdits.clear()
                                            } else {
                                                AppContainer.debuggerUseCase.log("HEX", "Some byte write operations failed on wire", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                            }
                                        }
                                    }
                                },
                                enabled = isEditingUnlocked && pendingEdits.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PS5ThemeColors.AccentCyan,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Inject (${pendingEdits.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(8, 16, 32).forEach { cols ->
                            FilterChip(
                                selected = bytesPerRow == cols,
                                onClick = {
                                    bytesPerRow = cols
                                    memoryCache.clear()
                                    selectionStart = null
                                    selectionEnd = null
                                    pendingEdits.clear()
                                    scrollPosition = 0L
                                },
                                label = { Text("$cols", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = goToAddressText,
                    onValueChange = { goToAddressText = it },
                    label = { Text("Go to Address (Hex)") },
                    modifier = Modifier.width(200.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    singleLine = true
                )
                
                Tooltip("Go to address") {
                    Button(
                        onClick = {
                            val addr = goToAddressText.trim().toLongOrNull(16)
                            if (addr != null && addr in startAddress..endAddress) {
                                val rowIdx = ((addr - startAddress) / bytesPerRow).toInt()
                                scrollPosition = rowIdx.toLong().coerceIn(0L, maxScrollPosition)
                            } else {
                                AppContainer.debuggerUseCase.log("HEX", "Address out of range or invalid", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PS5ThemeColors.AccentCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically).height(40.dp).width(64.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Go", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                
                Spacer(Modifier.weight(1f))

                // Unlock and Inject buttons
                Tooltip(if (isEditingUnlocked) "Lock Editing" else "Unlock Editing") {
                    Button(
                        onClick = { isEditingUnlocked = !isEditingUnlocked },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEditingUnlocked) PS5ThemeColors.AccentAmber else PS5ThemeColors.SecondaryBg
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditingUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = if (isEditingUnlocked) "Lock Editing" else "Unlock Editing",
                            tint = if (isEditingUnlocked) Color.Black else PS5ThemeColors.TextMain,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Tooltip("Undo changes") {
                    Button(
                        onClick = { pendingEdits.clear() },
                        enabled = pendingEdits.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PS5ThemeColors.SecondaryBg,
                            disabledContainerColor = PS5ThemeColors.SecondaryBg.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo changes",
                            tint = if (pendingEdits.isNotEmpty()) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Tooltip("Inject overrides to memory") {
                    Button(
                        onClick = {
                            if (pid != null) {
                                coroutineScope.launch {
                                    var success = true
                                    pendingEdits.forEach { (addr, b) ->
                                        val ok = client.writeMemory(pid, addr, byteArrayOf(b))
                                        if (!ok) success = false
                                    }
                                    if (success) {
                                        AppContainer.debuggerUseCase.log("HEX", "Successfully injected ${pendingEdits.size} bytes overwrites", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                        pendingEdits.keys.map { (it / pageSize) * pageSize }.distinct().forEach {
                                            memoryCache.remove(it)
                                        }
                                        pendingEdits.clear()
                                    } else {
                                        AppContainer.debuggerUseCase.log("HEX", "Some byte write operations failed on wire", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                    }
                                }
                            }
                        },
                        enabled = isEditingUnlocked && pendingEdits.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PS5ThemeColors.AccentCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Inject" +
                                " (${pendingEdits.size})", fontWeight = FontWeight.Bold)
                    }
                }
                
                VerticalDivider(modifier = Modifier.height(32.dp), color = PS5ThemeColors.BorderColor)

                Text("Columns:")
                Row {
                    listOf(8, 16, 32).forEach { cols ->
                        FilterChip(
                            selected = bytesPerRow == cols,
                            onClick = {
                                bytesPerRow = cols
                                memoryCache.clear()
                                selectionStart = null
                                selectionEnd = null
                                pendingEdits.clear()
                                scrollPosition = 0L
                            },
                            label = { Text("$cols") },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
        
        if (activeMap == null || pid == null) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Select a Process and Virtual Memory Map to view hex memory", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val horizontalScrollState = rememberScrollState()
            val gridWidth = (rowWidthPx / density).dp + 16.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = gridWidth)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState, enabled = !isMouseDown)
                ) {
                    Column(
                        modifier = Modifier
                            .width(gridWidth)
                            .fillMaxHeight()
                    ) {
                    // Hex view titles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PS5ThemeColors.SecondaryBg)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showAddress) {
                            Text(
                                text = "Address",
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isMobile) 11.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PS5ThemeColors.TextMain,
                                modifier = Modifier.width(addressWidthDp).padding(start = if (isMobile) 4.dp else 8.dp)
                            )
                            
                            Spacer(Modifier.width(spacerAddressToHexDp))
                        }
                        
                        Text(
                            text = "Hexadecimal Data",
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (isMobile) 11.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PS5ThemeColors.TextMain,
                            modifier = Modifier.width((bytesPerRow * hexCellWidthDp.value).dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(Modifier.width(spacerHexToAsciiDp))
                        
                        Text(
                            text = "ASCII",
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (isMobile) 11.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PS5ThemeColors.TextMain,
                            modifier = Modifier.width((bytesPerRow * asciiCellWidthDp.value).dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(Modifier.width(16.dp))
                    }

                    // Grid scroll area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Static rendering column with mouse coordinate listener
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeMap, bytesPerRow, density) {
                                    try {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.first()
                                                val position = change.position
                                                
                                                when (event.type) {
                                                    PointerEventType.Scroll -> {
                                                        val delta = change.scrollDelta.y
                                                        val rows = if (delta > 0f) 3L else if (delta < 0f) -3L else 0L
                                                        val currentMax = maxOf(0L, ((endAddress - startAddress + bytesPerRow - 1) / bytesPerRow - visibleRowsCount))
                                                        scrollPosition = (scrollPosition + rows).coerceIn(0L, currentMax)
                                                    }
                                                    PointerEventType.Press -> {
                                                        isMouseDown = true
                                                        if (!isMobile) {
                                                            focusRequester.requestFocus()
                                                        }
                                                        touchStartPos = position
                                                        touchStartScroll = scrollPosition
                                                        isDraggingToScroll = false
                                                        isDraggingToSelect = false
                                                        isLongPressSelection = false
                                                        isSecondaryClick = event.buttons.isSecondaryPressed
                                                        
                                                        val clickResult = getAddressAtOffset(position.x, position.y, size.height.toFloat(), size.width.toFloat())
                                                        
                                                        if (isSecondaryClick) {
                                                            if (clickResult != null) {
                                                                val (addr, area) = clickResult
                                                                clickedArea = area
                                                                contextMenuAddr = addr
                                                                if (area == ClickedArea.HEX || area == ClickedArea.ASCII) {
                                                                    if (!isAddressSelected(addr)) {
                                                                        selectionStart = addr
                                                                        selectionEnd = addr
                                                                    }
                                                                }
                                                                contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                                                showContextMenu = true
                                                            }
                                                        } else {
                                                            if (clickResult != null) {
                                                                val (addr, area) = clickResult
                                                                longPressJob?.cancel()
                                                                if (!isMobile && (area == ClickedArea.HEX || area == ClickedArea.ASCII)) {
                                                                    clickedArea = area
                                                                    selectionStart = addr
                                                                    selectionEnd = addr
                                                                    hexInputBuffer = ""
                                                                }
                                                                isLongPressSelection = false
                                                                longPressJob = coroutineScope.launch {
                                                                    delay(400)
                                                                    if (!isDraggingToScroll && !isDraggingToSelect) {
                                                                        isLongPressSelection = true
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    PointerEventType.Move -> {
                                                        val startPos = touchStartPos
                                                        if (startPos != null) {
                                                            val dragY = position.y - startPos.y
                                                            val dragX = position.x - startPos.x
                                                            val dragDistance = kotlin.math.sqrt(dragX * dragX + dragY * dragY)
                                                            val dragThreshold = if (isMobile) 24f * density else 8f * density
                                                            
                                                            val pressClickResult = getAddressAtOffset(startPos.x, startPos.y, size.height.toFloat(), size.width.toFloat())
                                                            val isSelectionArea = pressClickResult != null && (pressClickResult.second == ClickedArea.HEX || pressClickResult.second == ClickedArea.ASCII)
                                                            
                                                            if (!isDraggingToScroll && !isDraggingToSelect && dragDistance > dragThreshold) {
                                                                if (isMobile) {
                                                                    if (isSelectionArea && kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.5f) {
                                                                        isDraggingToSelect = true
                                                                        longPressJob?.cancel()
                                                                        val startClick = getAddressAtOffset(startPos.x, startPos.y, size.height.toFloat(), size.width.toFloat())
                                                                        if (startClick != null) {
                                                                            clickedArea = startClick.second
                                                                            selectionStart = startClick.first
                                                                            selectionEnd = startClick.first
                                                                            hexInputBuffer = ""
                                                                        }
                                                                    } else {
                                                                        isDraggingToScroll = true
                                                                        longPressJob?.cancel()
                                                                    }
                                                                } else {
                                                                    if (isSelectionArea) {
                                                                        isDraggingToSelect = true
                                                                        longPressJob?.cancel()
                                                                    } else {
                                                                        isDraggingToScroll = true
                                                                        longPressJob?.cancel()
                                                                    }
                                                                }
                                                            }
                                                            
                                                            if (isDraggingToSelect) {
                                                                change.consume()
                                                                val clickResult = getAddressAtOffset(position.x, position.y, size.height.toFloat(), size.width.toFloat())
                                                                if (clickResult != null) {
                                                                    val (addr, area) = clickResult
                                                                    if (area == ClickedArea.HEX || area == ClickedArea.ASCII) {
                                                                        selectionEnd = addr
                                                                    }
                                                                }
                                                                if (position.y >= size.height) {
                                                                    scrollPosition = (scrollPosition + 1L).coerceIn(0L, maxScrollPosition)
                                                                } else if (position.y < 0f) {
                                                                    scrollPosition = (scrollPosition - 1L).coerceIn(0L, maxScrollPosition)
                                                                }
                                                            } else if (isDraggingToScroll) {
                                                                change.consume()
                                                                val rowDelta = (-dragY / (24f * density)).toLong()
                                                                scrollPosition = (touchStartScroll + rowDelta).coerceIn(0L, maxScrollPosition)
                                                            }
                                                        }
                                                    }
                                                    PointerEventType.Release -> {
                                                        longPressJob?.cancel()
                                                        val startPos = touchStartPos
                                                        if (startPos != null && !isSecondaryClick) {
                                                            val dragY = position.y - startPos.y
                                                            val dragX = position.x - startPos.x
                                                            val dragDistance = kotlin.math.sqrt(dragX * dragX + dragY * dragY)
                                                            val dragThreshold = if (isMobile) 24f * density else 8f * density
                                                            val hasDragged = dragDistance > dragThreshold
                                                            
                                                            val clickResult = getAddressAtOffset(position.x, position.y, size.height.toFloat(), size.width.toFloat())
                                                            if (clickResult != null) {
                                                                val (addr, area) = clickResult
                                                                
                                                                if (isDraggingToSelect) {
                                                                    if (isMobile) {
                                                                        clickedArea = area
                                                                        contextMenuAddr = addr
                                                                        contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                                                        showContextMenu = true
                                                                    }
                                                                } else if (isLongPressSelection) {
                                                                    clickedArea = area
                                                                    contextMenuAddr = addr
                                                                    contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                                                    showContextMenu = true
                                                                } else if (!isDraggingToScroll) {
                                                                    if (area == ClickedArea.HEX || area == ClickedArea.ASCII) {
                                                                        clickedArea = area
                                                                        selectionStart = addr
                                                                        selectionEnd = addr
                                                                        hexInputBuffer = ""
                                                                    }
                                                                    
                                                                     // Double tap to edit (requests focus & opens soft keyboard)
                                                                     val currentTime = System.currentTimeMillis()
                                                                     println("Release event: addr=$addr, area=$area, timeDiff=${currentTime - lastTapTime}ms, isEditingUnlocked=$isEditingUnlocked")
                                                                     if (currentTime - lastTapTime < 500) {
                                                                         println("Double-tap detected! Showing keyboard.")
                                                                         if (isMobile && isEditingUnlocked && (area == ClickedArea.HEX || area == ClickedArea.ASCII)) {
                                                                             coroutineScope.launch {
                                                                                 try {
                                                                                     keyboardFocusRequester.requestFocus()
                                                                                     kotlinx.coroutines.delay(100)
                                                                                     keyboardController?.show()
                                                                                 } catch (e: Exception) {
                                                                                     e.printStackTrace()
                                                                                 }
                                                                             }
                                                                         }
                                                                     }
                                                                     lastTapTime = currentTime
                                                                }
                                                            }
                                                        }
                                                        touchStartPos = null
                                                        isMouseDown = false
                                                        isDraggingToScroll = false
                                                        isDraggingToSelect = false
                                                        isLongPressSelection = false
                                                        isSecondaryClick = false
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        isMouseDown = false
                                        isDraggingToScroll = false
                                        isDraggingToSelect = false
                                        isLongPressSelection = false
                                        isSecondaryClick = false
                                        touchStartPos = null
                                    }
                                }
                        ) {
                            for (rowIndex in 0 until visibleRowsCount) {
                                val rowAddress = viewStartAddress + (rowIndex * bytesPerRow)
                                if (rowAddress < endAddress) {
                                    val pageStart = (rowAddress / pageSize) * pageSize
                                    val cachedPage = memoryCache[pageStart]
                                    val offsetInPage = (rowAddress - pageStart).toInt()
                                    
                                    val stableRowBytes = remember(rowAddress, cachedPage) {
                                        val bytes = ByteArray(bytesPerRow)
                                        if (cachedPage != null) {
                                            val available = maxOf(0, cachedPage.size - offsetInPage)
                                            val toCopy = minOf(bytesPerRow, available)
                                            System.arraycopy(cachedPage, offsetInPage, bytes, 0, toCopy)
                                        }
                                        StableRowBytes(bytes)
                                    }
 
                                     HexRowView(
                                         address = rowAddress,
                                         stableBytes = stableRowBytes,
                                         columns = bytesPerRow,
                                         selectionMin = selMin,
                                         selectionMax = selMax,
                                         cursorAddress = selectionEnd,
                                         pendingEdits = pendingEdits,
                                         hexInputBuffer = hexInputBuffer,
                                         isMobile = isMobile,
                                         showAddress = showAddress
                                     )
                                } else {
                                    // Render empty filler rows at the end of the region
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                        }

                        // Custom Reactive Drag scrollbar
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(10.dp)
                                .background(PS5ThemeColors.SecondaryBg, RoundedCornerShape(4.dp))
                        ) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val trackHeight = maxHeight
                                val thumbHeight = if (rowCount > 0) {
                                    (trackHeight * visibleRowsCount.toFloat() / rowCount).coerceAtLeast(30.dp)
                                } else {
                                    trackHeight
                                }
                                val maxOffset = trackHeight - thumbHeight
                                val thumbOffset = if (maxScrollPosition > 0) {
                                    maxOffset * (scrollPosition.toFloat() / maxScrollPosition)
                                } else {
                                    0.dp
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(thumbOffset, thumbHeight, maxScrollPosition) {
                                            detectTapGestures { pressOffset ->
                                                val y = pressOffset.y
                                                val thumbOffsetPx = thumbOffset.toPx()
                                                val thumbHeightPx = thumbHeight.toPx()
                                                if (y < thumbOffsetPx) {
                                                    scrollPosition = (scrollPosition - visibleRowsCount).coerceIn(0L, maxScrollPosition)
                                                } else if (y > thumbOffsetPx + thumbHeightPx) {
                                                    scrollPosition = (scrollPosition + visibleRowsCount).coerceIn(0L, maxScrollPosition)
                                                }
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(y = thumbOffset)
                                            .fillMaxWidth()
                                            .height(thumbHeight)
                                            .background(PS5ThemeColors.AccentCyan, RoundedCornerShape(4.dp))
                                            .pointerInput(maxOffset, maxScrollPosition) {
                                                detectDragGestures { _, dragAmount ->
                                                    val maxOffsetPx = maxOffset.toPx()
                                                    if (maxOffsetPx > 0f && maxScrollPosition > 0L) {
                                                        val fraction = dragAmount.y / maxOffsetPx
                                                        val deltaRows = (fraction * maxScrollPosition).toLong()
                                                        scrollPosition = (scrollPosition + deltaRows).coerceIn(0L, maxScrollPosition)
                                                    }
                                                }
                                            }
                                    )
                                }
                            }
                        }

                        // Global Context Menu
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false },
                            offset = contextMenuOffset
                        ) {
                            if (clickedArea == ClickedArea.ADDRESS) {
                                DropdownMenuItem(
                                    text = { Text("Copy Address", fontSize = 12.sp) },
                                    onClick = {
                                        if (contextMenuAddr != null) {
                                            copyToClipboard(String.format("0x%016X", contextMenuAddr))
                                        }
                                        showContextMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Address to Watchlist", fontSize = 12.sp) },
                                    onClick = {
                                        if (contextMenuAddr != null) {
                                            AppContainer.debuggerUseCase.addToWatchlist(contextMenuAddr!!, type = "Int32", byteLength = 4)
                                        }
                                        showContextMenu = false
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Add Selection to Watchlist", fontSize = 12.sp) },
                                    onClick = {
                                        val watchStart = selMin ?: contextMenuAddr
                                        val watchLength = if (selMin != null && selMax != null) (selMax - selMin + 1).toInt() else 4
                                        if (watchStart != null) {
                                            AppContainer.debuggerUseCase.addToWatchlist(watchStart, type = "ByteArray", byteLength = watchLength)
                                        }
                                        showContextMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy Hex String", fontSize = 12.sp) },
                                    onClick = {
                                        val hexStr = getSelectedBytesText()
                                        if (hexStr.isNotEmpty()) {
                                            copyToClipboard(hexStr)
                                        }
                                        showContextMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy ASCII String", fontSize = 12.sp) },
                                    onClick = {
                                        val asciiStr = getSelectedAsciiText()
                                        if (asciiStr.isNotEmpty()) {
                                            copyToClipboard(asciiStr)
                                        }
                                        showContextMenu = false
                                    }
                                )
                                HorizontalDivider(color = PS5ThemeColors.BorderColor)
                                DropdownMenuItem(
                                    text = { Text("Paste Hex Value", fontSize = 12.sp) },
                                    enabled = isEditingUnlocked,
                                    onClick = {
                                        if (isEditingUnlocked && contextMenuAddr != null) {
                                            val pasteVal = getFromClipboard()
                                            val cleanHex = pasteVal.trim().filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(2)
                                            if (cleanHex.length == 2) {
                                                val b = cleanHex.toIntOrNull(16)?.toByte()
                                                if (b != null) {
                                                    pendingEdits[contextMenuAddr!!] = b
                                                }
                                            }
                                        }
                                        showContextMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

@Composable
fun HexRowView(
    address: Long,
    stableBytes: StableRowBytes,
    columns: Int,
    selectionMin: Long?,
    selectionMax: Long?,
    cursorAddress: Long?,
    pendingEdits: Map<Long, Byte>,
    hexInputBuffer: String,
    isMobile: Boolean,
    showAddress: Boolean = true
) {
    val addressWidthDp = if (isMobile) 80.dp else 120.dp
    val hexCellWidthDp = if (isMobile) 20.dp else 24.dp
    val asciiCellWidthDp = if (isMobile) 9.dp else 12.dp
    val spacerAddressToHexDp = if (isMobile) 6.dp else 8.dp
    val spacerHexToAsciiDp = if (isMobile) 12.dp else 16.dp

    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (showAddress) {
            Text(
                text = String.format("%X", address),
                fontFamily = FontFamily.Monospace,
                fontSize = if (isMobile) 11.sp else 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(addressWidthDp).padding(start = if (isMobile) 4.dp else 8.dp)
            )

            Spacer(Modifier.width(spacerAddressToHexDp))
        }

        // Hex data group
        Row(
            modifier = Modifier.width((columns * hexCellWidthDp.value).dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (i in 0 until columns) {
                if (i < stableBytes.bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: stableBytes.bytes[i]
                    val isCursor = cursorAddress == byteAddr
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr >= selectionMin && byteAddr <= selectionMax
                    ByteHexCell(
                        byte = b,
                        isSelected = isSelected,
                        isCursor = isCursor,
                        isPendingEdit = pendingEdits.containsKey(byteAddr),
                        hexInputBuffer = if (isCursor) hexInputBuffer else "",
                        width = hexCellWidthDp,
                        isMobile = isMobile
                    )
                } else {
                    Spacer(Modifier.width(hexCellWidthDp))
                }
            }
        }

        Spacer(Modifier.width(spacerHexToAsciiDp))

        // ASCII group
        Row(
            modifier = Modifier.width((columns * asciiCellWidthDp.value).dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (i in 0 until columns) {
                if (i < stableBytes.bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: stableBytes.bytes[i]
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr >= selectionMin && byteAddr <= selectionMax
                    ByteAsciiCell(
                        byte = b,
                        isSelected = isSelected,
                        width = asciiCellWidthDp,
                        isMobile = isMobile
                    )
                } else {
                    Spacer(Modifier.width(asciiCellWidthDp))
                }
            }
        }
        
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
fun ByteHexCell(
    byte: Byte,
    isSelected: Boolean,
    isCursor: Boolean,
    isPendingEdit: Boolean,
    hexInputBuffer: String,
    width: androidx.compose.ui.unit.Dp,
    isMobile: Boolean
) {
    val displayStr = if (hexInputBuffer.isNotEmpty() && isCursor) {
        hexInputBuffer + "_"
    } else {
        String.format("%02X", byte)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(24.dp)
            .background(
                if (isCursor) PS5ThemeColors.AccentCyan.copy(alpha = 0.55f)
                else if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .border(
                1.dp,
                if (isPendingEdit) PS5ThemeColors.AccentAmber
                else if (isCursor) PS5ThemeColors.AccentCyan
                else Color.Transparent,
                RoundedCornerShape(2.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayStr,
            fontFamily = FontFamily.Monospace,
            fontSize = if (isMobile) 11.sp else 13.sp,
            color = if (isPendingEdit) PS5ThemeColors.AccentAmber
                    else if (isCursor) PS5ThemeColors.AccentCyan
                    else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun ByteAsciiCell(
    byte: Byte,
    isSelected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    isMobile: Boolean
) {
    val charStr = if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else "."

    Box(
        modifier = Modifier
            .width(width)
            .height(24.dp)
            .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.45f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = charStr,
            fontFamily = FontFamily.Monospace,
            fontSize = if (isMobile) 11.sp else 13.sp,
            color = if (isSelected) PS5ThemeColors.AccentCyan else Color.Green
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tooltip(
    text: String,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = PS5ThemeColors.SecondaryBg,
                contentColor = PS5ThemeColors.TextMain
            ) {
                Text(text, fontSize = 11.sp)
            }
        },
        state = rememberTooltipState()
    ) {
        content()
    }
}

@androidx.compose.runtime.Immutable
class StableRowBytes(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StableRowBytes) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}
