package org.melodyne.app.ui.skins

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.Txt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val CASSETTE_ASPECT = 1.587f           // a real cassette is 102 x 64 mm
private val MONO_T = FontFamily.Monospace
private val INK = Color(0xFF1B1C1F)
private val GLYPH = Color(0xFF7D8189)

/** How much the machine has been used: 0 mint, 1 used, 2 well worn. Chosen in Settings > Appearance. */
const val TAPE_WEAR_PREF = "tape_wear"

/**
 * A black-and-silver pocket cassette recorder, full screen, with the marks of a machine that has been used. A grille
 * whose holes breathe with the bass, a smoked-glass door with a cassette behind it (reels turn at the right relative
 * speeds, tape moves from spool to spool with the song), a brushed-silver strip, silk-screened labels, and piano keys.
 * Hold REVIEW or CUE to wind, drag the door to seek, long-press EJECT to leave.
 */
@Composable
internal fun TapeBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val volume = rememberSystemVolume()
    val playing = st.ps.isPlaying
    val loaded = st.ps.hasItem
    val density = LocalDensity.current
    val wear = remember { ServiceLocator.prefs.getInt(TAPE_WEAR_PREF, 1).coerceIn(0, 2) }

    val progress = if (st.dur.longValue > 0) (st.pos.longValue.toFloat() / st.dur.longValue).coerceIn(0f, 1f) else 0f
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val shownProgress by rememberUpdatedState(dragFrac ?: progress)
    val spinning by rememberUpdatedState(playing && loaded)
    val currentProgress by rememberUpdatedState(progress)
    val durMs by rememberUpdatedState(st.dur.longValue)
    var scrubDir by remember { mutableIntStateOf(0) }
    var scrubAt by remember { mutableLongStateOf(0L) }
    val rotL = remember { mutableFloatStateOf(0f) }
    val rotR = remember { mutableFloatStateOf(0f) }

    // Tape moves at a constant speed, so the small, fast reel turns quicker than the big, slow one (angular speed = v / radius).
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val dt = (t - last) / 1e9f
                val scrubbing = scrubDir != 0 && System.currentTimeMillis() - scrubAt < 350
                val speed = when { scrubbing -> 7f * scrubDir; spinning -> 1f; else -> 0f }
                if (speed != 0f) {
                    val p = shownProgress
                    val supply = 1f - 0.55f * p
                    val takeUp = 0.45f + 0.55f * p
                    rotL.floatValue = (rotL.floatValue - 80f * speed / supply * dt) % 360f
                    rotR.floatValue = (rotR.floatValue - 80f * speed / takeUp * dt) % 360f
                }
            }
            last = t
        }
    }

    // A cassette slides into the door each time a song starts.
    val insert = remember { Animatable(0f) }
    LaunchedEffect(st.ps.songId, loaded) {
        insert.snapTo(0f)
        if (loaded) insert.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }

    Box(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1B1E), Color(0xFF0B0B0D))))
            .drawWithCache {
                val scuffs = bodyScuffs(size.width, size.height, wear, density)
                onDrawWithContent {
                    drawContent()
                    // the plastic edge catching the light, and the scuffs a pocket recorder collects at its corners
                    drawRoundRect(Color(0x26FFFFFF), Offset(1.5.dp.toPx(), 1.5.dp.toPx()), Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()), CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
                    scuffs.draw(this)
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        ) {
            // ---------------------------------------------------------------- speaker grille
            Box(
                Modifier.fillMaxWidth().weight(0.46f).drawWithCache {
                    val grille = Grille(size, density, wear)
                    onDrawWithContent {
                        val f = levels.value
                        grille.draw(this, ((f.bands[0] + f.bands[1] + f.bands[2]) / 3f).coerceIn(0f, 1f))
                    }
                },
            )
            Spacer(Modifier.height(6.dp))

            // ---------------------------------------------------------------- cassette door
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(0.29f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF09090B))
                    .border(1.dp, Color(0xFF2C2D31), RoundedCornerShape(5.dp))
                    .pointerInput(Unit) {
                        // turning the tape by hand: drag along the door to seek
                        detectHorizontalDragGestures(
                            onDragStart = { dragFrac = currentProgress },
                            onDragEnd = { dragFrac?.let { f -> if (durMs > 0) act.onSeekTo((f * durMs).toLong()) }; dragFrac = null },
                            onDragCancel = { dragFrac = null },
                        ) { change, dx ->
                            dragFrac = ((dragFrac ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                            scrubDir = if (dx >= 0) 1 else -1
                            scrubAt = System.currentTimeMillis()
                            change.consume()
                        }
                    },
            ) {
                // The cassette leaves a margin on both sides so the machine's own parts show, as in the photo.
                val topPad = 28.dp
                val cassW = minOf(maxWidth * 0.78f, (maxHeight - topPad - 10.dp) * CASSETTE_ASPECT)
                val cassH = cassW / CASSETTE_ASPECT
                val cassX = (maxWidth - cassW) / 2
                val cassY = topPad + (maxHeight - topPad - cassH) / 2
                val cassHPx = with(density) { cassH.toPx() }

                Canvas(Modifier.fillMaxSize()) {
                    val x = cassX.toPx(); val y = cassY.toPx(); val cw = cassW.toPx(); val ch = cassH.toPx()
                    drawMechanism(x, y, cw, ch, wear)
                    if (loaded) {
                        translate(top = (1f - insert.value) * ch * 0.7f) {
                            drawCassette(x, y, cw, ch, rotL.floatValue, rotR.floatValue, shownProgress, spinning, wear)
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        "Pocket Line", Modifier.border(1.dp, Color.White.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        size = 12.sp, color = Color.White, fontFamily = MONO_T,
                    )
                    Txt("AC/BATTERY", Modifier.padding(start = 10.dp), size = 10.sp, color = Color.White.copy(alpha = 0.85f), fontFamily = MONO_T)
                }
                val titleSp = with(density) { (cassH * 0.082f).toSp() }
                val artistSp = with(density) { (cassH * 0.062f).toSp() }
                if (loaded) {
                    // The writing sits in its own band on the label, above the reel window, so nothing is drawn over it.
                    Column(
                        Modifier.offset(cassX + cassW * 0.085f, cassY + cassH * 0.145f).width(cassW * 0.83f).height(cassH * 0.20f)
                            .graphicsLayer { translationY = (1f - insert.value) * cassHPx * 0.7f },
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Txt(st.ps.title, size = titleSp, color = Color(0xFF1F356B), weight = FontWeight.Bold, fontFamily = FontFamily.Cursive, maxLines = 1)
                        Txt(st.ps.artist, size = artistSp, color = Color(0xFF2B4380), fontFamily = FontFamily.Cursive, maxLines = 1)
                    }
                }
                Txt(
                    "%04d".format((dragFrac?.let { (it * st.dur.longValue).toLong() } ?: st.pos.longValue) / 1000),
                    Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp).background(Color(0xFF050505)).padding(horizontal = 6.dp, vertical = 2.dp),
                    size = 12.sp, color = Color(0xFFE8E4D8), fontFamily = MONO_T,
                )
                // smoked glass, in front of everything: tint, sheen, the printed arrow, the white hairlines, and the marks of use
                Box(
                    Modifier.fillMaxSize().drawWithCache {
                        val marks = glassMarks(size.width, size.height, wear, density)
                        onDrawWithContent {
                            drawRect(Color(0x1F000000))
                            drawRect(Brush.linearGradient(listOf(Color(0x26FFFFFF), Color.Transparent, Color(0x0FFFFFFF)), Offset.Zero, Offset(size.width, size.height)))
                            drawInsertArrow(Offset(size.width * 0.5f, size.height * 0.84f), size.width * 0.15f)
                            drawLine(Color(0xD9FFFFFF), Offset(0f, 2.dp.toPx()), Offset(size.width, 2.dp.toPx()), 1.5.dp.toPx())
                            drawLine(Color(0xD9FFFFFF), Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 1.5.dp.toPx())
                            marks.draw(this)
                        }
                    },
                )
            }

            // ---------------------------------------------------------------- brushed-silver strip
            Row(
                Modifier.fillMaxWidth().height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFD6D8DB), Color(0xFFA7AAAF), Color(0xFFCBCDD1))))
                    .drawWithCache {
                        val marks = stripMarks(size.width, size.height, wear, density)
                        onDrawWithContent {
                            var y = 0f
                            while (y < size.height) { drawLine(Color(0x10000000), Offset(0f, y), Offset(size.width, y), 1f); y += 3f }
                            drawContent()
                            marks.draw(this)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(12.dp))
                Canvas(Modifier.size(22.dp)) {
                    val bw = size.width / 7f
                    listOf(0.35f, 0.8f, 0.55f, 1f).forEachIndexed { i, hgt ->
                        drawRoundRect(INK, Offset(i * bw * 1.8f, size.height * (1f - hgt)), Size(bw, size.height * hgt), CornerRadius(bw / 2))
                    }
                }
                Txt("Vina", Modifier.padding(start = 8.dp), size = 17.sp, weight = FontWeight.Bold, color = INK)
                Spacer(Modifier.weight(1f))
                StripBtn("EQ", act.onEq); StripBtn("LYRICS", act.onLyrics); StripBtn("QUEUE", act.onQueue); StripBtn("MENU", act.onMenu)
                Spacer(Modifier.width(4.dp))
            }

            // ---------------------------------------------------------------- silk-screened labels (the wider first key is PLAY)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Spacer(Modifier.width(46.dp))
                KeyLabel("▶ PLAY", Modifier.weight(1.5f))
                KeyLabel("◀◀ REVIEW", Modifier.weight(1f))
                KeyLabel("CUE ▶▶", Modifier.weight(1f))
                KeyLabel("■ STOP", Modifier.weight(1f))
                KeyLabel("EJECT/PAUSE", Modifier.weight(1.1f))
            }

            // ---------------------------------------------------------------- piano keys
            Row(
                Modifier.fillMaxWidth().weight(0.12f).heightIn(min = 60.dp).padding(horizontal = 10.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VolumeSlide(volume)
                TapeKey(Modifier.weight(1.5f), pressed = playing, wear = wear, onTap = { if (!playing) act.onToggle() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(20.dp).drawBehind { if (playing) drawCircle(Color(0x66FF6A1A), size.minDimension * 1.15f) }
                                .clip(RoundedCornerShape(2.dp)).background(if (playing) Color(0xFFFF6A1A) else if (wear > 0) Color(0xFFB3501B) else Color(0xFFFF6A1A)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Glyph(G.Play, GLYPH, 18.dp)
                    }
                }
                TapeKey(
                    Modifier.weight(1f), pressed = false, wear = wear, repeatDir = -1,
                    onRepeat = { d -> act.scrub(st, d); scrubDir = d; scrubAt = System.currentTimeMillis() }, onTap = act.onPrev,
                ) { DoubleArrow(left = true, color = GLYPH) }
                TapeKey(
                    Modifier.weight(1f), pressed = false, wear = wear, repeatDir = 1,
                    onRepeat = { d -> act.scrub(st, d); scrubDir = d; scrubAt = System.currentTimeMillis() }, onTap = act.onNext,
                ) { DoubleArrow(left = false, color = GLYPH) }
                TapeKey(Modifier.weight(1f), pressed = false, wear = wear, onTap = { act.stop(st) }) { Glyph(G.Stop, GLYPH, 18.dp) }
                // tap = pause or resume, like the real key; long-press ejects the tape, which leaves the screen
                TapeKey(
                    Modifier.weight(1.1f), pressed = !playing && loaded, wear = wear, onLongPress = act.onClose,
                    onTap = { if (loaded) act.onToggle() },
                ) { Glyph(G.Pause, GLYPH, 18.dp) }
            }
            // the base lip of the case
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(5.dp).clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(Color(0xFF121214)))
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun StripBtn(text: String, onClick: () -> Unit) {
    Txt(
        text, Modifier.clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 17.dp),
        size = 9.sp, weight = FontWeight.Bold, color = INK, fontFamily = MONO_T,
    )
}

