package org.melodyne.app.ui.skins

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** How worn the player looks: 0 mint, 1 used, 2 well loved (the default). Chosen in Settings > Appearance. */
const val WALKMAN_WEAR_PREF = "walkman_wear"
/** Transport button colour: 0 green (the look this skin is built around), 1 silver (what the original actually had). */
const val WALKMAN_BUTTONS_PREF = "walkman_buttons"

private val GOLD = Color(0xFFE2B01E)
private val BODY_INK = Color(0xFF202225)           // the colour printed lettering wears down to
private val LCD_INK = Color(0xFF273122)
private val MONO_W = FontFamily.Monospace

/** The tape's moving parts, shared between the door, the keys and the frame loop. */
private class Motion {
    var dragFrac by mutableStateOf<Float?>(null)
    var scrubDir by mutableIntStateOf(0)
    var scrubAt by mutableLongStateOf(0L)
    val rotL = mutableFloatStateOf(0f)
    val rotR = mutableFloatStateOf(0f)
}

/**
 * A black metal pocket cassette player from the mid-80s, full screen and well loved: brushed gunmetal with chipped
 * edges and scratches where hands grip it, worn gold lettering, a yellowed scuffed window with a real cassette whose
 * reels turn, chunky keys that sink when pressed, a ridged volume wheel, a warm red LED and a small LCD readout.
 * Keys: REW and FF skip on a tap and wind while held; PAUSE toggles; EJECT leaves the screen.
 */
@Composable
internal fun WalkmanBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val volume = rememberSystemVolume()
    val playing = st.ps.isPlaying
    val loaded = st.ps.hasItem
    val wear = remember { ServiceLocator.prefs.getInt(WALKMAN_WEAR_PREF, 2).coerceIn(0, 2) }
    val silver = remember { ServiceLocator.prefs.getInt(WALKMAN_BUTTONS_PREF, 0) == 1 }
    val m = remember { Motion() }

    val progress = if (st.dur.longValue > 0) (st.pos.longValue.toFloat() / st.dur.longValue).coerceIn(0f, 1f) else 0f
    val shownProgress by rememberUpdatedState(m.dragFrac ?: progress)
    val currentProgress by rememberUpdatedState(progress)
    val spinning by rememberUpdatedState(playing && loaded)

    // The tape moves at a constant speed, so the small, fast reel turns quicker than the big, slow one.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val dt = (t - last) / 1e9f
                val scrubbing = m.scrubDir != 0 && System.currentTimeMillis() - m.scrubAt < 350
                val speed = when { scrubbing -> 7f * m.scrubDir; spinning -> 1f; else -> 0f }
                if (speed != 0f) {
                    val p = shownProgress
                    m.rotL.floatValue = (m.rotL.floatValue - 80f * speed / (1f - 0.55f * p) * dt) % 360f
                    m.rotR.floatValue = (m.rotR.floatValue - 80f * speed / (0.45f + 0.55f * p) * dt) % 360f
                }
            }
            last = t
        }
    }
    val insert = remember { Animatable(0f) }
    LaunchedEffect(st.ps.songId, loaded) {
        insert.snapTo(0f)
        if (loaded) insert.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }

    Box(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2F3135), Color(0xFF1F2124), Color(0xFF2A2C30))))
            .drawWithCache {
                val art = BodyArt(size.width, size.height, density, wear)
                onDrawWithContent {
                    art.drawBase(this)
                    drawContent()
                    art.drawWear(this)
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------------------------------------------------------- lettering and the operation light
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    WornText("VINA", 34.sp, wear, 1, italic = true, weight = FontWeight.ExtraBold)
                    WornText("STEREO CASSETTE PLAYER", 8.sp, wear, 2, mono = true)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Led(playing)
                    WornText("OPR/BATT", 7.sp, wear, 3, mono = true)
                }
            }

            // ---------------------------------------------------------------- door with the cassette, and the volume wheel
            Row(Modifier.fillMaxWidth().weight(0.46f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CassetteDoor(
                    st, act, m, wear, loaded, spinning, { currentProgress }, { shownProgress }, { insert.value },
                    Modifier.weight(1f).fillMaxHeight(),
                )
                Column(Modifier.width(34.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    WornText("VOL", 8.sp, wear, 4, mono = true)
                    Spacer(Modifier.height(4.dp))
                    VolumeWheel(volume, wear, Modifier.width(34.dp).weight(0.7f))
                }
            }

            // ---------------------------------------------------------------- the LCD readout and the tape position
            LcdReadout(st, wear)
            TapeBar(st, wear) { act.onSeekTo(it) }

            // ---------------------------------------------------------------- little recessed switches
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniSwitch("EQ", wear, 11, Modifier.weight(1f), act.onEq)
                MiniSwitch("LYRICS", wear, 12, Modifier.weight(1f), act.onLyrics)
                MiniSwitch("QUEUE", wear, 13, Modifier.weight(1f), act.onQueue)
                MiniSwitch("MENU", wear, 14, Modifier.weight(1f), act.onMenu)
                MiniSwitch("EJECT", wear, 15, Modifier.weight(1f), act.onClose)
            }

            // ---------------------------------------------------------------- the chunky keys
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WmKey(
                    Modifier.weight(1f), latched = false, silver = silver, wear = wear, seed = 21, repeatDir = -1,
                    onRepeat = { d -> act.scrub(st, d); m.scrubDir = d; m.scrubAt = System.currentTimeMillis() }, onTap = act.onPrev,
                ) { DoubleArrow(left = true, color = keyInk(silver)) }
                WmKey(Modifier.weight(1f), latched = playing, silver = silver, wear = wear, seed = 22, onTap = { if (!playing) act.onToggle() }) {
                    Glyph(G.Play, keyInk(silver), 24.dp)
                }
                WmKey(
                    Modifier.weight(1f), latched = false, silver = silver, wear = wear, seed = 23, repeatDir = 1,
                    onRepeat = { d -> act.scrub(st, d); m.scrubDir = d; m.scrubAt = System.currentTimeMillis() }, onTap = act.onNext,
                ) { DoubleArrow(left = false, color = keyInk(silver)) }
                WmKey(Modifier.weight(1f), latched = !playing && loaded, silver = silver, wear = wear, seed = 24, onTap = { if (loaded) act.onToggle() }) {
                    Glyph(G.Pause, keyInk(silver), 24.dp)
                }
                WmKey(Modifier.weight(1f), latched = false, silver = silver, wear = wear, seed = 25, onTap = { act.stop(st) }) {
                    Glyph(G.Stop, keyInk(silver), 22.dp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("REW", "PLAY", "FF", "PAUSE", "STOP").forEachIndexed { i, label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { WornText(label, 8.sp, wear, 30 + i, mono = true) }
                }
            }
        }
    }
}

