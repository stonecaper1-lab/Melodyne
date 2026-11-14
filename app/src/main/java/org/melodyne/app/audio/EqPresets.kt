package org.melodyne.app.audio

import kotlin.math.ln
import kotlin.math.roundToInt

const val CUSTOM_ID = "custom"
const val USER_PREFIX = "user_"

/** A pure EQ curve: gain in dB for each of the ten bands (31 Hz to 16 kHz). Nothing else. */
data class EqPreset(val id: String, val name: String, val blurb: String, val gains: List<Float>)

/** Moves curves made for the old seven-band equaliser onto the ten bands, by interpolating along the frequency axis. */
object EqLegacy {
    val OLD_BANDS = listOf(60f, 170f, 450f, 1000f, 2500f, 6000f, 14000f)

    fun resample(old: List<Float>): List<Float> {
        require(old.size == OLD_BANDS.size)
        return EQ_BANDS.map { f ->
            val v = when {
                f <= OLD_BANDS.first() -> old.first()
                f >= OLD_BANDS.last() -> old.last()
                else -> {
                    val i = OLD_BANDS.indexOfLast { it <= f }
                    val t = ((ln(f.toDouble()) - ln(OLD_BANDS[i].toDouble())) / (ln(OLD_BANDS[i + 1].toDouble()) - ln(OLD_BANDS[i].toDouble()))).toFloat()
                    old[i] + (old[i + 1] - old[i]) * t
                }
            }
            (v * 2f).roundToInt() / 2f
        }
    }
}

object EqPresets {
    private fun p(id: String, name: String, blurb: String, vararg sevenBand: Float) = EqPreset(id, name, blurb, EqLegacy.resample(sevenBand.toList()))

    val all = listOf(
        p("flat", "Flat", "Untouched. The reference to compare against.", 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        p("signature", "Signature", "Warm low end, forward vocals, a little air.", 2f, 1.2f, 0f, -0.3f, 1f, 1.8f, 2.2f),
        p("bass_drive", "Bass Drive", "Deep sub and punchy kick for hip-hop, EDM and big film numbers.", 5.5f, 4f, 1.5f, -0.5f, 0f, 0.8f, 1.2f),
        p("vocal", "Vocal Clarity", "Trims low-end mud and lifts the 1 to 4 kHz where voices live.", -3f, -2f, -0.5f, 1.8f, 3.5f, 2.5f, 0.5f),
        p("sparkle", "Bollywood Sparkle", "Bright playback vocals, crisp tabla, shimmering strings.", 1.5f, 0.5f, 0f, 1.2f, 3.5f, 4f, 4f),
        p("unplugged", "Unplugged", "Woody body for guitar, ghazal and acoustic sets.", 1f, 3f, 2.5f, 1f, 0.5f, -0.5f, -1.5f),
        p("low_volume", "Low Volume", "Restores the bass and treble ears lose at quiet levels.", 4.5f, 2.5f, 0.5f, -1f, 0.5f, 2f, 3.5f),
        p("soft_treble", "Soft Treble", "Eases harsh, hissy or sibilant recordings.", 1f, 1f, 0.5f, 0f, -1.5f, -3.5f, -5f),
        p("car", "Car & Bluetooth", "Lifts bass, presence and air that road noise and Bluetooth coding take away.", 3f, 1.5f, -0.5f, 0.5f, 2.5f, 3f, 3f),
        p("small_speaker", "Small Speaker", "Drops sub-bass tiny speakers can't play, lifts the mids that carry.", -5f, -2.5f, 1f, 3f, 3.5f, 2.5f, 1f),
    )

    fun byId(id: String): EqPreset = all.firstOrNull { it.id == id } ?: all.first()
}
