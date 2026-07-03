package com.osr.ps5debugger.ports.inbound

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

interface DebuggerUseCase {
    val isConnected: StateFlow<Boolean>
    val processes: StateFlow<List<Process>>
    val activeProcess: StateFlow<Process?>
    val activeProcessInfo: StateFlow<Ps5ProcessInfo?>
    val debugEvents: SharedFlow<Ps5DebugEvent>
    val logs: StateFlow<List<LogEntry>>
    val watchlist: StateFlow<List<WatchItem>>
    val vmMaps: StateFlow<List<MemoryRange>>

    suspend fun connect(ip: String): Boolean
    suspend fun disconnect()
    suspend fun refreshProcesses()
    suspend fun selectProcess(proc: Process?)
    suspend fun loadMemoryMaps(proc: Process)
    suspend fun readMemory(address: Long, length: Int): Result<ByteArray>
    suspend fun writeMemory(address: Long, data: ByteArray): Result<Boolean>
    
    fun log(tag: String, message: String, level: LogEntry.Level = LogEntry.Level.DEBUG)
    fun clearLogs()
    
    fun addToWatchlist(address: Long, type: String = "Int32", byteLength: Int? = null)
    fun addWatchItem(item: WatchItem)
    fun updateWatchItem(item: WatchItem)
    fun removeWatchItem(item: WatchItem)
    fun toggleFreezeWatchItem(item: WatchItem)
    fun clearWatchlist()
}
