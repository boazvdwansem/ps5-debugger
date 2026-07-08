package com.osr.ps5debugger.adapters.network

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

class SocketDebuggerAdapter(
    private val scope: CoroutineScope
) : DebuggerClientPort {

    val connection = com.osr.ps5debugger.network.Ps5Connection()
    val client = com.osr.ps5debugger.network.Ps5Client(connection)
    private val debugChannel = com.osr.ps5debugger.network.Ps5DebugChannel(scope)
    private val klogForwarder = com.osr.ps5debugger.network.Ps5KlogForwarder(scope)

    override val isConnected: Boolean get() = connection.isConnected
    override val debugEvents: SharedFlow<Ps5DebugEvent> get() = debugChannel.events
    override val logLines: SharedFlow<String> get() = klogForwarder.logLines

    override suspend fun connect(ip: String): Boolean {
        val timeout = com.osr.ps5debugger.util.DefaultIpHelper.getConnectionTimeoutMs()
        return connection.connect(ip, timeoutMs = timeout)
    }

    override suspend fun disconnect() {
        connection.disconnect()
    }

    override suspend fun auth(): Boolean {
        return client.auth()
    }

    override suspend fun ping(): Boolean {
        return client.ping()
    }

    override suspend fun getProcesses(): List<Process> {
        return client.getProcesses().map { Process(name = it.name, pid = it.pid) }
    }

    override suspend fun getMaps(pid: Int): List<MemoryRange> {
        return client.getMaps(pid).map { 
            MemoryRange(
                name = it.name,
                start = it.start,
                end = it.end,
                offset = it.offset,
                protections = it.prot
            )
        }
    }

    override suspend fun getProcessInfo(pid: Int): Ps5ProcessInfo {
        return client.getProcessInfo(pid)
    }

    override suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray {
        return client.readMemory(pid, address, length)
    }

    override suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean {
        return client.writeMemory(pid, address, data)
    }

    override suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean): Boolean {
        return client.writeMemoryMulti(pid, writes, withStatusReport)
    }

    override fun startDebugChannel() {
        debugChannel.start()
    }

    override fun stopDebugChannel() {
        debugChannel.stop()
    }

    override fun startKlogForwarder(ip: String) {
        klogForwarder.start(ip)
    }

    override fun stopKlogForwarder() {
        klogForwarder.stop()
    }
}
