package com.osr.ps5debugger.domain.service

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.domain.service.managers.CheatManager
import com.osr.ps5debugger.domain.service.managers.LogManager
import com.osr.ps5debugger.domain.service.managers.ProcessManager
import com.osr.ps5debugger.domain.service.managers.WatchlistManager
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.ports.outbound.LogStoragePort
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DebuggerDomainService(
    private val clientPort: DebuggerClientPort,
    private val logPort: LogStoragePort,
    private val cheatStorage: com.osr.ps5debugger.ports.outbound.CheatStoragePort
) : DebuggerUseCase {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logManager = LogManager(logPort)
    private val watchlistManager = WatchlistManager(clientPort, scope, logManager)
    private val processManager = ProcessManager(clientPort, logManager)
    private val cheatManager = CheatManager(clientPort, scope, logManager, cheatStorage)

    init {
        scope.launch {
            processManager.activeProcessInfo.collect { info ->
                if (info != null) {
                    cheatManager.updateGameName(info.titleId, info.name)
                }
            }
        }
    }

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isAttached = MutableStateFlow(false)
    override val isAttached: StateFlow<Boolean> = _isAttached.asStateFlow()

    private val _isProcessStopped = MutableStateFlow(false)
    override val isProcessStopped: StateFlow<Boolean> = _isProcessStopped.asStateFlow()

    private val _threadList = MutableStateFlow<List<Int>>(emptyList())
    override val threadList: StateFlow<List<Int>> = _threadList.asStateFlow()

    private val _selectedLwpid = MutableStateFlow<Int?>(null)
    override val selectedLwpid: StateFlow<Int?> = _selectedLwpid.asStateFlow()

    private val _selectedRegs = MutableStateFlow<com.osr.ps5debugger.protocol.GpRegs?>(null)
    override val selectedRegs: StateFlow<com.osr.ps5debugger.protocol.GpRegs?> = _selectedRegs.asStateFlow()

    private val _selectedDbRegs = MutableStateFlow<com.osr.ps5debugger.protocol.DbRegs?>(null)
    override val selectedDbRegs: StateFlow<com.osr.ps5debugger.protocol.DbRegs?> = _selectedDbRegs.asStateFlow()

    private val _selectedFsGs = MutableStateFlow<Pair<Long, Long>?>(null)
    override val selectedFsGs: StateFlow<Pair<Long, Long>?> = _selectedFsGs.asStateFlow()

    override val processes: StateFlow<List<Process>> get() = processManager.processes
    override val activeProcess: StateFlow<Process?> get() = processManager.activeProcess
    override val activeProcessInfo: StateFlow<Ps5ProcessInfo?> get() = processManager.activeProcessInfo
    override val debugEvents: SharedFlow<Ps5DebugEvent> = clientPort.debugEvents
    override val logs: StateFlow<List<LogEntry>> get() = logManager.logs
    override val watchlist: StateFlow<List<WatchItem>> get() = watchlistManager.watchlist
    override val vmMaps: StateFlow<List<MemoryRange>> get() = processManager.vmMaps
    override val gameCheatProfiles: StateFlow<List<com.osr.ps5debugger.domain.model.GameCheatProfile>> get() = cheatManager.gameProfiles

    private var lastConnectedIp: String? = null

    init {
        watchlistManager.getActiveProcessPid = { processManager.activeProcess.value?.pid }

        // Connection keeper loop to handle background disconnects (like detach socket closures)
        scope.launch {
            while (isActive) {
                if (_isConnected.value && !clientPort.isConnected) {
                    if (com.osr.ps5debugger.util.DefaultIpHelper.isAutoReconnectEnabled()) {
                        log("SYSTEM", "Socket connection lost. Reconnecting...", LogEntry.Level.WARN)
                        val ip = lastConnectedIp
                        if (ip != null) {
                            val ok = clientPort.connect(ip)
                            if (ok) {
                                try {
                                    clientPort.auth()
                                    log("SYSTEM", "Reconnected successfully.", LogEntry.Level.INFO)
                                } catch (e: Exception) {
                                    log("SYSTEM", "Reconnect authentication failed: ${e.message}", LogEntry.Level.ERROR)
                                    _isConnected.value = false
                                }
                            } else {
                                log("SYSTEM", "Reconnect failed. Returning to connection screen.", LogEntry.Level.ERROR)
                                _isConnected.value = false
                            }
                        } else {
                            _isConnected.value = false
                        }
                    } else {
                        log("SYSTEM", "Socket connection lost. Auto-reconnect is disabled.", LogEntry.Level.WARN)
                        _isConnected.value = false
                    }
                }
                delay(2000)
            }
        }

        // Collect network changes and forward connection logs
        scope.launch {
            isConnected.collect { connected ->
                if (connected) {
                    log("SYSTEM", "Connected to PS5 target", LogEntry.Level.INFO)
                    clientPort.startDebugChannel()
                } else {
                    log("SYSTEM", "Disconnected from PS5 target", LogEntry.Level.WARN)
                    _isAttached.value = false
                    _isProcessStopped.value = false
                    clientPort.stopDebugChannel()
                    clientPort.stopKlogForwarder()
                    watchlistManager.clearFrozen()
                    processManager.clear()
                }
            }
        }

        // Forward kernel logs adapter events to Domain logs flow
        scope.launch {
            clientPort.logLines.collect { line ->
                log("KERNEL", line, LogEntry.Level.INFO)
            }
        }

        // Forward debug channel events
        scope.launch {
            clientPort.debugEvents.collect { event ->
                _isProcessStopped.value = true
                _selectedLwpid.value = event.lwpid
                _selectedRegs.value = event.regs
                _selectedDbRegs.value = event.dbregs
                log("DEBUG", "Hit breakpoint/event on LWP ID ${event.lwpid} (thread: ${event.threadName}, rip: 0x${event.regs.rip.toString(16)})", LogEntry.Level.INFO)
            }
        }
    }

    override fun setAttached(attached: Boolean) {
        _isAttached.value = attached
        if (!attached) _isProcessStopped.value = false
    }

    override fun setProcessStopped(stopped: Boolean) {
        _isProcessStopped.value = stopped
    }

    override fun setThreadList(threads: List<Int>) {
        _threadList.value = threads
    }

    override fun setSelectedLwpid(lwpid: Int?) {
        _selectedLwpid.value = lwpid
    }

    override fun setSelectedRegs(regs: com.osr.ps5debugger.protocol.GpRegs?) {
        _selectedRegs.value = regs
    }

    override fun setSelectedDbRegs(regs: com.osr.ps5debugger.protocol.DbRegs?) {
        _selectedDbRegs.value = regs
    }

    override fun setSelectedFsGs(fsgs: Pair<Long, Long>?) {
        _selectedFsGs.value = fsgs
    }

    override suspend fun connect(ip: String): Boolean {
        log("SYSTEM", "Connecting to $ip...", LogEntry.Level.INFO)
        val ok = clientPort.connect(ip)
        if (ok) {
            lastConnectedIp = ip
            try {
                val authOk = clientPort.auth()
                log("SYSTEM", "Handshake authentication: " + if (authOk) "SUCCESS" else "FAILED", LogEntry.Level.INFO)
                clientPort.startKlogForwarder(ip)
            } catch (e: Exception) {
                log("SYSTEM", "Authentication error: ${e.message}", LogEntry.Level.ERROR)
            }
            _isConnected.value = true
        } else {
            _isConnected.value = false
        }
        return ok
    }

    override suspend fun disconnect() {
        clientPort.disconnect()
        _isConnected.value = false
    }

    override suspend fun refreshProcesses() {
        processManager.refreshProcesses()
    }

    override suspend fun selectProcess(proc: Process?) {
        processManager.selectProcess(
            proc = proc,
            isAttached = _isAttached,
            threadList = _threadList,
            selectedLwpid = _selectedLwpid,
            selectedRegs = _selectedRegs,
            selectedDbRegs = _selectedDbRegs,
            selectedFsGs = _selectedFsGs,
            isConnected = _isConnected,
            lastConnectedIp = lastConnectedIp,
            connectFunc = { connect(it) }
        )
    }

    override suspend fun loadMemoryMaps(proc: Process) {
        processManager.loadMemoryMaps(proc)
    }

    override suspend fun pullFile(path: String): Result<ByteArray> = try {
        val data = clientPort.pullFile(path)
        if (data != null) Result.success(data) else Result.failure(Exception("File not found or empty: $path"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun readMemory(address: Long, length: Int): Result<ByteArray> {
        // Check if the address is within any local map first
        val localMap = vmMaps.value.firstOrNull { it.localData != null && address >= it.start && address < it.end }
        if (localMap != null) {
            val offset = (address - localMap.start).toInt()
            val available = (localMap.end - address).toInt()
            val readLen = minOf(length, available)
            if (readLen <= 0) return Result.success(ByteArray(0))
            return try {
                Result.success(localMap.localData!!.copyOfRange(offset, offset + readLen))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val pid = processManager.activeProcess.value?.pid ?: return Result.failure(IllegalStateException("No active process selected"))
        return try {
            val data = clientPort.readMemory(pid, address, length)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeMemory(address: Long, data: ByteArray): Result<Boolean> {
        // Check if the address is within any local map first
        val localMap = vmMaps.value.firstOrNull { it.localData != null && address >= it.start && address < it.end }
        if (localMap != null) {
            val offset = (address - localMap.start).toInt()
            val available = (localMap.end - address).toInt()
            val writeLen = minOf(data.size, available)
            if (writeLen <= 0) return Result.success(true)
            return try {
                data.copyInto(localMap.localData!!, offset, 0, writeLen)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val pid = processManager.activeProcess.value?.pid ?: return Result.failure(IllegalStateException("No active process selected"))
        return try {
            val ok = clientPort.writeMemory(pid, address, data)
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun log(tag: String, message: String, level: LogEntry.Level) {
        logManager.log(tag, message, level)
    }

    override fun clearLogs() {
        logManager.clear()
    }

    override fun addToWatchlist(address: Long, type: String, byteLength: Int?) {
        watchlistManager.addToWatchlist(address, type, byteLength)
    }

    override fun addWatchItem(item: WatchItem) {
        watchlistManager.addWatchItem(item)
    }

    override fun updateWatchItem(item: WatchItem) {
        watchlistManager.updateWatchItem(item)
    }

    override fun removeWatchItem(item: WatchItem) {
        watchlistManager.removeWatchItem(item)
    }

    override fun clearWatchlist() {
        watchlistManager.clear()
    }

    override fun toggleFreezeWatchItem(item: WatchItem) {
        watchlistManager.toggleFreezeWatchItem(item) { addr, len -> readMemory(addr, len) }
    }

    override fun addCheat(titleId: String, version: String, cheat: com.osr.ps5debugger.domain.model.Cheat, gameName: String) {
        cheatManager.addCheat(titleId, version, cheat, gameName)
    }

    override fun toggleCheat(titleId: String, version: String, cheatId: String) {
        cheatManager.toggleCheat(titleId, version, cheatId)
    }

    override fun deleteCheat(titleId: String, version: String, cheatId: String) {
        cheatManager.deleteCheat(titleId, version, cheatId)
    }

    override fun updateGameName(titleId: String, name: String) {
        cheatManager.updateGameName(titleId, name)
    }

    override fun updateGameVersion(titleId: String, version: String) {
        cheatManager.updateGameVersion(titleId, version)
    }

    override fun updateGamePlatform(titleId: String, platform: String) {
        cheatManager.updateGamePlatform(titleId, platform)
    }

    override suspend fun applyCheat(pid: Int, cheat: com.osr.ps5debugger.domain.model.Cheat, newValue: String?) {
        cheatManager.applyCheat(pid, cheat, newValue)
    }

    override fun saveCheats(onResult: (String) -> Unit) {
        cheatManager.saveCheats(onResult)
    }

    override fun loadCheats(json: String) {
        cheatManager.loadCheats(json)
    }
}
