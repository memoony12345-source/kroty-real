package com.example.domain.network

import android.net.Network
import com.example.domain.web.BalanceAndErrorParser
import com.example.domain.web.ParsedMikroTikStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class PortalProbeResult(
    val gatewayIp: String,
    val portalBaseUrl: String,
    val discoveredLoginUrl: String,
    val statusUrl: String,
    val logoutUrl: String,
    val isInternetActive: Boolean,
    val liveStatus: ParsedMikroTikStatus? = null
)

class CaptivePortalProber(private val radarEngine: WifiRadarEngine) {

    private fun buildWifiClient(wifiNetwork: Network?, followRedirects: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)
        if (wifiNetwork != null) {
            try {
                builder.socketFactory(wifiNetwork.socketFactory)
                // Resolve names on the Wi-Fi too: the hotspot's DNS is what knows the portal.
                builder.dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        wifiNetwork.getAllByName(hostname).toList()
                })
            } catch (_: Exception) {}
        }
        return builder.build()
    }

    private fun hostOfUrl(url: String): String = PortalDiscovery.hostOf(url)

    /**
     * @param overrideBaseUrl portal origin already discovered (e.g. http://3.3.3.3)
     * @param discoveredLoginUrl exact page Android would open (e.g. http://c.net/index.csp?..)
     */
    suspend fun probeCurrentNetwork(
        overrideBaseUrl: String? = null,
        discoveredLoginUrl: String? = null
    ): PortalProbeResult = withContext(Dispatchers.IO) {
        val wifiState = radarEngine.refreshNow()
        val wifiNet = radarEngine.getActiveWifiNetwork()
        val gatewayIp = wifiState.gatewayIp

        val knownLogin = discoveredLoginUrl?.trim().orEmpty()
        var loginUrl = if (knownLogin.startsWith("http")) knownLogin
        else if (gatewayIp.isNotBlank()) "http://$gatewayIp/login" else ""
        var isInternetActive = false

        // 1. Check the captive-portal probe inside the Wi-Fi interface (redirect = portal URL)
        val noRedirectClient = buildWifiClient(wifiNet, followRedirects = false)
        val probes = listOf(
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://clients3.google.com/generate_204",
            "http://detectportal.firefox.com/success.txt"
        )
        for (url in probes) {
            var done = false
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile")
                    .header("Connection", "close")
                    .build()
                noRedirectClient.newCall(req).execute().use { resp ->
                    when (resp.code) {
                        204 -> {
                            isInternetActive = true
                            done = true
                        }
                        in 300..399 -> {
                            val loc = resp.header("Location").orEmpty()
                            if (loc.isNotBlank()) {
                                val abs = try {
                                    java.net.URI(url).resolve(loc).toString()
                                } catch (_: Exception) {
                                    loc
                                }
                                val host = hostOfUrl(abs)
                                if (host.isNotBlank() && !host.contains("google", true) &&
                                    !host.contains("gstatic", true) && !host.contains("firefox", true)
                                ) {
                                    loginUrl = abs
                                }
                            }
                            done = true
                        }
                        200 -> {
                            val body = resp.body?.string().orEmpty()
                            val meta = Regex("""url\s*=\s*['"]?(http[^'">\s]+)""", RegexOption.IGNORE_CASE)
                                .find(body)?.groupValues?.getOrNull(1)
                            if (!meta.isNullOrBlank()) loginUrl = meta
                            done = true
                        }
                    }
                }
            } catch (_: Exception) {}
            if (done) break
        }

        val overrideBase = overrideBaseUrl?.trim()?.removeSuffix("/").orEmpty()
        val baseHost = when {
            overrideBase.startsWith("http") -> overrideBase
            loginUrl.startsWith("http") && hostOfUrl(loginUrl).isNotBlank() -> {
                val scheme = if (loginUrl.startsWith("https")) "https" else "http"
                "$scheme://${hostOfUrl(loginUrl)}"
            }
            gatewayIp.isNotBlank() -> "http://$gatewayIp"
            else -> ""
        }

        val statusUrl = if (baseHost.isNotBlank()) "$baseHost/status" else ""
        val logoutUrl = if (baseHost.isNotBlank()) "$baseHost/logout" else ""
        val finalLoginUrl = when {
            overrideBase.isNotBlank() && knownLogin.isBlank() && baseHost.isNotBlank() -> "$baseHost/login"
            knownLogin.isNotBlank() -> knownLogin
            loginUrl.isNotBlank() -> loginUrl
            baseHost.isNotBlank() -> "$baseHost/login"
            else -> ""
        }

        // 2. Fetch the live /status page (and the login page as fallback) to read
        //    the active voucher & remaining balance from the real portal.
        var liveStatus: ParsedMikroTikStatus? = null
        val followClient = buildWifiClient(wifiNet, followRedirects = true)
        val fetchOrder = listOfNotNull(
            statusUrl.takeIf { it.isNotBlank() },
            finalLoginUrl.takeIf { it.startsWith("http") && it != statusUrl }
        )
        for (target in fetchOrder) {
            if (liveStatus != null) break
            try {
                val statusReq = Request.Builder()
                    .url(target)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()
                followClient.newCall(statusReq).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    val html = try {
                        resp.body?.string().orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    if (html.isNotBlank()) {
                        val parsed = BalanceAndErrorParser.parseStatusOrLoginHtml(finalUrl, html)
                        if (finalUrl.contains("/login", ignoreCase = true) && !finalUrl.contains("/status")) {
                            loginUrl = finalUrl
                        } else if (parsed.isLoggedInStatusPage ||
                            parsed.remainingMegabytes != null ||
                            parsed.activeUsername != null
                        ) {
                            liveStatus = parsed
                            isInternetActive = true
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        PortalProbeResult(
            gatewayIp = gatewayIp,
            portalBaseUrl = baseHost,
            discoveredLoginUrl = finalLoginUrl,
            statusUrl = statusUrl,
            logoutUrl = logoutUrl,
            isInternetActive = isInternetActive,
            liveStatus = liveStatus
        )
    }

    suspend fun sendLogoutRequest(logoutUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (logoutUrl.isBlank()) return@withContext false
        val wifiNet = radarEngine.getActiveWifiNetwork()
        val client = buildWifiClient(wifiNet, followRedirects = true)
        return@withContext try {
            val req = Request.Builder().url(logoutUrl).build()
            client.newCall(req).execute().use { it.isSuccessful || it.code in 200..399 }
        } catch (_: Exception) {
            false
        }
    }
}
