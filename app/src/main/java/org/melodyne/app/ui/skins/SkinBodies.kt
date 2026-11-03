package org.melodyne.app.ui.skins

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.player.PlayerUiState
import org.melodyne.app.ui.AccentSlider
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.fmtMs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SkinState(
    val ps: PlayerUiState, val pos: LongState, val dur: LongState, val live: IntState, val avg: IntState,
    val audiophile: Boolean, val eqActive: Boolean, val infinite: Boolean, val balance: Float,
)

class SkinActions(
    val onToggle: () -> Unit, val onNext: () -> Unit, val onPrev: () -> Unit, val onSeekTo: (Long) -> Unit,
    val onEq: () -> Unit, val onLyrics: () -> Unit, val onQueue: () -> Unit,
)

@Composable
fun SkinBody(id: String, st: SkinState, act: SkinActions, modifier: Modifier = Modifier) {
    when (id) {
        "amp" -> AmpBody(st, act, modifier)
        "tape" -> TapeBody(st, act, modifier)
        else -> DeckBody(st, act, modifier)
    }
}

private fun kbps(st: SkinState): String {
    val k = if (st.live.intValue > 0) st.live.intValue else st.ps.quality?.kbps ?: 0
    return if (k > 0) "$k" else "---"
}

private fun khz(st: SkinState): String = st.ps.quality?.sampleRateHz?.takeIf { it > 0 }?.let { "%.0f".format(it / 1000f) } ?: "--"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Marquee(text: String, color: Color, size: Int, modifier: Modifier = Modifier) {
    BasicText(
        text, modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
        style = TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        maxLines = 1, softWrap = false,
    )
}

private fun SkinActions.scrub(st: SkinState, dir: Int) =
    onSeekTo((st.pos.longValue + dir * 5000L).coerceIn(0L, maxOf(0L, st.dur.longValue)))

