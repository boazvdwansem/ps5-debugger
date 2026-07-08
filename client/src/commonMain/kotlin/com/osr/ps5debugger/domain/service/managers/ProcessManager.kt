package com.osr.ps5debugger.domain.service.managers

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProcessManager(
    private val clientPort: DebuggerClientPort,
    private val logManager: LogManager
) {
    private val _processes = MutableStateFlow<List<Process>>(emptyList())
    val processes: StateFlow<List<Process>> = _processes.asStateFlow()

    private val _activeProcess = MutableStateFlow<Process?>(null)
    val activeProcess: StateFlow<Process?> = _activeProcess.asStateFlow()

    private val _activeProcessInfo = MutableStateFlow<Ps5ProcessInfo?>(null)
    val activeProcessInfo: StateFlow<Ps5ProcessInfo?> = _activeProcessInfo.asStateFlow()

    private val _vmMaps = MutableStateFlow<List<MemoryRange>>(emptyList())
    val vmMaps: StateFlow<List<MemoryRange>> = _vmMaps.asStateFlow()

    suspend fun refreshProcesses() {
        if (!clientPort.isConnected) return
        try {
            val list = clientPort.getProcesses()
            _processes.value = list
            logManager.log("SYSTEM", "Refreshed processes list (${list.size} found)", LogEntry.Level.DEBUG)
        } catch (e: Exception) {
            logManager.log("SYSTEM", "Failed to retrieve process list: ${e.message}", LogEntry.Level.ERROR)
        }
    }

    suspend fun selectProcess(
        proc: Process?,
        isAttached: MutableStateFlow<Boolean>,
        threadList: MutableStateFlow<List<Int>>,
        selectedLwpid: MutableStateFlow<Int?>,
        selectedRegs: MutableStateFlow<com.osr.ps5debugger.protocol.GpRegs?>,
        selectedDbRegs: MutableStateFlow<com.osr.ps5debugger.protocol.DbRegs?>,
        selectedFsGs: MutableStateFlow<Pair<Long, Long>?>,
        isConnected: MutableStateFlow<Boolean>,
        lastConnectedIp: String?,
        connectFunc: suspend (String) -> Boolean
    ) {
        _activeProcess.value = proc
        _vmMaps.value = emptyList()
        isAttached.value = false
        threadList.value = emptyList()
        selectedLwpid.value = null
        selectedRegs.value = null
        selectedDbRegs.value = null
        selectedFsGs.value = null
        
        if (proc != null) {
            if (!clientPort.isConnected && lastConnectedIp != null) {
                logManager.log("SYSTEM", "Connection lost. Attempting auto-reconnect to $lastConnectedIp...", LogEntry.Level.WARN)
                val ok = connectFunc(lastConnectedIp)
                if (!ok) {
                    logManager.log("SYSTEM", "Auto-reconnect failed.", LogEntry.Level.ERROR)
                    isConnected.value = false
                    return
                }
            }

            try {
                val info = clientPort.getProcessInfo(proc.pid)
                _activeProcessInfo.value = info
                logManager.log("SYSTEM", "Selected active process: ${proc.name} (PID: ${proc.pid}, TitleID: ${info.titleId})", LogEntry.Level.INFO)
            } catch (e: Exception) {
                _activeProcessInfo.value = null
                logManager.log("SYSTEM", "Selected active process: ${proc.name} (PID: ${proc.pid}) (Could not load process details: ${e.message})", LogEntry.Level.INFO)
            }

            try {
                loadMemoryMaps(proc)
            } catch (e: Exception) {
                logManager.log("SYSTEM", "Failed to load memory maps: ${e.message}", LogEntry.Level.ERROR)
            }
        } else {
            _activeProcessInfo.value = null
        }
    }

    suspend fun loadMemoryMaps(proc: Process) {
        if (!clientPort.isConnected) return
        try {
            val maps = clientPort.getMaps(proc.pid)
            _vmMaps.value = maps
            logManager.log("SYSTEM", "Loaded ${maps.size} virtual memory maps for PID ${proc.pid}", LogEntry.Level.DEBUG)
        } catch (e: Exception) {
            logManager.log("SYSTEM", "Failed to load memory maps: ${e.message}", LogEntry.Level.ERROR)
        }
    }

    fun clear() {
        _processes.value = emptyList()
        _activeProcess.value = null
        _activeProcessInfo.value = null
        _vmMaps.value = emptyList()
    }
}
