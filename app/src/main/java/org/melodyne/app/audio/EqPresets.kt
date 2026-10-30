package org.melodyne.app.audio

const val CUSTOM_ID = "custom"

/** A pure EQ curve: gain in dB for each of the seven bands (60, 170, 450, 1k, 2.5k, 6k, 14k Hz). Nothing else. */
data class EqPreset(val id: String, val name: String, val blurb: String, val gains: List<Float>)

object EqPresets {
    val all = listOf(
        EqPreset("flat", "Flat", "Untouched. The reference to compare against.", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("signature", "Signature", "Warm low end, forward vocals, a little air.", listOf(2f, 1.2f, 0f, -0.3f, 1f, 1.8f, 2.2f)),
        EqPreset("bass_drive", "Bass Drive", "Deep sub and punchy kick for hip-hop, EDM and big film numbers.", listOf(5.5f, 4f, 1.5f, -0.5f, 0f, 0.8f, 1.2f)),
        EqPreset("vocal", "Vocal Clarity", "Trims low-end mud and lifts the 1 to 4 kHz where voices live.", listOf(-3f, -2f, -0.5f, 1.8f, 3.5f, 2.5f, 0.5f)),
        EqPreset("sparkle", "Bollywood Sparkle", "Bright playback vocals, crisp tabla, shimmering strings.", listOf(1.5f, 0.5f, 0f, 1.2f, 3.5f, 4f, 4f)),
        EqPreset("unplugged", "Unplugged", "Woody body for guitar, ghazal and acoustic sets.", listOf(1f, 3f, 2.5f, 1f, 0.5f, -0.5f, -1.5f)),
        EqPreset("low_volume", "Low Volume", "Restores the bass and treble ears lose at quiet levels.", listOf(4.5f, 2.5f, 0.5f, -1f, 0.5f, 2f, 3.5f)),
        EqPreset("soft_treble", "Soft Treble", "Eases harsh, hissy or sibilant recordings.", listOf(1f, 1f, 0.5f, 0f, -1.5f, -3.5f, -5f)),
        EqPreset("small_speaker", "Small Speaker", "Drops sub-bass tiny speakers can't play, lifts the mids that carry.", listOf(-5f, -2.5f, 1f, 3f, 3.5f, 2.5f, 1f)),
    )

    fun byId(id: String): EqPreset = all.firstOrNull { it.id == id } ?: all.first()
}
