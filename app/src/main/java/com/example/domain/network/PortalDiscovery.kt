package com.example.domain.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * v3.6 realistic portal discovery.
 *
 * The old engine guessed a handful of addresses. In the field that fails:
 *  - the Android gateway often has NO web server at all (only Winbox on 8291),
 *  - the portal can live on a public domain (e.g. c.net) or on an unrelated IP (3.3.3.3),
 *  - with mobile data ON the probe may leak out of the Wi-Fi and answer "204 healthy".
 *
 * What Android itself does, and what we now mirror, in order:
 *  1. HTTP probe of connectivitycheck.gstatic.com/generate_204 **inside** the Wi-Fi
 *     (socket + DNS bound to that Network) and read the redirect Location.
 *  2. DNS hijack trick: a real hotspot answers *any* name with its own portal IP.
 *  3. Only then probe the gateway / private DNS / known MikroTik hosts, and accept a
 *     candidate **only** when the page really is a MikroTik hotspot login page.
 *  4. Cache per SSID, but re-validate the cached portal before trusting it.
 */
data class PortalEndpoint(
    val baseUrl: String,
    val host: String,
    val source: String,
    val loginUrl: String = ""
)

object PortalDiscovery {

    private const val PREFS = "kroty_portal_endpoints"
    private const val KEY_BASE = "portal_base_"
    private const val KEY_LOGIN = "portal_login_"
    private const val KEY_TIME = "portal_time_"
    private const val KEY_ATTEMPT = "portal_attempt_"
    private const val KEY_LAST = "portal_last"