private fun keyInk(silver: Boolean): Color = if (silver) Color(0xCC1B1C1F) else Color(0xCC0A2D12)

// ------------------------------------------------------------------ lettering that has been worn by thumbs

private class WornMask(val fade: Brush, val marks: Marks) {
    fun draw(s: DrawScope) {
        s.drawRect(fade)
        marks.draw(s)
    }
}

/** Paint chips, smudges and a fade, all in the colour of the case, laid over printed lettering so it looks rubbed away. */
private fun wornMask(w: Float, h: Float, wear: Int, seed: Int, d: Float): WornMask {
    val rnd = Random(seed)
    val k = if (wear >= 2) 1.8f else 1f
    val lines = ArrayList<MLine>(); val dots = ArrayList<MDot>(); val ovals = ArrayList<MOval>()
    repeat((4 * k).toInt()) {
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        val a = rnd.nextFloat() * PI.toFloat()
        val len = (0.2f + rnd.nextFloat() * 0.5f) * h
        lines += MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), BODY_INK.copy(alpha = 0.75f + rnd.nextFloat() * 0.2f), (0.8f + rnd.nextFloat()) * d)
    }
    repeat((12 * k).toInt()) {
        dots += MDot(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), (0.5f + rnd.nextFloat() * 1.2f) * d, BODY_INK.copy(alpha = 0.7f + rnd.nextFloat() * 0.25f))
    }
    repeat((2 * k).toInt()) {
        val sw = (0.2f + rnd.nextFloat() * 0.4f) * w
        ovals += MOval(Offset(rnd.nextFloat() * (w - sw), 0f), Size(sw, h), BODY_INK.copy(alpha = 0.12f + rnd.nextFloat() * 0.18f))
    }
    val edge = BODY_INK.copy(alpha = 0.18f + rnd.nextFloat() * 0.35f)
    val fade = if (rnd.nextBoolean()) Brush.horizontalGradient(listOf(Color.Transparent, edge), 0f, w) else Brush.horizontalGradient(listOf(edge, Color.Transparent), 0f, w)
    return WornMask(fade, Marks(ovals, lines, dots))
}

