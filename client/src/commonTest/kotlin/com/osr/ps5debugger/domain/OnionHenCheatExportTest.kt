package com.osr.ps5debugger.domain

import com.osr.ps5debugger.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnionHenCheatExportTest {

    private val onionHenJson = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private fun cleanHex(hexStr: String?): String {
        if (hexStr == null) return ""
        return hexStr
            .replace("0x", "", ignoreCase = true)
            .replace("0X", "")
            .replace(" ", "")
            .replace(",", "")
            .replace("-", "")
            .uppercase()
    }

    @Test
    fun testDynamicCheatsExportWithThreeOffsets() {
        // User creates a single cheat with 3 patches/offsets
        val userCheat = Cheat(
            id = "cheat_custom_1",
            name = "Infinite Health & Ammo",
            type = CheatType.Toggle,
            patches = listOf(
                CheatPatch(address = 0x1000L, hexOnValue = "90 90 90", hexOffValue = "89 45 FC", comment = "Nop health dec"),
                CheatPatch(address = 0x2000L, hexOnValue = "31 C0", hexOffValue = "FF 08", comment = "Nop ammo dec"),
                CheatPatch(address = 0x3000L, hexOnValue = "E9 00 00 00 00", hexOffValue = "0F 84 00 00 00 00")
            ),
            titleId = "CUSA99999",
            version = "1.00"
        )

        val memoryEntries = userCheat.getEffectivePatches().map { patch ->
            OnionHenMemoryEntry(
                comment = patch.comment,
                offset = patch.address.toString(16).uppercase(),
                on = cleanHex(patch.hexOnValue),
                off = patch.hexOffValue?.takeIf { it.isNotBlank() }?.let { cleanHex(it) }
            )
        }

        val mod = OnionHenMod(name = userCheat.name, type = "checkbox", memory = memoryEntries)
        val cheatFile = OnionHenCheatFile(
            name = "Custom User Game",
            id = "CUSA99999",
            version = "1.00",
            process = "eboot.bin",
            mods = listOf(mod),
            credits = listOf("Boaz")
        )

        val json = onionHenJson.encodeToString(cheatFile)

        assertEquals(1, cheatFile.mods.size)
        assertEquals(3, cheatFile.mods.first().memory.size)
        assertTrue(json.contains("\"name\": \"Custom User Game\""))
        assertTrue(json.contains("\"id\": \"CUSA99999\""))
        assertTrue(json.contains("\"offset\": \"1000\""))
        assertTrue(json.contains("\"on\": \"909090\""))
        assertTrue(json.contains("\"off\": \"8945FC\""))
        assertTrue(json.contains("\"comment\": \"Nop health dec\""))
        assertTrue(json.contains("\"offset\": \"2000\""))
        assertTrue(json.contains("\"on\": \"31C0\""))
        assertTrue(json.contains("\"off\": \"FF08\""))
        assertTrue(json.contains("\"offset\": \"3000\""))
        assertTrue(json.contains("\"on\": \"E900000000\""))
        assertTrue(json.contains("\"off\": \"0F8400000000\""))
    }
}
