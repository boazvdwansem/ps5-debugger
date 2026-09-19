package com.osr.ps5debugger.network.services

import com.osr.ps5debugger.network.Ps5Connection
import com.osr.ps5debugger.protocol.*
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ProcessService(private val connection: Ps5Connection) {
    private val pullMutex = kotlinx.coroutines.sync.Mutex()

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

    suspend fun pullFile(path: String): ByteArray? = pullMutex.withLock {
        if (com.osr.ps5debugger.di.AppContainer.unsupportedCommands.contains(ProtocolConstants.CMD_CONSOLE_PULL_FILE)) return null
        
        return try {
            connection.execute(readTimeoutMs = 4000) { inStr, outStr ->
                val pathBytes = path.toByteArray(Charsets.UTF_8)
                val payload = BinaryBuffer(pathBytes.size + 1).apply {
                    writeBytes(pathBytes)
                    writeByte(0) // NULL terminator
                }.bytes
                
                connection.sendPacket(outStr, ProtocolConstants.CMD_CONSOLE_PULL_FILE, payload)
                
                val status = try { 
                    connection.receiveStatus(inStr) 
                } catch (e: Exception) {
                    // Fatal error during pull, likely command unsupported
                    com.osr.ps5debugger.di.AppContainer.unsupportedCommands.add(ProtocolConstants.CMD_CONSOLE_PULL_FILE)
                    throw e
                }

                if (status != ProtocolConstants.CMD_SUCCESS) {
                    return@execute null
                }
                
                val sizeBytes = connection.readExactly(inStr, 8)
                val size = BinaryBuffer(sizeBytes).readLong()
                
                if (size < 0 || size > 20 * 1024 * 1024) return@execute null
                if (size == 0L) return@execute ByteArray(0)
                
                connection.readExactly(inStr, size.toInt())
            }
        } catch (e: Exception) {
            // If it timed out or socket broke, don't try again this session
            if (e !is kotlinx.coroutines.CancellationException) {
                com.osr.ps5debugger.di.AppContainer.unsupportedCommands.add(ProtocolConstants.CMD_CONSOLE_PULL_FILE)
            }
            null
        }
    }

    suspend fun uploadElfRpc(pid: Int, elfBytes: ByteArray): Long? = connection.execute { inStr, outStr ->
        // Build header: pid (4) + length (4)
        val payload = BinaryBuffer(8).apply {
            writeInt(pid)
            writeInt(elfBytes.size)
        }.bytes
        connection.sendPacket(outStr, ProtocolConstants.CMD_PROC_ELF_RPC, payload)

        // Server acknowledges and then expects the ELF bytes
        val ready = connection.receiveStatus(inStr)
        if (ready != ProtocolConstants.CMD_SUCCESS) return@execute null

        // send ELF bytes
        outStr.write(elfBytes)
        outStr.flush()

        // read response status + entry + optional JSON symbol payload
        val status = connection.receiveStatus(inStr)
        if (status != ProtocolConstants.CMD_SUCCESS) return@execute null

        val respBytes = connection.readExactly(inStr, 8)
        val entry = BinaryBuffer(respBytes).readLong()
        if (entry != 0L) {
            com.osr.ps5debugger.di.AppContainer.elfEntryPoint = entry
        }

        // read trailing JSON length (u32) then payload
        val lenBytes = connection.readExactly(inStr, 4)
        val jsonLen = BinaryBuffer(lenBytes).readInt()
        if (jsonLen > 0) {
            try {
                // safety caps
                val MAX_JSON = 5 * 1024 * 1024 // 5 MB
                val MAX_SYMBOLS = 20000
                if (jsonLen.toLong() > MAX_JSON) throw IllegalArgumentException("JSON payload too large")
                val jsonBytes = connection.readExactly(inStr, jsonLen)
                val jsonStr = String(jsonBytes, Charsets.UTF_8)

                @Serializable
                data class SymbolDTO(val addr: Long, val size: Long = 0L, val type: Int = 0, val name: String = "")

                val symbols: List<SymbolDTO> = Json.decodeFromString(jsonStr)
                var count = 0
                for (s in symbols) {
                    if (s.name.isEmpty()) continue
                    com.osr.ps5debugger.di.AppContainer.renameSymbol(s.addr, s.name)
                    if ((s.type and 0xF) == 2) {
                        if (!com.osr.ps5debugger.di.AppContainer.discoveredFunctions.contains(s.addr)) {
                            com.osr.ps5debugger.di.AppContainer.discoveredFunctions.add(s.addr)
                        }
                    }
                    count++
                    if (count >= MAX_SYMBOLS) break
                }
            } catch (e: Exception) {
                // on parse failure, ignore and continue
            }
        }

        entry
    }
}
