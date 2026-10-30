package org.melodyne.app.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.melodyne.app.ai.Suggestion
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText

/** Turns "title + artist" pairs (from Last.fm) into playable catalogue songs. Strict: a wrong song is worse than a missing one. */
class TrackResolver(private val repo: MusicRepository) {
    suspend fun resolve(items: List<Suggestion>): List<Song> {
        val gate = Semaphore(4)
        return coroutineScope {
            items.map { s ->
                async { gate.withPermit { runCatching { pick(repo.search("${s.title} ${s.artist}".trim()), s) }.getOrNull() } }
            }.awaitAll().filterNotNull().distinctBy { it.id }
        }
    }

    private fun pick(results: List<Song>, s: Suggestion): Song? {
        val title = TitleText.key(s.title)
        val artists = TitleText.artistKeys(s.artist)
        fun artistOk(song: Song) = artists.isEmpty() || TitleText.artistKeys(song.artist).intersect(artists).isNotEmpty()
        return results.firstOrNull { TitleText.key(it.title) == title && artistOk(it) }
            ?: results.firstOrNull { TitleText.key(it.title).contains(title) && artistOk(it) }
    }
}
