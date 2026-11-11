package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Size
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.domain.scanner.KrotyCameraAnalyzer
import com.example.ui.KrotyViewModel
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: KrotyViewModel,
    onNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContainer = viewModel.container

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var scannedCode by remember { mutableStateOf("") }
    var voucherPassword by remember { mutableStateOf("") }
    var voucherPrice by remember { mutableStateOf("") }
    var voucherMb by remember { mutableStateOf("") }
    var targetLength by remember { mutableIntStateOf(0) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var autoConnectOnScan by remember { mutableStateOf(false) }
    var scanStatusMessage by remember { mutableStateOf<String?>(null) }
    var isAnalyzingGallery by remember { mutableStateOf(false) }
    var cameraRebindKey by remember { mutableIntStateOf(0) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val krotyAnalyzer = remember {
        KrotyCameraAnalyzer(
            ocrParser = appContainer.ocrParser,
            consensusTracker = appContainer.consensusTracker,
            onVoucherLocked = { lockedCode ->
                if (scannedCode != lockedCode) {
                    scannedCode = lockedCode
                    scanStatusMessage = "✅ تم التقاط رقم الكرت بالكاميرا: $lockedCode"
                    try {
                        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                            .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    } catch (_: Exception) {}

                    if (autoConnectOnScan) {
                        viewModel.saveAndConnectVoucher(
                            code = lockedCode,
                            connectImmediately = true
                        )
                        onNavigateToRadar()
                    }
                }
            }
        )
    }

    LaunchedEffect(targetLength) {
        krotyAnalyzer.targetCodeLength = targetLength
        appContainer.consensusTracker.reset()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzingGallery = true
            scanStatusMessage = "⏳ جاري قراءة صورة الكرت من المعرض بالذكاء الاصطناعي..."
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val barcodeScanner = BarcodeScanning.getClient()

                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val qr = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (!qr.isNullOrBlank()) {
                            val cleaned = qr.trim()
                            scannedCode = cleaned
                            isAnalyzingGallery = false
                            scanStatusMessage = "✅ تم استخراج الكود من باركود الصورة: $cleaned"
                            if (autoConnectOnScan) {
                                viewModel.saveAndConnectVoucher(code = cleaned, connectImmediately = true)
                                onNavigateToRadar()
                            }
                        } else {
                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    val best = appContainer.ocrParser.extractBestCandidate(
                                        visionText = visionText,
                                        targetLength = targetLength,
                                        customRegex = null
                                    )
                                    val fallbackCode = if (best == null) {
                                        val normalized = visionText.text.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').replace('S', '5').replace('B', '8')
                                        Regex("\\b\\d{5,16}\\b").findAll(normalized)
                                            .map { it.value }
                                            .firstOrNull { candidate ->
                                                targetLength <= 0 || candidate.length == targetLength
                                            }
                                    } else null

                                    val finalCode = best?.code ?: fallbackCode
                                    isAnalyzingGallery = false
                                    if (!finalCode.isNullOrBlank()) {
                                        scannedCode = finalCode
                                        scanStatusMessage = "✅ تم استخراج رقم الكرت من صورة المعرض: $finalCode"
                                        try {
                                            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                                                .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                        } catch (_: Exception) {}
                                        if (autoConnectOnScan) {
                                            viewModel.saveAndConnectVoucher(code = finalCode, connectImmediately = true)
                                            onNavigateToRadar()
                                        }
                                    } else {
                                        scanStatusMessage = "⚠️ لم يتم العثور على أرقام واضحة في الصورة. جرب تقريب الصورة أو تحديد طول الكرت."
                                    }
                                }
                                .addOnFailureListener {
                                    isAnalyzingGallery = false
                                    scanStatusMessage = "❌ تعذر تحليل النص في الصورة المختارة."
                                }
                        }
                    }
                    .addOnFailureListener {
                        isAnalyzingGallery = false
                        scanStatusMessage = "❌ تعذر قراءة الصورة من المعرض."
                    }
            } catch (_: Exception) {
                isAnalyzingGallery = false
                scanStatusMessage = "❌ حدث خطأ أثناء فتح الصورة من المعرض."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Camera & AI Scanner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header Row with weight(1f) so buttons NEVER get pushed off-screen!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = KrotyCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الماسح الذكي للكروت (AI Camera)",
                            color = KrotyCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            appContainer.consensusTracker.reset()
                            cameraRebindKey++
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "إعادة تنشيط الكاميرا",
                            tint = KrotyTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Camera Viewfinder Box (Uses COMPATIBLE TextureView for 100% Xiaomi/Android reliability)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(235.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(2.dp, KrotyCyan.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        key(cameraRebindKey) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setTargetResolution(Size(1280, 720))
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also {
                                                    it.setAnalyzer(cameraExecutor, krotyAnalyzer)
                                                }

                                            cameraProvider.unbindAll()
                                            val cam = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageAnalysis
                                            )
                                            cameraRef = cam
                                        } catch (_: Exception) {
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                }
                            )
                        }

                        // Target Reticle Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .height(88.dp)
                                .border(2.dp, KrotyEmerald, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .height(1.5.dp)
                                    .background(KrotyCyan.copy(alpha = 0.65f))
                            )
                        }

                        Surface(
                            color = KrotyDarkBg.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "وجّه الكاميرا نحو أرقام الكرت أو الباركود",
                                color = KrotyCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = KrotyAmber,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "صلاحية الكاميرا مطلوبة لتشغيل الماسح الضوئي الحي",
                                color = KrotyTextPrimary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan)
                            ) {
                                Text("تفعيل الكاميرا الآن", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PROMINENT FLASH & GALLERY CONTROL BAR (Clear Arabic Buttons + Icons!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val newState = !torchEnabled
                            torchEnabled = newState
                            cameraRef?.cameraControl?.enableTorch(newState)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (torchEnabled) KrotyAmber else Color(0xFF1F2E4D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "الفلاش",
                            tint = if (torchEnabled) KrotyDarkBg else KrotyAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (torchEnabled) "إطفاء الفلاش" else "تشغيل الفلاش",
                            color = if (torchEnabled) KrotyDarkBg else KrotyTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3648)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        if (isAnalyzingGallery) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = KrotyCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "المعرض",
                                tint = KrotyCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "صورة من المعرض",
                            color = KrotyCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Status feedback banner for camera / gallery OCR
                scanStatusMessage?.let { statusMsg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF0E2238),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, KrotyCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = statusMsg,
                            color = KrotyEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تقريب العدسة: ${String.format("%.1fx", zoomRatio)}",
                        color = KrotyTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Slider(
                        value = zoomRatio,
                        onValueChange = {
                            zoomRatio = it
                            cameraRef?.cameraControl?.setZoomRatio(it)
                        },
                        valueRange = 1.0f..3.0f,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Length Filter Chips inside LazyRow so "12 رقم" NEVER wraps vertically!
                val lengthOptions = listOf(0, 6, 8, 10, 12)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lengthOptions) { len ->
                        FilterChip(
                            selected = targetLength == len,
                            onClick = { targetLength = len },
                            label = {
                                Text(
                                    text = if (len == 0) "تلقائي (كل الأطوال)" else "$len أرقام",
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "الاتصال التلقائي بالبوابة فور التقاط الكرت ⚡",
                        color = KrotyTextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoConnectOnScan,
                        onCheckedChange = { autoConnectOnScan = it }
                    )
                }
            }
        }

        // 2. Extracted Voucher & Instant Execution Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "بيانات الكرت المستخرج والتنفيذ الفوري",
                    color = KrotyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = scannedCode,
                    onValueChange = { scannedCode = it },
                    label = { Text("رقم الكرت (يُملأ تلقائياً من الكاميرا أو المعرض أو يدوياً)") },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KrotyEmerald
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = voucherPassword,
                    onValueChange = { voucherPassword = it },
                    label = { Text("كلمة المرور (اتركه فارغاً لكروت المايكروتك العادية)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = voucherPrice,
                        onValueChange = { voucherPrice = it },
                        label = { Text("السعر (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = voucherMb,
                        onValueChange = { voucherMb = it },
                        label = { Text("الباقة MB (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveAndConnectVoucher(
                                code = scannedCode,
                                password = voucherPassword,
                                priceRials = voucherPrice.toDoubleOrNull() ?: 0.0,
                                initialMb = voucherMb.toDoubleOrNull() ?: 0.0,
                                connectImmediately = true
                            )
                            onNavigateToRadar()
                        },
                        enabled = scannedCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = KrotyDarkBg)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اتصال فوري بالبوابة", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.saveAndConnectVoucher(
                                code = scannedCode,
                                password = voucherPassword,
                                priceRials = voucherPrice.toDoubleOrNull() ?: 0.0,
                                initialMb = voucherMb.toDoubleOrNull() ?: 0.0,
                                connectImmediately = false
                            )
                            scannedCode = ""
                            appContainer.consensusTracker.reset()
                        },
                        enabled = scannedCode.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = KrotyCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ في المحفظة", color = KrotyCyan)
                    }
                }
            }
        }
    }
}
