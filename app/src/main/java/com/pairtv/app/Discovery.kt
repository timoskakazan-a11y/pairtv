package com.pairtv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class DiscoveredPc(
    val name: String,
    val ip: String,
    val httpPort: Int,
    val videoCount: Int,
)

private const val DISCOVERY_PORT = 47123
private const val DISCOVERY_MAGIC = "PAIRTV_DISCOVER"
private const val SCAN_TIMEOUT_MS = 3000

object Discovery {

    /**
     * Рассылает широковещательный UDP-запрос и собирает ответы от ПК
     * в течение SCAN_TIMEOUT_MS миллисекунд.
     */
    suspend fun scan(): List<DiscoveredPc> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredPc>()
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = SCAN_TIMEOUT_MS

            val requestBytes = DISCOVERY_MAGIC.toByteArray()
            for (broadcastAddr in broadcastAddresses()) {
                try {
                    val packet = DatagramPacket(requestBytes, requestBytes.size, broadcastAddr, DISCOVERY_PORT)
                    socket.send(packet)
                } catch (_ : Exception) {
                    // игнорируем недоступные интерфейсы
                }
            }

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + SCAN_TIMEOUT_MS
            val seen = mutableSetOf<String>()
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) break
                socket.soTimeout = remaining
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                val text = String(packet.data, 0, packet.length)
                val ip = packet.address.hostAddress ?: continue
                if (ip in seen) continue
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "PAIRTV_HELLO") {
                        seen.add(ip)
                        results.add(
                            DiscoveredPc(
                                name = json.optString("name", ip),
                                ip = ip,
                                httpPort = json.optInt("http_port", 8765),
                                videoCount = json.optInt("video_count", 0),
                            )
                        )
                    }
                } catch (_: Exception) {
                    // не JSON — игнорируем
                }
            }
        } finally {
            socket.close()
        }
        results
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val broadcast = ifaceAddr.broadcast ?: continue
                    addresses.add(broadcast)
                }
            }
        } catch (_: Exception) {
            // fallback ниже
        }
        if (addresses.isEmpty()) {
            addresses.add(InetAddress.getByName("255.255.255.255"))
        }
        return addresses
    }
}
