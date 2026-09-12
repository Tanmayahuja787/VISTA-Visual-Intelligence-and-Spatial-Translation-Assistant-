package com.example.vista.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vista.domain.model.DetectionCategory
import com.example.vista.domain.model.DetectionItem
import com.example.vista.ui.camera.CameraPermissionCard
import com.example.vista.ui.camera.CameraPreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VistaMainScreen(
    modifier: Modifier = Modifier,
    viewModel: VistaViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionCard(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    } else {
        Scaffold(
            topBar = {
                VistaTopBar(
                    isTtsReady = uiState.isTtsReady,
                    isMuted = uiState.isMuted,
                    onToggleMute = { viewModel.toggleMute() }
                )
            },
            bottomBar = {
                VistaBottomControls(
                    isDetecting = uiState.isDetecting,
                    isGeminiEnabled = uiState.isGeminiEnabled,
                    onToggleDetection = { viewModel.toggleDetection() },
                    onToggleGemini = { viewModel.toggleGemini() },
                    onReplayAlert = { viewModel.repeatLastAlert() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Live camera viewfinder
                CameraPreview(
                    isDetecting = uiState.isDetecting,
                    onFrameAvailable = { imageProxy ->
                        viewModel.onFrameReceived(imageProxy)
                    }
                )

                DetectionBoxesOverlay(
                    detectedItems = uiState.detectedItems,
                    modifier = Modifier.fillMaxSize()
                )

                // Top status indicator banner over camera
                DetectionStatusBadge(
                    isDetecting = uiState.isDetecting,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )

                // Overlay card containing detected objects list
                DetectionFeedOverlay(
                    detectedItems = uiState.detectedItems,
                    lastSpokenAlert = uiState.lastSpokenAlert,
                    isDetecting = uiState.isDetecting,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

        }
    }
}

@Composable
private fun DetectionBoxesOverlay(
    detectedItems: List<DetectionItem>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        detectedItems.forEach { item ->
            val box = item.box ?: return@forEach
            val left = box.left.coerceIn(0f, 1f) * size.width
            val top = box.top.coerceIn(0f, 1f) * size.height
            val right = box.right.coerceIn(0f, 1f) * size.width
            val bottom = box.bottom.coerceIn(0f, 1f) * size.height
            drawRect(
                color = Color(0xFF00E676),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun VistaTopBar(
    isTtsReady: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "VISTA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "YOLOv8n • AI Assistive Companion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // TTS Readiness chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTtsReady) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFFE65100).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isTtsReady) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isTtsReady) Color(0xFF2E7D32) else Color(0xFFEF6C00))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTtsReady) "Audio Active" else "Audio Loading",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isTtsReady) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onToggleMute) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute alerts" else "Mute alerts",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DetectionStatusBadge(
    isDetecting: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isDetecting) Color(0xFF004D40).copy(alpha = 0.85f) else Color(0xFF212121).copy(alpha = 0.75f),
        label = "statusColor"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isDetecting) Color(0xFF00E676) else Color(0xFFB0BEC5))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isDetecting) "YOLOv8n • LIVE SCANNING" else "YOLOv8n • DETECTION PAUSED",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun DetectionFeedOverlay(
    detectedItems: List<DetectionItem>,
    lastSpokenAlert: String,
    isDetecting: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Header for detected objects
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detected Objects & Surroundings",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${detectedItems.size} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Spoken alert preview banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Spoken alert",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Last Alert: $lastSpokenAlert",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (detectedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDetecting) "Scanning surroundings for objects..." else "Press 'Start Detection' below to begin scanning.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(detectedItems, key = { it.id }) { item ->
                        DetectionItemRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionItemRow(item: DetectionItem) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormatter.format(Date(item.timestamp)) }

    val categoryColor = when (item.category) {
        DetectionCategory.OBSTACLE -> Color(0xFFD32F2F)
        DetectionCategory.PERSON -> Color(0xFF1976D2)
        DetectionCategory.TEXT_OCR -> Color(0xFF7B1FA2)
        DetectionCategory.OBJECT -> Color(0xFF388E3C)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${(item.confidence * 100).toInt()}% match",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = categoryColor
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun VistaBottomControls(
    isDetecting: Boolean,
    isGeminiEnabled: Boolean,
    onToggleDetection: () -> Unit,
    onToggleGemini: () -> Unit,
    onReplayAlert: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-contrast, large touch-target primary button
            Button(
                onClick = onToggleDetection,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDetecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isDetecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isDetecting) "Stop Detection" else "Start Detection",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDetecting) "STOP DETECTION" else "START DETECTION",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            FilledTonalButton(
                onClick = onToggleGemini,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isGeminiEnabled) Color(0xFFB39DDB) else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = if (isGeminiEnabled) "Disable Gemini" else "Enable Gemini",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isGeminiEnabled) "Gemini ON" else "Gemini",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Quick replay button to repeat the last spoken audio alert
            FilledTonalButton(
                onClick = onReplayAlert,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = "Repeat last spoken alert",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Repeat",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}
