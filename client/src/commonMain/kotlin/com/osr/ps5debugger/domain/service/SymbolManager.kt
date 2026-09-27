package com.osr.ps5debugger.domain.service

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf

object SymbolManager {
    val symbolNames = mutableStateMapOf<Long, String>()
    val discoveredFunctions = mutableStateListOf<Long>()
    val discoveredJumpTargets = mutableStateListOf<Long>()
    var elfEntryPoint: Long? = null

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
}
