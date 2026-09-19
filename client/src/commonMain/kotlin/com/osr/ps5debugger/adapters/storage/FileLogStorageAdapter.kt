package com.osr.ps5debugger.adapters.storage

import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ports.outbound.LogStoragePort
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogStorageAdapter : LogStoragePort {
    private val lastLogFile = File("last_log.txt")
    private val prevLogFile = File("prev_log.txt")

    override fun persistLog(entry: LogEntry) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val logText = "[${dateFormat.format(Date(entry.timestamp))}] [${entry.tag}] [${entry.level.name}] ${entry.message}\n"
            
            // Simple rotation: if file > 1MB, move to prev
            if (lastLogFile.exists() && lastLogFile.length() > 1024 * 1024) {
                if (prevLogFile.exists()) prevLogFile.delete()
                lastLogFile.renameTo(prevLogFile)
            }
            
            lastLogFile.appendText(logText)
        } catch (_: Exception) {}
    }
}
