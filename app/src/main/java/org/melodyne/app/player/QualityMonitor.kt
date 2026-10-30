package org.melodyne.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlin.math.abs

/**
 * Reads the format ExoPlayer actually feeds the audio decoder, so the badge reflects the real
 * stream rather than a guess from the URL.
 *
 *  - onAudioInputFormatChanged: fires with the Format of the samples entering the codec.
 *  - onTracksChanged: the selected audio track's Format (covers the moment before decoding).
 *  - Fallback when the container carries no bitrate: the API's label, flagged verified = false.
 */
@UnstableApi
class QualityMonitor(
    private val player: ExoPlayer,
    private val publish: (AudioQuality) -> Unit,
) {
    fun attach() {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) = emit(format)
        })
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                hintOnly(mediaItem)?.let(publish)
            }

            override fun onTracksChanged(tracks: Tracks) {
                for (g in tracks.groups) {
                    if (g.type != C.TRACK_TYPE_AUDIO || !g.isSelected) continue
                    for (i in 0 until g.length) if (g.isTrackSelected(i)) { emit(g.getTrackFormat(i)); return }
                }
            }
        })
    }

    private fun hintKbps(item: MediaItem?) = item?.mediaMetadata?.extras?.getInt(Keys.HINT_KBPS, 0) ?: 0

    private fun hintOnly(item: MediaItem?): AudioQuality? {
        val k = hintKbps(item)
        return if (item == null || k <= 0) null else AudioQuality(k, "AAC", "", 0, verified = false)
    }

    private fun emit(f: Format) {
        val bps = listOf(f.averageBitrate, f.bitrate, f.peakBitrate)
            .firstOrNull { it != Format.NO_VALUE && it > 0 }
        val measured = bps?.let { (it + 500) / 1000 }
        val (codec, profile) = codecOf(f)
        publish(
            AudioQuality(
                kbps = snap(measured ?: hintKbps(player.currentMediaItem)),
                codec = codec,
                profile = profile,
                sampleRateHz = f.sampleRate.takeIf { it != Format.NO_VALUE } ?: 0,
                verified = measured != null,
                measured = measured ?: 0,
            )
        )
    }

    // Encoders land a hair off the nominal tier (e.g. 319 for "320"); within 4 % show the tier, otherwise show what was measured.
    private fun snap(k: Int) = TIERS.firstOrNull { abs(k - it) <= it * 0.04 } ?: k

    private fun codecOf(f: Format): Pair<String, String> {
        val codecs = f.codecs.orEmpty()
        return when {
            codecs.startsWith("mp4a.40.29") -> "AAC" to "HE-AAC v2"
            codecs.startsWith("mp4a.40.5") -> "AAC" to "HE-AAC"
            codecs.startsWith("mp4a.40.2") -> "AAC" to "AAC-LC"
            f.sampleMimeType == MimeTypes.AUDIO_AAC -> "AAC" to ""
            f.sampleMimeType == MimeTypes.AUDIO_MPEG -> "MP3" to ""
            f.sampleMimeType == MimeTypes.AUDIO_OPUS -> "Opus" to ""
            f.sampleMimeType == MimeTypes.AUDIO_FLAC -> "FLAC" to ""
            else -> (f.sampleMimeType?.substringAfter('/')?.uppercase() ?: "Audio") to ""
        }
    }

    private companion object { val TIERS = intArrayOf(12, 48, 96, 160, 320) }
}