@Composable
private fun KeyLabel(text: String, modifier: Modifier) {
    Txt(text, modifier, size = 8.sp, weight = FontWeight.Bold, color = Color(0xFFE6E2D6), fontFamily = MONO_T, maxLines = 1)
}

@Composable
private fun DoubleArrow(left: Boolean, color: Color) {
    Canvas(Modifier.size(width = 26.dp, height = 18.dp)) {
        val w = size.width
        val h = size.height
        fun tri(x0: Float) {
            val p = Path().apply {
                if (left) { moveTo(x0 + w * 0.5f, 0f); lineTo(x0, h / 2); lineTo(x0 + w * 0.5f, h) }
                else { moveTo(x0, 0f); lineTo(x0 + w * 0.5f, h / 2); lineTo(x0, h) }
                close()
            }
            drawPath(p, color)
        }
        tri(0f); tri(w * 0.5f)
    }
}

/** A deck key. It stays pressed down while its function is active; some keys also repeat while held, or react to a long press. */
@Composable
private fun TapeKey(
    modifier: Modifier, pressed: Boolean, wear: Int = 1, repeatDir: Int = 0, onRepeat: (Int) -> Unit = {}, onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit, content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val repeatCb by rememberUpdatedState(onRepeat)
    val longCb by rememberUpdatedState(onLongPress)
    Box(
        modifier.fillMaxHeight().offset(y = if (pressed) 4.dp else 0.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(if (pressed) listOf(Color(0xFF0E0E10), Color(0xFF1A1A1D)) else listOf(Color(0xFF2B2C30), Color(0xFF151517))))
            .border(1.dp, Color(0xFF3A3B40), RoundedCornerShape(4.dp))
            .drawWithCache {
                val marks = keyMarks(size.width, size.height, wear, density)
                onDrawWithContent {
                    drawContent()
                    // plastic polished by thumbs along the top edge, and dust in the gaps
                    if (wear > 0) drawRect(Brush.verticalGradient(listOf(Color(if (wear > 1) 0x30FFFFFF else 0x1CFFFFFF), Color.Transparent), 0f, size.height * 0.32f), Offset.Zero, Size(size.width, size.height * 0.32f))
                    marks.draw(this)
                }
            }
            .pointerInput(repeatDir) {
                detectTapGestures(
                    onPress = {
                        var held = false
                        val job = if (repeatDir != 0) scope.launch {
                            delay(350)
                            held = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            while (true) { repeatCb(repeatDir); delay(200) }
                        } else null
                        val released = tryAwaitRelease()
                        job?.cancel()
                        if (repeatDir != 0 && released && !held) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); tap() }
                    },
                    onTap = { if (repeatDir == 0) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); tap() } },
                    onLongPress = { longCb?.let { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                )
            },
        contentAlignment = Alignment.Center, content = content,
    )
}

