package org.melodyne.app.ui.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.audio.EQ_BANDS
import org.melodyne.app.audio.EQ_LABELS
import org.melodyne.app.audio.EQ_MAX_DB
import org.melodyne.app.audio.EqMath
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.skins.rememberLevels
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val CURVE_FREQS = List(97) { 20.0 * 1000.0.pow(it / 96.0) }          // 20 Hz to 20 kHz, evenly spaced on a log axis

/** The plotting area inside the canvas, with the maps between frequency/gain and pixels. */
private class Plot(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    fun x(f: Double): Float = l + w * ((log10(f) - log10(20.0)) / 3.0).toFloat()
    fun y(db: Float): Float = t + h / 2f - db / EQ_MAX_DB * h / 2f
}

private fun plotOf(w: Float, h: Float, d: Density): Plot = with(d) { Plot(30.dp.toPx(), 10.dp.toPx(), w - 12.dp.toPx(), h - 22.dp.toPx()) }

private fun nearestBand(x: Float, p: Plot): Int = EQ_BANDS.indices.minByOrNull { abs(p.x(EQ_BANDS[it].toDouble()) - x) } ?: 0

private fun dbAt(y: Float, p: Plot): Float {
    val raw = (p.y(0f) - y) / (p.h / 2f) * EQ_MAX_DB
    return (raw.coerceIn(-EQ_MAX_DB, EQ_MAX_DB) * 2f).roundToInt() / 2f
}

/**
 * The equaliser as a drawn frequency-response curve over the live spectrum. Drag any point up or down; the curve is the
 * true combined response of the filters, and it passes through the points you place.
 */
@Composable
fun EqGraph(gains: List<Float>, enabled: Boolean, playing: Boolean, onBand: (Int, Float) -> Unit, modifier: Modifier = Modifier) {
    val accent = if (enabled) LocalAccent.current else Amoled.Muted
    val levels = rememberLevels(playing)
    val curve = remember(gains) { EqMath.curve(gains, CURVE_FREQS) }
    var active by remember { mutableIntStateOf(-1) }
    val cb by rememberUpdatedState(onBand)
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE } }

    Canvas(
        modifier.fillMaxWidth().height(220.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val p = plotOf(size.width.toFloat(), size.height.toFloat(), this)
                    cb(nearestBand(o.x, p), dbAt(o.y, p))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val p = plotOf(size.width.toFloat(), size.height.toFloat(), this)
                        active = nearestBand(o.x, p)
                        cb(active, dbAt(o.y, p))
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                ) { change, _ ->
                    if (active >= 0) cb(active, dbAt(change.position.y, plotOf(size.width.toFloat(), size.height.toFloat(), this)))
                    change.consume()
                }
            },
    ) {
        val p = plotOf(size.width, size.height, this)
        drawRoundRect(Color(0xFF0E0E10), Offset.Zero, size, CornerRadius(14.dp.toPx()))

        // grid and dB labels
        paint.textSize = 9.sp.toPx()
        paint.color = 0xFF8C8C92.toInt()
        for (db in listOf(-12, -6, 0, 6, 12)) {
            val y = p.y(db.toFloat())
            drawLine(if (db == 0) Color(0xFF4A4A50) else Color(0xFF26262A), Offset(p.l, y), Offset(p.r, y), 1.dp.toPx())
            val label = if (db > 0) "+$db" else "$db"
            drawContext.canvas.nativeCanvas.drawText(label, 2.dp.toPx(), y + 3.dp.toPx(), paint)
        }
        for (f in listOf(100.0, 1000.0, 10000.0)) drawLine(Color(0xFF1C1C20), Offset(p.x(f), p.t), Offset(p.x(f), p.b), 1.dp.toPx())

        // the live spectrum, faintly, behind the curve
        if (playing) {
            val f = levels.value
            val x0 = p.x(31.0)
            val bw = (p.x(16000.0) - x0) / 16f
            for (i in 0 until 16) {
                val bh = p.h * 0.5f * f.bands[i].coerceIn(0f, 1f)
                drawRect(accent.copy(alpha = 0.16f), Offset(x0 + i * bw + 1f, p.b - bh), Size(bw - 2f, bh))
            }
        }

        // the response curve, filled towards 0 dB
        clipRect(p.l, p.t, p.r, p.b) {
            val path = Path()
            CURVE_FREQS.forEachIndexed { i, f ->
                val x = p.x(f)
                val y = p.y(curve[i].toFloat().coerceIn(-EQ_MAX_DB * 1.4f, EQ_MAX_DB * 1.4f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply { addPath(path); lineTo(p.r, p.y(0f)); lineTo(p.l, p.y(0f)); close() }
            drawPath(fill, accent.copy(alpha = 0.16f))
            drawPath(path, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // the points you drag, with their labels underneath
        EQ_BANDS.forEachIndexed { i, hz ->
            val c = Offset(p.x(hz.toDouble()), p.y(gains[i]))
            val on = i == active
            drawCircle(Color.Black, (if (on) 11 else 9).dp.toPx(), c)
            drawCircle(accent, (if (on) 8 else 6).dp.toPx(), c)
            paint.color = (if (on) accent else Color(0xFF8C8C92)).toArgb()
            paint.textSize = 9.sp.toPx()
            val label = EQ_LABELS[i]
            drawContext.canvas.nativeCanvas.drawText(label, c.x - paint.measureText(label) / 2, size.height - 6.dp.toPx(), paint)
            if (on) {
                val txt = if (abs(gains[i]) < 0.05f) "0 dB" else "%+.1f dB".format(gains[i])
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 11.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText(txt, (c.x - paint.measureText(txt) / 2).coerceIn(p.l, p.r - paint.measureText(txt)), (c.y - 16.dp.toPx()).coerceAtLeast(p.t + 10.dp.toPx()), paint)
            }
        }
    }
}

