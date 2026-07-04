package com.osr.ps5debugger.domain.service

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.ports.outbound.LogStoragePort
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DebuggerDomainService(
    private val clientPort: DebuggerClientPort,
    private val logPort: LogStoragePort
) : DebuggerUseCase {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isAttached = MutableStateFlow(false)
    override val isAttached: StateFlow<Boolean> = _isAttached.asStateFlow()

    override fun setAttached(attached: Boolean) {
        _isAttached.value = attached
    }

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

    private val _processes = MutableStateFlow<List<Process>>(emptyList())
    override val processes: StateFlow<List<Process>> = _processes.asStateFlow()

    private val _activeProcess = MutableStateFlow<Process?>(null)
    override val activeProcess: StateFlow<Process?> = _activeProcess.asStateFlow()

    private val _activeProcessInfo = MutableStateFlow<Ps5ProcessInfo?>(null)
    override val activeProcessInfo: StateFlow<Ps5ProcessInfo?> = _activeProcessInfo.asStateFlow()

    override val debugEvents: SharedFlow<Ps5DebugEvent> = clientPort.debugEvents

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _watchlist = MutableStateFlow<List<WatchItem>>(emptyList())
    override val watchlist: StateFlow<List<WatchItem>> = _watchlist.asStateFlow()

    private val _vmMaps = MutableStateFlow<List<MemoryRange>>(emptyList())
    override val vmMaps: StateFlow<List<MemoryRange>> = _vmMaps.asStateFlow()

    private var lastConnectedIp: String? = null

    // Memory Freezing state
    private val frozenAddresses = mutableMapOf<Long, ByteArray>()
    private val freezeMutex = Mutex()
    private var freezeJob: Job? = null
    private var isFreezeLoopRunning = false
    private var freezeIntervalMs = 500L

    init {
        // Connection keeper loop to handle background disconnects (like detach socket closures)
        scope.launch {
            while (isActive) {
                if (_isConnected.value && !clientPort.isConnected) {
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
                    clientPort.stopDebugChannel()
                    clientPort.stopKlogForwarder()
                    clearFrozen()
                    _processes.value = emptyList()
                    _activeProcess.value = null
                    _activeProcessInfo.value = null
                    _vmMaps.value = emptyList()
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
                log("DEBUG", "Hit breakpoint/event on LWP ID ${event.lwpid} (thread: ${event.threadName}, rip: 0x${event.regs.rip.toString(16)})", LogEntry.Level.INFO)
            }
        }

        // Periodic watchlist value updater
        scope.launch {
            while (isActive) {
                if (_isConnected.value && _activeProcess.value != null && _watchlist.value.isNotEmpty()) {
                    val pid = _activeProcess.value!!.pid
                    val updated = _watchlist.value.map { item ->
                        try {
                            val size = typeSizeBytes(item.type, item.byteLength)
                            val data = clientPort.readMemory(pid, item.address, size)
                            item.copy(valueStr = parseValueBytes(data, item.type))
                        } catch (_: Exception) {
                            item.copy(valueStr = "??")
                        }
                    }
                    _watchlist.value = updated
                }
                delay(1000)
            }
        }
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
        if (!clientPort.isConnected) return
        try {
            val list = clientPort.getProcesses()
            _processes.value = list
            log("SYSTEM", "Refreshed processes list (${list.size} found)", LogEntry.Level.DEBUG)
        } catch (e: Exception) {
            log("SYSTEM", "Failed to retrieve process list: ${e.message}", LogEntry.Level.ERROR)
        }
    }

    override suspend fun selectProcess(proc: Process?) {
        _activeProcess.value = proc
        _vmMaps.value = emptyList()
        _isAttached.value = false
        _threadList.value = emptyList()
        _selectedLwpid.value = null
        _selectedRegs.value = null
        _selectedDbRegs.value = null
        _selectedFsGs.value = null
        if (proc != null) {
            if (!clientPort.isConnected && lastConnectedIp != null) {
                log("SYSTEM", "Connection lost. Attempting auto-reconnect to $lastConnectedIp...", LogEntry.Level.WARN)
                val ok = connect(lastConnectedIp!!)
                if (!ok) {
                    log("SYSTEM", "Auto-reconnect failed.", LogEntry.Level.ERROR)
                    _isConnected.value = false
                    return
                }
            }

            try {
                val info = clientPort.getProcessInfo(proc.pid)
                _activeProcessInfo.value = info
                log("SYSTEM", "Selected active process: ${proc.name} (PID: ${proc.pid}, TitleID: ${info.titleId})", LogEntry.Level.INFO)
            } catch (e: Exception) {
                _activeProcessInfo.value = null
                log("SYSTEM", "Selected active process: ${proc.name} (PID: ${proc.pid}) (Could not load process details: ${e.message})", LogEntry.Level.INFO)
            }

            try {
                loadMemoryMaps(proc)
            } catch (e: Exception) {
                log("SYSTEM", "Failed to load memory maps: ${e.message}", LogEntry.Level.ERROR)
            }
        } else {
            _activeProcessInfo.value = null
        }
    }

    override suspend fun loadMemoryMaps(proc: Process) {
        if (!clientPort.isConnected) return
        try {
            val maps = clientPort.getMaps(proc.pid)
            _vmMaps.value = maps
            log("SYSTEM", "Loaded ${maps.size} virtual memory maps for PID ${proc.pid}", LogEntry.Level.DEBUG)
        } catch (e: Exception) {
            log("SYSTEM", "Failed to load memory maps: ${e.message}", LogEntry.Level.ERROR)
        }
    }

    override suspend fun readMemory(address: Long, length: Int): Result<ByteArray> {
        val pid = _activeProcess.value?.pid ?: return Result.failure(IllegalStateException("No active process selected"))
        return try {
            val data = clientPort.readMemory(pid, address, length)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeMemory(address: Long, data: ByteArray): Result<Boolean> {
        val pid = _activeProcess.value?.pid ?: return Result.failure(IllegalStateException("No active process selected"))
        return try {
            val ok = clientPort.writeMemory(pid, address, data)
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun log(tag: String, message: String, level: LogEntry.Level) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        _logs.update { it + entry }
        logPort.persistLog(entry)
    }

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun addToWatchlist(address: Long, type: String, byteLength: Int?) {
        val label = "HexWatch_0x" + address.toString(16).uppercase()
        _watchlist.update { list ->
            if (list.none { it.address == address }) {
                val item = WatchItem(label = label, address = address, type = type, byteLength = byteLength)
                log("WATCHLIST", "Added 0x${address.toString(16).uppercase()} to watchlist", LogEntry.Level.INFO)
                list + item
            } else {
                list
            }
        }
    }
    override fun addWatchItem(item: WatchItem) {
        _watchlist.update { list ->
            if (list.none { it.address == item.address }) {
                log("WATCHLIST", "Added 0x${item.address.toString(16).uppercase()} to watchlist", LogEntry.Level.INFO)
                list + item
            } else {
                list
            }
        }
    }
    override fun updateWatchItem(item: WatchItem) {
        _watchlist.update { list ->
            list.map { if (it.address == item.address) item else it }
        }
    }

    override fun removeWatchItem(item: WatchItem) {
        _watchlist.update { list ->
            list.filterNot { it.address == item.address }
        }
        scope.launch { unfreeze(item.address) }
    }

    override fun clearWatchlist() {
        _watchlist.value = emptyList()
        scope.launch { clearFrozen() }
    }

    override fun toggleFreezeWatchItem(item: WatchItem) {
        val updated = item.copy(isFrozen = !item.isFrozen)
        updateWatchItem(updated)
        
        scope.launch {
            if (updated.isFrozen) {
                // Read current value of the watch item to freeze it
                readMemory(item.address, item.byteLength ?: 4).onSuccess { data ->
                    freeze(item.address, data)
                }
            } else {
                unfreeze(item.address)
            }
        }
    }

    // Domain loop for freezing values in background
    private suspend fun freeze(address: Long, value: ByteArray) {
        freezeMutex.withLock {
            frozenAddresses[address] = value
        }
        startFreezeLoop()
    }

    private suspend fun unfreeze(address: Long) {
        freezeMutex.withLock {
            frozenAddresses.remove(address)
            if (frozenAddresses.isEmpty()) {
                stopFreezeLoop()
            }
        }
    }

    private suspend fun clearFrozen() {
        freezeMutex.withLock {
            frozenAddresses.clear()
            stopFreezeLoop()
        }
    }

    private fun startFreezeLoop() {
        if (isFreezeLoopRunning) return
        isFreezeLoopRunning = true
        freezeJob = scope.launch(Dispatchers.IO) {
            while (isActive && isFreezeLoopRunning) {
                val targets = freezeMutex.withLock {
                    frozenAddresses.map { Pair(it.key, it.value) }
                }
                
                val pid = _activeProcess.value?.pid
                if (pid != null && clientPort.isConnected && targets.isNotEmpty()) {
                    try {
                        clientPort.writeMemoryMulti(pid, targets, withStatusReport = false)
                    } catch (_: Exception) {}
                }
                
                delay(freezeIntervalMs)
            }
            isFreezeLoopRunning = false
        }
    }

    private fun stopFreezeLoop() {
        isFreezeLoopRunning = false
        freezeJob?.cancel()
        freezeJob = null
    }

    private fun typeSizeBytes(type: String, byteLength: Int?): Int = byteLength?.coerceAtLeast(1) ?: when (type) {
        "Byte" -> 1
        "Int16" -> 2
        "Int32" -> 4
        "Int64" -> 8
        "Float" -> 4
        "Double" -> 8
        "String" -> 32
        else -> 4
    }

    private fun parseValueBytes(bytes: ByteArray, type: String): String {
        if (bytes.isEmpty()) return "??"
        val buf = com.osr.ps5debugger.protocol.BinaryBuffer(bytes)
        return when (type) {
            "Byte"   -> buf.readByte().toString()
            "Int16"  -> buf.readShort().toString()
            "Int32"  -> buf.readInt().toString()
            "Int64"  -> buf.readLong().toString()
            "Float"  -> buf.readFloat().toString()
            "Double" -> buf.readDouble().toString()
            "String" -> bytes
                .takeWhile { it != 0.toByte() }
                .map { byte ->
                    val value = byte.toInt() and 0xFF
                    if (value in 32..126) value.toChar() else '.'
                }
                .joinToString("")
            else -> "??"
        }
    }
}
