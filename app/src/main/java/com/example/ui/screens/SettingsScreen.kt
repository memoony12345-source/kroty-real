package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.KrotyViewModel
import com.example.worker.SmartBalanceWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: KrotyViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val networks by viewModel.allNetworks.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val ghostMode by viewModel.ghostModeEnabled.collectAsState()
    val dataSaver by viewModel.dataSaverEnabled.collectAsState()
    val biometricLock by viewModel.biometricLockEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var backupJsonDialogText by remember { mutableStateOf<String?>(null) }
    var importJsonInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        feedbackBanner?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B))) {
                Text(it, modifier = Modifier.padding(12.dp), color = Color.White, fontSize = 13.sp)
            }
        }

        // General Preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الإعدادات الذكية والمظهر", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("وضع الدخول التلقائي بالبوابة", fontSize = 14.sp)
                    Switch(checked = ghostMode, onCheckedChange = { viewModel.setGhostMode(it) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("حماية المحفظة بالبصمة", fontSize = 14.sp)
                    Switch(checked = biometricLock, onCheckedChange = { viewModel.setBiometricLock(it) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("توفير بيانات الكرت داخل البوابة", fontSize = 14.sp)
                    Switch(checked = dataSaver, onCheckedChange = { viewModel.setDataSaver(it) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == "DARK", onClick = { viewModel.setThemeMode("DARK") }, label = { Text("داكن ملكي") })
                    FilterChip(selected = themeMode == "AMOLED", onClick = { viewModel.setThemeMode("AMOLED") }, label = { Text("أسود AMOLED") })
                    FilterChip(selected = themeMode == "LIGHT", onClick = { viewModel.setThemeMode("LIGHT") }, label = { Text("فاتح") })
                }

                Button(
                    onClick = {
                        SmartBalanceWorker.runOnceNow(context)
                        feedbackBanner = "✅ تم تشغيل فحص الرصيد الخلفي وتحديث الودجت الآن"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تشغيل فحص الرصيد الخلفي وتحديث الودجت الآن")
                }
            }
        }

        // Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("النسخ الاحتياطي والاستعادة (JSON)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                backupJsonDialogText = viewModel.exportBackupJson()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تصدير الكروت والشبكات")
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("استعادة نسخة")
                    }
                }
            }
        }

        // Discovered Dynamic Networks Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الشبكات المكتشفة تلقائياً (${networks.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (networks.isEmpty()) {
                    Text("لم يتم حفظ أي شبكة بعد. اتصل بالواي فاي ليتم اكتشاف البوابة تلقائياً.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    networks.forEach { net ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(net.ssid, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("البوابة: ${net.gatewayIp} • الدخول: ${net.loginUrl}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                                if (net.learnedInputSelector != null) {
                                    TextButton(onClick = {
                                        viewModel.updateNetworkSettings(net.copy(learnedInputSelector = null))
                                    }) {
                                        Text("مسح المربع المتعلم", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real Connection Logs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("السجل التشخيصي المباشر (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = { viewModel.clearLogs() }) {
                        Text("مسح السجل")
                    }
                }
                if (logs.isEmpty()) {
                    Text("لا توجد أحداث مسجلة بعد.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    logs.take(15).forEach { log ->
                        Text("• [${log.networkSsid}] ${log.messageAr}", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    }
                }
            }
        }
    }

    if (backupJsonDialogText != null) {
        val jsonText = backupJsonDialogText!!
        AlertDialog(
            onDismissRequest = { backupJsonDialogText = null },
            title = { Text("النسخة الاحتياطية (JSON)") },
            text = {
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, jsonText)
                    }
                    context.startActivity(Intent.createChooser(share, "مشاركة النسخة الاحتياطية"))
                }) {
                    Text("مشاركة / حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupJsonDialogText = null }) { Text("إغلاق") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("استعادة نسخة احتياطية") },
            text = {
                OutlinedTextField(
                    value = importJsonInput,
                    onValueChange = { importJsonInput = it },
                    label = { Text("الصق كود JSON هنا") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackupJson(importJsonInput) { _, msg ->
                        feedbackBanner = msg
                        showImportDialog = false
                    }
                }) {
                    Text("استيراد الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