/** The little ribbed volume slide at the left of the keys: drag up for louder. This is the phone's real media volume. */
@Composable
private fun VolumeSlide(volume: VolumeControl) {
    Box(
        Modifier.width(46.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF0A0A0B))
            .border(1.dp, Color(0xFF2C2D31), RoundedCornerShape(4.dp))
            .pointerInput(Unit) { detectTapGestures { o -> volume.set(1f - (o.y / size.height).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectVerticalDragGestures { c, _ -> volume.set(1f - (c.position.y / size.height).coerceIn(0f, 1f)); c.consume() } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val slotW = 5.dp.toPx()
            drawRoundRect(Color(0xFF000000), Offset((size.width - slotW) / 2, 8.dp.toPx()), Size(slotW, size.height - 16.dp.toPx()), CornerRadius(slotW / 2))
            val th = 16.dp.toPx()
            val y = 8.dp.toPx() + (1f - volume.level.value) * (size.height - 16.dp.toPx() - th)
            drawRoundRect(Color(0xFF2B2C30), Offset(6.dp.toPx(), y), Size(size.width - 12.dp.toPx(), th), CornerRadius(2.dp.toPx()))
            var ry = y + 3.dp.toPx()
            while (ry < y + th - 2.dp.toPx()) { drawLine(Color(0xFF0A0A0B), Offset(8.dp.toPx(), ry), Offset(size.width - 8.dp.toPx(), ry), 1.5.dp.toPx()); ry += 3.5.dp.toPx() }
        }
    }
}

// ------------------------------------------------------------------ the marks of use
// Everything below is generated from a fixed seed, so the same machine shows the same scratches every time.

private class MLine(val a: Offset, val b: Offset, val color: Color, val w: Float)
private class MDot(val c: Offset, val r: Float, val color: Color)
private class MOval(val topLeft: Offset, val size: Size, val color: Color)

private class Marks(val ovals: List<MOval>, val lines: List<MLine>, val dots: List<MDot>) {
    fun draw(s: DrawScope) {
        for (o in ovals) s.drawOval(o.color, o.topLeft, o.size)
        for (l in lines) s.drawLine(l.color, l.a, l.b, l.w, StrokeCap.Round)
        for (d in dots) s.drawCircle(d.color, d.r, d.c)
    }
}

/** Scratches, dust specks and smudges scattered over a [w] x [h] surface. [wear] 1 = used, 2 = well worn. */
private fun scatter(
    w: Float, h: Float, wear: Int, seed: Int, density: Float, scratches: Int, dust: Int, smudges: Int,
    light: Color = Color.White, dark: Color = Color.Black, scratchLen: Float = 0.16f,
): Marks {
    if (wear <= 0) return Marks(emptyList(), emptyList(), emptyList())
    val rnd = Random(seed)
    val k = if (wear >= 2) 2.2f else 1f
    val ovals = ArrayList<MOval>(); val lines = ArrayList<MLine>(); val dots = ArrayList<MDot>()
    repeat((smudges * k).toInt()) {
        val sw = (0.08f + rnd.nextFloat() * 0.18f) * w
        val sh = sw * (0.5f + rnd.nextFloat() * 0.6f)
        ovals += MOval(Offset(rnd.nextFloat() * (w - sw), rnd.nextFloat() * (h - sh)), Size(sw, sh), light.copy(alpha = 0.03f + rnd.nextFloat() * 0.04f))
    }
    repeat((scratches * k).toInt()) {
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        val a = rnd.nextFloat() * PI.toFloat()
        val len = (0.02f + rnd.nextFloat() * scratchLen) * min(w, h)
        lines += MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), (if (rnd.nextBoolean()) light else dark).copy(alpha = 0.18f + rnd.nextFloat() * 0.4f), (0.5f + rnd.nextFloat()) * density)
    }
    repeat((dust * k).toInt()) {
        dots += MDot(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), (0.4f + rnd.nextFloat() * 0.9f) * density, light.copy(alpha = 0.10f + rnd.nextFloat() * 0.25f))
    }
    return Marks(ovals, lines, dots)
}

