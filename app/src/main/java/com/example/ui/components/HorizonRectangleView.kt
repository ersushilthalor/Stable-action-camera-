package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.model.MotionSnapshot
import kotlin.math.min

/**
 * 1:1 Port of iOS HorizonRectangleView.swift:
 * - A floating 3:4 rectangle centered in the sensor frame.
 * - Counter-rotates by -roll so the box stays upright.
 * - Shifts with translation offset (offsetX, offsetY) with margin dampening.
 */
@Composable
fun HorizonRectangleView(
    motion: MotionSnapshot,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cW = maxWidth
        val cH = maxHeight

        val rectW = min(cW.value, cH.value).dp * (3.0f / 5.0f) * 0.90f
        val rectH = rectW * (4.0f / 3.0f)

        val angleDegrees = -Math.toDegrees(motion.roll).toFloat()

        val marginX = (cW - rectW) / 2f
        val marginY = (cH - rectH) / 2f

        val shiftX = (motion.offsetX.toFloat() * marginX.value * 0.9f).dp
        val shiftY = -(motion.offsetY.toFloat() * marginY.value * 0.9f).dp

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = shiftX, y = shiftY)
                .size(width = rectW, height = rectH)
                .graphicsLayer {
                    rotationZ = angleDegrees
                }
        ) {
            // Subtle translucent fill
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(width = 1.5.dp, color = Color.White.copy(alpha = 0.6f))
            )

            // Viewfinder yellow corners
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val arm = 22.dp.toPx()
                val bracketColor = Color(0xFFFFD600)
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)

                // Top-left
                val tl = Path().apply {
                    moveTo(0f, arm)
                    lineTo(0f, 0f)
                    lineTo(arm, 0f)
                }
                drawPath(tl, bracketColor, style = stroke)

                // Top-right
                val tr = Path().apply {
                    moveTo(w - arm, 0f)
                    lineTo(w, 0f)
                    lineTo(w, arm)
                }
                drawPath(tr, bracketColor, style = stroke)

                // Bottom-right
                val br = Path().apply {
                    moveTo(w, h - arm)
                    lineTo(w, h)
                    lineTo(w - arm, h)
                }
                drawPath(br, bracketColor, style = stroke)

                // Bottom-left
                val bl = Path().apply {
                    moveTo(arm, h)
                    lineTo(0f, h)
                    lineTo(0f, h - arm)
                }
                drawPath(bl, bracketColor, style = stroke)
            }
        }
    }
}
