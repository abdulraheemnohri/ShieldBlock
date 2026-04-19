package com.example.shieldblock.vpn

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.example.shieldblock.analytics.EventLogger
import com.example.shieldblock.data.StatsManager
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.regex.Pattern

class DnsProxy(private val vpnInterface: ParcelFileDescriptor, private val service: MyVpnService) {
    private val eventLogger = EventLogger(service)
    private val statsManager = StatsManager(service)
    private val blacklist = mutableSetOf<String>()
    private val whitelist = mutableSetOf<String>()
    private val customRules = mutableSetOf<String>()
    private val regexRules = mutableSetOf<Pattern>()
    private val blockedIps = mutableSetOf<String>()

    private var customDnsServer = "8.8.8.8"
    private var strictMode = false
    private var smartFiltering = false
    private var dataSaver = false
    private var blockIpv6 = true
    private var safeSearch = false
    private var logAllQueries = false
    private var dnsOverHttps = false
    private var stealthMode = false

    private var totalBytesRead = 0L
    private var totalBytesWritten = 0L

    companion object {
        private val GOOGLE_SAFE_IPS = listOf("216.239.38.120")
    }

    fun updateBlacklist(domains: Collection<String>) {
        synchronized(blacklist) {
            blacklist.clear()
            blacklist.addAll(domains)
        }
    }

    fun updateWhitelist(domains: Collection<String>) {
        synchronized(whitelist) {
            whitelist.clear()
            whitelist.addAll(domains)
        }
    }

    fun updateSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(service)
        customDnsServer = prefs.getString("custom_dns", "8.8.8.8") ?: "8.8.8.8"
        strictMode = prefs.getBoolean("strict_mode", false)
        smartFiltering = prefs.getBoolean("smart_filtering", false)
        dataSaver = prefs.getBoolean("data_saver", false)
        blockIpv6 = prefs.getBoolean("block_ipv6", true)
        safeSearch = prefs.getBoolean("safe_search", false)
        logAllQueries = prefs.getBoolean("log_all_queries", false)
        dnsOverHttps = prefs.getBoolean("dns_over_https", false)
        stealthMode = prefs.getBoolean("stealth_mode", false)

        val rules = prefs.getStringSet("custom_rules", emptySet()) ?: emptySet()
        synchronized(customRules) {
            customRules.clear()
            regexRules.clear()
            rules.forEach { rule ->
                if (rule.startsWith("regex:")) {
                    try {
                        regexRules.add(Pattern.compile(rule.substring(6), Pattern.CASE_INSENSITIVE))
                    } catch (e: Exception) {}
                } else {
                    customRules.add(rule)
                }
            }
        }

