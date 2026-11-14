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

/** Ten octave bands, 31 Hz to 16 kHz. */
val EQ_BANDS = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
const val EQ_MAX_DB = 12f

/** Normalised biquad (a0 = 1). */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

/** RBJ cookbook filters, designed per sample rate, evaluated in double precision. */
object EqMath {
    const val BELL_Q = 1.2
    const val SHELF_Q = 0.7071
    /** The sample rate used to draw curves and to read imported profiles. */
    const val UI_RATE = 48000.0

    /** One band's bell, at its centre frequency. */
    fun design(band: Int, gainDb: Float, fs: Double): Biquad = peaking(EQ_BANDS[band].toDouble(), gainDb.toDouble(), BELL_Q, fs)

    fun peaking(freq: Double, gainDb: Double, q: Double, fs: Double): Biquad {
        val f0 = min(freq, fs * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val al = sin(w0) / (2 * q)
        return norm(1 + al * a, -2 * cw, 1 - al * a, 1 + al / a, -2 * cw, 1 - al / a)
    }

    fun lowShelf(freq: Double, gainDb: Double, q: Double, fs: Double): Biquad {
        val f0 = min(freq, fs * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val al = sin(w0) / (2 * q)
        val sa = sqrt(a)
        return norm(
            a * ((a + 1) - (a - 1) * cw + 2 * sa * al), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - 2 * sa * al),
            (a + 1) + (a - 1) * cw + 2 * sa * al, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - 2 * sa * al,
        )
    }

    fun highShelf(freq: Double, gainDb: Double, q: Double, fs: Double): Biquad {
        val f0 = min(freq, fs * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val al = sin(w0) / (2 * q)
        val sa = sqrt(a)
        return norm(
            a * ((a + 1) + (a - 1) * cw + 2 * sa * al), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - 2 * sa * al),
            (a + 1) - (a - 1) * cw + 2 * sa * al, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - 2 * sa * al,
        )
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

    /**
     * Neighbouring bells overlap, so a slider at +6 dB would not give +6 dB on its own. This nudges each band until the
     * combined response passes through the gains as drawn: the curve you draw is the curve you get.
     */
    fun designGains(targets: List<Float>, fs: Double = UI_RATE, iterations: Int = 20): List<Float> {
        if (targets.all { abs(it) < 0.05f }) return List(targets.size) { 0f }
        val limit = EQ_MAX_DB * 2f
        val g = targets.toMutableList()
        repeat(iterations) {
            val filters = g.indices.map { design(it, g[it], fs) }
            for (i in g.indices) {
                val centre = min(EQ_BANDS[i].toDouble(), fs * 0.45)
                val actual = filters.fold(0.0) { acc, q -> acc + magnitudeDb(q, centre, fs) }
                g[i] = (g[i] + (targets[i] - actual).toFloat()).coerceIn(-limit, limit)
            }
        }
        return g
    }

    /** The response curve (dB) that the gains as drawn produce, at each of [freqs]. */
    fun curve(targets: List<Float>, freqs: List<Double>, fs: Double = UI_RATE): List<Double> {
        val designed = designGains(targets, fs)
        val filters = designed.indices.map { design(it, designed[it], fs) }
        return freqs.map { f -> filters.fold(0.0) { acc, q -> acc + magnitudeDb(q, f, fs) } }
    }

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

    /** Headroom for the gains as drawn (it measures the pre-compensated bands that actually run). */
    fun headroomForTargets(targets: List<Float>, fs: Double = 44100.0): Double = headroomDb(designGains(targets, fs), fs)
}