// ------------------------------------------------------------------ Classic Amp
@Composable
private fun AmpBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val volume = rememberSystemVolume()
    var remaining by remember { mutableStateOf(false) }
    val green = Color(0xFF00FF66)
    val dim = Color(0xFF0B3A1E)
    val shown = if (remaining) st.pos.longValue - st.dur.longValue else st.pos.longValue
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).panel(Color(0xFF3A3F47), Color(0xFF22262C), Color(0xFF6B7380)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF040E08)).border(2.dp, Color(0xFF1B2A20), RoundedCornerShape(6.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    (if (remaining) "-" else " ") + fmtMs(kotlin.math.abs(shown)), Modifier.clickable { remaining = !remaining },
                    style = TextStyle(color = green, fontFamily = FontFamily.Monospace, fontSize = 38.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                Canvas(Modifier.size(width = 150.dp, height = 46.dp)) {
                    val bands = levels.value.bands
                    val bw = size.width / 16f
                    for (i in 0 until 16) {
                        val hgt = size.height * bands[i].coerceIn(0f, 1f)
                        drawRect(dim, Offset(i * bw + 1f, 0f), Size(bw - 3f, size.height))
                        drawRect(green, Offset(i * bw + 1f, size.height - hgt), Size(bw - 3f, hgt))
                    }
                }
            }
            Marquee("${st.ps.title} - ${st.ps.artist}", green, 15)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lamp("${kbps(st)} KBPS", true, green); Lamp("${khz(st)} KHZ", true, green); Lamp("STEREO", true, green)
                Lamp("PURE", st.audiophile, green); Lamp("EQ", st.eqActive, green); Lamp("INF", st.infinite, green)
            }
        }
        RetroSeek(st.pos, st.dur, green, onSeek = act.onSeekTo)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("VOL", size = 12.sp, color = green, fontFamily = FontFamily.Monospace)
            AccentSlider(volume.level.value, volume.set, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("BAL", size = 12.sp, color = green, fontFamily = FontFamily.Monospace)
            AccentSlider((st.balance + 1f) / 2f, { v -> ServiceLocator.playback.setBalance(v * 2f - 1f) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RetroBtn(G.Prev, green, onClick = act.onPrev)
            RetroBtn(if (st.ps.isPlaying) G.Pause else G.Play, green, onClick = act.onToggle)
            RetroBtn(G.Next, green, onClick = act.onNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabelBtn("EQ", green, st.eqActive, act.onEq); LabelBtn("PL", green, false, act.onQueue); LabelBtn("LYR", green, false, act.onLyrics)
        }
    }
}

// ------------------------------------------------------------------ Cassette
@Composable
private fun TapeBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val amber = Color(0xFFFFB347)
    val rot = remember { mutableFloatStateOf(0f) }
    val playing = st.ps.isPlaying
    LaunchedEffect(playing) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L && playing) rot.floatValue = (rot.floatValue + (t - last) / 1e9f * 150f) % 360f
            last = t
        }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRoundRect(Color(0xFF2B2420), Offset.Zero, Size(w, h), CornerRadius(18.dp.toPx()))
                drawRoundRect(Color(0xFFE9DDC2), Offset(10.dp.toPx(), 10.dp.toPx()), Size(w - 20.dp.toPx(), h - 20.dp.toPx()), CornerRadius(12.dp.toPx()))
                drawRoundRect(Color(0xFFF7F0DC), Offset(26.dp.toPx(), 22.dp.toPx()), Size(w - 52.dp.toPx(), 62.dp.toPx()), CornerRadius(6.dp.toPx()))
                val wx = w * 0.18f; val wy = h * 0.55f; val ww = w * 0.64f; val wh = h * 0.34f
                drawRoundRect(Color(0xFF1A1512), Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2))
                val p = if (st.dur.longValue > 0) (st.pos.longValue.toFloat() / st.dur.longValue).coerceIn(0f, 1f) else 0f
                val maxR = wh * 0.47f
                val minR = wh * 0.22f
                val left = Offset(wx + wh / 2, wy + wh / 2)
                val right = Offset(wx + ww - wh / 2, wy + wh / 2)
                drawCircle(Color(0xFF4A3524), maxR - (maxR - minR) * p, left)           // the tape moves from the left spool to the right as the song plays
                drawCircle(Color(0xFF4A3524), minR + (maxR - minR) * p, right)
                for (c in listOf(left, right)) {
                    drawCircle(Color(0xFFD9D4C7), minR * 0.85f, c)
                    rotate(rot.floatValue, c) {
                        for (k in 0 until 6) {
                            val a = k * PI / 3
                            drawLine(Color(0xFF2B2420), c, Offset(c.x + minR * 0.8f * cos(a).toFloat(), c.y + minR * 0.8f * sin(a).toFloat()), 2.dp.toPx())
                        }
                    }
                }
            }
            Column(Modifier.padding(start = 36.dp, end = 36.dp, top = 28.dp)) {
                Txt(st.ps.title, size = 17.sp, color = Color(0xFF3B2A1A), weight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
                Txt(st.ps.artist, size = 13.sp, color = Color(0xFF6B5A44), fontFamily = FontFamily.Cursive)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VuMeter(levels.value.l, "L", amber, Modifier.weight(1f))
            VuMeter(levels.value.r, "R", amber, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                "%04d".format(st.pos.longValue / 1000),
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0E0B09)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = amber, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Lamp("${kbps(st)} KBPS", true, amber); Lamp("PURE", st.audiophile, amber); Lamp("EQ", st.eqActive, amber)
        }
        RetroSeek(st.pos, st.dur, amber, onSeek = act.onSeekTo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScrubButton(G.Prev, amber, -1, act.onPrev) { d -> act.scrub(st, d) }
            RetroBtn(if (playing) G.Pause else G.Play, amber, onClick = act.onToggle)
            ScrubButton(G.Next, amber, 1, act.onNext) { d -> act.scrub(st, d) }
        }
        Txt("Tap to skip, hold to rewind or fast-forward", size = 11.sp, color = Amoled.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabelBtn("EQ", amber, st.eqActive, act.onEq); LabelBtn("QUEUE", amber, false, act.onQueue); LabelBtn("LYRICS", amber, false, act.onLyrics)
        }
    }
}

@Composable
private fun VuMeter(level: Float, label: String, color: Color, modifier: Modifier) {
    Box(modifier.height(86.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFE4C8)).border(2.dp, Color(0xFF2B2420), RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.92f
            val len = size.height * 0.78f
            for (k in 0..10) {
                val a = (-50.0 + k * 10.0) * PI / 180
                val inner = len * 0.88f
                drawLine(if (k >= 8) Color(0xFFB3261E) else Color(0xFF3B2A1A), Offset(cx + inner * sin(a).toFloat(), cy - inner * cos(a).toFloat()), Offset(cx + len * sin(a).toFloat(), cy - len * cos(a).toFloat()), 2.dp.toPx())
            }
            val a = (-50.0 + level.coerceIn(0f, 1f) * 100.0) * PI / 180
            drawLine(Color(0xFF1A1512), Offset(cx, cy), Offset(cx + len * sin(a).toFloat(), cy - len * cos(a).toFloat()), 2.5.dp.toPx())
        }
        Txt(label, Modifier.align(Alignment.BottomStart).padding(8.dp), size = 11.sp, weight = FontWeight.Bold, color = Color(0xFF3B2A1A))
    }
}

