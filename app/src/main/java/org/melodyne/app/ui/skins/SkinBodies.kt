package org.melodyne.app.ui.skins

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Song
import org.melodyne.app.player.PlayerUiState
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.Txt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

class SkinState(
    val ps: PlayerUiState, val pos: LongState, val dur: LongState, val live: IntState, val avg: IntState,
    val audiophile: Boolean, val eqActive: Boolean, val infinite: Boolean, val balance: Float,
)

class SkinActions(
    val onToggle: () -> Unit, val onNext: () -> Unit, val onPrev: () -> Unit, val onSeekTo: (Long) -> Unit,
    val onEq: () -> Unit, val onLyrics: () -> Unit, val onQueue: () -> Unit,
    val onClose: () -> Unit, val queue: () -> Pair<List<Song>, Int>, val onJump: (Int) -> Unit,
)

@Composable
fun SkinBody(id: String, st: SkinState, act: SkinActions, modifier: Modifier = Modifier) {
    when (id) {
        "amp" -> AmpBody(st, act, modifier)
        "tape" -> TapeBody(st, act, modifier)
        else -> DeckBody(st, act, modifier)
    }
}

private val MONO = FontFamily.Monospace

private fun kbps(st: SkinState): String {
    val k = if (st.live.intValue > 0) st.live.intValue else st.ps.quality?.kbps ?: 0
    return if (k > 0) "$k" else "---"
}

private fun khz(st: SkinState): String = st.ps.quality?.sampleRateHz?.takeIf { it > 0 }?.let { "%.0f".format(it / 1000f) } ?: "--"

