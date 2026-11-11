package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.local.entity.VoucherEntity
import com.example.domain.wallet.VoucherQrGenerator
import com.example.ui.KrotyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WalletScreen(
    viewModel: KrotyViewModel,
    onConnectNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vouchers by viewModel.allVouchers.collectAsState()
    val biometricEnabled by viewModel.biometricLockEnabled.collectAsState()

    var isUnlocked by remember { mutableStateOf(!biometricEnabled) }
    var maskCodes by remember { mutableStateOf(false) }
    var qrVoucher by remember { mutableStateOf<VoucherEntity?>(null) }

    LaunchedEffect(biometricEnabled) {
        if (!biometricEnabled) isUnlocked = true
    }

    if (biometricEnabled && !isUnlocked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text("المحفظة محمية بالبصمة الحيوية", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    val executor = ContextCompat.getMainExecutor(context)
                    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            isUnlocked = true
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            isUnlocked = true
                        }
                    })
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("فتح محفظة كروتي نت")
                        .setSubtitle("استخدم البصمة لعرض الكروت المحفوظة")
                        .setNegativeButtonText("فتح مباشر")
                        .build()
                    prompt.authenticate(info)
                } else {
                    isUnlocked = true
                }
            }) {
                Text("تحقق بالبصمة لفتح المحفظة")
            }
        }
        return
    }

    val totalPrice = vouchers.sumOf { it.price ?: 0.0 }
    val queuedCount = vouchers.count { it.status == "NEW_QUEUED" }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Analytics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("إجمالي الكروت: ${vouchers.size} (الاحتياط: $queuedCount)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("إجمالي التكلفة المسجلة: ${totalPrice.toInt()} ريال", fontSize = 13.sp, color = Color(0xFF38BDF8))
                }
                IconButton(onClick = { maskCodes = !maskCodes }) {
                    Icon(
                        imageVector = if (maskCodes) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "إخفاء/إظهار الأرقام"
                    )
                }
            }
        }

        if (vouchers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("المحفظة فارغة حالياً", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("امسح كرتاً بالكاميرا أو اتصل بالبوابة ليتم حفظ كروتك الحقيقية هنا.", fontSize = 13.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vouchers, key = { it.id }) { voucher ->
                    val displayCode = if (maskCodes && voucher.code.length > 4)
                        "${voucher.code.take(2)}****${voucher.code.takeLast(2)}"
                    else voucher.code

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayCode, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                Text(
                                    text = when (voucher.status) {
                                        "ACTIVE" -> "نشط ✅"
                                        "PAUSED" -> "مجمد ❄️"
                                        "NEW_QUEUED" -> "احتياطي في الطابور ⏳"
                                        else -> voucher.status
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF10B981)
                                )
                            }

                            Text(
                                text = "الشبكة: ${voucher.networkSsid.ifBlank { "عام" }} • المتبقي: ${voucher.remainingMegabytes?.let { "${String.format("%.1f", it)} MB" } ?: "غير مقروء"}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.activateExistingVoucher(voucher)
                                        onConnectNavigateToRadar()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("دخول الآن", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { qrVoucher = voucher }) {
                                    Icon(Icons.Default.QrCode, contentDescription = "QR", modifier = Modifier.size(18.dp))
                                }
                                OutlinedButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("voucher", voucher.code))
                                    scope.launch {
                                        delay(60000)
                                        try { cm.setPrimaryClip(ClipData.newPlainText("", "")) } catch (_: Exception) {}
                                    }
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteVoucher(voucher) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (qrVoucher != null) {
        val v = qrVoucher!!
        val qrBitmap = remember(v.code) { VoucherQrGenerator.generateQrBitmap(v.code, 512) }
        AlertDialog(
            onDismissRequest = { qrVoucher = null },
            title = { Text("مشاركة الكرت عبر QR Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier.size(220.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("رقم الكرت: ${v.code}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "كرت شبكة (${v.networkSsid}): ${v.code}")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة الكرت"))
                }) {
                    Text("مشاركة كنص")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrVoucher = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}
