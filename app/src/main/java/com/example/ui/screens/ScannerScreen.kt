package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.scanner.KrotyCameraAnalyzer
import com.example.ui.KrotyViewModel
import com.example.ui.theme.AuroraAmber
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraEmerald
import com.example.ui.theme.AuroraRose
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.MetalButton
import com.example.ui.theme.AuroraViolet
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: KrotyViewModel,
    onConnectedNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = rememberScannerTabLifecycleOwner()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var quotaMbInput by remember { mutableStateOf("") }

    var liveDetectedCode by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf(0f) }
    var selectedLengthLock by remember { mutableStateOf<Int?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var autoConnectOnConsensus by remember { mutableStateOf(false) }
    var numericCardMode by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var statusBanner by remember { mutableStateOf("وجّه الكاميرا الحقيقية نحو أرقام الكرت أو الباركود") }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            statusBanner = "جاري قراءة أرقام الكرت من الصورة..."
            KrotyCameraAnalyzer.scanGalleryUri(context, uri, selectedLengthLock) { result ->
                if (result != null) {
                    codeInput = result.code
                    liveDetectedCode = result.code
                    result.detectedPrice?.let { priceInput = it.toInt().toString() }
                    result.detectedQuotaMb?.let { quotaMbInput = it.toString() }
                    statusBanner = "✅ تم استخراج الكرت (${result.code}) من الصورة بنجاح!"
                    vibratePhone(context)
                } else {
                    statusBanner = "⚠️ لم يتم العثور على رقم كرت واضح في الصورة المختارة"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real Live CameraX Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الماسح الذكي (فلاش + معرض + دخول آلي)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row {
                        IconButton(onClick = {
                            torchEnabled = !torchEnabled
                            cameraRef?.cameraControl?.enableTorch(torchEnabled)
                        }) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "الفلاش",
                                tint = if (torchEnabled) AuroraAmber else Color.White
                            )
                        }
                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "صورة من المعرض", tint = AuroraCyan)
                        }
                    }
                }

                // Live CameraX PreviewView Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp)
                        .shadow(22.dp, RoundedCornerShape(22.dp), spotColor = AuroraCyan)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF04070D))
                        .border(
                            2.dp,
                            Brush.linearGradient(listOf(AuroraCyan, AuroraViolet, AuroraEmerald)),
                            RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                val executor = Executors.newSingleThreadExecutor()

                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val analyzer = KrotyCameraAnalyzer(
                                            getExpectedLength = { selectedLengthLock },
                                            isNumericOnly = { numericCardMode },
                                            onLiveCandidate = { candidate, prog, confirmed ->
                                                liveDetectedCode = candidate.code
                                                confidence = prog
                                                if (confirmed && codeInput != candidate.code) {
                                                    codeInput = candidate.code
                                                    candidate.detectedPrice?.let { priceInput = it.toInt().toString() }
                                                    candidate.detectedQuotaMb?.let { quotaMbInput = it.toString() }
                                                    statusBanner = "✅ تم التقاط رقم الكرت الحقيقي: ${candidate.code}"
                                                    vibratePhone(ctx)
                                                    if (autoConnectOnConsensus) {
                                                        viewModel.saveAndConnectVoucher(
                                                            code = candidate.code,
                                                            password = passwordInput,
                                                            price = priceInput.toDoubleOrNull(),
                                                            quotaMb = quotaMbInput.toLongOrNull(),
                                                            connectImmediately = true
                                                        )
                                                        onConnectedNavigateToRadar()
                                                    }
                                                }
                                            }
                                        )
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { it.setAnalyzer(executor, analyzer) }

                                        cameraProvider.unbindAll()
                                        cameraRef = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (_: Exception) {}
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            }
                        )

                        // Target ROI Frame Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(95.dp)
                                .border(2.dp, AuroraEmerald, RoundedCornerShape(12.dp))
                        )

                        // Live Detected Overlay Badge at Bottom of Camera
                        Surface(
                            color = Color(0xCC0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (liveDetectedCode.isNotBlank())
                                    "الملتقط: $liveDetectedCode (${(confidence * 100).toInt()}%)"
                                else statusBanner,
                                color = AuroraCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("يرجى منح صلاحية الكاميرا لتشغيل الماسح الحقيقي", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            MetalButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("منح صلاحية الكاميرا الآن")
                            }
                        }
                    }
                }

                // Zoom Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تقريب العدسة: ${String.format("%.1fx", zoomRatio)}", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = zoomRatio,
                        onValueChange = {
                            zoomRatio = it
                            cameraRef?.cameraControl?.setZoomRatio(it)
                        },
                        valueRange = 1f..3f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }

                // Length Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val lengths = listOf(null to "الكل", 6 to "6 خانات", 8 to "8 خانات", 10 to "10 خانات", 12 to "12 خانة")
                    for ((len, label) in lengths) {
                        FilterChip(
                            selected = selectedLengthLock == len,
                            onClick = { selectedLengthLock = len },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الاتصال التلقائي بالبوابة فور التقاط الكرت ⚡", fontSize = 13.sp)
                    Switch(checked = autoConnectOnConsensus, onCheckedChange = { autoConnectOnConsensus = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("كرت أرقام فقط (بدون حروف)", fontSize = 13.sp)
                        Text(
                            "فعّله لكروت الفئة الرقمية ليتجاهل الحروف المشابهة للأرقام",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(checked = numericCardMode, onCheckedChange = {
                        numericCardMode = it
                        statusBanner = if (it) "وضع الأرقام فقط مفعّل" else "الوضع التلقائي (أرقام وحروف)"
                    })
                }
            }
        }

        // Extracted / Manual Voucher Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("بيانات الكرت المستخرج والتنفيذ الفوري", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("رقم الكرت (يُملأ تلقائياً من الكاميرا أو يدوياً)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("كلمة المرور (اتركه فارغاً لكروت المايكروتك العادية)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("السعر (اختياري)", maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quotaMbInput,
                        onValueChange = { quotaMbInput = it },
                        label = { Text("الباقة MB (اختياري)", maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetalButton(
                        onClick = {
                            if (codeInput.isNotBlank()) {
                                viewModel.saveAndConnectVoucher(
                                    code = codeInput,
                                    password = passwordInput,
                                    price = priceInput.toDoubleOrNull(),
                                    quotaMb = quotaMbInput.toLongOrNull(),
                                    connectImmediately = true
                                )
                                onConnectedNavigateToRadar()
                            } else {
                                statusBanner = "⚠️ وجّه الكاميرا نحو الكرت أولاً أو اكتب رقم الكرت"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraEmerald)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("اتصال فوري بالبوابة", maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = {
                            if (codeInput.isNotBlank()) {
                                viewModel.saveAndConnectVoucher(
                                    code = codeInput,
                                    password = passwordInput,
                                    price = priceInput.toDoubleOrNull(),
                                    quotaMb = quotaMbInput.toLongOrNull(),
                                    connectImmediately = false
                                )
                                statusBanner = "✅ تم حفظ الكرت (${codeInput}) في طابور المحفظة!"
                                codeInput = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("حفظ في المحفظة", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

private fun vibratePhone(context: Context) {
    try {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(120)
        }
    } catch (_: Exception) {}
}

private class ScannerTabLifecycle : androidx.lifecycle.LifecycleOwner {
    val registry = androidx.lifecycle.LifecycleRegistry(this)
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}

@Composable
private fun rememberScannerTabLifecycleOwner(): androidx.lifecycle.LifecycleOwner {
    val context = androidx.compose.ui.platform.LocalContext.current
    val parentOwner = LocalLifecycleOwner.current
    val tabOwner = remember { ScannerTabLifecycle() }

    DisposableEffect(parentOwner) {
        try {
            tabOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
            tabOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            tabOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        } catch (_: Exception) {}

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            try {
                tabOwner.registry.handleLifecycleEvent(event)
            } catch (_: Exception) {}
        }
        parentOwner.lifecycle.addObserver(observer)

        onDispose {
            parentOwner.lifecycle.removeObserver(observer)
            try {
                tabOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
                tabOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
                tabOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
            } catch (_: Exception) {}
            try {
                val providerFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    try {
                        providerFuture.get().unbindAll()
                    } catch (_: Exception) {}
                }, androidx.core.content.ContextCompat.getMainExecutor(context))
            } catch (_: Exception) {}
        }
    }
    return tabOwner
}
