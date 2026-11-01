package org.melodyne.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * "Liquid glass": a translucent body, a soft sheen across the top half, and a hairline edge that catches light
 * on one corner and picks up the accent colour on the other.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), alpha: Float = 0.08f, strong: Boolean = false): Modifier {
    val accent = LocalAccent.current
    val edge = if (strong) 0.55f else 0.36f
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha * 1.8f), Color.White.copy(alpha = alpha * 0.5f))))
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), startY = 0f, endY = size.height * 0.5f))
        }
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = edge), Color.White.copy(alpha = 0.05f), accent.copy(alpha = edge * 0.6f))),
            shape,
        )
}

/** Bottom sheets and dialogs: nearly opaque so text stays readable, with the same glass edge. */
@Composable
fun Modifier.glassSheet(shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)): Modifier =
    this.clip(shape).background(Color(0xF2080808)).glass(shape, alpha = 0.05f, strong = true)

/** Two slow, drifting pools of colour behind everything, so the glass has something to refract. Off = pure black. */
@Composable
fun AmbientGlow(accent: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "glow")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val second = lerp(accent, Color(0xFF6A5CFF), 0.55f)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c1 = Offset(w * (0.10f + 0.55f * a), h * (0.10f + 0.12f * b))
        val c2 = Offset(w * (0.95f - 0.6f * b), h * (0.78f + 0.12f * a))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), center = c1, radius = w * 0.95f), radius = w * 0.95f, center = c1)
        drawCircle(Brush.radialGradient(listOf(second.copy(alpha = 0.16f), Color.Transparent), center = c2, radius = w * 0.85f), radius = w * 0.85f, center = c2)
    }
}
