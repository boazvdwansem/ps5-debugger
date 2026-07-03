package com.osr.ps5debugger.network

import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.ProtocolConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface

object Ps5Discovery {

    suspend fun discoverConsoles(timeoutMs: Int = 1500): List<String> = withContext(Dispatchers.IO) {
        val discoveredIps = mutableSetOf<String>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            // Package magic into buffer
            val payload = BinaryBuffer(4).apply { writeInt(ProtocolConstants.BROADCAST_MAGIC) }.bytes

            // Broadcast to 255.255.255.255
            try {
                val pack = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 1010)
                socket.send(pack)
            } catch (_: Exception) {}

            // Also broadcast to all subnet interfaces
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        val broadcast = interfaceAddress.broadcast ?: continue
                        val pack = DatagramPacket(payload, payload.size, broadcast, 1010)
                        socket.send(pack)
                    }
                }
            } catch (_: Exception) {}

            // Listen for echoes
            val rxBuffer = ByteArray(4)
            val pack = DatagramPacket(rxBuffer, rxBuffer.size)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(pack)
                    val incomingMagic = BinaryBuffer(pack.data).readInt()
                    if (incomingMagic == ProtocolConstants.BROADCAST_MAGIC) {
                        val hostAddress = pack.address?.hostAddress
                        if (hostAddress != null) {
                            discoveredIps.add(hostAddress)
                        }
                    }
                } catch (_: java.io.InterruptedIOException) {
                    break // Timeout reached
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
        }
        discoveredIps.toList()
    }
}
