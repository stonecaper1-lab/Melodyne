package org.melodyne.app.ui.skins

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.melodyne.app.di.ServiceLocator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ======================================================================================================
// Sony DD-100 "Boodo Khan" (1986), as measured from the reference photographs.
//
// The front face is drawn in the pixel coordinates of the reference photo: the body is 766 x 1080. The band that carries
// the lettering and the controls runs at 41.5 degrees, so most of the lettering is placed in a rotated frame:
// "u" runs along the text (rising to the right) and "v" across it, with the origin at the centre of the face.
// Only the Sony wordmark is replaced (by VINA). Everything else is as the original prints it.
// The player always looks well loved, and has the silver keys of the original.
// ======================================================================================================

private const val FW = 766f
private const val FH = 1080f
private const val ANG = 41.5f
private const val CX = 383f
private const val CY = 540f
private val COSA = cos(Math.toRadians(ANG.toDouble())).toFloat()
private val SINA = sin(Math.toRadians(ANG.toDouble())).toFloat()

/** Band frame (u along the text, v across it) to reference pixels. */
private fun ref(u: Float, v: Float): Offset = Offset(CX + u * COSA + v * SINA, CY - u * SINA + v * COSA)

/** Reference pixels to the band frame. */
private fun toBand(x: Float, y: Float): Offset = Offset((x - CX) * COSA - (y - CY) * SINA, (x - CX) * SINA + (y - CY) * COSA)

// Control centres in the band frame, measured from the photograph.
private val REW_UV = Offset(367f, -206f)
private val FF_UV = Offset(247f, -207f)
private val STOP_UV = Offset(311f, 7f)
private val PLAY_UV = Offset(182f, -96f)
private val LED_UV = Offset(484f, -101f)

/** How bright the anodised face is, as a function of the distance across the band. Measured, not guessed. */
private val SHADE = listOf(
    -660f to 150f, -590f to 141f, -510f to 124f, -430f to 108f, -350f to 92f, -324f to 94f,
    -310f to 100f, -270f to 105f, -230f to 89f, -190f to 88f, -150f to 80f, -100f to 85f, -30f to 88f,
    10f to 67f, 50f to 43f, 90f to 28f, 118f to 26f, 130f to 35f, 170f to 32f, 210f to 28f, 250f to 25f, 330f to 22f, 450f to 20f, 660f to 18f,
)

private fun shadeAt(v: Float): Float {
    if (v <= SHADE.first().first) return SHADE.first().second
    if (v >= SHADE.last().first) return SHADE.last().second
    val i = SHADE.indexOfLast { it.first <= v }
    val (v0, l0) = SHADE[i]
    val (v1, l1) = SHADE[i + 1]
    return l0 + (l1 - l0) * ((v - v0) / (v1 - v0))
}

private fun lumColor(l: Float): Color {
    val c = l.coerceIn(0f, 255f) / 255f
    return Color(min(1f, c + 0.004f), c, max(0f, c - 0.01f))
}

private val INK_WHITE = Color(0xFFEEEBE2)
private val INK_BLUE = Color(0xFF5477E0)
private val SILVER_TOP = Color(0xFFFBFBF9)
private val SILVER_MID = Color(0xFFE2E3DF)
private val SILVER_LOW = Color(0xFFB4B5B0)
private val GREEN_RING = Color(0xFF1EC28C)

private class Motion {
    var dragFrac by mutableStateOf<Float?>(null)
    var scrubDir by mutableIntStateOf(0)
    var scrubAt by mutableLongStateOf(0L)
    val rotL = mutableFloatStateOf(0f)
    val rotR = mutableFloatStateOf(0f)
}

private fun DrawScope.band(block: DrawScope.() -> Unit) {
    translate(CX, CY) { rotate(-ANG, Offset.Zero) { block() } }
}

private fun paintOf(color: Color, size: Float, face: Typeface): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = size; typeface = face; isSubpixelText = true }

private val SANS_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val SANS_BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
private val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
private val SANS_REG: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
private val SANS_LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

/** Draws [text] with its glyph box centred on [vc], starting at [u0] (or centred / right-aligned), stretched to [len] if given. */
private fun DrawScope.fitText(text: String, p: Paint, u0: Float, vc: Float, len: Float? = null, center: Boolean = false, right: Boolean = false) {
    p.letterSpacing = 0f
    if (len != null && text.length > 1) {
        val natural = p.measureText(text)
        p.letterSpacing = ((len - natural) / (text.length - 1)) / p.textSize
    }
    val r = android.graphics.Rect()
    p.getTextBounds(text, 0, text.length, r)
    val w = p.measureText(text)
    val x = when {
        center -> u0 - w / 2f
        right -> u0 - w
        else -> u0 - r.left
    }
    val y = vc - (r.top + r.bottom) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

// ------------------------------------------------------------------------------------------------------
//  The skin
// ------------------------------------------------------------------------------------------------------

/**
 * The front of the player (controls), its back (the cassette, lying horizontally), and its top edge (volume wheel and
 * headphone jacks), on a dark wooden table. In portrait they stack; in landscape the front stands beside the other two.
 * REW and FF skip on a tap and wind while held; PLAY starts and pauses; STOP stops; the DOL switch is the Loudness
 * preset; the wheel on the top edge is the volume; dragging along the cassette window seeks.
 */
@Composable
internal fun WalkmanBody(st: SkinState, act: SkinActions, modifier: Modifier) {
    val volume = rememberSystemVolume()
    val playing = st.ps.isPlaying
    val loaded = st.ps.hasItem
    val m = remember { Motion() }
    val eq by ServiceLocator.eq.state.collectAsState()
    val dolOn = eq.presetId == "low_volume"

    val progress = if (st.dur.longValue > 0) (st.pos.longValue.toFloat() / st.dur.longValue).coerceIn(0f, 1f) else 0f
    val shownProgress by rememberUpdatedState(m.dragFrac ?: progress)
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
                    m.rotL.floatValue = (m.rotL.floatValue + 80f * speed / (1f - 0.55f * p) * dt) % 360f
                    m.rotR.floatValue = (m.rotR.floatValue + 80f * speed / (0.45f + 0.55f * p) * dt) % 360f
                }
            }
            last = t
        }
    }
    val insert = remember { Animatable(0f) }
    LaunchedEffect(st.ps.songId, loaded) {
        insert.snapTo(0f)
        if (loaded) insert.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
    }

    Box(
        modifier.fillMaxSize().drawWithCache {
            val wood = Wood(size.width, size.height, density)
            onDrawWithContent {
                wood.draw(this)
                drawContent()
            }
        },
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val aw = maxWidth.value
                val ah = maxHeight.value
                val gap = 8f
                val actionH = 34f
                if (aw > ah * 1.15f) {
                    // landscape: the front stands on the left; the cassette side, the top edge and the buttons stack on the right
                    val frontW = min((ah - 4f) / (FH / FW), aw * 0.34f)
                    val colW = min(aw - frontW - 24f, (ah - actionH - 2f * gap) / (BACK_H / BACK_W + 0.8f / STRIP_ASPECT))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        FrontFace(st, act, m, playing, dolOn, { ServiceLocator.eq.setPreset(if (dolOn) "flat" else "low_volume") }, frontW.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                            BackPanel(st, m, loaded, spinning, { shownProgress }, { insert.value }, act, colW.dp)
                            TopStrip(volume, (colW * 0.8f).dp)
                            ActionRow(st, act, colW.dp)
                        }
                    }
                } else {
                    // portrait: cassette side on top, front below it, then the top edge with the volume wheel, then the buttons
                    val w = min(aw, (ah - actionH - 3f * gap) / (BACK_H / BACK_W + FH / FW + 1f / STRIP_ASPECT))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                        BackPanel(st, m, loaded, spinning, { shownProgress }, { insert.value }, act, w.dp)
                        FrontFace(st, act, m, playing, dolOn, { ServiceLocator.eq.setPreset(if (dolOn) "flat" else "low_volume") }, w.dp)
                        TopStrip(volume, w.dp)
                        ActionRow(st, act, w.dp)
                    }
                }
            }
        }
    }
}

