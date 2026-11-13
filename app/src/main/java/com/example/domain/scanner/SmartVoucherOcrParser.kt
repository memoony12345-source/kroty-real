package com.example.domain.scanner

import com.google.mlkit.vision.text.Text

data class ScannedVoucherCandidate(
    val code: String,
    val detectedPrice: Double? = null,
    val detectedQuotaMb: Long? = null,
    val score: Int = 0
)

object SmartVoucherOcrParser {

    private val blacklistKeywords = listOf(
        "s/n", "serial", "تسلسلي", "متسلسل", "خدمة العملاء", "للتواصل",
        "جوال", "هاتف", "تلفون", "واتس", "سعر", "ريال", "تاريخ", "صلاحية"
    )
    private val positiveKeywords = listOf(
        "رمز", "كود", "كرت", "بطاقة", "الكرت", "user", "username", "voucher", "pin", "code", "card"
    )
    private val phonePrefixes = listOf("77", "78", "71", "73", "70", "05", "967", "966")

    fun normalizeOpticalDigits(raw: String, isNumericOnly: Boolean): String {
        val cleaned = raw.trim().replace(" ", "").replace("-", "")
        if (!isNumericOnly) return cleaned
        val sb = StringBuilder()
        for (ch in cleaned) {
            when (ch) {
                'O', 'o', 'Q', 'D' -> sb.append('0')
                'I', 'l', '|', '!' -> sb.append('1')
                'Z', 'z' -> sb.append('2')
                'S', 's', '$' -> sb.append('5')
                'G', 'b' -> sb.append('6')
                'B' -> sb.append('8')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseMlKitText(
        visionText: Text,
        expectedLength: Int? = null,
        isNumericOnly: Boolean = true
    ): ScannedVoucherCandidate? {
        val fullText = visionText.text
        if (fullText.isBlank()) return null

        val price = extractPrice(fullText)
        val quotaMb = extractQuotaMb(fullText)

        var maxBoxHeight = 1
        val allLines = visionText.textBlocks.flatMap { it.lines }
        for (line in allLines) {
            val h = line.boundingBox?.height() ?: 1
            if (h > maxBoxHeight) maxBoxHeight = h
        }

        val candidates = mutableListOf<ScannedVoucherCandidate>()

        for (line in allLines) {
            val lineText = line.text.trim()
            val lowerLine = lineText.lowercase()
            if (blacklistKeywords.any { lowerLine.contains(it) }) continue

            val hasPositiveKeyword = positiveKeywords.any { lowerLine.contains(it) }
            val lineHeight = line.boundingBox?.height() ?: 10
            val fontScore = ((lineHeight.toFloat() / maxBoxHeight.toFloat()) * 40f).toInt()

            val tokens = Regex("""[A-Za-z0-9IlOQSZB|]{4,20}
