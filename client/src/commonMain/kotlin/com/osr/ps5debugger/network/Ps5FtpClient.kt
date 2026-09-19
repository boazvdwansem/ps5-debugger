package com.osr.ps5debugger.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*

class Ps5FtpClient(private val ip: String, private val port: Int = 2121) {

    data class FtpFile(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val permissions: String,
        val raw: String
    )

    private var controlSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val socketMutex = Mutex()

    private suspend fun connect() = withContext(Dispatchers.IO) {
        if (controlSocket?.isConnected == true && controlSocket?.isClosed == false) {
            try {
                sendCommand("NOOP")
                val resp = readResponse()
                if (resp.startsWith("200")) return@withContext
            } catch (_: Exception) {
                disconnect()
            }
        }
        
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").split(":")[0]
        controlSocket = Socket()
        controlSocket!!.connect(InetSocketAddress(cleanIp, port), 10000)
        controlSocket!!.soTimeout = 15000
        reader = controlSocket!!.getInputStream().bufferedReader()
        writer = controlSocket!!.getOutputStream().bufferedWriter()
        
        readResponse() // Wait for greeting
        
        sendCommand("USER anonymous")
        readResponse()
        sendCommand("PASS anonymous")
        readResponse()
        
        sendCommand("TYPE I")
        readResponse()
    }

    private fun sendCommand(cmd: String) {
        writer?.write("$cmd\r\n")
        writer?.flush()
    }

    private fun readResponse(): String {
        var line = reader?.readLine() ?: throw IOException("FTP control connection closed unexpectedly")
        if (line.length > 3 && line[3] == '-') {
            val code = line.substring(0, 3)
            while (true) {
                val nextLine = reader?.readLine() ?: break
                if (nextLine.startsWith("$code ")) {
                    line = nextLine
                    break
                }
            }
        }
        return line
    }

    private suspend fun openDataConnection(): Socket = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").split(":")[0]
        
        sendCommand("PASV")
        val response = readResponse()
        
        if (response.startsWith("227")) {
            val regex = Regex("""\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)""")
            val match = regex.find(response)
            if (match != null) {
                val (_, _, _, _, p1, p2) = match.destructured
                val dataPort = p1.toInt() * 256 + p2.toInt()
                
                val socket = Socket()
                socket.connect(InetSocketAddress(cleanIp, dataPort), 5000)
                return@withContext socket
            }
        }
        throw IOException("Failed to enter passive mode: $response")
    }

    suspend fun listFiles(path: String): List<FtpFile> = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            sendCommand("CWD $path")
            readResponse()
            
            val dataSocket = openDataConnection()
            sendCommand("LIST")
            readResponse() // 150
            
            val files = mutableListOf<FtpFile>()
            dataSocket.getInputStream().bufferedReader().use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    line?.let { 
                        val parts = it.split(Regex("\\s+"))
                        if (parts.size >= 9) {
                            val perms = parts[0]
                            val isDir = perms.startsWith("d")
                            val size = parts[4].toLongOrNull() ?: 0L
                            val name = parts.drop(8).joinToString(" ")
                            if (name != "." && name != "..") {
                                files.add(FtpFile(name, isDir, size, perms, it))
                            }
                        }
                    }
                }
            }
            dataSocket.close()
            try { readResponse() } catch (_: Exception) {} // 226
            files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    suspend fun downloadFile(remotePath: String, outputStream: OutputStream) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            val dataSocket = openDataConnection()
            sendCommand("RETR $remotePath")
            
            val status = readResponse()
            if (status.startsWith("550") || status.startsWith("553") || status.startsWith("450")) {
                dataSocket.close()
                throw FileNotFoundException("FTP Error: $status for path $remotePath")
            }
            
            if (!status.startsWith("125") && !status.startsWith("150")) {
                dataSocket.close()
                throw IOException("Unexpected FTP response: $status")
            }
            
            dataSocket.getInputStream().use { it.copyTo(outputStream) }
            dataSocket.close()
            
            try { readResponse() } catch (_: Exception) {} // 226
        }
    }

    suspend fun uploadFile(remotePath: String, inputStream: InputStream) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            val dataSocket = openDataConnection()
            sendCommand("STOR $remotePath")
            readResponse()
            dataSocket.getOutputStream().use { inputStream.copyTo(it) }
            dataSocket.close()
            readResponse()
        }
    }

    suspend fun deleteFile(path: String) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            sendCommand("DELE $path")
            readResponse()
        }
    }

    suspend fun deleteDirectory(path: String) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            sendCommand("RMD $path")
            readResponse()
        }
    }

    suspend fun rename(oldPath: String, newPath: String) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            sendCommand("RNFR $oldPath")
            readResponse()
            sendCommand("RNTO $newPath")
            readResponse()
        }
    }

    suspend fun chmod(path: String, permissions: String) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            sendCommand("SITE CHMOD $permissions $path")
            readResponse()
        }
    }

    suspend fun createDirectory(path: String) = socketMutex.withLock {
        withContext(Dispatchers.IO) {
            connect()
            sendCommand("MKD $path")
            readResponse()
        }
    }

    fun disconnect() {
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        reader = null
        writer = null
    }
}