private fun glassMarks(w: Float, h: Float, wear: Int, d: Float): Marks = scatter(w, h, wear, 11, d, scratches = 9, dust = 70, smudges = 5, scratchLen = 0.22f)
private fun stripMarks(w: Float, h: Float, wear: Int, d: Float): Marks = scatter(w, h, wear, 23, d, scratches = 14, dust = 18, smudges = 4, light = Color.White, dark = Color(0xFF3A3C40), scratchLen = 0.10f)
private fun keyMarks(w: Float, h: Float, wear: Int, d: Float): Marks = scatter(w, h, wear, 31, d, scratches = 2, dust = 6, smudges = 1, scratchLen = 0.3f)

/** Light scuffs along the edges and corners of the black plastic. */
private fun bodyScuffs(w: Float, h: Float, wear: Int, d: Float): Marks {
    if (wear <= 0) return Marks(emptyList(), emptyList(), emptyList())
    val rnd = Random(47)
    val lines = ArrayList<MLine>()
    val n = if (wear >= 2) 46 else 20
    repeat(n) {
        val corner = rnd.nextInt(4)
        val ox = if (corner % 2 == 0) 0f else w
        val oy = if (corner < 2) 0f else h
        val x = ox + (if (ox == 0f) 1 else -1) * rnd.nextFloat() * w * 0.16f
        val y = oy + (if (oy == 0f) 1 else -1) * rnd.nextFloat() * h * 0.05f
        val a = rnd.nextFloat() * PI.toFloat()
        val len = (4f + rnd.nextFloat() * 16f) * d
        lines += MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), Color(0xFF8A8C92).copy(alpha = 0.10f + rnd.nextFloat() * 0.3f), (0.6f + rnd.nextFloat()) * d)
    }
    return Marks(emptyList(), lines, emptyList())
}