/** Meters read in dB like real ones: 0..1 across the last 48 dB. */
private fun dbLevel(p: Float): Float = ((20f * log10(p.coerceAtLeast(1e-4f)) + 48f) / 48f).coerceIn(0f, 1f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Marquee(text: String, color: Color, size: Int, modifier: Modifier = Modifier) {
    BasicText(
        text, modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
        style = TextStyle(color = color, fontSize = size.sp, fontFamily = MONO, fontWeight = FontWeight.Bold),
        maxLines = 1, softWrap = false,
    )
}

private fun SkinActions.scrub(st: SkinState, dir: Int) =
    onSeekTo((st.pos.longValue + dir * 5000L).coerceIn(0L, maxOf(0L, st.dur.longValue)))

private fun SkinActions.stop(st: SkinState) { if (st.ps.isPlaying) onToggle(); onSeekTo(0L) }

// ================================================================== Classic Amp
/** A 90s desktop player: titled windows (with shade and close), 7-segment time, spectrum with falling caps, a playlist window. */
@Composable
private fun AmpBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val volume = rememberSystemVolume()
    var remaining by remember { mutableStateOf(false) }
    var shade by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(false) }
    var eqWin by remember { mutableStateOf(false) }
    val eq by ServiceLocator.eq.state.collectAsState()
    val green = Color(0xFF00FF66)
    val timeText = (if (remaining) "-" else "") + mmss(if (remaining) st.dur.longValue - st.pos.longValue else st.pos.longValue)
    val playing = st.ps.isPlaying

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmpWindow("MELODYNE AMP", onShade = { shade = !shade }, onClose = act.onClose) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Color(0xFF040E08)).border(2.dp, Color(0xFF1B2A20), RoundedCornerShape(2.dp))
                    .scanlines().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.clickable { remaining = !remaining }) { SevenSeg(timeText, green, 16.dp, 30.dp) }
                    Spacer(Modifier.weight(1f))
                    if (!shade) Canvas(Modifier.size(width = 150.dp, height = 44.dp)) {
                        val f = levels.value
                        val bw = size.width / 16f
                        val brush = Brush.verticalGradient(listOf(Color(0xFFFF3B2F), Color(0xFFFFE600), Color(0xFF00E050)), startY = 0f, endY = size.height)
                        for (i in 0 until 16) {
                            val h = size.height * f.bands[i].coerceIn(0f, 1f)
                            drawRect(brush, Offset(i * bw + 1f, size.height - h), Size(bw - 2f, h))
                            val cy = size.height - size.height * f.caps[i].coerceIn(0f, 1f)
                            drawRect(Color(0xFFB8FFD0), Offset(i * bw + 1f, cy - 2f), Size(bw - 2f, 2f))
                        }
                    }
                }
                Marquee("${st.ps.title} - ${st.ps.artist}", green, 14)
                if (!shade) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column { SevenSeg(kbps(st), green, 7.dp, 13.dp); Txt("KBPS", size = 8.sp, color = green.copy(alpha = 0.6f), fontFamily = MONO) }
                    Column { SevenSeg(khz(st), green, 7.dp, 13.dp); Txt("KHZ", size = 8.sp, color = green.copy(alpha = 0.6f), fontFamily = MONO) }
                    Spacer(Modifier.weight(1f))
                    Lamp("MONO", false, green); Lamp("STEREO", true, green)
                }
            }
            if (!shade) {
                RetroSeek(st.pos, st.dur, green, onSeek = act.onSeekTo)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("VOL", size = 11.sp, color = green, fontFamily = MONO)
                    AmpSlider(volume.level.value, volume.set, Modifier.weight(1f))
                    Txt("BAL", size = 11.sp, color = green, fontFamily = MONO)
                    AmpSlider((st.balance + 1f) / 2f, { v -> ServiceLocator.playback.setBalance(v * 2f - 1f) }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RetroBtn(G.Prev, green, width = 48.dp, onClick = act.onPrev)
                    RetroBtn(G.Play, green, width = 48.dp) { if (!playing) act.onToggle() }
                    RetroBtn(G.Pause, green, width = 48.dp) { if (playing) act.onToggle() }
                    RetroBtn(G.Stop, green, width = 48.dp) { act.stop(st) }
                    RetroBtn(G.Next, green, width = 48.dp, onClick = act.onNext)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelBtn("EQ", green, eqWin) { eqWin = !eqWin }
                    LabelBtn("PL", green, playlist) { playlist = !playlist }
                    LabelBtn("LYR", green, false, act.onLyrics)
                    Spacer(Modifier.weight(1f))
                    Lamp("PURE", st.audiophile, green); Lamp("INF", st.infinite, green)
                }
            }
        }
        // The equalizer window: the same seven bands as everywhere else. "Preamp" is the automatic headroom that keeps boosts from clipping.
        if (eqWin) AmpWindow("EQUALIZER", onShade = null, onClose = { eqWin = false }) {
            val gains = eq.gains
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelBtn("ON", green, eq.enabled) { ServiceLocator.eq.setEnabled(!eq.enabled) }
                LabelBtn(if (eq.presetId == org.melodyne.app.audio.CUSTOM_ID) "CUSTOM" else org.melodyne.app.audio.EqPresets.byId(eq.presetId).name.uppercase().take(12), green, false) {
                    val all = org.melodyne.app.audio.EqPresets.all
                    val i = all.indexOfFirst { it.id == eq.presetId }
                    ServiceLocator.eq.setPreset(all[(i + 1) % all.size].id)
                }
                Spacer(Modifier.weight(1f))
                Txt("PREAMP -%.1f dB".format(org.melodyne.app.audio.EqMath.headroomDb(gains)), size = 11.sp, color = green, fontFamily = MONO)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                gains.forEachIndexed { i, g -> AmpBand(org.melodyne.app.audio.EQ_LABELS[i], g, green) { ServiceLocator.eq.setBand(i, it) } }
            }
        }
        if (playlist) AmpWindow("PLAYLIST EDITOR", onShade = null, onClose = { playlist = false }) {
            val (queue, index) = remember(st.ps.songId) { act.queue() }
            Column(Modifier.fillMaxWidth().background(Color(0xFF040E08)).padding(8.dp)) {
                queue.take(80).forEachIndexed { i, s ->
                    Txt(
                        "${i + 1}. ${s.title} - ${s.artist}",
                        Modifier.fillMaxWidth().clickable { act.onJump(i) }.padding(vertical = 3.dp),
                        size = 12.sp, fontFamily = MONO, weight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == index) Color(0xFFE6FFE9) else green.copy(alpha = 0.75f),
                    )
                }
                if (queue.isEmpty()) Txt("Nothing queued.", size = 12.sp, color = green.copy(alpha = 0.6f), fontFamily = MONO)
            }
        }
    }
}

