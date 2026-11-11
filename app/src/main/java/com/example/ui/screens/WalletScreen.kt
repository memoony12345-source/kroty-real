package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.VoucherEntity
import com.example.data.local.entity.VoucherStatus
import com.example.ui.KrotyViewModel
import com.example.ui.theme.*

@Composable
fun WalletScreen(
    viewModel: KrotyViewModel,
    isAuthenticated: Boolean,
    onRequestBiometricAuth: () -> Unit,
    onNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val vouchers by viewModel.allVouchers.collectAsState()
    val prefs by viewModel.preferences.collectAsState()

    var maskCodes by remember(prefs.maskVoucherCodes) { mutableStateOf(prefs.maskVoucherCodes) }
    var selectedQrVoucher by remember { mutableStateOf<VoucherEntity?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (prefs.biometricLockEnabled && !isAuthenticated) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KrotyDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = KrotyCyan, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("خزنة الكروت محمية بالبصمة", color = KrotyTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("يرجى تأكيد هويتك لعرض الكروت المحفوظة والاحتياطية.", color = KrotyTextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRequestBiometricAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan)
                    ) {
                        Text("فتح القفل بالبصمة", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val totalSpent = vouchers.sumOf { it.priceRials }
    val activeCount = vouchers.count { it.status == VoucherStatus.ACTIVE }
    val readyCount = vouchers.count { it.status == VoucherStatus.NEW || it.status == VoucherStatus.PAUSED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("محفظة الكروت والتحليل المالي", color = KrotyTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { maskCodes = !maskCodes }) {
                            Icon(
                                imageVector = if (maskCodes) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "إخفاء/إظهار",
                                tint = KrotyCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WalletStatColumn("إجمالي الكروت", "${vouchers.size}", KrotyCyan)
                        WalletStatColumn("جاهز بالاحتياط", "$readyCount", KrotyEmerald)
                        WalletStatColumn("نشط حالياً", "$activeCount", KrotyAmber)
                        WalletStatColumn("إجمالي التكلفة", "${totalSpent.toInt()} ريال", KrotyTextPrimary)
                    }
                }
            }
        }

        if (vouchers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KrotyTextSecondary, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("المحفظة فارغة حالياً", color = KrotyTextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "لا توجد أي كروت وهمية! قم بمسح كرت جديد بالكاميرا أو إضافته ليظهر هنا.",
                            color = KrotyTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(vouchers, key = { it.id }) { voucher ->
                VoucherWalletItemCard(
                    voucher = voucher,
                    maskCode = maskCodes,
                    onConnectNow = {
                        viewModel.activateAndConnectFromWallet(voucher)
                        onNavigateToRadar()
                    },
                    onCopy = { viewModel.copyVoucherSecurely(voucher.code) },
                    onShareQr = {
                        selectedQrVoucher = voucher
                        qrBitmap = viewModel.container.qrGenerator.generateQrBitmap(voucher.code)
                    },
                    onShareText = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "كود الكرت: ${voucher.code}\nالفئة: ${voucher.categoryLabel}\nالشبكة: ${voucher.ssid}"
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة الكرت"))
                    },
                    onDelete = { viewModel.deleteVoucher(voucher) }
                )
            }
        }
    }

    if (selectedQrVoucher != null && qrBitmap != null) {
        Dialog(onDismissRequest = { selectedQrVoucher = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("مشاركة الكرت عبر QR", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(selectedQrVoucher!!.code, color = KrotyTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { selectedQrVoucher = null }) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = KrotyTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun VoucherWalletItemCard(
    voucher: VoucherEntity,
    maskCode: Boolean,
    onConnectNow: () -> Unit,
    onCopy: () -> Unit,
    onShareQr: () -> Unit,
    onShareText: () -> Unit,
    onDelete: () -> Unit
) {
    val displayedCode = if (maskCode && voucher.code.length > 4) {
        "••••" + voucher.code.takeLast(3)
    } else {
        voucher.code
    }

    val statusColor = when (voucher.status) {
        VoucherStatus.ACTIVE -> KrotyEmerald
        VoucherStatus.NEW -> KrotyCyan
        VoucherStatus.PAUSED -> KrotyAmber
        VoucherStatus.EXHAUSTED -> KrotyCoralRed
    }

    val statusLabel = when (voucher.status) {
        VoucherStatus.ACTIVE -> "نشط الآن"
        VoucherStatus.NEW -> "احتياطي جاهز"
        VoucherStatus.PAUSED -> "مجمد مؤقتاً"
        VoucherStatus.EXHAUSTED -> "منتهي"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayedCode,
                    color = KrotyTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "الشبكة: ${voucher.ssid} • الفئة: ${voucher.categoryLabel}" +
                    (voucher.remainingMb?.let { " • المتبقي: ${String.format("%.1f MB", it)}" } ?: ""),
                color = KrotyTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onConnectNow,
                    colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = KrotyDarkBg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اتصال بالبوابة", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = KrotyCyan)
                    }
                    IconButton(onClick = onShareQr) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR", tint = KrotyCyan)
                    }
                    IconButton(onClick = onShareText) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = KrotyEmerald)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = KrotyCoralRed)
                    }
                }
            }
        }
    }
}
