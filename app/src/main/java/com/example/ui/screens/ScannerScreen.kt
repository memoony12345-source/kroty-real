package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.scanner.KrotyCameraAnalyzer
import com.example.ui.KrotyViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

private val KrotyDarkBg = Color(0xFF0A0E17)
private val KrotyCardBg = Color(0xFF131B2E)
private val KrotyCardBorder = Color(0xFF1E293B)
private val KrotyCyan = Color(0xFF00E5FF)
private val KrotyEmerald = Color(0xFF00E676)
private val KrotyAmber = Color(0xFFFFB300)
private val KrotyCoralRed = Color(0xFFFF5252)
private val KrotyTextPrimary = Color(0xFFF8FAFC)
private val KrotyTextSecondary = Color(0xFF94A3B8)

private class ScannerTabLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    fun handleEvent(event: Lifecycle.Event) {
        try { registry.handleLifecycleEvent(event) } catch (_: Exception) {}
    }
    fun forceDestroy() {
        try {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun ScannerScreen(
    viewModel: KrotyViewModel,
    onConnectedNavigateToRadar: () -> Unit = {},
    onNavigateToRadar: () -> Unit = onConnectedNavigateToRadar
) {
    val context = LocalContext.current
    val parentLifecycleOwner = LocalLifecycleOwner.current
    val appContainer = viewModel.appContainer

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var detectedCode by remember { mutableStateOf("") }
    var isConfirmedConsensus by remember { mutableStateOf(false) }
    var expectedLengthFilter by remember { mutableStateOf<Int?>(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isGalleryScanning by remember { mutableStateOf(false) }
    var manualEditCode by remember { mutableStateOf("") }
    var autoConnectTriggered by remember { mutableStateOf(false) }

    val tabLifecycleOwner = remember { ScannerTabLifecycleOwner() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    fun forceShutDownCameraHardware() {
        try { cameraInstance?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
        try { cameraProviderInstance?.unbindAll() } catch (_: Exception) {}
        cameraInstance = null
        tabLifecycleOwner.forceDestroy()
    }

    DisposableEffect(parentLifecycleOwner) {
        tabLifecycleOwner.handleEvent(Lifecycle.Event.ON_CREATE)
        tabLifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
        tabLifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)

        val parentObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tabLifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
                Lifecycle.Event.ON_PAUSE -> {
                    try { cameraInstance?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
                    isTorchEnabled = false
                    tabLifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
                }
                Lifecycle.Event.ON_STOP -> {
                    try { cameraProviderInstance?.unbindAll() } catch (_: Exception) {}
                    tabLifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
                }
                Lifecycle.Event.ON_DESTROY -> forceShutDownCameraHardware()
                else -> {}
            }
        }
        parentLifecycleOwner.lifecycle.addObserver(parentObserver)

        onDispose {
            parentLifecycleOwner.lifecycle.removeObserver(parentObserver)
            forceShutDownCameraHardware()
            try { cameraExecutor.shutdownNow() } catch (_: Exception) {}
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isGalleryScanning = true
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        isGalleryScanning = false
                        val candidate = appContainer.ocrParser.extractBestCandidate(
                            visionText = visionText,
                            expectedLength = expectedLengthFilter
                        )
                        if (candidate != null) {
                            detectedCode = candidate.code
                            manualEditCode = candidate.code
                            isConfirmedConsensus = true
                        } else {
                            val fallback = visionText.text
                                .replace('O', '0').replace('o', '0')
                                .replace('I', '1').replace('l', '1')
                                .replace('S', '5').replace('B', '8')
                                .lines()
                                .map { it.trim() }
                                .firstOrNull { it.length in 4..16 && it.any { c -> c.isDigit() } }
                            if (fallback != null) {
                                detectedCode = fallback
                                manualEditCode = fallback
                                isConfirmedConsensus = true
                            }
                        }
                    }
                    .addOnFailureListener { isGalleryScanning = false }
            } catch (_: Exception) {
                isGalleryScanning = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
            border = BorderStroke(1.dp, KrotyCardBorder),
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = KrotyCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ماسح الكروت الذكي (CameraX + ML Kit)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KrotyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val nextTorch = !isTorchEnabled
                            isTorchEnabled = nextTorch
                            cameraInstance?.cameraControl?.enableTorch(nextTorch)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTorchEnabled) KrotyAmber else KrotyCardBorder,
                            contentColor = if (isTorchEnabled) Color.Black else KrotyTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTorchEnabled) "إيقاف الفلاش" else "تشغيل الفلاش",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, KrotyCyan),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = KrotyCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "صورة من المعرض",
                            color = KrotyCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        val lengthOptions = listOf(null to "تلقائي شامل", 6 to "6 أرقام", 8 to "8 أرقام", 10 to "10 أرقام", 12 to "12 رقم")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(lengthOptions) { (len, label) ->
                FilterChip(
                    selected = expectedLengthFilter == len,
                    onClick = {
                        expectedLengthFilter = len
                        appContainer.consensusTracker.reset()
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KrotyCyan.copy(alpha = 0.22f),
                        selectedLabelColor = KrotyCyan
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        previewView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {}
                            override fun onViewDetachedFromWindow(v: View) {
                                forceShutDownCameraHardware()
                            }
                        })

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProviderInstance = cameraProvider

                                if (tabLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                                    cameraProvider.unbindAll()
                                    return@addListener
                                }

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val analyzer = KrotyCameraAnalyzer(
                                    ocrParser = appContainer.ocrParser,
                                    consensusTracker = appContainer.consensusTracker,
                                    expectedLengthProvider = { expectedLengthFilter },
                                    onLiveCandidateDetected = { liveCode ->
                                        if (!isConfirmedConsensus) {
                                            detectedCode = liveCode
                                        }
                                    },
                                    onConsensusConfirmed = { confirmedCode, _ ->
                                        detectedCode = confirmedCode
                                        manualEditCode = confirmedCode
                                        isConfirmedConsensus = true
                                    }
                                )

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { it.setAnalyzer(cameraExecutor, analyzer) }

                                cameraProvider.unbindAll()
                                cameraInstance = cameraProvider.bindToLifecycle(
                                    tabLifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxW = size.width * 0.82f
                    val boxH = size.height * 0.36f
                    val left = (size.width - boxW) / 2f
                    val top = (size.height - boxH) / 2f
                    val strokeColor = if (isConfirmedConsensus) KrotyEmerald else KrotyCyan

                    drawRoundRect(
                        color = strokeColor,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(boxW, boxH),
                        cornerRadius = CornerRadius(24f, 24f),
                        style = Stroke(width = 5f)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مستوى التقريب (Zoom)", color = KrotyTextSecondary, fontSize = 11.sp, maxLines = 1)
                        Text("${String.format("%.1f", zoomRatio)}x", color = KrotyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Slider(
                        value = zoomRatio,
                        onValueChange = { newZoom ->
                            zoomRatio = newZoom
                            cameraInstance?.cameraControl?.setZoomRatio(newZoom)
                        },
                        valueRange = 1f..3f,
                        colors = SliderDefaults.colors(thumbColor = KrotyCyan, activeTrackColor = KrotyCyan)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = KrotyCoralRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("يلزم منح صلاحية الكاميرا لمسح الكروت الحقيقية", color = KrotyTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("منح صلاحية الكاميرا الآن", maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
            border = BorderStroke(1.dp, if (isConfirmedConsensus) KrotyEmerald else KrotyCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isConfirmedConsensus) "تم تأكيد الكرت بنجاح (تطابق إطارين)"
                        else if (isGalleryScanning) "جاري فحص الصورة..."
                        else "وجّه الكاميرا نحو رقم الكرت أو الباركود",
                        color = if (isConfirmedConsensus) KrotyEmerald else KrotyTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (detectedCode.isNotEmpty()) {
                        TextButton(onClick = {
                            detectedCode = ""
                            manualEditCode = ""
                            isConfirmedConsensus = false
                            autoConnectTriggered = false
                            appContainer.consensusTracker.reset()
                        }) {
                            Text("إعادة مسح", color = KrotyAmber, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                OutlinedTextField(
                    value = manualEditCode.ifEmpty { detectedCode },
                    onValueChange = {
                        manualEditCode = it.trim()
                        detectedCode = it.trim()
                    },
                    label = { Text("الكود المستخرج (قابل للتعديل أو اللصق)") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = KrotyCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val finalCode = manualEditCode.ifEmpty { detectedCode }
                            if (finalCode.isNotBlank()) {
                                forceShutDownCameraHardware()
                                viewModel.saveScannedVoucherAndConnect(
                                    code = finalCode,
                                    categoryLabel = "مسح كاميرا ذكي",
                                    priceYemeniRial = 100,
                                    connectImmediately = true
                                )
                                autoConnectTriggered = true
                                onNavigateToRadar()
                            }
                        },
                        enabled = (manualEditCode.isNotBlank() || detectedCode.isNotBlank()),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ واتصال فوري", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = {
                            val finalCode = manualEditCode.ifEmpty { detectedCode }
                            if (finalCode.isNotBlank()) {
                                viewModel.saveScannedVoucherAndConnect(
                                    code = finalCode,
                                    categoryLabel = "مخزون المحفظة",
                                    priceYemeniRial = 100,
                                    connectImmediately = false
                                )
                                detectedCode = ""
                                manualEditCode = ""
                                isConfirmedConsensus = false
                                appContainer.consensusTracker.reset()
                            }
                        },
                        enabled = (manualEditCode.isNotBlank() || detectedCode.isNotBlank()),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, KrotyCyan)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = KrotyCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ بالمحفظة", color = KrotyCyan, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
