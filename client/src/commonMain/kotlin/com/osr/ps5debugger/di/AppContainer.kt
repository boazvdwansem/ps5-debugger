package com.osr.ps5debugger.di

import com.osr.ps5debugger.adapters.network.SocketDebuggerAdapter
import com.osr.ps5debugger.adapters.storage.FileLogStorageAdapter
import com.osr.ps5debugger.domain.service.DebuggerDomainService
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

object AppContainer {
    var debugMockEnabled by androidx.compose.runtime.mutableStateOf(
        try { com.osr.ps5debugger.util.DefaultIpHelper.isMockEnabled() } catch (_: Exception) { false }
    )
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val symbolNames = mutableStateMapOf<Long, String>()
    val discoveredFunctions = mutableStateListOf<Long>()
    val discoveredJumpTargets = mutableStateListOf<Long>()
    var elfEntryPoint: Long? = null

    val instructionsCache = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<com.osr.ps5debugger.ui.DisasmLine>>()
    val disassemblyProgressCache = mutableStateMapOf<String, Float>()
    val hexCache = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateMap<Long, ByteArray>>()
    
    sealed class IconState {
        object Loading : IconState()
        data class Success(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : IconState()
        object NotFound : IconState()
    }
    val iconCache = mutableStateMapOf<String, IconState>()
    
    val titleIdToName = mutableStateMapOf<String, String>()
    val titleIdToVersion = mutableStateMapOf<String, String>()
    val titleIdToPlatform = mutableStateMapOf<String, String>()
    val unsupportedCommands = mutableSetOf<Int>()

    val clientAdapter = SocketDebuggerAdapter(appScope)
    val logStorageAdapter = com.osr.ps5debugger.adapters.storage.FileLogStorageAdapter()
    val cheatStorageAdapter = com.osr.ps5debugger.adapters.storage.FileCheatStorageAdapter()

    val debuggerUseCase: DebuggerUseCase = DebuggerDomainService(
        clientPort = clientAdapter,
        logPort = logStorageAdapter,
        cheatStorage = cheatStorageAdapter
    )

    fun getInstructions(mapKey: String): androidx.compose.runtime.snapshots.SnapshotStateList<com.osr.ps5debugger.ui.DisasmLine> {
        return instructionsCache.getOrPut(mapKey) { androidx.compose.runtime.mutableStateListOf() }
    }

    fun getHexCache(mapKey: String): androidx.compose.runtime.snapshots.SnapshotStateMap<Long, ByteArray> {
        return hexCache.getOrPut(mapKey) { androidx.compose.runtime.mutableStateMapOf() }
    }

    fun clearCache(mapKey: String) {
        instructionsCache.remove(mapKey)
        disassemblyProgressCache.remove(mapKey)
        hexCache.remove(mapKey)
    }

    private fun isLikelyPrintableAscii(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val printable = bytes.count { b ->
            val v = b.toInt() and 0xFF
            v == 0x09 || v == 0x0A || v == 0x0D || (v in 0x20..0x7E)
        }
        return printable.toDouble() / bytes.size.toDouble() > 0.75
    }

    private fun isLikelyCodePrefix(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (isLikelyPrintableAscii(bytes)) return false
        val first = bytes[0].toInt() and 0xFF
        val second = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0
        val commonX86Starts = setOf(
            0x00, 0x0F, 0x18, 0x20, 0x29, 0x2E, 0x31, 0x33, 0x39, 0x3B, 0x40, 0x41, 0x48,
            0x49, 0x4C, 0x4D, 0x50, 0x51, 0x52, 0x53, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A,
            0x5B, 0x5C, 0x5D, 0x5E, 0x5F, 0x60, 0x61, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
            0x69, 0x6A, 0x6C, 0x6E, 0x70, 0x71, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7C,
            0x7D, 0x80, 0x81, 0x83, 0x85, 0x88, 0x89, 0x8B, 0x8D, 0x8E, 0x90, 0x91, 0xA1,
            0xA3, 0xB8, 0xB9, 0xBA, 0xBB, 0xBE, 0xBF, 0xC3, 0xC5, 0xC7, 0xE8, 0xEB, 0xF3,
            0xFF
        )
        if (first in commonX86Starts || second in commonX86Starts) return true
        return (first and 0xF0) in setOf(0x40, 0x50, 0x60, 0x70, 0x80, 0x90)
    }

