package com.osr.ps5debugger.network

import com.osr.ps5debugger.util.DefaultIpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.net.InetSocketAddress
import java.net.Socket

object Ps5PayloadInjector {
    const val DEFAULT_PAYLOAD_PORT = 9021

    /**
     * Attempts to find and load the requested payload bytes from known file paths
     * or from embedded application resources.
     */
    fun loadPayloadBytes(filename: String = "ps5debug.elf", customPath: String? = null): ByteArray {
        if (!customPath.isNullOrBlank()) {
            val customFile = File(customPath)
            if (customFile.exists() && customFile.isFile && customFile.length() > 0) {
                return customFile.readBytes()
            }
        }

        // Search common filesystem candidate paths
        val userDir = try { System.getProperty("user.dir") ?: "" } catch (_: Exception) { "" }
        val searchPaths = listOf(
            File("payloads/$filename"),
            File("../payloads/$filename"),
            File("./payloads/$filename"),
            File("/payloads/$filename"),
            File(userDir, "payloads/$filename"),
            File(userDir, "../payloads/$filename"),
            File(userDir, "client/src/desktopMain/resources/payloads/$filename")
        )

        for (candidate in searchPaths) {
            try {
                if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                    return candidate.readBytes()
                }
            } catch (_: Exception) {}
        }

        // Search classloader resources
        val resourceNames = listOf(
            "/payloads/$filename",
            "payloads/$filename",
            "/$filename",
            "$filename"
        )

        val classLoaders = listOfNotNull(
            Ps5PayloadInjector::class.java.classLoader,
            Thread.currentThread().contextClassLoader
        )

        for (resourceName in resourceNames) {
            for (cl in classLoaders) {
                try {
                    cl.getResourceAsStream(resourceName.removePrefix("/"))?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.isNotEmpty()) return bytes
                    }
                } catch (_: Exception) {}
            }
            try {
                Ps5PayloadInjector::class.java.getResourceAsStream(resourceName)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {}
        }

        throw FileNotFoundException("Payload file not found at /payloads/$filename or in application resources")
    }

    /**
     * Injects the ps5debug payload into the target PS5 console over TCP.
     */
    suspend fun injectPayload(
        ip: String,
        port: Int = DefaultIpHelper.getPayloadPort(),
        timeoutMs: Int = DefaultIpHelper.getConnectionTimeoutMs(),
        payloadBytes: ByteArray? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanIp = ip.trim()
            if (cleanIp.isEmpty()) {
                throw IllegalArgumentException("Target IP address cannot be blank")
            }

            val bytes = payloadBytes ?: loadPayloadBytes("ps5debug.elf")
            if (bytes.isEmpty()) {
                throw IllegalStateException("Payload is empty (0 bytes)")
            }

            val sendBytes = { payloadData: ByteArray ->
                val socket = Socket()
                try {
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(cleanIp, port), timeoutMs)
                    socket.getOutputStream().use { outStream ->
                        val bufferSize = 16384
                        var offset = 0
                        while (offset < payloadData.size) {
                            val chunkLength = minOf(bufferSize, payloadData.size - offset)
                            outStream.write(payloadData, offset, chunkLength)
                            offset += chunkLength
                        }
                        outStream.flush()
                    }
                } finally {
                    try {
                        socket.close()
                    } catch (_: Exception) {}
                }
            }

            sendBytes(bytes)

            // Also try to inject ftp.elf if payloadBytes wasn't customized
            if (payloadBytes == null) {
                try {
                    val ftpBytes = loadPayloadBytes("ftp.elf")
                    if (ftpBytes.isNotEmpty()) {
                        kotlinx.coroutines.delay(1000)
                        sendBytes(ftpBytes)
                    }
                } catch (_: Exception) {
                    // Log or ignore if ftp.elf is not found or fails, don't break main injection
                }
            }

            bytes.size
        }
    }
}
