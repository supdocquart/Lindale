package com.metrolist.desktop

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.net.spi.InetAddressResolver
import java.net.spi.InetAddressResolver.LookupPolicy
import java.net.spi.InetAddressResolverProvider
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/**
 * Resolves Discord hosts over DNS-over-HTTPS because some ISPs block them at DNS level;
 * any other host falls back to DoH only when the system resolver cannot find it.
 * Disable with -Dmetrolist.doh=false.
 */
class DohResolverProvider : InetAddressResolverProvider() {
    override fun get(configuration: Configuration): InetAddressResolver {
        val builtin = configuration.builtinResolver()
        return if (System.getProperty("metrolist.doh") == "false") builtin else DohResolver(builtin)
    }

    override fun name(): String = "metrolist-doh"
}

private class DohResolver(private val builtin: InetAddressResolver) : InetAddressResolver {
    private class Entry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    private val forcedSuffixes = listOf(
        "discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media", "discordstatus.com",
    )

    private val servers = listOf(
        "https://1.1.1.1/dns-query?type=A&name=",
        "https://8.8.8.8/resolve?type=A&name=",
        "https://1.0.0.1/dns-query?type=A&name=",
    )

    override fun lookupByName(host: String, lookupPolicy: LookupPolicy): Stream<InetAddress> {
        val name = host.lowercase().trimEnd('.')
        val forced = forcedSuffixes.any { name == it || name.endsWith(".$it") }

        if (!forced) {
            try {
                return builtin.lookupByName(host, lookupPolicy)
            } catch (e: UnknownHostException) {
                // Fall through to DoH.
            }
        }

        val viaDoh = resolveViaDoh(name)
        if (viaDoh.isNotEmpty()) return viaDoh.stream()
        return builtin.lookupByName(host, lookupPolicy)
    }

    override fun lookupByAddress(addr: ByteArray): String = builtin.lookupByAddress(addr)

    private fun resolveViaDoh(name: String): List<InetAddress> {
        cache[name]?.let { if (it.expiresAt > System.currentTimeMillis()) return it.addresses }
        for (server in servers) {
            val result = runCatching { query(server, name) }
            val (addresses, ttl) = result.getOrNull() ?: continue
            if (addresses.isEmpty()) continue
            val ttlMs = ttl.coerceIn(60L, 3600L) * 1000L
            cache[name] = Entry(addresses, System.currentTimeMillis() + ttlMs)
            println("INFO: doh: $name -> ${addresses.joinToString { it.hostAddress }} (via ${URI(server).host})")
            return addresses
        }
        System.err.println("WARN: doh: could not resolve $name")
        return emptyList()
    }

    private fun query(server: String, name: String): Pair<List<InetAddress>, Long> {
        val url = URI(server + URLEncoder.encode(name, StandardCharsets.UTF_8)).toURL()
        val conn = url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("Accept", "application/dns-json")
            if (conn.responseCode != 200) return emptyList<InetAddress>() to 0L
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val addresses = mutableListOf<InetAddress>()
            var minTtl = Long.MAX_VALUE
            val answers = Regex("\"Answer\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                ?: return emptyList<InetAddress>() to 0L
            for (obj in Regex("\\{[^}]*}").findAll(answers)) {
                val text = obj.value
                val type = Regex("\"type\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                val data = Regex("\"data\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                val ttl = Regex("\"TTL\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull()
                if (type == 1 && data != null && IPV4.matches(data)) {
                    val bytes = data.split('.').map { it.toInt().toByte() }.toByteArray()
                    addresses += InetAddress.getByAddress(name, bytes)
                    if (ttl != null) minTtl = minOf(minTtl, ttl)
                }
            }
            return addresses to (if (minTtl == Long.MAX_VALUE) 300L else minTtl)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    }
}
