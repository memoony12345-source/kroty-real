package com.example.domain.network

import android.content.Context
import android.net.Network
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Discovers the REAL hotspot portal of the current Wi-Fi, which is often NOT the
 * Android gateway (e.g. gateway 10.0.20.1 but the MikroTik hotspot lives on 3.3.3.3).
 *
 * Strategy, in order:
 * 1. Cached endpoint remembered for this SSID.
 * 2. Captive-portal redirect: a plain HTTP probe answers 302 whose Location is the portal.
 * 3. Parallel probe of the gateway, private DNS servers and well known MikroTik
 *    hotspot hosts, fingerprinting the response (302 to itself / hotspot markers).
 */
data class PortalEndpoint(
    val baseUrl: String,
    val host: String,
    val source: String
)

object PortalDiscovery {

    private const val PREFS = "kroty_portal_endpoints"
    private const val KEY_BASE = "portal_base_"
    private const val KEY_TIME = "portal_time_"
    private const val KEY_ATTEMPT = "portal_attempt_"
    private const val KEY_LAST = "portal_last"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val ATTEMPT_THROTTLE_MS = 5 * 60 * 1000L

    /** MikroTik hotspot hosts seen in the field plus MikroTik/common defaults. */
    private val knownHotspotHosts = listOf(
        "3.3.3.3", "10.5.50.1", "10.10.10.10", "192.168.88.1", "172.16.0.1", "10.0.0.1"
    )

    private val hotspotMarkers = listOf(
        "hotspot", "mikrotik", "voucher", "routeros", "/login"
    )

    fun normalizeSsid(raw: String?): String = (raw ?: "").replace("\"", "").trim()

    fun hostOf(baseUrl: String): String = try {
        java.net.URI(baseUrl).host.orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyFor(ssid: String): String =
        if (ssid.isBlank()) KEY_LAST else KEY_BASE + ssid

    /** Cached portal for this SSID (falls back to the last discovered one). */
    fun cached(context: Context, ssid: String): PortalEndpoint? {
        return try {
            val p = prefs(context)
            val key = keyFor(normalizeSsid(ssid))
            var base = p.getString(key, null)
            if (base.isNullOrBlank() && key != KEY_LAST) base = p.getString(KEY_LAST, null)
            if (base.isNullOrBlank()) return null
            val at = p.getLong(KEY_TIME + key, 0L)
            if (at > 0L && SystemClock.elapsedRealtime() - at > CACHE_TTL_MS) return null
            PortalEndpoint(base, hostOf(base), "CACHE")
        } catch (_: Exception) {
            null
        }
    }

    fun cachedBaseFor(context: Context, ssid: String): String = cached(context, ssid)?.baseUrl.orEmpty()

    fun remember(context: Context, ssid: String, baseUrl: String) {
        val clean = baseUrl.trim().removeSuffix("/")
        if (clean.isBlank()) return
        try {
            val p = prefs(context)
            val key = keyFor(normalizeSsid(ssid))
            p.edit()
                .putString(key, clean)
                .putLong(KEY_TIME + key, SystemClock.elapsedRealtime())
                .putString(KEY_LAST, clean)
                .apply()
        } catch (_: Exception) {}
    }

    suspend fun discover(
        context: Context,
        wifiNet: Network?,
        gatewayIp: String,
        ssid: String,
        dnsServers: List<String> = emptyList(),
        force: Boolean = false
    ): PortalEndpoint? = withContext(Dispatchers.IO) {
        val cleanSsid = normalizeSsid(ssid)
        if (!force) {
            cached(context, cleanSsid)?.let { return@withContext it }
            if (recentAttempt(context, cleanSsid)) return@withContext null
        }
        markAttempt(context, cleanSsid)

        val client = buildClient(wifiNet, followRedirects = false)

        // 1) Captive portal redirect reveals the true hotspot host.
        redirectProbe(client)?.let { base ->
            remember(context, cleanSsid, base)
            return@withContext PortalEndpoint(base, hostOf(base), "REDIRECT")
        }

        // 2) Probe gateway + private DNS + well known hotspot hosts in parallel.
        val candidates = LinkedHashSet<String>()
        if (gatewayIp.isNotBlank()) candidates.add(gatewayIp)
        val dns = dnsServers.ifEmpty { wifiDnsServers(context) }
        dns.map { it.trim() }.filter { isPrivateIpv4(it) }.forEach { candidates.add(it) }
        knownHotspotHosts.forEach { candidates.add(it) }

        val found = coroutineScope {
            candidates.map { ip ->
                async {
                    if (ip.isBlank()) null
                    else {
                        val base = "http://$ip"
                        if (looksLikeHotspot(client, base)) base else null
                    }
                }
            }.awaitAll().firstOrNull { it != null }
        }

        if (found != null) {
            remember(context, cleanSsid, found)
            return@withContext PortalEndpoint(found, hostOf(found), "PROBE")
        }
        null
    }

    private fun recentAttempt(context: Context, ssid: String): Boolean {
        return try {
            val at = prefs(context).getLong(KEY_ATTEMPT + ssid, 0L)
            at > 0L && SystemClock.elapsedRealtime() - at < ATTEMPT_THROTTLE_MS
        } catch (_: Exception) {
            false
        }
    }

    private fun markAttempt(context: Context, ssid: String) {
        try {
            prefs(context).edit()
                .putLong(KEY_ATTEMPT + ssid, SystemClock.elapsedRealtime())
                .apply()
        } catch (_: Exception) {}
    }

    private fun buildClient(wifiNet: Network?, followRedirects: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(1200, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)
        if (wifiNet != null) {
            try {
                builder.socketFactory(wifiNet.socketFactory)
            } catch (_: Exception) {}
        }
        return builder.build()
    }

    private fun redirectProbe(client: OkHttpClient): String? {
        val probes = listOf(
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://clients3.google.com/generate_204",
            "http://detectportal.firefox.com/success.txt"
        )
        for (url in probes) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code in 300..399) {
                        val loc = resp.header("Location").orEmpty()
                        val uri = try { java.net.URI(loc) } catch (_: Exception) { null }
                        val host = uri?.host ?: ""
                        if (host.isNotBlank() &&
                            !host.contains("google", true) &&
                            !host.contains("gstatic", true) &&
                            !host.contains("firefox", true) &&
                            !host.contains("mozilla", true)
                        ) {
                            val scheme = uri?.scheme ?: "http"
                            return "$scheme://$host"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun looksLikeHotspot(client: OkHttpClient, baseUrl: String): Boolean {
        val host = hostOf(baseUrl)
        for (path in listOf("/", "/login")) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl$path")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val location = resp.header("Location").orEmpty()
                    if (code in 300..399) {
                        if (location.startsWith("/")) return true
                        if (host.isNotBlank() && location.contains(host, true)) return true
                    }
                    if (code in 200..299) {
                        val body = try { resp.body?.string().orEmpty() } catch (_: Exception) { "" }
                        val lower = body.lowercase()
                        if (lower.isNotBlank() && hotspotMarkers.any { lower.contains(it) }) return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun wifiDnsServers(context: Context): List<String> {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            for (net in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) continue
                val lp = cm.getLinkProperties(net) ?: continue
                return lp.dnsServers.mapNotNull { it.hostAddress }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return when (a) {
            10 -> true
            172 -> b in 16..31
            192 -> b == 168
            else -> false
        }
    }
}
