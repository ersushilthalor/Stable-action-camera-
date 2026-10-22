package com.example.model

import android.net.Uri

data class MotionSnapshot(
    val roll: Double = 0.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0
)

data class RecordedVideo(
    val uri: Uri,
    val fileAbsolutePath: String,
    val durationSeconds: Long,
    val displayName: String,
    val dateAdded: Long
)
