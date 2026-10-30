package org.melodyne.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The app icon's four equaliser bars, alive: they dance while music plays and rest otherwise. */
@Composable
fun MelodyneMark(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mark")
    val a by t.animateFloat(0.30f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.25f, 0.95f, infiniteRepeatable(tween(690, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.40f, 1f, infiniteRepeatable(tween(440, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c")
    val d by t.animateFloat(0.20f, 0.90f, infiniteRepeatable(tween(610, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d")
    Canvas(modifier.size(30.dp)) {
        val rest = floatArrayOf(0.30f, 0.62f, 0.46f, 0.80f)
        val live = floatArrayOf(a, b, c, d)
        val w = size.width
        val h = size.height
        for (i in 0 until 4) {
            val x = w * (0.14f + i * 0.24f)
            val bar = (if (playing) live[i] else rest[i]) * h * 0.9f
            drawLine(color, Offset(x, h / 2 - bar / 2), Offset(x, h / 2 + bar / 2), strokeWidth = w * 0.13f, cap = StrokeCap.Round)
        }
    }
}

/** Placeholder row shown while the catalogue loads, pulsing instead of a bare "Loading…". */
@Composable
fun SkeletonRow() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val pulse by t.animateFloat(0.25f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).graphicsLayer { alpha = pulse },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Amoled.SurfaceHi))
        Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(160.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Amoled.SurfaceHi))
            Box(Modifier.width(100.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Amoled.SurfaceHi))
        }
    }
}
