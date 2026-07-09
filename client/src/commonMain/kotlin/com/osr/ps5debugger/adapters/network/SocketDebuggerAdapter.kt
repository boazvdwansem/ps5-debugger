package com.osr.ps5debugger.adapters.network

import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import com.osr.ps5debugger.di.AppContainer
import kotlinx.coroutines.launch

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
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            kotlinx.coroutines.delay(500)
            AppContainer.debuggerUseCase.log("MOCK", "Simulation Mode Active - Connection spoofed", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
            return true
        }
        val timeout = com.osr.ps5debugger.util.DefaultIpHelper.getConnectionTimeoutMs()
        return connection.connect(ip, timeoutMs = timeout)
    }

    override suspend fun disconnect() {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            AppContainer.debuggerUseCase.log("MOCK", "Simulation Mode Deactivated", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
            return
        }
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

    private var mockJob: kotlinx.coroutines.Job? = null

    override fun startDebugChannel() {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            mockJob?.cancel()
            mockJob = scope.launch {
                while (com.osr.ps5debugger.di.AppContainer.debugMockEnabled && isConnected) {
                    kotlinx.coroutines.delay(10000) // every 10 seconds
                    val fakeEvent = Ps5DebugEvent(
                        lwpid = 10001,
                        status = 0x137f, // stop signal SIGTRAP
                        threadName = "MainThread",
                        regs = com.osr.ps5debugger.protocol.GpRegs(
                            r15=0, r14=0, r13=0, r12=0, r11=0, r10=0, r9=0, r8=0,
                            rdi=0x400000L, rsi=0, rbp=0x7FFFFFFFDF00L, rbx=0, rdx=0, rcx=0, rax=5,
                            trapno=3, fs=0, gs=0, err=0, es=0, ds=0,
                            rip=0x400009L, cs=0, rflags=0x202, rsp=0x7FFFFFFFDEE0L, ss=0
                        ),
                        fpu = ByteArray(832),
                        dbregs = com.osr.ps5debugger.protocol.DbRegs(0,0,0,0,0,0,0,0, LongArray(8))
                    )
                    debugChannel.triggerMockEvent(fakeEvent)
                }
            }
        } else {
            debugChannel.start()
        }
    }

    override fun stopDebugChannel() {
        mockJob?.cancel()
        mockJob = null
        debugChannel.stop()
    }

    override fun startKlogForwarder(ip: String) {
        klogForwarder.start(ip)
    }

    override fun stopKlogForwarder() {
        klogForwarder.stop()
    }
}
