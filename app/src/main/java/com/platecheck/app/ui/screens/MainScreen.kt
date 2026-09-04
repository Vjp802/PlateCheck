package com.platecheck.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import com.platecheck.app.ui.theme.PlateCheckTheme
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.createBitmap
import com.platecheck.app.model.AnalysisResult
import com.platecheck.app.model.HistoryItem
import com.platecheck.app.model.UiState
import com.platecheck.app.model.Verdict
import com.platecheck.app.ui.theme.GreenContainer
import com.platecheck.app.ui.theme.Green40
import com.platecheck.app.ui.theme.RedContainer
import com.platecheck.app.ui.theme.Red40
import com.platecheck.app.ui.theme.YellowContainer
import com.platecheck.app.ui.theme.Yellow40
import java.util.concurrent.Executors

private const val TAG = "MainScreen"

@Composable
fun MainScreen(
    uiState: UiState,
    onPhotoCaptured: (Bitmap) -> Unit,
    onReset: () -> Unit,
    onToggleDetails: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PlateCheck",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            // History button (only in Idle/Camera state)
            if (uiState is UiState.Idle) {
                IconButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = "View History")
                }
            }
            
            // Back button (only in History state)
            if (uiState is UiState.History) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Camera")
                }
            }
        }

        when (uiState) {
            is UiState.Setup -> SetupContent(uiState)
            is UiState.Idle -> IdleContent(onPhotoCaptured)
            is UiState.History -> HistoryListContent(uiState.items)
            is UiState.Analyzing -> AnalyzingContent(uiState, onReset)
            is UiState.Result -> ResultContent(uiState, onReset, onToggleDetails)
            is UiState.Unavailable -> UnavailableContent(uiState)
            is UiState.Error -> ErrorContent(uiState, onReset, onRetry)
        }
    }
}

// ─── Setup State ─────────────────────────────────────────────────────────────

@Composable
private fun SetupContent(state: UiState.Setup) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Idle State (Camera) ──────────────────────────────────────────────────

@Composable
private fun IdleContent(
    onPhotoCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = loadBitmapFromUri(context, uri)
            bitmap?.let { onPhotoCaptured(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            if (!LocalInspectionMode.current) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = CameraPreview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Camera bind failed", e)
                            }
                        },
                        ContextCompat.getMainExecutor(context)
                    )
                }
            } else {
                // Preview placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera Preview", color = Color.White)
                }
            }

            // Buttons overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Button
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "Pick from Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Capture button
                IconButton(
                    onClick = {
                        imageCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(imageProxy)
                                    imageProxy.close()
                                    if (bitmap != null) {
                                        onPhotoCaptured(bitmap)
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(TAG, "Capture failed", exception)
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        .padding(4.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Capture",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(88.dp)) // Maintain balance or add another button
            }
        }
        
        Text(
            text = "Snap or upload a meal photo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp).align(Alignment.CenterHorizontally)
        )
    }
}

// ─── History State (Full List) ───────────────────────────────────────────────