/** A titled window with the little stripes and buttons of a 90s desktop player. */
@Composable
private fun AmpWindow(title: String, onShade: (() -> Unit)?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3A3F47), Color(0xFF22262C))))
            .border(2.dp, Brush.linearGradient(listOf(Color(0xFF8A92A0), Color(0xFF14171B))), RoundedCornerShape(3.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(22.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                var y = 0f
                var k = 0
                while (y < size.height) { drawRect(if (k % 2 == 0) Color(0xFF5B6B8A) else Color(0xFF3D4A66), Offset(0f, y), Size(size.width, 2f)); y += 2f; k++ }
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(title, size = 11.sp, weight = FontWeight.Bold, color = Color(0xFFE6E0C8), fontFamily = MONO)
                Spacer(Modifier.weight(1f))
                if (onShade != null) Txt("_", Modifier.clickable(onClick = onShade).padding(horizontal = 8.dp), size = 14.sp, weight = FontWeight.Bold, color = Color.White, fontFamily = MONO)
                Txt("×", Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp), size = 14.sp, weight = FontWeight.Bold, color = Color.White, fontFamily = MONO)
            }
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

// ================================================================== Cassette deck
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
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val slide by animateFloatAsState(if (entered) 0f else 260f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "insert")   // the tape is "put in"

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(214.dp).graphicsLayer { translationY = slide; alpha = (1f - slide / 300f).coerceIn(0f, 1f) }) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRoundRect(Color(0xFF2B2420), Offset.Zero, Size(w, h), CornerRadius(18.dp.toPx()))
                drawRoundRect(Color(0xFFE9DDC2), Offset(10.dp.toPx(), 10.dp.toPx()), Size(w - 20.dp.toPx(), h - 20.dp.toPx()), CornerRadius(12.dp.toPx()))
                // label with ruled lines
                val lx = 26.dp.toPx(); val ly = 22.dp.toPx(); val lw = w - 52.dp.toPx(); val lh = 70.dp.toPx()
                drawRoundRect(Color(0xFFF7F0DC), Offset(lx, ly), Size(lw, lh), CornerRadius(6.dp.toPx()))
                for (k in 1..3) drawLine(Color(0xFFB9C7DB), Offset(lx + 8.dp.toPx(), ly + k * lh / 4f), Offset(lx + lw - 8.dp.toPx(), ly + k * lh / 4f), 1f)
                drawRect(Color(0xFFD94F3D), Offset(lx, ly), Size(6.dp.toPx(), lh))                    // the red edge stripe
                // window with tape-length ticks
                val wx = w * 0.18f; val wy = h * 0.52f; val ww = w * 0.64f; val wh = h * 0.30f
                drawRoundRect(Color(0xFF1A1512), Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2))
                var tx = wx + wh
                while (tx < wx + ww - wh) { drawLine(Color(0x33FFFFFF), Offset(tx, wy + 3.dp.toPx()), Offset(tx, wy + 8.dp.toPx()), 1f); tx += 14.dp.toPx() }
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
                // four screws
                for (c in listOf(Offset(20.dp.toPx(), 20.dp.toPx()), Offset(w - 20.dp.toPx(), 20.dp.toPx()), Offset(20.dp.toPx(), h - 20.dp.toPx()), Offset(w - 20.dp.toPx(), h - 20.dp.toPx()))) {
                    drawCircle(Color(0xFFB9B3A3), 5.dp.toPx(), c)
                    rotate(35f, c) { drawLine(Color(0xFF5C5648), Offset(c.x - 4.dp.toPx(), c.y), Offset(c.x + 4.dp.toPx(), c.y), 1.5.dp.toPx()) }
                }
            }
            Column(Modifier.padding(start = 40.dp, end = 90.dp, top = 22.dp)) {
                Txt(st.ps.title, size = 16.sp, color = Color(0xFF2B3A67), weight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
                Txt(st.ps.artist, size = 13.sp, color = Color(0xFF2B3A67), fontFamily = FontFamily.Cursive)
            }
            Txt("C-90", Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 34.dp).background(Color(0xFFD94F3D)).padding(horizontal = 6.dp, vertical = 2.dp),
                size = 11.sp, weight = FontWeight.Black, color = Color.White, fontFamily = MONO)
            Txt("A", Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 14.dp), size = 11.sp, weight = FontWeight.Black, color = Color(0xFF2B2420), fontFamily = MONO)
            Txt("MELODYNE  TYPE II  CHROME", Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp), size = 9.sp, weight = FontWeight.Bold, color = Color(0xFF6B5A44), fontFamily = MONO)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VuMeter(levels.value.l, "L", Modifier.weight(1f))
            VuMeter(levels.value.r, "R", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                "%04d".format(st.pos.longValue / 1000),
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0E0B09)).border(1.dp, Color(0xFF3A2A12), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = amber, fontFamily = MONO, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Lamp("${kbps(st)} KBPS", true, amber); Lamp("DIRECT", st.audiophile, amber); Lamp("EQ", st.eqActive, amber)
        }
        RetroSeek(st.pos, st.dur, amber, onSeek = act.onSeekTo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            ScrubButton(G.Prev, amber, -1, 50.dp, act.onPrev) { d -> act.scrub(st, d) }
            PianoKey(G.Play, "PLAY", playing) { if (!playing) act.onToggle() }
            PianoKey(G.Pause, "PAUSE", !playing && st.ps.hasItem) { if (playing) act.onToggle() }
            PianoKey(G.Stop, "STOP", false) { act.stop(st) }
            ScrubButton(G.Next, amber, 1, 50.dp, act.onNext) { d -> act.scrub(st, d) }
        }
        Txt("Tap REW / FF to skip, hold to scrub", size = 11.sp, color = Amoled.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabelBtn("EQ", amber, st.eqActive, act.onEq); LabelBtn("QUEUE", amber, false, act.onQueue); LabelBtn("LYRICS", amber, false, act.onLyrics)
        }
    }
}