private const val BACK_W = 990f
private const val BACK_H = 677f
private const val STRIP_W = 1000f
private const val STRIP_H = 345f
private const val STRIP_ASPECT = STRIP_W / STRIP_H

/** A very dark walnut table with a soft vignette, like the one in the product photographs. */
private class Wood(private val w: Float, private val h: Float, private val d: Float) {
    private val grain = ArrayList<MLine>()

    init {
        val rnd = Random(3)
        repeat(260) {
            val y = rnd.nextFloat() * h
            val x = rnd.nextFloat() * w * 0.6f
            val len = w * (0.3f + rnd.nextFloat() * 0.7f)
            val c = if (rnd.nextFloat() < 0.65f) Color(0xFF000000) else Color(0xFF8A5A30)
            grain.add(MLine(Offset(x, y), Offset(x + len, y + (rnd.nextFloat() - 0.5f) * 8f * d), c.copy(alpha = 0.04f + rnd.nextFloat() * 0.09f), (0.6f + rnd.nextFloat() * 1.8f) * d))
        }
    }

    fun draw(s: DrawScope) = with(s) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1C12), Color(0xFF17100B), Color(0xFF110C08))), Offset.Zero, Size(w, h))
        for (g in grain) drawLine(g.color, g.a, g.b, g.w, StrokeCap.Round)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xAA000000)), Offset(w / 2f, h / 2f), max(w, h) * 0.75f), Offset.Zero, Size(w, h))
    }
}

/** A soft shadow under a unit, built from a few translucent rounded rectangles. */
private fun Modifier.unitShadow(): Modifier = this.drawBehind {
    for (i in 1..6) {
        val grow = i * 3.dp.toPx()
        drawRoundRect(Color(0x14000000), Offset(-grow + 2.dp.toPx(), -grow + 7.dp.toPx() + i * 1.5.dp.toPx()), Size(size.width + 2 * grow - 4.dp.toPx(), size.height + 2 * grow), CornerRadius(14.dp.toPx() + grow))
    }
}

// ------------------------------------------------------------------------------------------------------
//  The front face
// ------------------------------------------------------------------------------------------------------

private class Streak(val u0: Float, val u1: Float, val v: Float, val color: Color, val w: Float)
private class Fleck(val path: Path, val alpha: Float)

/** Everything on the front face that does not move, generated once from fixed seeds so the player looks the same every time. */
private class FrontArt {
    private val grainLight = ArrayList<Offset>()
    private val grainDark = ArrayList<Offset>()
    private val streaks = ArrayList<Streak>()
    private val scratches = ArrayList<MLine>()
    private val rubs = ArrayList<MOval>()
    private val grime = ArrayList<MDot>()
    private val chips = ArrayList<Fleck>()
    private val wornBand = ArrayList<MLine>()
    private val wornBandDots = ArrayList<MDot>()
    private val wornAbs = ArrayList<MDot>()
    private val body: Path = Path().apply { addRoundRect(RoundRect(0f, 0f, FW, FH, 14f, 14f)) }

