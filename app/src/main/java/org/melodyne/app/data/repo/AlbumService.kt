package org.melodyne.app.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText

/** "Go to album": the album's own track list when the source gives an id, otherwise songs found by album name. */
class AlbumService(private val repo: MusicRepository) {
    suspend fun tracks(song: Song): List<Song> {
        if (song.albumId.isNotBlank()) {
            val byId = runCatching { repo.albumSongs(song.albumId) }.getOrDefault(emptyList())
            if (byId.isNotEmpty()) return byId
        }
        val key = TitleText.key(song.album)
        val found = runCatching { repo.search("${song.album} ${TitleText.primaryArtist(song.artist)}") }.getOrDefault(emptyList())
            .filter { TitleText.key(it.album) == key }
        return if (found.any { it.id == song.id }) found else listOf(song) + found
    }
}

data class PersonDetail(val name: String, val bio: String?, val tags: List<String>, val songs: List<Song>)

/** "Go to composer": songs credited to that composer, plus what Last.fm says about them. */
class PersonService(private val repo: MusicRepository, private val enrich: EnrichmentService) {
    suspend fun composer(name: String): PersonDetail = coroutineScope {
        val info = async { if (enrich.available) runCatching { enrich.artistInfo(name) }.getOrNull() else null }
        val key = TitleText.key(name)
        val found = runCatching { repo.search(name) }.getOrDefault(emptyList())
        val credited = found.filter { key in TitleText.artistKeys(it.composer) || key in TitleText.artistKeys(it.artist) }
        val (tags, bio) = info.await() ?: (emptyList<String>() to null)
        PersonDetail(name, bio, tags, credited.ifEmpty { found })
    }
}
