package com.osr.ps5debugger.domain.service.managers

import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ports.outbound.LogStoragePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LogManager(private val logPort: LogStoragePort) {
    companion object {
        const val MAX_LOGS = 1000
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: LogEntry.Level) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        _logs.update { current ->
            if (current.size >= MAX_LOGS) {
                val trim = current.size - MAX_LOGS + 1
                current.drop(trim) + entry
            } else {
                current + entry
            }
        }
        logPort.persistLog(entry)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
