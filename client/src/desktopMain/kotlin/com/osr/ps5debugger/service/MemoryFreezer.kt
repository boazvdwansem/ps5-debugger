package com.osr.ps5debugger.service

import com.osr.ps5debugger.network.Ps5Client
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FrozenAddress(
    val address: Long,
    val value: ByteArray,
    val label: String = "",
    val comment: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrozenAddress
        if (address != other.address) return false
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

class MemoryFreezer(
    private val client: Ps5Client,
    private val scope: CoroutineScope
) {
    private val frozenAddresses = mutableMapOf<Long, FrozenAddress>()
    private val mutex = Mutex()
    private var job: Job? = null
    
    @Volatile
    var isRunning: Boolean = false
        private set
        
    @Volatile
    var intervalMs: Long = 500
        set(value) {
            field = maxOf(50, value)
        }

    suspend fun freeze(address: Long, value: ByteArray, label: String = "", comment: String = "") {
        mutex.withLock {
            frozenAddresses[address] = FrozenAddress(address, value, label, comment)
        }
        startLoop()
    }

    suspend fun unfreeze(address: Long) {
        mutex.withLock {
            frozenAddresses.remove(address)
            if (frozenAddresses.isEmpty()) {
                stopLoop()
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            frozenAddresses.clear()
            stopLoop()
        }
    }

    suspend fun getFrozen(): List<FrozenAddress> {
        mutex.withLock {
            return frozenAddresses.values.toList()
        }
    }

    private fun startLoop() {
        if (isRunning) return
        isRunning = true
        job = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                val targets = mutex.withLock {
                    frozenAddresses.values.toList()
                }
                
                if (targets.isEmpty()) {
                    delay(intervalMs)
                    continue
                }
                
                try {
                    // Batch write all frozen addresses in one multi-write packet
                    val writes = targets.map { Pair(it.address, it.value) }
                    // Note: MemoryFreezer doesn't know the PID, so we write to the active process.
                    // The client coordinates the PID, so we will use the activePid from the debugger service.
                    val activePid = DebuggerService.activePid
                    if (activePid != null && client.connection.isConnected) {
                        client.writeMemoryMulti(activePid, writes, withStatusReport = false)
                    }
                } catch (_: Exception) {
                    // Ignore transient networking errors during freeze loop
                }
                
                delay(intervalMs)
            }
            isRunning = false
        }
    }

    private fun stopLoop() {
        isRunning = false
        job?.cancel()
        job = null
    }
}
