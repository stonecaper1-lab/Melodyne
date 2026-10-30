package org.melodyne.app.player

import android.os.Bundle

/** Keys shared by the UI process side and the service (all in one process, but IPC-safe via Bundles). */
object Keys {
    const val HINT_KBPS = "hint_kbps"
    const val DURATION_SEC = "duration_sec"
    const val HAS_LYRICS = "has_lyrics"
    const val STREAM_URL = "stream_url"
    const val THUMB = "thumb"
}

/**
 * What the decoder is really being fed. [verified] is true when the bitrate came from the
 * stream's own container metadata (ExoPlayer Format), false when only the API's label is known.
 */
data class AudioQuality(
    val kbps: Int,
    val codec: String,
    val profile: String,
    val sampleRateHz: Int,
    val verified: Boolean,
    /** The bitrate exactly as found in the stream header (0 when the header carried none). */
    val measured: Int = 0,
) {
    fun label(): String = (if (kbps > 0) "$kbps kbps " else "") + codec

    fun detail(): String = listOfNotNull(
        profile.takeIf { it.isNotBlank() },
        sampleRateHz.takeIf { it > 0 }?.let { "%.1f kHz".format(it / 1000f) },
    ).joinToString(" · ")

    fun toBundle() = Bundle().apply {
        putInt("kbps", kbps); putString("codec", codec); putString("profile", profile)
        putInt("sr", sampleRateHz); putBoolean("verified", verified); putInt("measured", measured)
    }

    companion object {
        fun from(b: Bundle?): AudioQuality? {
            if (b == null || !b.containsKey("codec")) return null
            return AudioQuality(
                b.getInt("kbps"), b.getString("codec").orEmpty(), b.getString("profile").orEmpty(),
                b.getInt("sr"), b.getBoolean("verified"), b.getInt("measured"),
            )
        }
    }
}
