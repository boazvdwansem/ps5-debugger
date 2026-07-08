package com.osr.ps5debugger.network.services

import com.osr.ps5debugger.network.Ps5Connection
import com.osr.ps5debugger.protocol.*
import java.io.InputStream
import java.io.OutputStream

class DebuggerService(private val connection: Ps5Connection) {

    suspend fun attach(pid: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(pid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_ATTACH, payload)
        val status = connection.receiveStatus(inStr)
        status == ProtocolConstants.CMD_SUCCESS || status == ProtocolConstants.CMD_ALREADY_DEBUG
    }

    suspend fun detach(): Boolean = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_DETACH)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun setBreakpoint(index: Int, enabled: Boolean, address: Long): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(16).apply {
            writeInt(index)
            writeInt(if (enabled) 1 else 0)
            writeLong(address)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SET_BREAKPOINT, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun setWatchpoint(index: Int, enabled: Boolean, length: Int, breakType: Int, address: Long): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(24).apply {
            writeInt(index)
            writeInt(if (enabled) 1 else 0)
            writeInt(length)
            writeInt(breakType)
            writeLong(address)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SET_WATCHPOINT, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun stopProcess(): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(1) }.bytes // 1 = stop
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_CONTINUE, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun resumeProcess(): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(0) }.bytes // 0 = resume
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_CONTINUE, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun stepProcess(): Boolean = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_STEP)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun getThreadList(): List<Int> = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_GET_THREAD_LIST)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute emptyList()
        val numBytes = connection.readExactly(inStr, 4)
        val num = BinaryBuffer(numBytes).readInt()
        val list = mutableListOf<Int>()
        if (num > 0) {
            val listBytes = connection.readExactly(inStr, num * 4)
            val buf = BinaryBuffer(listBytes)
            for (i in 0 until num) {
                list.add(buf.readInt())
            }
        }
        list
    }

    suspend fun suspendThread(lwpid: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SUSPEND_THREAD, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun resumeThread(lwpid: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_RESUME_THREAD, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun getRegs(lwpid: Int): GpRegs = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_GETREGS, payload)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Get regs failed: status 0x${status.toString(16)}")
        val bytes = connection.readExactly(inStr, 176)
        GpRegs.parse(BinaryBuffer(bytes))
    }

    suspend fun setRegs(lwpid: Int, regs: GpRegs): Boolean = connection.execute { inStr, outStr ->
        val regBytes = BinaryBuffer(176).apply {
            writeLong(regs.r15)
            writeLong(regs.r14)
            writeLong(regs.r13)
            writeLong(regs.r12)
            writeLong(regs.r11)
            writeLong(regs.r10)
            writeLong(regs.r9)
            writeLong(regs.r8)
            writeLong(regs.rdi)
            writeLong(regs.rsi)
            writeLong(regs.rbp)
            writeLong(regs.rbx)
            writeLong(regs.rdx)
            writeLong(regs.rcx)
            writeLong(regs.rax)
            writeInt(regs.trapno)
            writeShort(regs.fs.toShort())
            writeShort(regs.gs.toShort())
            writeInt(regs.err)
            writeShort(regs.es.toShort())
            writeShort(regs.ds.toShort())
            writeLong(regs.rip)
            writeLong(regs.cs)
            writeLong(regs.rflags)
            writeLong(regs.rsp)
            writeLong(regs.ss)
        }.bytes
        
        val payload = BinaryBuffer(8).apply {
            writeInt(lwpid)
            writeInt(176)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SETREGS, payload)
        if (connection.receiveStatus(inStr) != ProtocolConstants.CMD_SUCCESS) return@execute false
        outStr.write(regBytes)
        outStr.flush()
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun getFpRegs(lwpid: Int): ByteArray = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_GETFPREGS, payload)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Get FP regs failed")
        connection.readExactly(inStr, 832)
    }

    suspend fun setFpRegs(lwpid: Int, fpBytes: ByteArray): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(8).apply {
            writeInt(lwpid)
            writeInt(fpBytes.size)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SETFPREGS, payload)
        if (connection.receiveStatus(inStr) != ProtocolConstants.CMD_SUCCESS) return@execute false
        outStr.write(fpBytes)
        outStr.flush()
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun getDbRegs(lwpid: Int): DbRegs = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_GETDBREGS, payload)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Get DB regs failed")
        val bytes = connection.readExactly(inStr, 128)
        DbRegs.parse(BinaryBuffer(bytes))
    }

    suspend fun setDbRegs(lwpid: Int, dbRegs: DbRegs): Boolean = connection.execute { inStr, outStr ->
        val regBytes = BinaryBuffer(128).apply {
            writeLong(dbRegs.dr0)
            writeLong(dbRegs.dr1)
            writeLong(dbRegs.dr2)
            writeLong(dbRegs.dr3)
            writeLong(dbRegs.dr4)
            writeLong(dbRegs.dr5)
            writeLong(dbRegs.dr6)
            writeLong(dbRegs.dr7)
            for (r in dbRegs.reserved) {
                writeLong(r)
            }
        }.bytes
        val payload = BinaryBuffer(8).apply {
            writeInt(lwpid)
            writeInt(128)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SETDBREGS, payload)
        if (connection.receiveStatus(inStr) != ProtocolConstants.CMD_SUCCESS) return@execute false
        outStr.write(regBytes)
        outStr.flush()
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun getFsGsBase(lwpid: Int): Pair<Long, Long> = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_GETFSGSBASE, payload)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Get FS/GS base failed")
        val bytes = connection.readExactly(inStr, 16)
        val buf = BinaryBuffer(bytes)
        Pair(buf.readLong(), buf.readLong())
    }

    suspend fun setFsGsBase(lwpid: Int, fsBase: Long, gsBase: Long): Boolean = connection.execute { inStr, outStr ->
        val baseBytes = BinaryBuffer(16).apply {
            writeLong(fsBase)
            writeLong(gsBase)
        }.bytes
        val payload = BinaryBuffer(8).apply {
            writeInt(lwpid)
            writeInt(16)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_SETFSGSBASE, payload)
        if (connection.receiveStatus(inStr) != ProtocolConstants.CMD_SUCCESS) return@execute false
        outStr.write(baseBytes)
        outStr.flush()
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun stepThread(lwpid: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(lwpid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_STEP_THREAD, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }
}
