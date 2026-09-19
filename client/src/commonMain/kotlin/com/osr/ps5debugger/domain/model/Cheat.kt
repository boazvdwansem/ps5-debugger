package com.osr.ps5debugger.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CheatType {
    TextField,
    Toggle,
    Dropdown
}

@Serializable
enum class InputFormat {
    Text,
    Hex
}

@Serializable
data class CheatOption(
    val name: String,
    val hexValue: String
)

@Serializable
data class Cheat(
    val id: String,
    val name: String,
    val type: CheatType,
    val address: Long,
    val inputFormat: InputFormat = InputFormat.Hex,
    val hexOnValue: String = "", // Value for Toggle ON or default for TextField/Dropdown
    val hexOffValue: String? = null, // Only for Toggle
    val options: List<CheatOption> = emptyList(), // For Dropdown
    val isEnabled: Boolean = false,
    val titleId: String,
    val version: String = "1.00",
    val description: String = ""
)

@Serializable
data class GameCheatProfile(
    val titleId: String,
    val name: String,
    val version: String,
    val platform: String? = null,
    val iconUrl: String? = null,
    val cheats: List<Cheat> = emptyList()
)
