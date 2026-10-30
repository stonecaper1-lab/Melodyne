package org.melodyne.app.data.repo

import android.content.SharedPreferences
import android.util.LruCache
import kotlinx.serialization.SerializationException
import org.melodyne.app.data.remote.Envelope
import org.melodyne.app.data.remote.HostedApi
import org.melodyne.app.data.remote.SongDto
import org.melodyne.app.data.remote.UrlDto
import org.melodyne.app.domain.LrcParser
import org.melodyne.app.domain.Lyrics
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Playlist
import org.melodyne.app.domain.PlaylistInfo
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.StreamPicker
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Ordered list of API hosts: the user's own instance first, then built-ins. */
class HostStore(private val prefs: SharedPreferences) {
    fun custom(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    /** Accepts https URLs only (cleartext is disabled). Empty clears the override. */
    fun setCustom(url: String?): Boolean {
        val v = url?.trim()?.trimEnd('/').orEmpty()
        if (v.isNotEmpty() && !v.startsWith("https://")) return false
        prefs.edit().putString(KEY, v).apply()
        return true
    }

    fun ordered(): List<String> = listOfNotNull(custom()) + DEFAULT_HOSTS

    companion object {
        private const val KEY = "api_host"
        val DEFAULT_HOSTS = emptyList<String>()
    }
}

class ApiUnavailable(cause: Throwable?, val details: List<String> = emptyList()) : IOException("Music source unavailable", cause) {
    val userMessage: String
        get() {
            val c = cause
            val head = when {
                c is HttpException && c.code() == 402 ->
                    "The custom source answered 402 (quota or payment). Check it in Settings."
                c is HttpException -> "Source error ${c.code()}. Retry, or change the custom source in Settings."
                else -> "Can't reach the music source. Check your connection."
            }
            return if (details.isEmpty()) head else head + "\n\n" + details.distinct().take(4).joinToString("\n")
        }
}

class HostedRepository(private val api: HostedApi, private val hosts: HostStore) : MusicRepository {
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val cache = LruCache<String, Any>(48)

    /**
     * Runs [block] against each healthy host. HTTP 402/429/5xx and I/O failures put the host on
     * cooldown and fall through to the next one; if all fail, the last good cached answer is used.
     */
    private suspend fun <T : Any> call(key: String, block: suspend (host: String) -> Envelope<T>): T {
        val all = hosts.ordered()
        val now = System.currentTimeMillis()
        val candidates = all.filter { (cooldownUntil[it] ?: 0L) <= now }.ifEmpty { all }
        var last: Throwable? = null
        val details = ArrayList<String>()
        for (host in candidates) {
            val name = host.removePrefix("https://").substringBefore('/')
            try {
                val env = block(host)
                val data = env.data
                if (env.success && data != null) {
                    cache.put(key, data)
                    return data
                }
                last = IOException(env.message ?: "Empty response")
                details += "$name: ${env.message ?: "empty reply"}"
            } catch (e: HttpException) {
                last = e
                details += "$name: HTTP ${e.code()}"
                if (e.code() == 402 || e.code() == 429 || e.code() >= 500) cooldownUntil[host] = now + COOLDOWN_MS
            } catch (e: IOException) {
                last = e
                details += "$name: ${e.javaClass.simpleName} ${e.message.orEmpty().take(60)}"
                cooldownUntil[host] = now + 60_000
            } catch (e: SerializationException) {
                last = e
                details += "$name: unexpected reply format"
            }
        }
        @Suppress("UNCHECKED_CAST")
        (cache.get(key) as T?)?.let { return it }
        throw ApiUnavailable(last, details)
    }

    override suspend fun search(query: String): List<Song> =
        call("s:$query") { h -> api.searchSongs("$h/search/songs", query, 0, 40) }
            .results.mapNotNull { it.toSong() }

    override suspend fun searchPlaylists(query: String): List<PlaylistInfo> =
        call("sp:$query") { h -> api.searchPlaylists("$h/search/playlists", query, 0, 12) }
            .results.map { PlaylistInfo(it.id, it.name.clean(), it.image.bestArt()) }

    override suspend fun playlist(id: String): Playlist {
        val d = call("p:$id") { h -> api.playlist("$h/playlists", id, 0, 50) }
        return Playlist(PlaylistInfo(d.id, d.name.clean(), d.image.bestArt()), d.songs.mapNotNull { it.toSong() })
    }

    override suspend fun lyrics(songId: String): Lyrics? = try {
        val d = call("l:$songId") { h -> api.lyrics("$h/songs/$songId/lyrics") }
        d.lyrics?.takeIf { it.isNotBlank() }?.let { LrcParser.parse(it) }
    } catch (e: ApiUnavailable) {
        null // lyrics are optional; never block playback
    }

    private companion object { const val COOLDOWN_MS = 5 * 60_000L }
}

private fun String.clean() = replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")

private fun List<UrlDto>.bestArt(): String? = lastOrNull { it.url.isNotBlank() }?.url

private fun SongDto.toSong(): Song? {
    // Force the highest bitrate in downloadUrl (320kbps when present).
    val (url, kbps) = StreamPicker.pick(downloadUrl.map { it.quality to it.url }) ?: return null
    val names = artists?.primary?.takeIf { it.isNotEmpty() } ?: artists?.all.orEmpty()
    return Song(
        id = id,
        title = name.clean(),
        artist = names.joinToString(", ") { it.name.clean() },
        album = album?.name?.clean().orEmpty(),
        durationSec = duration ?: 0,
        artUrl = image.bestArt(),
        thumbUrl = image.firstOrNull { it.quality.startsWith("150") }?.url ?: image.firstOrNull()?.url,
        streamUrl = url,
        streamKbps = kbps,
        hasLyrics = hasLyrics,
    )
}
