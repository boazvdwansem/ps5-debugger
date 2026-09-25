package com.osr.ps5debugger.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PayloadInjectorTest {

    @Test
    fun testLoadPayloadBytes() {
        val testFile = java.io.File("build/tmp/test_ps5debug.elf").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 2, 3))
        }
        val bytes = try {
            Ps5PayloadInjector.loadPayloadBytes()
        } catch (_: java.io.FileNotFoundException) {
            Ps5PayloadInjector.loadPayloadBytes(customPath = testFile.absolutePath)
        }
        assertTrue(bytes.isNotEmpty(), "Payload bytes should not be empty")
        // Check ELF magic header: 0x7F 'E' 'L' 'F'
        assertEquals(0x7F.toByte(), bytes[0])
        assertEquals('E'.code.toByte(), bytes[1])
        assertEquals('L'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
    }

    @Test
    fun testInjectPayloadSuccess() = runBlocking {
        val testPayload = "Hello PS5 Debugger Payload".toByteArray(Charsets.UTF_8)
        val serverSocket = ServerSocket(0)
        val serverPort = serverSocket.localPort

        val receivedStream = ByteArrayOutputStream()

        val serverJob = launch(Dispatchers.IO) {
            serverSocket.use { server ->
                val client = server.accept()
                client.use { s ->
                    val input = s.getInputStream()
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        receivedStream.write(buffer, 0, read)
                    }
                }
            }
        }

        val result = Ps5PayloadInjector.injectPayload(
            ip = "127.0.0.1",
            port = serverPort,
            timeoutMs = 3000,
            payloadBytes = testPayload
        )

        serverJob.join()

        assertTrue(result.isSuccess, "Injection should succeed: ${result.exceptionOrNull()}")
        assertEquals(testPayload.size, result.getOrNull())
        assertEquals(String(testPayload), String(receivedStream.toByteArray()))
    }

    @Test
    fun testInjectPayloadBlankIpFails() = runBlocking {
        val result = Ps5PayloadInjector.injectPayload(
            ip = "   ",
            port = 9021,
            timeoutMs = 1000,
            payloadBytes = byteArrayOf(1, 2, 3)
        )
        assertTrue(result.isFailure, "Injection with blank IP should fail")
    }

    @Test
    fun testInjectPayloadConnectionRefusedFails() = runBlocking {
        // Connect to a port where nothing is listening
        val dummySocket = ServerSocket(0)
        val unusedPort = dummySocket.localPort
        dummySocket.close()

        val result = Ps5PayloadInjector.injectPayload(
            ip = "127.0.0.1",
            port = unusedPort,
            timeoutMs = 1000,
            payloadBytes = byteArrayOf(1, 2, 3)
        )
        assertTrue(result.isFailure, "Injection to closed port should fail")
    }
}
