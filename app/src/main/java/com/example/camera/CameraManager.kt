package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.RecordedVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraManager(private val context: Context) {

    enum class CameraType { WIDE, ULTRA_WIDE, TELEPHOTO }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _actionModeEnabled = MutableStateFlow(false)
    val actionModeEnabled: StateFlow<Boolean> = _actionModeEnabled.asStateFlow()

    private val _cameraType = MutableStateFlow(CameraType.WIDE)
    val cameraType: StateFlow<CameraType> = _cameraType.asStateFlow()

    private val _lastVideoUri = MutableStateFlow<Uri?>(null)
    val lastVideoUri: StateFlow<Uri?> = _lastVideoUri.asStateFlow()

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    private val _recordedVideos = MutableStateFlow<List<RecordedVideo>>(emptyList())
    val recordedVideos: StateFlow<List<RecordedVideo>> = _recordedVideos.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        loadExistingVideos()
    }

    fun setActionMode(enabled: Boolean) {
        _actionModeEnabled.value = enabled
        _cameraType.value = if (enabled) CameraType.ULTRA_WIDE else CameraType.WIDE
        rebindCamera()
    }

    fun setCameraType(type: CameraType) {
        _cameraType.value = type
        rebindCamera()
    }

    fun bindToLifecycle(
        owner: LifecycleOwner,
        previewSurfaceProvider: Preview.SurfaceProvider
    ) {
        this.lifecycleOwner = owner
        this.surfaceProvider = previewSurfaceProvider

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                rebindCamera()
            } catch (e: Exception) {
                Log.e("CameraManager", "Error obtaining ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun rebindCamera() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val surfaceProv = surfaceProvider ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = surfaceProv
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                videoCapture
            )

            // Adjust zoom factor based on cameraType / action mode
            camera?.let { cam ->
                val zoomInfo = cam.cameraInfo.zoomState.value
                val minZoom = zoomInfo?.minZoomRatio ?: 1.0f
                val maxZoom = zoomInfo?.maxZoomRatio ?: 1.0f
                when (_cameraType.value) {
                    CameraType.ULTRA_WIDE -> {
                        // Set to lowest available zoom (often < 1.0x if ultra-wide available)
                        cam.cameraControl.setZoomRatio(minZoom)
                    }
                    CameraType.WIDE -> {
                        val target = 1.0f.coerceIn(minZoom, maxZoom)
                        cam.cameraControl.setZoomRatio(target)
                    }
                    CameraType.TELEPHOTO -> {
                        val target = 2.0f.coerceIn(minZoom, maxZoom)
                        cam.cameraControl.setZoomRatio(target)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("CameraManager", "Use case binding failed", e)
        }
    }

    fun focusAt(normX: Float, normY: Float) {
        val cam = camera ?: return
        try {
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val point = factory.createPoint(normX, normY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.e("CameraManager", "Focus failed", e)
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val vc = videoCapture ?: return
        if (_isRecording.value) return

        val name = "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/StableAction")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val pendingRecording = vc.output.prepareRecording(context, mediaStoreOutputOptions)

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            try {
                pendingRecording.withAudioEnabled()
            } catch (e: SecurityException) {
                Log.w("CameraManager", "Audio permission denied or unavailable", e)
            }
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _isRecording.value = true
                }
                is VideoRecordEvent.Finalize -> {
                    _isRecording.value = false
                    if (!event.hasError()) {
                        val uri = event.outputResults.outputUri
                        _lastVideoUri.value = uri
                        loadExistingVideos()
                    } else {
                        Log.e("CameraManager", "Video recording error: ${event.error}")
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false
    }

    fun loadExistingVideos() {
        scope.launch(Dispatchers.IO) {
            val list = mutableListOf<RecordedVideo>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                val cursor = context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "Video"
                        val durationMs = c.getLong(durationCol)
                        val dateAdded = c.getLong(dateCol)
                        val dataPath = c.getString(dataCol) ?: ""

                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )

                        list.add(
                            RecordedVideo(
                                uri = contentUri,
                                fileAbsolutePath = dataPath,
                                durationSeconds = durationMs / 1000,
                                displayName = name,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }

                _recordedVideos.value = list
                if (_lastVideoUri.value == null && list.isNotEmpty()) {
                    _lastVideoUri.value = list.first().uri
                }
            } catch (e: Exception) {
                Log.e("CameraManager", "Error querying videos", e)
            }
        }
    }
}
