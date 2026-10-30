package org.melodyne.app.data.repo

import kotlinx.coroutines.CancellationException
import org.melodyne.app.domain.Lyrics
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Playlist
import org.melodyne.app.domain.PlaylistInfo
import org.melodyne.app.domain.Refine
import org.melodyne.app.domain.Song

/** Tries each source in order; keeps every failure so the UI can say exactly what went wrong. */
class ChainRepository(private val sources: () -> List<MusicRepository>) : MusicRepository {

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> first(usable: (T) -> Boolean = { true }, block: suspend (MusicRepository) -> T): T {
        val details = ArrayList<String>()
        var last: Throwable? = null
        var emptyButOk: T? = null
        var gotEmpty = false
        for (src in sources()) {
            try {
                val r = block(src)
                if (usable(r)) return r
                emptyButOk = r; gotEmpty = true
            } catch (e: ApiUnavailable) {
                last = e.cause ?: e; details += e.details
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e; details += "${e.javaClass.simpleName}: ${e.message.orEmpty().take(60)}"
            }
        }
        if (gotEmpty) return emptyButOk as T
        throw ApiUnavailable(last, details)
    }

    override suspend fun search(query: String): List<Song> =
        Refine.songs(query, first({ it: List<Song> -> it.isNotEmpty() }) { it.search(query) })

    override suspend fun searchPlaylists(query: String): List<PlaylistInfo> = first({ it.isNotEmpty() }) { it.searchPlaylists(query) }
    override suspend fun playlist(id: String): Playlist = first({ it.songs.isNotEmpty() }) { it.playlist(id) }

    override suspend fun lyrics(songId: String): Lyrics? {
        for (src in sources()) {
            try { src.lyrics(songId)?.let { return it } } catch (e: CancellationException) { throw e } catch (ignored: Exception) {}
        }
        return null
    }
}
