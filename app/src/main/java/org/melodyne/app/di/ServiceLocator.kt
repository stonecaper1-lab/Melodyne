package org.melodyne.app.di

import android.app.Application
import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.melodyne.app.BuildConfig
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.data.lastfm.LastFmApi
import org.melodyne.app.data.lastfm.LastFmClient
import org.melodyne.app.data.lastfm.LastFmReadApi
import org.melodyne.app.data.lastfm.SessionStore
import org.melodyne.app.data.local.LibraryStore
import org.melodyne.app.data.update.UpdateChecker
import org.melodyne.app.data.lyrics.LrcLibApi
import org.melodyne.app.data.lyrics.LrcLibRepository
import org.melodyne.app.data.local.RecentStore
import org.melodyne.app.ai.AiClient
import org.melodyne.app.ai.AiConfigStore
import org.melodyne.app.audio.EqController
import org.melodyne.app.audio.NormalizerController
import org.melodyne.app.audio.PlaybackSettings
import org.melodyne.app.data.catalog.CatalogApi
import org.melodyne.app.data.catalog.CatalogRepository
import org.melodyne.app.data.catalog.Source
import org.melodyne.app.data.remote.HostedApi
import org.melodyne.app.data.repo.ChainRepository
import org.melodyne.app.data.repo.HostStore
import org.melodyne.app.data.repo.HostedRepository
import org.melodyne.app.data.repo.MoodService
import org.melodyne.app.data.repo.TrackResolver
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.player.PlayerConnection
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Hand-rolled DI: no Hilt/Dagger/KSP, which keeps both build time and APK size down. */
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

object ServiceLocator {
    lateinit var app: Application
        private set

    fun init(application: Application) { app = application }

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    val prefs by lazy { app.getSharedPreferences("melodyne", Context.MODE_PRIVATE) }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", BROWSER_UA).build() else req)
            }
            .build()
    }

    private fun retrofit(base: String) = Retrofit.Builder()
        .baseUrl(base).client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val hosts by lazy { HostStore(prefs) }
    val repo: MusicRepository by lazy {
        // baseUrl is a placeholder: every call passes a full @Url chosen by HostStore.
        val hosted = HostedRepository(retrofit("https://localhost/").create(HostedApi::class.java), hosts)
        val catalog = CatalogRepository(retrofit(Source.base).create(CatalogApi::class.java), json)
        // A custom source the user configured wins; the built-in catalogue is always the fallback.
        ChainRepository { if (hosts.custom() != null) listOf(hosted, catalog) else listOf(catalog) }
    }
    private val lastFmRetrofit by lazy { retrofit("https://ws.audioscrobbler.com/") }
    val lastFm by lazy {
        LastFmClient(
            lastFmRetrofit.create(LastFmApi::class.java),
            SessionStore(prefs), BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_API_SECRET,
        )
    }
    val enrichment by lazy { EnrichmentService(lastFmRetrofit.create(LastFmReadApi::class.java), { lastFm.currentKey() }, app.cacheDir, json) }
    val resolver by lazy { TrackResolver(repo) }
    val moods by lazy { MoodService(enrichment, resolver, repo) }
    val lrclib by lazy { LrcLibRepository(retrofit("https://lrclib.net/").create(LrcLibApi::class.java)) }
    val updates by lazy { UpdateChecker(http, json, prefs, BuildConfig.VERSION_NAME) }
    val recents by lazy { RecentStore(prefs, json) }
    val library by lazy { LibraryStore(app.filesDir, json, prefs) }
    val eq by lazy { EqController(prefs) }
    val playback by lazy { PlaybackSettings(prefs) }
    val normalizer by lazy { NormalizerController(prefs) }
    val aiConfig by lazy { AiConfigStore(prefs) }
    val ai by lazy { AiClient(http, json) }
    val player by lazy { PlayerConnection(app) }
}