@Composable
private fun HistoryListContent(items: List<HistoryItem>) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No history yet.\nTake a photo to start tracking!",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                CompactHistoryItem(item)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactHistoryItem(item: HistoryItem) {
    val context = LocalContext.current
    val accentColor = when (item.result.verdict) {
        Verdict.GOOD -> Green40
        Verdict.NOT_RECOMMENDED -> Red40
        Verdict.MODIFY -> Yellow40
    }
    
    val timeString = DateFormat.getTimeFormat(context).format(item.timestamp)
    val dateString = DateFormat.getDateFormat(context).format(item.timestamp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.result.verdict.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                Text(
                    text = "$dateString at $timeString",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (item.result.foodItems.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.result.foodItems.forEach { food ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(food, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                text = item.result.reason,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Analyzing State ─────────────────────────────────────────────────────────

@Composable
private fun AnalyzingContent(state: UiState.Analyzing, onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanProgress"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                bitmap = state.photo.asImageBitmap(),
                contentDescription = "Captured meal",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Scanning Line Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.15f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = scanProgress * 500f // Adjust based on box height roughly
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Analyzing your plate...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Gemini Nano is looking at your food",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

// ─── Result State ────────────────────────────────────────────────────────────

@Composable
private fun ResultContent(
    state: UiState.Result,
    onReset: () -> Unit,
    onToggleDetails: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Image(
            bitmap = state.photo.asImageBitmap(),
            contentDescription = "Analyzed meal",
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        VerdictCard(
            result = state.result,
            showDetails = state.showDetails
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Plate")
            }
            OutlinedButton(
                onClick = onToggleDetails,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (state.showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.showDetails) "Hide Logic" else "AI Logic")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictCard(
    result: AnalysisResult,
    showDetails: Boolean
) {
    val (containerColor, accentColor, icon, title) = when (result.verdict) {
        Verdict.GOOD -> VerdictStyle(GreenContainer, Green40, Icons.Filled.Check, "Looks Good")
        Verdict.NOT_RECOMMENDED -> VerdictStyle(RedContainer, Red40, Icons.Filled.Close, "Not Recommended")
        Verdict.MODIFY -> VerdictStyle(YellowContainer, Yellow40, Icons.Filled.Edit, "Modify")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            if (result.foodItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Detected Items:",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    result.foodItems.forEach { food ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(food) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = result.reason,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
            )

            if ((result.verdict == Verdict.MODIFY) && result.suggestion.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "💡 ${result.suggestion}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(
                visible = showDetails,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Raw on-device response:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.rawResponse,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

private data class VerdictStyle(
    val containerColor: Color,
    val accentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String
)

// ─── Unavailable State ───────────────────────────────────────────────────────

@Composable
private fun UnavailableContent(state: UiState.Unavailable) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Device Not Supported",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Error State ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    state: UiState.Error,
    onReset: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = state.photo.asImageBitmap(),
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )

            state.rawResponse?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset) {
                    Text("New Plate")
                }
                Button(onClick = onRetry) {
                    Text("Try Again")
                }
            }
        }
    }
}

// ─── Utilities ───────────────────────────────────────────────────────────────

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer[bytes]
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    // Correct rotation
    val rotation = imageProxy.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load bitmap from URI", e)
        null
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun PreviewMainIdle() {
    PlateCheckTheme {
        MainScreen(
            uiState = UiState.Idle,
            onPhotoCaptured = {},
            onReset = {},
            onToggleDetails = {},
            onRetry = {},
            onNavigateToHistory = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewResultGood() {
    val mockBitmap = createMockBitmap(Color.Green)
    PlateCheckTheme {
        MainScreen(
            uiState = UiState.Result(
                photo = mockBitmap,
                result = AnalysisResult(
                    foodItems = listOf("Grilled Chicken", "Quinoa", "Spinach"),
                    verdict = Verdict.GOOD,
                    reason = "Perfectly balanced meal with lean protein, complex carbs, and greens.",
                    suggestion = "",
                    rawResponse = "VERDICT: GOOD\nITEMS: Chicken, Quinoa, Spinach"
                )
            ),
            onPhotoCaptured = {},
            onReset = {},
            onToggleDetails = {},
            onRetry = {},
            onNavigateToHistory = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewResultModify() {
    val mockBitmap = createMockBitmap(Color.Yellow)
    PlateCheckTheme {
        MainScreen(
            uiState = UiState.Result(
                photo = mockBitmap,
                result = AnalysisResult(
                    foodItems = listOf("Pasta Carbonara"),
                    verdict = Verdict.MODIFY,
                    reason = "This meal is calorie-dense and lacks fiber.",
                    suggestion = "Add a side salad or some steamed vegetables to balance the plate.",
                    rawResponse = "VERDICT: MODIFY\nITEMS: Pasta"
                )
            ),
            onPhotoCaptured = {},
            onReset = {},
            onToggleDetails = {},
            onRetry = {},
            onNavigateToHistory = {}
        )
    }
}

private fun createMockBitmap(color: Color): Bitmap {
    val bitmap = createBitmap(400, 300, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.color = color.toArgb()
    canvas.drawRect(0f, 0f, 400f, 300f, paint)
    return bitmap
}
