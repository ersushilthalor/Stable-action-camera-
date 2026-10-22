package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 1:1 Port of iOS FocusSquare:
 * - 76dp square with 4 yellow L-shaped corner brackets (14dp arm)
 * - 5dp center dot (yellow 0.6 opacity)
 * - Scales down from 1.4 -> 1.0 with spring animation
 * - Line opacity dims to 0.45 after 0.9s, then entire square fades out at 1.4s -> 1.75s
 */
@Composable
fun FocusSquare(
    onFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1.4f) }
    val lineOpacity = remember { Animatable(1.0f) }
    val totalOpacity = remember { Animatable(1.0f) }

    LaunchedEffect(Unit) {
        // Spring scale down: 1.4 -> 1.0
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
        )
        // Delay 0.9s, dim line opacity to 0.45
        delay(900)
        lineOpacity.animateTo(
            targetValue = 0.45f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, easing = LinearEasing)
        )
        // Delay to 1.4s, fade out to 0
        delay(250)
        totalOpacity.animateTo(
            targetValue = 0.0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
        onFinished?.invoke()
    }

    val yellowColor = Color(0xFFFFD600)
    val sizeDp = 76.dp
    val armDp = 14.dp

    Box(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = totalOpacity.value
            }
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val w = size.width
            val h = size.height
            val a = armDp.toPx()
            val strokeW = 1.5.dp.toPx()
            val effectiveAlpha = lineOpacity.value

            val stroke = Stroke(width = strokeW, cap = StrokeCap.Round)
            val strokeColor = yellowColor.copy(alpha = effectiveAlpha)

            // Top-Left corner
            val tl = Path().apply {
                moveTo(0f, a)
                lineTo(0f, 0f)
                lineTo(a, 0f)
            }
            drawPath(tl, strokeColor, style = stroke)

            // Top-Right corner
            val tr = Path().apply {
                moveTo(w - a, 0f)
                lineTo(w, 0f)
                lineTo(w, a)
            }
            drawPath(tr, strokeColor, style = stroke)

            // Bottom-Right corner
            val br = Path().apply {
                moveTo(w, h - a)
                lineTo(w, h)
                lineTo(w - a, h)
            }
            drawPath(br, strokeColor, style = stroke)

            // Bottom-Left corner
            val bl = Path().apply {
                moveTo(a, h)
                lineTo(0f, h)
                lineTo(0f, h - a)
            }
            drawPath(bl, strokeColor, style = stroke)

            // Center dot (5dp)
            drawCircle(
                color = yellowColor.copy(alpha = 0.6f * effectiveAlpha),
                radius = 2.5.dp.toPx(),
                center = Offset(w / 2f, h / 2f)
            )
        }
    }
}
