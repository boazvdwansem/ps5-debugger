package com.osr.ps5debugger.network

import com.osr.ps5debugger.network.services.ProcessService
import com.osr.ps5debugger.network.services.MemoryService
import com.osr.ps5debugger.network.services.DebuggerService
import com.osr.ps5debugger.protocol.*
import java.io.InputStream
import java.io.OutputStream

class Ps5Client(val connection: Ps5Connection) {

    private val processService = ProcessService(connection)
    private val memoryService = MemoryService(connection)
    private val debuggerService = DebuggerService(connection)

    // LFSR for authentication
    private class Lfsr {
        var s1: Int = 0
        var s2: Int = 0
        var s3: Int = 0
        var s4: Int = 0

        fun next(): Int {
            var n1 = s1
            var n2 = s2
            var n3 = s3
            var n4 = s4

            n1 = ((n1 shl 18) and 0xFFF80000.toInt()) xor ((n1 xor (n1 shl 6)).ushr(13))
            n2 = ((n2 shl  2) and 0xFFFFFFE0.toInt()) xor ((n2 xor (n2 shl 2)).ushr(27))
            n3 = ((n3 shl  7) and 0xFFFFF800.toInt()) xor ((n3 xor (n3 shl 13)).ushr(21))
            n4 = ((n4 shl 13) and 0xFFF00000.toInt()) xor ((n4 xor (n4 shl 3)).ushr(12))

            s1 = n1
            s2 = n2
            s3 = n3
            s4 = n4
            return n1 xor n2 xor n3 xor n4
        }

        fun setState(a: Int, b: Int, c: Int, d: Int) {
            s1 = a
            s2 = b
            s3 = c
            s4 = d
        }
    }

