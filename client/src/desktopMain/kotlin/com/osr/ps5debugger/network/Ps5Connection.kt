package com.osr.ps5debugger.network

import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.ProtocolConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class Ps5Connection {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val mutex = Mutex()
    
    var ipAddress: String? = null
        private set
    
    @Volatile
    var isConnected: Boolean = false
        private set

    suspend fun connect(ip: String, port: Int = 744, timeoutMs: Int = 5000): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isConnected) return@withLock true
            try {
                val newSocket = Socket()
                newSocket.tcpNoDelay = true
                newSocket.keepAlive = true
                newSocket.soTimeout = timeoutMs
                newSocket.connect(InetSocketAddress(ip, port), timeoutMs)
                
                socket = newSocket
                inputStream = newSocket.getInputStream()
                outputStream = newSocket.getOutputStream()
                ipAddress = ip
                isConnected = true
                true
            } catch (e: Exception) {
                cleanup()
                false
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cleanup()
        }
    }

    private fun cleanup() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        isConnected = false
    }

    /**
     * Executes a command block safely while holding the lock.
     */
    suspend fun <T> execute(block: suspend (InputStream, OutputStream) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val inStr = inputStream ?: throw IllegalStateException("Not connected")
            val outStr = outputStream ?: throw IllegalStateException("Not connected")
            try {
                block(inStr, outStr)
            } catch (e: Exception) {
                cleanup()
                throw e
            }
        }
    }

    /**
     * Send packet header (12 bytes) + data payload
     */
    fun sendPacket(out: OutputStream, cmd: Int, data: ByteArray = ByteArray(0)) {
        val buffer = BinaryBuffer(12 + data.size)
        buffer.writeInt(ProtocolConstants.PACKET_MAGIC)
        buffer.writeInt(cmd)
        buffer.writeInt(data.size)
        if (data.isNotEmpty()) {
            buffer.writeBytes(data)
        }
        out.write(buffer.bytes)
        out.flush()
    }

    /**
     * Reads exactly [length] bytes from the input stream.
     */
    fun readExactly(inStream: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = inStream.read(data, offset, length - offset)
            if (read == -1) {
                throw java.io.IOException("Socket closed before reading $length bytes (read $offset)")
            }
            offset += read
        }
        return data
    }

    /**
     * Receives and un-swaps a status word
     */
    fun receiveStatus(inStream: InputStream): Int {
        val bytes = readExactly(inStream, 4)
        val rawStatus = BinaryBuffer(bytes).readInt()
        return ProtocolConstants.bitswap32(rawStatus)
    }
}
