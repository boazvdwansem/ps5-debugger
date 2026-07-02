package com.osr.ps5debugger.network

import com.osr.ps5debugger.protocol.*
import java.io.InputStream
import java.io.OutputStream

class Ps5Client(val connection: Ps5Connection) {

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
        // Format decimal ver, e.g. 900 -> 9.00, 1240 -> 12.40
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
        
        // Find NUL separator
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

    suspend fun getProcesses(): List<Ps5Process> = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_LIST)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute emptyList()
        
        val countBytes = connection.readExactly(inStr, 4)
        val count = BinaryBuffer(countBytes).readInt()
        val data = connection.readExactly(inStr, count * 36)
        
        val processes = mutableListOf<Ps5Process>()
        val buf = BinaryBuffer(data)
        for (i in 0 until count) {
            val name = buf.readString(32)
            val pid = buf.readInt()
            processes.add(Ps5Process(name, pid))
        }
        processes
    }

    suspend fun getMaps(pid: Int): List<Ps5VmMapEntry> = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(pid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_MAPS, payload)
        
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute emptyList()

        val countBytes = connection.readExactly(inStr, 4)
        val count = BinaryBuffer(countBytes).readInt()
        val data = connection.readExactly(inStr, count * 58)

        val maps = mutableListOf<Ps5VmMapEntry>()
        val buf = BinaryBuffer(data)
        for (i in 0 until count) {
            val name = buf.readString(32)
            val start = buf.readLong()
            val end = buf.readLong()
            val offset = buf.readLong()
            val prot = buf.readUShort()
            maps.add(Ps5VmMapEntry(name, start, end, offset, prot))
        }
        maps
    }

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

    suspend fun getProcessInfo(pid: Int): Ps5ProcessInfo = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(pid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_INFO, payload)
        
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Get process info failed")

        val data = connection.readExactly(inStr, 188)
        val buf = BinaryBuffer(data)
        val rPid = buf.readInt()
        val name = buf.readString(40)
        val path = buf.readString(64)
        val titleId = buf.readString(16)
        val contentId = buf.readString(64)
        Ps5ProcessInfo(rPid, name, path, titleId, contentId)
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

    suspend fun getForegroundApp(): Ps5ForegroundApp = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_CONSOLE_FOREGROUND_APP)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Foreground app query failed")
        
        val data = connection.readExactly(inStr, 140)
        val buf = BinaryBuffer(data)
        val pid = buf.readInt()
        val titleid = buf.readString(16)
        val contentid = buf.readString(64)
        val name = buf.readString(40)
        val appVer = buf.readString(16)
        Ps5ForegroundApp(pid, titleid, contentid, name, appVer)
    }

    suspend fun attach(pid: Int): Boolean = connection.execute { inStr, outStr ->
        val payload = BinaryBuffer(4).apply { writeInt(pid) }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_ATTACH, payload)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
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
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_STOP)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun resumeProcess(): Boolean = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_RESUME)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
    }

    suspend fun stepProcess(): Boolean = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_DEBUG_STEP)
        connection.receiveStatus(inStr) == ProtocolConstants.CMD_SUCCESS
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

    suspend fun getKernBase(): Long = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_KERN_BASE)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute 0L
        val bytes = connection.readExactly(inStr, 8)
        BinaryBuffer(bytes).readLong()
    }
}
