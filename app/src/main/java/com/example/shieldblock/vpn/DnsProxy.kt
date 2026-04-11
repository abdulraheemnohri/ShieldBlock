package com.example.shieldblock.vpn

import android.net.VpnService
import androidx.preference.PreferenceManager
import android.util.Log
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.analytics.EventLogger
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsProxy(
    private val vpnFd: FileDescriptor,
    private val service: VpnService
) : Runnable {
    private val blacklist = mutableSetOf<String>()
    private val whitelist = mutableSetOf<String>()
    private val statsManager = StatsManager(service)
    private val eventLogger = EventLogger(service)
    private var customDnsServer: String = "8.8.8.8"

    fun updateBlacklist(newList: Collection<String>) {
        synchronized(blacklist) {
            blacklist.clear()
            blacklist.addAll(newList)
        }
    }

    fun updateWhitelist(newList: Set<String>) {
        synchronized(whitelist) {
            whitelist.clear()
            whitelist.addAll(newList)
        }
    }

    override fun run() {
        var socket: DatagramSocket? = null
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(service)
            customDnsServer = prefs.getString("custom_dns", "8.8.8.8") ?: "8.8.8.8"

            socket = DatagramSocket()
            service.protect(socket)
            socket.soTimeout = 5000

            val inputStream = FileInputStream(vpnFd)
            val outputStream = FileOutputStream(vpnFd)
            val buffer = ByteBuffer.allocate(32768)

            while (!Thread.interrupted()) {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)
                    handlePacket(buffer, socket, outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("DnsProxy", "Fatal error: ${e.message}")
            eventLogger.logEvent("VPN Fatal: ${e.message}")
        } finally {
            socket?.close()
        }
    }

    private fun handlePacket(
        buffer: ByteBuffer,
        socket: DatagramSocket,
        outputStream: FileOutputStream
    ) {
        if (buffer.limit() < 28) return // Too small for IP+UDP

        // Simple IPv4 Check (Version = 4, Protocol = 17 for UDP)
        val version = (buffer.get(0).toInt() shr 4) and 0x0F
        val protocol = buffer.get(9).toInt() and 0xFF
        if (version != 4 || protocol != 17) return

        val sourceIP = ByteArray(4)
        val destIP = ByteArray(4)
        buffer.position(12)
        buffer.get(sourceIP)
        buffer.get(destIP)

        val sourcePort = buffer.getShort(20).toInt() and 0xFFFF
        val destPort = buffer.getShort(22).toInt() and 0xFFFF

        if (destPort != 53) return // Only handle DNS

        val dnsDataSize = (buffer.getShort(24).toInt() and 0xFFFF) - 8
        if (dnsDataSize <= 0) return

        val dnsData = ByteArray(dnsDataSize)
        buffer.position(28)
        buffer.get(dnsData)

        val domain = parseDomainName(dnsData) ?: "unknown"

        if (isWhitelisted(domain)) {
            forwardDns(dnsData, socket, outputStream, sourceIP, sourcePort, destIP)
            return
        }

        if (isBlacklisted(domain)) {
            statsManager.incrementBlockedAds()
            eventLogger.logEvent("Blocked: $domain")
            val nxResponse = createNxDomainResponse(dnsData)
            sendResponse(nxResponse, outputStream, destIP, 53, sourceIP, sourcePort)
        } else {
            forwardDns(dnsData, socket, outputStream, sourceIP, sourcePort, destIP)
        }
    }

    private fun forwardDns(
        dnsData: ByteArray,
        socket: DatagramSocket,
        outputStream: FileOutputStream,
        origSource: ByteArray,
        origSourcePort: Int,
        origDest: ByteArray
    ) {
        try {
            val serverAddr = InetAddress.getByName(customDnsServer)
            val outPacket = DatagramPacket(dnsData, dnsData.size, serverAddr, 53)
            socket.send(outPacket)

            val inBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            socket.receive(inPacket)

            val responseData = inPacket.data.copyOf(inPacket.length)
            sendResponse(responseData, outputStream, origDest, 53, origSource, origSourcePort)
        } catch (e: Exception) {
            Log.e("DnsProxy", "Forward error: ${e.message}")
        }
    }

    private fun sendResponse(
        dnsResponse: ByteArray,
        outputStream: FileOutputStream,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int
    ) {
        val totalLen = 20 + 8 + dnsResponse.size
        val packet = ByteBuffer.allocate(totalLen)
        packet.order(ByteOrder.BIG_ENDIAN)

        // IP Header
        packet.put(0, 0x45.toByte())
        packet.putShort(2, totalLen.toShort())
        packet.put(9, 17.toByte()) // UDP
        packet.position(12)
        packet.put(srcIp)
        packet.put(dstIp)

        // UDP Header
        packet.position(20)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        packet.putShort(24, (8 + dnsResponse.size).toShort())

        // DNS Data
        packet.position(28)
        packet.put(dnsResponse)

        outputStream.write(packet.array())
    }

    private fun isWhitelisted(domain: String): Boolean {
        synchronized(whitelist) {
            return whitelist.any { domain == it || domain.endsWith(".$it") }
        }
    }

    private fun isBlacklisted(domain: String): Boolean {
        synchronized(blacklist) {
            return blacklist.contains(domain) || blacklist.any { domain.endsWith(".$it") }
        }
    }

    private fun parseDomainName(data: ByteArray): String? {
        if (data.size < 13) return null
        val sb = StringBuilder()
        var pos = 12
        try {
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) break
                if (pos + len + 1 > data.size) return null
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(String(data, pos + 1, len))
                pos += len + 1
            }
        } catch (e: Exception) {
            return null
        }
        return sb.toString()
    }

    private fun createNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size < 4) return response
        response[2] = (response[2].toInt() or 0x81).toByte() // Response + Recursion Desired
        response[3] = (response[3].toInt() or 0x83).toByte() // Recursion Avail + NXDOMAIN
        return response
    }
}
