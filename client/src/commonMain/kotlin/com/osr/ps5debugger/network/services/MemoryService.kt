package com.osr.ps5debugger.network.services

import com.osr.ps5debugger.network.Ps5Connection
import com.osr.ps5debugger.protocol.*
import java.io.InputStream
import java.io.OutputStream

class MemoryService(private val connection: Ps5Connection) {

    suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(16).apply {
            writeInt(pid)
            writeLong(address)
            writeInt(length)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_READ, payload)
        
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Read memory command failed")

        connection.readExactly(inStr, length)
    }

    suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(16).apply {
            writeInt(pid)
            writeLong(address)
            writeInt(data.size)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_WRITE, payload)
        
        var status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute false

        outStr.write(data)
        outStr.flush()

        status = connection.receiveStatus(inStr)
        status == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean = false): Boolean = connection.execute { inStr, outStr ->
        if (writes.isEmpty()) return@execute true
        
        val flags = if (withStatusReport) ProtocolConstants.PROC_WRITE_MULTI_F_STATUS else 0
        val headerPayload = BinaryBuffer(12).apply {
            writeInt(pid)
            writeInt(writes.size)
            writeInt(flags)
        }.bytes
        
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_WRITE_MULTI, headerPayload)
        
        var status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute false

        // Stream entries
        for ((address, data) in writes) {
            val entryHeader = BinaryBuffer(12).apply {
                writeLong(address)
                writeInt(data.size)
            }.bytes
            outStr.write(entryHeader)
            outStr.write(data)
        }
        outStr.flush()

        if (withStatusReport) {
            // Read status bytes
            connection.readExactly(inStr, writes.size)
        }

        status = connection.receiveStatus(inStr)
        status == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun allocateMemory(pid: Int, length: Int): Long = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(8).apply {
            writeInt(pid)
            writeInt(length)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_ALLOC, payload)
        
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute 0L

        val respBytes = connection.readExactly(inStr, 8)
        BinaryBuffer(respBytes).readLong()
    }

    suspend fun allocateMemoryHinted(pid: Int, hint: Long, length: Int): Long = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(16).apply {
            writeInt(pid)
            writeLong(hint)
            writeInt(length)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_ALLOC_HINTED, payload)
        
        val status = connection.receiveStatus(inStr)
        val respBytes = connection.readExactly(inStr, 8)
        val address = BinaryBuffer(respBytes).readLong()
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute 0L
        address
    }

    suspend fun freeMemory(pid: Int, address: Long, length: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(16).apply {
            writeInt(pid)
            writeLong(address)
            writeInt(length)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_FREE, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun changeProtection(pid: Int, address: Long, length: Int, prot: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(20).apply {
            writeInt(pid)
            writeLong(address)
            writeInt(length)
            writeInt(prot)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_PROTECT, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun disassembleRegion(pid: Int, address: Long, length: Int, maxEntries: Int = 1000): List<Ps5DisasmInstr> = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(20).apply {
            writeInt(pid)
            writeLong(address)
            writeInt(length)
            writeInt(maxEntries)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_DISASM_REGION, payload)
        
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Disassemble region command failed: status 0x${status.toString(16)}")

        val list = mutableListOf<Ps5DisasmInstr>()
        while (true) {
            val bufBytes = connection.readExactly(inStr, 32)
            // Check sentinel (all bytes 0xFF)
            var isSentinel = true
            for (b in bufBytes) {
                if (b != 0xFF.toByte()) {
                    isSentinel = false
                    break
                }
            }
            if (isSentinel) break

            val buf = BinaryBuffer(bufBytes)
            val addr = buf.readLong()
            val ripRelTarget = buf.readLong()
            val memDisp = buf.readLong()
            val len = buf.readUByte()
            val kind = buf.readUByte()
            val memBaseReg = buf.readUByte()
            val memIndexReg = buf.readUByte()
            val memScale = buf.readUByte()
            val mnemonicLo = buf.readUByte()
            // Skip 2 bytes pad
            buf.readShort()

            list.add(Ps5DisasmInstr(
                addr = addr,
                ripRelTarget = ripRelTarget,
                memDisp = memDisp,
                length = len,
                kind = kind,
                memBaseReg = memBaseReg,
                memIndexReg = memIndexReg,
                memScale = memScale,
                mnemonicLo = mnemonicLo
            ))
        }
        list
    }

    suspend fun getKernBase(): Long = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_KERN_BASE)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute 0L
        val bytes = connection.readExactly(inStr, 8)
        BinaryBuffer(bytes).readLong()
    }
}
