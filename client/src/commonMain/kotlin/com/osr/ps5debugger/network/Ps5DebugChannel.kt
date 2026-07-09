package com.osr.ps5debugger.network

import com.osr.ps5debugger.protocol.Ps5DebugEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

class Ps5DebugChannel(private val scope: CoroutineScope) {
    private var serverSocket: ServerSocket? = null
    
    private val _events = MutableSharedFlow<Ps5DebugEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<Ps5DebugEvent> = _events.asSharedFlow()

    fun triggerMockEvent(event: Ps5DebugEvent) {
        _events.tryEmit(event)
    }

    @Volatile
    var isListening: Boolean = false
        private set

    fun start(port: Int = 755) {
        if (isListening) return
        isListening = true
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                while (isActive && !serverSocket!!.isClosed) {
                    val clientSocket = serverSocket!!.accept()
                    scope.launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                // Server socket closed or error
            } finally {
                isListening = false
            }
        }
    }

    fun stop() {
        isListening = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val inStream = socket.getInputStream()
            val buffer = ByteArray(1184)
            while (socket.isConnected && !socket.isClosed) {
                // Read exactly 1184 bytes
                var offset = 0
                while (offset < 1184) {
                    val read = inStream.read(buffer, offset, 1184 - offset)
                    if (read == -1) {
                        return // Client closed connection
                    }
                    offset += read
                }
                
                // Parse and emit
                val event = Ps5DebugEvent.parse(buffer)
                _events.tryEmit(event)
            }
        } catch (e: Exception) {
            // Connection error
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
