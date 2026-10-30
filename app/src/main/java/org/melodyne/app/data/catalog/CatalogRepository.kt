package org.melodyne.app.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.melodyne.app.data.repo.ApiUnavailable
import org.melodyne.app.domain.LrcParser
import org.melodyne.app.domain.Lyrics
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Playlist
import org.melodyne.app.domain.PlaylistInfo
import org.melodyne.app.domain.Song
import retrofit2.HttpException
import java.io.IOException

/**
 * The built-in music source. Streams: decrypt the stored media URL, then ask for the 320 kbps variant.
 * No middle-man server that can run out of quota (402) or disappear.
 */
class CatalogRepository(private val api: CatalogApi, private val json: Json) : MusicRepository {

    private suspend fun get(vararg extra: Pair<String, String>): String {
        val params = mapOf("_format" to "json", "_marker" to "0", "api_version" to "4", "ctx" to "web6dot0") + extra
        try {
            return withContext(Dispatchers.IO) { api.call(params).string() }
        } catch (e: HttpException) {
            throw ApiUnavailable(e, listOf("source: HTTP ${e.code()}"))
        } catch (e: IOException) {
            throw ApiUnavailable(e, listOf("source: ${e.javaClass.simpleName} ${e.message.orEmpty().take(60)}"))
        }
    }

    private fun <T> parse(text: String, ser: KSerializer<T>): T = try {
        json.decodeFromString(ser, text)
    } catch (e: SerializationException) {
        throw ApiUnavailable(e, listOf("source: unexpected reply \"${text.take(50).replace('\n', ' ')}\""))
    } catch (e: IllegalArgumentException) {
        throw ApiUnavailable(e, listOf("source: unexpected reply \"${text.take(50).replace('\n', ' ')}\""))
    }

    override suspend fun search(query: String): List<Song> {
        val r = parse(get("__call" to "search.getResults", "q" to query, "p" to "1", "n" to "40"), CSearch.serializer())
        return r.results.mapNotNull { it.toSong() }
    }

    override suspend fun searchPlaylists(query: String): List<PlaylistInfo> {
        val r = parse(get("__call" to "search.getPlaylistResults", "q" to query, "p" to "1", "n" to "12"), CPlaylistSearch.serializer())
        return r.results.map { PlaylistInfo(it.id, it.title.clean(), it.image.big()) }
    }

    override suspend fun playlist(id: String): Playlist {
        val r = parse(get("__call" to "playlist.getDetails", "listid" to id, "p" to "1", "n" to "50"), CPlaylist.serializer())
        return Playlist(PlaylistInfo(r.id, r.title.clean(), r.image.big()), r.list.mapNotNull { it.toSong() })
    }

    override suspend fun lyrics(songId: String): Lyrics? = try {
        val r = parse(get("__call" to "lyrics.getLyrics", "lyrics_id" to songId), CLyrics.serializer())
        r.lyrics?.takeIf { it.isNotBlank() }?.let { LrcParser.parse(it) }
    } catch (e: ApiUnavailable) {
        null
    }
}

private fun String.clean() = replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
private fun String.big() = replace("150x150", "500x500").replace("50x50", "500x500").takeIf { it.isNotBlank() }

private fun CSong.toSong(): Song? {
    val m = more ?: return null
    val base = StreamCrypto.decrypt(m.encryptedUrl ?: return null) ?: return null
    // 320 kbps exists only when the song is flagged for it; otherwise take the next best.
    val kbps = if (m.kbps320 == "true") 320 else 160
    val artists = m.artistMap?.primary?.takeIf { it.isNotEmpty() } ?: m.artistMap?.artists.orEmpty()
    return Song(
        id = id,
        title = title.clean(),
        artist = artists.joinToString(", ") { it.name.clean() }.ifBlank { subtitle.substringBefore(" - ").clean() },
        album = m.album.clean(),
        durationSec = m.duration?.toIntOrNull() ?: 0,
        artUrl = image.big(),
        thumbUrl = image.takeIf { it.isNotBlank() },
        streamUrl = StreamCrypto.withBitrate(base, kbps),
        streamKbps = kbps,
        hasLyrics = m.hasLyrics == "true",
    )
}
