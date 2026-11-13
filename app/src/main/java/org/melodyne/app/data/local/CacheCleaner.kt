package org.melodyne.app.data.local

import android.content.Context
import android.os.Environment
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import coil.imageLoader
import org.melodyne.app.BuildConfig
import org.melodyne.app.data.update.UpdateChecker
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.StorageText
import java.io.File

/**
 * Keeps Vina light: the cache (artwork, buffered audio, Last.fm lookups) and finished update downloads are deleted when
 * the app closes. Your library, playlists and settings live elsewhere and are never touched.
 */
@UnstableApi
object CacheCleaner {
    private const val PREF = "clear_cache_exit"
    private const val SWEEP_AT = 20L * 1024 * 1024

    fun enabled(): Boolean = ServiceLocator.prefs.getBoolean(PREF, true)
    fun setEnabled(on: Boolean) { ServiceLocator.prefs.edit().putBoolean(PREF, on).apply() }

    /** Called by the playback service once it has shut down (after it released the audio cache). */
    fun clearIfEnabled(ctx: Context) { if (enabled()) runCatching { clear(ctx) } }

    /** Safety net for when Android ended the app without telling it: tidy up at the next start if a lot was left behind. */
    fun sweepOnStart(ctx: Context) { if (enabled() && sizeBytes(ctx) > SWEEP_AT) runCatching { clear(ctx) } }

    fun sizeBytes(ctx: Context): Long =
        ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } + (apkDir(ctx)?.listFiles()?.sumOf { it.length() } ?: 0L)

    /** Clears everything it safely can and returns the bytes freed. */
    fun clear(ctx: Context): Long {
        val before = sizeBytes(ctx)
        runCatching { ctx.imageLoader.memoryCache?.clear(); ctx.imageLoader.diskCache?.clear() }       // artwork, through Coil itself
        val audio = File(ctx.cacheDir, "audio")
        ctx.cacheDir.listFiles()?.forEach { f ->
            when (f.name) {
                "image_cache" -> Unit
                "audio" -> if (!SimpleCache.isCacheFolderLocked(audio)) f.deleteRecursively()       // never under a player that is using it
                else -> f.deleteRecursively()
            }
        }
        pruneInstalledApks(ctx)
        return (before - sizeBytes(ctx)).coerceAtLeast(0L)
    }

    private fun apkDir(ctx: Context): File? = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

    /** Update downloads that are already installed (or not recognisable) go; a newer one still waiting to be installed stays. */
    private fun pruneInstalledApks(ctx: Context) {
        apkDir(ctx)?.listFiles()?.forEach { f ->
            val v = StorageText.apkVersion(f.name)
            if (v == null || !UpdateChecker.isNewer(v, BuildConfig.VERSION_NAME)) f.delete()
        }
    }
}