/** A piano-style deck key. It really stays pressed down while its function is active. */
@Composable
private fun PianoKey(kind: G, label: String, pressed: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.width(52.dp).offset(y = if (pressed) 4.dp else 0.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(if (pressed) listOf(Color(0xFF1E2024), Color(0xFF30343A)) else listOf(Color(0xFF5A606B), Color(0xFF2E3238))))
            .border(1.dp, Color(0xFF8A92A0), RoundedCornerShape(4.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }.padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(kind, Color(0xFFE8E2D0), 20.dp)
        Txt(label, size = 8.sp, weight = FontWeight.Bold, color = Color(0xFFC8CDD6), fontFamily = MONO)
    }
}

/** A backlit analogue VU meter with a dB scale; the needle is spring-damped, so it overshoots a little like the real thing. */
@Composable
private fun VuMeter(rawLevel: Float, label: String, modifier: Modifier) {
    val needle by animateFloatAsState(dbLevel(rawLevel), spring(dampingRatio = 0.45f, stiffness = 380f), label = "vu")
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF3B2A1A.toInt(); typeface = android.graphics.Typeface.MONOSPACE } }
    Box(
        modifier.height(92.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.radialGradient(listOf(Color(0xFFFFF1CC), Color(0xFFE8D6A4)))).border(2.dp, Color(0xFF2B2420), RoundedCornerShape(8.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.95f
            val len = size.height * 0.80f
            paint.textSize = 9.sp.toPx()
            val marks = listOf("-20", "-10", "-5", "0", "+3")
            for (k in 0..10) {
                val a = (-50.0 + k * 10.0) * PI / 180
                val inner = len * (if (k % 2 == 0) 0.84f else 0.90f)
                drawLine(if (k >= 7) Color(0xFFB3261E) else Color(0xFF3B2A1A), Offset(cx + inner * sin(a).toFloat(), cy - inner * cos(a).toFloat()), Offset(cx + len * sin(a).toFloat(), cy - len * cos(a).toFloat()), 2.dp.toPx())
                if (k % 2 == 0) {
                    val m = marks[k / 2]
                    val tr = len * 0.70f
                    drawContext.canvas.nativeCanvas.drawText(m, cx + tr * sin(a).toFloat() - paint.measureText(m) / 2, cy - tr * cos(a).toFloat(), paint)
                }
            }
            val a = (-50.0 + needle.coerceIn(0f, 1.05f) * 100.0) * PI / 180
            drawLine(Color(0xFF1A1512), Offset(cx, cy), Offset(cx + len * sin(a).toFloat(), cy - len * cos(a).toFloat()), 2.dp.toPx())
            drawCircle(Color(0xFF1A1512), 5.dp.toPx(), Offset(cx, cy))
        }
        Txt("VU", Modifier.align(Alignment.TopCenter).padding(top = 6.dp), size = 10.sp, weight = FontWeight.Black, color = Color(0xFF3B2A1A), fontFamily = MONO)
        Txt(label, Modifier.align(Alignment.BottomStart).padding(8.dp), size = 11.sp, weight = FontWeight.Bold, color = Color(0xFF3B2A1A), fontFamily = MONO)
    }
}