// ------------------------------------------------------------------ the speaker grille

/** Holes are precomputed once per size and drawn in three batches (centre, middle, rim) so the cone can "breathe" cheaply. */
private class Grille(size: Size, density: Float, private val wear: Int) {
    private val w = size.width
    private val h = size.height
    private val inset = 10f * density
    private val px = inset
    private val py = inset
    private val pw = w - 2 * inset
    private val ph = h - 2 * inset
    private val cols = 38
    private val pitch = pw / cols
    private val rows = (ph / pitch).toInt()
    private val rings = Array(3) { ArrayList<Offset>() }
    private val rims = Array(3) { ArrayList<Offset>() }          // the lit lower edge of each hole, computed once
    private val clogged = ArrayList<Offset>()
    private val marks: Marks

    init {
        val offX = px + (pw - cols * pitch) / 2 + pitch / 2
        val offY = py + (ph - rows * pitch) / 2 + pitch / 2
        val cx = px + pw / 2
        val cy = py + ph / 2
        val maxD = hypot(pw / 2, ph / 2)
        val rnd = Random(5)
        for (i in 0 until rows) for (j in 0 until cols) {
            val p = Offset(offX + j * pitch, offY + i * pitch)
            val d = hypot(p.x - cx, p.y - cy) / maxD
            val ring = if (d < 0.33f) 0 else if (d < 0.66f) 1 else 2
            rings[ring].add(p)
            rims[ring].add(Offset(p.x + 0.6f, p.y + 0.9f))
            if (wear > 0 && rnd.nextFloat() < (if (wear > 1) 0.035f else 0.012f)) clogged.add(p)       // a few holes are blocked with lint
        }
        // scratches show black on the silver, as in the photo, with a few bright ones
        marks = scatter(w, h, wear, 3, density, scratches = 12, dust = 50, smudges = 6, light = Color.White, dark = Color(0xFF1B1C1F), scratchLen = 0.30f)
    }

