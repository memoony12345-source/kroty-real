package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.KrotyViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: KrotyViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    val networks by viewModel.allNetworks.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importBackup(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Text("الأمان والأتمتة الذكية", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "قفل محفظة الكروت بالبصمة (Biometric Lock)",
                        subtitle = "حماية الكروت المخزنة ببصمة الإصبع أو الوجه",
                        checked = prefs.biometricLockEnabled,
                        onCheckedChange = {
                            scope.launch { viewModel.container.preferencesRepository.setBiometricLock(it) }
                        }
                    )

                    HorizontalDivider(color = KrotyCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "إخفاء أرقام الكروت افتراضياً (Privacy Mask)",
                        subtitle = "حماية أرقام الكروت من التلصص على الشاشة",
                        checked = prefs.maskVoucherCodes,
                        onCheckedChange = {
                            scope.launch { viewModel.container.preferencesRepository.setMaskVoucherCodes(it) }
                        }
                    )

                    HorizontalDivider(color = KrotyCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "تجميد الكرت عند إغلاق الشاشة (Auto-Freeze)",
                        subtitle = "إرسال طلب تسجيل خروج مؤقت لتوفير الرصيد",
                        checked = prefs.autoFreezeOnScreenOff,
                        onCheckedChange = {
                            scope.launch { viewModel.container.preferencesRepository.setAutoFreezeOnScreenOff(it) }
                        }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("النسخ الاحتياطي والاستعادة (JSON)", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "احفظ كافة كروتك وإعدادات شبكات المايكروتك في ملف خارجي أو استعدها في أي وقت.",
                        color = KrotyTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("kroty_net_backup.json") },
                            colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = KrotyDarkBg)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير الكروت", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = KrotyEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد نسخة", color = KrotyEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("الشبكات المكتشفة والمحفوظة (${networks.size})", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (networks.isEmpty()) {
                        Text("سيتم حفظ كل شبكة مايكروتك تتصل بها تلقائياً هنا مع بوابتها الخاصة.", color = KrotyTextSecondary, fontSize = 12.sp)
                    } else {
                        networks.forEach { net ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(net.ssid, color = KrotyTextPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        "البوابة: ${net.gatewayIp} • الحقل المخصص: ${net.voucherFieldSelector.ifBlank { "تلقائي" }}",
                                        color = KrotyTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch { viewModel.container.repository.deleteNetwork(net) }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = KrotyCoralRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            item {
                Text("سجل العمليات الحية", color = KrotyTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(logs.take(15)) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${log.eventType} • ${log.ssid}", color = KrotyCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(log.details, color = KrotyTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = KrotyTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = KrotyTextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
