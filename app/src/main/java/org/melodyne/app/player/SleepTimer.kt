package org.melodyne.app.player

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SleepUi(val endAt: Long? = null, val endOfSong: Boolean = false, val remainingMs: Long = 0L) {
    val active: Boolean get() = endAt != null || endOfSong
    fun label(): String = if (endOfSong) "End of this song" else "%d:%02d".format(remainingMs / 60_000, (remainingMs / 1000) % 60)
}

/** Shared by the Now Playing sheet (sets it) and the playback service (carries it out, even with the app closed). */
class SleepTimer {
    private val _state = MutableStateFlow(SleepUi())
    val state: StateFlow<SleepUi> = _state.asStateFlow()

    fun start(minutes: Int) {
        val ms = minutes * 60_000L
        _state.value = SleepUi(endAt = SystemClock.elapsedRealtime() + ms, remainingMs = ms)
    }

    fun extend(minutes: Int) {
        _state.update { s -> if (s.endAt != null) s.copy(endAt = s.endAt + minutes * 60_000L, remainingMs = s.remainingMs + minutes * 60_000L) else s }
    }

    fun startEndOfSong() { _state.value = SleepUi(endOfSong = true) }
    fun cancel() { _state.value = SleepUi() }
    internal fun publishRemaining(ms: Long) { _state.update { if (it.endAt != null) it.copy(remainingMs = ms) else it } }

    companion object {
        const val FADE_MS = 20_000L
        /** 1 = full volume; falls to 0 over the last [FADE_MS]. */
        fun fadeFactor(remainingMs: Long): Float = (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }
}

/**
 * Pauses playback when the timer runs out, fading the last 20 seconds. "End of this song" uses ExoPlayer's own
 * pause-at-end-of-item, so the song finishes cleanly. The fade is skipped while a crossfade is running.
 */
@UnstableApi
class SleepEngine(
    private val player: ExoPlayer,
    private val timer: SleepTimer,
    private val scope: CoroutineScope,
    private val crossfading: () -> Boolean,
) {
    private var fading = false

    fun attach() {
        scope.launch {
            timer.state.collect { s ->
                player.pauseAtEndOfMediaItems = s.endOfSong
                if (!s.active && fading) { player.volume = 1f; fading = false }          // cancelled mid-fade: bring the volume back
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && timer.state.value.endOfSong) timer.cancel()
            }
        })
        scope.launch { while (isActive) { delay(1000); tick() } }
    }

    private fun tick() {
        val end = timer.state.value.endAt ?: return
        val left = end - SystemClock.elapsedRealtime()
        if (left <= 0) {
            player.pause()
            player.volume = 1f
            fading = false
            timer.cancel()
            return
        }
        timer.publishRemaining(left)
        if (left <= SleepTimer.FADE_MS && player.isPlaying && !crossfading()) {
            player.volume = SleepTimer.fadeFactor(left)
            fading = true
        }
    }
}
