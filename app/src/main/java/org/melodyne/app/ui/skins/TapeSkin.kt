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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.Txt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val CASSETTE_ASPECT = 1.587f           // a real cassette is 102 x 64 mm
private val MONO_T = FontFamily.Monospace
private val INK = Color(0xFF1B1C1F)

/**
 * A black-and-silver pocket cassette recorder, full screen: a pulsing speaker grille, a smoked-glass door with a real
 * cassette inside (reels turn at the right relative speeds, the tape moves from one spool to the other with the song),
 * a brushed-silver strip, silk-screened labels and a row of piano keys. Hold REVIEW or CUE to wind, long-press EJECT to leave.
 */
@Composable
internal fun TapeBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val levels = rememberLevels(st.ps.isPlaying)
    val volume = rememberSystemVolume()
    val playing = st.ps.isPlaying
    val loaded = st.ps.hasItem
    val density = LocalDensity.current

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

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1B1E), Color(0xFF0B0B0D))))) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        ) {
            // ---------------------------------------------------------------- speaker grille
            Canvas(Modifier.fillMaxWidth().weight(0.44f)) {
                val f = levels.value
                drawGrille(((f.bands[0] + f.bands[1] + f.bands[2]) / 3f).coerceIn(0f, 1f))
            }
            Spacer(Modifier.height(6.dp))

            // ---------------------------------------------------------------- cassette door
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(0.30f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF09090B))
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
                val topPad = 26.dp
                val cassW = minOf(maxWidth * 0.86f, (maxHeight - topPad - 8.dp) * CASSETTE_ASPECT)
                val cassH = cassW / CASSETTE_ASPECT
                val cassX = (maxWidth - cassW) / 2
                val cassY = topPad + (maxHeight - topPad - cassH) / 2
                val cassHPx = with(density) { cassH.toPx() }

                Canvas(Modifier.fillMaxSize()) {
                    val x = cassX.toPx(); val y = cassY.toPx(); val cw = cassW.toPx(); val ch = cassH.toPx()
                    drawMechanism(x, y, cw, ch)
                    if (loaded) {
                        translate(top = (1f - insert.value) * ch * 0.7f) {
                            drawCassette(x, y, cw, ch, rotL.floatValue, rotR.floatValue, shownProgress, spinning)
                        }
                    } else {
                        drawInsertArrow(Offset(size.width * 0.5f, size.height * 0.80f), size.width * 0.16f)
                    }
                }
                Row(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        "Pocket Line", Modifier.border(1.dp, Color.White.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        size = 12.sp, color = Color.White, fontFamily = MONO_T,
                    )
                    Txt("AC/BATTERY", Modifier.padding(start = 10.dp), size = 10.sp, color = Color.White.copy(alpha = 0.85f), fontFamily = MONO_T)
                }
                if (loaded) {
                    Column(
                        Modifier.offset(cassX + cassW * 0.10f, cassY + cassH * 0.165f).width(cassW * 0.80f)
                            .graphicsLayer { translationY = (1f - insert.value) * cassHPx * 0.7f },
                    ) {
                        Txt(st.ps.title, size = (cassH.value * 0.085f).sp, color = Color(0xFF243A6B), weight = FontWeight.Bold, fontFamily = FontFamily.Cursive, maxLines = 1)
                        Txt(st.ps.artist, size = (cassH.value * 0.068f).sp, color = Color(0xFF243A6B), fontFamily = FontFamily.Cursive, maxLines = 1)
                    }
                }
                Txt(
                    "%04d".format((dragFrac?.let { (it * st.dur.longValue).toLong() } ?: st.pos.longValue) / 1000),
                    Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp).background(Color(0xFF050505)).padding(horizontal = 6.dp, vertical = 2.dp),
                    size = 12.sp, color = Color(0xFFE8E4D8), fontFamily = MONO_T,
                )
                // smoked glass: a faint diagonal sheen and the white hairlines at the top and bottom of the door
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(Color(0x24FFFFFF), Color.Transparent, Color(0x0FFFFFFF)), Offset.Zero, Offset(size.width, size.height)))
                    drawLine(Color(0xD9FFFFFF), Offset(0f, 2.dp.toPx()), Offset(size.width, 2.dp.toPx()), 1.5.dp.toPx())
                    drawLine(Color(0xD9FFFFFF), Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 1.5.dp.toPx())
                }
            }

            // ---------------------------------------------------------------- brushed-silver strip
            Row(
                Modifier.fillMaxWidth().height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFD6D8DB), Color(0xFFA7AAAF), Color(0xFFCBCDD1))))
                    .drawBehind { var y = 0f; while (y < size.height) { drawLine(Color(0x10000000), Offset(0f, y), Offset(size.width, y), 1f); y += 3f } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(12.dp))
                Canvas(Modifier.size(22.dp)) {
                    val bw = size.width / 7f
                    listOf(0.35f to 0f, 0.8f to 1f, 0.55f to 2f, 1f to 3f).forEachIndexed { i, (hgt, _) ->
                        drawRoundRect(INK, Offset(i * bw * 1.8f, size.height * (1f - hgt)), Size(bw, size.height * hgt), CornerRadius(bw / 2))
                    }
                }
                Txt("Vina", Modifier.padding(start = 8.dp), size = 17.sp, weight = FontWeight.Bold, color = INK)
                Spacer(Modifier.weight(1f))
                StripBtn("EQ", act.onEq); StripBtn("LYRICS", act.onLyrics); StripBtn("QUEUE", act.onQueue); StripBtn("MENU", act.onMenu)
                Spacer(Modifier.width(4.dp))
            }

            // ---------------------------------------------------------------- silk-screened labels
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Spacer(Modifier.width(46.dp))
                KeyLabel("PLAY", Modifier.weight(1f))
                KeyLabel("◀◀ REVIEW", Modifier.weight(1f))
                KeyLabel("CUE ▶▶", Modifier.weight(1f))
                KeyLabel("■ STOP", Modifier.weight(1f))
                KeyLabel("EJECT/PAUSE", Modifier.weight(1.25f))
            }

            // ---------------------------------------------------------------- piano keys
            Row(
                Modifier.fillMaxWidth().weight(0.12f).padding(horizontal = 10.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VolumeSlide(volume)
                TapeKey(Modifier.weight(1f), pressed = playing, onTap = { if (!playing) act.onToggle() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(18.dp).drawBehind { if (playing) drawCircle(Color(0x66FF6A1A), size.minDimension * 1.15f) }
                                .clip(RoundedCornerShape(2.dp)).background(if (playing) Color(0xFFFF6A1A) else Color(0xFF6B3515)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Glyph(G.Play, Color(0xFF8D9199), 18.dp)
                    }
                }
                TapeKey(
                    Modifier.weight(1f), pressed = false, repeatDir = -1,
                    onRepeat = { d -> act.scrub(st, d); scrubDir = d; scrubAt = System.currentTimeMillis() }, onTap = act.onPrev,
                ) { DoubleArrow(left = true, color = Color(0xFF8D9199)) }
                TapeKey(
                    Modifier.weight(1f), pressed = false, repeatDir = 1,
                    onRepeat = { d -> act.scrub(st, d); scrubDir = d; scrubAt = System.currentTimeMillis() }, onTap = act.onNext,
                ) { DoubleArrow(left = false, color = Color(0xFF8D9199)) }
                TapeKey(Modifier.weight(1f), pressed = false, onTap = { act.stop(st) }) { Glyph(G.Stop, Color(0xFF8D9199), 18.dp) }
                // tap = pause or resume, like the real key; long-press ejects the tape, which leaves the screen
                TapeKey(
                    Modifier.weight(1.25f), pressed = !playing && loaded, onLongPress = act.onClose,
                    onTap = { if (loaded) act.onToggle() },
                ) { Glyph(G.Pause, Color(0xFF8D9199), 18.dp) }
            }
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
    modifier: Modifier, pressed: Boolean, repeatDir: Int = 0, onRepeat: (Int) -> Unit = {}, onLongPress: (() -> Unit)? = null,
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

// ------------------------------------------------------------------ drawing

/** The silver speaker panel: brushed aluminium with a grid of round holes whose size breathes with the bass. */
private fun DrawScope.drawGrille(bass: Float) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF141416), Offset.Zero, size, CornerRadius(14.dp.toPx()))
    val inset = 10.dp.toPx()
    val px = inset; val py = inset; val pw = w - 2 * inset; val ph = h - 2 * inset
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFC9CCD1), Color(0xFF9EA2A8), Color(0xFFBFC2C7)), startY = py, endY = py + ph),
        Offset(px, py), Size(pw, ph), CornerRadius(3.dp.toPx()),
    )
    var ly = py
    while (ly < py + ph) { drawLine(Color(0x12000000), Offset(px, ly), Offset(px + pw, ly), 1f); ly += 3f }
    val cols = 30
    val s = pw / cols
    val rows = (ph / s).toInt()
    val offX = px + (pw - cols * s) / 2 + s / 2
    val offY = py + (ph - rows * s) / 2 + s / 2
    val cx = px + pw / 2
    val cy = py + ph / 2
    val maxD = hypot(pw / 2, ph / 2)
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val x = offX + j * s
            val y = offY + i * s
            val d = hypot(x - cx, y - cy) / maxD
            val r = s * 0.30f * (1f + 0.45f * bass * (1f - d).coerceAtLeast(0f))
            drawCircle(Color(0x55FFFFFF), r, Offset(x + 0.6f, y + 0.9f))
            drawCircle(Color(0xFF15161A), r, Offset(x, y))
        }
    }
}