// ================================================================== Receiver
@Composable
private fun DeckBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val volume = rememberSystemVolume()
    val eq by ServiceLocator.eq.state.collectAsState()
    val amber = Color(0xFFFFB000)
    val ink = Color(0xFF2B2D31)
    val g = eq.gains
    val bassDb = (g[0] + g[1]) / 2f
    val trebleDb = (g[5] + g[6]) / 2f
    val playing = st.ps.isPlaying
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; color = 0xFFFFB000.toInt(); typeface = android.graphics.Typeface.MONOSPACE } }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).drawBehind { wood() }.padding(horizontal = 20.dp)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFCDD0D5), Color(0xFFA9ADB4), Color(0xFFC3C6CC))))
                    .drawBehind { var y = 0f; while (y < size.height) { drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y), 1f); y += 3f } }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // tuner dial: the needle sits at the song's position, like tuning across the band
                Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0B0704)).border(2.dp, Color(0xFF3A3B40), RoundedCornerShape(6.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.radialGradient(listOf(Color(0x55FFB000), Color.Transparent), center = Offset(size.width / 2, size.height), radius = size.width * 0.6f))
                        val pad = 18.dp.toPx()
                        val usable = size.width - 2 * pad
                        paint.textSize = 10.sp.toPx()
                        var f = 88.0
                        while (f <= 108.01) {
                            val x = pad + ((f - 88.0) / 20.0).toFloat() * usable
                            val major = (f.toInt() % 4 == 0) && f % 1.0 == 0.0
                            drawLine(Color(0xFFFFB000), Offset(x, size.height * 0.52f), Offset(x, size.height * (if (major) 0.74f else 0.64f)), 1.dp.toPx())
                            if (major) drawContext.canvas.nativeCanvas.drawText("${f.toInt()}", x - paint.measureText("${f.toInt()}") / 2, size.height * 0.46f, paint)
                            f += 0.5
                        }
                        val p = if (st.dur.longValue > 0) (st.pos.longValue.toFloat() / st.dur.longValue).coerceIn(0f, 1f) else 0f
                        val nx = pad + p * usable
                        drawLine(Color(0xFFFF3B30), Offset(nx, 6.dp.toPx()), Offset(nx, size.height - 6.dp.toPx()), 2.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText("FM", 8.dp.toPx(), size.height - 8.dp.toPx(), paint)
                        drawContext.canvas.nativeCanvas.drawText("MHz", size.width - 38.dp.toPx(), size.height - 8.dp.toPx(), paint)
                    }
                    Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Lamp("STEREO", true, amber); Lamp("TUNED", playing, amber)
                    }
                }
                // amber display
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0xFF140A00)).border(2.dp, Color(0xFF3A2A12), RoundedCornerShape(4.dp)).scanlines().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Marquee("${st.ps.title.uppercase()}  /  ${st.ps.artist.uppercase()}", amber, 15)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SevenSeg(kbps(st), amber, 14.dp, 26.dp)
                        Txt("KBPS", Modifier.padding(bottom = 2.dp), size = 10.sp, color = amber, fontFamily = MONO)
                        Spacer(Modifier.weight(1f))
                        SevenSeg(mmss(st.pos.longValue), amber, 10.dp, 20.dp)
                    }
                }
                // LED level meters and spectrum
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    LedMeter(dbLevel(levels.value.l), "L", ink)
                    LedMeter(dbLevel(levels.value.r), "R", ink)
                }
                Canvas(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0B0B0C))) {
                    val bands = levels.value.bands
                    val cw = size.width / 16f
                    val rows = 8
                    val rh = size.height / rows
                    for (i in 0 until 16) {
                        val lit = (bands[i].coerceIn(0f, 1f) * rows).toInt()
                        for (r in 0 until rows) {
                            val c = when { r < 5 -> Color(0xFF3DDC84); r < 7 -> amber; else -> Color(0xFFFF4B3E) }
                            drawRect(if (r < lit) c else c.copy(alpha = 0.10f), Offset(i * cw + 1.5f, size.height - (r + 1) * rh + 1.5f), Size(cw - 3f, rh - 3f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lamp("PURE DIRECT", st.audiophile, Color(0xFF8A4B00)); Lamp("LOUDNESS", eq.presetId == "low_volume", Color(0xFF8A4B00))
                    Lamp("EQ", st.eqActive, Color(0xFF8A4B00)); Lamp("INF", st.infinite, Color(0xFF8A4B00))
                }
                // knobs: bass and treble really move the EQ; the big one is the phone's volume
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Knob((bassDb + 10f) / 20f, { v -> val db = v * 20f - 10f; ServiceLocator.eq.setBand(0, db); ServiceLocator.eq.setBand(1, db) }, amber, 66.dp, centreDetent = true)
                        Txt("BASS", size = 10.sp, weight = FontWeight.Bold, color = ink, fontFamily = MONO)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Knob(volume.level.value, volume.set, amber, 108.dp)
                        Txt("VOLUME", size = 10.sp, weight = FontWeight.Bold, color = ink, fontFamily = MONO)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Knob((trebleDb + 10f) / 20f, { v -> val db = v * 20f - 10f; ServiceLocator.eq.setBand(5, db); ServiceLocator.eq.setBand(6, db) }, amber, 66.dp, centreDetent = true)
                        Txt("TREBLE", size = 10.sp, weight = FontWeight.Bold, color = ink, fontFamily = MONO)
                    }
                }
                Txt("TUNING", size = 10.sp, weight = FontWeight.Bold, color = ink, fontFamily = MONO)
                RetroSeek(st.pos, st.dur, amber, onSeek = act.onSeekTo)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RetroBtn(G.Prev, amber, onClick = act.onPrev)
                    RetroBtn(if (playing) G.Pause else G.Play, amber, onClick = act.onToggle)
                    RetroBtn(G.Next, amber, onClick = act.onNext)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelBtn("PURE DIRECT", amber, st.audiophile) { ServiceLocator.playback.setAudiophile(!st.audiophile) }
                    LabelBtn("LOUDNESS", amber, eq.presetId == "low_volume") { ServiceLocator.eq.setPreset(if (eq.presetId == "low_volume") "flat" else "low_volume") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelBtn("EQ", amber, st.eqActive, act.onEq); LabelBtn("QUEUE", amber, false, act.onQueue); LabelBtn("LYRICS", amber, false, act.onLyrics)
                }
            }
        }
    }
}

