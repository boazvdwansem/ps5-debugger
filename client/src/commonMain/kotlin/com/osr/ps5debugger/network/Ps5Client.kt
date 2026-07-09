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

    private val mockCode = byteArrayOf(
        0x55.toByte(),                                     // 0: push rbp
        0x48.toByte(), 0x89.toByte(), 0xE5.toByte(),       // 1: mov rbp, rsp
        0x48.toByte(), 0x83.toByte(), 0xEC.toByte(), 0x20.toByte(), // 2: sub rsp, 0x20
        0xC7.toByte(), 0x45.toByte(), 0xFC.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // 3: mov [rbp - 4], 0
        0x8B.toByte(), 0x45.toByte(), 0xFC.toByte(),       // 4: mov eax, [rbp - 4]
        0x83.toByte(), 0xC0.toByte(), 0x01.toByte(),       // 5: add eax, 1
        0x83.toByte(), 0xF8.toByte(), 0x10.toByte(),       // 6: cmp eax, 16
        0x75.toByte(), 0x05.toByte(),                      // 7: jne to offset +5 (which is index 9: jmp)
        0x89.toByte(), 0x45.toByte(), 0xFC.toByte(),       // 8: mov [rbp - 4], eax
        0xE9.toByte(), 0xDD.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // 9: jmp to offset
        0xC9.toByte(),                                     // 10: leave
        0xC3.toByte()                                      // 11: ret
    )

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

    suspend fun auth(flags: Int = 3): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return connection.execute { inStr, outStr ->
            val payload = BinaryBuffer(8).apply {
                writeInt(ProtocolConstants.AUTH_MAGIC)
                writeInt(flags)
            }.bytes
            
            connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_AUTH, payload)
            
            val status = connection.receiveStatus(inStr)
            if (status != ProtocolConstants.CMD_SUCCESS) return@execute false

            val lenBytes = connection.readExactly(inStr, 2)
            val chlen = ((lenBytes[1].toInt() and 0xFF shl 8) or (lenBytes[0].toInt() and 0xFF))
            if (chlen != 64) return@execute false

            val challenge = connection.readExactly(inStr, 64)

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
    }

    suspend fun ping(): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return connection.execute { inStr, outStr ->
            connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_NOP)
            connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
        }
    }

    suspend fun getVersion(): String {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return "ps5debug-mock-1.0"
        return connection.execute { inStr, outStr ->
            connection.sendPacket(outStr, ProtocolConstants.CMD_VERSION)
            val lenBytes = connection.readExactly(inStr, 4)
            val len = BinaryBuffer(lenBytes).readInt()
            val data = connection.readExactly(inStr, len)
            String(data, Charsets.UTF_8)
        }
    }

    suspend fun getFwVersion(): String {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return "8.40"
        return connection.execute { inStr, outStr ->
            connection.sendPacket(outStr, ProtocolConstants.CMD_FW_VERSION)
            val verBytes = connection.readExactly(inStr, 2)
            val rawVer = ((verBytes[1].toInt() and 0xFF shl 8) or (verBytes[0].toInt() and 0xFF))
            val major = rawVer / 100
            val minor = rawVer % 100
            val minorStr = if (minor < 10) "0$minor" else "$minor"
            "$major.$minorStr"
        }
    }

    suspend fun getBranding(): Pair<String, String> {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return Pair("ps5debug-mock", "DUMP,DISASM,DEBUG")
        return connection.execute { inStr, outStr ->
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
    }

    suspend fun sendNotification(text: String): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return connection.execute { inStr, outStr ->
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val payload = BinaryBuffer(4 + textBytes.size).apply {
                writeInt(textBytes.size)
                writeBytes(textBytes)
            }.bytes
            connection.sendPacket(outStr, ProtocolConstants.CMD_CONSOLE_NOTIFY, payload)
            connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
        }
    }

    // Process Delegation
    suspend fun getProcesses(): List<Ps5Process> {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            return listOf(
                Ps5Process("eboot.bin", 1234),
                Ps5Process("daemon_service", 99),
                Ps5Process("shellui", 50)
            )
        }
        return processService.getProcesses()
    }

    suspend fun getMaps(pid: Int): List<Ps5VmMapEntry> {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            return listOf(
                Ps5VmMapEntry("executable", 0x400000L, 0x410000L, 0L, 5), // RX
                Ps5VmMapEntry("libkernel.sprx", 0x8001000000L, 0x8001020000L, 0L, 5), // RX
                Ps5VmMapEntry("stack", 0x7FFFFFFF0000L, 0x7FFFFFFFF000L, 0L, 3), // RW
                Ps5VmMapEntry("heap", 0x9000000000L, 0x9001000000L, 0L, 3) // RW
            )
        }
        return processService.getMaps(pid)
    }

    suspend fun getProcessInfo(pid: Int): Ps5ProcessInfo {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            return Ps5ProcessInfo(pid, "eboot.bin", "/system/app/eboot.bin", "CUSA00001", "JP0001-CUSA00001_00-0000000000000000")
        }
        return processService.getProcessInfo(pid)
    }

    suspend fun getForegroundApp(): Ps5ForegroundApp {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            return Ps5ForegroundApp(1234, "CUSA00001", "JP0001-CUSA00001_00-0000000000000000", "eboot.bin", "1.00")
        }
        return processService.getForegroundApp()
    }

    // Memory Delegation
    suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            val safeLength = minOf(length, 1024 * 1024) // Cap at 1MB
            val bytes = ByteArray(safeLength)
            var i = 0
            while (i < safeLength) {
                val offset = ((address + i) % mockCode.size).toInt()
                val chunk = minOf(safeLength - i, mockCode.size - offset)
                System.arraycopy(mockCode, offset, bytes, i, chunk)
                i += chunk
            }
            return bytes
        }
        return memoryService.readMemory(pid, address, length)
    }

    suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return memoryService.writeMemory(pid, address, data)
    }

    suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean = false): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return memoryService.writeMemoryMulti(pid, writes, withStatusReport)
    }

    suspend fun allocateMemory(pid: Int, length: Int): Long {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return 0x9000500000L
        return memoryService.allocateMemory(pid, length)
    }

    suspend fun allocateMemoryHinted(pid: Int, hint: Long, length: Int): Long {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return hint
        return memoryService.allocateMemoryHinted(pid, hint, length)
    }

    suspend fun freeMemory(pid: Int, address: Long, length: Int): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return memoryService.freeMemory(pid, address, length)
    }

    suspend fun changeProtection(pid: Int, address: Long, length: Int, prot: Int): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return memoryService.changeProtection(pid, address, length, prot)
    }

    private data class MockInstrSpec(val len: Int, val mnemonicId: Int, val kind: Int, val ripRelTarget: Long, val memDisp: Long)

    suspend fun disassembleRegion(pid: Int, address: Long, length: Int, maxEntries: Int = 1000): List<Ps5DisasmInstr> {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            val list = mutableListOf<Ps5DisasmInstr>()
            val funcStart = (address / 36) * 36
            var addr = address
            val end = address + length
            while (addr < end && list.size < maxEntries) {
                val funcOffset = ((addr - funcStart) % 36).toInt()
                val spec = when (funcOffset) {
                    0 -> MockInstrSpec(1, 669, 0, 0L, 0L)
                    1 -> MockInstrSpec(3, 440, 0, 0L, 0L)
                    4 -> MockInstrSpec(4, 781, 0, 0L, 0L)
                    8 -> MockInstrSpec(7, 440, 0x10, 0L, -4L)
                    15 -> MockInstrSpec(3, 440, 0x10, 0L, -4L)
                    18 -> MockInstrSpec(3, 13, 0, 0L, 0L)
                    21 -> MockInstrSpec(3, 111, 0, 0L, 0L)
                    24 -> MockInstrSpec(2, 0, 0x08, funcStart + 29, 0L)
                    26 -> MockInstrSpec(3, 440, 0x10, 0L, -4L)
                    29 -> MockInstrSpec(5, 315, 0x04, funcStart + 15, 0L)
                    34 -> MockInstrSpec(1, 401, 0, 0L, 0L)
                    35 -> MockInstrSpec(1, 695, 0x02, 0L, 0L)
                    else -> MockInstrSpec(1, 0, 0, 0L, 0L)
                }
                
                list.add(Ps5DisasmInstr(
                    addr = addr,
                    ripRelTarget = spec.ripRelTarget,
                    memDisp = spec.memDisp,
                    length = spec.len,
                    kind = spec.kind,
                    memBaseReg = if (spec.kind == 0x10) 67 else 0,
                    memIndexReg = 0,
                    memScale = 0,
                    mnemonic = spec.mnemonicId,
                    mnemonicLo = 0
                ))
                addr += spec.len
            }
            return list
        }
        return memoryService.disassembleRegion(pid, address, length, maxEntries)
    }

    suspend fun getKernBase(): Long {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return 0xFFFFFFFF80200000UL.toLong()
        return memoryService.getKernBase()
    }

    // Debugger Delegation
    suspend fun attach(pid: Int): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.attach(pid)
    }

    suspend fun detach(): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.detach()
    }

    suspend fun setBreakpoint(index: Int, enabled: Boolean, address: Long): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.setBreakpoint(index, enabled, address)
    }

    suspend fun setWatchpoint(index: Int, enabled: Boolean, length: Int, breakType: Int, address: Long): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.setWatchpoint(index, enabled, length, breakType, address)
    }

    suspend fun stopProcess(): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.stopProcess()
    }

    suspend fun resumeProcess(): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.resumeProcess()
    }

    suspend fun stepProcess(): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.stepProcess()
    }

    suspend fun getThreadList(): List<Int> {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return listOf(10001, 10002)
        return debuggerService.getThreadList()
    }

    suspend fun suspendThread(lwpid: Int): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.suspendThread(lwpid)
    }

    suspend fun resumeThread(lwpid: Int): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.resumeThread(lwpid)
    }

    suspend fun getRegs(lwpid: Int): GpRegs {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) {
            return GpRegs(
                r15=0, r14=0, r13=0, r12=0, r11=0, r10=0, r9=0, r8=0,
                rdi=0x400000L, rsi=0, rbp=0x7FFFFFFFDF00L, rbx=0, rdx=0, rcx=0, rax=5,
                trapno=3, fs=0, gs=0, err=0, es=0, ds=0,
                rip=0x400009L, cs=0, rflags=0x202, rsp=0x7FFFFFFFDEE0L, ss=0
            )
        }
        return debuggerService.getRegs(lwpid)
    }

    suspend fun setRegs(lwpid: Int, regs: GpRegs): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.setRegs(lwpid, regs)
    }

    suspend fun getFpRegs(lwpid: Int): ByteArray {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return ByteArray(832)
        return debuggerService.getFpRegs(lwpid)
    }

    suspend fun setFpRegs(lwpid: Int, fpBytes: ByteArray): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.setFpRegs(lwpid, fpBytes)
    }

    suspend fun getDbRegs(lwpid: Int): DbRegs {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return DbRegs(0,0,0,0,0,0,0,0, LongArray(8))
        return debuggerService.getDbRegs(lwpid)
    }

    suspend fun setDbRegs(lwpid: Int, dbRegs: DbRegs): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.setDbRegs(lwpid, dbRegs)
    }

    suspend fun getFsGsBase(lwpid: Int): Pair<Long, Long> {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return Pair(0L, 0L)
        return debuggerService.getFsGsBase(lwpid)
    }

    suspend fun setFsGsBase(lwpid: Int, fsBase: Long, gsBase: Long): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.setFsGsBase(lwpid, fsBase, gsBase)
    }

    suspend fun stepThread(lwpid: Int): Boolean {
        if (com.osr.ps5debugger.di.AppContainer.debugMockEnabled) return true
        return debuggerService.stepThread(lwpid)
    }
}
