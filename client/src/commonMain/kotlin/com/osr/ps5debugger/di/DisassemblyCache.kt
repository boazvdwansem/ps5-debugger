package com.osr.ps5debugger.di

import androidx.compose.runtime.mutableStateListOf

object DisassemblyCache {
    val instructionsCache = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<com.osr.ps5debugger.ui.memory.disasm.DisasmLine>>()
    val disassemblyProgressCache = java.util.concurrent.ConcurrentHashMap<String, Float>()
    private const val MAX_DISASM_CACHED_REGIONS = 32
    private val disasmAccessOrder = mutableListOf<String>()

    fun getInstructions(mapKey: String): androidx.compose.runtime.snapshots.SnapshotStateList<com.osr.ps5debugger.ui.memory.disasm.DisasmLine> {
        synchronized(disasmAccessOrder) {
            disasmAccessOrder.remove(mapKey)
            disasmAccessOrder.add(mapKey)
            while (disasmAccessOrder.size > MAX_DISASM_CACHED_REGIONS) {
                val oldestKey = disasmAccessOrder.removeAt(0)
                if (oldestKey != mapKey) {
                    instructionsCache.remove(oldestKey)
                    disassemblyProgressCache.remove(oldestKey)
                }
            }
            return instructionsCache.getOrPut(mapKey) { mutableStateListOf() }
        }
    }

    fun clearCache(mapKey: String) {
        synchronized(disasmAccessOrder) { disasmAccessOrder.remove(mapKey) }
        instructionsCache.remove(mapKey)
        disassemblyProgressCache.remove(mapKey)
    }
}