private class Reels(val left: Offset, val right: Offset, val maxR: Float, val minR: Float)

private fun reelsFor(x: Float, y: Float, cw: Float, ch: Float): Reels {
    val ww = cw * 0.56f; val wh = ch * 0.26f; val wx = x + cw * 0.22f; val wy = y + ch * 0.30f
    return Reels(Offset(wx + ww * 0.26f, wy + wh / 2), Offset(wx + ww * 0.74f, wy + wh / 2), wh * 0.52f, wh * 0.24f)
}

/** What is behind the cassette: the dark well with the two spindles, and the light plate you can just see in the photo. */
private fun DrawScope.drawMechanism(x: Float, y: Float, cw: Float, ch: Float) {
    drawRoundRect(Color(0xFF3A3B3E), Offset(size.width * 0.05f, size.height * 0.60f), Size(size.width * 0.16f, size.height * 0.30f), CornerRadius(3.dp.toPx()))
    val r = reelsFor(x, y, cw, ch)
    for (c in listOf(r.left, r.right)) {
        drawCircle(Color(0xFF17181A), r.minR * 1.1f, c)
        drawCircle(Color(0xFF6C7078), r.minR * 0.55f, c)
    }
}

private fun DrawScope.drawInsertArrow(c: Offset, w: Float) {
    val h = w * 0.36f
    val p = Path().apply {
        moveTo(c.x - w / 2, c.y - h * 0.2f); lineTo(c.x + w * 0.05f, c.y - h * 0.2f); lineTo(c.x + w * 0.05f, c.y - h / 2)
        lineTo(c.x + w / 2, c.y); lineTo(c.x + w * 0.05f, c.y + h / 2); lineTo(c.x + w * 0.05f, c.y + h * 0.2f)
        lineTo(c.x - w / 2, c.y + h * 0.2f); close()
    }
    drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(1.5.dp.toPx()))
}

