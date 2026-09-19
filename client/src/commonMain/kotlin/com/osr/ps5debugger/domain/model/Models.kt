package com.osr.ps5debugger.domain.model

data class Process(
    val name: String,
    val pid: Int
)

data class MemoryRange(
    val name: String,
    val start: Long,
    val end: Long,
    val offset: Long,
    val protections: Int,
    val localData: ByteArray? = null,
    val subRanges: List<MemoryRange> = emptyList(),
    val titleId: String? = null
) {
    val size: Long get() = end - start
    
    fun getProtString(): String {
        val r = if ((protections and 1) != 0) "R" else "-"
        val w = if ((protections and 2) != 0) "W" else "-"
        val x = if ((protections and 4) != 0) "X" else "-"
        return "$r$w$x"
    }
}

data class DumpRegionEntry(
    val id: String,
    val name: String,
    val start: Long,
    val end: Long,
    val totalSize: Long,
    val protections: Int,
    val subRanges: List<MemoryRange>,
    val isMergedLibrary: Boolean
) {
    val size: Long get() = totalSize

    fun getProtString(): String {
        val r = if ((protections and 1) != 0) "R" else "-"
        val w = if ((protections and 2) != 0) "W" else "-"
        val x = if ((protections and 4) != 0) "X" else "-"
        return "$r$w$x"
    }

    fun toMemoryRange(titleId: String? = null): MemoryRange = MemoryRange(
        name = name,
        start = start,
        end = end,
        offset = 0,
        protections = protections,
        subRanges = subRanges,
        titleId = titleId
    )
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: Level
) {
    enum class Level { DEBUG, INFO, WARN, ERROR, PROTOCOL }
}

data class WatchItem(
    val label: String,
    val address: Long,
    val type: String, // "Byte", "Int16", "Int32", "Int64", "Float", "Double", "String"
    val valueStr: String = "??",
    val isFrozen: Boolean = false,
    val comment: String = "",
    val byteLength: Int? = null
)
