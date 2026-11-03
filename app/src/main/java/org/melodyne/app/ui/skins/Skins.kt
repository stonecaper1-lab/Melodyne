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
            "A 90s desktop player. LCD time (tap to flip between elapsed and remaining), a real 16-band spectrum, live kbps and kHz lamps, scrolling title, working volume and balance, and a playlist button.",
            Color(0xFF00FF66),
        ),
        SkinSpec(
            "tape", "Cassette",
            "Reels that turn as you play, tape that moves from one spool to the other with the song's progress, VU needles driven by the real levels, a tape counter, and hold-to-scrub rewind and fast-forward.",
            Color(0xFFFFB347),
        ),
        SkinSpec(
            "deck", "Receiver",
            "A 1982 hi-fi receiver. Amber display, LED spectrum, a volume knob you can turn, a tuning dial for seeking, and lamps that really light for Pure Direct, Loudness and EQ.",
            Color(0xFFFFB000),
        ),
    )

    fun byId(id: String): SkinSpec = ALL.firstOrNull { it.id == id } ?: ALL.first()
    fun accent(id: String): Color = byId(id).accent

    fun background(id: String): Brush = when (id) {
        "amp" -> Brush.verticalGradient(listOf(Color(0xFF14171B), Color(0xFF0A0C0E)))
        "tape" -> Brush.verticalGradient(listOf(Color(0xFF241B16), Color(0xFF120D0A)))
        "deck" -> Brush.verticalGradient(listOf(Color(0xFF1B1B1D), Color(0xFF0B0B0C)))
        else -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    }
}
