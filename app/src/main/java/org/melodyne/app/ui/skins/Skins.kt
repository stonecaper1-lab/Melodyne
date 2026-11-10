package org.melodyne.app.ui.skins

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class SkinSpec(val id: String, val name: String, val blurb: String, val accent: Color)

/**
 * Nostalgic player skins. They are not just paint: each one shows the real live spectrum, levels, bitrate and
 * state (Pure Direct, EQ, infinite play), and its controls do real work (volume, balance, scrubbing, seeking).
 * Homages to the hardware and software of the 80s and 90s; original artwork, no borrowed assets.
 */
object Skins {
    const val LIQUID = "liquid"
    val ALL = listOf(
        SkinSpec(LIQUID, "Liquid", "The default: liquid glass, blurred artwork and your song's own colour.", Color(0xFF1DE9B6)),
        SkinSpec(
            "amp", "Classic Amp",
            "The 90s desktop player, window by window: a titled main window you can shade or close, seven-segment time (tap to flip elapsed and remaining), a real spectrum with falling peak caps, and a playlist editor.",
            Color(0xFF00FF66),
        ),
        SkinSpec(
            "tape", "Cassette ’96",
            "A cassette deck reimagined with the compact graphite panels, electric-lime analyzer, blue title bar, segmented readout and playlist feel of a classic 90s desktop player. Original Melodyne artwork and real playback controls.",
            Color(0xFFB7FF35),
        ),
        SkinSpec(
            "deck", "Receiver",
            "A 1982 hi-fi receiver in walnut and brushed silver. A tuner dial whose needle tracks the song, an amber display, LED meters, a volume knob, and bass and treble knobs that really move the EQ.",
            Color(0xFFFFB000),
        ),
    )

    fun byId(id: String): SkinSpec = ALL.firstOrNull { it.id == id } ?: ALL.first()
    fun accent(id: String): Color = byId(id).accent

    fun background(id: String): Brush = when (id) {
        "amp" -> Brush.verticalGradient(listOf(Color(0xFF14171B), Color(0xFF0A0C0E)))
        "tape" -> Brush.verticalGradient(listOf(Color(0xFF111824), Color(0xFF080B10)))
        "deck" -> Brush.verticalGradient(listOf(Color(0xFF1B1B1D), Color(0xFF0B0B0C)))
        else -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    }
}