    suspend fun auth(flags: Int = 3): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(8).apply {
            writeInt(ProtocolConstants.AUTH_MAGIC)
            writeInt(flags)
        }.bytes
        
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_AUTH, payload)
        
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute false

        // Challenge length (2 bytes)
        val lenBytes = connection.readExactly(inStr, 2)
        val chlen = ((lenBytes[1].toInt() and 0xFF shl 8) or (lenBytes[0].toInt() and 0xFF))
        if (chlen != 64) return@execute false

        val challenge = connection.readExactly(inStr, 64)

        // Generate response using xorshift-128
        val lfsr = Lfsr()
        lfsr.setState(200, 300, 400, 500)
        val keystream = ByteArray(256)
        for (i in 0 until 256) {
            keystream[i] = lfsr.next().toByte()
        }

        val response = ByteArray(64)
        for (i in 0 until 64) {
            response[i] = (challenge[i].toInt() xor keystream[i].toInt()).toByte()
        }

        outStr.write(response)
        outStr.flush()

        val finalStatus = connection.receiveStatus(inStr)
        finalStatus == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun ping(): Boolean = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_NOP)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun getVersion(): String = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_VERSION)
        val lenBytes = connection.readExactly(inStr, 4)
        val len = BinaryBuffer(lenBytes).readInt()
        val data = connection.readExactly(inStr, len)
        String(data, Charsets.UTF_8)
    }

    suspend fun getFwVersion(): String = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_FW_VERSION)
        val verBytes = connection.readExactly(inStr, 2)
        val rawVer = ((verBytes[1].toInt() and 0xFF shl 8) or (verBytes[0].toInt() and 0xFF))
        val major = rawVer / 100
        val minor = rawVer % 100
        val minorStr = if (minor < 10) "0$minor" else "$minor"
        "$major.$minorStr"
    }

    suspend fun getBranding(): Pair<String, String> = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_BRANDING)
        val lenBytes = connection.readExactly(inStr, 4)
        val len = BinaryBuffer(lenBytes).readInt()
        val data = connection.readExactly(inStr, len)
        
        var nulIdx = -1
        for (i in data.indices) {
            if (data[i] == 0.toByte()) {
                nulIdx = i
                break
            }
        }
        if (nulIdx != -1) {
            val brand = String(data, 0, nulIdx, Charsets.UTF_8)
            val caps = String(data, nulIdx + 1, data.size - nulIdx - 1, Charsets.UTF_8)
            Pair(brand, caps)
        } else {
            Pair(String(data, Charsets.UTF_8), "")
        }
    }

    suspend fun sendNotification(text: String): Boolean = connection.execute { inStr, outStr ->
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = BinaryBuffer(4 + textBytes.size).apply {
            writeInt(textBytes.size)
            writeBytes(textBytes)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_CONSOLE_NOTIFY, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    // Process Delegation
    suspend fun getProcesses(): List<Ps5Process> = processService.getProcesses()
    suspend fun getMaps(pid: Int): List<Ps5VmMapEntry> = processService.getMaps(pid)
    suspend fun getProcessInfo(pid: Int): Ps5ProcessInfo = processService.getProcessInfo(pid)
    suspend fun getForegroundApp(): Ps5ForegroundApp = processService.getForegroundApp()

    // Memory Delegation
    suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray = memoryService.readMemory(pid, address, length)
    suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean = memoryService.writeMemory(pid, address, data)
    suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean = false): Boolean =
        memoryService.writeMemoryMulti(pid, writes, withStatusReport)
    suspend fun allocateMemory(pid: Int, length: Int): Long = memoryService.allocateMemory(pid, length)
    suspend fun allocateMemoryHinted(pid: Int, hint: Long, length: Int): Long = memoryService.allocateMemoryHinted(pid, hint, length)
    suspend fun freeMemory(pid: Int, address: Long, length: Int): Boolean = memoryService.freeMemory(pid, address, length)
    suspend fun changeProtection(pid: Int, address: Long, length: Int, prot: Int): Boolean = memoryService.changeProtection(pid, address, length, prot)
    suspend fun disassembleRegion(pid: Int, address: Long, length: Int, maxEntries: Int = 1000): List<Ps5DisasmInstr> =
        memoryService.disassembleRegion(pid, address, length, maxEntries)
    suspend fun getKernBase(): Long = memoryService.getKernBase()

    // Debugger Delegation
    suspend fun attach(pid: Int): Boolean = debuggerService.attach(pid)
    suspend fun detach(): Boolean = debuggerService.detach()
    suspend fun setBreakpoint(index: Int, enabled: Boolean, address: Long): Boolean = debuggerService.setBreakpoint(index, enabled, address)
    suspend fun setWatchpoint(index: Int, enabled: Boolean, length: Int, breakType: Int, address: Long): Boolean =
        debuggerService.setWatchpoint(index, enabled, length, breakType, address)
    suspend fun stopProcess(): Boolean = debuggerService.stopProcess()
    suspend fun resumeProcess(): Boolean = debuggerService.resumeProcess()
    suspend fun stepProcess(): Boolean = debuggerService.stepProcess()
    suspend fun getThreadList(): List<Int> = debuggerService.getThreadList()
    suspend fun suspendThread(lwpid: Int): Boolean = debuggerService.suspendThread(lwpid)
    suspend fun resumeThread(lwpid: Int): Boolean = debuggerService.resumeThread(lwpid)
    suspend fun getRegs(lwpid: Int): GpRegs = debuggerService.getRegs(lwpid)
    suspend fun setRegs(lwpid: Int, regs: GpRegs): Boolean = debuggerService.setRegs(lwpid, regs)
    suspend fun getFpRegs(lwpid: Int): ByteArray = debuggerService.getFpRegs(lwpid)
    suspend fun setFpRegs(lwpid: Int, fpBytes: ByteArray): Boolean = debuggerService.setFpRegs(lwpid, fpBytes)
    suspend fun getDbRegs(lwpid: Int): DbRegs = debuggerService.getDbRegs(lwpid)
    suspend fun setDbRegs(lwpid: Int, dbRegs: DbRegs): Boolean = debuggerService.setDbRegs(lwpid, dbRegs)
    suspend fun getFsGsBase(lwpid: Int): Pair<Long, Long> = debuggerService.getFsGsBase(lwpid)
    suspend fun setFsGsBase(lwpid: Int, fsBase: Long, gsBase: Long): Boolean = debuggerService.setFsGsBase(lwpid, fsBase, gsBase)
    suspend fun stepThread(lwpid: Int): Boolean = debuggerService.stepThread(lwpid)
}
