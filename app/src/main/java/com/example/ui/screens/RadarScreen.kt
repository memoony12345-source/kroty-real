package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.VoucherEntity
import com.example.domain.radar.WifiTelemetry
import com.example.domain.web.MikroTikJsScripts
import com.example.ui.KrotyViewModel
import com.example.ui.PortalUiState
import com.example.ui.theme.*

@Composable
fun RadarScreen(
    viewModel: KrotyViewModel,
    onRequestLocationEnable: () -> Unit
) {
    val telemetry by viewModel.wifiTelemetry.collectAsState()
    val activeVoucher by viewModel.activeVoucher.collectAsState()
    val allVouchers by viewModel.allVouchers.collectAsState()
    val portalState by viewModel.portalUiState.collectAsState()
    val networks by viewModel.allNetworks.collectAsState()

    val currentNetConfig = networks.firstOrNull { it.ssid == telemetry.ssid }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            portalState.statusBannerMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2942)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            color = KrotyCyan,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearBanner() }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = KrotyTextSecondary)
                        }
                    }
                }
            }
        }

        if (telemetry.isLocationToggleRequired && telemetry.isWifiConnected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2510)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = KrotyAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل الموقع مطلوب لقراءة اسم الشبكة", color = KrotyAmber, fontWeight = FontWeight.Bold)
                            Text("نظام أندرويد يخفي اسم الـ SSID بدون تشغيل الـ GPS.", color = KrotyTextPrimary, fontSize = 12.sp)
                        }
                        TextButton(onClick = onRequestLocationEnable) {
                            Text("تفعيل الآن", color = KrotyCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            LiveWifiRadarCard(
                telemetry = telemetry,
                isProbing = portalState.isProbingSession,
                onRefreshRadar = { viewModel.syncLiveMikroTikSession(silent = false) }
            )
        }

        if (telemetry.isMacRandomized) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331B24)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = KrotyCoralRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("تنبيه: العنوان العشوائي (MAC Randomization) مفعل", color = KrotyCoralRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "قد يرفض سيرفر المايكروتك إعادة دخول الكرت إذا تغير عنوان MAC (${telemetry.localMacAddress}). يفضل ضبط MAC الهاتف كعنوان ثابت لهذه الشبكة.",
                                color = KrotyTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            LiveSessionAndBalanceCard(
                telemetry = telemetry,
                activeVoucher = activeVoucher,
                portalState = portalState,
                onOpenLoginPortal = {
                    viewModel.openRealPortal(targetPath = "/login", autoLoginVoucher = activeVoucher)
                },
                onOpenStatusPortal = {
                    viewModel.openRealPortal(targetPath = "/status")
                },
                onFreezeSession = {
                    viewModel.freezeActiveSessionNow()
                }
            )
        }

        item {
            PortalQuickActionsCard(
                gatewayIp = telemetry.gatewayIp,
                onOpenCustomUrl = { path -> viewModel.openRealPortal(targetPath = path) },
                onTouchToLearn = {
                    viewModel.openRealPortal(targetPath = "/login", enableTouchToLearn = true)
                },
                onRunWorkerSync = { viewModel.triggerBackgroundWorkerNow() }
            )
        }
    }

    if (portalState.isPortalDialogOpen) {
        RealPortalWebViewDialog(
            viewModel = viewModel,
            telemetry = telemetry,
            portalState = portalState,
            activeVoucher = activeVoucher,
            allVouchers = allVouchers,
            customSelector = currentNetConfig?.voucherFieldSelector ?: ""
        )
    }
}

