package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v3.6: reads the notification Android itself puts up when a Wi-Fi needs a login
 * ("تسجيل الدخول إلى شبكة Wi-Fi" / "Sign in to network").
 *
 * The notification does not carry the portal URL in readable form (Android keeps it in
 * a PendingIntent), but it is the fastest and surest *trigger*: the moment it appears we
 * know this network has a portal, we know its SSID, and we start our own bound probe
 * before the system dialog has even finished drawing.
 */
class PortalNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val pkg = n.packageName.orEmpty()
        val isCaptivePkg = pkg == "com.google.android.captiveportallogin" ||
            pkg.contains("captiveportal", true)
        val extras = n.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val blob = "$title $text $big"

        if (!isCaptivePkg && !looksLikeSignInNotification(blob, pkg)) return

        val ssid = extractSsid(text.ifBlank { big }.ifBlank { title })
        PortalEvents.publishSignInRequest(ssid, pkg, blob.trim())
    }

    private fun looksLikeSignInNotification(blob: String, pkg: String): Boolean {
        val allowedPackage = pkg == "android" ||
            pkg.contains("systemui", true) ||
            pkg.contains("wifi", true) ||
            pkg.contains("settings", true) ||
            pkg.contains("captiveportal", true)
        if (!allowedPackage) return false
        val t = blob.lowercase()
        return t.contains("تسجيل الدخول") || t.contains("الشبكة") ||
            t.contains("sign in") || t.contains("sign-in") ||
            t.contains("captive") || t.contains("log in to") ||
            t.contains("network login")
    }

    private fun extractSsid(raw: String): String =
        raw.replace("\u200f", "").replace("\u200e", "")
            .replace("\"", "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    companion object {
        fun isEnabled(context: Context): Boolean {
            return try {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ).orEmpty()
                flat.contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }

        fun componentName(context: Context) =
            ComponentName(context, PortalNotificationListener::class.java)
    }
}

/** In-process bridge: the listener wakes the app, nothing is stored about the user. */
object PortalEvents {

    private val _signInRequests = MutableSharedFlow<PortalSignInEvent>(extraBufferCapacity = 4)
    val signInRequests: SharedFlow<PortalSignInEvent> = _signInRequests.asSharedFlow()

    private val _lastSignIn = MutableStateFlow<PortalSignInEvent?>(null)
    val lastSignIn: StateFlow<PortalSignInEvent?> = _lastSignIn.asStateFlow()

    fun publishSignInRequest(ssid: String, packageName: String, rawText: String) {
        val event = PortalSignInEvent(ssid, packageName, rawText, System.currentTimeMillis())
        _lastSignIn.value = event
        _signInRequests.tryEmit(event)
    }
}

data class PortalSignInEvent(
    val ssid: String,
    val packageName: String,
    val rawText: String,
    val atMillis: Long
)
