package org.melodyne.app.ui.skins

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.melodyne.app.player.LevelBus
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.Txt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One frame of the real audio levels: 16 spectrum bands (0..1), their slowly falling peak caps, and left/right peaks. */
class LevelFrame(val bands: FloatArray = FloatArray(16), val caps: FloatArray = FloatArray(16), val l: Float = 0f, val r: Float = 0f)

@Composable
fun rememberLevels(playing: Boolean): State<LevelFrame> {
    val state = remember { mutableStateOf(LevelFrame()) }
    DisposableEffect(Unit) { LevelBus.watching = true; onDispose { LevelBus.watching = false } }
    LaunchedEffect(playing) {
        val shown = FloatArray(16)
        val caps = FloatArray(16)
        var pl = 0f
        var pr = 0f
        while (true) {
            for (i in 0 until 16) {
                shown[i] = maxOf(if (playing) LevelBus.bands[i] else 0f, shown[i] * 0.86f)
                caps[i] = maxOf(shown[i], caps[i] - 0.012f)                    // caps hang above the bars and sink slowly, like the originals
            }
            pl = maxOf(if (playing) LevelBus.peakL else 0f, pl * 0.9f)
            pr = maxOf(if (playing) LevelBus.peakR else 0f, pr * 0.9f)
            state.value = LevelFrame(shown.copyOf(), caps.copyOf(), pl, pr)
            delay(33)
        }
    }
    return state
}

class VolumeControl(val level: State<Float>, val set: (Float) -> Unit)

/** The phone's real media volume: reading follows the hardware keys, writing sets it. */
@Composable
fun rememberSystemVolume(): VolumeControl {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val level = remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    LaunchedEffect(Unit) { while (true) { level.floatValue = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max; delay(500) } }
    return remember {
        VolumeControl(level) { f ->
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt().coerceIn(0, max), 0)
            level.floatValue = f
        }
    }
}

/** mm:ss as two-digit minutes, the way an LCD shows it. */
fun mmss(ms: Long): String {
    val s = (kotlin.math.abs(ms) / 1000).toInt()
    return "%02d:%02d".format(s / 60, s % 60)
}

// ------------------------------------------------------------------ 7-segment LCD
object SevenSegments {
    private val table = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g",
    )
    fun segmentsFor(c: Char): String = table[c] ?: ""
}

/** Real seven-segment digits with the faint "ghost" of every unlit segment, like an LCD or LED display. */
@Composable
fun SevenSeg(text: String, color: Color, digitW: Dp = 20.dp, digitH: Dp = 34.dp, modifier: Modifier = Modifier) {
    val gap = digitW * 0.4f
    Canvas(modifier.size(width = (digitW + gap) * text.length, height = digitH)) {
        val dw = digitW.toPx()
        val dh = digitH.toPx()
        val g = gap.toPx()
        val t = dw * 0.2f
        val ghost = color.copy(alpha = 0.10f)
        text.forEachIndexed { i, ch ->
            val x = i * (dw + g)
            if (ch == ':') {
                drawCircle(color, t * 0.8f, Offset(x + dw / 2, dh * 0.32f))
                drawCircle(color, t * 0.8f, Offset(x + dw / 2, dh * 0.68f))
            } else if (ch == ' ') {
                // blank
            } else {
                val on = SevenSegments.segmentsFor(ch)
                fun seg(name: Char, ox: Float, oy: Float, w: Float, h: Float) {
                    drawRoundRect(if (name in on) color else ghost, Offset(x + ox, oy), Size(w, h), CornerRadius(t * 0.4f))
                }
                val half = dh / 2
                seg('a', t, 0f, dw - 2 * t, t)
                seg('g', t, half - t / 2, dw - 2 * t, t)
                seg('d', t, dh - t, dw - 2 * t, t)
                seg('f', 0f, t, t, half - 1.5f * t)
                seg('b', dw - t, t, t, half - 1.5f * t)
                seg('e', 0f, half + t / 2, t, half - 1.5f * t)
                seg('c', dw - t, half + t / 2, t, half - 1.5f * t)
            }
        }
    }
}

/** Fine horizontal scanlines over an LCD or CRT. */
fun Modifier.scanlines(alpha: Float = 0.2f): Modifier = this.drawWithContent {
    drawContent()
    var y = 0f
    while (y < size.height) { drawLine(Color.Black.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 1f); y += 3f }
}

// ------------------------------------------------------------------ buttons, lamps, sliders
private val btnBrush = Brush.verticalGradient(listOf(Color(0xFF5A606B), Color(0xFF30343B)))
private val btnEdge = Color(0xFF8A92A0)

@Composable
fun RetroBtn(kind: G, tint: Color, modifier: Modifier = Modifier, width: Dp = 56.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.size(width = width, height = 44.dp).clip(RoundedCornerShape(5.dp)).background(btnBrush)
            .border(1.dp, btnEdge, RoundedCornerShape(5.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) { Glyph(kind, tint, 22.dp) }
}

/** Tap = skip; hold = scrub in the given direction (-1 rewind, +1 fast-forward), like the tape deck it imitates. */
@Composable
fun ScrubButton(kind: G, tint: Color, direction: Int, width: Dp = 56.dp, onTap: () -> Unit, onScrub: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(width = width, height = 44.dp).clip(RoundedCornerShape(5.dp)).background(btnBrush)
            .border(1.dp, btnEdge, RoundedCornerShape(5.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var scrubbed = false
                    val job = scope.launch { delay(350); scrubbed = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); while (true) { onScrub(direction); delay(200) } }
                    tryAwaitRelease()
                    job.cancel()
                    if (!scrubbed) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTap() }
                })
            },
        contentAlignment = Alignment.Center,
    ) { Glyph(kind, tint, 22.dp) }
}