@Composable
private fun LiveWifiRadarCard(
    telemetry: WifiTelemetry,
    isProbing: Boolean,
    onRefreshRadar: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isWifiConnected) KrotyCyan.copy(alpha = 0.15f) else KrotyCoralRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (telemetry.isWifiConnected) KrotyCyan else KrotyCoralRed
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = telemetry.ssid,
                            color = KrotyTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (telemetry.gatewayIp.isNotBlank()) {
                                "البوابة المكتشفة: ${telemetry.gatewayIp}"
                            } else {
                                "بانتظار اكتشاف بوابة الشبكة..."
                            },
                            color = KrotyCyan,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(onClick = onRefreshRadar) {
                    if (isProbing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = KrotyCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "مزامنة", tint = KrotyCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = KrotyCardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RadarMetricItem("قوة الإشارة", "${telemetry.signalQualityPercent}% (${telemetry.rssiDbm}dBm)", KrotyEmerald)
                RadarMetricItem(
                    "بنق الراوتر",
                    if (telemetry.gatewayPingMs >= 0) "${telemetry.gatewayPingMs} ms" else "--",
                    KrotyCyan
                )
                RadarMetricItem(
                    "بنق الإنترنت",
                    if (telemetry.internetPingMs >= 0) "${telemetry.internetPingMs} ms" else "بوابة كابتف",
                    if (telemetry.internetPingMs >= 0) KrotyEmerald else KrotyAmber
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "نقطة الوصول (BSSID): ${telemetry.bssid} • سرعة الربط: ${telemetry.linkSpeedMbps} Mbps",
                color = KrotyTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RadarMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = KrotyTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun LiveSessionAndBalanceCard(
    telemetry: WifiTelemetry,
    activeVoucher: VoucherEntity?,
    portalState: PortalUiState,
    onOpenLoginPortal: () -> Unit,
    onOpenStatusPortal: () -> Unit,
    onFreezeSession: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الجلسة الحية والرصيد الحقيقي",
                    color = KrotyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Surface(
                    color = if (activeVoucher != null) KrotyEmerald.copy(alpha = 0.15f) else KrotyAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (activeVoucher != null) "كرت نشط: ${activeVoucher.code}" else "لا يوجد كرت محفوظ نشط",
                        color = if (activeVoucher != null) KrotyEmerald else KrotyAmber,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الرصيد المتبقي (من البوابة)", color = KrotyTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeVoucher?.remainingMb?.let { String.format("%.1f MB", it) }
                            ?: portalState.liveRemainingMb,
                        color = KrotyCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الوقت المتبقي", color = KrotyTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = portalState.liveRemainingTime,
                        color = KrotyEmerald,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenLoginPortal,
                    enabled = telemetry.gatewayIp.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, tint = KrotyDarkBg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بوابة الدخول", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onOpenStatusPortal,
                    enabled = telemetry.gatewayIp.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = KrotyCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("صفحة الرصيد", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onFreezeSession,
                enabled = telemetry.gatewayIp.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KrotyAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = KrotyAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تجميد الكرت مؤقتاً (تسجيل خروج /logout لحفظ الرصيد)", color = KrotyAmber, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PortalQuickActionsCard(
    gatewayIp: String,
    onOpenCustomUrl: (String) -> Unit,
    onTouchToLearn: () -> Unit,
    onRunWorkerSync: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "أدوات التحكم المتقدمة بالبوابة (${gatewayIp.ifBlank { "غير متصل" }})",
                color = KrotyTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onTouchToLearn,
                    enabled = gatewayIp.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحديد الحقل باللمس", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onRunWorkerSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحديث الإشعار والويدجت", fontSize = 12.sp)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealPortalWebViewDialog(
    viewModel: KrotyViewModel,
    telemetry: WifiTelemetry,
    portalState: PortalUiState,
    activeVoucher: VoucherEntity?,
    allVouchers: List<VoucherEntity>,
    customSelector: String
) {
    var urlInput by remember(portalState.currentPortalUrl) { mutableStateOf(portalState.currentPortalUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("بوابة المايكروتك الذكية") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showQuickVoucherDrawer by remember { mutableStateOf(false) }
    var manualQuickCode by remember(portalState.pendingAutoLoginCode, activeVoucher) {
        mutableStateOf(portalState.pendingAutoLoginCode ?: activeVoucher?.code ?: "")
    }
    var hasLoadError by remember { mutableStateOf(false) }
    var loadErrorDescription by remember { mutableStateOf("") }

    val effectiveHost = portalState.activePortalHost.ifBlank { telemetry.gatewayIp }

    Dialog(
        onDismissRequest = { viewModel.closePortalDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KrotyDarkBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Cyber Cockpit Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF162442), KrotyCardBg)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(KrotyCyan.copy(alpha = 0.16f))
                                    .border(1.dp, KrotyCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Router,
                                    contentDescription = null,
                                    tint = KrotyCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = telemetry.ssid,
                                        color = KrotyTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = KrotyCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = effectiveHost,
                                            color = KrotyCyan,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "$pageTitle • الرصيد: ${portalState.liveRemainingMb} • الوقت: ${portalState.liveRemainingTime}",
                                    color = KrotyEmerald,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { showQuickVoucherDrawer = !showQuickVoucherDrawer },
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (showQuickVoucherDrawer) KrotyCyan else Color(0xFF1E2D4A)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = "تبديل الكرت",
                                    tint = if (showQuickVoucherDrawer) KrotyDarkBg else KrotyCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalIconButton(
                                onClick = { viewModel.closePortalDialog() },
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF331B24)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق البوابة",
                                    tint = KrotyCoralRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Collapsible Quick Voucher Switcher & Auto-Injector Drawer
                    AnimatedVisibility(visible = showQuickVoucherDrawer) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1628)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, KrotyCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "حقن وتبديل الكرت الفوري داخل البوابة:",
                                    color = KrotyCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = manualQuickCode,
                                        onValueChange = { manualQuickCode = it },
                                        placeholder = { Text("اكتب أو اختر رقم الكرت...", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (manualQuickCode.isNotBlank()) {
                                                viewModel.setPendingAutoLoginCode(manualQuickCode)
                                                val script = MikroTikJsScripts.buildAutoLoginScript(
                                                    voucherCode = manualQuickCode.trim(),
                                                    password = "",
                                                    customSelector = customSelector
                                                )
                                                webViewRef?.evaluateJavascript(script, null)
                                                showQuickVoucherDrawer = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = KrotyDarkBg, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حقن ودخول", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (allVouchers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(allVouchers.take(10)) { v ->
                                            FilterChip(
                                                selected = manualQuickCode == v.code,
                                                onClick = {
                                                    manualQuickCode = v.code
                                                    viewModel.setPendingAutoLoginCode(v.code)
                                                },
                                                label = {
                                                    Text(
                                                        "${v.code} (${v.categoryLabel})",
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Modern Browser Navigation & URL Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = KrotyTextPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                hasLoadError = false
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = KrotyCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                webViewRef?.settings?.apply {
                                    userAgentString = if (isDesktopMode) {
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                    } else {
                                        null
                                    }
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                contentDescription = "وضع العرض",
                                tint = if (isDesktopMode) KrotyAmber else KrotyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF0A1020),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, KrotyCardBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = KrotyCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = KrotyTextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            val target = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                                            hasLoadError = false
                                            webViewRef?.loadUrl(target)
                                        }
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val target = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                                hasLoadError = false
                                webViewRef?.loadUrl(target)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("فتح", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Scrollable Smart Action Ribbon (Never wraps or clips!)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PortalActionChip(
                                label = "⚡ دخول فوري بالكرت",
                                color = KrotyEmerald,
                                onClick = {
                                    val codeToInject = manualQuickCode.ifBlank {
                                        portalState.pendingAutoLoginCode ?: activeVoucher?.code ?: ""
                                    }
                                    if (codeToInject.isNotBlank()) {
                                        val script = MikroTikJsScripts.buildAutoLoginScript(
                                            voucherCode = codeToInject,
                                            password = portalState.pendingAutoLoginPassword,
                                            customSelector = customSelector
                                        )
                                        webViewRef?.evaluateJavascript(script, null)
                                    } else {
                                        showQuickVoucherDrawer = true
                                    }
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "📊 الرصيد (/status)",
                                color = KrotyCyan,
                                onClick = {
                                    val target = "http://$effectiveHost/status"
                                    urlInput = target
                                    hasLoadError = false
                                    webViewRef?.loadUrl(target)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "🔑 الدخول (/login)",
                                color = KrotyTextPrimary,
                                onClick = {
                                    val target = "http://$effectiveHost/login"
                                    urlInput = target
                                    hasLoadError = false
                                    webViewRef?.loadUrl(target)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "❄️ تجميد الكرت (/logout)",
                                color = KrotyAmber,
                                onClick = {
                                    val target = "http://$effectiveHost/logout"
                                    urlInput = target
                                    hasLoadError = false
                                    webViewRef?.loadUrl(target)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = if (portalState.touchToLearnMode) "🎯 جاري تعلّم الحقل..." else "🎯 تحديد الحقل باللمس",
                                color = if (portalState.touchToLearnMode) KrotyEmerald else Color(0xFFB388FF),
                                onClick = {
                                    viewModel.toggleTouchToLearnMode(true)
                                    webViewRef?.evaluateJavascript(MikroTikJsScripts.buildTouchToLearnScript(), null)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "🌐 كشف تحويل الشبكة",
                                color = KrotyCyan,
                                onClick = {
                                    val fallback = "http://${telemetry.gatewayIp.ifBlank { "10.0.0.1" }}/"
                                    urlInput = fallback
                                    hasLoadError = false
                                    webViewRef?.loadUrl(fallback)
                                }
                            )
                        }
                    }
                }

                // Loading Progress Bar
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        color = KrotyCyan,
                        trackColor = KrotyCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                } else {
                    HorizontalDivider(color = KrotyCardBorder)
                }

                // Touch-to-Learn Instruction Banner
                if (portalState.touchToLearnMode) {
                    Surface(
                        color = Color(0xFF281847),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFD1C4E9), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "وضع التعلّم باللمس نشط: المس مربع إدخال رقم الكرت داخل الصفحة ليتم حفظه تلقائياً لهذه الشبكة.",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.toggleTouchToLearnMode(false) }) {
                                Text("إلغاء", color = KrotyAmber, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Status / Error Banner inside Portal
                portalState.statusBannerMessage?.let { banner ->
                    Surface(
                        color = Color(0xFF102838),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = KrotyCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = banner,
                                color = KrotyCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = KrotyTextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // 4. Main WebView Container with Smart Recovery Overlay
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            viewModel.bindWifiForPortal()
                            WebView(ctx).apply {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.javaScriptCanOpenWindowsAutomatically = true

                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onHtmlExtracted(url: String, html: String) {
                                            viewModel.onWebViewHtmlExtracted(url, html)
                                        }

                                        @JavascriptInterface
                                        fun onFieldSelectorLearned(selector: String) {
                                            viewModel.saveCustomFieldSelector(selector)
                                        }

                                        @JavascriptInterface
                                        fun onMaxLengthDetected(maxLen: Int) {
                                            // Auto-detected voucher field maxLength
                                        }

                                        @JavascriptInterface
                                        fun onAutoLoginInjected(code: String) {
                                            // Auto-login script injected
                                        }
                                    },
                                    "KrotyBridge"
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            urlInput = url
                                            Uri.parse(url).host?.let { host ->
                                                viewModel.updateActivePortalHost(host)
                                            }
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val currentUrl = url ?: ""
                                        urlInput = currentUrl

                                        Uri.parse(currentUrl).host?.let { host ->
                                            viewModel.updateActivePortalHost(host)
                                        }

                                        view?.evaluateJavascript(
                                            "(function(){ if(window.KrotyBridge && document.documentElement){ window.KrotyBridge.onHtmlExtracted(window.location.href, document.documentElement.outerHTML); } })();",
                                            null
                                        )
                                        view?.evaluateJavascript(
                                            MikroTikJsScripts.buildInputMaxLengthDetectorScript(),
                                            null
                                        )

                                        if (portalState.touchToLearnMode) {
                                            view?.evaluateJavascript(
                                                MikroTikJsScripts.buildTouchToLearnScript(),
                                                null
                                            )
                                        }

                                        val pendingCode = portalState.pendingAutoLoginCode
                                        if (!pendingCode.isNullOrBlank() && !currentUrl.contains("/status")) {
                                            val script = MikroTikJsScripts.buildAutoLoginScript(
                                                voucherCode = pendingCode,
                                                password = portalState.pendingAutoLoginPassword,
                                                customSelector = customSelector
                                            )
                                            view?.evaluateJavascript(script, null)
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            hasLoadError = true
                                            loadErrorDescription = error?.description?.toString() ?: "تعذر الوصول لبوابة الشبكة"
                                        }
                                    }
                                }

                                loadUrl(portalState.currentPortalUrl)
                                webViewRef = this
                            }
                        }
                    )

                    if (hasLoadError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(KrotyDarkBg.copy(alpha = 0.95f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.WifiFind,
                                        contentDescription = null,
                                        tint = KrotyAmber,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "تعذر فتح الرابط المباشر للبوابة",
                                        color = KrotyTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "بعض شبكات المايكروتك تستخدم اسم نطاق خاص بدلاً من الرقم ($effectiveHost). اضغط على الزر أدناه لاكتشاف صفحة التحويل تلقائياً.",
                                        color = KrotyTextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        loadErrorDescription,
                                        color = KrotyCoralRed,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                hasLoadError = false
                                                viewModel.bindWifiForPortal()
                                                webViewRef?.loadUrl("http://${telemetry.gatewayIp.ifBlank { "10.0.0.1" }}/login")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan)
                                        ) {
                                            Text("إعادة المحاولة", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                hasLoadError = false
                                                viewModel.bindWifiForPortal()
                                                webViewRef?.loadUrl("http://neverssl.com")
                                            }
                                        ) {
                                            Text("كشف التحويل التلقائي", color = KrotyEmerald)
                                        }
                                    }
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
private fun PortalActionChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