    private val kanji = paintOf(INK_WHITE, 92f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
    private val boodo = paintOf(INK_BLUE, 38f, SANS_REG)
    private val small = paintOf(INK_WHITE, 17f, SANS_MEDIUM)
    private val label = paintOf(INK_WHITE, 22.5f, SANS_MEDIUM)
    private val battery = paintOf(INK_WHITE, 21f, SANS_BOLD)
    private val wordmark = paintOf(INK_WHITE, 70f, SERIF_BOLD)
    private val dol = paintOf(INK_WHITE, 34f, SANS_BOLD)
    private val onoff = paintOf(INK_WHITE, 17f, SANS_MEDIUM)
    private val tiny = paintOf(INK_WHITE, 18.5f, SANS_BOLD)

    init {
        val rnd = Random(86)
        repeat(2600) { grainLight.add(Offset(rnd.nextFloat() * FW, rnd.nextFloat() * FH)); grainDark.add(Offset(rnd.nextFloat() * FW, rnd.nextFloat() * FH)) }
        // brushed streaks along the band, brighter towards its upper edge and fading out towards the lower seam
        repeat(900) {
            val v = -324f + rnd.nextFloat() * 450f
            val fade = (1f - ((v + 324f) / 450f)).pow(1.3f)
            val u0 = -700f + rnd.nextFloat() * 900f
            val len = 70f + rnd.nextFloat() * 520f
            val light = rnd.nextFloat() < 0.78f
            streaks.add(Streak(u0, u0 + len, v, (if (light) Color.White else Color.Black).copy(alpha = (0.02f + rnd.nextFloat() * 0.13f) * (0.25f + fade)), 0.6f + rnd.nextFloat() * 1.4f))
        }
        // micro-scratches: denser in the lower half and on the right, where hands hold it; mostly across the grain
        repeat(380) {
            val gripped = rnd.nextFloat() < 0.65f
            val y = if (gripped) FH * (0.45f + 0.55f * rnd.nextFloat().pow(0.7f)) else rnd.nextFloat() * FH
            val x = if (gripped && rnd.nextBoolean()) FW * (0.6f + 0.4f * rnd.nextFloat()) else rnd.nextFloat() * FW
            val a = if (rnd.nextFloat() < 0.7f) (rnd.nextFloat() - 0.5f) * 0.9f else rnd.nextFloat() * PI.toFloat()
            val len = 6f + rnd.nextFloat() * 34f
            scratches.add(MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), (if (rnd.nextFloat() < 0.75f) Color(0xFFD5D8DE) else Color.Black).copy(alpha = 0.06f + rnd.nextFloat() * 0.2f), 0.5f + rnd.nextFloat() * 0.7f))
        }
        repeat(10) {                                                  // a few long hairline scuffs
            val x = rnd.nextFloat() * FW
            val y = FH * (0.35f + rnd.nextFloat() * 0.6f)
            val a = (rnd.nextFloat() - 0.5f) * 0.5f
            val len = 80f + rnd.nextFloat() * 160f
            scratches.add(MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), Color(0xFFD5D8DE).copy(alpha = 0.05f + rnd.nextFloat() * 0.07f), 0.8f))
        }
        // polished where thumbs rest: round the controls and in the lower right
        for (c in listOf(ref(PLAY_UV.x, PLAY_UV.y), ref(STOP_UV.x, STOP_UV.y), Offset(640f, 930f), Offset(540f, 760f))) rubs.add(MOval(Offset(c.x - 90f, c.y - 70f), Size(180f, 140f), Color.White.copy(alpha = 0.035f)))
        // dust and grime collected in the seams and around the controls
        repeat(260) {
            val onSeam = rnd.nextBoolean()
            val u = -800f + rnd.nextFloat() * 1600f
            val v = if (onSeam) (if (rnd.nextBoolean()) -324f else 118f) + (rnd.nextFloat() - 0.5f) * 9f else -320f + rnd.nextFloat() * 440f
            val p = ref(u, v)
            if (p.x in 8f..(FW - 8f) && p.y in 8f..(FH - 8f)) grime.add(MDot(p, 0.5f + rnd.nextFloat() * 1.3f, (if (rnd.nextFloat() < 0.6f) Color(0xFFC9C2AE) else Color.Black).copy(alpha = 0.12f + rnd.nextFloat() * 0.28f)))
        }
        // paint worn off the edges, corners and the ends of the seams, with bare aluminium showing through
        repeat(110) {
            val side = rnd.nextInt(4)
            val t = if (rnd.nextFloat() < 0.5f) (if (rnd.nextBoolean()) rnd.nextFloat() * 0.09f else 1f - rnd.nextFloat() * 0.09f) else rnd.nextFloat()
            val inward = rnd.nextFloat() * 5f
            val cx = when (side) { 0 -> t * FW; 1 -> FW - inward; 2 -> t * FW; else -> inward }
            val cy = when (side) { 0 -> inward; 1 -> t * FH; 2 -> FH - inward; else -> t * FH }
            val r = 1.5f + rnd.nextFloat() * 4.5f
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
            chips.add(Fleck(p, 0.55f + rnd.nextFloat() * 0.4f))
        }
        // lettering worn away by thumbs: marks in the colour of the metal beneath, scattered over each printed area
        fun wearBand(u0: Float, u1: Float, v0: Float, v1: Float, n: Int) {
            repeat(n) {
                val u = u0 + rnd.nextFloat() * (u1 - u0)
                val v = v0 + rnd.nextFloat() * (v1 - v0)
                val base = lumColor(shadeAt(v))
                if (rnd.nextFloat() < 0.35f) {
                    val a = rnd.nextFloat() * PI.toFloat()
                    val len = 6f + rnd.nextFloat() * 20f
                    wornBand.add(MLine(Offset(u, v), Offset(u + cos(a) * len, v + sin(a) * len), base.copy(alpha = 0.7f + rnd.nextFloat() * 0.25f), 0.9f + rnd.nextFloat() * 1.2f))
                } else {
                    wornBandDots.add(MDot(Offset(u, v), 0.6f + rnd.nextFloat() * 1.6f, base.copy(alpha = 0.6f + rnd.nextFloat() * 0.35f)))
                }
            }
        }
        wearBand(-240f, 35f, -139f, -51f, 60)          // the kanji
        wearBand(-239f, 35f, -31f, -4f, 26)            // BOODO KHAN
        wearBand(-455f, -143f, 14f, 46f, 90)           // the two small lines
        wearBand(-133f, 37f, 12f, 45f, 24)             // the Dolby marks
        wearBand(204f, 412f, -278f, -259f, 26)         // FF and REW labels
        wearBand(111f, 169f, -162f, -148f, 12)         // PLAY label
        wearBand(178f, 240f, -1f, 15f, 14)             // STOP label
        wearBand(340f, 428f, -109f, -93f, 14)          // BATTERY
        repeat(60) { wornAbs.add(MDot(Offset(50f + rnd.nextFloat() * 261f, 37f + rnd.nextFloat() * 49f), 0.7f + rnd.nextFloat() * 1.8f, lumColor(shadeAt(-480f)).copy(alpha = 0.6f + rnd.nextFloat() * 0.35f))) }   // the wordmark
        repeat(30) { wornAbs.add(MDot(Offset(520f + rnd.nextFloat() * 75f, 1000f + rnd.nextFloat() * 24f), 0.7f + rnd.nextFloat() * 1.6f, Color(0xFF131311).copy(alpha = 0.7f + rnd.nextFloat() * 0.25f))) }   // DOL
    }

    fun draw(s: DrawScope, k: Float) = with(s) {
        scale(k, k, Offset.Zero) {
            clipPath(body) {
                drawBase()
                drawPrint()
                drawHousings()
                drawWear()
            }
            for (f in chips) {
                translate(0.8f, 0.8f) { drawPath(f.path, Color(0x66000000)) }
                drawPath(f.path, Color(0xFFC3C7CE).copy(alpha = f.alpha))
            }
        }
    }

    private fun DrawScope.drawBase() {
        val n = Offset(SINA, COSA)
        val centre = Offset(CX, CY)
        val stops = SHADE.map { (v, l) -> ((v + 660f) / 1320f) to lumColor(l) }.toTypedArray()
        drawRect(Brush.linearGradient(*stops, start = centre + n * -660f, end = centre + n * 660f), Offset.Zero, Size(FW, FH))
        band {
            for (st in streaks) drawLine(st.color, Offset(st.u0, st.v), Offset(st.u1, st.v), st.w)
            // the two diagonal seams: a dark groove with a bright edge
            drawLine(Color(0xAA000000), Offset(-900f, -324f), Offset(900f, -324f), 3f)
            drawLine(Color(0x38FFFFFF), Offset(-900f, -327f), Offset(900f, -327f), 1.5f)
            drawLine(Color(0xB3000000), Offset(-900f, 118f), Offset(900f, 118f), 3.5f)
            drawLine(Color(0x24FFFFFF), Offset(-900f, 124f), Offset(900f, 124f), 1.5f)
        }
        // side walls, top and bottom strips
        drawRect(Brush.horizontalGradient(listOf(lumColor(15f), lumColor(34f)), 0f, 26f), Offset.Zero, Size(26f, FH))
        drawRect(Brush.horizontalGradient(listOf(lumColor(41f), lumColor(11f)), 740f, FW), Offset(740f, 0f), Size(FW - 740f, FH))
        drawLine(Color(0x80000000), Offset(26f, 0f), Offset(26f, FH), 2f)
        drawLine(Color(0x1FFFFFFF), Offset(28f, 0f), Offset(28f, FH), 1.5f)
        drawLine(Color(0x80000000), Offset(740f, 0f), Offset(740f, FH), 2f)
        drawRect(Brush.verticalGradient(listOf(lumColor(140f), lumColor(82f)), 0f, 30f), Offset.Zero, Size(FW, 30f))
        drawLine(Color(0x8C000000), Offset(0f, 30f), Offset(FW, 30f), 2f)
        drawLine(Color(0x4DFFFFFF), Offset(0f, 1.5f), Offset(FW, 1.5f), 2f)
        drawRect(lumColor(12f), Offset(0f, 1044f), Size(FW, FH - 1044f))
        drawLine(Color(0x99000000), Offset(0f, 1044f), Offset(FW, 1044f), 2f)
        drawLine(Color(0x1AFFFFFF), Offset(0f, 1047f), Offset(FW, 1047f), 1.5f)
        drawPoints(grainLight, PointMode.Points, Color(0x14FFFFFF), 1.3f, StrokeCap.Round)
        drawPoints(grainDark, PointMode.Points, Color(0x1A000000), 1.3f, StrokeCap.Round)
    }

    /** All the printing. The Sony wordmark is the only thing that is not as the original has it: it reads VINA. */
    private fun DrawScope.drawPrint() {
        fitText("VINA", wordmark, 50f, 61.5f, 261f)
        band {
            fitText("武道館", kanji, -240f, -95f, 275f)
            fitText("BOODO KHAN", boodo, -239f, -17.5f, 274f)
            fitText("DISC DRIVE CAPSTAN SERVO", small, -430f, 22.5f, 287f)
            fitText("DYNAMIC OPTIMUM LOUDNESS", small, -455f, 38.8f, 312f)
            // Dolby double-D and the DOLBY NR box
            drawDolbyD(-133f, 16f, 21f, 29f, true)
            drawDolbyD(-109f, 16f, 21f, 29f, false)
            drawRoundRect(INK_WHITE, Offset(-84f, 12f), Size(121f, 33f), CornerRadius(2f), style = Stroke(2.2f))
            fitText("DOLBY NR", tiny, -73f, 28.8f, 100f)
            // control labels
            drawTriangle(204f, -268f, 19f, 16f, true); drawTriangle(222f, -268f, 19f, 16f, true)
            fitText("FF", label, 251f, -268f, 30f)
            fitText("REW", label, 308f, -268.5f, 54f)
            drawTriangle(373f, -268.5f, 19f, 16f, false); drawTriangle(392f, -268.5f, 19f, 16f, false)
            fitText("PLAY", label, 111.7f, -155f, 57f)
            fitText("STOP", label, 178f, 7f, 62f)
        }
        // the DOL switch's printing, in absolute pixels
        fitText("DOL", dol, 520f, 1012f, 75f)
        fitText("ON", onoff, 547f, 938f, 28f)
        fitText("OFF", onoff, 547f, 958f, 44f)
        drawLine(INK_WHITE, Offset(595f, 938f), Offset(626f, 938f), 1.6f)
        drawLine(INK_WHITE, Offset(595f, 958f), Offset(626f, 958f), 1.6f)
    }

    private fun DrawScope.drawTriangle(u: Float, v: Float, w: Float, h: Float, pointsLeft: Boolean) {
        val p = Path().apply {
            if (pointsLeft) { moveTo(u, v); lineTo(u + w, v - h / 2f); lineTo(u + w, v + h / 2f) } else { moveTo(u + w, v); lineTo(u, v - h / 2f); lineTo(u, v + h / 2f) }
            close()
        }
        drawPath(p, INK_WHITE)
    }

    /** One half of the Dolby double-D: a solid D with a black D cut out of it. [mirrored] is the left one. */
    private fun DrawScope.drawDolbyD(x: Float, y: Float, w: Float, h: Float, mirrored: Boolean) {
        fun dShape(ix: Float, iy: Float, iw: Float, ih: Float): Path = Path().apply {
            if (mirrored) {
                moveTo(ix + iw, iy); lineTo(ix + ih / 2f, iy); arcTo(Rect(ix, iy, ix + ih, iy + ih), -90f, -180f, false); lineTo(ix + iw, iy + ih)
            } else {
                moveTo(ix, iy); lineTo(ix + iw - ih / 2f, iy); arcTo(Rect(ix + iw - ih, iy, ix + iw, iy + ih), -90f, 180f, false); lineTo(ix, iy + ih)
            }
            close()
        }
        drawPath(dShape(x, y, w, h), INK_WHITE)
        drawPath(dShape(x + 3.2f, y + 3.2f, w - 6.4f, h - 6.4f), Color(0xFF121212))
    }

    /** Recesses and fixed parts of the controls: the gaskets round the keys, the PLAY slot, the battery window, the LED housing, the DOL slot. */
    private fun DrawScope.drawHousings() {
        for ((uv, r, cap) in listOf(Triple(REW_UV, 46f, 26f), Triple(FF_UV, 46f, 26f), Triple(STOP_UV, 59f, 36f))) {
            val c = ref(uv.x, uv.y)
            drawCircle(Color(0x33FFFFFF), r + 3.5f, c, style = Stroke(2.2f))                     // the faint bright edge outside the gasket
            drawCircle(Color(0xFF050505), r, c)                                                  // the black outline
            drawCircle(Brush.radialGradient(listOf(Color(0xFF6C6D69), Color(0xFF4A4B48)), c, r), r - 7f, c)    // the grey floor of the recess
            drawCircle(Color(0x66000000), r - 7f, c, style = Stroke(2f))
            drawCircle(Color(0xFF0B0B0A), cap + 4.5f, c)                                         // the shadow round the cap
        }
        band {
            // PLAY slot: the green ring is rounded at the left end and nearly square at the right
            drawPath(pill(87f, -139f, 275f, -54f, 42f, 12f), GREEN_RING)
            drawPath(pill(87f, -139f, 275f, -54f, 42f, 12f), Color(0x59FFFFFF), style = Stroke(1.5f))
            drawPath(pill(99f, -127f, 263f, -66f, 30f, 3f), Color(0xFF030303))
            // battery bar, its dark end block, and the LED's housing
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF626260), Color(0xFF2E2E2D)), -118f, -84f), Offset(277f, -118f), Size(180f, 34f), CornerRadius(5f))
            drawRoundRect(Color(0x55000000), Offset(277f, -118f), Size(180f, 34f), CornerRadius(5f), style = Stroke(1.6f))
            drawLine(Color(0x40FFFFFF), Offset(281f, -116.5f), Offset(453f, -116.5f), 1.4f)
            drawRoundRect(Color(0xFF151515), Offset(446f, -118f), Size(24f, 34f), CornerRadius(4f))
            drawCircle(Color(0xFF060606), 15.5f, Offset(LED_UV.x, LED_UV.y))
            drawCircle(Color(0x59FFFFFF), 15.5f, Offset(LED_UV.x, LED_UV.y), style = Stroke(1.4f))
            fitText("BATTERY", battery, 340f, -101f, 88f)
        }
        // DOL slot
        drawRoundRect(Color(0xFF040404), Offset(608f, 904f), Size(140f, 146f), CornerRadius(6f))
        drawRoundRect(Color(0x2EFFFFFF), Offset(608f, 904f), Size(140f, 146f), CornerRadius(6f), style = Stroke(1.5f))
    }

    private fun DrawScope.drawWear() {
        for (r in rubs) drawOval(Brush.radialGradient(listOf(r.color, Color.Transparent), Offset(r.topLeft.x + r.size.width / 2f, r.topLeft.y + r.size.height / 2f), r.size.width / 2f), r.topLeft, r.size)
        for (l in scratches) drawLine(l.color, l.a, l.b, l.w, StrokeCap.Round)
        for (d in grime) drawCircle(d.color, d.r, d.c)
        band {
            for (l in wornBand) drawLine(l.color, l.a, l.b, l.w, StrokeCap.Round)
            for (d in wornBandDots) drawCircle(d.color, d.r, d.c)
        }
        for (d in wornAbs) drawCircle(d.color, d.r, d.c)
    }
}

