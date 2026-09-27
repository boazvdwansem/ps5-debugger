package com.osr.ps5debugger.util

import com.osr.ps5debugger.di.HexCache
import com.osr.ps5debugger.di.DisassemblyCache
import com.osr.ps5debugger.domain.service.SymbolManager
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.infrastructure.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.infrastructure.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.ui.memory.disasm.DisasmLine
import com.osr.ps5debugger.ui.watchlist.SymbolSaveItem
import com.osr.ps5debugger.ui.watchlist.extractJsonObjects
import com.osr.ps5debugger.ui.watchlist.jsonEscape
import com.osr.ps5debugger.ui.watchlist.readJsonRawField
import com.osr.ps5debugger.ui.watchlist.readJsonStringField
import com.osr.ps5debugger.ui.watchlist.watchListFromJson
import com.osr.ps5debugger.ui.watchlist.watchListToJson
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SavedSessionInfo(
    val file: File,
    val name: String,
    val description: String,
    val createdAt: Long,
    val processName: String,
    val pid: Int,
    val titleId: String?,
    val contentId: String?,
    val regionsCount: Int,
    val totalMemoryBytes: Long,
    val hasDisassembly: Boolean,
    val fileSizeBytes: Long
)

data class SessionRegionDescriptor(
    val name: String,
    val start: Long,
    val end: Long,
    val protections: Int,
    val offset: Long = 0L,
    val flags: Long = 0L,
    val hasHex: Boolean = false,
    val hasDisassembly: Boolean = false,
    val cachedBytes: Long = 0L,
    val memoryEntry: String? = null,
    val disasmEntry: String? = null
)

object SessionManager {