// ------------------------------------------------------------------ Receiver
@Composable
private fun DeckBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val volume = rememberSystemVolume()
    val amber = Color(0xFFFFB000)
    val eqPreset = ServiceLocator.eq.state.value.presetId
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).panel(Color(0xFF4A4B4F), Color(0xFF2A2B2E), Color(0xFF86878C)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF140A00)).border(2.dp, Color(0xFF3A2A12), RoundedCornerShape(6.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Marquee("${st.ps.title.uppercase()}  /  ${st.ps.artist.uppercase()}", amber, 16)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(kbps(st), style = TextStyle(color = amber, fontFamily = FontFamily.Monospace, fontSize = 44.sp, fontWeight = FontWeight.Bold))
                Txt("KBPS", Modifier.padding(bottom = 8.dp), size = 12.sp, color = amber, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                BasicText(fmtMs(st.pos.longValue), style = TextStyle(color = amber, fontFamily = FontFamily.Monospace, fontSize = 22.sp))
            }
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val bands = levels.value.bands
                val cw = size.width / 16f
                val rows = 10
                val rh = size.height / rows
                for (i in 0 until 16) {
                    val lit = (bands[i].coerceIn(0f, 1f) * rows).toInt()
                    for (r in 0 until rows) {
                        val on = r < lit
                        val c = when { r < 6 -> Color(0xFF3DDC84); r < 8 -> amber; else -> Color(0xFFFF4B3E) }
                        drawRect(if (on) c else c.copy(alpha = 0.12f), Offset(i * cw + 1.5f, size.height - (r + 1) * rh + 1.5f), Size(cw - 3f, rh - 3f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lamp("PURE DIRECT", st.audiophile, amber); Lamp("LOUDNESS", eqPreset == "low_volume" || eqPreset == "car", amber)
                Lamp("EQ", st.eqActive, amber); Lamp("INFINITE", st.infinite, amber)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Knob(volume.level.value, volume.set, amber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("TUNING", size = 11.sp, color = Color(0xFFC8CDD6), fontFamily = FontFamily.Monospace)
                RetroSeek(st.pos, st.dur, amber, onSeek = act.onSeekTo)
                Txt("VOLUME  ${(volume.level.value * 100).toInt()}", size = 11.sp, color = Color(0xFFC8CDD6), fontFamily = FontFamily.Monospace)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RetroBtn(G.Prev, amber, onClick = act.onPrev)
            RetroBtn(if (st.ps.isPlaying) G.Pause else G.Play, amber, onClick = act.onToggle)
            RetroBtn(G.Next, amber, onClick = act.onNext)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabelBtn("EQ", amber, st.eqActive, act.onEq); LabelBtn("QUEUE", amber, false, act.onQueue); LabelBtn("LYRICS", amber, false, act.onLyrics)
        }
    }
}

/** A volume knob you turn by dragging around it. */
@Composable
private fun Knob(value: Float, onChange: (Float) -> Unit, color: Color) {
    Canvas(
        Modifier.size(120.dp).pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val deg = Math.toDegrees(atan2((change.position.y - c.y).toDouble(), (change.position.x - c.x).toDouble()))
                val v = (((deg + 225.0 + 360.0) % 360.0) / 270.0).toFloat()          // 270 degree sweep, dead zone at the bottom
                if (v <= 1f) onChange(v)
                change.consume()
            }
        },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        for (k in 0..10) {
            val a = (135.0 + k * 27.0) * PI / 180
            drawLine(if (k / 10f <= value) color else Color(0xFF5A5B60), Offset(c.x + r * 0.9f * cos(a).toFloat(), c.y + r * 0.9f * sin(a).toFloat()), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 3.dp.toPx())
        }
        drawCircle(Color(0xFF1B1B1D), r * 0.78f, c)
        drawCircle(Color(0xFF8A8C92), r * 0.74f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        val a = (135.0 + value.coerceIn(0f, 1f) * 270.0) * PI / 180
        drawLine(color, Offset(c.x + r * 0.3f * cos(a).toFloat(), c.y + r * 0.3f * sin(a).toFloat()), Offset(c.x + r * 0.68f * cos(a).toFloat(), c.y + r * 0.68f * sin(a).toFloat()), 4.dp.toPx())
    }
}
