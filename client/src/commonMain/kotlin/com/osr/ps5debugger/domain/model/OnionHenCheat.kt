package com.osr.ps5debugger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OnionHenMemoryEntry(
    val comment: String? = null,
    val offset: String,
    val on: String,
    val off: String? = null
)

@Serializable
data class OnionHenMod(
    val name: String,
    val type: String = "checkbox",
    val memory: List<OnionHenMemoryEntry> = emptyList()
)

@Serializable
data class OnionHenCheatFile(
    val name: String,
    val id: String,
    val version: String,
    val process: String = "eboot.bin",
    val mods: List<OnionHenMod> = emptyList(),
    val credits: List<String> = emptyList()
)