@Composable
private fun LedMeter(level: Float, label: String, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, size = 10.sp, weight = FontWeight.Bold, color = ink, fontFamily = MONO)
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val n = 24
            val cw = size.width / n
            val lit = (level.coerceIn(0f, 1f) * n).toInt()
            for (i in 0 until n) {
                val c = when { i < 15 -> Color(0xFF3DDC84); i < 20 -> Color(0xFFFFB000); else -> Color(0xFFFF4B3E) }
                drawRect(if (i < lit) c else Color(0xFF1B1B1D), Offset(i * cw + 1f, 0f), Size(cw - 2f, size.height))
            }
        }
    }
}

/** Walnut veneer for the receiver's side panels: a warm gradient with deterministic grain. */
private fun DrawScope.wood() {
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF5A3A1F), Color(0xFF7A5230), Color(0xFF5A3A1F))))
    val rnd = kotlin.random.Random(7)
    repeat(90) {
        val x = rnd.nextFloat() * size.width
        val a = 0.06f + rnd.nextFloat() * 0.16f
        drawLine(Color(0xFF241104).copy(alpha = a), Offset(x, 0f), Offset(x + (rnd.nextFloat() - 0.5f) * 8f, size.height), 1f + rnd.nextFloat() * 1.6f)
    }
}

