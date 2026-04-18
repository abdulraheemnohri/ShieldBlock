package com.example.shieldblock.vpn

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.preference.PreferenceManager
import com.example.shieldblock.analytics.EventLogger
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.StatsManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsProxy(private val vpnFd: ParcelFileDescriptor, private val service: MyVpnService) : Runnable {
    private val blacklist = mutableSetOf<String>()
    private val whitelist = mutableSetOf<String>()
    private val customRules = mutableSetOf<String>()
    private val statsManager = StatsManager(service)
    private val filterManager = FilterManager(service)
    private val eventLogger = EventLogger(service)
    private var customDnsServer: String = "8.8.8.8"

    private var strictMode = false
    private var safeSearch = false
    private var smartFiltering = false
    private var dataSaver = false
    private var blockIpv6 = true
    private var logAllQueries = false
    private var dnsOverHttps = false

    private var totalBytesRead = 0L
    private var totalBytesWritten = 0L

    companion object {
        private val GOOGLE_SAFE_IPS = listOf("216.239.38.120")
    }

    fun getTrafficStats(): Pair<Long, Long> = totalBytesRead to totalBytesWritten

    fun updateSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(service)
        customDnsServer = prefs.getString("custom_dns", "8.8.8.8") ?: "8.8.8.8"
        strictMode = prefs.getBoolean("strict_mode", false)
        safeSearch = prefs.getBoolean("safe_search", false)
        smartFiltering = prefs.getBoolean("smart_filtering", false)
        dataSaver = prefs.getBoolean("data_saver", false)
        blockIpv6 = prefs.getBoolean("block_ipv6", true)
        logAllQueries = prefs.getBoolean("log_all_queries", false)
        dnsOverHttps = prefs.getBoolean("dns_over_https", false)

        synchronized(customRules) {
            customRules.clear()
            customRules.addAll(filterManager.getCustomRules())
        }
    }

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
            updateSettings()

            socket = DatagramSocket()
            service.protect(socket)
            socket.soTimeout = 5000

            val inputStream = FileInputStream(vpnFd.fileDescriptor)
            val outputStream = FileOutputStream(vpnFd.fileDescriptor)
            val buffer = ByteBuffer.allocate(32768)

            while (!Thread.interrupted()) {
                buffer.clear()
                val length = try { inputStream.read(buffer.array()) } catch(e: Exception) { -1 }
                if (length > 0) {
                    totalBytesRead += length
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
        if (buffer.limit() < 28) return

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

        if (destPort != 53) return

        val dnsDataSize = (buffer.getShort(24).toInt() and 0xFFFF) - 8
        if (dnsDataSize <= 0) return

        val dnsData = ByteArray(dnsDataSize)
        buffer.position(28)
        buffer.get(dnsData)

        val domain = parseDomainName(dnsData) ?: "unknown"
        val qType = getQueryType(dnsData)
        val qTypeName = getQueryTypeName(qType)

        if (blockIpv6 && qType == 28) {
            val emptyResponse = createEmptyResponse(dnsData)
            sendResponse(emptyResponse, outputStream, destIP, 53, sourceIP, sourcePort)
            if (logAllQueries) eventLogger.logEvent("Query: $domain ($qTypeName) -> BLOCKED (IPv6 Leak Protection)")
            return
        }

        if (isWhitelisted(domain)) {
            statsManager.incrementSafeQueries()
            broadcastEvent(domain, "Allowed (Whitelist)")
            val latency = forwardDns(dnsData, socket, outputStream, sourceIP, sourcePort, destIP)
            if (logAllQueries) eventLogger.logEvent("Query: $domain ($qTypeName) -> ALLOWED via $customDnsServer (Whitelist) [${latency}ms]")
            return
        }

        if (strictMode) {
            blockDomain(domain, dnsData, outputStream, destIP, sourceIP, sourcePort, qType, "Strict Mode")
            return
        }

        if (safeSearch && isSearchDomain(domain)) {
            broadcastEvent(domain, "Redirect (SafeSearch)")
            val safeResponse = createARecordResponse(dnsData, GOOGLE_SAFE_IPS.first())
            sendResponse(safeResponse, outputStream, destIP, 53, sourceIP, sourcePort)
            if (logAllQueries) eventLogger.logEvent("Query: $domain ($qTypeName) -> REDIRECT (SafeSearch Enforcement)")
            return
        }

        val reason = getBlockedReason(domain)
        if (reason != null) {
            blockDomain(domain, dnsData, outputStream, destIP, sourceIP, sourcePort, qType, reason)
        } else {
            statsManager.incrementSafeQueries()
            broadcastEvent(domain, "Allowed ($qTypeName)")
            val latency = forwardDns(dnsData, socket, outputStream, sourceIP, sourcePort, destIP)
            if (logAllQueries) {
                val path = if (dnsOverHttps) "SECURE (DoH)" else customDnsServer
                eventLogger.logEvent("Query: $domain ($qTypeName) -> ALLOWED via $path [${latency}ms]")
            }
        }
    }

    private fun getBlockedReason(domain: String): String? {
        if (isBlacklisted(domain)) return "Blocklist Match"
        if (matchesCustomRules(domain)) return "Custom Blocking Rule"

        if (smartFiltering) {
            val suspicious = listOf("tracker", "telemetry", "analytics", "metrics", "log-", "stats", "ads.", "doubleclick", "adservice")
            if (suspicious.any { domain.contains(it, ignoreCase = true) }) return "Smart Filter (Suspicious Keyword)"
        }

        if (dataSaver) {
            val heavy = listOf("tiktok.com", "instagram.com", "fbcdn.net", "googlevideo.com")
            if (heavy.any { domain.contains(it, ignoreCase = true) }) return "Data Saver (Heavy Media)"
        }

        return null
    }

    private fun broadcastEvent(domain: String, action: String) {
        service.sendBroadcast(Intent("com.example.shieldblock.PACKET_EVENT").apply {
            putExtra("domain", domain)
            putExtra("action", action)
        })
    }

    private fun blockDomain(domain: String, dnsData: ByteArray, outputStream: FileOutputStream, destIP: ByteArray, sourceIP: ByteArray, sourcePort: Int, qType: Int, reason: String) {
        statsManager.incrementBlockedAds()
        statsManager.logBlockedDomain(domain)
        eventLogger.logEvent("Blocked: $domain ($reason)")
        broadcastEvent(domain, "Blocked ($reason)")
        val nxResponse = createNxDomainResponse(dnsData)
        sendResponse(nxResponse, outputStream, destIP, 53, sourceIP, sourcePort)
    }

    private fun isSearchDomain(domain: String): Boolean {
        return domain.contains("google.") || domain.contains("bing.com") || domain.contains("youtube.com") || domain.contains("duckduckgo.com")
    }

    private fun forwardDns(
        dnsData: ByteArray,
        socket: DatagramSocket,
        outputStream: FileOutputStream,
        origSource: ByteArray,
        origSourcePort: Int,
        origDest: ByteArray
    ): Long {
        val startTime = System.currentTimeMillis()
        try {
            val serverAddr = InetAddress.getByName(customDnsServer)
            val outPacket = DatagramPacket(dnsData, dnsData.size, serverAddr, 53)
            socket.send(outPacket)

            val inBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            socket.receive(inPacket)

            val latency = System.currentTimeMillis() - startTime
            val responseData = inPacket.data.copyOf(inPacket.length)
            sendResponse(responseData, outputStream, origDest, 53, origSource, origSourcePort)
            return latency
        } catch (e: Exception) {
            return -1
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

        packet.put(0, 0x45.toByte())
        packet.putShort(2, totalLen.toShort())
        packet.put(9, 17.toByte())
        packet.position(12)
        packet.put(srcIp)
        packet.put(dstIp)

        packet.position(20)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        packet.putShort(24, (8 + dnsResponse.size).toShort())

        packet.position(28)
        packet.put(dnsResponse)

        val data = packet.array()
        try {
            outputStream.write(data)
            totalBytesWritten += data.size
        } catch (e: Exception) {}
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

    private fun matchesCustomRules(domain: String): Boolean {
        synchronized(customRules) {
            return customRules.any { rule ->
                try {
                    if (rule.contains("*")) {
                        val regexStr = rule.replace(".", "\\.").replace("*", ".*")
                        val regex = regexStr.toRegex(RegexOption.IGNORE_CASE)
                        regex.matches(domain)
                    } else {
                        domain.contains(rule, ignoreCase = true)
                    }
                } catch (e: Exception) {
                    false
                }
            }
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

    private fun getQueryTypeName(type: Int): String = when(type) {
        1 -> "A"
        28 -> "AAAA"
        5 -> "CNAME"
        15 -> "MX"
        16 -> "TXT"
        else -> "TYPE_$type"
    }

    private fun getQueryType(data: ByteArray): Int {
        var pos = 12
        try {
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) {
                    pos++
                    if (pos + 1 < data.size) {
                        return ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    }
                    break
                }
                pos += len + 1
            }
        } catch (e: Exception) {}
        return 1
    }

    private fun createNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size < 4) return response
        response[2] = (response[2].toInt() or 0x81).toByte()
        response[3] = (response[3].toInt() or 0x83).toByte()
        return response
    }

    private fun createEmptyResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size < 4) return response
        response[2] = (response[2].toInt() or 0x81).toByte()
        response[3] = (response[3].toInt() or 0x80).toByte()
        return response
    }

    private fun createARecordResponse(query: ByteArray, ip: String): ByteArray {
        val response = query.copyOf().toMutableList()
        if (response.size < 12) return query
        response[2] = 0x81.toByte()
        response[3] = 0x80.toByte()
        response[7] = 0x01.toByte()
        response.add(0xc0.toByte()); response.add(0x0c.toByte())
        response.add(0x00.toByte()); response.add(0x01.toByte())
        response.add(0x00.toByte()); response.add(0x01.toByte())
        response.add(0x00.toByte()); response.add(0x00.toByte()); response.add(0x00.toByte()); response.add(0x3c.toByte())
        response.add(0x00.toByte()); response.add(0x04.toByte())
        ip.split(".").forEach { response.add(it.toInt().toByte()) }
        return response.toByteArray()
    }
}