@Composable
private fun WornText(
    text: String, fontSize: TextUnit, wear: Int, seed: Int, modifier: Modifier = Modifier,
    italic: Boolean = false, mono: Boolean = false, weight: FontWeight = FontWeight.Bold, color: Color = GOLD,
) {
    Box(
        modifier.drawWithCache {
            val mask = if (wear > 0) wornMask(size.width, size.height, wear, seed, density) else null
            onDrawWithContent {
                drawContent()
                mask?.draw(this)
            }
        },
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = color, fontSize = fontSize, fontWeight = weight, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (mono) MONO_W else FontFamily.SansSerif,
            ),
            maxLines = 1, softWrap = false,
        )
    }
}

// ------------------------------------------------------------------ the metal case

private class Fleck(val path: Path, val alpha: Float)

/** Everything drawn on the case itself, generated once from fixed seeds so the same player looks the same every time. */
private class BodyArt(private val w: Float, private val h: Float, private val d: Float, private val wear: Int) {
    private val grainLight = ArrayList<Offset>()
    private val grainDark = ArrayList<Offset>()
    private val scratches = ArrayList<MLine>()
    private val flecks = ArrayList<Fleck>()
    private val screws = listOf(Offset(16f * d, 16f * d), Offset(w - 16f * d, 16f * d), Offset(16f * d, h - 16f * d), Offset(w - 16f * d, h - 16f * d))

    init {
        val rnd = Random(86)
        repeat(if (wear > 0) 2600 else 900) {
            grainLight.add(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
            grainDark.add(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
        }
        if (wear > 0) {
            // hands grip the lower half and the right-hand edge, so scratches are denser there, mostly running across the grain
            repeat(if (wear > 1) 340 else 140) {
                val gripped = rnd.nextFloat() < 0.65f
                val y = if (gripped) h * (0.45f + 0.55f * rnd.nextFloat().pow(0.7f)) else rnd.nextFloat() * h
                val x = if (gripped && rnd.nextBoolean()) w * (0.6f + 0.4f * rnd.nextFloat()) else rnd.nextFloat() * w
                val a = if (rnd.nextFloat() < 0.7f) (rnd.nextFloat() - 0.5f) * 0.9f else rnd.nextFloat() * PI.toFloat()
                val len = (6f + rnd.nextFloat() * 34f) * d
                scratches += MLine(
                    Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len),
                    (if (rnd.nextFloat() < 0.75f) Color(0xFFD5D8DE) else Color.Black).copy(alpha = 0.06f + rnd.nextFloat() * 0.18f), (0.5f + rnd.nextFloat() * 0.6f) * d,
                )
            }
            repeat(if (wear > 1) 9 else 4) {                      // a few long hairline scuffs
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val a = (rnd.nextFloat() - 0.5f) * 0.5f
                val len = (60f + rnd.nextFloat() * 90f) * d
                scratches += MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), Color(0xFFD5D8DE).copy(alpha = 0.05f + rnd.nextFloat() * 0.07f), 0.7f * d)
            }
            // paint worn off the edges and corners, with bare aluminium showing through
            repeat(if (wear > 1) 85 else 32) {
                val side = rnd.nextInt(4)
                val t = if (rnd.nextFloat() < 0.5f) (if (rnd.nextBoolean()) rnd.nextFloat() * 0.09f else 1f - rnd.nextFloat() * 0.09f) else rnd.nextFloat()
                val inward = rnd.nextFloat() * 4f * d
                val cx = when (side) { 0 -> t * w; 1 -> w - inward; 2 -> t * w; else -> inward }
                val cy = when (side) { 0 -> inward; 1 -> t * h; 2 -> h - inward; else -> t * h }
                val r = (1.2f + rnd.nextFloat() * 3.8f) * d
                val p = Path()
                val verts = 4 + rnd.nextInt(2)
                for (i in 0 until verts) {
                    val ang = (i / verts.toFloat()) * 2f * PI.toFloat() + rnd.nextFloat() * 0.6f
                    val rr = r * (0.55f + rnd.nextFloat() * 0.7f)
                    val px = cx + cos(ang) * rr
                    val py = cy + sin(ang) * rr * 0.8f
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                flecks.add(Fleck(p, 0.55f + rnd.nextFloat() * 0.4f))
            }
        }
    }

