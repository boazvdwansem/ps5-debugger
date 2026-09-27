package com.osr.ps5debugger.di

import androidx.compose.runtime.mutableStateMapOf

object HexCache {
    val hexCache = mutableStateMapOf<Long, ByteArray>()

    class HexRegionProgress(
        val completedChunks: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet(),
        @Volatile var loadedBytes: Long = 0L,
        @Volatile var progress: Float = 0f,
        @Volatile var isComplete: Boolean = false
    )
    
    val hexProgressCache = java.util.concurrent.ConcurrentHashMap<String, HexRegionProgress>()

    fun getHexCache(mapKey: String = ""): androidx.compose.runtime.snapshots.SnapshotStateMap<Long, ByteArray> {
        return hexCache
    }

    fun clearHexCache() {
        hexCache.clear()
        hexProgressCache.clear()
    }
}
