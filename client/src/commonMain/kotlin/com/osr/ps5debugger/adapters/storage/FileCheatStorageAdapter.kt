package com.osr.ps5debugger.adapters.storage

import com.osr.ps5debugger.domain.model.GameCheatProfile
import com.osr.ps5debugger.ports.outbound.CheatStoragePort
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class FileCheatStorageAdapter : CheatStoragePort {
    private val cheatFile = File("cheats.json")
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun saveCheats(profiles: List<GameCheatProfile>) {
        try {
            val data = json.encodeToString(profiles)
            cheatFile.writeText(data)
        } catch (e: Exception) {
            println("Failed to save cheats: ${e.message}")
        }
    }

    override fun loadCheats(): List<GameCheatProfile> {
        if (!cheatFile.exists()) return emptyList()
        return try {
            val data = cheatFile.readText()
            json.decodeFromString(data)
        } catch (e: Exception) {
            println("Failed to load cheats: ${e.message}")
            emptyList()
        }
    }
}
