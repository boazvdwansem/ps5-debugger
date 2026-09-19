package com.osr.ps5debugger.service

import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.DumpRegionEntry
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MemoryDumper {

    fun buildElf64Header(
        entryPoint: Long,
        phNum: Int
    ): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        // e_ident
        buf.put(0x7F.toByte())
        buf.put('E'.code.toByte())
        buf.put('L'.code.toByte())
        buf.put('F'.code.toByte())
        buf.put(2.toByte()) // ELFCLASS64
        buf.put(1.toByte()) // ELFDATA2LSB (little-endian)
        buf.put(1.toByte()) // EV_CURRENT
        buf.put(9.toByte()) // ELFOSABI_FREEBSD
        buf.put(0.toByte()) // ABIVERSION
        for (i in 0 until 7) {
            buf.put(0.toByte()) // padding
        }
        buf.putShort(3.toShort()) // e_type: ET_DYN (Shared object / PRX)
        buf.putShort(0x3E.toShort()) // e_machine: EM_X86_64
        buf.putInt(1) // e_version: EV_CURRENT
        buf.putLong(entryPoint) // e_entry
        buf.putLong(64L) // e_phoff: 64
        buf.putLong(0L) // e_shoff: 0
        buf.putInt(0) // e_flags: 0
        buf.putShort(64.toShort()) // e_ehsize: 64
        buf.putShort(56.toShort()) // e_phentsize: 56
        buf.putShort(phNum.toShort()) // e_phnum
        buf.putShort(64.toShort()) // e_shentsize: 64
        buf.putShort(0.toShort()) // e_shnum
        buf.putShort(0.toShort()) // e_shstrndx
        return buf.array()
    }

    fun buildElf64Phdr(
        pType: Int = 1, // PT_LOAD
        pFlags: Int,
        pOffset: Long,
        pVaddr: Long,
        pPaddr: Long,
        pFilesz: Long,
        pMemsz: Long,
        pAlign: Long = 0x4000L
    ): ByteArray {
        val buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(pType)
        buf.putInt(pFlags)
        buf.putLong(pOffset)
        buf.putLong(pVaddr)
        buf.putLong(pPaddr)
        buf.putLong(pFilesz)
        buf.putLong(pMemsz)
        buf.putLong(pAlign)
        return buf.array()
    }

    /**
     * Merges individual memory segments belonging to the same library or binary into a single
     * DumpRegionEntry, and merges all anonymous/unnamed memory regions into a single unified entry.
     */
    fun mergeLibraryMaps(maps: List<MemoryRange>): List<DumpRegionEntry> {
        if (maps.isEmpty()) return emptyList()

        val result = mutableListOf<DumpRegionEntry>()
        val namedGroups = mutableMapOf<String, MutableList<MemoryRange>>()
        val groupOrder = mutableListOf<String>()
        val unnamedRanges = mutableListOf<MemoryRange>()

        for (map in maps) {
            val trimmedName = map.name.trim()
            if (trimmedName.isEmpty() || trimmedName.equals("unnamed", ignoreCase = true)) {
                unnamedRanges.add(map)
            } else {
                val list = namedGroups.getOrPut(trimmedName) {
                    groupOrder.add(trimmedName)
                    mutableListOf()
                }
                list.add(map)
            }
        }

        // Process named modules/libraries
        for (name in groupOrder) {
            val subRanges = namedGroups[name] ?: continue
            val sortedSubRanges = subRanges.sortedBy { it.start }
            val minStart = sortedSubRanges.first().start
            val maxEnd = sortedSubRanges.maxOf { it.end }
            val combinedProt = sortedSubRanges.fold(0) { acc, r -> acc or r.protections }
            val totalSize = maxEnd - minStart
            result.add(
                DumpRegionEntry(
                    id = "lib_${name}_0x${minStart.toString(16)}",
                    name = name,
                    start = minStart,
                    end = maxEnd,
                    totalSize = totalSize,
                    protections = combinedProt,
                    subRanges = sortedSubRanges,
                    isMergedLibrary = sortedSubRanges.size > 1
                )
            )
        }

        // Merge all unnamed/anonymous regions into a single unified entry
        if (unnamedRanges.isNotEmpty()) {
            val sortedUnnamed = unnamedRanges.sortedBy { it.start }
            val minStart = sortedUnnamed.first().start
            val maxEnd = sortedUnnamed.maxOf { it.end }
            val totalActualSize = sortedUnnamed.sumOf { it.size }
            val combinedProt = sortedUnnamed.fold(0) { acc, r -> acc or r.protections }
            result.add(
                DumpRegionEntry(
                    id = "anon_all_unnamed",
                    name = "unnamed",
                    start = minStart,
                    end = maxEnd,
                    totalSize = totalActualSize,
                    protections = combinedProt,
                    subRanges = sortedUnnamed,
                    isMergedLibrary = sortedUnnamed.size > 1
                )
            )
        }

        return result.sortedBy { it.start }
    }

    suspend fun dumpRegions(
        pid: Int,
        regions: List<DumpRegionEntry>,
        outputDir: File,
        clientPort: DebuggerClientPort,
        useCase: DebuggerUseCase,
        onProgress: (currentRegion: String, progress: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("MemoryDumper: Starting dump of ${regions.size} regions for PID $pid to ${outputDir.absolutePath}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val chunkSize = 16384 // 16KB chunks for highly stable network streaming on mobile and desktop
            val zeroBuffer = ByteArray(chunkSize)

            regions.forEachIndexed { index, entry ->
                val isUnnamed = entry.name.equals("unnamed", ignoreCase = true) || entry.name.isBlank()
                val cleanedName = entry.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "unnamed" }

                // For named modules (e.g. libc.prx, eboot.bin), use the exact module filename so it produces a valid PRX/ELF file
                val fileName = if (isUnnamed) {
                    String.format("dump_%d_unnamed_0x%X.bin", pid, entry.start)
                } else if (entry.name.endsWith(".prx", ignoreCase = true) ||
                           entry.name.endsWith(".bin", ignoreCase = true) ||
                           entry.name.endsWith(".elf", ignoreCase = true) ||
                           entry.name.endsWith(".sprx", ignoreCase = true)) {
                    entry.name
                } else {
                    "${cleanedName}.prx"
                }

                val outputFile = File(outputDir, fileName)

                useCase.log("DUMPER", "Dumping region ${index + 1}/${regions.size}: $fileName (0x${entry.start.toString(16)})", LogEntry.Level.INFO)

                val base = entry.start
                val totalSpan = maxOf(1L, if (isUnnamed) entry.totalSize else entry.end - base)
                var bytesDumpedForEntry = 0L
                var currentFileOffset = 0L

                val sortedRanges = entry.subRanges.sortedBy { it.start }

                // Check if the memory already contains an ELF header at the first segment's start address
                var memoryHasElfHeader = false
                if (!isUnnamed && sortedRanges.isNotEmpty()) {
                    try {
                        val initialBytes = clientPort.readMemory(pid, sortedRanges.first().start, 4)
                        if (initialBytes.size >= 4 &&
                            initialBytes[0] == 0x7F.toByte() &&
                            initialBytes[1] == 'E'.code.toByte() &&
                            initialBytes[2] == 'L'.code.toByte() &&
                            initialBytes[3] == 'F'.code.toByte()) {
                            memoryHasElfHeader = true
                        }
                    } catch (_: Exception) {}
                }

                val synthesizeElfHeader = !isUnnamed && !memoryHasElfHeader
                val headerPageSize = 0x4000L // 16 KB header alignment page for PRX

                FileOutputStream(outputFile).use { fos ->
                    if (synthesizeElfHeader) {
                        val ehdr = buildElf64Header(entryPoint = base, phNum = sortedRanges.size)
                        fos.write(ehdr)
                        currentFileOffset += ehdr.size

                        for (segment in sortedRanges) {
                            var pFlags = 0
                            val prot = segment.protections
                            if ((prot and 1) != 0) pFlags = pFlags or 4 // PF_R
                            if ((prot and 2) != 0) pFlags = pFlags or 2 // PF_W
                            if ((prot and 4) != 0) pFlags = pFlags or 1 // PF_X
                            if (pFlags == 0) pFlags = 4 or 1 // default PF_R | PF_X

                            val segSize = segment.end - segment.start
                            val relOffset = segment.start - base
                            val segFileOffset = headerPageSize + relOffset

                            val phdr = buildElf64Phdr(
                                pType = 1, // PT_LOAD
                                pFlags = pFlags,
                                pOffset = segFileOffset,
                                pVaddr = segment.start,
                                pPaddr = segment.start,
                                pFilesz = segSize,
                                pMemsz = segSize,
                                pAlign = 0x4000L
                            )
                            fos.write(phdr)
                            currentFileOffset += phdr.size
                        }

                        // Pad header region to headerPageSize (0x4000)
                        while (currentFileOffset < headerPageSize && isActive) {
                            val toWrite = minOf(zeroBuffer.size.toLong(), headerPageSize - currentFileOffset).toInt()
                            fos.write(zeroBuffer, 0, toWrite)
                            currentFileOffset += toWrite
                        }
                    }

                    for (segment in sortedRanges) {
                        if (!isActive) break

                        if (!isUnnamed) {
                            // In a PRX file, align segment to its relative file/memory offset
                            val targetOffset = if (synthesizeElfHeader) {
                                headerPageSize + (segment.start - base)
                            } else if (segment.offset in 0 until totalSpan) {
                                segment.offset
                            } else {
                                segment.start - base
                            }

                            if (targetOffset > currentFileOffset) {
                                var gapRemaining = targetOffset - currentFileOffset
                                while (gapRemaining > 0 && isActive) {
                                    val toWrite = minOf(zeroBuffer.size.toLong(), gapRemaining).toInt()
                                    fos.write(zeroBuffer, 0, toWrite)
                                    gapRemaining -= toWrite
                                    currentFileOffset += toWrite
                                    bytesDumpedForEntry += toWrite
                                }
                            }
                        }

                        // Read the segment's memory in chunks from the console
                        var currentAddress = segment.start
                        val endAddress = segment.end

                        while (currentAddress < endAddress && isActive) {
                            val remaining = endAddress - currentAddress
                            val toRead = minOf(chunkSize.toLong(), remaining).toInt()

                            val data = clientPort.readMemory(pid, currentAddress, toRead)
                            fos.write(data)

                            currentAddress += toRead
                            currentFileOffset += toRead
                            bytesDumpedForEntry += toRead

                            val progressFraction = (bytesDumpedForEntry.toFloat() / totalSpan.toFloat()).coerceIn(0f, 1f)
                            onProgress(fileName, progressFraction)
                            yield()
                        }
                    }
                }

                // Verify ELF/PRX header for named binaries
                if (!isUnnamed && outputFile.exists() && outputFile.length() >= 4) {
                    try {
                        val magic = ByteArray(4)
                        FileInputStream(outputFile).use { it.read(magic) }
                        if (magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte() && magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()) {
                            useCase.log("DUMPER", "Verified valid ELF/PRX header for $fileName", LogEntry.Level.INFO)
                        } else {
                            useCase.log("DUMPER", "Note: $fileName dumped without standard ELF magic header", LogEntry.Level.WARN)
                        }
                    } catch (_: Exception) {}
                }

                if (!isActive) {
                    useCase.log("DUMPER", "Dump cancelled by user", LogEntry.Level.WARN)
                    return@withContext Result.failure(IOException("Cancelled"))
                }
            }
            useCase.log("DUMPER", "Successfully dumped all selected regions to ${outputDir.absolutePath}", LogEntry.Level.INFO)
            println("MemoryDumper: Successfully finished dump.")
            Result.success(Unit)
        } catch (e: Exception) {
            useCase.log("DUMPER", "Dump failed: ${e.message}", LogEntry.Level.ERROR)
            println("MemoryDumper: Dump failed with error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun dumpRanges(
        pid: Int,
        regions: List<MemoryRange>,
        outputDir: File,
        clientPort: DebuggerClientPort,
        useCase: DebuggerUseCase,
        onProgress: (currentRegion: String, progress: Float) -> Unit
    ): Result<Unit> = dumpRegions(
        pid = pid,
        regions = mergeLibraryMaps(regions),
        outputDir = outputDir,
        clientPort = clientPort,
        useCase = useCase,
        onProgress = onProgress
    )
}