    /** Brushed metal, grain, uneven sheen, scratches, panel seams, bevel and screws. Drawn behind the controls. */
    fun drawBase(s: DrawScope) = with(s) {
        var y = 0f
        var k = 0
        while (y < h) { drawLine(if (k % 2 == 0) Color(0x0AFFFFFF) else Color(0x0A000000), Offset(0f, y), Offset(w, y), 1f); y += 2f; k++ }
        drawPoints(grainLight, PointMode.Points, Color(0x14FFFFFF), 1.3f * d, StrokeCap.Round)
        drawPoints(grainDark, PointMode.Points, Color(0x1A000000), 1.3f * d, StrokeCap.Round)
        // uneven sheen: polished where hands rest, dull and dark elsewhere
        drawRect(Brush.radialGradient(listOf(Color(if (wear > 0) 0x24FFFFFF else 0x12FFFFFF), Color.Transparent), Offset(w * 0.5f, h * 0.80f), w * 0.75f))
        drawRect(Brush.radialGradient(listOf(Color(0x1CFFFFFF), Color.Transparent), Offset(w, h * 0.5f), w * 0.55f))
        drawRect(Brush.radialGradient(listOf(Color(0x30000000), Color.Transparent), Offset(w * 0.1f, h * 0.25f), w * 0.6f))
        for (l in scratches) drawLine(l.color, l.a, l.b, l.w, StrokeCap.Round)
        // recessed panel lines: a dark groove with a thin bright edge under it
        for (fy in listOf(0.105f, 0.60f, 0.93f)) {
            drawLine(Color(0x99000000), Offset(0f, h * fy), Offset(w, h * fy), 1.5f * d)
            drawLine(Color(0x22FFFFFF), Offset(0f, h * fy + 1.5f * d), Offset(w, h * fy + 1.5f * d), 1f * d)
        }
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0x66FFFFFF), Color.Transparent, Color(0x88000000)), Offset.Zero, Offset(w, h)),
            Offset(d, d), Size(w - 2f * d, h - 2f * d), CornerRadius(10f * d), style = Stroke(2.5f * d),
        )
        screws.forEachIndexed { i, c -> drawScrew(c, 5.5f * d, 20f + i * 37f, wear) }
    }

    /** Chipped paint along the edges, drawn over everything. */
    fun drawWear(s: DrawScope) = with(s) {
        for (f in flecks) {
            translate(0.8f * d, 0.8f * d) { drawPath(f.path, Color(0x66000000)) }
            drawPath(f.path, Color(0xFFC3C7CE).copy(alpha = f.alpha))
        }
    }
}

/** A Phillips screw: a grime ring, a domed head, a cross slot at its own angle, and a dark rim. */
private fun DrawScope.drawScrew(c: Offset, r: Float, angle: Float, wear: Int) {
    if (wear > 0) drawCircle(Color(0x66000000), r * 1.35f, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB8BBC0), Color(0xFF6B6E74)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.4f), r, c)
    rotate(angle, c) {
        drawLine(Color(0xFF2A2B2E), Offset(c.x - r * 0.7f, c.y), Offset(c.x + r * 0.7f, c.y), r * 0.28f)
        drawLine(Color(0xFF2A2B2E), Offset(c.x, c.y - r * 0.7f), Offset(c.x, c.y + r * 0.7f), r * 0.28f)
    }
    drawCircle(Color(0x66000000), r, c, style = Stroke(r * 0.16f))
}

// ------------------------------------------------------------------ the cassette door

private class DoorArt(size: Size, private val d: Float, private val wear: Int) {
    private val w = size.width
    private val h = size.height
    private val wl = 14f * d
    private val wt = 26f * d
    private val ww = w - 28f * d
    private val wh = h - 52f * d
    private val dust = ArrayList<MDot>()

