package org.melodyne.app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.melodyne.app.data.lastfm.LastFmClient

/**
 * Last.fm rules: track longer than 30 s, scrobble after 50 % of its length or 4 minutes of
 * actual listening, whichever comes first. Time only accrues while playing.
 */
class Scrobbling(
    private val player: Player,
    private val scope: CoroutineScope,
    private val lastFm: LastFmClient,
) {
    private var item: MediaItem? = null
    private var startedAtSec = 0L
    private var playedMs = 0L
    private var scrobbled = false
    private var ticker: Job? = null

    fun attach() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = begin(mediaItem)

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ticker?.cancel()
                if (!isPlaying) return
                if (item == null) begin(player.currentMediaItem)
                ticker = scope.launch {
                    while (isActive) {
                        delay(1_000)
                        playedMs += 1_000
                        maybeScrobble()
                    }
                }
            }
        })
    }

    private fun durationSec(mi: MediaItem?): Int {
        val fromApi = mi?.mediaMetadata?.extras?.getInt(Keys.DURATION_SEC, 0) ?: 0
        if (fromApi > 0) return fromApi
        val live = player.duration                       // fall back to what the decoder reports
        return if (live > 0) (live / 1000).toInt() else 0
    }

    private fun begin(mi: MediaItem?) {
        item = mi
        playedMs = 0
        scrobbled = false
        startedAtSec = System.currentTimeMillis() / 1000
        val m = mi?.mediaMetadata ?: return
        scope.launch {
            lastFm.nowPlaying(m.artist?.toString().orEmpty(), m.title?.toString().orEmpty(), m.albumTitle?.toString(), durationSec(mi))
        }
    }

    private fun maybeScrobble() {
        val mi = item ?: return
        val dur = durationSec(mi)
        if (scrobbled || dur < 30) return
        if (playedMs < minOf(dur * 1000L / 2, 240_000L)) return
        scrobbled = true
        val m = mi.mediaMetadata
        val started = startedAtSec
        scope.launch {
            lastFm.scrobble(m.artist?.toString().orEmpty(), m.title?.toString().orEmpty(), m.albumTitle?.toString(), started, dur)
        }
    }
}
