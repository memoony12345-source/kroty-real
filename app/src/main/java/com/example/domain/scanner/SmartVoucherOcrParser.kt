package com.example.domain.scanner

import com.google.mlkit.vision.text.Text

data class ScannedVoucherCandidate(
    val code: String,
    val detectedPrice: Double? = null,
    val detectedQuotaMb: Long? = null,
    val score: Int = 0,
    /** true when the code was read as a pure.number line (numeric voucher card) */
    val numericCard: Boolean = false
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
    private val commonWords = listOf(
        "hotspot", "login", "logout", "internet", "wifi", "wi-fi", "welcome",
        "user", "username", "password", "voucher", "code", "card", "price",
        "http", "https", "www", "connect", "status", "support", "fast", "real"
    )

    /** A voucher code is 6..12 chars: anything shorter is price/serial noise. */
    const val MIN_CODE_LENGTH = 6
    const val MAX_NUMERIC_LENGTH = 12
    const val MAX_ALNUM_LENGTH = 14

    /** Characters OCR mixes with digits on printed voucher cards. */
    private const val DIGIT_CONFUSABLES = "OoQDIl|!ZzSs\$GbB"

    private val separatorRegex = Regex("""[\s\-–—._·/\\:,|]+""")
    private val dateLikeRegex = Regex("""^\s*\d{1,4}\s*[/.\-]\s*\d{1,2}\s*[/.\-]\s*\d{1,4}\s*$""")
    private val decimalRegex = Regex("""^\s*\d{1,4}[.,]\d{1,2}\s*$""")

    /** Character class body for "a digit or something OCR misreads as one", incl. Arabic digits. */
    private val NUMERIC_CLASS =
        "0-9OoQDIl|!ZzSs\\\$GbB\\u0660-\\u0669\\u06F0-\\u06F9"

    /** One numeric voucher number, possibly split into groups by spaces or dashes. */
    private val numericRunRegex =
        Regex("[$NUMERIC_CLASS][$NUMERIC_CLASS\\s,،\\-–—._·]*[$NUMERIC_CLASS]")

    fun normalizeOpticalDigits(raw: String, isNumericOnly: Boolean): String {
        val cleaned = raw.trim().replace(" ", "").replace("-", "")
        if (!isNumericOnly) return toLatinDigits(cleaned)
        val sb = StringBuilder()
        for (ch in toLatinDigits(cleaned)) {
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

    /** Arabic-Indic and Persian digits -> ASCII, so OCR of a numeric card still works. */
    private fun toLatinDigits(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val code = ch.code
            when {
                code in 0x0660..0x0669 -> sb.append(('0' + (code - 0x0660)))
                code in 0x06F0..0x06F9 -> sb.append(('0' + (code - 0x06F0)))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * A numeric voucher is usually printed as one long number, often split by OCR into
     * groups ("2990 885 685", "2990-885-685"). Merge the whole run back into one number
     * instead of letting the tokenizer emit a handful of short numbers.
     */
    fun mergeNumericLine(rawLine: String): String? {
        return mergeNumericRuns(rawLine).firstOrNull()
    }

    /** All numeric runs on a line, longest first (labels/words around them are ignored). */
    fun mergeNumericRuns(rawLine: String): List<String> {
        val line = rawLine.trim()
        if (line.isBlank()) return emptyList()
        if (dateLikeRegex.matches(line) || decimalRegex.matches(line)) return emptyList()

        val runs = numericRunRegex.findAll(line).map { it.value }.toList()

        val merged = LinkedHashSet<String>()
        for (run in runs) {
            val withoutSeparators = run.replace(separatorRegex, "")
            if (withoutSeparators.isBlank()) continue
            val repaired = normalizeOpticalDigits(withoutSeparators, isNumericOnly = true)
            if (repaired.length !in MIN_CODE_LENGTH..MAX_NUMERIC_LENGTH) continue
            if (!repaired.all { it.isDigit() }) continue
            // A run that is mostly repaired letters is a word ("hotspot"), not a number.
            val plainDigitCount = withoutSeparators.count { it.isDigit() }
            if (plainDigitCount < (withoutSeparators.length * 0.6f).toInt()) continue
            merged.add(repaired)
        }
        return merged.sortedByDescending { it.length }
    }


    fun parseMlKitText(
        visionText: Text,
        expectedLength: Int? = null,
        isNumericOnly: Boolean = false
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
            val isBiggestLine = lineHeight >= (maxBoxHeight * 0.75f).toInt() && maxBoxHeight > 12

            // ---- Numeric-voucher reading: merge the whole number run into one -------------
            val quotaOrPriceLine = Regex("""(?i)\b(gb|mb|kb|ريال|جيجا|ميجا)\b""").containsMatchIn(lineText)
            val numericRuns = if (quotaOrPriceLine) emptyList() else mergeNumericRuns(lineText)
            var addedNumeric = false
            for (mergedNumber in numericRuns) {
                if (mergedNumber.length !in MIN_CODE_LENGTH..MAX_NUMERIC_LENGTH) continue
                val lengthOk = expectedLength == null || expectedLength <= 0 || mergedNumber.length == expectedLength
                if (!lengthOk) continue
                val looksLikePhone = mergedNumber.length in 9..12 &&
                    phonePrefixes.any { mergedNumber.startsWith(it) } &&
                    !hasPositiveKeyword
                if (looksLikePhone && !isBiggestLine && !hasPositiveKeyword) continue

                var score = 45 + fontScore
                if (expectedLength != null && expectedLength > 0 && mergedNumber.length == expectedLength) score += 80
                if (hasPositiveKeyword) score += 45
                if (mergedNumber.length in 6..12) score += 25
                if (isBiggestLine) score += 35
                candidates.add(
                    ScannedVoucherCandidate(
                        code = mergedNumber,
                        detectedPrice = price,
                        detectedQuotaMb = quotaMb,
                        score = score,
                        numericCard = true
                    )
                )
                addedNumeric = true
            }

            // If the line was a pure number, don't also emit its short fragments.
            val wholeLineAsNumber = normalizeOpticalDigits(
                lineText.replace(separatorRegex, ""),
                isNumericOnly = true
            )
            if (addedNumeric && numericRuns.any { it == wholeLineAsNumber }) continue

            // ---- Mixed alphanumeric reading (letters + digits) ------------------------
            val tokens = Regex("""[A-Za-z0-9IlOQSZB|]{6,16}""").findAll(lineText).map { it.value }
            for (token in tokens) {
                // A token made only of digits (or of letters OCR mixes up with digits)
                // is a numeric voucher; otherwise it must mix letters and digits.
                val looksNumeric = token.all { it.isDigit() || DIGIT_CONFUSABLES.contains(it) }
                val opticalRepair = normalizeOpticalDigits(token, isNumericOnly = true)
                val normalized = if (looksNumeric) opticalRepair
                else toLatinDigits(token.trim().replace(" ", "").replace("-", ""))

                val maxLen = if (looksNumeric) MAX_NUMERIC_LENGTH else MAX_ALNUM_LENGTH
                if (normalized.length !in MIN_CODE_LENGTH..maxLen) continue

                val hasLetter = normalized.any { it.isLetter() }
                val hasDigit = normalized.any { it.isDigit() }

                if (isNumericOnly) {
                    if (!normalized.all { it.isDigit() }) continue
                } else {
                    if (!hasDigit) continue
                    if (!looksNumeric && commonWords.any { normalized.equals(it, ignoreCase = true) }) continue
                }
                if (normalized.length in 9..10 &&
                    phonePrefixes.any { normalized.startsWith(it) } &&
                    !hasPositiveKeyword &&
                    expectedLength != normalized.length
                ) {
                    continue
                }

                if (expectedLength != null && expectedLength > 0 && normalized.length != expectedLength) {
                    continue
                }

                var score = 20 + fontScore
                if (expectedLength != null && normalized.length == expectedLength) score += 80
                if (hasPositiveKeyword) score += 50
                if (normalized.length in 6..12) score += 20
                if (!isNumericOnly) {
                    if (hasLetter && hasDigit) score += 60
                }

                candidates.add(
                    ScannedVoucherCandidate(
                        code = normalized,
                        detectedPrice = price,
                        detectedQuotaMb = quotaMb,
                        score = score,
                        numericCard = looksNumeric
                    )
                )
            }
        }

        // Selection order:
        // 1. An unmistakable alphanumeric code (letters + digits) beats a plain number.
        // 2. Otherwise the best numeric line wins (numeric voucher cards).
        // 3. Otherwise any candidate.
        val bestAlnum = candidates
            .filter { c -> c.code.any { it.isLetter() } && c.code.any { it.isDigit() } && !c.numericCard }
            .maxByOrNull { it.score }
        if (bestAlnum != null && bestAlnum.score >= 110) return bestAlnum
        val bestNumeric = candidates.filter { it.numericCard }.maxByOrNull { it.score }
        if (bestNumeric != null && (bestNumeric.score >= 70 || isNumericOnly)) return bestNumeric
        return candidates.maxByOrNull { it.score }
    }

    private fun extractPrice(text: String): Double? {
        val m = Regex("""(\d{2,5})\s*(?:ريال|ر\.ي|ر\.س|RY|YER|SAR)""", RegexOption.IGNORE_CASE).find(text)
        return m?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun extractQuotaMb(text: String): Long? {
        val m = Regex("""(\d+(?:\.\d+)?)\s*(GB|MB|جيجا|ميجا)""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val u = m.groupValues[2].lowercase()
        return if (u.contains("gb") || u.contains("جيجا")) (v * 1024).toLong() else v.toLong()
    }
}
