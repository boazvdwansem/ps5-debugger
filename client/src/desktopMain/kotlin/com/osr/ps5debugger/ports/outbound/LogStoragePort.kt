package com.osr.ps5debugger.ports.outbound

import com.osr.ps5debugger.domain.model.LogEntry

interface LogStoragePort {
    fun persistLog(entry: LogEntry)
}