    init {
        if (wear > 0) {
            val rnd = Random(7)
            val perimeter = 2f * (ww + wh)
            repeat(if (wear > 1) 150 else 60) {
                val t = rnd.nextFloat() * perimeter
                val out = rnd.nextFloat() * 6f * d
                // dust and grime collect in the recess around the window
                val p = when {
                    t < ww -> Offset(wl + t, wt - out)
                    t < ww + wh -> Offset(wl + ww + out, wt + (t - ww))
                    t < 2f * ww + wh -> Offset(wl + ww - (t - ww - wh), wt + wh + out)
                    else -> Offset(wl - out, wt + wh - (t - 2f * ww - wh))
                }
                dust += MDot(p, (0.5f + rnd.nextFloat() * 1.1f) * d, Color(0xFFC2B79B).copy(alpha = 0.18f + rnd.nextFloat() * 0.3f))
            }
        }
    }

    fun draw(s: DrawScope) = with(s) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF26282C), Color(0xFF191B1E))), Offset.Zero, Size(w, h), CornerRadius(10f * d))
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0x55FFFFFF), Color.Transparent, Color(0x99000000)), Offset.Zero, Offset(w, h)),
            Offset(d, d), Size(w - 2f * d, h - 2f * d), CornerRadius(10f * d), style = Stroke(2f * d),
        )
        drawRoundRect(Color(0xFF050506), Offset(wl - 3f * d, wt - 3f * d), Size(ww + 6f * d, wh + 6f * d), CornerRadius(9f * d))
        drawRoundRect(Color(0x33FFFFFF), Offset(wl - 3f * d, wt - 3f * d), Size(ww + 6f * d, wh + 6f * d), CornerRadius(9f * d), style = Stroke(1f * d))
        for (p in dust) drawCircle(p.color, p.r, p.c)
        for (c in listOf(Offset(9f * d, 9f * d), Offset(w - 9f * d, 9f * d), Offset(9f * d, h - 9f * d), Offset(w - 9f * d, h - 9f * d))) drawScrew(c, 4.2f * d, c.x * 0.7f, wear)
    }
}