    fun getDefaultSessionsDir(): File {
        val userHome = System.getProperty("user.home")
        val baseDir = if (!userHome.isNullOrBlank()) File(userHome, ".ps5debugger") else File(System.getProperty("java.io.tmpdir"), ".ps5debugger")
        val sessionsDir = File(baseDir, "sessions")
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs()
        }
        return sessionsDir
    }

    fun listSavedSessions(dir: File = getDefaultSessionsDir()): List<SavedSessionInfo> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".ps5session", ignoreCase = true) } ?: return emptyList()
        val list = mutableListOf<SavedSessionInfo>()
        for (f in files) {
            try {
                val info = readSessionInfo(f)
                if (info != null) {
                    list.add(info)
                }
            } catch (_: Exception) {}
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun deleteSession(file: File): Boolean {
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun readSessionInfo(file: File): SavedSessionInfo? {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("session.json") ?: return null
            val json = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val name = readJsonStringField(json, "name") ?: file.nameWithoutExtension
            val description = readJsonStringField(json, "description") ?: ""
            val createdAt = readJsonRawField(json, "createdAt")?.toLongOrNull() ?: file.lastModified()
            val processName = readJsonStringField(json, "processName") ?: "Unknown"
            val pid = readJsonRawField(json, "pid")?.toIntOrNull() ?: 0
            val titleId = readJsonStringField(json, "titleId")
            val contentId = readJsonStringField(json, "contentId")
            val regionsCount = readJsonRawField(json, "regionsCount")?.toIntOrNull() ?: 0
            val totalMemoryBytes = readJsonRawField(json, "totalMemoryBytes")?.toLongOrNull() ?: 0L
            val hasDisassembly = readJsonRawField(json, "hasDisassembly")?.toBooleanStrictOrNull() ?: false

            return SavedSessionInfo(
                file = file,
                name = name,
                description = description,
                createdAt = createdAt,
                processName = processName,
                pid = pid,
                titleId = titleId,
                contentId = contentId,
                regionsCount = regionsCount,
                totalMemoryBytes = totalMemoryBytes,
                hasDisassembly = hasDisassembly,
                fileSizeBytes = file.length()
            )
        }
    }

    fun saveSession(
        name: String,
        description: String,
        targetDir: File,
        excludeHex: Boolean,
        excludeDisassembly: Boolean,
        selectedRegions: List<MemoryRange>,
        activeProcess: Process?,
        activeProcessInfo: Ps5ProcessInfo?,
        allVmMaps: List<MemoryRange>,
        watchlist: List<WatchItem>,
        symbols: Map<Long, String>,
        discoveredFunctions: List<Long>,
        discoveredJumpTargets: List<Long>,
        elfEntryPoint: Long?
    ): Result<File> {
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val sanitizedName = name.trim().replace("[^a-zA-Z0-9._-]".toRegex(), "_").ifEmpty { "session" }
            val targetFile = File(targetDir, "$sanitizedName.ps5session")

            val pageSize = 65536L
            val chunkSize = 512 * 1024L
            val regionDescriptors = mutableListOf<SessionRegionDescriptor>()
            var totalMemoryBytes = 0L
            var totalHasDisasm = false

            // Prepare memory entries and disasm entries to write
            val memoryPayloads = mutableMapOf<String, List<Pair<Long, ByteArray>>>()
            val disasmPayloads = mutableMapOf<String, List<DisasmLine>>()

            for (reg in selectedRegions) {
                var regCachedBytes = 0L
                val memEntryName = "memory/0x${reg.start.toString(16)}_0x${reg.end.toString(16)}.bin"
                val disasmEntryName = "disassembly/0x${reg.start.toString(16)}_0x${reg.end.toString(16)}.bin"

                var hasHexData = false
                if (!excludeHex) {
                    val pages = mutableListOf<Pair<Long, ByteArray>>()
                    if (reg.localData != null && reg.localData.isNotEmpty()) {
                        val localData = reg.localData
                        var offset = 0
                        var currAddr = reg.start
                        while (offset < localData.size) {
                            val len = minOf(pageSize.toInt(), localData.size - offset)
                            val page = ByteArray(pageSize.toInt())
                            System.arraycopy(localData, offset, page, 0, len)
                            pages.add(currAddr to page)
                            regCachedBytes += len
                            offset += len
                            currAddr += pageSize
                        }
                    } else {
                        var p = (reg.start / pageSize) * pageSize
                        while (p < reg.end) {
                            val pageBytes = HexCache.hexCache[p]
                            if (pageBytes != null) {
                                pages.add(p to pageBytes)
                                regCachedBytes += pageBytes.size
                            }
                            p += pageSize
                        }
                    }
                    if (pages.isNotEmpty()) {
                        hasHexData = true
                        memoryPayloads[memEntryName] = pages
                        totalMemoryBytes += regCachedBytes
                    }
                }

                var hasDisasmData = false
                if (!excludeDisassembly) {
                    val lines = mutableListOf<DisasmLine>()
                    DisassemblyCache.instructionsCache.forEach { (key, cachedLines) ->
                        if (key.startsWith("${reg.start}_${reg.end}")) {
                            lines.addAll(cachedLines)
                        }
                    }
                    val distinctLines = lines.distinctBy { it.instr.addr }.sortedBy { it.instr.addr }
                    if (distinctLines.isNotEmpty()) {
                        hasDisasmData = true
                        totalHasDisasm = true
                        disasmPayloads[disasmEntryName] = distinctLines
                    }
                }

                regionDescriptors.add(
                    SessionRegionDescriptor(
                        name = reg.name,
                        start = reg.start,
                        end = reg.end,
                        protections = reg.protections,
                        offset = reg.offset,
                        flags = 0L,
                        hasHex = hasHexData,
                        hasDisassembly = hasDisasmData,
                        cachedBytes = regCachedBytes,
                        memoryEntry = if (hasHexData) memEntryName else null,
                        disasmEntry = if (hasDisasmData) disasmEntryName else null
                    )
                )
            }

            // Build session.json
            val jsonContent = buildString {
                appendLine("{")
                append("  \"version\": 1,\n")
                append("  \"name\": \"").append(jsonEscape(name)).append("\",\n")
                append("  \"description\": \"").append(jsonEscape(description)).append("\",\n")
                append("  \"createdAt\": ").append(System.currentTimeMillis()).append(",\n")
                append("  \"processName\": \"").append(jsonEscape(activeProcessInfo?.name ?: activeProcess?.name ?: "Unknown")).append("\",\n")
                append("  \"pid\": ").append(activeProcess?.pid ?: 0).append(",\n")
                append("  \"titleId\": ").append(activeProcessInfo?.titleId?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",\n")
                append("  \"contentId\": ").append(activeProcessInfo?.contentId?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",\n")
                append("  \"path\": ").append(activeProcessInfo?.path?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",\n")
                append("  \"elfEntryPoint\": ").append(elfEntryPoint ?: "null").append(",\n")
                append("  \"regionsCount\": ").append(regionDescriptors.size).append(",\n")
                append("  \"totalMemoryBytes\": ").append(totalMemoryBytes).append(",\n")
                append("  \"hasDisassembly\": ").append(totalHasDisasm).append(",\n")

                // Regions
                append("  \"regions\": [\n")
                regionDescriptors.forEachIndexed { idx, r ->
                    append("    {")
                    append("\"name\":\"").append(jsonEscape(r.name)).append("\",")
                    append("\"start\":").append(r.start).append(",")
                    append("\"end\":").append(r.end).append(",")
                    append("\"protections\":").append(r.protections).append(",")
                    append("\"offset\":").append(r.offset).append(",")
                    append("\"flags\":").append(r.flags).append(",")
                    append("\"hasHex\":").append(r.hasHex).append(",")
                    append("\"hasDisassembly\":").append(r.hasDisassembly).append(",")
                    append("\"cachedBytes\":").append(r.cachedBytes).append(",")
                    append("\"memoryEntry\":").append(r.memoryEntry?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",")
                    append("\"disasmEntry\":").append(r.disasmEntry?.let { "\"${jsonEscape(it)}\"" } ?: "null")
                    append("}")
                    if (idx != regionDescriptors.lastIndex) append(",")
                    appendLine()
                }
                append("  ],\n")

                // All vmMaps for offline memory layout
                append("  \"allVmMaps\": [\n")
                val mapsToSave = if (allVmMaps.isNotEmpty()) allVmMaps else selectedRegions
                mapsToSave.forEachIndexed { idx, m ->
                    append("    {")
                    append("\"name\":\"").append(jsonEscape(m.name)).append("\",")
                    append("\"start\":").append(m.start).append(",")
                    append("\"end\":").append(m.end).append(",")
                    append("\"protections\":").append(m.protections).append(",")
                    append("\"offset\":").append(m.offset)
                    append("}")
                    if (idx != mapsToSave.lastIndex) append(",")
                    appendLine()
                }
                append("  ],\n")

                // Watchlist
                append("  \"watchlist\": ")
                val watchlistJson = watchListToJson(watchlist)
                appendLine(watchlistJson.replace("\n", "\n  "))
                append(",\n")

                // Custom symbols
                append("  \"symbols\": [\n")
                val symbolList = symbols.entries.mapNotNull { (addr, symName) ->
                    val map = mapsToSave.firstOrNull { addr >= it.start && addr < it.end } ?: return@mapNotNull null
                    val offset = addr - map.start
                    val isFunc = discoveredFunctions.contains(addr)
                    Triple(map.name, offset, isFunc to symName)
                }
                symbolList.forEachIndexed { index, (mapName, offset, data) ->
                    val (isFunc, symName) = data
                    append("    {")
                    append("\"mapName\":\"").append(jsonEscape(mapName)).append("\",")
                    append("\"offset\":").append(offset).append(",")
                    append("\"name\":\"").append(jsonEscape(symName)).append("\",")
                    append("\"isFunction\":").append(isFunc)
                    append("}")
                    if (index != symbolList.lastIndex) append(",")
                    appendLine()
                }
                append("  ],\n")

                // Discovered functions & jump targets
                append("  \"discoveredFunctions\": [").append(discoveredFunctions.joinToString(",")).append("],\n")
                append("  \"discoveredJumpTargets\": [").append(discoveredJumpTargets.joinToString(",")).append("]\n")
                append("}")
            }

            // Write ZIP file
            FileOutputStream(targetFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        // 1. Write session.json
                        zos.putNextEntry(ZipEntry("session.json"))
                        val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
                        zos.write(jsonBytes)
                        zos.closeEntry()

                        // 2. Write memory payloads
                        for ((memEntryName, pages) in memoryPayloads) {
                            zos.putNextEntry(ZipEntry(memEntryName))
                            val dout = DataOutputStream(zos)
                            dout.writeInt(pages.size)
                            for ((pageAddr, pageData) in pages) {
                                dout.writeLong(pageAddr)
                                dout.writeInt(pageData.size)
                                dout.write(pageData)
                            }
                            dout.flush()
                            zos.closeEntry()
                        }

                        // 3. Write disassembly payloads
                        for ((disasmEntryName, lines) in disasmPayloads) {
                            zos.putNextEntry(ZipEntry(disasmEntryName))
                            val dout = DataOutputStream(zos)
                            dout.writeInt(lines.size)
                            for (line in lines) {
                                val instr = line.instr
                                dout.writeLong(instr.addr)
                                dout.writeLong(instr.ripRelTarget)
                                dout.writeLong(instr.memDisp)
                                dout.writeInt(instr.length)
                                dout.writeInt(instr.kind)
                                dout.writeInt(instr.memBaseReg)
                                dout.writeInt(instr.memIndexReg)
                                dout.writeInt(instr.memScale)
                                dout.writeInt(instr.mnemonic)
                                dout.writeInt(instr.mnemonicLo)
                                dout.writeInt(line.bytes.size)
                                dout.write(line.bytes)
                                dout.writeUTF(line.symbolName ?: "")
                                dout.writeInt(line.xrefs.size)
                                for (xref in line.xrefs) {
                                    dout.writeLong(xref)
                                }
                            }
                            dout.flush()
                            zos.closeEntry()
                        }
                    }
                }
            }

            return Result.success(targetFile)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun loadSession(file: File): Result<String> {
        try {
            if (!file.exists()) return Result.failure(IllegalArgumentException("File does not exist: ${file.absolutePath}"))

            ZipFile(file).use { zip ->
                val jsonEntry = zip.getEntry("session.json")
                    ?: return Result.failure(IllegalStateException("Not a valid session file (missing session.json)"))
                val json = zip.getInputStream(jsonEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val sessionName = readJsonStringField(json, "name") ?: file.nameWithoutExtension
                val processName = readJsonStringField(json, "processName") ?: "Unknown"
                val pid = readJsonRawField(json, "pid")?.toIntOrNull() ?: 1
                val titleId = readJsonStringField(json, "titleId") ?: ""
                val contentId = readJsonStringField(json, "contentId") ?: ""
                val path = readJsonStringField(json, "path") ?: "/app0/eboot.bin"
                val elfEntryPoint = readJsonRawField(json, "elfEntryPoint")?.toLongOrNull()

                // Parse regions
                val regions = mutableListOf<SessionRegionDescriptor>()
                val regionsIdx = json.indexOf("\"regions\"")
                if (regionsIdx != -1) {
                    val startBrack = json.indexOf('[', regionsIdx)
                    if (startBrack != -1) {
                        var depth = 1
                        var i = startBrack + 1
                        while (i < json.length && depth > 0) {
                            if (json[i] == '[') depth++ else if (json[i] == ']') depth--
                            i++
                        }
                        if (i <= json.length) {
                            val subJson = json.substring(startBrack, i)
                            val objs = extractJsonObjects(subJson)
                            for (obj in objs) {
                                val rName = readJsonStringField(obj, "name") ?: "map"
                                val rStart = readJsonRawField(obj, "start")?.toLongOrNull() ?: 0L
                                val rEnd = readJsonRawField(obj, "end")?.toLongOrNull() ?: 0L
                                val rProt = readJsonRawField(obj, "protections")?.toIntOrNull() ?: 7
                                val rOffset = readJsonRawField(obj, "offset")?.toLongOrNull() ?: 0L
                                val rFlags = readJsonRawField(obj, "flags")?.toLongOrNull() ?: 0L
                                val rHasHex = readJsonRawField(obj, "hasHex")?.toBooleanStrictOrNull() ?: false
                                val rHasDisasm = readJsonRawField(obj, "hasDisassembly")?.toBooleanStrictOrNull() ?: false
                                val rCachedBytes = readJsonRawField(obj, "cachedBytes")?.toLongOrNull() ?: 0L
                                val rMemEntry = readJsonStringField(obj, "memoryEntry")
                                val rDisasmEntry = readJsonStringField(obj, "disasmEntry")
                                regions.add(
                                    SessionRegionDescriptor(
                                        rName, rStart, rEnd, rProt, rOffset, rFlags,
                                        rHasHex, rHasDisasm, rCachedBytes, rMemEntry, rDisasmEntry
                                    )
                                )
                            }
                        }
                    }
                }

                // Parse allVmMaps
                val allVmMaps = mutableListOf<MemoryRange>()
                val mapsIdx = json.indexOf("\"allVmMaps\"")
                if (mapsIdx != -1) {
                    val startBrack = json.indexOf('[', mapsIdx)
                    if (startBrack != -1) {
                        var depth = 1
                        var i = startBrack + 1
                        while (i < json.length && depth > 0) {
                            if (json[i] == '[') depth++ else if (json[i] == ']') depth--
                            i++
                        }
                        if (i <= json.length) {
                            val subJson = json.substring(startBrack, i)
                            val objs = extractJsonObjects(subJson)
                            for (obj in objs) {
                                val mName = readJsonStringField(obj, "name") ?: "map"
                                val mStart = readJsonRawField(obj, "start")?.toLongOrNull() ?: 0L
                                val mEnd = readJsonRawField(obj, "end")?.toLongOrNull() ?: 0L
                                val mProt = readJsonRawField(obj, "protections")?.toIntOrNull() ?: 7
                                val mOffset = readJsonRawField(obj, "offset")?.toLongOrNull() ?: 0L
                                allVmMaps.add(
                                    MemoryRange(
                                        name = mName,
                                        start = mStart,
                                        end = mEnd,
                                        offset = mOffset,
                                        protections = mProt
                                    )
                                )
                            }
                        }
                    }
                }

                val finalVmMaps = if (allVmMaps.isNotEmpty()) allVmMaps else regions.map {
                    MemoryRange(
                        name = it.name,
                        start = it.start,
                        end = it.end,
                        offset = it.offset,
                        protections = it.protections
                    )
                }

                // Parse watchlist
                val watchlist = mutableListOf<WatchItem>()
                val watchlistIdx = json.indexOf("\"watchlist\"")
                if (watchlistIdx != -1) {
                    val startBrack = json.indexOf('[', watchlistIdx)
                    if (startBrack != -1) {
                        var depth = 1
                        var i = startBrack + 1
                        while (i < json.length && depth > 0) {
                            if (json[i] == '[') depth++ else if (json[i] == ']') depth--
                            i++
                        }
                        if (i <= json.length) {
                            val subJson = json.substring(startBrack, i)
                            watchlist.addAll(watchListFromJson(subJson))
                        }
                    }
                }

                // Parse symbols
                val symbols = mutableListOf<SymbolSaveItem>()
                val symbolsIdx = json.indexOf("\"symbols\"")
                if (symbolsIdx != -1) {
                    val startBrack = json.indexOf('[', symbolsIdx)
                    if (startBrack != -1) {
                        var depth = 1
                        var i = startBrack + 1
                        while (i < json.length && depth > 0) {
                            if (json[i] == '[') depth++ else if (json[i] == ']') depth--
                            i++
                        }
                        if (i <= json.length) {
                            val subJson = json.substring(startBrack, i)
                            val objs = extractJsonObjects(subJson)
                            for (obj in objs) {
                                val mapName = readJsonStringField(obj, "mapName") ?: continue
                                val offset = readJsonRawField(obj, "offset")?.toLongOrNull() ?: continue
                                val symName = readJsonStringField(obj, "name") ?: continue
                                val isFunc = readJsonRawField(obj, "isFunction")?.toBooleanStrictOrNull() ?: false
                                symbols.add(SymbolSaveItem(mapName, offset, symName, isFunc))
                            }
                        }
                    }
                }

                // Parse functions and jump targets
                val functions = mutableListOf<Long>()
                val funcIdx = json.indexOf("\"discoveredFunctions\"")
                if (funcIdx != -1) {
                    val startBrack = json.indexOf('[', funcIdx)
                    val endBrack = json.indexOf(']', startBrack)
                    if (startBrack != -1 && endBrack != -1) {
                        val items = json.substring(startBrack + 1, endBrack).split(",")
                        for (it in items) {
                            it.trim().toLongOrNull()?.let { f -> functions.add(f) }
                        }
                    }
                }

                val jumpTargets = mutableListOf<Long>()
                val jumpIdx = json.indexOf("\"discoveredJumpTargets\"")
                if (jumpIdx != -1) {
                    val startBrack = json.indexOf('[', jumpIdx)
                    val endBrack = json.indexOf(']', startBrack)
                    if (startBrack != -1 && endBrack != -1) {
                        val items = json.substring(startBrack + 1, endBrack).split(",")
                        for (it in items) {
                            it.trim().toLongOrNull()?.let { j -> jumpTargets.add(j) }
                        }
                    }
                }

                // 1. Reset caches safely
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    HexCache.clearHexCache()
                    DisassemblyCache.instructionsCache.clear()
                    DisassemblyCache.disassemblyProgressCache.clear()
                    SymbolManager.symbolNames.clear()
                    SymbolManager.discoveredFunctions.clear()
                    SymbolManager.discoveredJumpTargets.clear()
                }
                AppContainer.debuggerUseCase.clearWatchlist()

                val chunkSize = 512 * 1024L

                // 2. Unpack Memory
                val loadedPages = mutableMapOf<Long, ByteArray>()
                for (reg in regions) {
                    val memEntryName = reg.memoryEntry
                    if (memEntryName != null) {
                        val entry = zip.getEntry(memEntryName)
                        if (entry != null) {
                            zip.getInputStream(entry).use { inStream ->
                                val din = DataInputStream(BufferedInputStream(inStream))
                                val numPages = din.readInt()
                                for (p in 0 until numPages) {
                                    val pageAddr = din.readLong()
                                    val pageLen = din.readInt()
                                    val pageBytes = ByteArray(pageLen)
                                    din.readFully(pageBytes)
                                    loadedPages[pageAddr] = pageBytes
                                }
                            }
                        }
                    }

                    // Register progress for this region so Hex Viewer recognizes it as complete
                    val regionKey = "${reg.start}-${reg.end}"
                    val progress = HexCache.hexProgressCache.getOrPut(regionKey) { HexCache.HexRegionProgress() }
                    progress.isComplete = true
                    progress.progress = 1.0f
                    progress.loadedBytes = reg.end - reg.start

                    var c = reg.start
                    while (c < reg.end) {
                        progress.completedChunks.add(c)
                        c += chunkSize
                    }
                }

                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    for ((pageAddr, pageBytes) in loadedPages) {
                        HexCache.hexCache[pageAddr] = pageBytes
                    }
                }

                // 3. Unpack Disassembly
                for (reg in regions) {
                    val disasmEntryName = reg.disasmEntry
                    if (disasmEntryName != null) {
                        val entry = zip.getEntry(disasmEntryName)
                        if (entry != null) {
                            val mapRegion = finalVmMaps.firstOrNull { it.start == reg.start && it.end == reg.end }
                                ?: MemoryRange(name = reg.name, start = reg.start, end = reg.end, offset = reg.offset, protections = reg.protections)

                            zip.getInputStream(entry).use { inStream ->
                                val din = DataInputStream(BufferedInputStream(inStream))
                                val numLines = din.readInt()
                                val restoredLines = ArrayList<DisasmLine>(numLines)
                                for (l in 0 until numLines) {
                                    val addr = din.readLong()
                                    val ripRelTarget = din.readLong()
                                    val memDisp = din.readLong()
                                    val length = din.readInt()
                                    val kind = din.readInt()
                                    val memBaseReg = din.readInt()
                                    val memIndexReg = din.readInt()
                                    val memScale = din.readInt()
                                    val mnemonic = din.readInt()
                                    val mnemonicLo = din.readInt()
                                    val bytesLen = din.readInt()
                                    val bytes = ByteArray(bytesLen)
                                    din.readFully(bytes)
                                    val symName = din.readUTF().takeIf { it.isNotEmpty() }
                                    val xrefsCount = din.readInt()
                                    val xrefs = ArrayList<Long>(xrefsCount)
                                    for (x in 0 until xrefsCount) {
                                        xrefs.add(din.readLong())
                                    }
                                    val instr = Ps5DisasmInstr(
                                        addr = addr,
                                        ripRelTarget = ripRelTarget,
                                        memDisp = memDisp,
                                        length = length,
                                        kind = kind,
                                        memBaseReg = memBaseReg,
                                        memIndexReg = memIndexReg,
                                        memScale = memScale,
                                        mnemonic = mnemonic,
                                        mnemonicLo = mnemonicLo
                                    )
                                    restoredLines.add(DisasmLine(instr, bytes, mapRegion, symName, xrefs))
                                }

                                val mapKey = "${reg.start}_${reg.end}_${reg.name}"
                                val list = DisassemblyCache.getInstructions(mapKey)
                                list.clear()
                                list.addAll(restoredLines)
                                DisassemblyCache.disassemblyProgressCache[mapKey] = 1.0f
                            }
                        }
                    }
                }

                // 4. Restore Symbols and Functions
                for (sym in symbols) {
                    val map = finalVmMaps.firstOrNull { it.name == sym.mapName }
                    if (map != null) {
                        val absAddr = map.start + sym.offset
                        SymbolManager.renameSymbol(absAddr, sym.name)
                        if (sym.isFunction && !SymbolManager.discoveredFunctions.contains(absAddr)) {
                            SymbolManager.discoveredFunctions.add(absAddr)
                        }
                    }
                }
                for (f in functions) {
                    if (!SymbolManager.discoveredFunctions.contains(f)) {
                        SymbolManager.discoveredFunctions.add(f)
                    }
                }
                SymbolManager.discoveredFunctions.sortBy { it.toULong() }

                for (j in jumpTargets) {
                    if (!SymbolManager.discoveredJumpTargets.contains(j)) {
                        SymbolManager.discoveredJumpTargets.add(j)
                    }
                }
                SymbolManager.discoveredJumpTargets.sortBy { it.toULong() }
                SymbolManager.elfEntryPoint = elfEntryPoint

                // 5. Restore Watchlist
                for (item in watchlist) {
                    AppContainer.debuggerUseCase.addWatchItem(item)
                }

                // 6. Set Process & Offline State
                val proc = Process(pid = pid, name = processName)
                val procInfo = Ps5ProcessInfo(
                    pid = pid,
                    name = processName,
                    titleId = titleId,
                    contentId = contentId,
                    path = path
                )
                AppContainer.isOfflineSession = true
                AppContainer.loadedSessionName = sessionName
                AppContainer.debuggerUseCase.setOfflineSession(proc, procInfo, finalVmMaps)

                AppContainer.debuggerUseCase.log(
                    "SESSION",
                    "Loaded offline session '$sessionName' for $processName ($titleId) with ${regions.size} processed memory regions.",
                    com.osr.ps5debugger.domain.model.LogEntry.Level.INFO
                )

                return Result.success(sessionName)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}