    private suspend fun findSaneCodeStart(map: com.osr.ps5debugger.domain.model.MemoryRange): Long? {
        if (map.size <= 0) return null
        val probeLen = minOf(64 * 1024L, map.size).toInt()
        val probe = try {
            debuggerUseCase.readMemory(map.start, probeLen).getOrNull() ?: return null
        } catch (_: Exception) {
            return null
        }
        if (probe.isEmpty()) return null

        for (offset in 0 until probe.size step 1) {
            if (offset + 8 > probe.size) break
            val chunk = probe.copyOfRange(offset, minOf(offset + 8, probe.size))
            if (chunk.size < 4) continue
            if (isLikelyCodePrefix(chunk)) return map.start + offset.toLong()
        }
        return null
    }

    suspend fun getDisassemblyStartForMap(map: com.osr.ps5debugger.domain.model.MemoryRange): Long {
        val explicitCandidates = discoveredFunctions.filter { it >= map.start && it < map.end }.sorted()
        if (explicitCandidates.isNotEmpty()) return explicitCandidates.first()
        val entry = elfEntryPoint
        if (entry != null && entry >= map.start && entry < map.end) return entry
        val saneCode = findSaneCodeStart(map)
        if (saneCode != null && saneCode >= map.start && saneCode < map.end) return saneCode
        return map.start
    }

    fun getSymbolName(address: Long, isFunction: Boolean): String {
        return symbolNames[address] ?: if (isFunction) {
            "FUN_${address.toString(16).uppercase().padStart(8, '0')}"
        } else {
            "DAT_${address.toString(16).uppercase().padStart(8, '0')}"
        }
    }

    fun getSymbolNameForTarget(address: Long, isCall: Boolean): String {
        val isFunction = isCall || discoveredFunctions.contains(address)
        return getSymbolName(address, isFunction)
    }

    fun renameSymbol(address: Long, newName: String) {
        if (newName.isBlank()) {
            symbolNames.remove(address)
        } else {
            symbolNames[address] = newName
        }
    }

    var onNavigateToMemory: ((Long) -> Unit)? = null
    var onNavigateRequested: ((Long, Int) -> Unit)? = null
    var onMcpServerToggled: ((Boolean) -> Unit)? = null
    var onCreateCheatRequested: ((com.osr.ps5debugger.domain.model.Cheat) -> Unit)? = null
    var filePicker: com.osr.ps5debugger.ports.inbound.FilePicker? = null
    var defaultDumpPath: String = ""

    private val metadataMutex = Mutex()
    private val resolvedThisSession = mutableSetOf<String>()
    private var lastSyncedIp: String? = null

    private fun getIconCacheFile(titleId: String): File {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".ps5debugger/icons")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${titleId.trim().uppercase()}.png")
    }