@Composable
private fun CassetteDoor(
    st: SkinState, act: SkinActions, m: Motion, wear: Int, loaded: Boolean, spinning: Boolean,
    progress: () -> Float, shown: () -> Float, insert: () -> Float, modifier: Modifier,
) {
    val uiDensity = LocalDensity.current
    BoxWithConstraints(
        modifier.drawWithCache {
            val art = DoorArt(size, density, wear)
            onDrawWithContent {
                art.draw(this)
                drawContent()
            }
        },
    ) {
        val winW = maxWidth - 28.dp
        val winH = maxHeight - 52.dp
        val cassW = minOf(winW * 0.92f, winH * 0.92f * CASSETTE_ASPECT)
        val cassH = cassW / CASSETTE_ASPECT
        val cassLX = (winW - cassW) / 2
        val cassLY = (winH - cassH) / 2
        val cassHPx = with(uiDensity) { cassH.toPx() }
        val titleSp = with(uiDensity) { (cassH * 0.082f).toSp() }
        val artistSp = with(uiDensity) { (cassH * 0.062f).toSp() }

        // the window: a cassette inside, and you can turn the tape by hand by dragging along it
        Box(
            Modifier.offset(14.dp, 26.dp).size(winW, winH).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0B0B0D))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { m.dragFrac = progress() },
                        onDragEnd = { m.dragFrac?.let { f -> if (st.dur.longValue > 0) act.onSeekTo((f * st.dur.longValue).toLong()) }; m.dragFrac = null },
                        onDragCancel = { m.dragFrac = null },
                    ) { change, dx ->
                        m.dragFrac = ((m.dragFrac ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                        m.scrubDir = if (dx >= 0) 1 else -1
                        m.scrubAt = System.currentTimeMillis()
                        change.consume()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = cassW.toPx(); val ch = cassH.toPx()
                val x = cassLX.toPx(); val y = cassLY.toPx()
                if (loaded) {
                    translate(top = (1f - insert()) * ch * 0.7f) {
                        drawCassette(x, y, cw, ch, m.rotL.floatValue, m.rotR.floatValue, shown(), spinning, wear)
                    }
                }
            }
            if (loaded) {
                // the writing sits in its own band on the label, above the reel window
                Column(
                    Modifier.offset(cassLX + cassW * 0.085f, cassLY + cassH * 0.145f).width(cassW * 0.83f).height(cassH * 0.20f)
                        .graphicsLayer { translationY = (1f - insert()) * cassHPx * 0.7f },
                ) {
                    BasicText(
                        st.ps.title, style = TextStyle(color = Color(0xFF1F356B), fontSize = titleSp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive),
                        maxLines = 1,
                    )
                    BasicText(
                        st.ps.artist, style = TextStyle(color = Color(0xFF2B4380), fontSize = artistSp, fontFamily = FontFamily.Cursive),
                        maxLines = 1,
                    )
                }
            }
            // aged plastic in front of everything: yellowing haze, dirt in the rim, glare, scuffs and dust
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val marks = scatter(size.width, size.height, wear, 91, density, scratches = 12, dust = 80, smudges = 6, light = Color.White, dark = Color(0xFF3A3326), scratchLen = 0.26f)
                    onDrawWithContent {
                        drawRect(Color(if (wear > 0) 0x22E4C870 else 0x0A000000))
                        if (wear > 0) {
                            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x40C9A24A)), Offset(size.width / 2f, size.height / 2f), max(size.width, size.height) * 0.75f))
                        }
                        drawRoundRect(Color(0x66000000), Offset.Zero, size, CornerRadius(6.dp.toPx()), style = Stroke(8.dp.toPx()))
                        drawRoundRect(Color(0x33000000), Offset(4.dp.toPx(), 4.dp.toPx()), Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), CornerRadius(5.dp.toPx()), style = Stroke(4.dp.toPx()))
                        val glare = Path().apply {
                            moveTo(size.width * 0.15f, 0f); lineTo(size.width * 0.35f, 0f); lineTo(size.width * 0.10f, size.height); lineTo(size.width * -0.10f, size.height); close()
                        }
                        drawPath(glare, Color(0x12FFFFFF))
                        marks.draw(this)
                    }
                },
            )
        }
        // printed lettering on the plate around the window
        WornText("BUDOKAN", 11.sp, wear, 41, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 7.dp), italic = true)
        WornText("STEREO", 9.sp, wear, 42, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 9.dp), mono = true)
        WornText("DIRECT DRIVE", 9.sp, wear, 43, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 8.dp), mono = true)
        WornText("NR", 9.sp, wear, 44, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp), mono = true)
    }
}

// ------------------------------------------------------------------ operation light

/** A small red lens that glows warmly while the tape runs, with a slight flicker like an old LED. */
@Composable
private fun Led(on: Boolean) {
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(400), label = "led")
    val flicker by rememberInfiniteTransition(label = "flicker").animateFloat(
        0.88f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "flickerValue",
    )
    Canvas(Modifier.size(34.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val level = glow * flicker
        if (glow > 0.01f) {
            drawCircle(Brush.radialGradient(listOf(Color(0x66FF3B30), Color.Transparent), c, size.minDimension / 2f), size.minDimension / 2f, c, alpha = level)
        }
        drawCircle(Color(0xFF2A0A0A), 6.dp.toPx(), c)
        drawCircle(lerp(Color(0xFF4A1210), Color(0xFFFF5A44), level), 4.5.dp.toPx(), c)
        drawCircle(Color(0x66FFFFFF), 1.2.dp.toPx(), Offset(c.x - 1.5.dp.toPx(), c.y - 1.5.dp.toPx()))
    }
}

// ------------------------------------------------------------------ readout and tape position

