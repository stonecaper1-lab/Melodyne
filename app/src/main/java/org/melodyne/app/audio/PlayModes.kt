package org.melodyne.app.audio

import kotlin.math.roundToInt

enum class PlayMode { NORMAL, AUDIOPHILE, FRUGAL }

/** Which of the three playback modes is in force right now, and how to describe it. Pure, so it is easy to test. */
object PlayModes {
    /** Frugal wins if both ever show up together; they switch each other off, so this is only a safety net. */
    fun of(s: PlaybackState): PlayMode = when {
        s.frugal -> PlayMode.FRUGAL
        s.audiophile -> PlayMode.AUDIOPHILE
        else -> PlayMode.NORMAL
    }

    fun title(m: PlayMode): String = when (m) {
        PlayMode.NORMAL -> "Normal mode"
        PlayMode.AUDIOPHILE -> "Audiophile Mode"
        PlayMode.FRUGAL -> "Frugal mode"
    }

    fun label(m: PlayMode): String = when (m) {
        PlayMode.NORMAL -> "NORMAL"
        PlayMode.AUDIOPHILE -> "AUDIOPHILE"
        PlayMode.FRUGAL -> "FRUGAL"
    }

    /** Why Frugal is on: you switched it on, or Auto saw Battery Saver or mobile data. */
    fun frugalReason(s: PlaybackState): String = when {
        s.frugalMode == 1 -> "Switched on"
        s.frugalMode == 2 && s.batterySaver -> "Auto: Battery Saver is on"
        s.frugalMode == 2 && s.onMetered -> "Auto: you're on mobile data"
        else -> "Switched on"
    }

    /** One or two sentences about what the mode does right now. [eqShaping] says whether the equaliser is actually changing the sound. */
    fun detail(s: PlaybackState, eqShaping: Boolean = false): String = when (of(s)) {
        PlayMode.FRUGAL ->
            frugalReason(s) + ". ${s.frugalKbps} kbps streams, small artwork and a smaller buffer; EQ, normalisation, crossfade and skins are off."
        PlayMode.AUDIOPHILE ->
            "Bit-transparent playback: EQ, normalisation, crossfade and stereo width are bypassed."
        PlayMode.NORMAL -> {
            val active = listOfNotNull(
                if (eqShaping) "EQ" else null,
                if (s.normalizeEffective) "volume normalised" else null,
                if (s.crossfadeEffective) "crossfade ${s.crossfadeSec} s" else null,
                if (s.width > 0.01f) "width +${(s.width * 100).roundToInt()}%" else null,
            )
            if (active.isEmpty()) "Standard playback, no effects." else "Effects active: " + active.joinToString(", ") + "."
        }
    }
}
