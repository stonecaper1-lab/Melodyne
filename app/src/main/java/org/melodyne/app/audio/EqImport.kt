package org.melodyne.app.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** [gains] are the ten band gains; [count] is how many filters or points were read; [skipped] those it could not use. */
data class EqImportResult(val gains: List<Float>, val count: Int, val skipped: Int, val clipped: Boolean, val kind: String)

/**
 * Reads headphone-correction profiles in the two common AutoEQ text formats: a "GraphicEQ: 20 0.0; 25 1.5; ..." line,
 * or a list of "Filter 1: ON PK Fc 105 Hz Gain -3.2 dB Q 0.70" lines (peaking and shelving filters). The profile's
 * response at each of our ten band centres becomes the band gain. A "Preamp" line is ignored: auto-headroom covers it.
 */
object EqImport {
    private val filter = Regex(
        """Filter\s*\d*\s*:\s*(ON|OFF)\s+(\w+)\s+Fc\s+([0-9.]+)\s*Hz\s+Gain\s+(-?[0-9.]+)\s*dB(?:\s+Q\s+([0-9.]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val graphic = Regex("""GraphicEQ\s*:\s*([^\r\n]*)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): EqImportResult? = parseGraphic(text) ?: parseParametric(text)

    private fun parseGraphic(text: String): EqImportResult? {
        val body = graphic.find(text)?.groupValues?.get(1) ?: return null
        val points = body.split(';').mapNotNull { part ->
            val t = part.trim().split(Regex("\\s+"))
            val f = t.getOrNull(0)?.toDoubleOrNull()
            val g = t.getOrNull(1)?.toDoubleOrNull()
            if (f != null && g != null && f > 0) f to g else null
        }.sortedBy { it.first }
        if (points.size < 2) return null
        return finish(EQ_BANDS.map { interpolate(points, it.toDouble()) }, points.size, 0, "graphic points")
    }

    private fun interpolate(points: List<Pair<Double, Double>>, f: Double): Double {
        if (f <= points.first().first) return points.first().second
        if (f >= points.last().first) return points.last().second
        val i = points.indexOfLast { it.first <= f }
        val (f0, g0) = points[i]
        val (f1, g1) = points[i + 1]
        val t = (ln(f) - ln(f0)) / (ln(f1) - ln(f0))
        return g0 + (g1 - g0) * t
    }

    private fun parseParametric(text: String): EqImportResult? {
        val fs = EqMath.UI_RATE
        val filters = ArrayList<Biquad>()
        var skipped = 0
        for (m in filter.findAll(text)) {
            val on = m.groupValues[1].equals("ON", ignoreCase = true)
            val type = m.groupValues[2].uppercase()
            val fc = m.groupValues[3].toDoubleOrNull()
            val gain = m.groupValues[4].toDoubleOrNull()
            val q = m.groupValues[5].toDoubleOrNull()
            if (!on || fc == null || gain == null) { skipped++; continue }
            val bq = when (type) {
                "PK", "PEQ" -> EqMath.peaking(fc, gain, q ?: 1.0, fs)
                "LSC", "LS" -> EqMath.lowShelf(fc, gain, q ?: EqMath.SHELF_Q, fs)
                "HSC", "HS" -> EqMath.highShelf(fc, gain, q ?: EqMath.SHELF_Q, fs)
                else -> null
            }
            if (bq == null) skipped++ else filters.add(bq)
        }
        if (filters.isEmpty()) return null
        val targets = EQ_BANDS.map { b -> filters.fold(0.0) { acc, q -> acc + EqMath.magnitudeDb(q, b.toDouble(), fs) } }
        return finish(targets, filters.size, skipped, "filters")
    }

    private fun finish(targets: List<Double>, count: Int, skipped: Int, kind: String): EqImportResult {
        val limit = EQ_MAX_DB.toDouble()
        val clipped = targets.any { abs(it) > limit }
        val gains = targets.map { (it.coerceIn(-limit, limit) * 2).roundToInt() / 2f }
        return EqImportResult(gains, count, skipped, clipped, kind)
    }
}
