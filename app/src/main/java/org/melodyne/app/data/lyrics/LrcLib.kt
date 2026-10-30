package org.melodyne.app.data.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.melodyne.app.domain.LrcParser
import org.melodyne.app.domain.Lyrics
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@Serializable
data class LrcLibResult(val syncedLyrics: String? = null, val plainLyrics: String? = null)

/** LRCLIB: a free, open lyrics database that carries time-synced (LRC) lyrics. */
interface LrcLibApi {
    @GET("api/get")
    suspend fun get(
        @Query("track_name") track: String, @Query("artist_name") artist: String,
        @Query("album_name") album: String, @Query("duration") duration: Int,
        @Header("User-Agent") ua: String,
    ): LrcLibResult

    @GET("api/search")
    suspend fun search(
        @Query("track_name") track: String, @Query("artist_name") artist: String,
        @Header("User-Agent") ua: String,
    ): List<LrcLibResult>
}

class LrcLibRepository(private val api: LrcLibApi) {
    suspend fun lyrics(song: Song): Lyrics? {
        val title = TitleText.strip(song.title)
        val artist = TitleText.primaryArtist(song.artist)
        val exact = try {
            api.get(title, artist, song.album, song.durationSec, UA)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val hit = exact?.takeIf { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            ?: try {
                val all = api.search(title, artist, UA)
                all.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: all.firstOrNull { !it.plainLyrics.isNullOrBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        val text = hit?.syncedLyrics?.takeIf { it.isNotBlank() } ?: hit?.plainLyrics?.takeIf { it.isNotBlank() } ?: return null
        return LrcParser.parse(text)
    }

    private companion object { const val UA = "Melodyne/0.5 (Android; free software)" }
}
