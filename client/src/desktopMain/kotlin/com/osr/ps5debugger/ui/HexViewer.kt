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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.protocol.Ps5VmMapEntry
import com.osr.ps5debugger.service.DebuggerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HexViewer(
    activeMap: Ps5VmMapEntry?,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val client = DebuggerService.client
    val pid = DebuggerService.activePid
    
    var startAddress by remember { mutableStateOf(activeMap?.start ?: 0L) }
    var endAddress by remember { mutableStateOf(activeMap?.end ?: 0L) }
    
    var bytesPerRow by remember { mutableStateOf(16) }
    var goToAddressText by remember { mutableStateOf("") }
    
    // Page caching (64KB)
    val pageSize = 65536
    val memoryCache = remember { mutableStateMapOf<Long, ByteArray>() }
    
    // Scrolling viewport state (static rendering of 25 rows)
    val visibleRowsCount = 25
    var scrollPosition by remember { mutableStateOf(0L) } // row index offset
    
    // Selection range
    var selectionStart by remember { mutableStateOf<Long?>(null) }
    var selectionEnd by remember { mutableStateOf<Long?>(null) }
    
    // Lock/Unlock editing state
    var isEditingUnlocked by remember { mutableStateOf(false) }
    
    // Keystroke inputs buffer
    var hexInputBuffer by remember { mutableStateOf("") }
    val pendingEdits = remember { mutableStateMapOf<Long, Byte>() }
    
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current.density
    
    var isMouseDown by remember { mutableStateOf(false) }

    // Global context menu popup positions
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    LaunchedEffect(activeMap) {
        if (activeMap != null) {
            startAddress = activeMap.start
            endAddress = activeMap.end
            memoryCache.clear()
            scrollPosition = 0L
            selectionStart = null
            selectionEnd = null
            pendingEdits.clear()
            isEditingUnlocked = false
            hexInputBuffer = ""
        }
    }

    LaunchedEffect(activeMap, jumpToAddress, bytesPerRow) {
        val target = jumpToAddress ?: return@LaunchedEffect
        val map = activeMap ?: return@LaunchedEffect
        if (target < map.start || target >= map.end) return@LaunchedEffect

        val targetRow = ((target - map.start) / bytesPerRow).toLong()
        val totalRows = ((map.end - map.start + bytesPerRow - 1) / bytesPerRow).toInt()
        val maxTargetScroll = maxOf(0L, (totalRows - visibleRowsCount).toLong())
        scrollPosition = targetRow.coerceIn(0L, maxTargetScroll)
        selectionStart = target
        selectionEnd = target
        goToAddressText = target.toString(16).uppercase()
    }

    val totalBytes = maxOf(0L, endAddress - startAddress)
    val rowCount = ((totalBytes + bytesPerRow - 1) / bytesPerRow).toInt()
    val maxScrollPosition = maxOf(0L, (rowCount - visibleRowsCount).toLong())
    val viewStartAddress = startAddress + scrollPosition * bytesPerRow

    // Load current viewport pages asynchronously
    LaunchedEffect(scrollPosition, bytesPerRow, startAddress, endAddress, pid) {
        if (pid == null || activeMap == null) return@LaunchedEffect
        delay(100) // Debounce loading during fast scrolling
        val visibleStart = startAddress + scrollPosition * bytesPerRow
        val visibleEnd = minOf(endAddress, visibleStart + visibleRowsCount * bytesPerRow)
        
        withContext(Dispatchers.IO) {
            var page = (visibleStart / pageSize) * pageSize
            while (page <= visibleEnd) {
                if (!memoryCache.containsKey(page)) {
                    try {
                        val data = client.readMemory(pid, page, pageSize)
                        memoryCache[page] = data
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

    fun getAddressAtOffset(x: Float, y: Float): Long? {
        val rowHeightPx = 24f * density
        val rowIndex = (y / rowHeightPx).toInt()
        if (rowIndex < 0 || rowIndex >= visibleRowsCount) return null
        
        val currentViewStart = startAddress + scrollPosition * bytesPerRow
        val rowAddress = currentViewStart + rowIndex * bytesPerRow
        if (rowAddress >= endAddress) return null
        
        val startHexX = (160f + 8f) * density
        val hexCellWidth = 24f * density
        val endHexX = startHexX + bytesPerRow * hexCellWidth
        
        val startAsciiX = endHexX + 16f * density
        val asciiCellWidth = 10f * density
        val endAsciiX = startAsciiX + bytesPerRow * asciiCellWidth
        
        return if (x in startHexX..endHexX) {
            val col = ((x - startHexX) / hexCellWidth).toInt().coerceIn(0, bytesPerRow - 1)
            rowAddress + col
        } else if (x in startAsciiX..endAsciiX) {
            val col = ((x - startAsciiX) / asciiCellWidth).toInt().coerceIn(0, bytesPerRow - 1)
            rowAddress + col
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusRequester.requestFocus() })
            }
            .onKeyEvent { keyEvent ->
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
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            
            Button(
                onClick = {
                    val addr = goToAddressText.trim().toLongOrNull(16)
                    if (addr != null && addr in startAddress..endAddress) {
                        val rowIdx = ((addr - startAddress) / bytesPerRow).toInt()
                        scrollPosition = rowIdx.toLong().coerceIn(0L, maxScrollPosition)
                    } else {
                        DebuggerService.log("HEX", "Address out of range or invalid", DebuggerService.LogEntry.Level.WARN)
                    }
                }
            ) {
                Text("Go")
            }
            
            Spacer(Modifier.weight(1f))

            // Unlock and Inject buttons
            Button(
                onClick = { isEditingUnlocked = !isEditingUnlocked },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEditingUnlocked) PS5ThemeColors.AccentAmber else PS5ThemeColors.SecondaryBg
                )
            ) {
                Text(
                    text = if (isEditingUnlocked) "Lock Editing" else "Unlock Editing",
                    color = if (isEditingUnlocked) Color.Black else PS5ThemeColors.TextMain,
                    fontSize = 12.sp
                )
            }

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
                                DebuggerService.log("HEX", "Successfully injected ${pendingEdits.size} bytes overwrites", DebuggerService.LogEntry.Level.INFO)
                                pendingEdits.keys.map { (it / pageSize) * pageSize }.distinct().forEach {
                                    memoryCache.remove(it)
                                }
                                pendingEdits.clear()
                            } else {
                                DebuggerService.log("HEX", "Some byte write operations failed on wire", DebuggerService.LogEntry.Level.ERROR)
                            }
                        }
                    }
                },
                enabled = isEditingUnlocked && pendingEdits.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
            ) {
                Text("Inject Overwrite (${pendingEdits.size})", color = Color.Black)
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
        
        if (activeMap == null || pid == null) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Select a Process and Virtual Memory Map to view hex memory", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            // Hex view titles
            Row(modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(vertical = 4.dp)) {
                Text(
                    text = "Address",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain,
                    modifier = Modifier.width(160.dp).padding(start = 8.dp)
                )
                Text(
                    text = "Hexadecimal Data",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "ASCII",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain,
                    modifier = Modifier.width(320.dp)
                )
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
                        .padding(end = 16.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.first()
                                    val position = change.position
                                    
                                    when (event.type) {
                                        PointerEventType.Scroll -> {
                                            val delta = change.scrollDelta.y
                                            val rows = if (delta > 0f) 3L else if (delta < 0f) -3L else 0L
                                            val currentMax = maxOf(0L, ((endAddress - startAddress + bytesPerRow - 1) / bytesPerRow - visibleRowsCount).toLong())
                                            scrollPosition = (scrollPosition + rows).coerceIn(0L, currentMax)
                                        }
                                        PointerEventType.Press -> {
                                            if (event.buttons.isPrimaryPressed) {
                                                isMouseDown = true
                                                focusRequester.requestFocus()
                                                val addr = getAddressAtOffset(position.x, position.y)
                                                if (addr != null) {
                                                    selectionStart = addr
                                                    selectionEnd = addr
                                                    hexInputBuffer = ""
                                                }
                                            } else if (event.buttons.isSecondaryPressed) {
                                                focusRequester.requestFocus()
                                                val addr = getAddressAtOffset(position.x, position.y)
                                                if (addr != null) {
                                                    if (!isAddressSelected(addr)) {
                                                        selectionStart = addr
                                                        selectionEnd = addr
                                                    }
                                                    contextMenuAddr = addr
                                                    // Map correct screen display offsets
                                                    contextMenuOffset = DpOffset(position.x.dp / density, position.y.dp / density)
                                                    showContextMenu = true
                                                }
                                            }
                                        }
                                        PointerEventType.Move -> {
                                            if (isMouseDown) {
                                                val addr = getAddressAtOffset(position.x, position.y)
                                                if (addr != null) {
                                                    selectionEnd = addr
                                                }
                                            }
                                        }
                                        PointerEventType.Release -> {
                                            isMouseDown = false
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    for (rowIndex in 0 until visibleRowsCount) {
                        val rowAddress = viewStartAddress + (rowIndex * bytesPerRow)
                        if (rowAddress < endAddress) {
                            val pageStart = (rowAddress / pageSize) * pageSize
                            val cachedPage = memoryCache[pageStart]
                            val offsetInPage = (rowAddress - pageStart).toInt()
                            val rowBytes = ByteArray(bytesPerRow)
                            
                            if (cachedPage != null) {
                                val available = maxOf(0, cachedPage.size - offsetInPage)
                                val toCopy = minOf(bytesPerRow, available)
                                System.arraycopy(cachedPage, offsetInPage, rowBytes, 0, toCopy)
                            }

                            HexRowView(
                                address = rowAddress,
                                bytes = rowBytes,
                                columns = bytesPerRow,
                                selectionMin = selMin,
                                selectionMax = selMax,
                                cursorAddress = selectionEnd,
                                pendingEdits = pendingEdits,
                                hexInputBuffer = hexInputBuffer
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

                // Global Context Menu
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = contextMenuOffset
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Selection to Watchlist", fontSize = 12.sp) },
                        onClick = {
                            val watchStart = selMin ?: contextMenuAddr
                            val watchEnd = selMax ?: contextMenuAddr
                            if (watchStart != null && watchEnd != null) {
                                val selectedLength = (watchEnd - watchStart + 1).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                DebuggerService.addToWatchlist(watchStart, type = "String", byteLength = selectedLength)
                            }
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy (Hex)") },
                        onClick = {
                            copyToClipboard(getSelectedBytesText())
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy (ASCII)") },
                        onClick = {
                            copyToClipboard(getSelectedAsciiText())
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paste Hex Value") },
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

@Composable
fun HexRowView(
    address: Long,
    bytes: ByteArray,
    columns: Int,
    selectionMin: Long?,
    selectionMax: Long?,
    cursorAddress: Long?,
    pendingEdits: Map<Long, Byte>,
    hexInputBuffer: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%016X", address),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(160.dp).padding(start = 8.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Hex data group
        Row(
            modifier = Modifier.width((columns * 24).dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (i in 0 until columns) {
                if (i < bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: bytes[i]
                    val isCursor = cursorAddress == byteAddr
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr >= selectionMin && byteAddr <= selectionMax
                    ByteHexCell(
                        byte = b,
                        isSelected = isSelected,
                        isCursor = isCursor,
                        isPendingEdit = pendingEdits.containsKey(byteAddr),
                        hexInputBuffer = if (isCursor) hexInputBuffer else ""
                    )
                } else {
                    Spacer(Modifier.width(24.dp))
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // ASCII group — extra 1.dp so the last cell's right border isn't clipped
        Row(
            modifier = Modifier.width((columns * 10 + 1).dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (i in 0 until columns) {
                if (i < bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: bytes[i]
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr >= selectionMin && byteAddr <= selectionMax
                    ByteAsciiCell(
                        byte = b,
                        isSelected = isSelected
                    )
                } else {
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
    }
}

@Composable
fun ByteHexCell(
    byte: Byte,
    isSelected: Boolean,
    isCursor: Boolean,
    isPendingEdit: Boolean,
    hexInputBuffer: String
) {
    val displayStr = if (hexInputBuffer.isNotEmpty() && isCursor) {
        hexInputBuffer + "_"
    } else {
        String.format("%02X", byte)
    }

    Box(
        modifier = Modifier
            .width(24.dp)
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
            fontSize = 13.sp,
            color = if (isPendingEdit) PS5ThemeColors.AccentAmber
                    else if (isCursor) PS5ThemeColors.AccentCyan
                    else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun ByteAsciiCell(
    byte: Byte,
    isSelected: Boolean
) {
    val charStr = if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else "."

    Box(
        modifier = Modifier
            .width(10.dp)
            .height(24.dp)
            .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.45f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.7f) else Color.Transparent,
                RoundedCornerShape(2.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = charStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (isSelected) PS5ThemeColors.AccentCyan else Color.Green
        )
    }
}