    /** A cached portal is validated before use, so it can live longer than before. */
    private const val CACHE_TTL_MS = 45 * 60 * 1000L
    private const val ATTEMPT_THROTTLE_MS = 25 * 1000L

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Same probe endpoints Android and other OSes use for captive detection. */
    private val probeUrls = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://connectivitycheck.android.com/generate_204",
        "http://clients3.google.com/generate_204",
        "http://detectportal.firefox.com/success.txt"
    )

    /** Hosts a MikroTik hotspot is commonly configured with, when everything else fails. */
    private val knownHotspotHosts = listOf(
        "3.3.3.3", "10.5.50.1", "10.10.10.10", "192.168.88.1",
        "172.16.0.1", "10.0.0.1", "192.168.0.1", "192.168.1.1"
    )

    private val portalPorts = listOf(80, 8080)

    private val portalMarkers = listOf(
        "mikrotik", "hotspot", "routeros", "voucher", "wifi-code",
        "name=\"username\"", "name='username'", "name=username",
        "name=\"password\"", "name=password", "md5.js", "/login?"
    )

    fun normalizeSsid(raw: String?): String = (raw ?: "").replace("\"", "").trim()

    fun hostOf(baseUrl: String): String = try {
        URI(baseUrl).host.orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun originOf(url: String): String = try {
        val u = URI(url.trim())
        val scheme = u.scheme ?: "http"
        val host = u.host ?: return ""
        val port = if (u.port > 0 && u.port != 80 && u.port != 443) ":${u.port}" else ""
        "$scheme://$host$port"
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
            var login = p.getString(KEY_LOGIN + key, null).orEmpty()
            if (base.isNullOrBlank() && key != KEY_LAST) {
                base = p.getString(KEY_LAST, null)
                login = ""
            }
            if (base.isNullOrBlank()) return null
            val at = p.getLong(KEY_TIME + key, 0L)
            if (at > 0L && SystemClock.elapsedRealtime() - at > CACHE_TTL_MS) return null
            PortalEndpoint(base, hostOf(base), "CACHE", login)
        } catch (_: Exception) {
            null
        }
    }

    fun cachedBaseFor(context: Context, ssid: String): String = cached(context, ssid)?.baseUrl.orEmpty()

    /** The exact page Android would open (redirect target), if it was captured. */
    fun cachedLoginFor(context: Context, ssid: String): String = cached(context, ssid)?.loginUrl.orEmpty()

    fun remember(context: Context, ssid: String, baseUrl: String, loginUrl: String = "") {
        val clean = baseUrl.trim().removeSuffix("/")
        if (clean.isBlank()) return
        try {
            val p = prefs(context)
            val key = keyFor(normalizeSsid(ssid))
            val login = loginUrl.trim()
            p.edit()
                .putString(key, clean)
                .putString(KEY_LOGIN + key, login)
                .putLong(KEY_TIME + key, SystemClock.elapsedRealtime())
                .putString(KEY_LAST, clean)
                .apply()
        } catch (_: Exception) {}
    }

    fun touch(context: Context, ssid: String) {
        try {
            val key = keyFor(normalizeSsid(ssid))
            prefs(context).edit().putLong(KEY_TIME + key, SystemClock.elapsedRealtime()).apply()
        } catch (_: Exception) {}
    }

    fun forget(context: Context, ssid: String) {
        try {
            val key = keyFor(normalizeSsid(ssid))
            prefs(context).edit()
                .remove(key)
                .remove(KEY_LOGIN + key)
                .remove(KEY_TIME + key)
                .remove(KEY_ATTEMPT + key)
                .apply()
        } catch (_: Exception) {}
    }

    /** True when Android itself has flagged the current Wi-Fi as a captive portal. */
    fun androidSeesPortal(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.any { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@any false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            }
        } catch (_: Exception) {
            false
        }
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
        val net = wifiNet ?: activeWifiNetwork(context)

        // 1) A previously learned portal, but only if it still answers on this network.
        if (!force) {
            val c = cached(context, cleanSsid)
            if (c != null && isEndpointAlive(net, c.baseUrl, c.loginUrl)) {
                touch(context, cleanSsid)
                return@withContext c
            }
            if (recentAttempt(context, cleanSsid)) return@withContext null
        }
        markAttempt(context, cleanSsid)

        // 2) The captive-portal redirect: exactly what Android's own probe sees.
        redirectProbe(net)?.let { hit ->
            val origin = originOf(hit.url)
            val base = origin.ifBlank { "http://${hostOf(hit.url)}" }
            if (base.isNotBlank()) {
                remember(context, cleanSsid, base, hit.url)
                return@withContext PortalEndpoint(base, hostOf(base), hit.source, hit.url)
            }
        }

        // 3) Candidate hosts: gateway, private DNS, DNS-hijacked answers, known MikroTik hosts.
        val candidates = LinkedHashSet<String>()
        if (gatewayIp.isNotBlank()) candidates.add(gatewayIp)
        dnsServers.ifEmpty { linkDnsServers(context) }
            .map { it.trim() }
            .filter { isPrivateIpv4(it) }
            .forEach { candidates.add(it) }
        dnsHijackCandidates(net).forEach { candidates.add(it) }
        knownHotspotHosts.forEach { candidates.add(it) }

        val client = noRedirectClient(net)
        val hits = coroutineScope {
            candidates.map { ip ->
                async {
                    if (ip.isBlank()) null
                    else if (looksLikePortal(client, ip)) "http://$ip" else null
                }
            }.awaitAll()
        }
        val found = hits.firstOrNull { it != null }
        if (found != null) {
            remember(context, cleanSsid, found, "$found/login")
            return@withContext PortalEndpoint(found, hostOf(found), "PROBE", "$found/login")
        }
        null
    }

    // ---------------------------------------------------------------- probes

    private data class PortalHit(val url: String, val source: String)

    /** Fetch the probe URLs in parallel inside the Wi-Fi network and read any redirect. */
    private suspend fun redirectProbe(net: Network?): PortalHit? = coroutineScope {
        val client = noRedirectClient(net)
        val hits = probeUrls.map { url ->
            async { probeOne(client, url) }
        }.awaitAll()
        hits.firstOrNull { it != null }
    }

    private fun probeOne(client: OkHttpClient, url: String): PortalHit? {
        return try {
            client.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "*/*")
                    .header("Connection", "close")
                    .build()
            ).execute().use { resp ->
                val code = resp.code
                when {
                    code in 300..399 -> {
                        val loc = resp.header("Location").orEmpty()
                        absoluteTarget(url, loc)?.let { PortalHit(it, "REDIRECT") }
                    }
                    code in 200..299 -> {
                        val body = try {
                            resp.body?.string().orEmpty()
                        } catch (_: Exception) {
                            ""
                        }
                        val finalUrl = resp.request.url.toString()
                        when {
                            looksLikePortalHtml(body) -> PortalHit(finalUrl, "HIJACK")
                            metaRefreshTarget(body) != null -> PortalHit(metaRefreshTarget(body)!!, "META")
                            body.trim().length > 120 && url.contains("generate_204") ->
                                PortalHit(finalUrl, "REWRITTEN")
                            else -> null
                        }
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Resolve a (possibly relative) Location header against the probe URL. */
    private fun absoluteTarget(requestUrl: String, location: String): String? {
        val loc = location.trim()
        if (loc.isBlank()) return null
        val resolved = try {
            URI(requestUrl).resolve(loc).toString()
        } catch (_: Exception) {
            loc
        }
        val host = hostOf(resolved)
        if (host.isBlank()) return null
        // Ignore redirects that stay on the probe provider itself (e.g. https upgrade).
        if (isProbeProvider(host)) return null
        return resolved
    }

    private fun isProbeProvider(host: String): Boolean {
        val h = host.lowercase()
        return h.contains("google") || h.contains("gstatic") || h.contains("android.com") ||
            h.contains("firefox") || h.contains("mozilla") || h.contains("apple")
    }

    private fun metaRefreshTarget(html: String): String? {
        if (html.isBlank()) return null
        val re = Regex("""(?i)content\s*=\s*["'][^"']*url\s*=\s*([^"'>\s]+)""")
        val alt = Regex("""(?i)url\s*=\s*['"]?(https?://[^'">\s]+)""")
        return (re.find(html) ?: alt.find(html))?.groupValues?.getOrNull(1)
    }

    private fun looksLikePortalHtml(html: String): Boolean {
        if (html.isBlank() || html.length < 40) return false
        val low = html.lowercase()
        var score = 0
        for (m in portalMarkers) if (low.contains(m)) score++
        // A plain "204 No Content"-ish body or a generic error page is not a portal.
        return score >= 2 || (score >= 1 && low.contains("<form"))
    }

    /**
     * A hotspot DNS answers every name with the portal address. Resolve a couple of
     * names inside the Wi-Fi: any private answer is a strong portal candidate.
     */
    private fun dnsHijackCandidates(net: Network?): List<String> {
        if (net == null) return emptyList()
        val out = LinkedHashSet<String>()
        val names = listOf(
            "connectivitycheck.gstatic.com",
            "kroty-portal-${(SystemClock.elapsedRealtime() % 9973)}.probe"
        )
        for (name in names) {
            try {
                net.getAllByName(name).forEach { addr ->
                    if (addr is Inet4Address) {
                        val ip = addr.hostAddress.orEmpty()
                        if (isPrivateIpv4(ip)) out.add(ip)
                    }
                }
            } catch (_: Exception) {}
        }
        return out.toList()
    }

    /** Strict check: the host must really serve a MikroTik-style hotspot login. */
    private fun looksLikePortal(client: OkHttpClient, ip: String): Boolean {
        for (port in portalPorts) {
            val base = if (port == 80) "http://$ip" else "http://$ip:$port"
            for (path in listOf("/login", "/")) {
                try {
                    val req = Request.Builder()
                        .url("$base$path")
                        .header("User-Agent", UA)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val code = resp.code
                        val location = resp.header("Location").orEmpty()
                        if (code in 300..399 && location.isNotBlank()) {
                            val target = absoluteTarget("$base$path", location)
                            if (target != null) return true
                            if (location.startsWith("/") && !location.contains("..")) return true
                        }
                        if (code in 200..299) {
                            val body = try {
                                resp.body?.string().orEmpty()
                            } catch (_: Exception) {
                                ""
                            }
                            if (looksLikePortalHtml(body)) return true
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return false
    }

    /** Cheap validation of the cached portal before we trust it again. */
    private fun isEndpointAlive(net: Network?, baseUrl: String, loginUrl: String): Boolean {
        if (baseUrl.isBlank()) return false
        val client = noRedirectClient(net)
        val urls = listOfNotNull(
            loginUrl.takeIf { it.startsWith("http") },
            "$baseUrl/login",
            "$baseUrl/status"
        )
        for (u in urls) {
            try {
                client.newCall(Request.Builder().url(u).header("User-Agent", UA).build())
                    .execute().use { resp ->
                        if (resp.code in 200..399) {
                            val body = try {
                                resp.body?.string().orEmpty()
                            } catch (_: Exception) {
                                ""
                            }
                            if (resp.code in 300..399) return true
                            if (looksLikePortalHtml(body) || body.contains("/status", true)) return true
                        }
                    }
            } catch (_: Exception) {}
        }
        return false
    }

    // ------------------------------------------------------------- plumbing

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

    /** OkHttp client fully bound to the Wi-Fi: sockets AND DNS resolution. */
    private fun noRedirectClient(net: Network?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .callTimeout(3500, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
        if (net != null) {
            try {
                builder.socketFactory(net.socketFactory)
                builder.dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        net.getAllByName(hostname).toList()
                })
            } catch (_: Exception) {}
        }
        return builder.build()
    }

    private fun activeWifiNetwork(context: Context): Network? {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun linkDnsServers(context: Context): List<String> {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (net in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
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
            100 -> b in 64..127 // carrier-grade NAT ranges used by some hotspots
            else -> false
        }
    }
}
