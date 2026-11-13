package com.example.ui.screens

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow


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
fun RadarScreen(
    viewModel: KrotyViewModel,
    onNavigateToScanner: () -> Unit
) {
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

@Composable
fun <T : View> KrotyCockpitAndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {}
) {
    var capturedWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var currentUrlDisplay by remember { mutableStateOf("http://10.0.0.1/login") }
    var isDesktopMode by remember { mutableStateOf(false) }

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
                        Brush.verticalGradient(
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
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = capturedWebView?.url ?: currentUrlDisplay,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                factory(ctx).also { view ->
                    if (view is android.webkit.WebView) {
                        capturedWebView = view
                        currentUrlDisplay = view.url ?: currentUrlDisplay
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
