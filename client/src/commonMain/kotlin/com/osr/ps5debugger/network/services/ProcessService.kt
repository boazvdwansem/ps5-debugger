package com.osr.ps5debugger.network.services

import com.osr.ps5debugger.network.Ps5Connection
import com.osr.ps5debugger.protocol.*
import java.io.InputStream
import java.io.OutputStream

class ProcessService(private val connection: Ps5Connection) {

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

    suspend fun getForegroundApp(): Ps5ForegroundApp = connection.execute { inStr, outStr ->
        connection.sendPacket(outStr, ProtocolConstants.CMD_CONSOLE_FOREGROUND_APP)
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) throw java.io.IOException("Foreground app query failed")
        
        val data = connection.readExactly(inStr, 140)
        val buf = BinaryBuffer(data)
        val pid = buf.readInt()
        val titleId = buf.readString(16)
        val contentId = buf.readString(64)
        val name = buf.readString(40)
        val appVer = buf.readString(16)
        Ps5ForegroundApp(pid, titleId, contentId, name, appVer)
    }
}
