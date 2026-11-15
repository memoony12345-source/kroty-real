package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.web.MikroTikJsScripts
import com.example.ui.KrotyViewModel

@Composable
fun RadarScreen(viewModel: KrotyViewModel,
    onNavigateToScanner: () -> Unit) {
    var showStandaloneForensicModal by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        RadarScreenOriginal(viewModel, onNavigateToScanner)

        ExtendedFloatingActionButton(
            onClick = { showStandaloneForensicModal = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF00E5FF),
            contentColor = Color(0xFF0A0E17)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "🕵️‍♂️ استخراج الكرت الضائع",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
        }

        if (showStandaloneForensicModal) {
            StandaloneLostVoucherForensicDialog(
                onDismiss = { showStandaloneForensicModal = false }
            )
        }
    }
}

@Composable
private fun RadarScreenOriginal(viewModel: KrotyViewModel,
    onNavigateToScanner: () -> Unit) {
    val context = LocalContext.current
    val wifi by viewModel.wifiState.collectAsState()
    val session by viewModel.portalSession.collectAsState()
    val activeVoucher by viewModel.activeVoucher.collectAsState()

    var showPortalDialog by remember { mutableStateOf(false) }
    var initialPortalUrl by remember { mutableStateOf("") }

    val dynamicGateway = wifi.gatewayIp.ifBlank { session.currentNetwork?.gatewayIp.orEmpty() }
    val dynamicLoginUrl = session.currentNetwork?.loginUrl?.takeIf { it.isNotBlank() }
        ?: if (dynamicGateway.isNotBlank()) "http://$dynamicGateway/login" else ""
    val dynamicStatusUrl = session.currentNetwork?.statusUrl?.takeIf { it.isNotBlank() }
        ?: if (dynamicGateway.isNotBlank()) "http://$dynamicGateway/status" else ""

    LaunchedEffect(session.openWebPortalAutomatically, session.pendingAutoLoginCode) {
        if (session.openWebPortalAutomatically && !session.pendingAutoLoginCode.isNullOrBlank()) {
            initialPortalUrl = dynamicLoginUrl.ifBlank { "http://$dynamicGateway" }
            showPortalDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Location Permission / GPS Warning Banner if SSID is hidden by Android
        if (wifi.locationPermissionMissing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFBBF24))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تفعيل الموقع ضروري لقراءة اسم الشبكة (SSID)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("تم اكتشاف البوابة (${wifi.gatewayIp}) بنجاح، فعل GPS لإظهار اسم الشبكة.", color = Color(0xFFFDE68A), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text("تفعيل GPS", color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Main Dynamic Network Radar Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (wifi.isConnectedToWifi) Color(0xFF38BDF8) else Color.Gray,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = wifi.ssid,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (dynamicGateway.isNotBlank()) "بوابة الشبكة المكتشفة: $dynamicGateway • BSSID: ${wifi.bssid}"
                    else "يرجى الاتصال بشبكة Wi-Fi لاكتشاف البوابة تلقائياً",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF0F2942),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "الإشارة: ${wifi.signalPercent}% (${wifi.rssiDbm} dBm)",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = if (session.isInternetReachable) Color(0xFF064E3B) else Color(0xFF3B181E),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (session.isInternetReachable) "البوابة متصلة ✅" else "بانتظار الدخول 🔒",
                            color = if (session.isInternetReachable) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = session.statusMessage,
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }

        // Dual Ping Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مقياس النبض المزدوج (Dual Ping)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { viewModel.refreshEverything() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("بوابة الراوتر (${dynamicGateway.ifBlank { "---" }})", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = wifi.routerPingMs?.let { "$it ms" } ?: "غير متاح",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("الإنترنت العالمي (8.8.8.8)", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = wifi.internetPingMs?.let { "$it ms" } ?: "غير متصل",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (wifi.internetPingMs != null) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Real Active Voucher & Live Portal Balance Card (NO FAKE DATA)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الكرت النشط وإدارة الجلسة الحقيقية", fontWeight = FontWeight.Bold, fontSize = 17.sp)

                if (activeVoucher != null) {
                    val v = activeVoucher!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("رقم الكرت المتصل", fontSize = 12.sp, color = Color.Gray)
                            Text(v.code, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("الرصيد المتبقي الفعلي", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = v.remainingMegabytes?.let { "${String.format("%.1f", it)} MB" } ?: "افتح صفحة الرصيد للقراءة",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "لا يوجد كرت مسجل حالياً. اضغط (فتح صفحة الرصيد / البوابة) لقراءة الكرت المتصل حالياً من الشبكة، أو امسح كرتاً جديداً بالكاميرا.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.refreshEverything() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فحص الرصيد", maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { viewModel.freezeActiveVoucher() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تجميد الكرت", maxLines = 1, softWrap = false)
                    }
                }

                Button(
                    onClick = {
                        initialPortalUrl = if (dynamicStatusUrl.isNotBlank()) dynamicStatusUrl else "http://neverssl.com"
                        showPortalDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("فتح متصفح بوابة الشبكة (${dynamicGateway.ifBlank { "تلقائي" }})")
                }

                OutlinedButton(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("مسح كرت جديد بالكاميرا الحقيقية 📷")
                }
            }
        }

        // Dynamic Portal Endpoints Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("روابط البوابة المكتشفة ديناميكياً للشبكة الحالية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("عنوان البوابة (Gateway): ${dynamicGateway.ifBlank { "بانتظار الواي فاي" }}", fontSize = 13.sp)
                Text("رابط الدخول: ${dynamicLoginUrl.ifBlank { "---" }}", fontSize = 13.sp, color = Color(0xFF38BDF8))
                Text("رابط الرصيد: ${dynamicStatusUrl.ifBlank { "---" }}", fontSize = 13.sp, color = Color(0xFF10B981))
                Text(
                    "محدد الحقل المتعلم: ${session.currentNetwork?.learnedInputSelector ?: "تلقائي (الأوزان الذكية)"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }

    if (showPortalDialog) {
        RealPortalWebViewDialog(
            viewModel = viewModel,
            gatewayIp = dynamicGateway,
            startUrl = initialPortalUrl.ifBlank {
                if (dynamicGateway.isNotBlank()) "http://$dynamicGateway/login" else "http://neverssl.com"
            },
            autoInjectCode = session.pendingAutoLoginCode,
            learnedSelector = session.currentNetwork?.learnedInputSelector,
            passwordMode = session.currentNetwork?.passwordMode ?: "EMPTY",
            onDismiss = {
                viewModel.wifiRadar.bindProcessToWifi(false)
                viewModel.clearPendingAutoLogin()
                showPortalDialog = false
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealPortalWebViewDialog(
    viewModel: KrotyViewModel,
    gatewayIp: String,
    startUrl: String,
    autoInjectCode: String?,
    learnedSelector: String?,
    passwordMode: String,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf(startUrl) }
    var isLoading by remember { mutableStateOf(true) }
    var touchLearnMode by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Bind process to Wi-Fi while portal dialog is open so 3G/4G doesn't hijack local router traffic!
    DisposableEffect(Unit) {
        viewModel.wifiRadar.bindProcessToWifi(true)
        onDispose {
            viewModel.wifiRadar.bindProcessToWifi(false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B1120)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top URL & Controls Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF162032))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("رابط بوابة الشبكة", maxLines = 1, softWrap = false) }
                        )
                        IconButton(onClick = {
                            val target = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                            webViewRef?.loadUrl(target)
                        }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "انتقال", tint = Color(0xFF38BDF8))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFFEF4444))
                        }
                    }

                    // Quick Action Chips for ANY Gateway
                    val baseHost = if (gatewayIp.isNotBlank()) "http://$gatewayIp" else "http://10.0.0.1"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val u = "$baseHost/status"
                                urlInput = u
                                webViewRef?.loadUrl(u)
                            },
                            label = { Text("الرصيد /status", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                val u = "$baseHost/login"
                                urlInput = u
                                webViewRef?.loadUrl(u)
                            },
                            label = { Text("الدخول /login", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                val u = "$baseHost/logout"
                                urlInput = u
                                webViewRef?.loadUrl(u)
                            },
                            label = { Text("خروج /logout", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                touchLearnMode = !touchLearnMode
                                if (touchLearnMode) {
                                    webViewRef?.evaluateJavascript(MikroTikJsScripts.buildTouchToLearnScript(), null)
                                }
                            },
                            label = {
                                Text(
                                    if (touchLearnMode) "إلمس المربع الآن 👆" else "تعليم المربع 👆",
                                    fontSize = 11.sp,
                                    color = if (touchLearnMode) Color(0xFF10B981) else Color.White
                                )
                            }
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF38BDF8))
                }

                // Real Full-Screen Android WebView
                KrotyCockpitAndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            CookieManager.getInstance().setAcceptCookie(true)

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onPageHtmlScraped(url: String, html: String) {
                                    viewModel.onWebPortalHtmlScraped(url, html)
                                }

                                @JavascriptInterface
                                fun onSelectorLearned(selector: String) {
                                    viewModel.saveLearnedInputSelector(selector)
                                }

                                @JavascriptInterface
                                fun onDetectedMaxLength(maxLen: Int) {
                                    viewModel.saveDetectedCardLength(maxLen)
                                }

                                @JavascriptInterface
                                fun onLoginFieldMissing() {}
                            }, "KrotyBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    if (!url.isNullOrBlank()) urlInput = url
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (!url.isNullOrBlank()) urlInput = url

                                    // Scrape page HTML to extract active voucher, balance, or errors
                                    view?.evaluateJavascript(MikroTikJsScripts.buildPageScrapeScript(), null)

                                    // Auto-inject voucher code if requested and on login page
                                    if (!autoInjectCode.isNullOrBlank() && (url?.contains("status", ignoreCase = true) != true)) {
                                        val js = MikroTikJsScripts.buildAutoLoginScript(
                                            voucherCode = autoInjectCode,
                                            password = "",
                                            passwordMode = passwordMode,
                                            learnedSelector = learnedSelector
                                        )
                                        view?.evaluateJavascript(js, null)
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(startUrl)
                        }
                    }
                )
            }
        }
    }
}

