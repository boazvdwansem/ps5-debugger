package com.osr.ps5debugger.domain.service.managers

import com.osr.ps5debugger.domain.model.*
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class CheatManager(
    private val clientPort: com.osr.ps5debugger.ports.outbound.DebuggerClientPort,
    private val scope: CoroutineScope,
    private val logManager: LogManager,
    private val storagePort: com.osr.ps5debugger.ports.outbound.CheatStoragePort
) {
    private val _gameProfiles = MutableStateFlow<List<GameCheatProfile>>(emptyList())
    val gameProfiles: StateFlow<List<GameCheatProfile>> = _gameProfiles.asStateFlow()

    private fun isBadName(name: String?): Boolean {
        if (name.isNullOrEmpty() || name == "Unknown") return true
        val lower = name.lowercase()
        return lower.contains("eboot.bin") || lower.endsWith(".elf") || lower.endsWith(".bin") || lower.endsWith(".prx") || lower.endsWith(".sprx")
    }

    init {
        val loaded = storagePort.loadCheats()
        _gameProfiles.value = loaded
        loaded.forEach { profile ->
            if (!isBadName(profile.name)) {
                com.osr.ps5debugger.di.AppContainer.titleIdToName[profile.titleId] = profile.name
            }
            if (!profile.platform.isNullOrEmpty()) {
                com.osr.ps5debugger.di.AppContainer.titleIdToPlatform[profile.titleId] = profile.platform
            }
        }
    }

    private fun persist() {
        storagePort.saveCheats(_gameProfiles.value)
    }

    fun updateGameName(titleId: String, name: String) {
        if (isBadName(name)) return
        
        com.osr.ps5debugger.di.AppContainer.titleIdToName[titleId] = name
        
        val currentProfiles = _gameProfiles.value.toMutableList()
        var changed = false
        currentProfiles.forEachIndexed { idx, profile ->
            if (profile.titleId == titleId && profile.name != name) {
                currentProfiles[idx] = profile.copy(name = name)
                changed = true
            }
        }
        if (changed) {
            _gameProfiles.value = currentProfiles
            persist()
        }
    }

    fun updateGameVersion(titleId: String, version: String) {
        if (version.isEmpty()) return
        
        com.osr.ps5debugger.di.AppContainer.titleIdToVersion[titleId] = version
        
        val currentProfiles = _gameProfiles.value.toMutableList()
        var changed = false
        currentProfiles.forEachIndexed { idx, profile ->
            if (profile.titleId == titleId && profile.version != version) {
                currentProfiles[idx] = profile.copy(version = version)
                changed = true
            }
        }
        if (changed) {
            _gameProfiles.value = currentProfiles
            persist()
        }
    }

    fun updateGamePlatform(titleId: String, platform: String) {
        if (platform.isEmpty()) return
        
        com.osr.ps5debugger.di.AppContainer.titleIdToPlatform[titleId] = platform
        
        val currentProfiles = _gameProfiles.value.toMutableList()
        var changed = false
        currentProfiles.forEachIndexed { idx, profile ->
            if (profile.titleId == titleId && profile.platform != platform) {
                currentProfiles[idx] = profile.copy(platform = platform)
                changed = true
            }
        }
        if (changed) {
            _gameProfiles.value = currentProfiles
            persist()
        }
    }

    fun addCheat(titleId: String, version: String, cheat: Cheat, gameName: String = "Unknown") {
        val currentProfiles = _gameProfiles.value.toMutableList()
        // Try exact match first, then fall back to titleId-only match to avoid creating duplicate profiles
        var profileIdx = currentProfiles.indexOfFirst { it.titleId == titleId && it.version == version }
        if (profileIdx == -1) {
            profileIdx = currentProfiles.indexOfFirst { it.titleId == titleId }
        }
        
        val iconPath = "/user/appmeta/$titleId/icon0.png"
        val platform = com.osr.ps5debugger.di.AppContainer.titleIdToPlatform[titleId]
        
        // Populate global name map for other views
        if (!isBadName(gameName)) {
            com.osr.ps5debugger.di.AppContainer.titleIdToName[titleId] = gameName
        }

        // Improve name resolution: don't overwrite a good name with a bad one (like eboot.bin or Unknown)
        fun selectBetterName(old: String, new: String): String {
            if (isBadName(new)) return old
            return new
        }

        if (profileIdx != -1) {
            val profile = currentProfiles[profileIdx]
            val updatedCheats = profile.cheats.toMutableList()
            val existingIdx = updatedCheats.indexOfFirst { it.id == cheat.id }
            if (existingIdx != -1) {
                updatedCheats[existingIdx] = cheat
            } else {
                val cheatId = if (cheat.id.isEmpty()) "cheat_${System.currentTimeMillis()}_${(0..999).random()}" else cheat.id
                updatedCheats.add(cheat.copy(id = cheatId))
            }
            
            val finalName = selectBetterName(profile.name, gameName)
            currentProfiles[profileIdx] = profile.copy(cheats = updatedCheats, name = finalName, iconUrl = iconPath, platform = platform)
        } else {
            val cheatId = if (cheat.id.isEmpty()) "cheat_${System.currentTimeMillis()}_${(0..999).random()}" else cheat.id
            val finalName = if (gameName == "Unknown" || gameName.contains("eboot.bin")) titleId else gameName
            currentProfiles.add(GameCheatProfile(
                titleId = titleId,
                name = finalName,
                version = version,
                platform = platform,
                iconUrl = iconPath,
                cheats = listOf(cheat.copy(id = cheatId))
            ))
        }
        _gameProfiles.value = currentProfiles
        persist()
        logManager.log("CHEATS", "Saved cheat '${cheat.name}' for $titleId ($version)", LogEntry.Level.INFO)
    }

    fun deleteCheat(titleId: String, version: String, cheatId: String) {
        val currentProfiles = _gameProfiles.value.toMutableList()
        val pIdx = currentProfiles.indexOfFirst { it.titleId == titleId && it.version == version }
        if (pIdx != -1) {
            val profile = currentProfiles[pIdx]
            val updatedCheats = profile.cheats.toMutableList()
            updatedCheats.removeAll { it.id == cheatId }
            if (updatedCheats.isEmpty()) {
                currentProfiles.removeAt(pIdx)
            } else {
                currentProfiles[pIdx] = profile.copy(cheats = updatedCheats)
            }
            _gameProfiles.value = currentProfiles
            persist()
        }
    }

    private fun parseHexBytes(hexStr: String): ByteArray {
        val clean = hexStr.trim()
        if (clean.isEmpty()) return ByteArray(0)
        val tokens = clean.split(Regex("[\\s,\\-]+")).filter { it.isNotBlank() }
        val byteList = mutableListOf<Byte>()
        for (token in tokens) {
            val t = token.removePrefix("0x").removePrefix("0X")
            if (t.isEmpty()) continue
            if (t.length % 2 != 0) {
                val padded = "0$t"
                padded.chunked(2).forEach { byteList.add(it.toInt(16).toByte()) }
            } else {
                t.chunked(2).forEach { byteList.add(it.toInt(16).toByte()) }
            }
        }
        return byteList.toByteArray()
    }

    suspend fun applyCheat(pid: Int, cheat: Cheat, newValue: String? = null) {
        val patches = cheat.getEffectivePatches()
        if (patches.isEmpty()) return

        val writes = mutableListOf<Pair<Long, ByteArray>>()

        for (patch in patches) {
            val hexToInject = when (cheat.type) {
                CheatType.Toggle -> {
                    if (cheat.isEnabled) patch.hexOnValue else patch.hexOffValue ?: ""
                }
                CheatType.TextField -> {
                    if (cheat.inputFormat == InputFormat.Text) {
                        val text = newValue ?: patch.hexOnValue
                        text.encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
                    } else {
                        newValue ?: patch.hexOnValue
                    }
                }
                CheatType.Dropdown -> {
                    newValue ?: patch.hexOnValue
                }
            }

            if (hexToInject.isNotBlank()) {
                try {
                    val bytes = parseHexBytes(hexToInject)
                    if (bytes.isNotEmpty()) {
                        writes.add(patch.address to bytes)
                    }
                } catch (e: Exception) {
                    logManager.log("CHEATS", "Invalid hex in patch for cheat '${cheat.name}' at 0x${patch.address.toString(16)}: ${e.message}", LogEntry.Level.ERROR)
                }
            }
        }

        if (writes.isEmpty()) {
            if (cheat.type == CheatType.Toggle && !cheat.isEnabled) {
                logManager.log("CHEATS", "Cheat '${cheat.name}' turned OFF (no OFF bytes specified to revert)", LogEntry.Level.INFO)
            } else {
                logManager.log("CHEATS", "No bytes to inject for cheat '${cheat.name}'", LogEntry.Level.WARN)
            }
            return
        }

        if (pid <= 0) {
            logManager.log("CHEATS", "Cannot apply cheat '${cheat.name}': No active process attached (PID $pid)", LogEntry.Level.ERROR)
            return
        }

        try {
            // Inject the same way MemoryViewerLayout (Hex Editor) injects memory
            val success = clientPort.writeMemoryMulti(pid, writes, withStatusReport = false)
            if (success) {
                val totalBytes = writes.sumOf { it.second.size }
                val stateText = if (cheat.type == CheatType.Toggle) (if (cheat.isEnabled) "ON" else "OFF") else "Applied"
                logManager.log("CHEATS", "[$stateText] Applied cheat '${cheat.name}': injected $totalBytes byte(s) across ${writes.size} patch location(s)", LogEntry.Level.INFO)
            } else {
                logManager.log("CHEATS", "PS5 rejected the memory injection for cheat '${cheat.name}' (PID $pid)", LogEntry.Level.ERROR)
            }
        } catch (e: Exception) {
            logManager.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${e.message}", LogEntry.Level.ERROR)
        }
    }

    fun toggleCheat(titleId: String, version: String, cheatId: String): Cheat? {
        val currentProfiles = _gameProfiles.value.toMutableList()
        val pIdx = currentProfiles.indexOfFirst { it.titleId == titleId && it.version == version }
        if (pIdx != -1) {
            val profile = currentProfiles[pIdx]
            val cIdx = profile.cheats.indexOfFirst { it.id == cheatId }
            if (cIdx != -1) {
                val updatedCheats = profile.cheats.toMutableList()
                val cheat = updatedCheats[cIdx]
                val toggled = cheat.copy(isEnabled = !cheat.isEnabled)
                updatedCheats[cIdx] = toggled
                currentProfiles[pIdx] = profile.copy(cheats = updatedCheats)
                _gameProfiles.value = currentProfiles
                persist()
                return toggled
            }
        }
        return null
    }

    private val onionHenJson = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun cleanHexForExport(hexStr: String?): String {
        if (hexStr == null) return ""
        val cleaned = hexStr
            .replace("0x", "", ignoreCase = true)
            .replace("0X", "")
            .replace(" ", "")
            .replace(",", "")
            .replace("-", "")
            .uppercase()
        return cleaned
    }

    suspend fun exportCheatsToPs5(
        ip: String,
        titleId: String,
        version: String,
        gameName: String,
        processName: String = "eboot.bin",
        credits: List<String> = listOf("Boaz"),
        cheats: List<Cheat>
    ): Result<String> {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) {
            val err = "PS5 IP address is empty. Please check your connection."
            logManager.log("CHEATS", err, LogEntry.Level.ERROR)
            return Result.failure(IllegalArgumentException(err))
        }
        val cleanTitleId = titleId.trim()
        if (cleanTitleId.isEmpty()) {
            val err = "Title ID cannot be empty."
            logManager.log("CHEATS", err, LogEntry.Level.ERROR)
            return Result.failure(IllegalArgumentException(err))
        }
        val cleanVersion = version.trim().ifEmpty { "1.00" }
        val cleanProcess = processName.trim().ifEmpty { "eboot.bin" }

        val mods = cheats.map { cheat ->
            val effectivePatches = cheat.getEffectivePatches()
            val memoryEntries = effectivePatches.map { patch ->
                val offsetHex = patch.address.toString(16).uppercase()
                val onHex = cleanHexForExport(patch.hexOnValue)
                val offHex = patch.hexOffValue?.takeIf { it.isNotBlank() }?.let { cleanHexForExport(it) }
                val comment = patch.comment?.takeIf { it.isNotBlank() } ?: cheat.description.takeIf { it.isNotBlank() }

                OnionHenMemoryEntry(
                    comment = comment,
                    offset = offsetHex,
                    on = onHex,
                    off = offHex
                )
            }
            OnionHenMod(
                name = cheat.name,
                type = "checkbox",
                memory = memoryEntries
            )
        }

        val cheatFile = OnionHenCheatFile(
            name = gameName.trim().ifEmpty { cleanTitleId },
            id = cleanTitleId,
            version = cleanVersion,
            process = cleanProcess,
            mods = mods,
            credits = credits.map { it.trim() }.filter { it.isNotEmpty() }
        )

        val jsonString = onionHenJson.encodeToString(cheatFile)
        val fileName = "${cleanTitleId}_${cleanVersion}.json"
        val targetDir = "/data/OnionHEN/cheats"
        val targetPath = "$targetDir/$fileName"

        return try {
            val ftpClient = com.osr.ps5debugger.network.Ps5FtpClient(cleanIp)
            // Ensure parent directories exist
            try { ftpClient.createDirectory("/data") } catch (_: Exception) {}
            try { ftpClient.createDirectory("/data/OnionHEN") } catch (_: Exception) {}
            try { ftpClient.createDirectory("/data/OnionHEN/cheats") } catch (_: Exception) {}

            java.io.ByteArrayInputStream(jsonString.encodeToByteArray()).use { input ->
                ftpClient.uploadFile(targetPath, input)
            }
            ftpClient.disconnect()

            logManager.log("CHEATS", "Successfully exported OnionHEN cheats to PS5 ($targetPath)", LogEntry.Level.INFO)
            Result.success(targetPath)
        } catch (e: Exception) {
            logManager.log("CHEATS", "Failed to export OnionHEN cheats to PS5 ($targetPath): ${e.message}", LogEntry.Level.ERROR)
            Result.failure(e)
        }
    }

    fun saveCheats(onResult: (String) -> Unit) {
        val json = Json.encodeToString(_gameProfiles.value)
        onResult(json)
    }

    fun loadCheats(json: String) {
        try {
            val profiles: List<GameCheatProfile> = Json.decodeFromString(json)
            _gameProfiles.value = profiles
        } catch (e: Exception) {
            logManager.log("CHEATS", "Failed to load cheats: ${e.message}", LogEntry.Level.ERROR)
        }
    }
}