/** A cassette seen through the door: label with ruled lines, the reel window, the bottom with heads, rollers and moving tape. */
private fun DrawScope.drawCassette(x: Float, y: Float, cw: Float, ch: Float, rotL: Float, rotR: Float, p: Float, playing: Boolean) {
    val corner = CornerRadius(ch * 0.06f)
    drawRoundRect(Color(0xFF2A2B2F), Offset(x, y), Size(cw, ch), corner)
    drawRoundRect(Color(0xFF3C3E43), Offset(x, y), Size(cw, ch), corner, style = Stroke(1.5f))
    // label, with the coloured stripe and ruled lines for the handwriting
    val lx = x + cw * 0.06f; val ly = y + ch * 0.07f; val lw = cw * 0.88f; val lh = ch * 0.60f
    drawRoundRect(Color(0xFFE9DFC4), Offset(lx, ly), Size(lw, lh), CornerRadius(ch * 0.03f))
    drawRect(Color(0xFFD94F3D), Offset(lx, ly), Size(lw, lh * 0.16f))
    for (k in 0..2) drawLine(Color(0xFFB4C2D8), Offset(lx + lw * 0.04f, ly + lh * (0.40f + k * 0.17f)), Offset(lx + lw * 0.96f, ly + lh * (0.40f + k * 0.17f)), 1f)
    // the reel window: tape packs that grow and shrink with the song's progress
    val ww = cw * 0.56f; val wh = ch * 0.26f; val wx = x + cw * 0.22f; val wy = y + ch * 0.30f
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
