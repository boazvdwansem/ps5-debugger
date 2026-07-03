package com.osr.ps5debugger.ports.outbound

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.flow.SharedFlow

interface DebuggerClientPort {
    val isConnected: Boolean
    val debugEvents: SharedFlow<Ps5DebugEvent>
    val logLines: SharedFlow<String>

    suspend fun connect(ip: String): Boolean
    suspend fun disconnect()
    suspend fun auth(): Boolean
    suspend fun ping(): Boolean
    suspend fun getProcesses(): List<Process>
    suspend fun getMaps(pid: Int): List<MemoryRange>
    suspend fun getProcessInfo(pid: Int): Ps5ProcessInfo
    suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray
    suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean
    suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean): Boolean
    
    fun startDebugChannel()
    fun stopDebugChannel()
    fun startKlogForwarder(ip: String)
    fun stopKlogForwarder()
}
