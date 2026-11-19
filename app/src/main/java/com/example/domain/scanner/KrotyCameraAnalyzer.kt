package com.example.domain.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicInteger

class KrotyCameraAnalyzer(
    private val getExpectedLength: () -> Int?,
    private val isNumericOnly: () -> Boolean,
    private val onLiveCandidate: (ScannedVoucherCandidate, Float, Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()
    private val consensusTracker = MultiFrameConsensusTracker(requiredMatches = 2)

    /** OCR is the slow part; run it on a steady ~7 frames/s while the preview stays smooth. */
    private var lastOcrAt = 0L
    private val ocrIntervalMs = 140L

    fun resetConsensus() = consensusTracker.reset()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val now = SystemClock.elapsedRealtime()
        val runOcr = now - lastOcrAt >= ocrIntervalMs
        if (runOcr) lastOcrAt = now

        // A faint card is often invisible to OCR until the contrast is stretched.
        val enhanced = if (runOcr) {
            runCatching {
                VoucherImageEnhancer.fromImageProxy(imageProxy)?.let { VoucherImageEnhancer.enhance(it) }
            }.getOrNull()
        } else null

        val pending = AtomicInteger(if (runOcr) 2 else 1)
        val finish = { if (pending.decrementAndGet() <= 0) imageProxy.close() }

        // 1. QR / barcode first: an instant, certain hit when the card carries one.
        val barcodeImage = InputImage.fromMediaImage(mediaImage, rotation)
        barcodeScanner.process(barcodeImage)
            .addOnSuccessListener { barcodes ->
                val rawQr = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                if (!rawQr.isNullOrBlank()) {
                    val cleanCode = rawQr.removePrefix("KROTY_VOUCHER:").trim()
                    if (cleanCode.length in SmartVoucherOcrParser.MIN_CODE_LENGTH..24) {
                        val candidate = ScannedVoucherCandidate(code = cleanCode, score = 200)
                        onLiveCandidate(candidate, 1.0f, true)
                    }
                }
            }
            .addOnFailureListener { }
            .addOnCompleteListener { finish() }

        if (!runOcr) return

        // 2. OCR the enhanced grayscale frame (falls back to the raw frame).
        val ocrImage = enhanced?.let { InputImage.fromBitmap(it, rotation) }
            ?: InputImage.fromMediaImage(mediaImage, rotation)

        textRecognizer.process(ocrImage)
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
            .addOnFailureListener { }
            .addOnCompleteListener { finish() }
    }

    companion object {
        fun scanGalleryUri(
            context: Context,
            uri: Uri,
            expectedLength: Int?,
            onResult: (ScannedVoucherCandidate?) -> Unit
        ) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / sample > 2000) sample *= 2

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sample
                }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (bitmap == null) {
                    onResult(null)
                    return
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val enhanced = runCatching { VoucherImageEnhancer.enhance(bitmap) }.getOrNull()
                    ?: bitmap

                recognizer.process(InputImage.fromBitmap(enhanced, 0))
                    .addOnSuccessListener { text ->
                        val parsed = SmartVoucherOcrParser.parseMlKitText(text, expectedLength, false)
                        if (parsed != null) {
                            onResult(parsed)
                        } else {
                            // Second chance on the untouched photo.
                            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                .addOnSuccessListener { raw ->
                                    onResult(SmartVoucherOcrParser.parseMlKitText(raw, expectedLength, false))
                                }
                                .addOnFailureListener { onResult(null) }
                        }
                    }
                    .addOnFailureListener { onResult(null) }
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }
}
