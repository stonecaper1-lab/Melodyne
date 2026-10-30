package org.melodyne.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Song

data class PlayerUiState(
    val hasItem: Boolean = false,
    val songId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artUrl: String? = null,
    val hasLyrics: Boolean = false,
    val hintDurationMs: Long = 0,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val quality: AudioQuality? = null,
    val error: String? = null,
    val currentSong: Song? = null,
)

/** UI-side handle to the playback service: commands go in, a StateFlow comes out. */
class PlayerConnection(private val app: Context) {
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val healed = HashSet<String>()
    private var countJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { scheduleCount(mediaItem) }
        override fun onPlayerError(error: PlaybackException) { controller?.let { heal(it) } }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                // The service publishes the real stream format here (QualityMonitor).
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = publishQuality(extras)
            })
            .buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync(c)
            publishQuality(c.sessionExtras)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun publishQuality(extras: Bundle) = _state.update { it.copy(quality = AudioQuality.from(extras)) }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val md = item?.mediaMetadata
        val ex = md?.extras
        _state.update {
            it.copy(
                hasItem = item != null,
                songId = item?.mediaId,
                title = md?.title?.toString().orEmpty(),
                artist = md?.artist?.toString().orEmpty(),
                artUrl = md?.artworkUri?.toString(),
                hasLyrics = ex?.getBoolean(Keys.HAS_LYRICS) ?: false,
                hintDurationMs = (ex?.getInt(Keys.DURATION_SEC) ?: 0) * 1000L,
                isPlaying = p.isPlaying,
                buffering = p.playbackState == Player.STATE_BUFFERING,
                error = p.playerError?.let { e -> "Stream failed (${e.errorCodeName})" },
                currentSong = item?.toSong(),
            )
        }
    }

    /** A listen only counts after 20 s, so skipping past a track doesn't feed smart playlists. */
    private fun scheduleCount(item: MediaItem?) {
        countJob?.cancel()
        val song = item?.toSong() ?: return
        countJob = scope.launch {
            delay(20_000)
            if (controller?.currentMediaItem?.mediaId == song.id) {
                ServiceLocator.library.recordPlay(song)
                ServiceLocator.recents.add(song)
            }
        }
    }

    /** A saved stream link can go stale. On a playback error, look the song up again once and swap in the fresh link. */
    private fun heal(c: Player) {
        val item = c.currentMediaItem ?: return
        val id = item.mediaId
        if (!healed.add(id)) return
        val index = c.currentMediaItemIndex
        val md = item.mediaMetadata
        scope.launch {
            val results = runCatching { ServiceLocator.repo.search("${md.title} ${md.artist}") }.getOrNull() ?: return@launch
            val fresh = results.firstOrNull { it.id == id } ?: results.firstOrNull() ?: return@launch
            withController { ctl ->
                ctl.replaceMediaItem(index, toItem(fresh))
                ctl.prepare()
                ctl.play()
            }
        }
    }

    private fun MediaItem.toSong(): Song? {
        val md = mediaMetadata
        val ex = md.extras ?: return null
        val url = ex.getString(Keys.STREAM_URL) ?: return null
        return Song(
            id = mediaId,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            durationSec = ex.getInt(Keys.DURATION_SEC),
            artUrl = md.artworkUri?.toString(),
            thumbUrl = ex.getString(Keys.THUMB) ?: md.artworkUri?.toString(),
            streamUrl = url,
            streamKbps = ex.getInt(Keys.HINT_KBPS),
            hasLyrics = ex.getBoolean(Keys.HAS_LYRICS),
        )
    }

    private fun toItem(s: Song) = MediaItem.Builder()
        .setMediaId(s.id)
        .setUri(s.streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setArtworkUri(s.artUrl?.let { Uri.parse(it) })
                .setExtras(Bundle().apply {
                    putInt(Keys.HINT_KBPS, s.streamKbps)
                    putInt(Keys.DURATION_SEC, s.durationSec)
                    putBoolean(Keys.HAS_LYRICS, s.hasLyrics)
                    putString(Keys.STREAM_URL, s.streamUrl)
                    putString(Keys.THUMB, s.thumbUrl)
                })
                .build()
        ).build()

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        val i = index.coerceIn(songs.indices)
        healed.clear()
        ServiceLocator.recents.add(songs[i])
        withController { c ->
            c.setMediaItems(songs.map { toItem(it) }, i, 0L)
            c.prepare()
            c.play()
        }
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.let { if (it.currentPosition > 3_000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun position(): Long = controller?.currentPosition ?: 0L
    fun duration(): Long =
        controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: _state.value.hintDurationMs
}
