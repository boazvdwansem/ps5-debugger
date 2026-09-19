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
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
}
