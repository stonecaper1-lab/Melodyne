package org.melodyne.app.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.melodyne.app.audio.CrossfadeCurve
import org.melodyne.app.audio.NormalizerController
import org.melodyne.app.audio.PlaybackSettings
import kotlin.math.pow

/**
 * Crossfade with two decoders. The session's player always stays the same one. A few seconds before the end, a helper
 * "tail" player is parked on the last seconds of the current song. At the fade point the tail player carries on with
 * the outgoing song (fading out) while the main player jumps to the next song (fading in).
 * Manual skips and seeks cut cleanly; the fade is skipped for the last song, repeat-one and very short songs.
 */
@UnstableApi
class CrossfadeEngine(
    private val main: ExoPlayer,
    private val newTailPlayer: () -> ExoPlayer,
    private val settings: PlaybackSettings,
    private val normalizer: NormalizerController,
    private val scope: CoroutineScope,
) {
    private var tail: ExoPlayer? = null
    private var fadeJob: Job? = null
    private var armedFor: String? = null
    private var firedFor: String? = null
    private var ignoreUntil = 0L

    fun attach() {
        scope.launch { while (isActive) { delay(250); tick() } }
        main.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (fadeJob?.isActive == true) tail?.playWhenReady = isPlaying       // pause/resume both together
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (SystemClock.uptimeMillis() < ignoreUntil) return                  // our own jump to the next song
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) cancel()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (SystemClock.uptimeMillis() < ignoreUntil) return
                cancel(); firedFor = null
            }
        })
    }

    val isFading: Boolean get() = fadeJob?.isActive == true

    fun release() { cancel(); tail?.release(); tail = null }

    private fun tick() {
        val s = settings.state.value
        if (!s.crossfadeEffective) { if (fadeJob != null || armedFor != null) cancel(); return }
        if (fadeJob?.isActive == true) return
        if (!main.isPlaying || !main.hasNextMediaItem() || main.repeatMode == Player.REPEAT_MODE_ONE) { armedFor = null; return }
        val dur = main.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val fadeMs = s.crossfadeSec * 1000L
        if (dur < fadeMs * 3) return                                                   // too short to be worth a fade
        val id = main.currentMediaItem?.mediaId ?: return
        if (firedFor == id) return
        val remaining = dur - main.currentPosition
        if (remaining <= fadeMs + 4000 && armedFor != id) arm(id, dur - fadeMs)
        if (remaining <= fadeMs && armedFor == id) fire(id, fadeMs, s.equalPower)
    }

    /** Parks the tail player, paused, on the point where the fade will begin. */
    private fun arm(id: String, startMs: Long) {
        val item = main.currentMediaItem ?: return
        val t = tail ?: newTailPlayer().also { tail = it }
        t.volume = 0f
        t.playWhenReady = false
        t.setMediaItem(item, startMs.coerceAtLeast(0))
        t.prepare()
        armedFor = id
    }

    private fun fire(id: String, fadeMs: Long, equalPower: Boolean) {
        val t = tail ?: return
        firedFor = id
        val keep = 10.0.pow((normalizer.gainDb.value ?: 0f) / 20.0).toFloat()          // outgoing song keeps its own loudness gain
        t.seekTo(main.currentPosition)
        t.volume = keep
        t.playWhenReady = true
        ignoreUntil = SystemClock.uptimeMillis() + 800
        main.volume = 0f
        main.seekToNextMediaItem()
        fadeJob = scope.launch {
            var elapsed = 0L
            while (isActive) {
                if (main.isPlaying) elapsed += 40                                      // the fade only moves while audio moves
                val p = (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)
                val (incoming, outgoing) = CrossfadeCurve.gains(p, equalPower)
                main.volume = incoming
                t.volume = outgoing * keep
                if (p >= 1f) break
                delay(40)
            }
            finish()
        }
    }

    private fun finish() {
        tail?.let { it.stop(); it.clearMediaItems() }
        main.volume = 1f
        armedFor = null
        fadeJob = null
    }

    private fun cancel() {
        fadeJob?.cancel(); fadeJob = null
        tail?.let { it.stop(); it.clearMediaItems() }
        main.volume = 1f
        armedFor = null
    }
}
