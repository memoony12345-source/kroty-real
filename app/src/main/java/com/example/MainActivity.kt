package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.KrotyViewModel
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.KrotyNetTheme

class MainActivity : FragmentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KrotyApplication).container

        setContent {
            val krotyViewModel: KrotyViewModel = viewModel(
                factory = KrotyViewModel.factory(
                    context = this,
                    repository = container.repository,
                    preferences = container.preferences,
                    wifiRadar = container.wifiRadarEngine,
                    portalProber = container.portalProber
                )
            )

            val themeMode by krotyViewModel.themeMode.collectAsState()
            var selectedTab by remember { mutableStateOf(0) }

            // Request Camera, Location (for SSID/BSSID), and Notification permissions immediately on launch!
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                krotyViewModel.refreshEverything()
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
                permissionsLauncher.launch(perms.toTypedArray())
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KrotyNetTheme(themeMode = themeMode) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text("كروتي نت • Kroty Pro v3.2", fontWeight = FontWeight.Bold)
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                                    label = { Text("الرادار والشبكة") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                    label = { Text("الماسح الذكي") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                    label = { Text("محفظة الكروت") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text("الإعدادات والسجل") }
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
                                    onNavigateToScanner = { selectedTab = 1 }
                                )
                                1 -> ScannerScreen(
                                    viewModel = krotyViewModel,
                                    onConnectedNavigateToRadar = { selectedTab = 0 }
                                )
                                2 -> WalletScreen(
                                    viewModel = krotyViewModel,
                                    onConnectNavigateToRadar = { selectedTab = 0 }
                                )
                                3 -> SettingsScreen(viewModel = krotyViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
