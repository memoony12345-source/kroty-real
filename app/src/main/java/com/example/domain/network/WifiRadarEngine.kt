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
