package org.melodyne.app.ui.skins

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.melodyne.app.player.LevelBus
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.Txt
import kotlin.math.roundToInt

/** One frame of the real audio levels: 16 spectrum bands (0..1) and the left/right peaks, with a decaying fall like real meters. */
class LevelFrame(val bands: FloatArray = FloatArray(16), val l: Float = 0f, val r: Float = 0f)

@Composable
fun rememberLevels(playing: Boolean): State<LevelFrame> {
    val state = remember { mutableStateOf(LevelFrame()) }
    DisposableEffect(Unit) { LevelBus.watching = true; onDispose { LevelBus.watching = false } }
    LaunchedEffect(playing) {
        val shown = FloatArray(16)
        var pl = 0f
        var pr = 0f
        while (true) {
            for (i in 0 until 16) shown[i] = maxOf(if (playing) LevelBus.bands[i] else 0f, shown[i] * 0.86f)
            pl = maxOf(if (playing) LevelBus.peakL else 0f, pl * 0.9f)
            pr = maxOf(if (playing) LevelBus.peakR else 0f, pr * 0.9f)
            state.value = LevelFrame(shown.copyOf(), pl, pr)
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

private val btnBrush = Brush.verticalGradient(listOf(Color(0xFF5A606B), Color(0xFF30343B)))
private val btnEdge = Color(0xFF8A92A0)

@Composable
fun RetroBtn(kind: G, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(5.dp)).background(btnBrush)
            .border(1.dp, btnEdge, RoundedCornerShape(5.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(kind, tint, 22.dp) }
}

/** Tap = skip; hold = scrub in the given direction (-1 rewind, +1 fast-forward), like the tape deck it imitates. */
@Composable
fun ScrubButton(kind: G, tint: Color, direction: Int, onTap: () -> Unit, onScrub: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(5.dp)).background(btnBrush)
            .border(1.dp, btnEdge, RoundedCornerShape(5.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var scrubbed = false
                    val job = scope.launch { delay(350); scrubbed = true; while (true) { onScrub(direction); delay(200) } }
                    tryAwaitRelease()
                    job.cancel()
                    if (!scrubbed) onTap()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Glyph(kind, tint, 22.dp) }
}

@Composable
fun LabelBtn(text: String, color: Color, on: Boolean = false, onClick: () -> Unit) {
    Txt(
        text,
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) color.copy(alpha = 0.25f) else Color(0xFF2A2E34))
            .border(1.dp, if (on) color else btnEdge, RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
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

/** A seek groove. Dragging previews; the seek is made when you let go. */
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
            drawRoundRect(color.copy(alpha = 0.85f), Offset(0f, y), Size(size.width * frac, t), CornerRadius(2.dp.toPx()))
            val hx = (size.width * frac).coerceIn(8.dp.toPx(), size.width - 8.dp.toPx())
            drawRoundRect(Color(0xFFB9BFC9), Offset(hx - 8.dp.toPx(), 3.dp.toPx()), Size(16.dp.toPx(), size.height - 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRect(Color(0xFF2A2E34), Offset(hx - 1.dp.toPx(), 6.dp.toPx()), Size(2.dp.toPx(), size.height - 12.dp.toPx()))
        }
    }
}

fun Modifier.panel(top: Color, bottom: Color, edge: Color): Modifier =
    this.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(top, bottom)))
        .border(2.dp, edge, RoundedCornerShape(12.dp)).padding(12.dp)

fun Modifier.fullWidth(): Modifier = this.fillMaxWidth().padding(vertical = 2.dp)
