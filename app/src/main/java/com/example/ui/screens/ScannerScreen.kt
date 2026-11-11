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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: KrotyViewModel,
    onConnectedNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    Text("الماسح الذكي للكروت", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row {
                        IconButton(onClick = {
                            torchEnabled = !torchEnabled
                            cameraRef?.cameraControl?.enableTorch(torchEnabled)
                        }) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "الفلاش",
                                tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White
                            )
                        }
                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "صورة من المعرض", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                // Live CameraX PreviewView Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }.apply {
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
                                            isNumericOnly = { true },
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
                                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
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
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("يرجى منح صلاحية الكاميرا لتشغيل الماسح الحقيقي", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
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
                    val lengths = listOf(null to "تلقائي", 6 to "6 أرقام", 8 to "8 أرقام", 10 to "10 أرقام", 12 to "12 رقم")
                    for ((len, label) in lengths) {
                        FilterChip(
                            selected = selectedLengthLock == len,
                            onClick = { selectedLengthLock = len },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // Auto-Connect Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الاتصال التلقائي بالبوابة فور التقاط الكرت ⚡", fontSize = 13.sp)
                    Switch(checked = autoConnectOnConsensus, onCheckedChange = { autoConnectOnConsensus = it })
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
                        label = { Text("السعر (اختياري)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quotaMbInput,
                        onValueChange = { quotaMbInput = it },
                        label = { Text("الباقة MB (اختياري)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("اتصال فوري بالبوابة")
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
                        Text("حفظ في المحفظة")
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
