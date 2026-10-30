package org.melodyne.app.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/** ITU-R BS.1770 K-weighting and the gain rule used by volume normalisation. Pure maths, unit-tested. */
object Loudness {
    const val TARGET_LUFS = -14.0
    const val MIN_GAIN_DB = -12.0
    const val MAX_GAIN_DB = 3.0

    /** Stage 1: high-shelf "head" filter. Returns b0,b1,b2,a1,a2 (a0 = 1). */
    fun stage1(fs: Double): DoubleArray {
        val f0 = 1681.974450955533; val g = 3.999843853973347; val q = 0.7071752369554196
        val k = tan(PI * f0 / fs); val vh = 10.0.pow(g / 20.0); val vb = vh.pow(0.4996667741545416)
        val a0 = 1.0 + k / q + k * k
        return doubleArrayOf(
            (vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0,
            2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0,
        )
    }

    /** Stage 2: RLB high-pass. Numerator is deliberately not divided by a0, as in the standard. */
    fun stage2(fs: Double): DoubleArray {
        val f0 = 38.13547087602444; val q = 0.5003270373238773
        val k = tan(PI * f0 / fs)
        val a0 = 1.0 + k / q + k * k
        return doubleArrayOf(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
    }

    fun lufs(meanSquareSum: Double): Double = -0.691 + 10.0 * log10(meanSquareSum)

    /** Gain (dB) that brings [integratedLufs] to the target, never boosting past what the measured [peak] (0..1) allows. */
    fun gainDb(integratedLufs: Double, peak: Double): Double {
        var db = (TARGET_LUFS - integratedLufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val peakDb = 20.0 * log10(peak.coerceAtLeast(1e-6))
        val headroom = (-peakDb - 0.5).coerceAtLeast(0.0)
        if (db > headroom) db = headroom
        return db
    }
}