data class ForensicCandidateItem(
    val code: String,
    val sourceLayer: String,
    val confidence: Int,
    val rawDetail: String
)

private fun copyVoucherToClipboard(context: android.content.Context, voucherCode: String) {
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Kroty Lost Voucher", voucherCode)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "✅ تم نسخ الكرت: $voucherCode", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

private fun detectActiveGatewayUrl(context: android.content.Context): String {
    try {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork
        val lp = if (net != null) cm.getLinkProperties(net) else null
        if (lp != null) {
            for (route in lp.routes) {
                val gw = route.gateway?.hostAddress
                if (!gw.isNullOrBlank() && !gw.contains(":") && gw != "0.0.0.0") {
                    return "http://$gw"
                }
            }
        }
    } catch (_: Exception) {}
    return "http://10.0.0.1"
}

private class ForensicJsBridge(
    private val onReportJson: (String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun postForensicReport(jsonStr: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onReportJson(jsonStr)
        }
    }
}

private val DEEP_FORENSIC_JS_SCRIPT = """
(function() {
    async function runDeepForensicScan() {
        var findings = [];
        var partialMasks = [];
        var seen = {};

        function addFinding(code, layer, confidence, detail) {
            if (!code) return;
            var c = String(code).trim();
            try { c = decodeURIComponent(c).trim(); } catch(e) {}
            c = c.replace(/^["']+|["']+$/g, '').trim();
            if (c.length < 3 || c.length > 24) return;
            var lower = c.toLowerCase();
            var blacklist = [
                'true','false','null','undefined','none','http','https','html','body','text','hidden',
                'submit','button','password','username','user','login','status','logout','mikrotik',
                'routeros','hotspot','default','utf-8',' utf8','windows','android','iphone','script',
                'style','table','center','right','left','width','height','color','border','margin',
                'padding','arial','tahoma','cairo','sans-serif','monospace','function','return',
                'window','document','location','cookie','localstorage','sessionstorage','10.0.0.1',
                '192.168.','172.16.','255.255.','00:00:00','erase-cookie'
            ];
            for (var i = 0; i < blacklist.length; i++) {
                if (lower === blacklist[i] || lower.indexOf('http://') === 0 || lower.indexOf('https://') === 0) return;
            }
            if (/^[a-zA-Z_]+$/.test(c) && !/\d/.test(c) && confidence < 90) return;
            if (/^\d+\.\d+\.\d+\.\d+$/.test(c)) return;
            if (/^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/.test(c)) return;

            if (/[\*\•\…]{1,}/.test(c) && /\d/.test(c)) {
                partialMasks.push(c);
            }

            var key = c + "|" + layer;
            if (seen[key]) return;
            seen[key] = true;
            findings.push({
                code: c,
                layer: layer,
                confidence: confidence,
                detail: String(detail || '').substring(0, 90)
            });
        }

        // Layer 1: Document Cookies
        try {
            var rawCookies = document.cookie || '';
            if (rawCookies) {
                var parts = rawCookies.split(';');
                for (var i = 0; i < parts.length; i++) {
                    var kv = parts[i].split('=');
                    if (kv.length >= 2) {
                        var k = kv[0].trim();
                        var v = kv.slice(1).join('=').trim();
                        var kl = k.toLowerCase();
                        var score = (kl.indexOf('user') !== -1 || kl.indexOf('login') !== -1 || kl.indexOf('voucher') !== -1 || kl.indexOf('card') !== -1 || kl.indexOf('code') !== -1 || kl.indexOf('uname') !== -1) ? 98 : 82;
                        addFinding(v, '🍪 الكوكيز (Cookies)', score, 'Cookie: ' + k);
                    }
                }
            }
        } catch(e) {}

        // Layer 2: LocalStorage & SessionStorage
        try {
            for (var i = 0; i < localStorage.length; i++) {
                var lk = localStorage.key(i);
                var lv = localStorage.getItem(lk);
                addFinding(lv, '💾 التخزين المحلي (LocalStorage)', 94, 'Key: ' + lk);
            }
        } catch(e) {}
        try {
            for (var i = 0; i < sessionStorage.length; i++) {
                var sk = sessionStorage.key(i);
                var sv = sessionStorage.getItem(sk);
                addFinding(sv, '💾 ذاكرة الجلسة (SessionStorage)', 94, 'Key: ' + sk);
            }
        } catch(e) {}

        function scanHtmlSource(htmlText, sourceTag) {
            if (!htmlText) return;

            // Layer 3: HTML Comments <!-- ... --> (Catches commented-out $(username) rows!)
            var commentRegex = /<!--([\\s\\S]*?)-->/g;
            var cm;
            while ((cm = commentRegex.exec(htmlText)) !== null) {
                var commentBody = cm[1];
                var numMatches = commentBody.match(/\\b[0-9A-Za-z]{4,16}\\b/g) || [];
                for (var j = 0; j < numMatches.length; j++) {
                    if (/\\d{3,}/.test(numMatches[j])) {
                        addFinding(numMatches[j], '🕵️ تعليقات السورس المخفية (' + sourceTag + ')', 95, 'داخل <!-- تعليق مخفي -->');
                    }
                }
            }

            // Layer 4: Inline JS Variables & Pre-Masking Strings
            var jsVarRegex = /(?:user(?:name)?|voucher|card|login|uname|code|account|member|id_card)\\s*[:=]\\s*["']([^"'\\r\\n]{3,20})["']/gi;
            var jm;
            while ((jm = jsVarRegex.exec(htmlText)) !== null) {
                if (jm[1].indexOf('$(') === -1) {
                    addFinding(jm[1], '📜 متغيرات السكربت (' + sourceTag + ')', 96, jm[0].substring(0, 60));
                }
            }

            // Catch strings right before .substring / .substr / .slice (Unmasking half-shown vouchers!)
            var sliceRegex = /["']([0-9A-Za-z]{4,20})["']\\s*\\.(?:substring|substr|slice|replace)\\s*\\(/g;
            var sm;
            while ((sm = sliceRegex.exec(htmlText)) !== null) {
                addFinding(sm[1], '🔓 كرت كامل قبل القص النصي (' + sourceTag + ')', 99, sm[0]);
            }

            // URL params like ?username=... or erase-cookie
            var urlParamRegex = /[?&](?:username|user|voucher|card|login|code)=([0-9A-Za-z%._-]{3,20})/gi;
            var um;
            while ((um = urlParamRegex.exec(htmlText)) !== null) {
                addFinding(um[1], '🔗 روابط الجلسة المخفية (' + sourceTag + ')', 93, um[0]);
            }

            // Input fields in raw HTML
            var inputRegex = /<input[^>]+name=["']?(?:username|user|voucher|card|code)["']?[^>]*value=["']([^"']{3,20})["']/gi;
            var im;
            while ((im = inputRegex.exec(htmlText)) !== null) {
                if (im[1].indexOf('$(') === -1) {
                    addFinding(im[1], '🔐 حقل إدخال مخفي (' + sourceTag + ')', 97, 'input value');
                }
            }
        }

        // Scan current DOM
        try {
            scanHtmlSource(document.documentElement.outerHTML, 'الصفحة الحالية');
        } catch(e) {}

        // Layer 5: Live DOM Inputs, Hidden Elements & Table Labels
        try {
            var inputs = document.querySelectorAll('input, textarea, select, [data-user], [data-username], [data-voucher]');
            for (var i = 0; i < inputs.length; i++) {
                var el = inputs[i];
                var val = el.value || el.getAttribute('value') || el.getAttribute('data-user') || el.getAttribute('data-username') || el.getAttribute('data-voucher') || '';
                var nameAttr = (el.name || el.id || el.className || '').toLowerCase();
                if (val && val.length >= 3) {
                    var sc = (nameAttr.indexOf('user') !== -1 || nameAttr.indexOf('voucher') !== -1 || nameAttr.indexOf('card') !== -1 || nameAttr.indexOf('login') !== -1) ? 97 : 84;
                    addFinding(val, '🔐 عناصر الصفحة المخفية (DOM)', sc, 'Element: ' + (el.name || el.id || el.tagName));
                }
            }

            var cells = document.querySelectorAll('td, th, span, div, p, b, strong, h1, h2, h3, h4, a');
            var labelKeywords = ['اسم المستخدم', 'المستخدم', 'رقم الكرت', 'رمز الكرت', 'الكرت', 'الرمز', 'كود', 'مرحبا', 'أهلا', 'username', 'user', 'voucher', 'card', 'account', 'logged in'];
            for (var i = 0; i < cells.length; i++) {
                var txt = (cells[i].textContent || '').trim();
                if (!txt || txt.length > 100) continue;
                var txtLower = txt.toLowerCase();
                for (var k = 0; k < labelKeywords.length; k++) {
                    if (txtLower.indexOf(labelKeywords[k]) !== -1) {
                        var nextEl = cells[i].nextElementSibling;
                        if (nextEl && nextEl.textContent) {
                            addFinding(nextEl.textContent.trim(), '📊 جدول بيانات الجلسة', 92, 'بجانب: ' + labelKeywords[k]);
                        }
                        var afterColon = txt.split(/[:：]/);
                        if (afterColon.length >= 2) {
                            addFinding(afterColon[afterColon.length - 1].trim(), '📊 بيانات الجلسة المباشرة', 91, txt);
                        }
                    }
                }
            }
        } catch(e) {}

        // Layer 6: Global Window JS Properties
        try {
            var winKeys = Object.keys(window);
            for (var i = 0; i < winKeys.length; i++) {
                var wk = winKeys[i];
                var wkl = wk.toLowerCase();
                if (wkl.indexOf('user') !== -1 || wkl.indexOf('voucher') !== -1 || wkl.indexOf('card') !== -1 || wkl.indexOf('login') !== -1 || wkl.indexOf('uname') !== -1) {
                    var wv = window[wk];
                    if (typeof wv === 'string' || typeof wv === 'number') {
                        addFinding(String(wv), '⚡ ذاكرة الجافاسكربت الحية (window)', 96, 'window.' + wk);
                    }
                }
            }
        } catch(e) {}

        // Layer 7: Background Multi-Endpoint Fetch (/status, /login, /status.html, /alogin.html)
        var endpoints = ['/status', '/login', '/status.html', '/alogin.html'];
        for (var eIdx = 0; eIdx < endpoints.length; eIdx++) {
            try {
                var ep = endpoints[eIdx];
                var resp = await fetch(ep, { credentials: 'include', cache: 'no-store' });
                if (resp && resp.ok) {
                    var rawText = await resp.text();
                    scanHtmlSource(rawText, ep);
                }
            } catch(err) {}
        }

        // Layer 8: Partial Code Unmasking Cross-Match
        if (partialMasks.length > 0) {
            for (var p = 0; p < partialMasks.length; p++) {
                var maskStr = partialMasks[p];
                var cleanPrefix = maskStr.replace(/[\\*\\•\\…]+.*$/, '');
                var cleanSuffix = maskStr.replace(/^.*[\\*\\•\\…]+/, '');
                for (var f = 0; f < findings.length; f++) {
                    var cand = findings[f].code;
                    if (cand !== maskStr && cand.indexOf('*') === -1) {
                        var matchPre = cleanPrefix.length > 0 && cand.indexOf(cleanPrefix) === 0;
                        var matchSuf = cleanSuffix.length > 0 && cand.slice(-cleanSuffix.length) === cleanSuffix;
                        if (matchPre || matchSuf) {
                            findings[f].confidence = 100;
                            findings[f].layer = '🎯 تطابق كامل مع الكرت المقصوص (' + maskStr + ')';
                        }
                    }
                }
            }
        }

        findings.sort(function(a, b) { return b.confidence - a.confidence; });
        var payload = JSON.stringify({
            url: window.location.href,
            partials: partialMasks,
            items: findings
        });
        if (window.KrotyForensicBridge && window.KrotyForensicBridge.postForensicReport) {
            window.KrotyForensicBridge.postForensicReport(payload);
        }
    }
    runDeepForensicScan();
})();
""".trimIndent()

private fun parseForensicResults(
    context: android.content.Context,
    targetUrl: String,
    jsonStr: String
): Pair<List<String>, List<ForensicCandidateItem>> {
    val results = mutableListOf<ForensicCandidateItem>()
    val partials = mutableListOf<String>()
    val seenCodes = mutableSetOf<String>()

    // Native Android CookieManager inspection (captures HttpOnly & root gateway cookies!)
    try {
        val cm = android.webkit.CookieManager.getInstance()
        val urlsToCheck = listOf(
            targetUrl,
            detectActiveGatewayUrl(context),
            "${detectActiveGatewayUrl(context)}/status",
            "${detectActiveGatewayUrl(context)}/login"
        )
        for (u in urlsToCheck) {
            val cookieStr = cm.getCookie(u)
            if (!cookieStr.isNullOrBlank()) {
                for (part in cookieStr.split(";")) {
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) {
                        val k = kv[0].trim()
                        val v = java.net.URLDecoder.decode(kv[1].trim(), "UTF-8").trim()
                        val kl = k.lowercase()
                        if (v.length in 3..24 && !v.equals("true", true) && !v.equals("false", true)) {
                            val isVoucherKey = kl.contains("user") || kl.contains("login") || kl.contains("voucher") || kl.contains("card") || kl.contains("code") || kl.contains("uname")
                            val conf = if (isVoucherKey) 99 else 85
                            if (seenCodes.add("$v|native_cookie")) {
                                results.add(
                                    ForensicCandidateItem(
                                        code = v,
                                        sourceLayer = "🍪 كوكيز النظام العميقة (Native CookieManager)",
                                        confidence = conf,
                                        rawDetail = "Cookie [$k] on $u"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}

    try {
        val root = org.json.JSONObject(jsonStr)
        val pArr = root.optJSONArray("partials")
        if (pArr != null) {
            for (i in 0 until pArr.length()) {
                val p = pArr.optString(i)
                if (p.isNotBlank() && !partials.contains(p)) partials.add(p)
            }
        }
        val arr = root.optJSONArray("items")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val c = obj.optString("code").trim()
                val l = obj.optString("layer")
                val conf = obj.optInt("confidence", 80)
                val det = obj.optString("detail")
                if (c.isNotBlank() && seenCodes.add("$c|$l")) {
                    results.add(ForensicCandidateItem(c, l, conf, det))
                }
            }
        }
    } catch (_: Exception) {}

    return partials to results.sortedByDescending { it.confidence }
}

@Composable
fun DeepForensicInspectorPanel(
    candidates: List<ForensicCandidateItem>,
    partialMasks: List<String>,
    isScanning: Boolean,
    onRescan: () -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var partialFilterInput by remember { mutableStateOf("") }

    val filteredCandidates = remember(candidates, partialFilterInput) {
        if (partialFilterInput.isBlank()) {
            candidates
        } else {
            val query = partialFilterInput.trim()
            candidates.filter { it.code.contains(query, ignoreCase = true) }
        }
    }

    val bestUnmaskedCandidate = remember(filteredCandidates) {
        filteredCandidates.firstOrNull { !it.code.contains("*") && !it.code.contains("•") }
            ?: filteredCandidates.firstOrNull()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🕵️‍♂️ الفحص الجنائي العميق للكرت الضائع",
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRescan, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة الفحص", tint = Color(0xFF00E5FF))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }
            }

            if (isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "جاري فحص الكوكيز، السكربتات، تعليقات السورس، وصفحات /status و /login...",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                }
            }

            if (partialMasks.isNotEmpty()) {
                Surface(
                    color = Color(0xFFFFB300).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🧩 تم رصد كرت مقصوص في الشاشة (${partialMasks.joinToString(" , ")}) — جاري مطابقته مع السورس العميق!",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (bestUnmaskedCandidate != null) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 الكرت المستخرج للجلسة الحالية:",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "ثقة ${bestUnmaskedCandidate.confidence}%",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bestUnmaskedCandidate.code,
                                color = Color(0xFFF8FAFC),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                            Button(
                                onClick = { copyVoucherToClipboard(context, bestUnmaskedCandidate.code) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("📋 نسخ الكرت", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }

                        Text(
                            text = "المصدر: ${bestUnmaskedCandidate.sourceLayer} (${bestUnmaskedCandidate.rawDetail})",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (!isScanning) {
                Text(
                    text = "لم يتم العثور على كرت مباشر بعد. اضغط على (🔑 صفحة الدخول) أو (📊 صفحة الرصيد) بالأسفل ثم اضغط تحديث الفحص.",
                    color = Color(0xFFFFB300),
                    fontSize = 11.sp
                )
            }

            OutlinedTextField(
                value = partialFilterInput,
                onValueChange = { partialFilterInput = it },
                label = { Text("هل يظهر نصف الكرت؟ اكتب الأرقام الظاهرة هنا لمطابقتها", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (filteredCandidates.size > 1) {
                Text(
                    text = "كافة النتائج المكتشفة في الطبقات العميقة (${filteredCandidates.size}):",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredCandidates.forEach { item ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.code,
                                        color = Color(0xFF00E5FF),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${item.sourceLayer} • ${item.rawDetail}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { copyVoucherToClipboard(context, item.code) }) {
                                    Text("نسخ", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StandaloneLostVoucherForensicDialog(
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseUrl = remember { detectActiveGatewayUrl(context) }
    var probeWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    var candidates by remember { mutableStateOf<List<ForensicCandidateItem>>(emptyList()) }
    var partialMasks by remember { mutableStateOf<List<String>>(emptyList()) }

    val triggerForensicScan: (android.webkit.WebView?) -> Unit = { wv ->
        if (wv != null) {
            isScanning = true
            wv.evaluateJavascript(DEEP_FORENSIC_JS_SCRIPT, null)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E17))
                .padding(8.dp)
        ) {
            DeepForensicInspectorPanel(
                candidates = candidates,
                partialMasks = partialMasks,
                isScanning = isScanning,
                onRescan = { triggerForensicScan(probeWebView) },
                onClose = onDismiss
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { probeWebView?.loadUrl("$baseUrl/status") },
                    label = { Text("📊 فحص /status", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                )
                AssistChip(
                    onClick = { probeWebView?.loadUrl("$baseUrl/login") },
                    label = { Text("🔑 فحص /login", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                )
                AssistChip(
                    onClick = { probeWebView?.loadUrl("$baseUrl/alogin.html") },
                    label = { Text("⚡ فحص /alogin.html", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                )
            }

            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp)),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        addJavascriptInterface(
                            ForensicJsBridge { jsonStr ->
                                val (p, c) = parseForensicResults(ctx, url ?: baseUrl, jsonStr)
                                partialMasks = p
                                candidates = c
                                isScanning = false
                            },
                            "KrotyForensicBridge"
                        )
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                triggerForensicScan(view)
                            }
                        }
                        probeWebView = this
                        loadUrl("$baseUrl/status")
                    }
                }
            )
        }
    }
}

@Composable
fun <T : android.view.View> KrotyCockpitAndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var capturedWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var currentUrlDisplay by remember { mutableStateOf("http://10.0.0.1/login") }
    var isDesktopMode by remember { mutableStateOf(false) }

    var showForensicDrawer by remember { mutableStateOf(false) }
    var isForensicScanning by remember { mutableStateOf(false) }
    var forensicCandidates by remember { mutableStateOf<List<ForensicCandidateItem>>(emptyList()) }
    var forensicPartials by remember { mutableStateOf<List<String>>(emptyList()) }
    var bridgeAttached by remember { mutableStateOf(false) }

    val runForensicOnCapturedWebView: () -> Unit = {
        val wv = capturedWebView
        if (wv != null) {
            showForensicDrawer = true
            isForensicScanning = true
            val (nativePartials, nativeItems) = parseForensicResults(
                context,
                wv.url ?: currentUrlDisplay,
                "{}"
            )
            if (nativeItems.isNotEmpty()) {
                forensicPartials = nativePartials
                forensicCandidates = nativeItems
            }
            wv.evaluateJavascript(DEEP_FORENSIC_JS_SCRIPT, null)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0A0E17))) {
        Surface(
            color = Color(0xFF0F172A),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF162238), Color(0xFF0B1120))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { if (capturedWebView?.canGoBack() == true) capturedWebView?.goBack() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "رجوع",
                            tint = Color(0xFFF8FAFC),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentUrlDisplay = capturedWebView?.url ?: currentUrlDisplay
                            capturedWebView?.reload()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D16))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = capturedWebView?.url ?: currentUrlDisplay,
                            color = Color(0xFF00E5FF),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(
                        onClick = {
                            isDesktopMode = !isDesktopMode
                            capturedWebView?.settings?.apply {
                                userAgentString = if (isDesktopMode) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                } else {
                                    null
                                }
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            capturedWebView?.reload()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDesktopMode) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF1E293B))
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = "وضع الكمبيوتر",
                            tint = if (isDesktopMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val extractHost: () -> String = {
                        val u = capturedWebView?.url ?: "http://10.0.0.1"
                        try {
                            val uri = android.net.Uri.parse(u)
                            if (!uri.host.isNullOrBlank()) "${uri.scheme ?: "http"}://${uri.host}" else "http://10.0.0.1"
                        } catch (_: Exception) {
                            "http://10.0.0.1"
                        }
                    }

                    AssistChip(
                        onClick = { runForensicOnCapturedWebView() },
                        label = {
                            Text("🕵️‍♂️ كشف الكرت الضائع (فحص عميق)", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF00E676).copy(alpha = 0.2f),
                            labelColor = Color(0xFF00E676)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFF00E676))
                    )

                    AssistChip(
                        onClick = {
                            val target = "${extractHost()}/status"
                            currentUrlDisplay = target
                            capturedWebView?.loadUrl(target)
                        },
                        label = {
                            Text("📊 صفحة الرصيد (/status)", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF00E5FF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    )

                    AssistChip(
                        onClick = {
                            val target = "${extractHost()}/login"
                            currentUrlDisplay = target
                            capturedWebView?.loadUrl(target)
                        },
                        label = {
                            Text("🔑 صفحة الدخول (/login)", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF00E676)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                    )

                    AssistChip(
                        onClick = {
                            val target = "${extractHost()}/logout"
                            currentUrlDisplay = target
                            capturedWebView?.loadUrl(target)
                        },
                        label = {
                            Text("❄️ تجميد الكرت (/logout)", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFFF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                    )

                    AssistChip(
                        onClick = {
                            capturedWebView?.loadUrl("http://neverssl.com")
                        },
                        label = {
                            Text("🌐 كشف بوابة الشبكة", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFFFB300)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f))
                    )
                }
            }
        }

        if (showForensicDrawer) {
            DeepForensicInspectorPanel(
                candidates = forensicCandidates,
                partialMasks = forensicPartials,
                isScanning = isForensicScanning,
                onRescan = { runForensicOnCapturedWebView() },
                onClose = { showForensicDrawer = false }
            )
        }

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                factory(ctx).also { view ->
                    if (view is android.webkit.WebView) {
                        capturedWebView = view
                        currentUrlDisplay = view.url ?: currentUrlDisplay
                        if (!bridgeAttached) {
                            try {
                                view.settings.domStorageEnabled = true
                                view.addJavascriptInterface(
                                    ForensicJsBridge { jsonStr ->
                                        val (p, c) = parseForensicResults(
                                            ctx,
                                            view.url ?: currentUrlDisplay,
                                            jsonStr
                                        )
                                        forensicPartials = p
                                        forensicCandidates = c
                                        isForensicScanning = false
                                    },
                                    "KrotyForensicBridge"
                                )
                                bridgeAttached = true
                            } catch (_: Exception) {}
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { view ->
                if (view is android.webkit.WebView) {
                    capturedWebView = view
                    if (!view.url.isNullOrBlank()) {
                        currentUrlDisplay = view.url ?: currentUrlDisplay
                    }
                }
                update(view)
            }
        )
    }
}
