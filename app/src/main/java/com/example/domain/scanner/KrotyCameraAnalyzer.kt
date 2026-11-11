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
