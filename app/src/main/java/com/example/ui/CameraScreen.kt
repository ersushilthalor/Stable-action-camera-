package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.camera.CameraManager
import com.example.sensor.MotionManager
import com.example.ui.components.CropMaskView
import com.example.ui.components.FocusSquare
import com.example.ui.components.LiquidGlassActionToggle
import com.example.ui.components.RecordButton
import com.example.ui.gallery.GalleryView
import com.example.ui.player.VideoPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    cameraManager: CameraManager,
    motionManager: MotionManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRecording by cameraManager.isRecording.collectAsState()
    val actionModeEnabled by cameraManager.actionModeEnabled.collectAsState()
    val lastVideoUri by cameraManager.lastVideoUri.collectAsState()
    val recordedVideos by cameraManager.recordedVideos.collectAsState()
    val motionSnapshot by motionManager.snapshotFlow.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (hasCameraPermission) {
            cameraManager.rebindCamera()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    DisposableEffect(Unit) {
        motionManager.start()
        onDispose {
            motionManager.stop()
        }
    }

    // Modal states
    var showingPlayerUri by remember { mutableStateOf<Uri?>(null) }
    var showingGallery by remember { mutableStateOf(false) }

    // Tap to focus state
    var focusPosition by remember { mutableStateOf<Offset?>(null) }
    var focusVisible by remember { mutableStateOf(false) }

    // Manual sensor simulation for emulator testing
    var showSimControls by remember { mutableStateOf(false) }
    var simulatedRollAngle by remember { mutableFloatStateOf(0f) }

    // Smooth real-time update loop
    LaunchedEffect(motionSnapshot) {
        motionManager.updateSmoothing(motionSnapshot)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Bar: Credits & Support ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // "Buy me a coffee" badge button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://buymeacoffee.com/rudrashah")
                            )
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("buy_me_coffee_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Coffee,
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Buy me a coffee",
                            color = Color(0xFFFFD600),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                // Developer credits + Simulator toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSimControls = !showSimControls }
                ) {
                    Text(
                        text = "Developer: ",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Rudra Shah",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Main Camera Viewport ───────────────────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .testTag("viewfinder_container"),
                contentAlignment = Alignment.Center
            ) {
                val containerW = maxWidth
                val containerH = maxHeight

                // Tap-to-focus detector on viewport
                val tapModifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusPosition = offset
                        focusVisible = true
                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                        val normY = (offset.y / size.height).coerceIn(0f, 1f)
                        cameraManager.focusAt(normX, normY)
                    }
                }

                if (!actionModeEnabled) {
                    // ── NORMAL MODE: Full-Frame 1x direct camera preview ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(tapModifier)
                            .testTag("preview_normal_mode")
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    cameraManager.bindToLifecycle(lifecycleOwner, surfaceProvider)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // ── ACTION MODE: Stabilized Horizon-Lock 3:4 Viewport ──
                    // Exactly like Samsung Super Steady & iOS Stable Action:
                    // 3:4 portrait crop window floating centered in sensor output.
                    // The camera stream inside is scaled (~1.85) and counter-rotated by -roll!
                    val shorterSide = min(containerW.value, containerH.value).dp
                    val cropW = shorterSide * (3.0f / 5.0f) * 0.90f
                    val cropH = cropW * (4.0f / 3.0f)

                    val angleDeg = -Math.toDegrees(motionManager.smoothedRoll).toFloat()

                    val marginX = (containerW - cropW) / 2f
                    val marginY = (containerH - cropH) / 2f

                    val shiftX = (motionManager.smoothedNormX.toFloat() * marginX.value * 0.9f).dp
                    val shiftY = -(motionManager.smoothedNormY.toFloat() * marginY.value * 0.9f).dp

                    // Floating 3:4 Stabilized Window
                    Box(
                        modifier = Modifier
                            .size(width = cropW, height = cropH)
                            .clipToBounds()
                            .then(tapModifier)
                            .testTag("preview_action_mode_viewport")
                    ) {
                        // The inner preview layer counter-rotates and shifts to keep horizon level!
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Scale to ensure rotated content completely fills the 3:4 crop
                                    scaleX = 1.95f
                                    scaleY = 1.95f
                                    rotationZ = angleDeg
                                    translationX = shiftX.toPx()
                                    translationY = shiftY.toPx()
                                }
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        cameraManager.bindToLifecycle(lifecycleOwner, surfaceProvider)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Viewfinder 3:4 framing borders and yellow corner brackets
                        CropMaskView()
                    }
                }

                // ── Animated Tap-To-Focus Square ───────────────────────────
                if (focusVisible && focusPosition != null) {
                    val pos = focusPosition!!
                    FocusSquare(
                        onFinished = { focusVisible = false },
                        modifier = Modifier
                            .offset { IntOffset(pos.x.roundToInt() - 38.dp.roundToPx(), pos.y.roundToInt() - 38.dp.roundToPx()) }
                    )
                }

                // ── REC Pulsing Indicator (Top Center) ─────────────────────
                if (isRecording) {
                    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 14.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("rec_indicator")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ── Emulator Simulation Toolbar (Optional) ─────────────────────
            if (showSimControls) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Horizon Roll Sim: ${simulatedRollAngle.roundToInt()}°",
                            color = Color(0xFFFFD600),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Reset",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(0.2f))
                                .clickable {
                                    simulatedRollAngle = 0f
                                    motionManager.setManualSimAngleDegrees(0f)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Slider(
                        value = simulatedRollAngle,
                        onValueChange = { angle ->
                            simulatedRollAngle = angle
                            motionManager.setManualSimAngleDegrees(angle)
                        },
                        valueRange = -180f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFD600),
                            activeTrackColor = Color(0xFFFFD600)
                        )
                    )
                }
            }

            // ── Bottom Controls ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Controls Row: Thumbnail | Record Button | Gallery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Video Thumbnail Button (Last clip)
                    VideoThumbnailButton(
                        uri = lastVideoUri,
                        onClick = {
                            if (lastVideoUri != null) {
                                showingPlayerUri = lastVideoUri
                            } else if (recordedVideos.isNotEmpty()) {
                                showingPlayerUri = recordedVideos.first().uri
                            }
                        }
                    )

                    // Record Button (Central white ring with morphing red shape)
                    RecordButton(
                        isRecording = isRecording,
                        onClick = {
                            if (hasCameraPermission && hasAudioPermission) {
                                cameraManager.toggleRecording()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                )
                            }
                        }
                    )

                    // Gallery Button (Opens all recordings)
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { showingGallery = true }
                            .testTag("gallery_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Open Gallery",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // iOS-Style Liquid Glass Action Toggle (Normal vs Action Mode)
                LiquidGlassActionToggle(
                    isOn = actionModeEnabled,
                    onToggleChanged = { enabled ->
                        cameraManager.setActionMode(enabled)
                    },
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        // ── Fullscreen Video Player ────────────────────────────────────────
        showingPlayerUri?.let { uri ->
            VideoPlayerView(
                videoUri = uri,
                onDismiss = { showingPlayerUri = null }
            )
        }

        // ── In-App Gallery ─────────────────────────────────────────────────
        if (showingGallery) {
            GalleryView(
                videos = recordedVideos,
                onDismiss = { showingGallery = false }
            )
        }

        // ── Permission Denied Modal ────────────────────────────────────────
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF262626))
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Camera access required",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Stable Action requires Camera and Microphone access to record horizon-locked video.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFD600))
                            .clickable {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Grant Permission",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1:1 Port of iOS VideoThumbnailView:
 * 54x54 dp with 10dp rounded corners, subtle white 0.35 border, play icon overlay.
 */
@Composable
fun VideoThumbnailButton(
    uri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        if (uri == null) {
            thumbBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(100_000)
                thumbBitmap = frame
                retriever.release()
            } catch (e: Exception) {
                thumbBitmap = null
            }
        }
    }

    Box(
        modifier = modifier
            .size(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .testTag("video_thumbnail_button"),
        contentAlignment = Alignment.Center
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = "Last recording thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play last video",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "No video yet",
                tint = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
