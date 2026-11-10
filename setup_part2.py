#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kroty Net v3.1.0-REAL - Part 2: Dynamic Multi-Network Wi-Fi Radar, Portal Prober,
Live Session & Balance Extractor, Real CameraX + ML Kit OCR, Widget & Quick Settings Tile.
"""
import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"[PART 2] Created: {path}")

# 1. WifiRadarEngine.kt (Dynamic Gateway Discovery for ANY Network + Wi-Fi Process Binding)
write_file("app/src/main/java/com/example/domain/network/WifiRadarEngine.kt", r"""
package com.example.domain.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LiveWifiState(
    val isConnectedToWifi: Boolean = false,
    val ssid: String = "غير متصل بـ Wi-Fi",
    val bssid: String = "00:00:00:00:00:00",
    val rssiDbm: Int = -100,
    val signalPercent: Int = 0,
    val gatewayIp: String = "",
    val localIp: String = "",
    val isRandomizedMacSuspected: Boolean = false,
    val locationPermissionMissing: Boolean = false,
    val routerPingMs: Long? = null,
    val internetPingMs: Long? = null
)

class WifiRadarEngine(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _wifiState = MutableStateFlow(LiveWifiState())
    val wifiState: StateFlow<LiveWifiState> = _wifiState.asStateFlow()

    @Volatile
    private var currentWifiNetwork: Network? = null

    init {
        registerNetworkCallback()
        refreshNow()
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    currentWifiNetwork = network
                    refreshNow(network)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    currentWifiNetwork = network
                    refreshNow(network)
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    currentWifiNetwork = network
                    refreshNow(network)
                }

                override fun onLost(network: Network) {
                    if (currentWifiNetwork == network) {
                        currentWifiNetwork = null
                        bindProcessToWifi(false)
                    }
                    refreshNow(null)
                }
            })
        } catch (_: Exception) {}
    }

    fun getActiveWifiNetwork(): Network? {
        currentWifiNetwork?.let { return it }
        return try {
            connectivityManager.allNetworks.firstOrNull { net ->
                val caps = connectivityManager.getNetworkCapabilities(net)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }.also { currentWifiNetwork = it }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Crucial fix when 3G/4G mobile data is turned ON alongside Wi-Fi:
     * Forces WebView & HTTP requests to route through the local MikroTik Wi-Fi router!
     */
    fun bindProcessToWifi(enable: Boolean) {
        try {
            val target = if (enable) getActiveWifiNetwork() else null
            connectivityManager.bindProcessToNetwork(target)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun refreshNow(explicitNetwork: Network? = null): LiveWifiState {
        val wifiNet = explicitNetwork ?: getActiveWifiNetwork()
        if (wifiNet == null && !wifiManager.isWifiEnabled) {
            val disconnected = LiveWifiState(isConnectedToWifi = false, ssid = "الواي فاي مغلق")
            _wifiState.value = disconnected
            return disconnected
        }

        val linkProps: LinkProperties? = try {
            wifiNet?.let { connectivityManager.getLinkProperties(it) }
        } catch (_: Exception) { null }

        // 1. Extract dynamic Gateway IP from LinkProperties routes or DHCP
        var gatewayIp = ""
        if (linkProps != null) {
            for (route in linkProps.routes) {
                val gw = route.gateway
                if (gw is Inet4Address && !gw.isLoopbackAddress && gw.hostAddress != "0.0.0.0") {
                    gatewayIp = gw.hostAddress ?: ""
                    if (route.isDefaultRoute) break
                }
            }
        }
        if (gatewayIp.isBlank()) {
            try {
                val dhcpGw = wifiManager.dhcpInfo?.gateway ?: 0
                if (dhcpGw != 0) {
                    val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dhcpGw).array()
                    gatewayIp = Inet4Address.getByAddress(bytes).hostAddress ?: ""
                }
            } catch (_: Exception) {}
        }

        // 2. Extract Local IPv4
        var localIp = ""
        if (linkProps != null) {
            for (la in linkProps.linkAddresses) {
                val addr = la.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    localIp = addr.hostAddress ?: ""
                    break
                }
            }
        }

        // If localIp exists but gatewayIp couldn't be read, infer .1 on the same subnet dynamically
        if (gatewayIp.isBlank() && localIp.count { it == '.' } == 3) {
            gatewayIp = localIp.substringBeforeLast(".") + ".1"
        }

        // 3. Extract SSID, BSSID, RSSI
        var rawSsid = ""
        var bssid = "00:00:00:00:00:00"
        var rssi = -65
        try {
            val info: WifiInfo? = wifiManager.connectionInfo
            if (info != null) {
                rawSsid = info.ssid?.replace("\"", "")?.trim() ?: ""
                bssid = info.bssid ?: "00:00:00:00:00:00"
                rssi = info.rssi
            }
        } catch (_: Exception) {}

        val locationMissing = rawSsid.isBlank() || rawSsid == "<unknown ssid>"
        val displaySsid = when {
            !locationMissing -> rawSsid
            gatewayIp.isNotBlank() -> "شبكة مايكروتك ($gatewayIp)"
            else -> "غير متصل بـ Wi-Fi"
        }

        val isConnected = wifiNet != null || gatewayIp.isNotBlank()
        val signalPercent = WifiManager.calculateSignalLevel(rssi.coerceIn(-100, -30), 101).coerceIn(0, 100)
        val macRandomSuspected = checkMacRandomization()

        val newState = _wifiState.value.copy(
            isConnectedToWifi = isConnected,
            ssid = displaySsid,
            bssid = bssid,
            rssiDbm = rssi,
            signalPercent = if (isConnected) signalPercent else 0,
            gatewayIp = gatewayIp,
            localIp = localIp,
            isRandomizedMacSuspected = macRandomSuspected,
            locationPermissionMissing = isConnected && locationMissing
        )
        _wifiState.value = newState
        return newState
    }

    private fun checkMacRandomization(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val wlan = interfaces.firstOrNull { it.name.equals("wlan0", ignoreCase = true) }
            val mac = wlan?.hardwareAddress ?: return false
            if (mac.isNotEmpty()) {
                // Locally Administered Address bit (bit 1 of first byte is 1 -> 2, 6, A, E)
                (mac[0].toInt() and 0x02) != 0
            } else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun runDualPing(): Pair<Long?, Long?> = withContext(Dispatchers.IO) {
        val state = refreshNow()
        val gw = state.gatewayIp
        val wifiNet = getActiveWifiNetwork()

        val routerPing = if (gw.isNotBlank()) measureTcpLatency(wifiNet, gw, 80, 1500) else null
        val internetPing = measureTcpLatency(wifiNet, "8.8.8.8", 53, 1500)

        _wifiState.value = _wifiState.value.copy(
            routerPingMs = routerPing,
            internetPingMs = internetPing
        )
        Pair(routerPing, internetPing)
    }

    private fun measureTcpLatency(network: Network?, host: String, port: Int, timeoutMs: Int): Long? {
        return try {
            val start = System.currentTimeMillis()
            val socket = network?.socketFactory?.createSocket() ?: Socket()
            socket.use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        }
    }
}
""")

# 2. BalanceAndErrorParser.kt (Extracts Active Username, Remaining MB/GB, Remaining Time & Errors)
write_file("app/src/main/java/com/example/domain/web/BalanceAndErrorParser.kt", r"""
package com.example.domain.web

data class ParsedMikroTikStatus(
    val isLoggedInStatusPage: Boolean = false,
    val activeUsername: String? = null,
    val remainingMegabytes: Double? = null,
    val remainingTimeSeconds: Long? = null,
    val errorState: String? = null,
    val errorMessageAr: String? = null
)

object BalanceAndErrorParser {

    fun parseStatusOrLoginHtml(url: String, rawHtmlOrText: String): ParsedMikroTikStatus {
        val cleanText = rawHtmlOrText
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val error = detectError(cleanText)
        val username = extractActiveUsername(cleanText, rawHtmlOrText)
        val remainingMb = extractRemainingMb(cleanText)
        val remainingSec = extractRemainingSeconds(cleanText)

        val isStatus = url.contains("/status", ignoreCase = true) ||
                cleanText.contains("تسجيل الخروج") ||
                cleanText.contains("log off", ignoreCase = true) ||
                cleanText.contains("logout", ignoreCase = true) ||
                remainingMb != null ||
                remainingSec != null

        return ParsedMikroTikStatus(
            isLoggedInStatusPage = isStatus && error == null,
            activeUsername = username,
            remainingMegabytes = remainingMb,
            remainingTimeSeconds = remainingSec,
            errorState = error?.first,
            errorMessageAr = error?.second
        )
    }

    private fun extractActiveUsername(cleanText: String, rawHtml: String): String? {
        val patterns = listOf(
            Regex("""(?:Welcome|مرحبا|مرحباً|أهلا|اهلا)\s+([A-Za-z0-9_-]{4,20})""", RegexOption.IGNORE_CASE),
            Regex("""(?:المستخدم|اسم المستخدم|رمز الكرت|رقم الكرت|الكرت|الإسم|الاسم|username|logged in as)\s*[:\-]?\s*([A-Za-z0-9_-]{4,20})""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val match = p.find(cleanText)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && !isReservedWord(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun isReservedWord(word: String): Boolean {
        val lower = word.lowercase()
        return lower in setOf("user", "username", "status", "login", "logout", "mikrotik", "admin", "hotspot", "true", "false")
    }

    private fun extractRemainingMb(text: String): Double? {
        // Priority 1: Look near balance keywords
        val contextRegex = Regex(
            """(?:الرصيد المتبقي|باقي التحميل|المتبقي من الباقة|البيانات المتبقية|المتبقي|remain[^\d]{0,20}|bytes left[^\d]{0,20}|traffic left[^\d]{0,20})\s*[:\-]?\s*(\d+(?:[.,]\d+)?)\s*(GB|MB|KB|TB|GiB|MiB|KiB|جيجا|كيلو|ميجا|غ.ب|م.ب)""",
            RegexOption.IGNORE_CASE
        )
        contextRegex.find(text)?.let { match ->
            return convertToMb(match.groupValues[1], match.groupValues[2])
        }

        // Priority 2: General data unit scan on status page
        val generalRegex = Regex(
            """(\d+(?:[.,]\d+)?)\s*(GB|MB|GiB|MiB|جيجا|ميجا|غ\.ب|م\.ب)""",
            RegexOption.IGNORE_CASE
        )
        val allMatches = generalRegex.findAll(text).mapNotNull {
            convertToMb(it.groupValues[1], it.groupValues[2])
        }.toList()

        return allMatches.firstOrNull()
    }

    private fun convertToMb(numStr: String, unit: String): Double? {
        val value = numStr.replace(",", ".").toDoubleOrNull() ?: return null
        val u = unit.lowercase()
        return when {
            u.contains("tb") -> value * 1024.0 * 1024.0
            u.contains("gb") || u.contains("gib") || u.contains("جيجا") || u.contains("غ") -> value * 1024.0
            u.contains("kb") || u.contains("kib") || u.contains("كيلو") -> value / 1024.0
            else -> value
        }
    }

    private fun extractRemainingSeconds(text: String): Long? {
        // MikroTik standard compact format e.g. 2d5h30m10s or 5h20m
        val compact = Regex("""(?:(\d+)\s*w)?\s*(?:(\d+)\s*d)?\s*(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?\s*(?:(\d+)\s*s)""", RegexOption.IGNORE_CASE)
        for (m in compact.findAll(text)) {
            val w = m.groupValues[1].toLongOrNull() ?: 0L
            val d = m.groupValues[2].toLongOrNull() ?: 0L
            val h = m.groupValues[3].toLongOrNull() ?: 0L
            val min = m.groupValues[4].toLongOrNull() ?: 0L
            val s = m.groupValues[5].toLongOrNull() ?: 0L
            val total = w * 604800 + d * 86400 + h * 3600 + min * 60 + s
            if (total > 0 && (w > 0 || d > 0 || h > 0 || min > 0)) {
                return total
            }
        }
        return null
    }

    fun detectError(text: String): Pair<String, String>? {
        val lower = text.lowercase()
        return when {
            lower.contains("simultaneous session") || lower.contains("already logged in") || lower.contains("مستخدم في جهاز") || lower.contains("متصل حاليا") ->
                "IN_USE" to "⚠️ هذا الكرت مفتوح حالياً في جهاز آخر!"
            lower.contains("traffic limit") || lower.contains("download limit") || lower.contains("انتهى الرصيد") || lower.contains("نفدت الباقة") || lower.contains("انتهت الباقة") ->
                "EXPIRED_DATA" to "نفد رصيد التحميل (الميجابايت) لهذا الكرت"
            lower.contains("uptime limit") || lower.contains("انتهى الوقت") || lower.contains("انتهت صلاحية") ->
                "EXPIRED_TIME" to "انتهى الوقت المسموح لهذا الكرت"
            lower.contains("invalid username") || lower.contains("invalid password") || lower.contains("غير صحيح") || lower.contains("خطأ في رمز") ->
                "INVALID" to "رقم الكرت غير صحيح في هذه الشبكة"
            else -> null
        }
    }
}
""")

# 3. CaptivePortalProber.kt (Probes ANY Network & Syncs Live /status Session)
write_file("app/src/main/java/com/example/domain/network/CaptivePortalProber.kt", r"""
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
""")

# 4. MikroTikJsScripts.kt (Smart Auto-Login + Touch-to-Learn + Status Scraping)
write_file("app/src/main/java/com/example/domain/web/MikroTikJsScripts.kt", r"""
package com.example.domain.web

object MikroTikJsScripts {

    fun buildAutoLoginScript(
        voucherCode: String,
        password: String,
        passwordMode: String,
        learnedSelector: String?
    ): String {
        val safeCode = voucherCode.replace("'", "\\'").replace("\"", "\\\"")
        val safePass = when (passwordMode) {
            "SAME_AS_USER" -> safeCode
            "CUSTOM" -> password.replace("'", "\\'")
            else -> ""
        }
        val safeLearned = learnedSelector?.replace("'", "\\'") ?: ""

        return """
        (function() {
            try {
                var code = '$safeCode';
                var pass = '$safePass';
                var learned = '$safeLearned';
                var targetInput = null;

                if (learned && learned.length > 0) {
                    targetInput = document.querySelector(learned);
                }

                if (!targetInput) {
                    var inputs = Array.from(document.querySelectorAll('input'));
                    var bestScore = -1;
                    for (var i = 0; i < inputs.length; i++) {
                        var el = inputs[i];
                        var type = (el.getAttribute('type') || 'text').toLowerCase();
                        if (type === 'hidden' || type === 'submit' || type === 'button' || type === 'checkbox') continue;
                        var rect = el.getBoundingClientRect();
                        if (rect.width === 0 || rect.height === 0) continue;

                        var score = 10;
                        var name = (el.getAttribute('name') || '').toLowerCase();
                        var id = (el.getAttribute('id') || '').toLowerCase();
                        var ph = (el.getAttribute('placeholder') || '').toLowerCase();
                        var combined = name + ' ' + id + ' ' + ph;

                        if (name === 'username' || name === 'user' || name === 'code' || name === 'voucher') score += 60;
                        if (combined.indexOf('كرت') !== -1 || combined.indexOf('رمز') !== -1 || combined.indexOf('بطاقة') !== -1 || combined.indexOf('كود') !== -1) score += 50;
                        if (combined.indexOf('user') !== -1 || combined.indexOf('voucher') !== -1 || combined.indexOf('card') !== -1 || combined.indexOf('pin') !== -1) score += 40;
                        if (type === 'text' || type === 'tel' || type === 'number') score += 15;

                        if (score > bestScore) {
                            bestScore = score;
                            targetInput = el;
                        }
                    }
                }

                if (!targetInput) {
                    if (window.KrotyBridge) window.KrotyBridge.onLoginFieldMissing();
                    return 'NO_FIELD';
                }

                var maxLen = parseInt(targetInput.getAttribute('maxlength') || '0', 10);
                if (maxLen >= 4 && maxLen <= 20 && window.KrotyBridge) {
                    window.KrotyBridge.onDetectedMaxLength(maxLen);
                }

                targetInput.focus();
                targetInput.value = code;
                targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                targetInput.dispatchEvent(new Event('change', { bubbles: true }));

                var passInput = document.querySelector('input[type="password"], input[name="password"]');
                if (passInput) {
                    passInput.value = pass;
                    passInput.dispatchEvent(new Event('input', { bubbles: true }));
                    passInput.dispatchEvent(new Event('change', { bubbles: true }));
                }

                setTimeout(function() {
                    if (typeof window.doLogin === 'function') {
                        window.doLogin();
                    } else {
                        var btn = document.querySelector('button[type="submit"], input[type="submit"], button');
                        if (btn) {
                            btn.click();
                        } else if (targetInput.form) {
                            targetInput.form.submit();
                        }
                    }
                }, 250);

                return 'INJECTED';
            } catch (e) {
                return 'ERR:' + e.message;
            }
        })();
        """.trimIndent()
    }

    fun buildTouchToLearnScript(): String = """
        (function() {
            document.addEventListener('click', function(e) {
                var el = e.target;
                if (el && el.tagName && el.tagName.toLowerCase() === 'input') {
                    e.preventDefault();
                    e.stopPropagation();
                    el.style.outline = '3px solid #10B981';
                    var selector = '';
                    if (el.id) {
                        selector = '#' + el.id;
                    } else if (el.name) {
                        selector = 'input[name="' + el.name + '"]';
                    } else {
                        selector = 'input[type="' + (el.type || 'text') + '"]';
                    }
                    if (window.KrotyBridge) {
                        window.KrotyBridge.onSelectorLearned(selector);
                    }
                }
            }, true);
        })();
    """.trimIndent()

    fun buildPageScrapeScript(): String = """
        (function() {
            try {
                var html = document.documentElement ? document.documentElement.innerHTML : '';
                if (window.KrotyBridge) {
                    window.KrotyBridge.onPageHtmlScraped(window.location.href, html);
                }
            } catch (e) {}
        })();
    """.trimIndent()
}
""")

# 5. SmartVoucherOcrParser.kt & MultiFrameConsensusTracker.kt & KrotyCameraAnalyzer.kt (100% Real CameraX + ML Kit)
write_file("app/src/main/java/com/example/domain/scanner/SmartVoucherOcrParser.kt", r"""
package com.example.domain.scanner

import com.google.mlkit.vision.text.Text

data class ScannedVoucherCandidate(
    val code: String,
    val detectedPrice: Double? = null,
    val detectedQuotaMb: Long? = null,
    val score: Int = 0
)

object SmartVoucherOcrParser {

    private val blacklistKeywords = listOf(
        "s/n", "serial", "تسلسلي", "متسلسل", "خدمة العملاء", "للتواصل",
        "جوال", "هاتف", "تلفون", "واتس", "سعر", "ريال", "تاريخ", "صلاحية"
    )
    private val positiveKeywords = listOf(
        "رمز", "كود", "كرت", "بطاقة", "الكرت", "user", "username", "voucher", "pin", "code", "card"
    )
    private val phonePrefixes = listOf("77", "78", "71", "73", "70", "05", "967", "966")

    fun normalizeOpticalDigits(raw: String, isNumericOnly: Boolean): String {
        val cleaned = raw.trim().replace(" ", "").replace("-", "")
        if (!isNumericOnly) return cleaned
        val sb = StringBuilder()
        for (ch in cleaned) {
            when (ch) {
                'O', 'o', 'Q', 'D' -> sb.append('0')
                'I', 'l', '|', '!' -> sb.append('1')
                'Z', 'z' -> sb.append('2')
                'S', 's', '$' -> sb.append('5')
                'G', 'b' -> sb.append('6')
                'B' -> sb.append('8')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseMlKitText(
        visionText: Text,
        expectedLength: Int? = null,
        isNumericOnly: Boolean = true
    ): ScannedVoucherCandidate? {
        val fullText = visionText.text
        if (fullText.isBlank()) return null

        val price = extractPrice(fullText)
        val quotaMb = extractQuotaMb(fullText)

        var maxBoxHeight = 1
        val allLines = visionText.textBlocks.flatMap { it.lines }
        for (line in allLines) {
            val h = line.boundingBox?.height() ?: 1
            if (h > maxBoxHeight) maxBoxHeight = h
        }

        val candidates = mutableListOf<ScannedVoucherCandidate>()

        for (line in allLines) {
            val lineText = line.text.trim()
            val lowerLine = lineText.lowercase()
            if (blacklistKeywords.any { lowerLine.contains(it) }) continue

            val hasPositiveKeyword = positiveKeywords.any { lowerLine.contains(it) }
            val lineHeight = line.boundingBox?.height() ?: 10
            val fontScore = ((lineHeight.toFloat() / maxBoxHeight.toFloat()) * 40f).toInt()

            val tokens = Regex("""[A-Za-z0-9IlOQSZB|]{4,20}""").findAll(lineText).map { it.value }
            for (token in tokens) {
                val normalized = normalizeOpticalDigits(token, isNumericOnly)
                if (isNumericOnly && !normalized.all { it.isDigit() }) continue
                if (normalized.length !in 4..20) continue

                // Skip phone numbers unless explicitly matching expected length + positive keyword
                if ((normalized.length == 9 || normalized.length == 10) &&
                    phonePrefixes.any { normalized.startsWith(it) } &&
                    !hasPositiveKeyword &&
                    expectedLength != normalized.length
                ) {
                    continue
                }

                // If user locked a specific length on the scanner bar, strictly enforce it!
                if (expectedLength != null && expectedLength > 0 && normalized.length != expectedLength) {
                    continue
                }

                var score = 20 + fontScore
                if (expectedLength != null && normalized.length == expectedLength) score += 80
                if (hasPositiveKeyword) score += 50
                if (normalized.length in 6..14) score += 20

                candidates.add(
                    ScannedVoucherCandidate(
                        code = normalized,
                        detectedPrice = price,
                        detectedQuotaMb = quotaMb,
                        score = score
                    )
                )
            }
        }

        return candidates.maxByOrNull { it.score }
    }

    private fun extractPrice(text: String): Double? {
        val m = Regex("""(\d{2,5})\s*(?:ريال|ر\.ي|ر\.س|RY|YER|SAR)""", RegexOption.IGNORE_CASE).find(text)
        return m?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun extractQuotaMb(text: String): Long? {
        val m = Regex("""(\d+(?:\.\d+)?)\s*(GB|MB|جيجا|ميجا)""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val u = m.groupValues[2].lowercase()
        return if (u.contains("gb") || u.contains("جيجا")) (v * 1024).toLong() else v.toLong()
    }
}
""")

write_file("app/src/main/java/com/example/domain/scanner/MultiFrameConsensusTracker.kt", r"""
package com.example.domain.scanner

class MultiFrameConsensusTracker(private val requiredMatches: Int = 2) {
    private val window = ArrayDeque<ScannedVoucherCandidate>()
    private val maxWindowSize = 5

    fun onCandidateDetected(candidate: ScannedVoucherCandidate): Pair<Float, ScannedVoucherCandidate?> {
        window.addLast(candidate)
        if (window.size > maxWindowSize) {
            window.removeFirst()
        }
        val count = window.count { it.code == candidate.code }
        val progress = (count.toFloat() / requiredMatches.toFloat()).coerceIn(0f, 1f)
        return if (count >= requiredMatches) {
            progress to candidate
        } else {
            progress to null
        }
    }

    fun reset() {
        window.clear()
    }
}
""")

write_file("app/src/main/java/com/example/domain/scanner/KrotyCameraAnalyzer.kt", r"""
package com.example.domain.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class KrotyCameraAnalyzer(
    private val getExpectedLength: () -> Int?,
    private val isNumericOnly: () -> Boolean,
    private val onLiveCandidate: (ScannedVoucherCandidate, Float, Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()
    private val consensusTracker = MultiFrameConsensusTracker(requiredMatches = 2)

    fun resetConsensus() = consensusTracker.reset()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // 1. Check QR / Barcode first (instant 100% confidence)
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val rawQr = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                if (!rawQr.isNullOrBlank()) {
                    val cleanCode = rawQr.removePrefix("KROTY_VOUCHER:").trim()
                    if (cleanCode.length in 4..24) {
                        val candidate = ScannedVoucherCandidate(code = cleanCode, score = 200)
                        onLiveCandidate(candidate, 1.0f, true)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }
                }

                // 2. Run OCR Text Recognition on the camera frame
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val candidate = SmartVoucherOcrParser.parseMlKitText(
                            visionText = visionText,
                            expectedLength = getExpectedLength(),
                            isNumericOnly = isNumericOnly()
                        )
                        if (candidate != null) {
                            val (progress, confirmed) = consensusTracker.onCandidateDetected(candidate)
                            onLiveCandidate(candidate, progress, confirmed != null)
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }

    companion object {
        fun scanGalleryUri(
            context: Context,
            uri: Uri,
            expectedLength: Int?,
            onResult: (ScannedVoucherCandidate?) -> Unit
        ) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val parsed = SmartVoucherOcrParser.parseMlKitText(text, expectedLength, true)
                        onResult(parsed)
                    }
                    .addOnFailureListener {
                        onResult(null)
                    }
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }
}
""")

# 6. Notifications, Background Worker, Widget Provider, and Quick Settings Tile
write_file("app/src/main/java/com/example/worker/KrotyNotificationHelper.kt", r"""
package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R

object KrotyNotificationHelper {
    private const val CHANNEL_ID = "kroty_alerts_channel"

    private fun ensureChannel(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيهات كروتي نت والرصيد",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        return nm
    }

    fun showNotification(context: Context, id: Int, title: String, message: String) {
        try {
            val nm = ensureChannel(context)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
            nm.notify(id, notif)
        } catch (_: Exception) {}
    }
}
""")

write_file("app/src/main/java/com/example/widget/KrotySmartWidgetProvider.kt", r"""
package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.KrotyDatabase
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KrotySmartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_BALANCE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val radar = WifiRadarEngine(context)
                    val prober = CaptivePortalProber(radar)
                    val probe = prober.probeCurrentNetwork()
                    val db = KrotyDatabase.getInstance(context).krotyDao()
                    val active = db.getActiveVoucherNow()
                    if (probe.liveStatus != null && active != null) {
                        db.upsertVoucher(
                            active.copy(
                                remainingMegabytes = probe.liveStatus.remainingMegabytes ?: active.remainingMegabytes,
                                remainingTimeSeconds = probe.liveStatus.remainingTimeSeconds ?: active.remainingTimeSeconds,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    updateAllWidgets(context)
                }
            }
            ACTION_TOGGLE_FREEZE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = KrotyDatabase.getInstance(context).krotyDao()
                    val radar = WifiRadarEngine(context)
                    val prober = CaptivePortalProber(radar)
                    val wifi = radar.refreshNow()
                    val active = db.getActiveVoucherNow()
                    if (active != null && wifi.gatewayIp.isNotBlank()) {
                        prober.sendLogoutRequest("http://${wifi.gatewayIp}/logout")
                        db.upsertVoucher(active.copy(status = "PAUSED"))
                    } else {
                        val paused = db.getPausedVoucherNow()
                        if (paused != null) {
                            db.upsertVoucher(paused.copy(status = "ACTIVE"))
                        }
                    }
                    updateAllWidgets(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_BALANCE = "com.example.widget.ACTION_REFRESH_BALANCE"
        const val ACTION_TOGGLE_FREEZE = "com.example.widget.ACTION_TOGGLE_FREEZE"

        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, KrotySmartWidgetProvider::class.java))
                    if (ids.isEmpty()) return@launch

                    val db = KrotyDatabase.getInstance(context).krotyDao()
                    val radar = WifiRadarEngine(context)
                    val wifi = radar.refreshNow()
                    val active = db.getActiveVoucherNow() ?: db.getPausedVoucherNow()

                    val views = RemoteViews(context.packageName, R.layout.widget_kroty_smart)
                    views.setTextViewText(R.id.widget_network_name, wifi.ssid)
                    views.setTextViewText(
                        R.id.widget_status_badge,
                        if (wifi.isConnectedToWifi) "متصل (${wifi.gatewayIp})" else "غير متصل"
                    )

                    if (active != null) {
                        val masked = if (active.code.length > 4) "${active.code.take(2)}****${active.code.takeLast(2)}" else active.code
                        views.setTextViewText(R.id.widget_card_info, "الكرت: $masked (${if (active.status == "PAUSED") "مجمد ❄️" else "نشط ✅"})")
                        val mbText = active.remainingMegabytes?.let { String.format("%.1f MB", it) } ?: "اضغط تحديث"
                        views.setTextViewText(R.id.widget_balance_text, "الرصيد المتبقي: $mbText")
                    } else {
                        views.setTextViewText(R.id.widget_card_info, "الكرت النشط: لا يوجد كرت مسجل")
                        views.setTextViewText(R.id.widget_balance_text, "افتح التطبيق أو امسح كرتاً جديداً")
                    }

                    val refreshIntent = Intent(context, KrotySmartWidgetProvider::class.java).apply { action = ACTION_REFRESH_BALANCE }
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_refresh,
                        PendingIntent.getBroadcast(context, 101, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )

                    val freezeIntent = Intent(context, KrotySmartWidgetProvider::class.java).apply { action = ACTION_TOGGLE_FREEZE }
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_freeze,
                        PendingIntent.getBroadcast(context, 102, freezeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )

                    val appIntent = Intent(context, MainActivity::class.java)
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_scan,
                        PendingIntent.getActivity(context, 103, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )

                    for (id in ids) {
                        manager.updateAppWidget(id, views)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
""")

write_file("app/src/main/java/com/example/worker/SmartBalanceWorker.kt", r"""
package com.example.worker

import android.content.Context
import androidx.work.*
import com.example.data.local.KrotyDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.KrotyRepository
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine
import com.example.widget.KrotySmartWidgetProvider
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SmartBalanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = KrotyDatabase.getInstance(applicationContext)
            val repo = KrotyRepository(db.krotyDao())
            val prefs = UserPreferencesRepository(applicationContext)
            val radar = WifiRadarEngine(applicationContext)
            val prober = CaptivePortalProber(radar)

            val wifi = radar.refreshNow()
            if (!wifi.isConnectedToWifi || wifi.gatewayIp.isBlank()) {
                return Result.success()
            }

            val probe = prober.probeCurrentNetwork()
            val live = probe.liveStatus
            if (live != null && (live.remainingMegabytes != null || live.activeUsername != null)) {
                repo.syncLivePortalSession(
                    detectedUsername = live.activeUsername,
                    remainingMb = live.remainingMegabytes,
                    remainingSec = live.remainingTimeSeconds,
                    networkSsid = wifi.ssid
                )
                val alertLimit = prefs.lowBalanceAlertMb.first()
                val remMb = live.remainingMegabytes
                if (remMb != null && remMb <= alertLimit.toDouble()) {
                    KrotyNotificationHelper.showNotification(
                        applicationContext,
                        2001,
                        "⚠️ تنبيه انخفاض رصيد الكرت",
                        "الرصيد المتبقي في شبكتك (${wifi.ssid}) هو ${String.format("%.1f", remMb)} ميجابايت فقط."
                    )
                }
            }
            KrotySmartWidgetProvider.updateAllWidgets(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SmartBalanceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "kroty_periodic_balance_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun runOnceNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SmartBalanceWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
""")

write_file("app/src/main/java/com/example/service/KrotyFreezeTileService.kt", r"""
package com.example.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.data.local.KrotyDatabase
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine
import com.example.widget.KrotySmartWidgetProvider
import com.example.worker.KrotyNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KrotyFreezeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        CoroutineScope(Dispatchers.IO).launch {
            val dao = KrotyDatabase.getInstance(applicationContext).krotyDao()
            val radar = WifiRadarEngine(applicationContext)
            val prober = CaptivePortalProber(radar)
            val wifi = radar.refreshNow()

            val active = dao.getActiveVoucherNow()
            if (active != null) {
                if (wifi.gatewayIp.isNotBlank()) {
                    prober.sendLogoutRequest("http://${wifi.gatewayIp}/logout")
                }
                dao.upsertVoucher(active.copy(status = "PAUSED", lastSyncedAt = System.currentTimeMillis()))
                KrotyNotificationHelper.showNotification(
                    applicationContext,
                    3001,
                    "❄️ تم تجميد الكرت بنجاح",
                    "تم تسجيل الخروج المؤقت من البوابة (${wifi.gatewayIp}) لحفظ وقت ورصيد كرتك."
                )
            } else {
                val paused = dao.getPausedVoucherNow()
                if (paused != null) {
                    dao.upsertVoucher(paused.copy(status = "ACTIVE", lastSyncedAt = System.currentTimeMillis()))
                    KrotyNotificationHelper.showNotification(
                        applicationContext,
                        3002,
                        "▶️ تم تفعيل الكرت",
                        "افتح كروتي نت لإتمام الدخول التلقائي في البوابة."
                    )
                }
            }
            KrotySmartWidgetProvider.updateAllWidgets(applicationContext)
            refreshTileState()
        }
    }

    private fun refreshTileState() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = KrotyDatabase.getInstance(applicationContext).krotyDao()
            val active = dao.getActiveVoucherNow()
            val paused = dao.getPausedVoucherNow()
            val tile = qsTile ?: return@launch

            if (active != null) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "تجميد الكرت ❄️"
            } else if (paused != null) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "استئناف الكرت ▶️"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "كروتي نت"
            }
            tile.updateTile()
        }
    }
}
""")
print("Part 2 generated successfully!")
