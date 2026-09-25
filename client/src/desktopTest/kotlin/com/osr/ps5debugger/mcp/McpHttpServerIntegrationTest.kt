package com.osr.ps5debugger.mcp

import com.osr.ps5debugger.di.AppContainer
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.*

class McpHttpServerIntegrationTest {

    @BeforeTest
    fun setup() {
        if (!McpHttpServer.isRunning) {
            McpHttpServer.start(port = 8599)
        }
    }

    @AfterTest
    fun tearDown() {
        McpHttpServer.stop()
    }

    @Test
    fun testStatusEndpoint() {
        val url = URL("http://127.0.0.1:8599/api/status")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"server\": \"ps5-debugger-desktop\""))
        assertTrue(body.contains("\"status\": \"ok\""))
    }

    @Test
    fun testNavigateEndpoint() {
        var navigatedAddr = 0L
        var navigatedMode = -1
        AppContainer.onNavigateRequested = { addr, mode ->
            navigatedAddr = addr
            navigatedMode = mode
        }

        val url = URL("http://127.0.0.1:8599/api/navigate")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.outputStream.bufferedWriter().use {
            it.write("""{"address":"0x401000","mode":"graph"}""")
        }

        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"success\":true"))
        assertEquals(0x401000L, navigatedAddr)
        assertEquals(1, navigatedMode) // 1 = graph
    }

    @Test
    fun testSymbolsAndFunctionsEndpoints() {
        AppContainer.symbolNames[0x400500L] = "main"
        AppContainer.discoveredFunctions.add(0x400500L)

        val symConn = URL("http://127.0.0.1:8599/api/symbols").openConnection() as HttpURLConnection
        assertEquals(200, symConn.responseCode)
        val symBody = symConn.inputStream.bufferedReader().readText()
        assertTrue(symBody.contains("main"))

        val funcConn = URL("http://127.0.0.1:8599/api/functions").openConnection() as HttpURLConnection
        assertEquals(200, funcConn.responseCode)
        val funcBody = funcConn.inputStream.bufferedReader().readText()
        assertTrue(funcBody.contains("main"))
    }
}
