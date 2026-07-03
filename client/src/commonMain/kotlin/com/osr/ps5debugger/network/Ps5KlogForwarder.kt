package com.osr.ps5debugger.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class Ps5KlogForwarder(private val scope: CoroutineScope) {
    private var socket: Socket? = null
    
    private val _logLines = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(ip: String, port: Int = 3232) {
        if (isRunning) return
        isRunning = true
        scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    val clientSocket = Socket(ip, port)
                    socket = clientSocket
                    val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
                    while (isActive && !clientSocket.isClosed) {
                        val line = reader.readLine() ?: break
                        _logLines.tryEmit(line)
                    }
                } catch (e: Exception) {
                    // Disconnected or connection error, wait before retry
                    kotlinx.coroutines.delay(2000)
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
