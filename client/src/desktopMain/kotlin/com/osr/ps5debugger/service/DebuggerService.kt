package com.osr.ps5debugger.service

import com.osr.ps5debugger.network.*
import com.osr.ps5debugger.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WatchItem(
    var label: String,
    val address: Long,
    var type: String, // "Byte", "Int16", "Int32", "Int64", "Float", "Double", "String"
    var valueStr: String = "??",
    var isFrozen: Boolean = false,
    var comment: String = "",
    val byteLength: Int? = null
)

object DebuggerService {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val connection = Ps5Connection()
    val client = Ps5Client(connection)
    
    val watchlist = androidx.compose.runtime.mutableStateListOf<WatchItem>()
    val vmMaps = androidx.compose.runtime.mutableStateListOf<Ps5VmMapEntry>()
    
    // Services
    val freezer = MemoryFreezer(client, scope)
    val debugChannel = Ps5DebugChannel(scope)
    val klogForwarder = Ps5KlogForwarder(scope)

    // State Flows
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _processes = MutableStateFlow<List<Ps5Process>>(emptyList())
    val processes: StateFlow<List<Ps5Process>> = _processes.asStateFlow()

    private val _activeProcess = MutableStateFlow<Ps5Process?>(null)
    val activeProcess: StateFlow<Ps5Process?> = _activeProcess.asStateFlow()

    private val _activeProcessInfo = MutableStateFlow<Ps5ProcessInfo?>(null)
    val activeProcessInfo: StateFlow<Ps5ProcessInfo?> = _activeProcessInfo.asStateFlow()

    private val _debugEvents = MutableSharedFlow<Ps5DebugEvent>(extraBufferCapacity = 64)
    val debugEvents: SharedFlow<Ps5DebugEvent> = _debugEvents.asSharedFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    val activePid: Int? get() = _activeProcess.value?.pid

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val level: Level
    ) {
        enum class Level { DEBUG, INFO, WARN, ERROR, PROTOCOL }
    }

    init {
        // Log connection changes
        scope.launch {
            isConnected.collect { connected ->
                if (connected) {
                    log("SYSTEM", "Connected to PS5 at ${connection.ipAddress}", LogEntry.Level.INFO)
                    // Start services
                    klogForwarder.start(connection.ipAddress!!)
                    debugChannel.start()
                } else {
                    log("SYSTEM", "Disconnected from PS5", LogEntry.Level.WARN)
                    klogForwarder.stop()
                    debugChannel.stop()
                    freezer.clear()
                    _processes.value = emptyList()
                    _activeProcess.value = null
                    _activeProcessInfo.value = null
                }
            }
        }

        // Forward debug channel events
        scope.launch {
            debugChannel.events.collect { event ->
                log("DEBUG", "Hit breakpoint/event on LWP ID ${event.lwpid} (thread: ${event.threadName}, rip: 0x${event.regs.rip.toString(16)})", LogEntry.Level.INFO)
                _debugEvents.emit(event)
            }
        }

        // Forward kernel logs
        scope.launch {
            klogForwarder.logLines.collect { line ->
                log("KERNEL", line, LogEntry.Level.INFO)
            }
        }
    }

    fun log(tag: String, message: String, level: LogEntry.Level = LogEntry.Level.DEBUG) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        _logs.update { (it + entry).takeLast(1000) }
    }

    suspend fun connect(ip: String): Boolean {
        log("SYSTEM", "Connecting to $ip...", LogEntry.Level.INFO)
        val ok = connection.connect(ip)
        if (ok) {
            // Attempt authentications to unlock scan operations
            try {
                val authOk = client.auth()
                log("SYSTEM", "Handshake authentication: " + if (authOk) "SUCCESS" else "FAILED", LogEntry.Level.INFO)
            } catch (e: Exception) {
                log("SYSTEM", "Authentication error: ${e.message}", LogEntry.Level.ERROR)
            }
            _isConnected.value = true
        } else {
            _isConnected.value = false
        }
        return ok
    }

    suspend fun disconnect() {
        connection.disconnect()
        _isConnected.value = false
    }

    suspend fun refreshProcesses() {
        if (!connection.isConnected) return
        try {
            val list = client.getProcesses()
            _processes.value = list
            log("SYSTEM", "Refreshed processes list (${list.size} found)", LogEntry.Level.DEBUG)
        } catch (e: Exception) {
            log("SYSTEM", "Failed to retrieve process list: ${e.message}", LogEntry.Level.ERROR)
        }
    }

    suspend fun selectProcess(proc: Ps5Process?) {
        _activeProcess.value = proc
        if (proc != null) {
            try {
                val info = client.getProcessInfo(proc.pid)
                _activeProcessInfo.value = info
                log("SYSTEM", "Selected active process: ${proc.name} (PID: ${proc.pid}, TitleID: ${info.titleId})", LogEntry.Level.INFO)
            } catch (e: Exception) {
                _activeProcessInfo.value = null
                log("SYSTEM", "Selected active process: ${proc.name} (PID: ${proc.pid})", LogEntry.Level.INFO)
            }
        } else {
            _activeProcessInfo.value = null
        }
    }

    fun addToWatchlist(address: Long, type: String = "Int32", byteLength: Int? = null) {
        val label = "HexWatch_0x" + address.toString(16).uppercase()
        val exists = watchlist.any { it.address == address }
        if (!exists) {
            watchlist.add(WatchItem(label = label, address = address, type = type, byteLength = byteLength))
            log("WATCHLIST", "Added 0x${address.toString(16).uppercase()} to watchlist", LogEntry.Level.INFO)
        }
    }
}
