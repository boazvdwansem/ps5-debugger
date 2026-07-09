package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ElfSegment(
    val type: String,
    val offset: Long,
    val start: Long,
    val end: Long,
    val length: Long,
    val flags: String
)

data class ElfSection(
    val name: String,
    val type: String,
    val start: Long,
    val end: Long,
    val length: Long,
    val offset: Long
)

@Composable
fun MemoryMapView(
    onJumpToAddress: (Long) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    
    var segments by remember { mutableStateOf<List<ElfSegment>>(emptyList()) }
    var sections by remember { mutableStateOf<List<ElfSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeProcess) {
        val proc = activeProcess
        if (proc == null) {
            segments = emptyList()
            sections = emptyList()
            return@LaunchedEffect
        }
        
        isLoading = true
        errorText = null
        
        coroutineScope.launch {
            try {
                val maps = AppContainer.debuggerUseCase.vmMaps.value
                // Find executable mapping (usually start of code segment) which starts with ELF header.
                // ELF magic: 0x7F 'E' 'L' 'F' -> 0x464C457F
                val elfHeaderMap = maps.firstOrNull { 
                    (it.protections and 1) != 0 && it.name.isNotEmpty() && !it.name.startsWith("[")
                } ?: maps.firstOrNull { (it.protections and 1) != 0 }
                
                if (elfHeaderMap == null) {
                    errorText = "No readable memory maps found"
                    isLoading = false
                    return@launch
                }

                // Read ELF header (usually 64 bytes for ELF64)
                val headerBytesResult = AppContainer.debuggerUseCase.readMemory(elfHeaderMap.start, 64)
                if (headerBytesResult.isFailure) {
                    errorText = "Failed to read process memory"
                    isLoading = false
                    return@launch
                }
                
                val headerBytes = headerBytesResult.getOrThrow()
                var hasElfHeader = headerBytes.size >= 64 &&
                    headerBytes[0] == 0x7F.toByte() &&
                    headerBytes[1] == 'E'.toByte() &&
                    headerBytes[2] == 'L'.toByte() &&
                    headerBytes[3] == 'F'.toByte()

                val parsedSegments = mutableListOf<ElfSegment>()
                val parsedSections = mutableListOf<ElfSection>()

                if (!hasElfHeader) {
                    // Fallback to displaying all Process memory regions as segments
                    maps.forEach { map ->
                        parsedSegments.add(
                            ElfSegment(
                                type = map.getProtString(),
                                offset = map.offset,
                                start = map.start,
                                end = map.end,
                                length = map.size,
                                flags = map.getProtString()
                            )
                        )
                    }
                    segments = parsedSegments
                    sections = emptyList()
                    isLoading = false
                    return@launch
                }

                withContext(Dispatchers.Default) {
                    val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    // ELF64 format offsets
                    val phoff = buffer.getLong(32) // Program header table file offset
                    val shoff = buffer.getLong(40) // Section header table file offset
                    val phentsize = buffer.getShort(54).toInt() and 0xFFFF
                    val phnum = buffer.getShort(56).toInt() and 0xFFFF
                    val shentsize = buffer.getShort(58).toInt() and 0xFFFF
                    val shnum = buffer.getShort(60).toInt() and 0xFFFF
                    val shstrndx = buffer.getShort(62).toInt() and 0xFFFF

                    // Read program headers (Segments)
                    if (phnum > 0 && phentsize >= 56) {
                        val phBytesResult = AppContainer.debuggerUseCase.readMemory(elfHeaderMap.start + phoff, phnum * phentsize)
                        if (phBytesResult.isSuccess) {
                            val phBuf = ByteBuffer.wrap(phBytesResult.getOrThrow()).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until phnum) {
                                val offset = i * phentsize
                                val pType = phBuf.getInt(offset)
                                val pFlags = phBuf.getInt(offset + 4)
                                val pOffset = phBuf.getLong(offset + 8)
                                val pVaddr = phBuf.getLong(offset + 16)
                                val pMemsz = phBuf.getLong(offset + 40)

                                if (pMemsz > 0) {
                                    val typeStr = when (pType) {
                                        1 -> "LOAD"
                                        2 -> "DYNAMIC"
                                        3 -> "INTERP"
                                        4 -> "NOTE"
                                        5 -> "SHLIB"
                                        6 -> "PHDR"
                                        7 -> "TLS"
                                        0x6474e551 -> "GNU_EH_FRAME"
                                        0x6474e552 -> "GNU_STACK"
                                        0x6474e553 -> "GNU_RELRO"
                                        else -> "0x${pType.toString(16).uppercase()}"
                                    }
                                    val flagsStr = buildString {
                                        append(if ((pFlags and 4) != 0) "R" else "-")
                                        append(if ((pFlags and 2) != 0) "W" else "-")
                                        append(if ((pFlags and 1) != 0) "X" else "-")
                                    }
                                    parsedSegments.add(
                                        ElfSegment(
                                            type = typeStr,
                                            offset = pOffset,
                                            start = elfHeaderMap.start + pVaddr,
                                            end = elfHeaderMap.start + pVaddr + pMemsz,
                                            length = pMemsz,
                                            flags = flagsStr
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Read section headers (Sections)
                    if (shnum > 0 && shentsize >= 64) {
                        val shBytesResult = AppContainer.debuggerUseCase.readMemory(elfHeaderMap.start + shoff, shnum * shentsize)
                        if (shBytesResult.isSuccess) {
                            val shBytes = shBytesResult.getOrThrow()
                            val shBuf = ByteBuffer.wrap(shBytes).order(ByteOrder.LITTLE_ENDIAN)
                            
                            // Find section names string table section
                            var shstrtabOffset = 0L
                            var shstrtabSize = 0L
                            if (shstrndx < shnum) {
                                val offset = shstrndx * shentsize
                                shstrtabOffset = shBuf.getLong(offset + 24)
                                shstrtabSize = shBuf.getLong(offset + 32)
                            }

                            // Read string table bytes
                            val strTab = if (shstrtabSize > 0) {
                                val strResult = AppContainer.debuggerUseCase.readMemory(elfHeaderMap.start + shstrtabOffset, shstrtabSize.toInt())
                                if (strResult.isSuccess) strResult.getOrThrow() else ByteArray(0)
                            } else {
                                ByteArray(0)
                            }

                            for (i in 0 until shnum) {
                                val offset = i * shentsize
                                val shNameIdx = shBuf.getInt(offset)
                                val shType = shBuf.getInt(offset + 4)
                                val shAddr = shBuf.getLong(offset + 16)
                                val shOffset = shBuf.getLong(offset + 24)
                                val shSize = shBuf.getLong(offset + 32)

                                if (shSize > 0) {
                                    val name = if (shNameIdx in strTab.indices) {
                                        var len = 0
                                        while (shNameIdx + len < strTab.size && strTab[shNameIdx + len] != 0.toByte()) {
                                            len++
                                        }
                                        String(strTab, shNameIdx, len, Charsets.UTF_8)
                                    } else {
                                        "section_$i"
                                    }

                                    val typeStr = when (shType) {
                                        1 -> "PROGBITS"
                                        2 -> "SYMTAB"
                                        3 -> "STRTAB"
                                        4 -> "RELA"
                                        5 -> "HASH"
                                        6 -> "DYNAMIC"
                                        7 -> "NOTE"
                                        8 -> "NOBITS"
                                        9 -> "REL"
                                        10 -> "SHLIB"
                                        11 -> "DYNSYM"
                                        0x6ffffff6 -> "GNU_HASH"
                                        0x6fffffff -> "VERSYM"
                                        0x6ffffffe -> "VERNEED"
                                        else -> "0x${shType.toString(16).uppercase()}"
                                    }

                                    parsedSections.add(
                                        ElfSection(
                                            name = name,
                                            type = typeStr,
                                            start = elfHeaderMap.start + shAddr,
                                            end = elfHeaderMap.start + shAddr + shSize,
                                            length = shSize,
                                            offset = shOffset
                                        )
                                    )
                                }
                            }
                        }
                    }

                    segments = parsedSegments
                    sections = parsedSections
                }
            } catch (e: Exception) {
                errorText = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(PS5ThemeColors.SecondaryBg)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Memory Map",
                color = PS5ThemeColors.TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Collapse",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PS5ThemeColors.AccentCyan)
            }
        } else if (errorText != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorText!!, color = PS5ThemeColors.StatusRed, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "SEGMENTS",
                        color = PS5ThemeColors.AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                items(segments) { seg ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onJumpToAddress(seg.start)
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = seg.type,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PS5ThemeColors.TextMain
                            )
                            Text(
                                text = seg.flags,
                                fontSize = 11.sp,
                                color = if (seg.flags.contains("X")) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = String.format("0x%X - 0x%X", seg.start, seg.end),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = String.format("%.2f MB", seg.length.toDouble() / (1024 * 1024)),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Divider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