@Composable
fun LabelBtn(text: String, color: Color, on: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Txt(
        text,
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) color.copy(alpha = 0.25f) else Color(0xFF2A2E34))
            .border(1.dp, if (on) color else btnEdge, RoundedCornerShape(4.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
        size = 12.sp, weight = FontWeight.Bold, color = if (on) color else Color(0xFFC8CDD6), fontFamily = FontFamily.Monospace,
    )
}

/** A status lamp that really is on or off. */
@Composable
fun Lamp(text: String, on: Boolean, color: Color) {
    Txt(
        text, Modifier.border(1.dp, if (on) color else color.copy(alpha = 0.25f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        size = 10.sp, weight = FontWeight.Bold, color = if (on) color else color.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace,
    )
}

/** A seek groove with a chunky beveled thumb. Dragging previews; the seek is made when you let go. */
@Composable
fun RetroSeek(pos: LongState, durState: LongState, color: Color, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = durState.longValue
    val frac = drag ?: if (dur > 0) (pos.longValue.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        modifier.fillMaxWidth().height(30.dp)
            .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { f -> if (dur > 0) onSeek((f * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = 8.dp.toPx()
            val y = size.height / 2 - t / 2
            drawRoundRect(Color(0xFF050505), Offset(0f, y), Size(size.width, t), CornerRadius(2.dp.toPx()))
            drawRoundRect(Color(0xFF3A3F47), Offset(0f, y), Size(size.width, t), CornerRadius(2.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            drawRoundRect(color.copy(alpha = 0.85f), Offset(0f, y), Size(size.width * frac, t), CornerRadius(2.dp.toPx()))
            val hx = (size.width * frac).coerceIn(9.dp.toPx(), size.width - 9.dp.toPx())
            val tw = 18.dp.toPx()
            drawRoundRect(Color(0xFF8E96A3), Offset(hx - tw / 2, 3.dp.toPx()), Size(tw, size.height - 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(Color(0xFFD5DAE2), Offset(hx - tw / 2 + 1f, 4.dp.toPx()), Size(tw - 2f, (size.height - 6.dp.toPx()) / 2), CornerRadius(2.dp.toPx()))
            drawRect(Color(0xFF2A2E34), Offset(hx - 1.dp.toPx(), 7.dp.toPx()), Size(2.dp.toPx(), size.height - 14.dp.toPx()))
        }
    }
}

/** A rotary knob you turn by dragging around it. 270 degrees of travel with a dead zone at the bottom. */
@Composable
fun Knob(value: Float, onChange: (Float) -> Unit, color: Color, diameter: Dp = 120.dp, centreDetent: Boolean = false) {
    Canvas(
        Modifier.size(diameter).pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val deg = Math.toDegrees(atan2((change.position.y - c.y).toDouble(), (change.position.x - c.x).toDouble()))
                var v = (((deg + 225.0 + 360.0) % 360.0) / 270.0).toFloat()
                if (v <= 1f) {
                    if (centreDetent && kotlin.math.abs(v - 0.5f) < 0.03f) v = 0.5f              // a click at the middle position
                    onChange(v)
                }
                change.consume()
            }
        },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        for (k in 0..10) {
            val a = (135.0 + k * 27.0) * PI / 180
            drawLine(if (k / 10f <= value) color else Color(0xFF5A5B60), Offset(c.x + r * 0.9f * cos(a).toFloat(), c.y + r * 0.9f * sin(a).toFloat()), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 2.dp.toPx())
        }
        drawCircle(Color(0xFF111113), r * 0.8f, c)
        for (k in 0 until 36) {                                                          // knurled edge
            val a = k * 10.0 * PI / 180
            drawLine(Color(0xFF3A3B40), Offset(c.x + r * 0.70f * cos(a).toFloat(), c.y + r * 0.70f * sin(a).toFloat()), Offset(c.x + r * 0.78f * cos(a).toFloat(), c.y + r * 0.78f * sin(a).toFloat()), 1.5.dp.toPx())
        }
        drawCircle(Color(0xFF8A8C92), r * 0.66f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        val a = (135.0 + value.coerceIn(0f, 1f) * 270.0) * PI / 180
        drawLine(color, Offset(c.x + r * 0.28f * cos(a).toFloat(), c.y + r * 0.28f * sin(a).toFloat()), Offset(c.x + r * 0.62f * cos(a).toFloat(), c.y + r * 0.62f * sin(a).toFloat()), 3.5.dp.toPx())
    }
}

fun Modifier.panel(top: Color, bottom: Color, edge: Color): Modifier =
    this.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(top, bottom)))
        .border(2.dp, edge, RoundedCornerShape(12.dp)).padding(12.dp)

fun Modifier.fullWidth(): Modifier = this.fillMaxWidth().padding(vertical = 2.dp)