@Composable
private fun LcdReadout(st: SkinState, wear: Int) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFC3CFB3), Color(0xFFAEBCA0))))
            .border(2.dp, Color(0xFF0E0F11), RoundedCornerShape(5.dp))
            .drawWithCache {
                val marks = scatter(size.width, size.height, wear, 57, density, scratches = 6, dust = 40, smudges = 3, light = Color.White, dark = Color(0xFF3A4034), scratchLen = 0.2f)
                onDrawWithContent {
                    drawContent()
                    if (wear > 0) drawRect(Color(0x1CE4C870))                         // the LCD's plastic has yellowed
                    marks.draw(this)
                }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SevenSeg(mmss(st.pos.longValue), LCD_INK, 13.dp, 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Marquee(st.ps.title.ifBlank { "VINA" }.uppercase(), LCD_INK, 13)
            BasicText(
                kbps(st) + " KBPS   " + khz(st) + " KHZ   " + st.ps.artist.uppercase(),
                style = TextStyle(color = LCD_INK.copy(alpha = 0.75f), fontSize = 9.sp, fontFamily = MONO_W, fontWeight = FontWeight.Bold),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** A printed tape-position scale with a worn gold marker. Tap or drag to seek. */
@Composable
private fun TapeBar(st: SkinState, wear: Int, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = st.dur.longValue
    val frac = drag ?: if (dur > 0) (st.pos.longValue.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(34.dp)
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
            val gy = size.height * 0.62f
            val gh = 8.dp.toPx()
            drawRoundRect(Color(0xFF0C0D0F), Offset(0f, gy - gh / 2), Size(size.width, gh), CornerRadius(gh / 2))
            drawRoundRect(Color(0x33FFFFFF), Offset(0f, gy - gh / 2), Size(size.width, gh), CornerRadius(gh / 2), style = Stroke(1.dp.toPx()))
            drawRoundRect(Color(0xFFB8901C).copy(alpha = 0.85f), Offset(0f, gy - gh / 2), Size(size.width * frac, gh), CornerRadius(gh / 2))
            for (i in 0..10) {                                               // the printed scale, a little faded
                val x = size.width * i / 10f
                drawLine(GOLD.copy(alpha = if (wear > 0) 0.45f else 0.8f), Offset(x, gy - gh / 2 - (if (i % 5 == 0) 9 else 5).dp.toPx()), Offset(x, gy - gh / 2 - 2.dp.toPx()), 1.dp.toPx())
            }
            val mx = (size.width * frac).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
            drawRoundRect(Color(0xFF3A2E08), Offset(mx - 4.dp.toPx(), gy - 12.dp.toPx()), Size(8.dp.toPx(), 24.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(GOLD.copy(alpha = if (wear > 0) 0.8f else 1f), Offset(mx - 3.dp.toPx(), gy - 11.dp.toPx()), Size(6.dp.toPx(), 22.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawLine(Color(0x88FFFFFF), Offset(mx, gy - 10.dp.toPx()), Offset(mx, gy + 10.dp.toPx()), 1.dp.toPx())
        }
    }
}

// ------------------------------------------------------------------ switches, wheel and keys

@Composable
private fun MiniSwitch(label: String, wear: Int, seed: Int, modifier: Modifier, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val slide by animateFloatAsState(if (down) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "switch")
    Column(
        modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = { down = true; tryAwaitRelease(); down = false },
                onTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(Modifier.size(width = 40.dp, height = 18.dp)) {
            drawRoundRect(Color(0xFF08090A), Offset.Zero, size, CornerRadius(3.dp.toPx()))
            drawRoundRect(Color(0x33FFFFFF), Offset.Zero, size, CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
            val tabW = 16.dp.toPx()
            val x = 3.dp.toPx() + slide * (size.width - tabW - 6.dp.toPx())
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8B8F96), Color(0xFF4E5157))), Offset(x, 2.dp.toPx()), Size(tabW, size.height - 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            for (i in 0..2) drawLine(Color(0x66000000), Offset(x + 4.dp.toPx() + i * 3.dp.toPx(), 4.dp.toPx()), Offset(x + 4.dp.toPx() + i * 3.dp.toPx(), size.height - 4.dp.toPx()), 1.dp.toPx())
        }
        WornText(label, 7.sp, wear, seed, mono = true)
    }
}

/** A thumb-wheel in the side of the case. The ridges scroll as you turn it, and are worn smooth where the thumb rests. */
@Composable
private fun VolumeWheel(volume: VolumeControl, wear: Int, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectVerticalDragGestures { change, dy -> volume.set((volume.level.value - dy / 260f).coerceIn(0f, 1f)); change.consume() } }
            .drawWithCache {
                val marks = scatter(size.width, size.height, wear, 63, density, scratches = 6, dust = 16, smudges = 2, light = Color.White, dark = Color.Black, scratchLen = 0.5f)
                onDrawWithContent {
                    drawContent()
                    if (wear > 0) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x3AB0B4BA), Color.Transparent), size.height * 0.2f, size.height * 0.8f))
                    marks.draw(this)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color(0xFF0C0D0F), Offset.Zero, size, CornerRadius(8.dp.toPx()))
            val inset = 3.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF1A1B1E), Color(0xFF5A5D63), Color(0xFF8C9097), Color(0xFF5A5D63), Color(0xFF1A1B1E))),
                Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(6.dp.toPx()),
            )
            val spacing = 7.dp.toPx()
            val offset = (volume.level.value * 420.dp.toPx()) % spacing
            var y = inset - spacing + offset
            while (y < size.height) {
                if (y > inset && y < size.height - inset) {
                    drawLine(Color(0x88000000), Offset(inset + 2.dp.toPx(), y), Offset(size.width - inset - 2.dp.toPx(), y), 2.dp.toPx())
                    drawLine(Color(0x33FFFFFF), Offset(inset + 2.dp.toPx(), y + 2.dp.toPx()), Offset(size.width - inset - 2.dp.toPx(), y + 2.dp.toPx()), 1.dp.toPx())
                }
                y += spacing
            }
        }
    }
}

