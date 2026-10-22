package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 1:1 Port of iOS CropMaskView.swift:
 * - Viewfinder framing overlay for 3:4 viewport
 * - Thin border tracing viewport edge (White 0.45 opacity, 1dp)
 * - Yellow corner brackets (22dp arm, 2.5dp stroke, rounded cap)
 */
@Composable
fun CropMaskView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val arm = 22.dp.toPx()
            val borderW = 1.dp.toPx()
            val cornerW = 2.5.dp.toPx()

            // Thin border tracing viewport edge
            drawRect(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset(borderW / 2, borderW / 2),
                size = Size(w - borderW, h - borderW),
                style = Stroke(width = borderW)
            )

            // Yellow corner brackets
            val bracketColor = Color(0xFFFFD600).copy(alpha = 0.9f)
            val bracketStroke = Stroke(width = cornerW, cap = StrokeCap.Round)
            val inset = 1.dp.toPx()

            // Top-left
            val tl = Path().apply {
                moveTo(inset, inset + arm)
                lineTo(inset, inset)
                lineTo(inset + arm, inset)
            }
            drawPath(tl, bracketColor, style = bracketStroke)

            // Top-right
            val tr = Path().apply {
                moveTo(w - inset - arm, inset)
                lineTo(w - inset, inset)
                lineTo(w - inset, inset + arm)
            }
            drawPath(tr, bracketColor, style = bracketStroke)

            // Bottom-right
            val br = Path().apply {
                moveTo(w - inset, h - inset - arm)
                lineTo(w - inset, h - inset)
                lineTo(w - inset - arm, h - inset)
            }
            drawPath(br, bracketColor, style = bracketStroke)

            // Bottom-left
            val bl = Path().apply {
                moveTo(inset + arm, h - inset)
                lineTo(inset, h - inset)
                lineTo(inset, h - inset - arm)
            }
            drawPath(bl, bracketColor, style = bracketStroke)
        }
    }
}
