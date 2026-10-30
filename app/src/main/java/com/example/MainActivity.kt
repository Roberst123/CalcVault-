package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.VaultDashboardScreen
import com.example.ui.theme.CalcVaultTheme
import com.example.viewmodel.CalcVaultViewModel
import com.example.viewmodel.VaultScreenState
import com.example.viewmodel.VaultTab

class MainActivity : FragmentActivity() {
    private val viewModel: CalcVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CalcVaultTheme {
                val screenState by viewModel.screenState.collectAsState()
                val statusMessage by viewModel.statusMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Manage FLAG_SECURE dynamically to prevent screenshots & app switcher previews when vault is active
                LaunchedEffect(screenState) {
                    if (screenState == VaultScreenState.VAULT_DASHBOARD && viewModel.authManager.isFlagSecureEnabled()) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // Show snackbars for status updates
                LaunchedEffect(statusMessage) {
                    statusMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearStatusMessage()
                    }
                }

                // Hardware Back button handling
                val currentTab by viewModel.currentTab.collectAsState()
                val activeViewer by viewModel.activeViewerItem.collectAsState()
                val isSelectionMode by viewModel.isSelectionMode.collectAsState()

                BackHandler(enabled = screenState == VaultScreenState.VAULT_DASHBOARD) {
                    if (activeViewer != null) {
                        viewModel.closeMediaViewer()
                    } else if (isSelectionMode) {
                        viewModel.deselectAll()
                    } else if (currentTab != VaultTab.PHOTOS) {
                        viewModel.setTab(VaultTab.PHOTOS)
                    } else {
                        viewModel.lockVault()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { _ ->
                    Crossfade(targetState = screenState, label = "ScreenTransition") { state ->
                        when (state) {
                            VaultScreenState.PIN_SETUP,
                            VaultScreenState.CALCULATOR,
                            VaultScreenState.RATE_LIMITED -> {
                                CalculatorScreen(viewModel = viewModel)
                            }
                            VaultScreenState.VAULT_DASHBOARD -> {
                                VaultDashboardScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.authManager.onAppMovedToBackground()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.screenState.value == VaultScreenState.VAULT_DASHBOARD) {
            if (viewModel.authManager.shouldAutoLock()) {
                viewModel.lockVault()
            }
        }
        viewModel.authManager.clearBackgroundTimestamp()
    }
}