/** A rounded slot, round at the left end and nearly square at the right, in the band frame. */
private fun pill(l: Float, t: Float, r: Float, b: Float, left: Float, right: Float): Path =
    Path().apply { addRoundRect(RoundRect(l, t, r, b, CornerRadius(left), CornerRadius(right), CornerRadius(right), CornerRadius(left))) }

/** A round silver key as seen from above: dished cap, bright upper-left edge, and a little scuffing. [press] is 0 to 1. */
private fun DrawScope.roundCap(c: Offset, r: Float, press: Float, mark: Boolean, seed: Int) {
    val rr = r * (1f - 0.06f * press)
    // the dished ring between the cap and the gasket, with a bright lower lip
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1B1B1A), Color(0xFF070707)), c, r + 5f), r + 4f, c)
    drawArc(Color(0x66FFFFFF), 20f, 140f, false, Offset(c.x - r - 2f, c.y - r - 2f), Size(2f * r + 4f, 2f * r + 4f), style = Stroke(1.6f, cap = StrokeCap.Round))
    drawCircle(
        Brush.radialGradient(listOf(SILVER_TOP, SILVER_MID, SILVER_LOW, Color(0xFF6B6C68)), Offset(c.x - rr * 0.22f, c.y - rr * 0.28f), rr * 1.3f),
        rr, c,
    )
    drawArc(Color(0x99FFFFFF), 195f, 80f, false, Offset(c.x - rr * 0.82f, c.y - rr * 0.82f), Size(rr * 1.64f, rr * 1.64f), style = Stroke(rr * 0.09f, cap = StrokeCap.Round))
    if (mark) drawRoundRect(Color(0x886C6C68), Offset(c.x - 11f, c.y - 11f), Size(22f, 22f), CornerRadius(4f), style = Stroke(2.4f))
    // finger oil has dulled the centre a little; years of use have scratched the rest
    drawOval(Brush.radialGradient(listOf(Color(0x22000000), Color.Transparent), c, rr * 0.6f), Offset(c.x - rr * 0.6f, c.y - rr * 0.5f), Size(rr * 1.2f, rr))
    val rnd = Random(seed)
    repeat(9) {
        val a = rnd.nextFloat() * 6.283f
        val d = rnd.nextFloat() * rr * 0.8f
        val x = c.x + cos(a) * d
        val y = c.y + sin(a) * d
        val ang = rnd.nextFloat() * 3.1416f
        val len = 3f + rnd.nextFloat() * 11f
        drawLine((if (rnd.nextBoolean()) Color.White else Color.Black).copy(alpha = 0.10f + rnd.nextFloat() * 0.2f), Offset(x, y), Offset(x + cos(ang) * len, y + sin(ang) * len), 0.7f + rnd.nextFloat() * 0.8f)
    }
    if (press > 0f) drawCircle(Color.Black.copy(alpha = 0.22f * press), rr, c)
}

