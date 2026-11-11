package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.KrotyViewModel
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.*

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as KrotyApplication

        setContent {
            KrotyNetTheme {
                val krotyViewModel: KrotyViewModel = viewModel(
                    factory = KrotyViewModel.provideFactory(applicationContext, app.container)
                )

                var selectedTab by remember { mutableIntStateOf(0) }
                var isBiometricAuthenticated by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    krotyViewModel.container.wifiRadarEngine.refreshSnapshot()
                    krotyViewModel.syncLiveMikroTikSession(silent = true)
                }

                LaunchedEffect(Unit) {
                    val perms = mutableListOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                }

                Scaffold(
                    containerColor = KrotyDarkBg,
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "كروتي نت • Kroty Net",
                                        color = KrotyCyan,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = KrotyCardBg),
                            actions = {
                                IconButton(
                                    onClick = { krotyViewModel.syncLiveMikroTikSession(silent = false) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "مزامنة الجلسة الحية",
                                        tint = KrotyEmerald
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = KrotyCardBg) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                                label = { Text("الرادار والبوابة") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                label = { Text("الماسح") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                label = { Text("المحفظة") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("الإعدادات") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> RadarScreen(
                                viewModel = krotyViewModel,
                                onRequestLocationEnable = {
                                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            )
                            1 -> ScannerScreen(
                                viewModel = krotyViewModel,
                                onNavigateToRadar = { selectedTab = 0 }
                            )
                            2 -> WalletScreen(
                                viewModel = krotyViewModel,
                                isAuthenticated = isBiometricAuthenticated,
                                onRequestBiometricAuth = {
                                    authenticateWithBiometrics {
                                        isBiometricAuthenticated = true
                                    }
                                },
                                onNavigateToRadar = { selectedTab = 0 }
                            )
                            3 -> SettingsScreen(viewModel = krotyViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun authenticateWithBiometrics(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد الهوية لفتح خزنة الكروت")
            .setSubtitle("استخدم البصمة أو قفل الشاشة لعرض الكروت المحفوظة")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
