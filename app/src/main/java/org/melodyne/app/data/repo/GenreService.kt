package org.melodyne.app.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song
import kotlin.math.ln

data class CloudTag(val name: String, val weight: Float)          // weight 0..1

data class GenreDetail(val tag: String, val summary: String?, val songs: List<Song>, val fromLastFm: Boolean)

/** Last.fm-style genre cloud, plus the page behind each word. */
class GenreService(
    private val enrich: EnrichmentService,
    private val resolver: TrackResolver,
    private val repo: MusicRepository,
) {
    suspend fun cloud(): List<CloudTag> {
        val chart = if (enrich.available) runCatching { enrich.topTags(90) }.getOrDefault(emptyList()) else emptyList()
        return CloudBuilder.build(chart)
    }

    suspend fun detail(tag: String): GenreDetail = coroutineScope {
        val summary = async { if (enrich.available) runCatching { enrich.tagSummary(tag) }.getOrNull() else null }
        if (enrich.available) {
            val songs = resolver.resolve(runCatching { enrich.tagTopTracks(tag, 50) }.getOrDefault(emptyList()))
            if (songs.size >= 5) return@coroutineScope GenreDetail(tag, summary.await(), songs.take(40), true)
        }
        // No Last.fm key (or too few matches): catalogue search, minus songs that only match by title.
        val fallback = runCatching { repo.search(tag) }.getOrDefault(emptyList()).filter { !it.title.contains(tag, ignoreCase = true) }
        GenreDetail(tag, summary.await(), fallback.take(30), false)
    }
}

object CloudBuilder {
    /** Genres that matter here but are small on a global chart. */
    val INDIAN = listOf(
        "bollywood", "hindi", "punjabi", "tamil", "telugu", "sufi", "ghazal", "bhangra", "indian", "filmi", "carnatic",
        "hindustani", "qawwali", "bhajan", "devotional", "indie pop", "lofi", "retro", "romantic", "marathi", "bengali",
        "malayalam", "kannada", "gujarati", "folk",
    )
    private val JUNK = setOf(
        "seen live", "favorites", "favourites", "favorite", "albums i own", "my music", "love", "awesome", "good", "best",
        "beautiful", "amazing", "cool", "spotify", "check out", "female vocalists", "male vocalists", "all", "under 2000 listeners",
    )

    fun build(chart: List<Pair<String, Long>>): List<CloudTag> {
        val clean = chart.map { it.first.trim().lowercase() to it.second }
            .filter { (n, _) -> n.length in 2..22 && n !in JUNK && !n.contains("listeners") && !Regex("\\d{4}").containsMatchIn(n) }
        val max = (clean.maxOfOrNull { it.second } ?: 1L).toDouble()
        val fromChart = clean.map { (n, w) -> CloudTag(n, (ln(w + 1.0) / ln(max + 1.0)).toFloat().coerceIn(0.05f, 1f)) }
        val have = fromChart.map { it.name }.toSet()
        val indian = INDIAN.filter { it !in have }.mapIndexed { i, n -> CloudTag(n, 0.42f + ((i * 7) % 5) * 0.08f) }
        // A fixed, name-based shuffle: the cloud looks organic but never reshuffles between recompositions.
        return (fromChart + indian).distinctBy { it.name }.sortedBy { it.name.hashCode() }
    }

    fun sizeSp(weight: Float): Float = 13f + weight.coerceIn(0f, 1f) * 21f
}