/** The PLAY slider: a pale silver cap in the green ring, with a green triangle pointing left. It sinks and darkens while the tape runs. */
private fun DrawScope.playCap(press: Float) {
    val sink = 1.5f * press
    band {
        val cap = pill(103f + sink, -121f + sink, 259f - sink, -70f - sink, 24f, 3f)
        drawPath(cap, Brush.verticalGradient(listOf(Color(0xFFE6ECE8), Color(0xFFD2D5D0), Color(0xFFB9BAB5)), -121f, -70f))
        drawPath(cap, Brush.horizontalGradient(listOf(Color(0x40000000), Color.Transparent), 103f, 150f))      // the tapered left end
        drawPath(cap, Color(0x66000000), style = Stroke(1.2f))
        drawRoundRect(Color(0x66FFFFFF), Offset(150f, -117f), Size(100f, 3f), CornerRadius(1.5f))
        val tri = Path().apply { moveTo(167f, -94f); lineTo(197f, -104f); lineTo(197f, -84f); close() }
        drawPath(tri, GREEN_RING)
        val rnd = Random(77)
        repeat(12) {
            val x = 112f + rnd.nextFloat() * 140f
            val y = -116f + rnd.nextFloat() * 40f
            val len = 4f + rnd.nextFloat() * 16f
            drawLine((if (rnd.nextBoolean()) Color.White else Color.Black).copy(alpha = 0.10f + rnd.nextFloat() * 0.2f), Offset(x, y), Offset(x + len, y + (rnd.nextFloat() - 0.5f) * 4f), 0.8f)
        }
        if (press > 0f) drawPath(cap, Color.Black.copy(alpha = 0.2f * press))
    }
}

/** The DOL slide switch: a ribbed silver block; the dark slit near its top lines up with ON when the loudness boost is on. */
private fun DrawScope.dolCap(on: Float) {
    val top = 925f - 10f * on
    drawRoundRect(Brush.verticalGradient(listOf(SILVER_TOP, SILVER_MID, SILVER_LOW), top, top + 112f), Offset(613f, top), Size(129f, 112f), CornerRadius(5f))
    drawRoundRect(Color(0x66000000), Offset(613f, top), Size(129f, 112f), CornerRadius(5f), style = Stroke(1.5f))
    drawRoundRect(Color(0xFF3D3D3A), Offset(626f, top + 9f), Size(44f, 6f), CornerRadius(2f))
    for (i in 0..7) {
        val y = top + 44f + i * 8f
        drawLine(Color(0x40000000), Offset(619f, y), Offset(736f, y), 2f)
        drawLine(Color(0x99FFFFFF), Offset(619f, y + 2.5f), Offset(736f, y + 2.5f), 1.4f)
    }
    val rnd = Random(91)
    repeat(14) {
        val x = 618f + rnd.nextFloat() * 110f
        val y = top + 6f + rnd.nextFloat() * 100f
        drawLine((if (rnd.nextBoolean()) Color.White else Color.Black).copy(alpha = 0.10f + rnd.nextFloat() * 0.2f), Offset(x, y), Offset(x + 4f + rnd.nextFloat() * 14f, y + (rnd.nextFloat() - 0.5f) * 3f), 0.8f)
    }
}

private fun DrawScope.ledLens(level: Float) {
    val c = ref(LED_UV.x, LED_UV.y)
    if (level > 0.02f) drawCircle(Brush.radialGradient(listOf(Color(0x80FF3B30), Color.Transparent), c, 34f), 34f, c, alpha = level)
    drawCircle(lerp(Color(0xFF5A1412), Color(0xFFFF5A44), level), 10f, c)
    drawCircle(Color(0x80FFFFFF), 2.4f, Offset(c.x - 3f, c.y - 3.2f))
}

/** Which control a touch at reference pixel ([x], [y]) is on: 0 REW, 1 FF, 2 STOP, 3 PLAY, 4 DOL, or -1. */
private fun hitTest(x: Float, y: Float): Int {
    fun inCircle(uv: Offset, r: Float): Boolean { val c = ref(uv.x, uv.y); return (x - c.x) * (x - c.x) + (y - c.y) * (y - c.y) <= r * r }
    val b = toBand(x, y)
    return when {
        inCircle(REW_UV, 54f) -> 0
        inCircle(FF_UV, 54f) -> 1
        inCircle(STOP_UV, 66f) -> 2
        kotlin.math.abs(b.x - 181f) <= 104f && kotlin.math.abs(b.y + 96.5f) <= 50f -> 3
        x in 600f..760f && y in 895f..1060f -> 4
        else -> -1
    }
}