    fun draw(s: DrawScope, bass: Float) = with(s) {
        drawRoundRect(Color(0xFF141416), Offset.Zero, Size(w, h), CornerRadius(14.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFC9CCD1), Color(0xFF9EA2A8), Color(0xFFBFC2C7)), startY = py, endY = py + ph),
            Offset(px, py), Size(pw, ph), CornerRadius(3.dp.toPx()),
        )
        if (wear > 0) drawRect(Color(0x14C8A050), Offset(px, py), Size(pw, ph))               // the silver has yellowed a little
        var ly = py
        while (ly < py + ph) { drawLine(Color(0x12000000), Offset(px, ly), Offset(px + pw, ly), 1f); ly += 3f }
        val falloff = floatArrayOf(0.85f, 0.5f, 0.15f)
        for (r in 0 until 3) {
            val dia = pitch * 0.60f * (1f + 0.45f * bass * falloff[r])
            drawPoints(rims[r], PointMode.Points, Color(0x55FFFFFF), dia, StrokeCap.Round)
            drawPoints(rings[r], PointMode.Points, Color(0xFF15161A), dia, StrokeCap.Round)
        }
        if (clogged.isNotEmpty()) drawPoints(clogged, PointMode.Points, Color(0xFF8A8478), pitch * 0.5f, StrokeCap.Round)
        marks.draw(this)
        // the bezel edge of the panel
        drawRoundRect(Color(0x55000000), Offset(px, py), Size(pw, ph), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

// ------------------------------------------------------------------ the cassette and the machine behind it

private class Reels(val left: Offset, val right: Offset, val maxR: Float, val minR: Float)

private fun reelsFor(x: Float, y: Float, cw: Float, ch: Float): Reels {
    val ww = cw * 0.56f; val wh = ch * 0.26f; val wx = x + cw * 0.22f; val wy = y + ch * 0.36f
    return Reels(Offset(wx + ww * 0.26f, wy + wh / 2), Offset(wx + ww * 0.74f, wy + wh / 2), wh * 0.52f, wh * 0.24f)
}

/** What is behind the cassette: the dark well, the two spindles, the cream plastic lever, and the cracked light plate from the photo. */
private fun DrawScope.drawMechanism(x: Float, y: Float, cw: Float, ch: Float, wear: Int) {
    // light grey plate at the lower left; with wear its edge is chipped
    val plate = Path().apply {
        moveTo(size.width * 0.04f, size.height * 0.60f); lineTo(size.width * 0.17f, size.height * 0.60f); lineTo(size.width * 0.17f, size.height * 0.80f)
        if (wear > 0) { lineTo(size.width * 0.15f, size.height * 0.84f); lineTo(size.width * 0.11f, size.height * 0.81f); lineTo(size.width * 0.09f, size.height * 0.90f) }
        else lineTo(size.width * 0.17f, size.height * 0.90f)
        lineTo(size.width * 0.04f, size.height * 0.90f); close()
    }
    drawPath(plate, Color(0xFF4A4B4F))
    // cream plastic lever with its hooks, at the right
    val lx = size.width * 0.925f
    drawRoundRect(Color(0xFF9C957F), Offset(lx, size.height * 0.20f), Size(size.width * 0.022f, size.height * 0.52f), CornerRadius(3.dp.toPx()))
    drawRoundRect(Color(0xFF9C957F), Offset(lx - size.width * 0.02f, size.height * 0.20f), Size(size.width * 0.042f, size.height * 0.05f), CornerRadius(2.dp.toPx()))
    drawRoundRect(Color(0xFF9C957F), Offset(lx - size.width * 0.02f, size.height * 0.67f), Size(size.width * 0.042f, size.height * 0.05f), CornerRadius(2.dp.toPx()))
    val r = reelsFor(x, y, cw, ch)
    for (c in listOf(r.left, r.right)) {
        drawCircle(Color(0xFF17181A), r.minR * 1.1f, c)
        drawCircle(Color(0xFF6C7078), r.minR * 0.55f, c)
    }
}

/** The outline arrow printed on the glass, as on the real door. */
private fun DrawScope.drawInsertArrow(c: Offset, w: Float) {
    val h = w * 0.36f
    val p = Path().apply {
        moveTo(c.x - w / 2, c.y - h * 0.2f); lineTo(c.x + w * 0.05f, c.y - h * 0.2f); lineTo(c.x + w * 0.05f, c.y - h / 2)
        lineTo(c.x + w / 2, c.y); lineTo(c.x + w * 0.05f, c.y + h / 2); lineTo(c.x + w * 0.05f, c.y + h * 0.2f)
        lineTo(c.x - w / 2, c.y + h * 0.2f); close()
    }
    drawPath(p, Color.White.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
}

/**
 * A cassette seen through the door, on the proportions of a real one: the label on top with the coloured stripe and a
 * band of ruled lines for the writing, then the reel window, then the shell's bottom with heads, rollers and moving tape.
 */
private fun DrawScope.drawCassette(x: Float, y: Float, cw: Float, ch: Float, rotL: Float, rotR: Float, p: Float, playing: Boolean, wear: Int) {
    val corner = CornerRadius(ch * 0.06f)
    drawRoundRect(Color(0xFF2A2B2F), Offset(x, y), Size(cw, ch), corner)
    drawRoundRect(Color(0xFF3C3E43), Offset(x, y), Size(cw, ch), corner, style = Stroke(1.5f))
    // label: stripe, writing band (two ruled lines, kept clear of the window), then the reel window
    val lx = x + cw * 0.05f; val ly = y + ch * 0.06f; val lw = cw * 0.90f; val lh = ch * 0.62f
    drawRoundRect(if (wear > 0) Color(0xFFE2D6B3) else Color(0xFFEEE4C8), Offset(lx, ly), Size(lw, lh), CornerRadius(ch * 0.03f))
    drawRect(Color(0xFFD94F3D), Offset(lx, ly), Size(lw, ch * 0.075f))
    for (yy in listOf(0.235f, 0.325f)) drawLine(Color(0xFFA9B8D2), Offset(lx + lw * 0.03f, y + ch * yy), Offset(lx + lw * 0.97f, y + ch * yy), 1f)
    if (wear > 0) {
        drawLine(Color(0x22000000), Offset(lx + lw * 0.62f, ly), Offset(lx + lw * 0.66f, ly + lh), 1f)            // a crease where the label was stuck down
        if (wear > 1) drawCircle(Color(0x16734A1E), ch * 0.09f, Offset(lx + lw * 0.84f, ly + lh * 0.72f), style = Stroke(ch * 0.012f))   // a cup ring
    }
    // the reel window: tape packs that grow and shrink with the song's progress
    val ww = cw * 0.56f; val wh = ch * 0.26f; val wx = x + cw * 0.22f; val wy = y + ch * 0.36f
    drawRoundRect(Color(0xFF14110F), Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2))
    val r = reelsFor(x, y, cw, ch)
    val leftR = r.maxR - (r.maxR - r.minR) * p
    val rightR = r.minR + (r.maxR - r.minR) * p
    drawCircle(Color(0xFF4A3524), leftR, r.left)
    drawCircle(Color(0xFF4A3524), rightR, r.right)
    for ((c, rot) in listOf(r.left to rotL, r.right to rotR)) {
        drawCircle(Color(0xFFDAD5C8), r.minR * 0.85f, c)
        rotate(rot, c) {
            for (k in 0 until 6) {
                val a = k * PI / 3
                drawLine(Color(0xFF2A2B2F), c, Offset(c.x + r.minR * 0.8f * cos(a).toFloat(), c.y + r.minR * 0.8f * sin(a).toFloat()), 2.dp.toPx())
            }
        }
        drawCircle(Color(0xFF2A2B2F), r.minR * 0.25f, c)
    }
    drawRoundRect(Color(0x22FFFFFF), Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2))           // the clear plastic of the tape window
    // the bottom of the shell: trapezoid, rollers, head, and the tape's run past it
    val bottom = Path().apply {
        moveTo(x + cw * 0.14f, y + ch); lineTo(x + cw * 0.22f, y + ch * 0.74f); lineTo(x + cw * 0.78f, y + ch * 0.74f); lineTo(x + cw * 0.86f, y + ch); close()
    }
    drawPath(bottom, Color(0xFF1E1F22))
    val guideL = Offset(x + cw * 0.27f, y + ch * 0.80f)
    val guideR = Offset(x + cw * 0.73f, y + ch * 0.80f)
    val tapeColor = Color(0xFF5A4030)
    drawLine(tapeColor, Offset(r.left.x, r.left.y + leftR), guideL, 2f)
    drawLine(tapeColor, guideL, guideR, 2f)
    drawLine(tapeColor, guideR, Offset(r.right.x, r.right.y + rightR), 2f)
    if (playing) {
        val phase = (rotR * -0.2f)
        drawLine(Color(0x66FFFFFF), guideL, guideR, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 9f), phase))
    }
    drawRoundRect(Color(0xFF8A8D93), Offset(x + cw * 0.455f, y + ch * 0.74f), Size(cw * 0.09f, ch * 0.075f), CornerRadius(2f))     // the head
    for (g in listOf(guideL, guideR)) { drawCircle(Color(0xFF8A8D93), ch * 0.030f, g); drawCircle(Color(0xFF2A2B2F), ch * 0.012f, g) }
    for (hx in listOf(0.31f, 0.69f)) {
        drawCircle(Color(0xFF0C0C0E), ch * 0.040f, Offset(x + cw * hx, y + ch * 0.91f))
        drawCircle(Color(0xFF3C3E43), ch * 0.040f, Offset(x + cw * hx, y + ch * 0.91f), style = Stroke(1.2f))
    }
    for (c in listOf(Offset(x + cw * 0.04f, y + ch * 0.06f), Offset(x + cw * 0.96f, y + ch * 0.06f), Offset(x + cw * 0.04f, y + ch * 0.94f), Offset(x + cw * 0.96f, y + ch * 0.94f))) {
        drawCircle(Color(0xFF8A8D93), ch * 0.022f, c)
    }
}
