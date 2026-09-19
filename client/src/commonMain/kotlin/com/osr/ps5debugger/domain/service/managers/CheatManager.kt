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

    private val titleIdMap = mapOf(
        "CUSA00001" to "Sample App",
        "PPSA01234" to "Sample PS5 Game",
        "CUSA57547" to "Call of Duty: Black Ops"
    )

    private fun resolveGameName(titleId: String, currentName: String): String {
        if (!isBadName(currentName)) return currentName
        return titleIdMap[titleId] ?: currentName
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
        val profileIdx = currentProfiles.indexOfFirst { it.titleId == titleId && it.version == version }
        
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

    suspend fun applyCheat(pid: Int, cheat: Cheat, newValue: String? = null) {
        val address = cheat.address
        val hexToInject = when (cheat.type) {
            CheatType.Toggle -> {
                if (cheat.isEnabled) cheat.hexOnValue else cheat.hexOffValue ?: ""
            }
            CheatType.TextField -> {
                if (cheat.inputFormat == InputFormat.Text) {
                    val text = newValue ?: cheat.hexOnValue
                    text.encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
                } else {
                    newValue ?: cheat.hexOnValue
                }
            }
            CheatType.Dropdown -> {
                newValue ?: cheat.hexOnValue
            }
        }

        if (hexToInject.isNotEmpty()) {
            try {
                val bytes = hexToInject.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val result = com.osr.ps5debugger.di.AppContainer.debuggerUseCase.writeMemory(address, bytes)
                if (result.isSuccess) {
                    logManager.log("CHEATS", "Applied cheat '${cheat.name}' at 0x${address.toString(16)}", LogEntry.Level.INFO)
                } else {
                    logManager.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${result.exceptionOrNull()?.message}", LogEntry.Level.ERROR)
                }
            } catch (e: Exception) {
                logManager.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${e.message}", LogEntry.Level.ERROR)
            }
        }
    }

    fun toggleCheat(titleId: String, version: String, cheatId: String) {
        val currentProfiles = _gameProfiles.value.toMutableList()
        val pIdx = currentProfiles.indexOfFirst { it.titleId == titleId && it.version == version }
        if (pIdx != -1) {
            val profile = currentProfiles[pIdx]
            val cIdx = profile.cheats.indexOfFirst { it.id == cheatId }
            if (cIdx != -1) {
                val updatedCheats = profile.cheats.toMutableList()
                val cheat = updatedCheats[cIdx]
                updatedCheats[cIdx] = cheat.copy(isEnabled = !cheat.isEnabled)
                currentProfiles[pIdx] = profile.copy(cheats = updatedCheats)
                _gameProfiles.value = currentProfiles
                persist()
            }
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
