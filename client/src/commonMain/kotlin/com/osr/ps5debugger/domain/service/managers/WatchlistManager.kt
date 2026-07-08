package com.osr.ps5debugger.domain.service.managers

import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WatchlistManager(
    private val clientPort: DebuggerClientPort,
    private val scope: CoroutineScope,
    private val logManager: LogManager
) {
    private val _watchlist = MutableStateFlow<List<WatchItem>>(emptyList())
    val watchlist: StateFlow<List<WatchItem>> = _watchlist.asStateFlow()

    private val frozenAddresses = mutableMapOf<Long, ByteArray>()
    private val freezeMutex = Mutex()
    private var freezeJob: Job? = null
    private var isFreezeLoopRunning = false
    private val freezeIntervalMs = 500L

    init {
        // Periodic watchlist value updater
        scope.launch {
            while (isActive) {
                val activeProc = getActiveProcessPid?.invoke()
                if (clientPort.isConnected && activeProc != null && _watchlist.value.isNotEmpty()) {
                    val updated = _watchlist.value.map { item ->
                        try {
                            val size = typeSizeBytes(item.type, item.byteLength)
                            val data = clientPort.readMemory(activeProc, item.address, size)
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

    var getActiveProcessPid: (() -> Int?)? = null

    fun addToWatchlist(address: Long, type: String, byteLength: Int?) {
        val label = "HexWatch_0x" + address.toString(16).uppercase()
        _watchlist.update { list ->
            if (list.none { it.address == address }) {
                val item = WatchItem(label = label, address = address, type = type, byteLength = byteLength)
                logManager.log("WATCHLIST", "Added 0x${address.toString(16).uppercase()} to watchlist", LogEntry.Level.INFO)
                list + item
            } else {
                list
            }
        }
    }

    fun addWatchItem(item: WatchItem) {
        _watchlist.update { list ->
            if (list.none { it.address == item.address }) {
                logManager.log("WATCHLIST", "Added 0x${item.address.toString(16).uppercase()} to watchlist", LogEntry.Level.INFO)
                list + item
            } else {
                list
            }
        }
    }

    fun updateWatchItem(item: WatchItem) {
        _watchlist.update { list ->
            list.map { if (it.address == item.address) item else it }
        }
    }

    fun removeWatchItem(item: WatchItem) {
        _watchlist.update { list ->
            list.filterNot { it.address == item.address }
        }
        scope.launch { unfreeze(item.address) }
    }

    fun clear() {
        _watchlist.value = emptyList()
        scope.launch { clearFrozen() }
    }

    fun toggleFreezeWatchItem(item: WatchItem, readMemory: suspend (Long, Int) -> Result<ByteArray>) {
        val updated = item.copy(isFrozen = !item.isFrozen)
        updateWatchItem(updated)
        
        scope.launch {
            if (updated.isFrozen) {
                readMemory(item.address, item.byteLength ?: 4).onSuccess { data ->
                    freeze(item.address, data)
                }
            } else {
                unfreeze(item.address)
            }
        }
    }

    suspend fun freeze(address: Long, value: ByteArray) {
        freezeMutex.withLock {
            frozenAddresses[address] = value
        }
        startFreezeLoop()
    }

    suspend fun unfreeze(address: Long) {
        freezeMutex.withLock {
            frozenAddresses.remove(address)
            if (frozenAddresses.isEmpty()) {
                stopFreezeLoop()
            }
        }
    }

    suspend fun clearFrozen() {
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
                
                val pid = getActiveProcessPid?.invoke()
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
