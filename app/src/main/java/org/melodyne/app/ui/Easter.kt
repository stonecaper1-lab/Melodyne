package org.melodyne.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The hidden screen: search "1509", or tap the version in Settings five times. Tap anywhere to leave. */
@Composable
fun EasterScreen(onClose: () -> Unit) {
    val accent = LocalAccent.current
    val t = rememberInfiniteTransition(label = "egg")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ring")
    val pulse by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension
            for (i in 0..2) {
                val p = (ring + i / 3f) % 1f
                drawCircle(accent.copy(alpha = (1f - p) * 0.55f), radius = base * 0.18f + p * base * 0.5f, style = Stroke(2.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Txt("1509", Modifier.scale(pulse), size = 96.sp, weight = FontWeight.Black, color = accent)
            MelodyneMark(true, accent, Modifier.size(44.dp))
            Txt("you found it", Modifier.padding(top = 4.dp), size = 13.sp, color = Amoled.Muted)
        }
    }
}
