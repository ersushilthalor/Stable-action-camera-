package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1:1 Port of iOS LiquidGlassActionToggle.swift:
 * - Frosted glass pill container (38dp height)
 * - Specular border gradient (topLeading to bottomTrailing)
 * - Sliding white capsule knob with spring physics (dampingFraction = 0.68)
 * - Smooth font color transitions between black (active) and white (inactive)
 * - Drag and tap interaction with haptic feedback
 */
@Composable
fun LiquidGlassActionToggle(
    isOn: Boolean,
    onToggleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val pillHeight = 38.dp
    val horizontalPad = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(pillHeight)
            .testTag("action_mode_toggle")
            .clip(CircleShape)
            .background(Color(0x38FFFFFF)) // Frosted ultra-thin material look
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(isOn) {
                detectTapGestures { offset ->
                    val newState = offset.x > size.width / 2f
                    if (newState != isOn) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleChanged(newState)
                    }
                }
            }
            .pointerInput(isOn) {
                detectDragGestures { change, _ ->
                    val newState = change.position.x > size.width / 2f
                    if (newState != isOn) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleChanged(newState)
                    }
                }
            }
    ) {
        val totalWidth = maxWidth
        val halfWidth = totalWidth / 2

        // Animated horizontal offset for the sliding white pill knob
        val knobOffset by animateDpAsState(
            targetValue = if (isOn) halfWidth + horizontalPad / 2 else horizontalPad / 2,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 400f),
            label = "knob_slide"
        )

        // Radial glow under the active option
        val glowAlpha by animateFloatAsState(
            targetValue = if (isOn) 0.20f else 0.10f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
            label = "glow_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isOn) {
                            listOf(Color.Transparent, Color.White.copy(alpha = glowAlpha))
                        } else {
                            listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent)
                        }
                    )
                )
        )

        // The sliding white capsule knob
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .padding(vertical = horizontalPad)
                .width(halfWidth - horizontalPad)
                .fillMaxHeight()
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )

        // Two labels: Normal and Action
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Normal Label (left)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Normal Mode",
                        tint = if (!isOn) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Normal",
                        color = if (!isOn) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Action Label (right)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Action Mode",
                        tint = if (isOn) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Action",
                        color = if (isOn) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
