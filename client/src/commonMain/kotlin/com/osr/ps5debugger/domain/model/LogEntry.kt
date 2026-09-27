package com.osr.ps5debugger.domain.model

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: Level
) {
    enum class Level { DEBUG, INFO, WARN, ERROR, PROTOCOL }
}
