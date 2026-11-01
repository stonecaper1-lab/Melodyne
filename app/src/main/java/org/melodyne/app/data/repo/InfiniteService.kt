package org.melodyne.app.data.repo

import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.data.local.LibraryStore
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText

/**
 * Endless play: when the queue is about to run out, find songs that sound like what just played and queue them.
 * With a Last.fm key it asks for similar tracks and similar artists; without one it falls back to more songs by the
 * same artists. Anything already in the queue or heard recently is skipped.
 */
class InfiniteService(
    private val repo: MusicRepository,
    private val enrich: EnrichmentService,
    private val resolver: TrackResolver,
    private val library: LibraryStore,
) {
    suspend fun next(queue: List<Song>, want: Int = 8): List<Song> {
        val have = queue.map { it.id }.toHashSet()
        val heard = library.data.value.stats.sortedByDescending { it.last }.take(40).map { it.song.id }.toHashSet()
        val seeds = queue.takeLast(3).reversed()
        val found = ArrayList<Song>()

        if (enrich.available) {
            for (seed in seeds) {
                val similar = runCatching { enrich.similarTracks(TitleText.strip(seed.title), TitleText.primaryArtist(seed.artist), 20) }
                    .getOrDefault(emptyList())
                found += resolver.resolve(similar)
                if (found.size >= want * 2) break
            }
            if (found.size < want) {
                for (seed in seeds.take(2)) {
                    val artists = runCatching { enrich.similarArtists(TitleText.primaryArtist(seed.artist), 5) }.getOrDefault(emptyList())
                    for (name in artists) {
                        val key = TitleText.key(name)
                        found += runCatching { repo.search(name) }.getOrDefault(emptyList())
                            .filter { key in TitleText.artistKeys(it.artist) }.take(2)
                    }
                }
            }
        }
        if (found.size < want) {
            for (seed in seeds) {
                val key = TitleText.key(TitleText.primaryArtist(seed.artist))
                found += runCatching { repo.search(TitleText.primaryArtist(seed.artist)) }.getOrDefault(emptyList())
                    .filter { key in TitleText.artistKeys(it.artist) }
            }
        }
        val fresh = found.distinctBy { it.id }.filter { it.id !in have }
        return (fresh.filter { it.id !in heard }.ifEmpty { fresh }).take(want)
    }
}
