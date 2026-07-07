package com.osr.ps5debugger.network.services

import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.ProtocolConstants
import com.osr.ps5debugger.network.Ps5Connection

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
        val payload = BinaryBuffer(12).apply {
            writeInt(pid)
            writeInt(writes.size)
            writeInt(if (withStatusReport) 1 else 0)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_WRITE_MULTI, payload)

        val initialStatus = connection.receiveStatus(inStr)
        if (initialStatus != ProtocolConstants.CMD_SUCCESS) return@execute false

        for (write in writes) {
            val hdr = BinaryBuffer(12).apply {
                writeLong(write.first)
                writeInt(write.second.size)
            }.bytes
            outStr.write(hdr)
            outStr.write(write.second)
        }
        outStr.flush()

        if (withStatusReport) {
            connection.readExactly(inStr, writes.size) // consume status bytes
        }

        val finalStatus = connection.receiveStatus(inStr)
        finalStatus == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun allocateMemory(pid: Int, length: Int): Long = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(8).apply {
            writeInt(pid)
            writeInt(length)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_ALLOC, payload)

        val status = connection.receiveStatus(inStr)
        val respBytes = connection.readExactly(inStr, 8)
        val address = BinaryBuffer(respBytes).readLong()
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute 0L
        address
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
            val mnemonic16 = buf.readUShort()

            list.add(Ps5DisasmInstr(
                addr = addr,
                ripRelTarget = ripRelTarget,
                memDisp = memDisp,
                length = len,
                kind = kind,
                memBaseReg = memBaseReg,
                memIndexReg = memIndexReg,
                memScale = memScale,
                mnemonic = mnemonic16,
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
