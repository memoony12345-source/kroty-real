package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.KrotyViewModel
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.AuroraBackground
import com.example.ui.theme.KrotyGlassNavBar
import com.example.ui.theme.KrotyNavItem
import com.example.ui.theme.KrotyNetTheme
import com.example.ui.theme.KrotyTopBar

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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

            val versionName = remember {
                runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName?.substringBefore("-")
                }.getOrNull() ?: "3.7"
            }

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

            val navItems = remember {
                listOf(
                    KrotyNavItem("الرادار", Icons.Default.Radar),
                    KrotyNavItem("الماسح", Icons.Default.QrCodeScanner),
                    KrotyNavItem("المحفظة", Icons.Default.AccountBalanceWallet),
                    KrotyNavItem("الإعدادات", Icons.Default.Settings)
                )
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KrotyNetTheme(themeMode = themeMode) {
                    AuroraBackground {
                        Scaffold(
                            containerColor = Color.Transparent,
                            topBar = {
                                Box(modifier = Modifier.statusBarsPadding()) {
                                    KrotyTopBar(version = versionName)
                                }
                            },
                            bottomBar = {
                                Box(modifier = Modifier.navigationBarsPadding()) {
                                    KrotyGlassNavBar(
                                        items = navItems,
                                        selectedIndex = selectedTab,
                                        onSelect = { selectedTab = it }
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
}
