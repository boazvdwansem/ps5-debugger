package com.osr.ps5debugger.util

import com.osr.ps5debugger.domain.model.MemoryRange

object ElfUtil {
    fun isElf(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.toByte() && bytes[2] == 'L'.toByte() && bytes[3] == 'F'.toByte()
    }

    fun getSegments(bytes: ByteArray, fileName: String): List<MemoryRange> {
        if (!isElf(bytes) || bytes.size < 64) return emptyList()
        val is64 = bytes[4] == 2.toByte()
        val phoff = if (is64) readLongLE(bytes, 0x20) else readIntLE(bytes, 0x1C).toLong() and 0xFFFFFFFFL
        val phentsize = (if (is64) readShortLE(bytes, 0x36) else readShortLE(bytes, 0x2A)).toInt() and 0xFFFF
        val phnum = (if (is64) readShortLE(bytes, 0x38) else readShortLE(bytes, 0x2C)).toInt() and 0xFFFF
        
        val segments = mutableListOf<MemoryRange>()
        
        // Detect if we should apply a default Orbis rebase (0x400000)
        var baseOffset = 0L
        for (i in 0 until phnum) {
            val offset = (phoff + i * phentsize).toInt()
            if (offset + 8 > bytes.size) break
            val type = readIntLE(bytes, offset)
            if (type == 1) { // PT_LOAD
                val vaddr = if (is64) readLongLE(bytes, offset + 16) else readIntLE(bytes, offset + 8).toLong() and 0xFFFFFFFFL
                if (vaddr == 0L) {
                    baseOffset = 0x400000L
                }
                break
            }
        }

        for (i in 0 until phnum) {
            val offset = (phoff + i * phentsize).toInt()
            if (offset + (if (is64) 56 else 32) > bytes.size) break
            
            val type = readIntLE(bytes, offset)
            if (type == 1) { // PT_LOAD
                val flags = if (is64) readIntLE(bytes, offset + 4) else readIntLE(bytes, offset + 24)
                val fileOff = if (is64) readLongLE(bytes, offset + 8) else readIntLE(bytes, offset + 4).toLong() and 0xFFFFFFFFL
                val vaddr = if (is64) readLongLE(bytes, offset + 16) else readIntLE(bytes, offset + 8).toLong() and 0xFFFFFFFFL
                val filesz = if (is64) readLongLE(bytes, offset + 32) else readIntLE(bytes, offset + 16).toLong() and 0xFFFFFFFFL
                val memsz = if (is64) readLongLE(bytes, offset + 40) else readIntLE(bytes, offset + 20).toLong() and 0xFFFFFFFFL
                
                if (filesz > 0 && fileOff + filesz <= bytes.size) {
                    val segmentData = bytes.copyOfRange(fileOff.toInt(), (fileOff + filesz).toInt())
                    val start = vaddr + baseOffset
                    segments.add(MemoryRange(
                        name = "$fileName [PT_LOAD $i]",
                        start = start,
                        end = start + memsz,
                        offset = fileOff,
                        protections = (if ((flags and 4) != 0) 1 else 0) or // R
                                      (if ((flags and 2) != 0) 2 else 0) or // W
                                      (if ((flags and 1) != 0) 4 else 0),   // X
                        localData = segmentData
                    ))
                }
            }
        }
        return segments
    }

    fun getMergedModule(bytes: ByteArray, fileName: String): MemoryRange? {
        val segments = getSegments(bytes, fileName)
        if (segments.isEmpty()) return null
        
        val minStart = segments.minOf { it.start }
        val maxEnd = segments.maxOf { it.end }
        val totalSize = (maxEnd - minStart).toInt()
        
        // Use a reasonable limit to prevent OOM on sparse ELFs (though rare for eboots)
        if (totalSize > 256 * 1024 * 1024) { 
             // If > 256MB, we might want to keep them separate to save memory
        }

        val mergedData = ByteArray(totalSize)
        for (seg in segments) {
            val offsetInMerged = (seg.start - minStart).toInt()
            seg.localData?.let { 
                it.copyInto(mergedData, offsetInMerged)
            }
        }
        
        val titleId = findTitleId(bytes)
        
        return MemoryRange(
            name = fileName,
            start = minStart,
            end = maxEnd,
            offset = 0,
            protections = segments.fold(0) { acc, seg -> acc or seg.protections },
            localData = mergedData,
            subRanges = segments,
            titleId = titleId
        )
    }

    private fun findTitleId(bytes: ByteArray): String? {
        // Search for CUSAxxxx or PPSAxxx pattern in the first 64KB
        val limit = minOf(bytes.size, 65536)
        val sb = StringBuilder()
        val pattern = Regex("[CP][UP]SA[0-9]{5}")
        
        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                if (sb.length >= 9) {
                    val match = pattern.find(sb.toString())
                    if (match != null) return match.value
                }
                sb.setLength(0)
            }
        }
        // Check last sequence
        if (sb.length >= 9) {
            val match = pattern.find(sb.toString())
            if (match != null) return match.value
        }
        return null
    }

    fun getEntryPoint(bytes: ByteArray): Long? {
        if (!isElf(bytes) || bytes.size < 64) return null
        val is64 = bytes[4] == 2.toByte()
        val entry = if (is64) readLongLE(bytes, 0x18) else readIntLE(bytes, 0x18).toLong() and 0xFFFFFFFFL
        
        val phoff = if (is64) readLongLE(bytes, 0x20) else readIntLE(bytes, 0x1C).toLong() and 0xFFFFFFFFL
        val phentsize = (if (is64) readShortLE(bytes, 0x36) else readShortLE(bytes, 0x2A)).toInt() and 0xFFFF
        val phnum = (if (is64) readShortLE(bytes, 0x38) else readShortLE(bytes, 0x2C)).toInt() and 0xFFFF
        
        for (i in 0 until phnum) {
            val offset = (phoff + i * phentsize).toInt()
            if (offset + 8 > bytes.size) break
            val type = readIntLE(bytes, offset)
            if (type == 1) {
                val vaddr = if (is64) readLongLE(bytes, offset + 16) else readIntLE(bytes, offset + 8).toLong() and 0xFFFFFFFFL
                return if (vaddr == 0L) entry + 0x400000L else entry
            }
        }
        return entry
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Short {
        if (offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readLongLE(bytes: ByteArray, offset: Int): Long {
        if (offset + 8 > bytes.size) return 0
        return (readIntLE(bytes, offset).toLong() and 0xFFFFFFFFL) or
               (readIntLE(bytes, offset + 4).toLong() shl 32)
    }
}
