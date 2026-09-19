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

    private fun resolveGameName(titleId: String, currentName: String): String {
        // 1. Source of truth: AppContainer map (populated from Cheats and foreground queries)
        if (currentName.isNotEmpty() && !currentName.contains("eboot.bin", ignoreCase = true) && currentName != "Unknown") {
            com.osr.ps5debugger.di.AppContainer.titleIdToName[titleId] = currentName
            return currentName
        }
        
        // 2. Check persistent map
        val mapped = com.osr.ps5debugger.di.AppContainer.titleIdToName[titleId]
        if (!mapped.isNullOrEmpty()) return mapped
        
        // 3. Last resort: Hardcoded common IDs (if any)
        return when (titleId) {
            "CUSA00001" -> "Sample App"
            "CUSA57547" -> "Call of Duty: Black Ops"
            "CUSA00123" -> "Destiny"
            "PPSA01234" -> "Sample PS5 Game"
            "CUSA01500" -> "Bloodborne"
            "CUSA00411" -> "The Witcher 3"
            "CUSA00552" -> "Fallout 4"
            "CUSA05624" -> "Dark Souls III"
            "CUSA02299" -> "Star Wars Battlefront"
            "CUSA00133" -> "Battlefield 4"
            else -> currentName
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
                var finalInfo = info
                
                // If titleId is missing or default, try to extract from path
                if (finalInfo.titleId.isEmpty() || finalInfo.titleId == "0") {
                    val path = finalInfo.path
                    val regex = Regex("(CUSA|PPSA)\\d{5}")
                    val match = regex.find(path)
                    if (match != null) {
                        finalInfo = finalInfo.copy(titleId = match.value)
                    }
                }

                // Try to get more descriptive name from foreground app info
                try {
                    val fgApp = clientPort.getForegroundApp()
                    if (fgApp.titleId == finalInfo.titleId) {
                        val betterName = fgApp.name
                        if (betterName.isNotEmpty() && !betterName.contains("eboot.bin") && betterName != "Unknown") {
                            finalInfo = finalInfo.copy(name = betterName)
                        }
                    }
                } catch (_: Exception) {}
                
                finalInfo = finalInfo.copy(name = resolveGameName(finalInfo.titleId, finalInfo.name))
                _activeProcessInfo.value = finalInfo

                logManager.log("SYSTEM", "Selected active process: ${finalInfo.name} (PID: ${proc.pid}, TitleID: ${finalInfo.titleId})", LogEntry.Level.INFO)
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

    private suspend fun primeExecutableElfMetadata(pid: Int, maps: List<MemoryRange>) {
        for (map in maps.sortedBy { it.start }) {
            if ((map.protections and 4) == 0 || map.size <= 64) continue
            try {
                val probeLen = minOf(256 * 1024L, map.size).toInt().coerceAtLeast(64)
                val probe = clientPort.readMemory(pid, map.start, probeLen)
                if (probe.size < 0x40) continue
                if (probe[0] != 0x7F.toByte() || probe[1] != 'E'.code.toByte() || probe[2] != 'L'.code.toByte() || probe[3] != 'F'.code.toByte()) continue
                val elfBytes = probe.copyOf(minOf(probe.size, probeLen))
                clientPort.uploadElfRpc(pid, elfBytes)
                return
            } catch (_: Exception) {
                // Ignore this map and keep scanning; the raw map start may be after the ELF header or not be an ELF-backed image.
            }
        }
    }

    suspend fun loadMemoryMaps(proc: Process) {
        if (!clientPort.isConnected) return
        try {
            val maps = clientPort.getMaps(proc.pid)
            _vmMaps.value = maps
            primeExecutableElfMetadata(proc.pid, maps)
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