    suspend fun fetchMetadata(titleId: String, consoleIp: String) = coroutineScope {
        val tid = titleId.trim().uppercase()
        fun isBad(n: String?) = n.isNullOrEmpty() || n == "Unknown" || n.lowercase().let { it.contains("eboot.bin") || it.endsWith(".elf") || it.endsWith(".bin") }
        
        // 1. Try local icon cache immediately
        if (iconCache[titleId] !is IconState.Success) {
            val localFile = getIconCacheFile(tid)
            if (localFile.exists()) {
                try {
                    val bytes = localFile.readBytes()
                    val bitmap = com.osr.ps5debugger.util.decodeImage(bytes)
                    if (bitmap != null) {
                        iconCache[titleId] = IconState.Success(bitmap)
                    }
                } catch (_: Exception) {}
            }
        }

        val needsIcon = iconCache[titleId] !is IconState.Success
        val needsSync = !resolvedThisSession.contains(tid)
        val needsDbSync = lastSyncedIp != consoleIp
        
        if (!needsIcon && !needsSync && !needsDbSync) return@coroutineScope
        
        metadataMutex.withLock {
            if (lastSyncedIp != consoleIp || !resolvedThisSession.contains(tid) || (needsIcon && iconCache[titleId] !is IconState.Success)) {
                
                val ftpClient = com.osr.ps5debugger.network.Ps5FtpClient(consoleIp)
                try {
                    // One-time App DB Sync per connection
                    if (lastSyncedIp != consoleIp) {
                        try {
                            val dbPath = "/system_data/priv/mms/app.db"
                            val localDbFile = File(System.getProperty("java.io.tmpdir"), "ps5_app_sync.db")
                            val baos = java.io.ByteArrayOutputStream()
                            ftpClient.downloadFile(dbPath, baos)
                            localDbFile.writeBytes(baos.toByteArray())
                            
                            val reader = com.osr.ps5debugger.util.SqliteReader(localDbFile.absolutePath)
                            val metaList = reader.queryAppMetadata()
                            metaList.forEach { meta ->
                                titleIdToName[meta.titleId] = meta.name
                                titleIdToPlatform[meta.titleId] = meta.platform
                                debuggerUseCase.updateGameName(meta.titleId, meta.name)
                                debuggerUseCase.updateGamePlatform(meta.titleId, meta.platform)
                            }
                            reader.close()
                            localDbFile.delete()
                            lastSyncedIp = consoleIp
                            debuggerUseCase.log("METADATA", "Synced ${metaList.size} titles from PS5 app database", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                        } catch (e: Exception) {
                            debuggerUseCase.log("METADATA", "Failed to sync app database: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                        }
                    }

                    if (iconCache[titleId] !is IconState.Success) {
                        iconCache[titleId] = IconState.Loading
                    }
                    
                    coroutineScope {
                        // Icon Task
                        if (iconCache[titleId] !is IconState.Success) {
                            launch {
                                try {
                                    val iconPath = "/user/appmeta/$tid/icon0.png"
                                    val baos = java.io.ByteArrayOutputStream()
                                    ftpClient.downloadFile(iconPath, baos)
                                    val bytes = baos.toByteArray()
                                    val bitmap = com.osr.ps5debugger.util.decodeImage(bytes)
                                    if (bitmap != null) {
                                        iconCache[titleId] = IconState.Success(bitmap)
                                        getIconCacheFile(tid).writeBytes(bytes)
                                    } else {
                                        iconCache[titleId] = IconState.NotFound
                                    }
                                } catch (e: Exception) {
                                    if (e !is CancellationException) iconCache[titleId] = IconState.NotFound
                                }
                            }
                        }

                        // Name & Version Sync Task
                        launch {
                            val currentName = titleIdToName[titleId]
                            val needsName = isBad(currentName)
                            
                            val metaFiles = if (needsName) {
                                listOf("param.sfo", "PARAM.SFO", "param.json", "pronunciation.xml", "changeinfo/changeinfo.xml")
                            } else {
                                listOf("changeinfo/changeinfo.xml")
                            }

                            for (metaFile in metaFiles) {
                                try {
                                    val path = "/user/appmeta/$tid/$metaFile"
                                    val baos = java.io.ByteArrayOutputStream()
                                    ftpClient.downloadFile(path, baos)
                                    val bytes = baos.toByteArray()
                                    
                                    if (metaFile.contains("changeinfo.xml")) {
                                        val text = bytes.decodeToString()
                                        val ver = Regex("app_ver=\"([^\"]+)\"").findAll(text).lastOrNull()?.groupValues?.get(1)
                                        if (!ver.isNullOrEmpty()) {
                                            titleIdToVersion[titleId] = ver
                                            debuggerUseCase.updateGameVersion(titleId, ver)
                                        }
                                    } else {
                                        val resolved = when {
                                            metaFile.endsWith(".sfo", true) -> com.osr.ps5debugger.util.SfoUtil.getTitleName(bytes)
                                            metaFile == "param.json" -> {
                                                val text = bytes.decodeToString()
                                                Regex("\"(?:title|name)\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                                            }
                                            metaFile == "pronunciation.xml" -> {
                                                val text = bytes.decodeToString()
                                                Regex("<text display=\"1\">([^<]+)</text>").find(text)?.groupValues?.get(1)
                                            }
                                            else -> null
                                        }
                                        if (!resolved.isNullOrEmpty() && isBad(resolved).not()) {
                                            titleIdToName[titleId] = resolved
                                            debuggerUseCase.updateGameName(titleId, resolved)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            resolvedThisSession.add(tid)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        debuggerUseCase.log("METADATA", "Error syncing metadata for $titleId: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                    }
                } finally {
                    ftpClient.disconnect()
                }
            }
        }
    }

    suspend fun preloadRegionInBackground(map: com.osr.ps5debugger.domain.model.MemoryRange, pid: Int?, jumpToAddress: Long? = null) {
        val chunkSize = 64 * 1024L
        val focusAddr = (jumpToAddress ?: map.start).coerceIn(map.start, maxOf(map.start, map.end - 1))
        val focusChunkIdx = ((focusAddr - map.start) / chunkSize).toInt()
        val key = "${map.start}_${map.end}_${map.name}"
        val disasmKey = "${map.start}_${map.end}_${map.name}_$focusChunkIdx"
        val hexKey = key

        // 1. Preload Hex Page 0 if not already in cache
        val pageSize = 65536
        val pageStart = (map.start / pageSize) * pageSize
        val hexMap = getHexCache(hexKey)
        if (!hexMap.containsKey(pageStart)) {
            try {
                val pageData = ByteArray(pageSize)
                val readLen = minOf(pageSize.toLong(), map.end - map.start).toInt()
                val data = if (map.localData != null) {
                    map.localData.copyOfRange(0, minOf(readLen, map.localData.size))
                } else if (pid != null) {
                    clientAdapter.client.readMemory(pid, pageStart, readLen)
                } else ByteArray(0)
                if (data.isNotEmpty()) {
                    System.arraycopy(data, 0, pageData, 0, data.size)
                    hexMap[pageStart] = pageData
                }
            } catch (_: Exception) {}
        }

        // 2. Preload Disassembly Window if not already in cache
        val mainList = getInstructions(key)
        val disasmList = getInstructions(disasmKey)
        if (mainList.isNotEmpty() || disasmList.isNotEmpty()) return

        // Non-executable remote regions don't need disassembly
        if ((map.protections and 4) == 0 && map.localData == null) {
            withContext(Dispatchers.Main) {
                disassemblyProgressCache[key] = 1.0f
                disassemblyProgressCache[disasmKey] = 1.0f
            }
            return
        }

        val totalRegionChunks = ((map.end - map.start + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val isLargeRegion = totalRegionChunks > 32

        val targetRequests = if (isLargeRegion) {
            val windowRadius = 16 // 32 chunks = 2MB window
            val startIdx = maxOf(0, focusChunkIdx - windowRadius)
            val endIdx = minOf(totalRegionChunks, focusChunkIdx + windowRadius + 1)
            (startIdx until endIdx).map { idx ->
                val start = map.start + idx * chunkSize
                val len = minOf(chunkSize, map.end - start).toInt()
                start to len
            }
        } else {
            generateSequence(map.start) { start ->
                val next = start + chunkSize
                if (start < map.end) next else null
            }.takeWhile { it < map.end }.map { start ->
                start to minOf(chunkSize, map.end - start).toInt()
            }.toList()
        }

        val client = clientAdapter.client
        val chunkLines = mutableListOf<com.osr.ps5debugger.ui.DisasmLine>()

        for ((chunkStart, len) in targetRequests) {
            try {
                val rawBytes = if (map.localData != null) {
                    val offset = (chunkStart - map.start).toInt()
                    map.localData.copyOfRange(offset, minOf(offset + len, map.localData.size))
                } else if (pid != null) {
                    client.readMemory(pid, chunkStart, len)
                } else ByteArray(0)

                if (rawBytes.isNotEmpty()) {
                    val syncAddrs = (discoveredFunctions.toSet() + symbolNames.keys.toSet() + discoveredJumpTargets.toSet())
                    val rawInstrs = if (map.localData != null) {
                        com.osr.ps5debugger.util.LocalDisassembler.disassemble(rawBytes, chunkStart, syncAddrs)
                    } else if (pid != null) {
                        client.disassembleRegion(pid, chunkStart, len, 4000)
                    } else emptyList()

                    val lines = if (map.localData != null) {
                        rawInstrs.map { instr ->
                            val offset = (instr.addr - chunkStart).toInt()
                            val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) rawBytes.copyOfRange(offset, offset + instr.length) else ByteArray(0)
                            com.osr.ps5debugger.ui.DisasmLine(instr, instrBytes, map, symbolNames[instr.addr])
                        }
                    } else {
                        val stringRanges = mutableListOf<Pair<Int, Int>>()
                        var strStart = -1
                        for (i in rawBytes.indices) {
                            val v = rawBytes[i].toInt() and 0xFF
                            if (v in 0x20..0x7E || v == 0x09) {
                                if (strStart < 0) strStart = i
                            } else {
                                if (strStart >= 0) {
                                    val slen = i - strStart
                                    if (slen >= 8 || (v == 0 && slen >= 4)) {
                                        stringRanges.add(strStart to if (v == 0) slen + 1 else slen)
                                    }
                                    strStart = -1
                                }
                            }
                        }
                        if (strStart >= 0 && rawBytes.size - strStart >= 8) stringRanges.add(strStart to (rawBytes.size - strStart))

                        val zeroRanges = mutableListOf<Pair<Int, Int>>()
                        var zStart = -1
                        for (i in 0..rawBytes.size) {
                            val isZero = i < rawBytes.size && rawBytes[i].toInt() == 0
                            if (isZero && zStart < 0) zStart = i
                            if (!isZero && zStart >= 0) {
                                val zlen = i - zStart
                                if (zlen >= 2) zeroRanges.add(zStart to zlen)
                                zStart = -1
                            }
                        }

                        val stringInstrs = stringRanges.map { (off, slen) ->
                            com.osr.ps5debugger.protocol.Ps5DisasmInstr(
                                addr = chunkStart + off,
                                ripRelTarget = 0,
                                memDisp = 0,
                                length = slen,
                                kind = 0x100,
                                memBaseReg = 0,
                                memIndexReg = 0,
                                memScale = 0,
                                mnemonic = 0,
                                mnemonicLo = 0
                            )
                        }

                        val zeroInstrs = mutableListOf<com.osr.ps5debugger.protocol.Ps5DisasmInstr>()
                        for ((off, zlen) in zeroRanges) {
                            var curOff = off
                            val endOff = off + zlen
                            val maxEmit = 64
                            var emitted = 0
                            while (curOff < endOff) {
                                val addr = chunkStart + curOff
                                val hasSync = syncAddrs.contains(addr)
                                if (emitted >= maxEmit && !hasSync) {
                                    val nextSync = syncAddrs.filter { it > addr && it < chunkStart + endOff }.minOrNull()
                                    if (nextSync != null) {
                                        curOff = (nextSync - chunkStart).toInt()
                                        emitted = 0
                                        continue
                                    } else break
                                }
                                val ilen = minOf(16, endOff - curOff)
                                zeroInstrs.add(
                                    com.osr.ps5debugger.protocol.Ps5DisasmInstr(
                                        addr = addr,
                                        ripRelTarget = 0,
                                        memDisp = 0,
                                        length = ilen,
                                        kind = 0x200,
                                        memBaseReg = 0,
                                        memIndexReg = 0,
                                        memScale = 0,
                                        mnemonic = 0,
                                        mnemonicLo = 0
                                    )
                                )
                                curOff += ilen
                                emitted += ilen
                            }
                        }

                        val dataRanges = stringRanges + zeroRanges
                        val filtered = if (dataRanges.isEmpty()) rawInstrs else {
                            rawInstrs.filterNot { instr ->
                                val iStart = instr.addr - chunkStart
                                val iEnd = iStart + instr.length
                                dataRanges.any { (dOff, dLen) -> iStart < (dOff + dLen) && iEnd > dOff }
                            }
                        }

                        (filtered + stringInstrs + zeroInstrs).distinctBy { it.addr }.sortedBy { it.addr }.map { instr ->
                            val offset = (instr.addr - chunkStart).toInt()
                            val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) rawBytes.copyOfRange(offset, offset + instr.length) else ByteArray(0)
                            com.osr.ps5debugger.ui.DisasmLine(instr, instrBytes, map, symbolNames[instr.addr])
                        }
                    }
                    chunkLines.addAll(lines)
                }
            } catch (_: Exception) {}
        }

        chunkLines.sortBy { it.instr.addr }
        val finalLines = ArrayList<com.osr.ps5debugger.ui.DisasmLine>(chunkLines.size)
        var prevAddr: Long? = null
        for (line in chunkLines) {
            if (line.instr.addr != prevAddr) {
                finalLines.add(line)
                prevAddr = line.instr.addr
            }
        }

        val extractedFunctions = mutableSetOf<Long>()
        if (finalLines.isNotEmpty()) {
            extractedFunctions.add(finalLines.first().instr.addr)
            for (i in finalLines.indices) {
                val line = finalLines[i]
                if (line.instr.isRet && i + 1 < finalLines.size) extractedFunctions.add(finalLines[i + 1].instr.addr)
                val target = com.osr.ps5debugger.ui.disasm.DisasmFormatter.getJumpTarget(line.instr, line.bytes)
                if (line.instr.isCall && target != 0L) extractedFunctions.add(target)
            }
        }

        withContext(Dispatchers.Main) {
            mainList.clear()
            mainList.addAll(finalLines)
            disasmList.clear()
            disasmList.addAll(finalLines)
            disassemblyProgressCache[key] = 1.0f
            disassemblyProgressCache[disasmKey] = 1.0f
            if (extractedFunctions.isNotEmpty()) {
                val merged = (discoveredFunctions + extractedFunctions).distinct().sortedBy { it.toULong() }
                discoveredFunctions.clear()
                discoveredFunctions.addAll(merged)
            }
        }
    }
}
