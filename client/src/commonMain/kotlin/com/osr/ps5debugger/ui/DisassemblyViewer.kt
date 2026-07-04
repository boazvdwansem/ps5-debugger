package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.ui.copyToClipboard
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class DisasmLine(
    val instr: Ps5DisasmInstr,
    val bytes: ByteArray
)

@Composable
fun ByteHexCellCompact(byte: Byte) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(20.dp)
            .background(Color.Transparent)
            .border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%02X", byte),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.LightGray
        )
    }
}

@Composable
fun ByteAsciiCellCompact(byte: Byte) {
    val charStr = if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else "."
    Box(
        modifier = Modifier.width(9.dp).height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = charStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.Green
        )
    }
}

@Composable
fun DisassemblyViewer(
    activeMap: MemoryRange?,
    jumpToAddress: Long? = null,
    modifier: Modifier = Modifier,
    selectionStart: Long? = null,
    selectionEnd: Long? = null,
    onSelectionChanged: ((Long?, Long?) -> Unit)? = null,
    onJumpToHex: ((Long) -> Unit)? = null,
    showHexDetails: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val client = AppContainer.clientAdapter.client
    val pid = AppContainer.debuggerUseCase.activeProcess.value?.pid
    
    var startAddress by remember { mutableStateOf(activeMap?.start ?: 0L) }
    var goToAddressText by remember { mutableStateOf("") }
    val instructions = remember { mutableStateListOf<DisasmLine>() }
    var isLoading by remember { mutableStateOf(false) }
    
    // Context Menu & Help State
    var showContextMenu by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var contextMenuAddr by remember { mutableStateOf<Long?>(null) }
    var contextMenuBytes by remember { mutableStateOf(byteArrayOf()) }
    var contextMenuDisasm by remember { mutableStateOf("") }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    val listState = rememberLazyListState()
    
    // Auto-update startAddress when map changes or jumpToAddress is requested
    LaunchedEffect(activeMap, jumpToAddress) {
        if (activeMap != null) {
            val target = jumpToAddress
            if (target != null && target >= activeMap.start && target < activeMap.end) {
                startAddress = target
                goToAddressText = target.toString(16).uppercase()
                
                // Try to find the item in our loaded list and scroll to it
                val index = instructions.indexOfFirst { it.instr.addr == target }
                if (index != -1) {
                    listState.animateScrollToItem(index)
                }
            } else if (startAddress < activeMap.start || startAddress >= activeMap.end) {
                startAddress = activeMap.start
            }
        }
    }
    
    // Load initial disassembly instructions when startAddress or pid changes
    LaunchedEffect(pid, activeMap, startAddress) {
        if (pid != null && activeMap != null) {
            isLoading = true
            try {
                val len = minOf(65536L, activeMap.end - startAddress).toInt() // Load larger initial chunk (64KB)
                if (len > 0) {
                    coroutineScope.launch {
                        val disasmDeferred = async { client.disassembleRegion(pid, startAddress, len, 500) }
                        val bytesDeferred = async {
                            try {
                                client.readMemory(pid, startAddress, len)
                            } catch (_: Exception) {
                                ByteArray(0)
                            }
                        }
                        
                        val rawInstrs = disasmDeferred.await()
                        val rawBytes = bytesDeferred.await()
                        
                        val lines = rawInstrs.map { instr ->
                            val offset = (instr.addr - startAddress).toInt()
                            val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                rawBytes.copyOfRange(offset, offset + instr.length)
                            } else {
                                ByteArray(0)
                            }
                            DisasmLine(instr, instrBytes)
                        }
                        
                        instructions.clear()
                        instructions.addAll(lines)
                        isLoading = false
                        
                        // Scroll to the jump target if selected
                        if (jumpToAddress != null) {
                            val index = instructions.indexOfFirst { it.instr.addr == jumpToAddress }
                            if (index != -1) {
                                listState.scrollToItem(index)
                            }
                        }
                    }
                } else {
                    isLoading = false
                }
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log("DISASM", "Disassembly failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                isLoading = false
            }
        }
    }

    // Infinite Scroll & Bidirectional Loading
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    // Load next block (scrolling down)
    LaunchedEffect(firstVisibleIndex, instructions.size, isLoading) {
        if (!isLoading && activeMap != null && pid != null && instructions.isNotEmpty()) {
            if (firstVisibleIndex + 50 >= instructions.size) {
                val lastLine = instructions.last()
                val nextStart = lastLine.instr.addr + lastLine.instr.length
                if (nextStart < activeMap.end) {
                    val nextLen = minOf(32768L, activeMap.end - nextStart).toInt()
                    if (nextLen > 0) {
                        try {
                            val newInstrs = client.disassembleRegion(pid, nextStart, nextLen, 200)
                            val rawBytes = try {
                                client.readMemory(pid, nextStart, nextLen)
                            } catch (_: Exception) {
                                ByteArray(0)
                            }
                            
                            val newLines = newInstrs.map { instr ->
                                val offset = (instr.addr - nextStart).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                    rawBytes.copyOfRange(offset, offset + instr.length)
                                } else {
                                    ByteArray(0)
                                }
                                DisasmLine(instr, instrBytes)
                            }
                            
                            instructions.addAll(newLines)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // Prepend previous block (scrolling up)
    LaunchedEffect(firstVisibleIndex, isLoading) {
        if (!isLoading && activeMap != null && pid != null && instructions.isNotEmpty()) {
            if (firstVisibleIndex <= 5) {
                val firstLine = instructions.first()
                if (firstLine.instr.addr > activeMap.start) {
                    val prevStart = maxOf(activeMap.start, firstLine.instr.addr - 32768L)
                    val prevLen = (firstLine.instr.addr - prevStart).toInt()
                    if (prevLen > 0) {
                        try {
                            val newInstrs = client.disassembleRegion(pid, prevStart, prevLen, 200)
                            val rawBytes = try {
                                client.readMemory(pid, prevStart, prevLen)
                            } catch (_: Exception) {
                                ByteArray(0)
                            }
                            
                            val newLines = newInstrs.map { instr ->
                                val offset = (instr.addr - prevStart).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                    rawBytes.copyOfRange(offset, offset + instr.length)
                                } else {
                                    ByteArray(0)
                                }
                                DisasmLine(instr, instrBytes)
                            }
                            
                            if (newLines.isNotEmpty()) {
                                instructions.addAll(0, newLines)
                                // Offset listState index to prevent scrolling jumps
                                listState.scrollToItem(
                                    listState.firstVisibleItemIndex + newLines.size,
                                    listState.firstVisibleItemScrollOffset
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Navigation & Help Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = goToAddressText,
                onValueChange = { goToAddressText = it },
                label = { Text("Go to Address (Hex)") },
                modifier = Modifier.width(180.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PS5ThemeColors.AccentCyan,
                    unfocusedBorderColor = PS5ThemeColors.BorderColor,
                    focusedLabelColor = PS5ThemeColors.AccentCyan,
                    unfocusedLabelColor = PS5ThemeColors.TextMuted
                )
            )
            
            Button(
                onClick = {
                    val addr = goToAddressText.trim().toLongOrNull(16)
                    if (activeMap != null && addr != null && addr in activeMap.start..activeMap.end) {
                        startAddress = addr
                    } else {
                        AppContainer.debuggerUseCase.log("DISASM", "Address out of range or invalid", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PS5ThemeColors.AccentCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Go", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            IconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Debugger Capabilities Info",
                    tint = PS5ThemeColors.AccentCyan
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            // Prev/Next page navigation (shifts the viewing window backwards or forwards)
            Button(
                onClick = {
                    if (activeMap != null) {
                        val prevAddr = maxOf(activeMap.start, startAddress - 8192)
                        startAddress = prevAddr
                        goToAddressText = prevAddr.toString(16).uppercase()
                    }
                },
                enabled = activeMap != null && startAddress > activeMap.start,
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) {
                Text("Prev Page", color = PS5ThemeColors.TextMain)
            }
            
            Button(
                onClick = {
                    if (activeMap != null && instructions.isNotEmpty()) {
                        val lastLine = instructions.last()
                        val nextAddr = lastLine.instr.addr + lastLine.instr.length
                        if (nextAddr < activeMap.end) {
                            startAddress = nextAddr
                            goToAddressText = nextAddr.toString(16).uppercase()
                        }
                    }
                },
                enabled = activeMap != null && instructions.isNotEmpty() && (instructions.last().instr.addr + instructions.last().instr.length) < activeMap.end,
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
            ) {
                Text("Next Page", color = PS5ThemeColors.TextMain)
            }
        }
        
        if (activeMap == null || pid == null) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Select a Process and Virtual Memory Map to disassemble", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (isLoading && instructions.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PS5ThemeColors.Surface, RoundedCornerShape(4.dp))
                        .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    itemsIndexed(instructions) { idx, line ->
                        val isSelected = isInstructionSelected(line.instr, selectionStart, selectionEnd)
                        
                        DisasmRow(
                            line = line,
                            isSelected = isSelected,
                            onAddressClicked = { addr, isShift ->
                                if (isShift && selectionStart != null) {
                                    onSelectionChanged?.invoke(selectionStart, addr)
                                } else {
                                    onSelectionChanged?.invoke(addr, addr)
                                }
                            },
                            onAddressRightClicked = { addr, bytes, disasmText, offset ->
                                contextMenuAddr = addr
                                contextMenuBytes = bytes
                                contextMenuDisasm = disasmText
                                contextMenuOffset = offset
                                showContextMenu = true
                            },
                            showHexDetails = showHexDetails
                        )
                    }
                }

                // Dropdown Context Menu
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = contextMenuOffset
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy Address", fontSize = 12.sp) },
                        onClick = {
                            if (contextMenuAddr != null) {
                                copyToClipboard(String.format("0x%012X", contextMenuAddr))
                            }
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Hex Bytes", fontSize = 12.sp) },
                        onClick = {
                            if (contextMenuBytes.isNotEmpty()) {
                                copyToClipboard(contextMenuBytes.joinToString(" ") { String.format("%02X", it) })
                            }
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Disassembly", fontSize = 12.sp) },
                        onClick = {
                            if (contextMenuDisasm.isNotEmpty()) {
                                copyToClipboard(contextMenuDisasm)
                            }
                            showContextMenu = false
                        }
                    )
                    if (onJumpToHex != null && contextMenuAddr != null) {
                        HorizontalDivider(color = PS5ThemeColors.BorderColor)
                        DropdownMenuItem(
                            text = { Text("Jump to Hex Viewer", fontSize = 12.sp, color = PS5ThemeColors.AccentCyan) },
                            onClick = {
                                onJumpToHex(contextMenuAddr!!)
                                showContextMenu = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Debugger capabilities documentation modal
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "PS5 Debugger Features",
                    color = PS5ThemeColors.AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("• **Connection Target**: Attach to a single target with CMD_DEBUG_ATTACH (sets up an async interrupt channel back to the client).", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                    Text("• **Software Breakpoints**: Up to 30 slots, transparent 0xCC injection.", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                    Text("• **Hardware Watchpoints**: Up to 4 DR0-DR3 slots with read / write / read-write and 1/2/4/8-byte granularity.", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                    Text("• **Thread Control**: List, suspend, resume, single-step, per-thread step.", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                    Text("• **Full Register Access**: General-purpose, FPU + YMM (AVX), debug registers, and the FS/GS segment base addresses (TLS pointers).", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                    Text("• **Process Operations**: Continue / stop / halt the whole process from one command.", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                    Text("• **Interrupt Handshake**: Asynchronous interrupt packets delivered on a separate TCP connection so the client never polls.", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close", color = PS5ThemeColors.AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PS5ThemeColors.SecondaryBg
        )
    }
}

private fun isInstructionSelected(instr: Ps5DisasmInstr, start: Long?, end: Long?): Boolean {
    if (start == null || end == null) return false
    val lo = minOf(start, end)
    val hi = maxOf(start, end)
    return instr.addr in lo..hi
}

private val regNames = setOf(
    "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15", "rip",
    "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi", "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
    "ax", "cx", "dx", "bx", "sp", "bp", "si", "di",
    "al", "cl", "dl", "bl", "ah", "ch", "dh", "bh"
)

@Composable
fun DisasmRow(
    line: DisasmLine,
    isSelected: Boolean,
    onAddressClicked: (Long, Boolean) -> Unit,
    onAddressRightClicked: (Long, ByteArray, String, DpOffset) -> Unit,
    showHexDetails: Boolean = false
) {
    val instr = line.instr
    val mnemonic = getMnemonic(instr)
    val operands = formatOperands(instr)
    val infoText = getInfoText(instr)
    
    // Ghidra Dark Theme Colors
    val addressColor = Color(0xFF90A4AE)      // Gray-blue lavender
    val byteColor = Color(0xFF808080)         // Dark Gray
    val mnemonicColor = when {
        instr.isCall || instr.isRet -> Color(0xFFF50057) // Bright Pink-red for call/ret
        instr.isJmp || instr.isCondJmp -> Color(0xFFFF4081) // Pink for branches
        else -> Color(0xFFFFB74D) // Ghidra Orange-Gold for standard instructions
    }
    val commentColor = Color(0xFF78909C)       // Muted gray-green comment color
    
    val bytesStr = line.bytes.joinToString(" ") { String.format("%02X", it) }
    val fullDisasmString = "$mnemonic $operands"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.35f) else Color.Transparent)
            .pointerInput(instr.addr) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            val change = event.changes.first()
                            val isShift = event.keyboardModifiers.isShiftPressed
                            val isSecondary = event.buttons.isSecondaryPressed
                            
                            if (isSecondary) {
                                val density = this.density
                                val offset = DpOffset(change.position.x.dp / density, change.position.y.dp / density)
                                onAddressRightClicked(instr.addr, line.bytes, fullDisasmString, offset)
                            } else {
                                onAddressClicked(instr.addr, isShift)
                            }
                        }
                    }
                }
            }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Address
        Text(
            text = String.format("%012X", instr.addr),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = addressColor,
            modifier = Modifier.width(110.dp)
        )
        
        if (!showHexDetails) {
            // Raw hex bytes string (standard mode)
            Text(
                text = bytesStr,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = byteColor,
                modifier = Modifier.width(130.dp),
                maxLines = 1
            )
        }
        
        // Mnemonic
        Text(
            text = mnemonic,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = mnemonicColor,
            modifier = Modifier.width(75.dp)
        )
        
        // Operands with Ghidra syntax highlight
        Box(modifier = Modifier.width(180.dp)) {
            val annotatedOps = buildAnnotatedString {
                val regex = Regex("(\\[|\\]|\\+|\\-|\\*|,|\\s+)|(0x[0-9A-Fa-f]+|[0-9]+)|([a-zA-Z0-9_]+)")
                val matches = regex.findAll(operands)
                for (match in matches) {
                    val token = match.value
                    val isReg = regNames.contains(token.lowercase())
                    val isNumber = token.startsWith("0x") || token.all { it.isDigit() }
                    
                    when {
                        isReg -> {
                            withStyle(style = SpanStyle(color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)) { // Teal/cyan for registers
                                append(token)
                            }
                        }
                        isNumber -> {
                            withStyle(style = SpanStyle(color = Color(0xFFFF8A65))) { // Orange/Red for numbers
                                append(token)
                            }
                        }
                        token == "[" || token == "]" -> {
                            withStyle(style = SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)) { // Yellow brackets
                                append(token)
                            }
                        }
                        else -> {
                            withStyle(style = SpanStyle(color = Color(0xFFECEFF1))) { // Muted off-white
                                append(token)
                            }
                        }
                    }
                }
                if (matches.none()) {
                    append(operands)
                }
            }
            Text(
                text = annotatedOps,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        
        if (showHexDetails) {
            Spacer(Modifier.width(16.dp))
            
            // Unified Hex cells (interactive block styling)
            Row(
                modifier = Modifier.width(180.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (i in 0 until 8) { // show up to 8 bytes in cells on desktop split view
                    if (i < line.bytes.size) {
                        ByteHexCellCompact(byte = line.bytes[i])
                    } else {
                        Spacer(Modifier.width(20.dp))
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Unified ASCII cells
            Row(
                modifier = Modifier.width(80.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (i in 0 until 8) {
                    if (i < line.bytes.size) {
                        ByteAsciiCellCompact(byte = line.bytes[i])
                    } else {
                        Spacer(Modifier.width(9.dp))
                    }
                }
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        // Info / Comments (target, xref etc)
        if (infoText.isNotEmpty()) {
            Text(
                text = " ; $infoText",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = commentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun getMnemonic(instr: Ps5DisasmInstr): String {
    return when (instr.mnemonicLo) {
        9 -> "ADD"
        31 -> "AND"
        149 -> "DEC"
        25 -> "INC"
        47 -> "JB"
        48 -> "JBE"
        49 -> "JCXZ"
        50 -> "JECXZ"
        53 -> "JL"
        54 -> "JLE"
        55 -> "JMP"
        56 -> "JNB"
        57 -> "JNBE"
        58 -> "JNL"
        59 -> "JNLE"
        60 -> "JNO"
        61 -> "JNP"
        62 -> "JNS"
        63 -> "JNZ"
        64 -> "JO"
        65 -> "JP"
        66 -> "JRCXZ"
        67 -> "JS"
        68 -> "JZ"
        71 -> "CALL"
        107 -> "CMP"
        140 -> "LEA"
        180 -> "MOV"
        231 -> "NOP"
        233 -> "OR"
        97 -> "POP"
        153 -> "PUSH"
        179 -> "RET"
        234 -> "SHL"
        237 -> "SHR"
        27 -> "TEST"
        235 -> "XOR"
        else -> "INSTR_${instr.mnemonicLo}"
    }
}

private fun formatOperands(instr: Ps5DisasmInstr): String {
    val ops = mutableListOf<String>()
    
    if (instr.isRipRel && instr.ripRelTarget != 0L) {
        ops.add(String.format("[rip + 0x%X]", instr.memDisp))
    } else if (instr.hasMemOp) {
        val base = getRegisterName(instr.memBaseReg)
        val index = getRegisterName(instr.memIndexReg)
        val scale = instr.memScale
        val disp = instr.memDisp
        
        val mem = StringBuilder("[")
        var hasPrev = false
        if (base.isNotEmpty()) {
            mem.append(base)
            hasPrev = true
        }
        if (index.isNotEmpty()) {
            if (hasPrev) mem.append(" + ")
            mem.append(index)
            if (scale > 1) {
                mem.append("*").append(scale)
            }
            hasPrev = true
        }
        if (disp != 0L) {
            if (hasPrev) {
                if (disp > 0) mem.append(" + ") else mem.append(" - ")
                mem.append(String.format("0x%X", kotlin.math.abs(disp)))
            } else {
                mem.append(String.format("0x%X", disp))
            }
        } else if (!hasPrev) {
            mem.append("0")
        }
        mem.append("]")
        ops.add(mem.toString())
    }
    
    if (instr.ripRelTarget != 0L && !instr.isRipRel) {
        ops.add(String.format("0x%X", instr.ripRelTarget))
    }
    
    return ops.joinToString(", ")
}

private fun getInfoText(instr: Ps5DisasmInstr): String {
    return when {
        instr.isRipRel && instr.ripRelTarget != 0L -> String.format("target: 0x%X", instr.ripRelTarget)
        instr.ripRelTarget != 0L -> String.format("target: 0x%X", instr.ripRelTarget)
        instr.isCall -> "subroutine call"
        instr.isRet -> "return from subroutine"
        else -> ""
    }
}

private fun getRegisterName(reg: Int): String {
    return when (reg) {
        37 -> "eax"
        38 -> "ecx"
        39 -> "edx"
        40 -> "ebx"
        41 -> "esp"
        42 -> "ebp"
        43 -> "esi"
        44 -> "edi"
        53 -> "rax"
        54 -> "rcx"
        55 -> "rdx"
        56 -> "rbx"
        57 -> "rsp"
        58 -> "rbp"
        59 -> "rsi"
        60 -> "rdi"
        61 -> "r8"
        62 -> "r9"
        63 -> "r10"
        64 -> "r11"
        65 -> "r12"
        66 -> "r13"
        67 -> "r14"
        68 -> "r15"
        197 -> "rip"
        0 -> ""
        else -> "reg_$reg"
    }
}