/** A chunky key. It sinks when pressed, and stays down while its function is active. The cap is darkened and smoothed by finger oil. */
@Composable
private fun WmKey(
    modifier: Modifier, latched: Boolean, silver: Boolean, wear: Int, seed: Int, repeatDir: Int = 0, onRepeat: (Int) -> Unit = {},
    onTap: () -> Unit, content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val repeatCb by rememberUpdatedState(onRepeat)
    var down by remember { mutableStateOf(false) }
    val depth by animateFloatAsState(if (latched || down) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "key")
    Box(
        modifier.fillMaxHeight()
            .graphicsLayer { translationY = depth * 5.dp.toPx() }
            .pointerInput(repeatDir) {
                detectTapGestures(
                    onPress = {
                        down = true
                        var held = false
                        val job = if (repeatDir != 0) scope.launch {
                            delay(350)
                            held = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            while (true) { repeatCb(repeatDir); delay(200) }
                        } else null
                        val released = tryAwaitRelease()
                        job?.cancel()
                        down = false
                        if (repeatDir != 0 && released && !held) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); tap() }
                    },
                    onTap = { if (repeatDir == 0) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); tap() } },
                )
            }
            .drawWithCache {
                val worn = scatter(size.width, size.height, wear, 400 + seed, density, scratches = 3, dust = 8, smudges = 0, light = if (silver) Color.White else Color(0xFFBFF5C8), dark = Color.Black, scratchLen = 0.3f)
                onDrawWithContent {
                    val r = 5.dp.toPx()
                    val lip = 3.dp.toPx()
                    val top = if (silver) Color(0xFFD9DBDF) else Color(0xFF39C957)
                    val bottom = if (silver) Color(0xFF9A9EA5) else Color(0xFF1C8A33)
                    drawRoundRect(Color(0xCC000000), Offset(0f, lip), size, CornerRadius(r))
                    drawRoundRect(Brush.verticalGradient(listOf(top, bottom)), Offset.Zero, Size(size.width, size.height - lip), CornerRadius(r))
                    drawRoundRect(Color(0x55FFFFFF), Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                    drawRoundRect(Color(0x66000000), Offset.Zero, Size(size.width, size.height - lip), CornerRadius(r), style = Stroke(1.dp.toPx()))
                    if (wear > 0) {
                        // finger oil: the middle of the cap is smoothed and a little darker
                        drawOval(
                            Brush.radialGradient(listOf(Color(0x44000000), Color.Transparent), Offset(size.width / 2f, size.height * 0.45f), size.width * 0.45f),
                            Offset(size.width * 0.12f, size.height * 0.12f), Size(size.width * 0.76f, size.height * 0.6f),
                        )
                    }
                    drawContent()
                    worn.draw(this)
                }
            },
        contentAlignment = Alignment.Center, content = content,
    )
}
