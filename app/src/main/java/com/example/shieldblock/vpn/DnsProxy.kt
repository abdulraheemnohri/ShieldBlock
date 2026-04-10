package com.example.shieldblock.vpn

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import java.io.FileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsProxy(
    private val vpnFd: FileDescriptor,
    private val context: Context
) : Runnable {
    private val blacklist = mutableListOf<String>()
    private val whitelist = mutableSetOf<String>()
    private val customRules = mutableListOf<String>()
    private var customDnsServer: String = "8.8.8.8"

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        customDnsServer = prefs.getString("custom_dns", "8.8.8.8") ?: "8.8.8.8"
        Log.d("DnsProxy", "Using DNS server: $customDnsServer")
    }

    fun updateBlacklist(newList: List<String>) {
        blacklist.clear()
        blacklist.addAll(newList)
        Log.d("DnsProxy", "Blacklist updated with ${newList.size} entries")
    }

    fun updateWhitelist(newList: Set<String>) {
        whitelist.clear()
        whitelist.addAll(newList)
        Log.d("DnsProxy", "Whitelist updated with ${newList.size} entries")
    }

    fun addCustomRule(rule: String) {
        customRules.add(rule)
        Log.d("DnsProxy", "Added custom rule: $rule")
    }

    override fun run() {
        val socket = DatagramSocket(53, InetAddress.getByName("127.0.0.1"))
        val buffer = ByteArray(1024)

        while (!Thread.interrupted()) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            val domain = extractDomain(packet.data)
            Log.d("DnsProxy", "Processing DNS query for: $domain")

            if (whitelist.contains(domain)) {
                // Whitelisted: Forward request
                Log.d("DnsProxy", "$domain is whitelisted, forwarding...")
                forwardDnsRequest(packet, socket)
            } else if (blacklist.any { domain.contains(it) } || customRules.any { domain.matches(Regex(it)) }) {
                // Blocked: Send NXDOMAIN
                Log.d("DnsProxy", "$domain is blocked, sending NXDOMAIN...")
                socket.send(createNxDomainResponse(packet))
            } else {
                // Normal: Forward request
                Log.d("DnsProxy", "$domain is allowed, forwarding...")
                forwardDnsRequest(packet, socket)
            }
        }
        socket.close()
    }

    private fun extractDomain(data: ByteArray): String {
        // Simplified: Parse DNS query to extract domain
        return try {
            // Actual DNS parsing logic would go here
            String(data.copyOfRange(12, data.size))
        } catch (e: Exception) {
            Log.e("DnsProxy", "Error extracting domain: ${e.message}")
            ""
        }
    }

    private fun createNxDomainResponse(packet: DatagramPacket): DatagramPacket {
        // Simplified: Create NXDOMAIN response
        val response = ByteArray(12) // Example
        return DatagramPacket(
            response, response.size,
            packet.address, packet.port
        )
    }

    private fun forwardDnsRequest(packet: DatagramPacket, socket: DatagramSocket) {
        val forwardPacket = DatagramPacket(
            packet.data, packet.length,
            InetAddress.getByName(customDnsServer), 53
        )
        socket.send(forwardPacket)
        Log.d("DnsProxy", "Forwarded DNS request to $customDnsServer")
        val responseBuffer = ByteArray(1024)
        val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)
        socket.send(DatagramPacket(
            responsePacket.data, responsePacket.length,
            packet.address, packet.port
        ))
    }
}