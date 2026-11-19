package com.example.domain.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * Makes faint, glossy or badly lit voucher cards readable.
 *
 * Phone screens and cheap thermal print give low contrast glyphs: the ink and
 * the paper sit only a few grey levels apart. We stretch the histogram to the
 * 2%..98% range and run an unsharp mask, which is what makes thin digits pop
 * for the OCR engine.
 */
object VoucherImageEnhancer {

    /** Grayscale luma copy of a camera frame (the Y plane), without any processing. */
    fun fromImageProxy(imageProxy: ImageProxy): Bitmap? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        if (plane.pixelStride != 1) return null
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return null

        val rowStride = plane.rowStride
        if (rowStride < width) return null
        val buffer = plane.buffer
        buffer.rewind()
        if (buffer.remaining() < rowStride) return null

        val pixels = IntArray(width * height)
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            if (buffer.remaining() < rowStride) break
            buffer.get(row, 0, rowStride)
            val base = y * width
            for (x in 0 until width) {
                val v = row[x].toInt() and 0xFF
                pixels[base + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Auto-levelled, sharpened grayscale copy of [bitmap]. */
    fun enhance(bitmap: Bitmap, sharpen: Boolean = true): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        val hist = IntArray(256)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val v = (r * 77 + g * 151 + b * 28) shr 8
            gray[i] = v
            hist[v]++
        }

        // Ignore the extreme 2% on both ends so a bright glare spot or a dark
        // shadow cannot flatten the rest of the card.
        val total = w * h
        val cut = (total * 0.02f).toInt().coerceAtLeast(1)

        var low = 0
        var acc = 0
        while (low < 255 && acc + hist[low] < cut) {
            acc += hist[low]
            low++
        }
        var high = 255
        acc = 0
        while (high > 0 && acc + hist[high] < cut) {
            acc += hist[high]
            high--
        }
        if (high - low < 24) {
            low = 0
            high = 255
        }
        val range = (high - low).coerceAtLeast(1)

        val lut = IntArray(256) { v -> (((v - low) * 255) / range).coerceIn(0, 255) }
        for (i in gray.indices) gray[i] = lut[gray[i]]

        val sharp = if (sharpen) unsharp(gray, w, h) else gray

        val out = IntArray(w * h)
        for (i in sharp.indices) {
            val v = sharp[i]
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun unsharp(gray: IntArray, w: Int, h: Int): IntArray {
        val tmp = IntArray(gray.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val x0 = if (x > 0) x - 1 else x
                val x1 = if (x < w - 1) x + 1 else x
                tmp[row + x] = (gray[row + x0] + gray[row + x] + gray[row + x1]) / 3
            }
        }
        val blur = IntArray(gray.size)
        for (y in 0 until h) {
            val y0 = if (y > 0) y - 1 else y
            val y1 = if (y < h - 1) y + 1 else y
            for (x in 0 until w) {
                blur[y * w + x] = (tmp[y0 * w + x] + tmp[y * w + x] + tmp[y1 * w + x]) / 3
            }
        }
        val out = IntArray(gray.size)
        for (i in out.indices) {
            out[i] = (2 * gray[i] - blur[i]).coerceIn(0, 255)
        }
        return out
    }
}