        val ips = prefs.getStringSet("firewall_blocked_ips", emptySet()) ?: emptySet()
        synchronized(blockedIps) {
            blockedIps.clear()
            blockedIps.addAll(ips)
        }
    }

    fun getTrafficStats(): Pair<Long, Long> = totalBytesRead to totalBytesWritten

    fun run() {
        val inputStream = FileInputStream(vpnInterface.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)
        val socket = DatagramSocket()
        service.protect(socket)

        try {
            while (!Thread.interrupted()) {
                packet.clear()
                val length = inputStream.read(packet.array())
                if (length > 0) {
                    totalBytesRead += length
                    handlePacket(packet, length, socket, outputStream)
                }
            }
        } catch (e: Exception) {
        } finally {
            socket.close()
        }
    }

    private fun handlePacket(buffer: ByteBuffer, length: Int, socket: DatagramSocket, outputStream: FileOutputStream) {
        buffer.limit(length)
        if (buffer.get(0).toInt() and 0xF0 != 0x45) return

        val ipHeaderSize = (buffer.get(0).toInt() and 0x0F) * 4
        val protocol = buffer.get(9).toInt() and 0xFF

        val sourceIP = ByteArray(4)
        val destIP = ByteArray(4)
        buffer.position(12)
        buffer.get(sourceIP)
        buffer.get(destIP)

        val destIpStr = InetAddress.getByAddress(destIP).hostAddress
        if (isIpBlocked(destIpStr)) {
            eventLogger.logEvent("Firewall: DROPPED packet to $destIpStr")
            broadcastEvent(destIpStr, "DROPPED (Core Firewall)")
            return
        }

        if (protocol == 1 && stealthMode) { // ICMP
            return
        }

        if (protocol != 17) return

        val sourcePort = buffer.getShort(ipHeaderSize).toInt() and 0xFFFF
        val destPort = buffer.getShort(ipHeaderSize + 2).toInt() and 0xFFFF

        if (destPort != 53) return

        buffer.position(ipHeaderSize + 8)
        val dnsData = ByteArray(length - ipHeaderSize - 8)
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
            broadcastEvent(domain, "Authorized (Whitelisted)")
            val latency = forwardDns(dnsData, socket, outputStream, sourceIP, sourcePort, destIP)
            if (logAllQueries) {
                val path = if (dnsOverHttps) "SECURE (DoH)" else customDnsServer
                eventLogger.logEvent("Query: $domain ($qTypeName) -> AUTHORIZED via $path [${latency}ms]")
            }
            return
        }

        if (strictMode) {
            blockDomain(domain, dnsData, outputStream, destIP, sourceIP, sourcePort, "Strict Protocol")
            return
        }

        if (safeSearch && isSearchDomain(domain)) {
            broadcastEvent(domain, "Enforcing SafeSearch")
            val safeResponse = createARecordResponse(dnsData, GOOGLE_SAFE_IPS.first())
            sendResponse(safeResponse, outputStream, destIP, 53, sourceIP, sourcePort)
            if (logAllQueries) eventLogger.logEvent("Query: $domain ($qTypeName) -> REDIRECT (Aegis SafeSearch)")
            return
        }

        val reason = getBlockedReason(domain)
        if (reason != null) {
            blockDomain(domain, dnsData, outputStream, destIP, sourceIP, sourcePort, reason)
        } else {
            statsManager.incrementSafeQueries()
            broadcastEvent(domain, "Authorized ($qTypeName)")
            val latency = forwardDns(dnsData, socket, outputStream, sourceIP, sourcePort, destIP)
            if (logAllQueries) {
                val path = if (dnsOverHttps) "SECURE (DoH)" else customDnsServer
                eventLogger.logEvent("Query: $domain ($qTypeName) -> AUTHORIZED via $path [${latency}ms]")
            }
        }
    }

    private fun isIpBlocked(ip: String): Boolean {
        synchronized(blockedIps) {
            return blockedIps.contains(ip)
        }
    }

    private fun getBlockedReason(domain: String): String? {
        if (isBlacklisted(domain)) return "Threat DB Match"
        if (matchesCustomRules(domain)) return "Custom Pattern"

        if (smartFiltering) {
            val suspicious = listOf("tracker", "telemetry", "analytics", "metrics", "log-", "stats", "ads.", "doubleclick", "adservice")
            if (suspicious.any { domain.contains(it, ignoreCase = true) }) return "Heuristic Anomaly"
        }

        if (dataSaver) {
            val heavy = listOf("tiktok.com", "instagram.com", "fbcdn.net", "googlevideo.com")
            if (heavy.any { domain.contains(it, ignoreCase = true) }) return "Bandwidth Throttle"
        }

        return null
    }

    private fun broadcastEvent(domain: String, action: String) {
        service.sendBroadcast(Intent("com.example.shieldblock.PACKET_EVENT").apply {
            putExtra("domain", domain)
            putExtra("action", action)
            putExtra("protocol", "UDP")
            putExtra("port", 53)
        })
    }

    private fun blockDomain(domain: String, dnsData: ByteArray, outputStream: FileOutputStream, destIP: ByteArray, sourceIP: ByteArray, sourcePort: Int, reason: String) {
        statsManager.incrementBlockedAds()
        statsManager.logBlockedDomain(domain)
        eventLogger.logEvent("Blocked: $domain ($reason)")
        broadcastEvent(domain, "MITIGATED: $reason")
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
            if (customRules.any { domain.contains(it, ignoreCase = true) }) return true
            if (regexRules.any { it.matcher(domain).find() }) return true
            return false
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
