package org.melodyne.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Seven bands: low shelf, five bells, high shelf. */
val EQ_BANDS = listOf(60f, 170f, 450f, 1000f, 2500f, 6000f, 14000f)
val EQ_LABELS = listOf("60", "170", "450", "1k", "2.5k", "6k", "14k")
const val EQ_MAX_DB = 10f

/** Normalised biquad (a0 = 1). */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

/** RBJ cookbook filters, designed per sample rate, evaluated in double precision. */
object EqMath {
    private const val BELL_Q = 1.0
    private const val SHELF_Q = 0.7071

    fun design(band: Int, gainDb: Float, fs: Double): Biquad {
        val f0 = min(EQ_BANDS[band].toDouble(), fs * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val sw = sin(w0)
        return when (band) {
            0 -> {
                val al = sw / (2 * SHELF_Q); val sa = sqrt(a)
                norm(
                    a * ((a + 1) - (a - 1) * cw + 2 * sa * al), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - 2 * sa * al),
                    (a + 1) + (a - 1) * cw + 2 * sa * al, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - 2 * sa * al,
                )
            }
            EQ_BANDS.lastIndex -> {
                val al = sw / (2 * SHELF_Q); val sa = sqrt(a)
                norm(
                    a * ((a + 1) + (a - 1) * cw + 2 * sa * al), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - 2 * sa * al),
                    (a + 1) - (a - 1) * cw + 2 * sa * al, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - 2 * sa * al,
                )
            }
            else -> {
                val al = sw / (2 * BELL_Q)
                norm(1 + al * a, -2 * cw, 1 - al * a, 1 + al / a, -2 * cw, 1 - al / a)
            }
        }
    }

    private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    fun magnitudeDb(q: Biquad, f: Double, fs: Double): Double {
        val w = 2.0 * PI * f / fs
        val nr = q.b0 + q.b1 * cos(w) + q.b2 * cos(2 * w)
        val ni = -(q.b1 * sin(w) + q.b2 * sin(2 * w))
        val dr = 1.0 + q.a1 * cos(w) + q.a2 * cos(2 * w)
        val di = -(q.a1 * sin(w) + q.a2 * sin(2 * w))
        return 10.0 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    /** Combined response of all bands at [f], in dB. */
    fun totalDb(gains: List<Float>, f: Double, fs: Double): Double =
        gains.indices.fold(0.0) { acc, i -> acc + magnitudeDb(design(i, gains[i], fs), f, fs) }

    /** How far the whole curve must be pulled down so its loudest point stays at or below 0 dB (no clipping). */
    fun headroomDb(gains: List<Float>, fs: Double = 44100.0): Double {
        if (gains.all { abs(it) < 0.05f }) return 0.0
        val filters = gains.indices.map { design(it, gains[it], fs) }
        var peak = -100.0
        val top = fs * 0.45
        for (k in 0..200) {
            val f = min(top, 20.0 * (20000.0 / 20.0).pow(k / 200.0))
            peak = max(peak, filters.fold(0.0) { acc, q -> acc + magnitudeDb(q, f, fs) })
        }
        return max(0.0, peak)
    }
}
