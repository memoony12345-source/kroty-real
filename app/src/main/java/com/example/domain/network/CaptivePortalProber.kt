package com.example.domain.network

import android.net.Network
import com.example.domain.web.BalanceAndErrorParser
import com.example.domain.web.ParsedMikroTikStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class PortalProbeResult(
    val gatewayIp: String,
    val discoveredLoginUrl: String,
    val statusUrl: String,
    val logoutUrl: String,
    val isInternetActive: Boolean,
    val liveStatus: ParsedMikroTikStatus? = null
)

class CaptivePortalProber(private val radarEngine: WifiRadarEngine) {

    private fun buildWifiClient(wifiNetwork: Network?, followRedirects: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)
        if (wifiNetwork != null) {
            try {
                builder.socketFactory(wifiNetwork.socketFactory)
            } catch (_: Exception) {}
        }
        return builder.build()
    }

    suspend fun probeCurrentNetwork(): PortalProbeResult = withContext(Dispatchers.IO) {
        val wifiState = radarEngine.refreshNow()
        val wifiNet = radarEngine.getActiveWifiNetwork()
        val gatewayIp = wifiState.gatewayIp

        var discoveredLoginUrl = if (gatewayIp.isNotBlank()) "http://$gatewayIp/login" else ""
        var isInternetActive = false

        // 1. Check standard captive portal HTTP 204 / 302 redirect over the Wi-Fi interface
        val noRedirectClient = buildWifiClient(wifiNet, followRedirects = false)
        try {
            val req = Request.Builder()
                .url("http://connectivitycheck.gstatic.com/generate_204")
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            noRedirectClient.newCall(req).execute().use { resp ->
                when (resp.code) {
                    204 -> isInternetActive = true
                    in 300..399 -> {
                        val loc = resp.header("Location")
                        if (!loc.isNullOrBlank() && loc.startsWith("http")) {
                            discoveredLoginUrl = loc
                        }
                    }
                    200 -> {
                        val body = resp.body?.string().orEmpty()
                        val metaRedirect = Regex("""url\s*=\s*['"]?(http[^'">\s]+)""", RegexOption.IGNORE_CASE)
                            .find(body)?.groupValues?.getOrNull(1)
                        if (!metaRedirect.isNullOrBlank()) {
                            discoveredLoginUrl = metaRedirect
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val baseHost = try {
            if (discoveredLoginUrl.startsWith("http")) {
                val u = java.net.URI(discoveredLoginUrl)
                "${u.scheme}://${u.authority}"
            } else if (gatewayIp.isNotBlank()) {
                "http://$gatewayIp"
            } else ""
        } catch (_: Exception) {
            if (gatewayIp.isNotBlank()) "http://$gatewayIp" else ""
        }

        val statusUrl = if (baseHost.isNotBlank()) "$baseHost/status" else ""
        val logoutUrl = if (baseHost.isNotBlank()) "$baseHost/logout" else ""
        val finalLoginUrl = if (discoveredLoginUrl.isNotBlank()) discoveredLoginUrl else if (baseHost.isNotBlank()) "$baseHost/login" else ""

        // 2. Fetch live /status page from the current MikroTik gateway to read active voucher & balance!
        var liveStatus: ParsedMikroTikStatus? = null
        if (statusUrl.isNotBlank()) {
            val followClient = buildWifiClient(wifiNet, followRedirects = true)
            try {
                val statusReq = Request.Builder()
                    .url(statusUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()
                followClient.newCall(statusReq).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    val html = resp.body?.string().orEmpty()
                    if (html.isNotBlank()) {
                        val parsed = BalanceAndErrorParser.parseStatusOrLoginHtml(finalUrl, html)
                        // If router redirected /status to a custom domain login page, capture that domain too!
                        if (finalUrl.contains("/login", ignoreCase = true)) {
                            discoveredLoginUrl = finalUrl
                        } else if (parsed.isLoggedInStatusPage || parsed.remainingMegabytes != null || parsed.activeUsername != null) {
                            liveStatus = parsed
                            isInternetActive = true
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        PortalProbeResult(
            gatewayIp = gatewayIp,
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
