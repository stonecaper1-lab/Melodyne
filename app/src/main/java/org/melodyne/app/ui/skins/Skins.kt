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
            "The 90s desktop player, full screen with the status bar hidden (swipe from the edge to peek), window by window: a titled main window you can shade or close, seven-segment time (tap to flip elapsed and remaining), a real spectrum with falling peak caps, live kbps and kHz, a scrolling title, green-to-red volume and balance bars, an Equalizer window with your ten bands and a playlist window you can tap to jump around.",
            Color(0xFF00FF66),
        ),
        SkinSpec(
            "tape", "Cassette",
            "A black-and-silver pocket cassette recorder, full screen with the status bar hidden. A cassette slides in for every song, the reels turn at the right relative speeds while tape moves from one spool to the other, the speaker grille breathes with the bass, and the piano keys stay down while they are active. It carries the marks of use (scratches, dust, smudges, scuffs; set the wear in Settings). Hold review or cue to wind, drag along the door to seek, long-press eject to leave.",
            Color(0xFFFFB347),
        ),
        SkinSpec(
            "walkman", "Boodo Khan",
            "The 1986 pocket cassette player, as it looks after decades of use. The front face with its diagonal brushed band, silver keys and green PLAY slider (REW and FF skip on a tap and wind while held, PLAY starts and pauses, STOP stops, and the DOL switch is the Loudness boost); the back with the cassette lying horizontally behind its window, reels turning at the right relative speeds (drag along the window to seek); and the top edge with its ribbed volume wheel. Full screen, with the status bar hidden.",
            Color(0xFFE2B01E),
        ),
        SkinSpec(
            "deck", "Receiver",
            "A 1982 hi-fi receiver in walnut and brushed silver. A tuner dial whose needle tracks the song, an amber display, LED meters, a volume knob, and bass and treble knobs that really move your EQ. Pure Direct and Loudness are real switches.",
            Color(0xFFFFB000),
        ),
    )

    /** Skins that take over the whole screen, status bar included. */
    fun isFullScreen(id: String): Boolean = id == "amp" || id == "tape" || id == "walkman"

    fun byId(id: String): SkinSpec = ALL.firstOrNull { it.id == id } ?: ALL.first()
    fun accent(id: String): Color = byId(id).accent

    fun background(id: String): Brush = when (id) {
        "amp" -> Brush.verticalGradient(listOf(Color(0xFF14171B), Color(0xFF0A0C0E)))
        "tape" -> Brush.verticalGradient(listOf(Color(0xFF241B16), Color(0xFF120D0A)))
        "walkman" -> Brush.verticalGradient(listOf(Color(0xFF26282B), Color(0xFF111214)))
        "deck" -> Brush.verticalGradient(listOf(Color(0xFF1B1B1D), Color(0xFF0B0B0C)))
        else -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    }
}
