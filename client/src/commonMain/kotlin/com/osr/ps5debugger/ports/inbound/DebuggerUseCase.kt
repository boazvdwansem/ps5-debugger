package com.osr.ps5debugger.ports.inbound

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import com.osr.ps5debugger.protocol.GpRegs
import com.osr.ps5debugger.protocol.DbRegs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

interface DebuggerUseCase {
    val isConnected: StateFlow<Boolean>
    val isAttached: StateFlow<Boolean>
    val isProcessStopped: StateFlow<Boolean>
    val threadList: StateFlow<List<Int>>
    val selectedLwpid: StateFlow<Int?>
    val selectedRegs: StateFlow<GpRegs?>
    val selectedDbRegs: StateFlow<DbRegs?>
    val selectedFsGs: StateFlow<Pair<Long, Long>?>
    val processes: StateFlow<List<Process>>
    val activeProcess: StateFlow<Process?>
    val activeProcessInfo: StateFlow<Ps5ProcessInfo?>
    val debugEvents: SharedFlow<Ps5DebugEvent>
    val logs: StateFlow<List<LogEntry>>
    val watchlist: StateFlow<List<WatchItem>>
    val vmMaps: StateFlow<List<MemoryRange>>
    val gameCheatProfiles: StateFlow<List<com.osr.ps5debugger.domain.model.GameCheatProfile>>

    fun setAttached(attached: Boolean)
    fun setProcessStopped(stopped: Boolean)
    fun setThreadList(threads: List<Int>)
    fun setSelectedLwpid(lwpid: Int?)
    fun setSelectedRegs(regs: GpRegs?)
    fun setSelectedDbRegs(regs: DbRegs?)
    fun setSelectedFsGs(fsgs: Pair<Long, Long>?)
    suspend fun connect(ip: String): Boolean
    suspend fun disconnect()
    suspend fun refreshProcesses()
    suspend fun selectProcess(proc: Process?)
    suspend fun loadMemoryMaps(proc: Process)
    suspend fun pullFile(path: String): Result<ByteArray>
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
    
    fun addCheat(titleId: String, version: String, cheat: com.osr.ps5debugger.domain.model.Cheat, gameName: String = "Unknown")
    fun toggleCheat(titleId: String, version: String, cheatId: String)
    fun deleteCheat(titleId: String, version: String, cheatId: String)
    fun updateGameName(titleId: String, name: String)
    fun updateGameVersion(titleId: String, version: String)
    fun updateGamePlatform(titleId: String, platform: String)
    suspend fun applyCheat(pid: Int, cheat: com.osr.ps5debugger.domain.model.Cheat, newValue: String? = null)
    fun saveCheats(onResult: (String) -> Unit)
    fun loadCheats(json: String)
}
