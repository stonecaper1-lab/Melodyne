package org.melodyne.app.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.melodyne.app.ai.Suggestion
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song

data class Mood(val label: String, val tags: List<String>, val fallbackQuery: String)

/**
 * A mood is a set of Last.fm tags, not a search word. Songs come from tag.getTopTracks (ranked by tag weight) and are then
 * matched to the catalogue, so a song is never picked just because "chill" appears in its title. Without a Last.fm key the
 * old search is used as a fallback, with any title that contains the mood word thrown out.
 */
class MoodService(
    private val enrich: EnrichmentService,
    private val resolver: TrackResolver,
    private val repo: MusicRepository,
) {
    suspend fun songs(label: String): List<Song> {
        val mood = ALL.firstOrNull { it.label == label } ?: return emptyList()
        val banned = mood.tags + mood.label + mood.fallbackQuery.split(" ")
        fun clean(list: List<Song>) = list.filter { s -> banned.none { w -> w.length > 2 && s.title.contains(w, ignoreCase = true) } }

        if (enrich.available) {
            val lists = coroutineScope { mood.tags.map { t -> async { enrich.tagTopTracks(t, 30) } }.awaitAll() }
            val mixed = ArrayList<Suggestion>()                                    // interleave so no single tag dominates
            for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) for (l in lists) l.getOrNull(i)?.let { mixed += it }
            val found = clean(resolver.resolve(mixed.distinctBy { it.title.lowercase() + it.artist.lowercase() }.take(45)))
            if (found.size >= 5) return found.take(25)
        }
        return clean(repo.search(mood.fallbackQuery)).take(25)
    }

    companion object {
        val ALL = listOf(
            Mood("Chill", listOf("chillout", "lofi", "downtempo"), "chill lofi hindi"),
            Mood("Workout", listOf("workout", "gym", "energetic"), "workout gym hits"),
            Mood("Romance", listOf("romantic", "love songs", "love"), "romantic hindi songs"),
            Mood("Party", listOf("party", "dance", "club"), "party dance hits"),
            Mood("Focus", listOf("instrumental", "ambient", "study"), "instrumental focus"),
            Mood("Retro", listOf("oldies", "classic", "60s"), "old hindi classics"),
            Mood("Sufi", listOf("sufi", "qawwali"), "sufi"),
            Mood("Punjabi", listOf("punjabi", "bhangra"), "punjabi hits"),
            Mood("Devotional", listOf("devotional", "bhajan", "spiritual"), "bhajan devotional"),
            Mood("Sad", listOf("sad", "melancholy", "heartbreak"), "sad hindi songs"),
        )
    }
}