@Composable
private fun FrontFace(
    st: SkinState, act: SkinActions, m: Motion, playing: Boolean, dolOn: Boolean, onDol: () -> Unit, width: Dp,
) {
    val height = width * (FH / FW)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val stNow by rememberUpdatedState(st)
    val actNow by rememberUpdatedState(act)
    val onDolNow by rememberUpdatedState(onDol)
    var down by remember { mutableIntStateOf(-1) }
    val keySpring = spring<Float>(dampingRatio = 0.6f, stiffness = 900f)
    val rew by animateFloatAsState(if (down == 0) 1f else 0f, keySpring, label = "rew")
    val ff by animateFloatAsState(if (down == 1) 1f else 0f, keySpring, label = "ff")
    val stop by animateFloatAsState(if (down == 2) 1f else 0f, keySpring, label = "stop")
    val play by animateFloatAsState(if (playing || down == 3) 1f else 0f, keySpring, label = "play")
    val dol by animateFloatAsState(if (dolOn) 1f else 0f, keySpring, label = "dol")
    val led by animateFloatAsState(if (playing) 1f else 0f, tween(450), label = "led")
    val flick by rememberInfiniteTransition(label = "flick").animateFloat(
        0.9f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "flickValue",
    )

    Box(
        Modifier.size(width, height).unitShadow()
            .drawWithCache {
                val art = FrontArt()
                onDrawWithContent {
                    art.draw(this, size.width / FW)
                    drawContent()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { o ->
                        val k = size.width / FW
                        val id = hitTest(o.x / k, o.y / k)
                        if (id >= 0) {
                            down = id
                            var held = false
                            val job = if (id == 0 || id == 1) scope.launch {
                                delay(350)
                                held = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val dir = if (id == 0) -1 else 1
                                while (true) {
                                    actNow.scrub(stNow, dir)
                                    m.scrubDir = dir
                                    m.scrubAt = System.currentTimeMillis()
                                    delay(200)
                                }
                            } else null
                            val released = tryAwaitRelease()
                            job?.cancel()
                            down = -1
                            if (released) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (id) {
                                    0 -> { if (!held) actNow.onPrev() }
                                    1 -> { if (!held) actNow.onNext() }
                                    2 -> { actNow.stop(stNow) }
                                    3 -> { actNow.onToggle() }
                                    else -> { onDolNow() }
                                }
                            }
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / FW
            scale(k, k, Offset.Zero) {
                val rc = ref(REW_UV.x, REW_UV.y)
                val fc = ref(FF_UV.x, FF_UV.y)
                val sc = ref(STOP_UV.x, STOP_UV.y)
                roundCap(rc, 26f, rew, false, 11)
                roundCap(fc, 26f, ff, false, 12)
                roundCap(sc, 36f, stop, true, 13)
                playCap(play)
                dolCap(dol)
                ledLens(led * flick)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------
//  The back: the cassette, lying horizontally
// ------------------------------------------------------------------------------------------------------

/**
 * The cassette-door side, turned a quarter turn so the cassette lies horizontally and the lettering reads normally.
 * Reference size 990 x 677. The window shows a cassette whose cream reels are cut off by the window edge, as on the real
 * unit; its label strip carries the title where the cassette's own printing would be.
 */
private class BackArt {
    private val scratches = ArrayList<MLine>()
    private val grime = ArrayList<MDot>()
    private val chips = ArrayList<Fleck>()
    private val wornText = ArrayList<MDot>()
    private val body: Path = Path().apply { addRoundRect(RoundRect(0f, 0f, BACK_W, BACK_H, 20f, 20f)) }
    private val wordmark = paintOf(INK_WHITE, 56f, SERIF_BOLD)
    private val model = paintOf(INK_WHITE, 25f, SANS_MEDIUM)

    init {
        val rnd = Random(40)
        repeat(300) {
            val x = rnd.nextFloat() * BACK_W
            val y = rnd.nextFloat() * BACK_H
            val a = if (rnd.nextFloat() < 0.7f) (rnd.nextFloat() - 0.5f) * 0.6f else rnd.nextFloat() * PI.toFloat()
            val len = 8f + rnd.nextFloat() * 44f
            scratches.add(MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), (if (rnd.nextFloat() < 0.75f) Color(0xFFD5D8DE) else Color.Black).copy(alpha = 0.06f + rnd.nextFloat() * 0.18f), 0.6f + rnd.nextFloat() * 0.8f))
        }
        repeat(220) {
            val onRim = rnd.nextFloat() < 0.6f
            val p = if (onRim) { val t = rnd.nextFloat() * 2f * (680f + 260f); rimPoint(t, rnd) } else Offset(rnd.nextFloat() * BACK_W, rnd.nextFloat() * BACK_H)
            grime.add(MDot(p, 0.6f + rnd.nextFloat() * 1.4f, (if (rnd.nextFloat() < 0.6f) Color(0xFFC9C2AE) else Color.Black).copy(alpha = 0.12f + rnd.nextFloat() * 0.28f)))
        }
        repeat(90) {
            val side = rnd.nextInt(4)
            val t = if (rnd.nextFloat() < 0.5f) (if (rnd.nextBoolean()) rnd.nextFloat() * 0.08f else 1f - rnd.nextFloat() * 0.08f) else rnd.nextFloat()
            val inward = rnd.nextFloat() * 6f
            val cx = when (side) { 0 -> t * BACK_W; 1 -> BACK_W - inward; 2 -> t * BACK_W; else -> inward }
            val cy = when (side) { 0 -> inward; 1 -> t * BACK_H; 2 -> BACK_H - inward; else -> t * BACK_H }
            val r = 2f + rnd.nextFloat() * 5f
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
            chips.add(Fleck(p, 0.55f + rnd.nextFloat() * 0.4f))
        }
        repeat(50) { wornText.add(MDot(Offset(395f + rnd.nextFloat() * 180f, 575f + rnd.nextFloat() * 50f), 0.7f + rnd.nextFloat() * 1.8f, Color(0xFF151515).copy(alpha = 0.6f + rnd.nextFloat() * 0.35f))) }
        repeat(60) { wornText.add(MDot(Offset(285f + rnd.nextFloat() * 400f, 538f + rnd.nextFloat() * 24f), 0.7f + rnd.nextFloat() * 1.5f, Color(0xFF151515).copy(alpha = 0.6f + rnd.nextFloat() * 0.35f))) }
        repeat(40) { wornText.add(MDot(Offset(415f + rnd.nextFloat() * 160f, 400f + rnd.nextFloat() * 74f), 0.7f + rnd.nextFloat() * 1.6f, Color(0xFF151515).copy(alpha = 0.5f + rnd.nextFloat() * 0.3f))) }
    }

    private fun rimPoint(t0: Float, rnd: Random): Offset {
        // a point just outside the window rectangle (160..840 x 100..360), walking round its perimeter
        val ww = 680f
        val wh = 260f
        val t = t0 % (2f * (ww + wh))
        val out = rnd.nextFloat() * 7f
        return when {
            t < ww -> Offset(160f + t, 100f - out)
            t < ww + wh -> Offset(840f + out, 100f + (t - ww))
            t < 2f * ww + wh -> Offset(840f - (t - ww - wh), 360f + out)
            else -> Offset(160f - out, 360f - (t - 2f * ww - wh))
        }
    }

    fun draw(s: DrawScope, k: Float) = with(s) {
        scale(k, k, Offset.Zero) {
            clipPath(body) {
                drawRect(Brush.verticalGradient(listOf(lumColor(40f), lumColor(22f), lumColor(15f)), 0f, BACK_H), Offset.Zero, Size(BACK_W, BACK_H))
                var y = 0f
                var i = 0
                while (y < BACK_H) { drawLine(if (i % 2 == 0) Color(0x0AFFFFFF) else Color(0x0A000000), Offset(0f, y), Offset(BACK_W, y), 1f); y += 2.2f; i++ }
                drawRect(Brush.verticalGradient(listOf(lumColor(125f), lumColor(62f)), 0f, 20f), Offset.Zero, Size(BACK_W, 20f))      // the thick edge of the case, seen from behind
                drawLine(Color(0x99000000), Offset(0f, 20f), Offset(BACK_W, 20f), 2f)
                drawRect(Brush.radialGradient(listOf(Color(0x1CFFFFFF), Color.Transparent), Offset(BACK_W * 0.35f, BACK_H * 0.8f), BACK_W * 0.6f), Offset.Zero, Size(BACK_W, BACK_H))
                // the window: a recess with a clear plastic rim
                drawRoundRect(Color(0xFF020203), Offset(150f, 90f), Size(700f, 280f), CornerRadius(48f))
                drawRoundRect(Color(0x55FFFFFF), Offset(150f, 90f), Size(700f, 280f), CornerRadius(48f), style = Stroke(2.5f))
                // lettering and the insertion arrow
                fitText("VINA", wordmark, 395f, 600f, 180f)
                fitText("STEREO CASSETTE PLAYER DD-100", model, 285f, 550f, 400f)
                val arrow = Path().apply {
                    moveTo(415f, 423f); lineTo(515f, 423f); lineTo(515f, 402f); lineTo(575f, 437f); lineTo(515f, 472f); lineTo(515f, 451f); lineTo(415f, 451f); close()
                }
                drawPath(arrow, INK_WHITE.copy(alpha = 0.92f), style = Stroke(5f, join = StrokeJoin.Round))
                drawCircle(Color(0x66FFFFFF), 8f, Offset(420f, 645f), style = Stroke(2.5f))
                for (l in scratches) drawLine(l.color, l.a, l.b, l.w, StrokeCap.Round)
                for (d in grime) drawCircle(d.color, d.r, d.c)
                for (d in wornText) drawCircle(d.color, d.r, d.c)
            }
            for (f in chips) {
                translate(0.8f, 0.8f) { drawPath(f.path, Color(0x66000000)) }
                drawPath(f.path, Color(0xFFC3C7CE).copy(alpha = f.alpha))
            }
        }
    }
}

/** The reels: cream flanges with ten holes round the edge, a toothed hub, a spindle. Whether a hole shows tape depends on how full the reel is. */
private fun DrawScope.reel(c: Offset, rot: Float, pack: Float) {
    drawCircle(Color(0xFF14110F), 96f, c)
    drawCircle(Color(0xFFE9DDBE), 90f, c)
    drawCircle(Color(0x33000000), 90f, c, style = Stroke(3f))
    rotate(rot, c) {
        for (i in 0 until 10) {
            val a = i * (2f * PI.toFloat() / 10f)
            val hc = Offset(c.x + cos(a) * 68f, c.y + sin(a) * 68f)
            drawCircle(if (pack > 68f) Color(0xFF4A3524) else Color(0xFF16130F), 11f, hc)       // brown where tape is behind the hole
        }
        drawCircle(Color(0xFFD2C7A7), 44f, c)
        drawCircle(Color(0xFF1B1916), 30f, c)
        for (i in 0 until 6) {
            val a = i * (PI.toFloat() / 3f)
            drawLine(Color(0xFFE9DDBE), Offset(c.x + cos(a) * 24f, c.y + sin(a) * 24f), Offset(c.x + cos(a) * 36f, c.y + sin(a) * 36f), 8f, StrokeCap.Round)
        }
        drawCircle(Color(0xFF7A7C80), 13f, c)
        drawLine(Color(0xFF2B2C2F), Offset(c.x - 9f, c.y), Offset(c.x + 9f, c.y), 3f)
        drawLine(Color(0xFF2B2C2F), Offset(c.x, c.y - 9f), Offset(c.x, c.y + 9f), 3f)
    }
}

@Composable
private fun BackPanel(
    st: SkinState, m: Motion, loaded: Boolean, spinning: Boolean, shown: () -> Float, insert: () -> Float, act: SkinActions, width: Dp,
) {
    val height = width * (BACK_H / BACK_W)
    val titlePaint = remember { paintOf(Color(0xFFEDE9DD), 28f, SANS_MEDIUM) }
    val subPaint = remember { paintOf(Color(0xFFB9B4A6), 20f, SANS_REG) }
    Box(
        Modifier.size(width, height).unitShadow()
            .drawWithCache {
                val art = BackArt()
                onDrawWithContent {
                    art.draw(this, size.width / BACK_W)
                    drawContent()
                }
            }
            .pointerInput(Unit) {
                // turning the tape by hand: drag along the window to seek
                detectHorizontalDragGestures(
                    onDragStart = { m.dragFrac = shown() },
                    onDragEnd = { m.dragFrac?.let { f -> if (st.dur.longValue > 0) act.onSeekTo((f * st.dur.longValue).toLong()) }; m.dragFrac = null },
                    onDragCancel = { m.dragFrac = null },
                ) { change, dx ->
                    m.dragFrac = ((m.dragFrac ?: 0f) + dx / size.width * 1.6f).coerceIn(0f, 1f)
                    m.scrubDir = if (dx >= 0) 1 else -1
                    m.scrubAt = System.currentTimeMillis()
                    change.consume()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / BACK_W
            scale(k, k, Offset.Zero) {
                val win = Path().apply { addRoundRect(RoundRect(160f, 100f, 840f, 360f, 40f, 40f)) }
                clipPath(win) {
                    drawRect(Color(0xFF0B0B0C), Offset(160f, 100f), Size(680f, 260f))
                    // the cassette slides in from the left each time a song starts
                    translate(left = -(1f - insert()) * 520f) {
                        if (loaded) {
                            val p = shown()
                            drawRect(Color(0xFF1B1A19), Offset(160f, 100f), Size(680f, 260f))
                            drawRect(Color(0xFF2A2724), Offset(160f, 100f), Size(680f, 100f))                      // the label strip
                            for (i in 0..9) drawLine(Color(0x40FFFFFF), Offset(402f + i * 20f, 218f), Offset(402f + i * 20f, 348f), 1.4f)   // the ruled lines between the reels
                            drawRect(Color(0xFFE29A1E), Offset(288f, 156f), Size(15f, 34f))                           // the orange tab
                            val leftPack = 48f + 36f * (1f - p)
                            val rightPack = 48f + 36f * p
                            reel(Offset(300f, 295f), m.rotL.floatValue, leftPack)
                            reel(Offset(680f, 295f), m.rotR.floatValue, rightPack)
                            val nc = drawContext.canvas.nativeCanvas
                            var title = st.ps.title
                            while (title.length > 3 && titlePaint.measureText(title) > 400f) title = title.dropLast(2)
                            if (title != st.ps.title) title = title.trimEnd() + "…"
                            nc.drawText(title, 270f, 150f, titlePaint)
                            nc.drawText(st.ps.artist.take(46), 270f, 182f, subPaint)
                        }
                    }
                    // clear plastic: a little yellowed, hazy, with its own scratches
                    drawRect(Color(0x1CE4C870), Offset(160f, 100f), Size(680f, 260f))
                    drawRect(Brush.linearGradient(listOf(Color(0x30FFFFFF), Color.Transparent, Color(0x10FFFFFF)), Offset(160f, 100f), Offset(840f, 360f)), Offset(160f, 100f), Size(680f, 260f))
                    val rnd = Random(55)
                    repeat(16) {
                        val x = 170f + rnd.nextFloat() * 660f
                        val y = 108f + rnd.nextFloat() * 245f
                        val a = (rnd.nextFloat() - 0.5f) * 1.2f
                        val len = 10f + rnd.nextFloat() * 60f
                        drawLine(Color.White.copy(alpha = 0.10f + rnd.nextFloat() * 0.25f), Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), 0.8f + rnd.nextFloat())
                    }
                    repeat(90) { drawCircle(Color(0xFFC9C2AE).copy(alpha = 0.08f + rnd.nextFloat() * 0.2f), 0.6f + rnd.nextFloat() * 1.3f, Offset(165f + rnd.nextFloat() * 670f, 105f + rnd.nextFloat() * 250f)) }
                    drawRoundRect(Color(0x66000000), Offset(160f, 100f), Size(680f, 260f), CornerRadius(40f), style = Stroke(12f))        // dirt in the rim
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------
//  The top edge: headphone jacks and the volume wheel
// ------------------------------------------------------------------------------------------------------

private class StripArt {
    private val scratches = ArrayList<MLine>()
    private val grime = ArrayList<MDot>()
    private val body: Path = Path().apply { addRoundRect(RoundRect(0f, 0f, STRIP_W, STRIP_H, 24f, 24f)) }
    private val text = paintOf(INK_WHITE, 25f, SANS_BOLD)
    private val big = paintOf(Color(0x66FFFFFF), 120f, SANS_BOLD)

    init {
        val rnd = Random(12)
        repeat(160) {
            val x = rnd.nextFloat() * STRIP_W
            val y = rnd.nextFloat() * STRIP_H
            val a = (rnd.nextFloat() - 0.5f) * 0.5f
            val len = 10f + rnd.nextFloat() * 50f
            scratches.add(MLine(Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), (if (rnd.nextFloat() < 0.7f) Color(0xFFD5D8DE) else Color.Black).copy(alpha = 0.06f + rnd.nextFloat() * 0.18f), 0.6f + rnd.nextFloat() * 0.8f))
        }
        repeat(140) { grime.add(MDot(Offset(rnd.nextFloat() * STRIP_W, rnd.nextFloat() * STRIP_H), 0.6f + rnd.nextFloat() * 1.3f, (if (rnd.nextFloat() < 0.6f) Color(0xFFC9C2AE) else Color.Black).copy(alpha = 0.12f + rnd.nextFloat() * 0.26f))) }
    }

    fun draw(s: DrawScope, k: Float) = with(s) {
        scale(k, k, Offset.Zero) {
            clipPath(body) {
                drawRect(Brush.verticalGradient(listOf(lumColor(82f), lumColor(40f), lumColor(22f)), 0f, STRIP_H), Offset.Zero, Size(STRIP_W, STRIP_H))
                var y = 0f
                var i = 0
                while (y < 240f) { drawLine(if (i % 2 == 0) Color(0x14FFFFFF) else Color(0x14000000), Offset(0f, y), Offset(STRIP_W, y), 1.4f); y += 3f; i++ }
                drawLine(Color(0x99000000), Offset(0f, 245f), Offset(STRIP_W, 245f), 2.5f)
                drawLine(Color(0x33FFFFFF), Offset(0f, 249f), Offset(STRIP_W, 249f), 1.5f)
                // the lower plate with the embossed STEREO and the dark recess
                fitText("STEREO.", big, 40f, 305f, 330f)
                drawRoundRect(Color(0xCC000000), Offset(640f, 268f), Size(300f, 62f), CornerRadius(10f))
                // headphone jacks A and B
                for ((cx, name) in listOf(240f to "A", 400f to "B")) {
                    val c = Offset(cx, 135f)
                    drawCircle(Color(0xFF050505), 40f, c)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFF1F1EE), Color(0xFF9C9C98), Color(0xFF4E4E4A)), c, 38f), 34f, c)
                    drawCircle(Color(0xFF9FCDB8), 34f, c, style = Stroke(3f))
                    drawCircle(Color(0xFF0A0A0A), 19f, c)
                    drawCircle(Color(0xFF8A8A86), 7f, c)
                    fitText(name, text, cx, 205f, null, center = true)
                }
                fitText("HEADPHONES", text, 320f, 232f, 230f, center = true)
                // the wedge that shows the volume rising, and the word VOLUME
                val wedge = Path().apply { moveTo(650f, 66f); lineTo(930f, 66f); lineTo(930f, 32f); close() }
                drawPath(wedge, INK_WHITE.copy(alpha = 0.9f), style = Stroke(3f, join = StrokeJoin.Round))
                fitText("VOLUME", text, 790f, 232f, 150f, center = true)
                for (l in scratches) drawLine(l.color, l.a, l.b, l.w, StrokeCap.Round)
                for (d in grime) drawCircle(d.color, d.r, d.c)
            }
        }
    }
}

@Composable
private fun TopStrip(volume: VolumeControl, width: Dp) {
    val height = width * (STRIP_H / STRIP_W)
    Box(
        Modifier.size(width, height).unitShadow()
            .drawWithCache {
                val art = StripArt()
                onDrawWithContent {
                    art.draw(this, size.width / STRIP_W)
                    drawContent()
                }
            }
            .pointerInput(Unit) {
                // the wheel: drag along it to turn it, which sets the phone's media volume
                detectHorizontalDragGestures { change, dx ->
                    volume.set((volume.level.value + dx / size.width * 2.2f).coerceIn(0f, 1f))
                    change.consume()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / STRIP_W
            scale(k, k, Offset.Zero) {
                // the ribbed wheel in its recess
                drawRoundRect(Color(0xFF040404), Offset(640f, 78f), Size(300f, 136f), CornerRadius(12f))
                clipRect(646f, 84f, 934f, 208f) {
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF1A1A18), Color(0xFF6B6B66), Color(0xFF9A9A94), Color(0xFF6B6B66), Color(0xFF1A1A18)), 646f, 934f), Offset(646f, 84f), Size(288f, 124f))
                    val spacing = 13f
                    val shift = (volume.level.value * 520f) % spacing
                    var x = 646f - spacing + shift
                    while (x < 934f) {
                        drawLine(Color(0x99000000), Offset(x, 90f), Offset(x, 204f), 3f)
                        drawLine(Color(0x44FFFFFF), Offset(x + 3.5f, 90f), Offset(x + 3.5f, 204f), 1.5f)
                        x += spacing
                    }
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x2EB0B4BA), Color.Transparent), 700f, 880f), Offset(646f, 84f), Size(288f, 124f))
                }
                drawRoundRect(Color(0x33FFFFFF), Offset(640f, 78f), Size(300f, 136f), CornerRadius(12f), style = Stroke(2f))
                // a marker showing the current level along the wedge
                val mx = 650f + volume.level.value * 280f
                drawLine(Color(0xCCE2B01E), Offset(mx, 28f), Offset(mx, 74f), 4f, StrokeCap.Round)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------
//  The row of Vina's own controls, printed like the rest of the lettering
// ------------------------------------------------------------------------------------------------------

@Composable
private fun ActionRow(st: SkinState, act: SkinActions, width: Dp) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.width(width).height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(
            mmss(st.pos.longValue) + " / " + mmss(st.dur.longValue),
            style = TextStyle(color = Color(0xFFD9D4C6), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        )
        for ((name, action) in listOf("EQ" to act.onEq, "LYRICS" to act.onLyrics, "QUEUE" to act.onQueue, "MENU" to act.onMenu, "EJECT" to act.onClose)) {
            Box(
                Modifier.height(34.dp).pointerInput(name) {
                    detectTapGestures(onTap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); action() })
                }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(name, style = TextStyle(color = Color(0xFFE9E5D8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
            }
        }
    }
}
