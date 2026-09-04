package com.platecheck.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.platecheck.app.ui.screens.MainScreen
import com.platecheck.app.ui.theme.PlateCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlateCheckTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlateCheckApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PlateCheckApp(
    viewModel: MainViewModel = viewModel()
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val uiState by viewModel.uiState.collectAsState()

    if (cameraPermissionState.status.isGranted) {
        MainScreen(
            uiState = uiState,
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onReset = viewModel::resetToIdle,
            onToggleDetails = viewModel::toggleDetails,
            onRetry = viewModel::retryFromError,
            onNavigateToHistory = viewModel::navigateToHistory
        )
    } else {
        CameraPermissionRequest(
            shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PlateCheck",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (shouldShowRationale) {
                "PlateCheck needs camera access to photograph your meals for on-device AI analysis. " +
                "No photos are uploaded — everything stays on your phone."
            } else {
                "To get started, PlateCheck needs access to your camera to take photos of your meals."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (shouldShowRationale) {
            // Permission was denied before — need to go to settings
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text("Open Settings")
            }
        } else {
            Button(onClick = onRequestPermission) {
                Text("Grant Camera Access")
            }
        }
    }
}
