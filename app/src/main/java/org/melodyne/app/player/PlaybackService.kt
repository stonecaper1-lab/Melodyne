package org.melodyne.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.melodyne.app.MainActivity
import org.melodyne.app.di.ServiceLocator
import java.io.File

/**
 * Owns ExoPlayer + MediaSession. Media3 supplies the foreground notification, lock-screen and Bluetooth controls.
 * Stream quality is broadcast through session extras; every audio frame passes the BitrateMeter on its way in;
 * the EQ and normaliser sit inside the audio sink; a helper player carries the outgoing song during a crossfade.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var cache: SimpleCache? = null
    private var engine: CrossfadeEngine? = null
    private val eqProcessors = ArrayList<EqProcessor>()
    private val loudnessProcessor = LoudnessProcessor(ServiceLocator.normalizer)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var dataSources: CacheDataSource.Factory
    private val musicAttrs = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()

    private fun renderers(eq: EqProcessor, loudness: LoudnessProcessor?): DefaultRenderersFactory =
        object : DefaultRenderersFactory(this@PlaybackService) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(if (loudness != null) arrayOf<AudioProcessor>(loudness, eq) else arrayOf<AudioProcessor>(eq))
                    .build()
        }

    private fun applyEq(p: EqProcessor) {
        val e = ServiceLocator.eq.state.value
        p.update(e.gains, e.enabled && !ServiceLocator.playback.state.value.audiophile)
    }

    private fun newTailPlayer(): ExoPlayer {
        val eq = EqProcessor().also { eqProcessors += it; applyEq(it) }
        return ExoPlayer.Builder(this, renderers(eq, null))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setAudioAttributes(musicAttrs, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val c = SimpleCache(
            File(cacheDir, "audio"), LeastRecentlyUsedCacheEvictor(64L * 1024 * 1024), StandaloneDatabaseProvider(this),
        )
        cache = c
        dataSources = CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(ServiceLocator.http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val eqMain = EqProcessor().also { eqProcessors += it }
        val extractors = MeteringExtractorsFactory(DefaultExtractorsFactory(), ServiceLocator.bitrate)
        val player = ExoPlayer.Builder(this, renderers(eqMain, loudnessProcessor))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources, extractors))
            .setAudioAttributes(musicAttrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Tapping the notification or lock-screen card opens Melodyne straight on the Now Playing screen.
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val s = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
        session = s
        QualityMonitor(player) { q -> s.setSessionExtras(q.toBundle()) }.attach()
        Scrobbling(player, scope, ServiceLocator.lastFm).attach()
        engine = CrossfadeEngine(player, { newTailPlayer() }, ServiceLocator.playback, ServiceLocator.normalizer, scope).also { it.attach() }

        // Audiophile Mode bypasses every processor; otherwise the EQ and normalisation follow the user's settings.
        scope.launch {
            combine(ServiceLocator.eq.state, ServiceLocator.playback.state) { e, p -> e to p }.collect { (e, p) ->
                eqProcessors.forEach { it.update(e.gains, e.enabled && !p.audiophile) }
                ServiceLocator.normalizer.enabled = p.normalizeEffective
                OutputInspector.applyBitPerfect(this@PlaybackService, p.audiophile)
            }
        }
        // The song id lets the normaliser remember a measured gain per song.
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                ServiceLocator.normalizer.currentKey = mediaItem?.mediaId
            }
        })
        ServiceLocator.normalizer.currentKey = player.currentMediaItem?.mediaId
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        session?.run { player.release(); release() }
        session = null
        cache?.release()
        cache = null
        scope.cancel()
        super.onDestroy()
    }
}
