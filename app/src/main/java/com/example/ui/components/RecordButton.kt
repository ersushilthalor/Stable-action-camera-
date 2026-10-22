package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * 1:1 Port of iOS RecordButton:
 * - Outer circle with 5dp white stroke (78dp diameter)
 * - Inner red shape: 60dp circle when idle, morphs to 30dp rounded rect (6dp corner radius) when recording
 * - Spring animation specs (response: 0.3, dampingFraction: 0.65)
 */
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Morph inner size: 60dp -> 30dp
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 30.dp else 60.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "record_button_size"
    )

    // Morph inner corner radius: 30dp (circle) -> 6dp (rounded rectangle)
    val cornerRadius by animateDpAsState(
        targetValue = if (isRecording) 6.dp else 30.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "record_button_corner"
    )

    val innerShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(78.dp)
            .testTag("record_button")
            .clip(CircleShape)
            .border(width = 5.dp, color = Color.White, shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(innerShape)
                .background(Color(0xFFE53935)) // Vivid Camera Red
        )
    }
}
