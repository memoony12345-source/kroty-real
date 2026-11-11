package com.example.domain.web

data class ParsedMikroTikStatus(
    val isLoggedInStatusPage: Boolean = false,
    val activeUsername: String? = null,
    val remainingMegabytes: Double? = null,
    val remainingTimeSeconds: Long? = null,
    val errorState: String? = null,
    val errorMessageAr: String? = null
)

object BalanceAndErrorParser {

    fun parseStatusOrLoginHtml(url: String, rawHtmlOrText: String): ParsedMikroTikStatus {
        val cleanText = rawHtmlOrText
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val error = detectError(cleanText)
        val username = extractActiveUsername(cleanText, rawHtmlOrText)
        val remainingMb = extractRemainingMb(cleanText)
        val remainingSec = extractRemainingSeconds(cleanText)

        val isStatus = url.contains("/status", ignoreCase = true) ||
                cleanText.contains("تسجيل الخروج") ||
                cleanText.contains("log off", ignoreCase = true) ||
                cleanText.contains("logout", ignoreCase = true) ||
                remainingMb != null ||
                remainingSec != null

        return ParsedMikroTikStatus(
            isLoggedInStatusPage = isStatus && error == null,
            activeUsername = username,
            remainingMegabytes = remainingMb,
            remainingTimeSeconds = remainingSec,
            errorState = error?.first,
            errorMessageAr = error?.second
        )
    }

    private fun extractActiveUsername(cleanText: String, rawHtml: String): String? {
        val patterns = listOf(
            Regex("""(?:Welcome|مرحبا|مرحباً|أهلا|اهلا)\s+([A-Za-z0-9_-]{4,20})""", RegexOption.IGNORE_CASE),
            Regex("""(?:المستخدم|اسم المستخدم|رمز الكرت|رقم الكرت|الكرت|الإسم|الاسم|username|logged in as)\s*[:\-]?\s*([A-Za-z0-9_-]{4,20})""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val match = p.find(cleanText)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && !isReservedWord(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun isReservedWord(word: String): Boolean {
        val lower = word.lowercase()
        return lower in setOf("user", "username", "status", "login", "logout", "mikrotik", "admin", "hotspot", "true", "false")
    }

    private fun extractRemainingMb(text: String): Double? {
        // Priority 1: Look near balance keywords
        val contextRegex = Regex(
            """(?:الرصيد المتبقي|باقي التحميل|المتبقي من الباقة|البيانات المتبقية|المتبقي|remain[^\d]{0,20}|bytes left[^\d]{0,20}|traffic left[^\d]{0,20})\s*[:\-]?\s*(\d+(?:[.,]\d+)?)\s*(GB|MB|KB|TB|GiB|MiB|KiB|جيجا|كيلو|ميجا|غ.ب|م.ب)""",
            RegexOption.IGNORE_CASE
        )
        contextRegex.find(text)?.let { match ->
            return convertToMb(match.groupValues[1], match.groupValues[2])
        }

        // Priority 2: General data unit scan on status page
        val generalRegex = Regex(
            """(\d+(?:[.,]\d+)?)\s*(GB|MB|GiB|MiB|جيجا|ميجا|غ\.ب|م\.ب)""",
            RegexOption.IGNORE_CASE
        )
        val allMatches = generalRegex.findAll(text).mapNotNull {
            convertToMb(it.groupValues[1], it.groupValues[2])
        }.toList()

        return allMatches.firstOrNull()
    }

    private fun convertToMb(numStr: String, unit: String): Double? {
        val value = numStr.replace(",", ".").toDoubleOrNull() ?: return null
        val u = unit.lowercase()
        return when {
            u.contains("tb") -> value * 1024.0 * 1024.0
            u.contains("gb") || u.contains("gib") || u.contains("جيجا") || u.contains("غ") -> value * 1024.0
            u.contains("kb") || u.contains("kib") || u.contains("كيلو") -> value / 1024.0
            else -> value
        }
    }

    private fun extractRemainingSeconds(text: String): Long? {
        // MikroTik standard compact format e.g. 2d5h30m10s or 5h20m
        val compact = Regex("""(?:(\d+)\s*w)?\s*(?:(\d+)\s*d)?\s*(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?\s*(?:(\d+)\s*s)""", RegexOption.IGNORE_CASE)
        for (m in compact.findAll(text)) {
            val w = m.groupValues[1].toLongOrNull() ?: 0L
            val d = m.groupValues[2].toLongOrNull() ?: 0L
            val h = m.groupValues[3].toLongOrNull() ?: 0L
            val min = m.groupValues[4].toLongOrNull() ?: 0L
            val s = m.groupValues[5].toLongOrNull() ?: 0L
            val total = w * 604800 + d * 86400 + h * 3600 + min * 60 + s
            if (total > 0 && (w > 0 || d > 0 || h > 0 || min > 0)) {
                return total
            }
        }
        return null
    }

    fun detectError(text: String): Pair<String, String>? {
        val lower = text.lowercase()
        return when {
            lower.contains("simultaneous session") || lower.contains("already logged in") || lower.contains("مستخدم في جهاز") || lower.contains("متصل حاليا") ->
                "IN_USE" to "⚠️ هذا الكرت مفتوح حالياً في جهاز آخر!"
            lower.contains("traffic limit") || lower.contains("download limit") || lower.contains("انتهى الرصيد") || lower.contains("نفدت الباقة") || lower.contains("انتهت الباقة") ->
                "EXPIRED_DATA" to "نفد رصيد التحميل (الميجابايت) لهذا الكرت"
            lower.contains("uptime limit") || lower.contains("انتهى الوقت") || lower.contains("انتهت صلاحية") ->
                "EXPIRED_TIME" to "انتهى الوقت المسموح لهذا الكرت"
            lower.contains("invalid username") || lower.contains("invalid password") || lower.contains("غير صحيح") || lower.contains("خطأ في رمز") ->
                "INVALID" to "رقم الكرت غير صحيح في هذه الشبكة"
            else -> null
        }
    }
}