/** The classic volume/balance bar: the fill shifts from green through yellow to red, with a beveled thumb. */
@Composable
private fun AmpSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    Canvas(
        modifier.height(20.dp)
            .pointerInput(Unit) { detectTapGestures { o -> cb((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> cb((c.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val t = 7.dp.toPx()
        val y = size.height / 2 - t / 2
        val v = value.coerceIn(0f, 1f)
        drawRect(Color(0xFF050505), Offset(0f, y), Size(size.width, t))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF00E050), Color(0xFFFFE600), Color(0xFFFF3B2F)), startX = 0f, endX = size.width), Offset(0f, y), Size(size.width * v, t))
        val tw = 12.dp.toPx()
        val x = (size.width * v).coerceIn(tw / 2, size.width - tw / 2)
        drawRoundRect(Color(0xFF8E96A3), Offset(x - tw / 2, 1.dp.toPx()), Size(tw, size.height - 2.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawRoundRect(Color(0xFFD5DAE2), Offset(x - tw / 2 + 1f, 2.dp.toPx()), Size(tw - 2f, (size.height - 4.dp.toPx()) / 2), CornerRadius(1.5.dp.toPx()))
    }
}

/** One vertical EQ slider with the retro look: a dB readout above, the band's name below. */
@Composable
private fun AmpBand(label: String, db: Float, color: Color, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    fun toDb(y: Float, h: Int): Float {
        val raw = (((h / 2f) - y) / (h / 2f)) * org.melodyne.app.audio.EQ_MAX_DB
        return (raw.coerceIn(-org.melodyne.app.audio.EQ_MAX_DB, org.melodyne.app.audio.EQ_MAX_DB) * 2f).roundToInt() / 2f
    }
    Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Txt(if (kotlin.math.abs(db) < 0.05f) "0" else "%+.0f".format(db), size = 9.sp, color = color, fontFamily = MONO)
        Canvas(
            Modifier.width(30.dp).height(96.dp)
                .pointerInput(Unit) { detectTapGestures { o -> cb(toDb(o.y, size.height)) } }
                .pointerInput(Unit) { detectVerticalDragGestures { c, _ -> cb(toDb(c.position.y, size.height)) } },
        ) {
            val cx = size.width / 2
            val mid = size.height / 2
            drawRect(Color(0xFF050505), Offset(cx - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height))
            drawLine(Color(0xFF3A3F47), Offset(cx - 11.dp.toPx(), mid), Offset(cx + 11.dp.toPx(), mid), 1f)
            val y = mid - (db / org.melodyne.app.audio.EQ_MAX_DB) * mid
            drawRect(color.copy(alpha = 0.55f), Offset(cx - 3.dp.toPx(), minOf(y, mid)), Size(6.dp.toPx(), kotlin.math.abs(y - mid)))
            drawRoundRect(Color(0xFF8E96A3), Offset(cx - 11.dp.toPx(), y - 4.dp.toPx()), Size(22.dp.toPx(), 8.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(Color(0xFFD5DAE2), Offset(cx - 10.dp.toPx(), y - 3.dp.toPx()), Size(20.dp.toPx(), 3.dp.toPx()), CornerRadius(1.dp.toPx()))
        }
        Txt(label, size = 8.sp, color = Color(0xFFC8CDD6), fontFamily = MONO)
    }
}
