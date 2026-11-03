package org.melodyne.app.data.repo

import android.content.SharedPreferences
import org.melodyne.app.ai.Suggestion
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song

data class ChartLang(val label: String, val tags: List<String>)
data class ChartEntry(val song: Song, /** "NEW", "▲2", "▼1", "–" or "" (below the top ten). */ val move: String)

/**
 * A Top 10 for every language. With a Last.fm key each chart is Last.fm's own ranking (country chart, world chart or
 * language tags) matched to the catalogue and cached for only 3 hours, so it keeps moving. Arrows compare with a
 * snapshot taken about every 12 hours. Without a key it falls back to a catalogue search.
 */
class ChartService(
    private val enrich: EnrichmentService,
    private val resolver: TrackResolver,
    private val repo: MusicRepository,
    private val prefs: SharedPreferences,
) {
    suspend fun top(label: String): List<ChartEntry> = withMovement(label, fetch(label))

    private suspend fun fetch(label: String): List<Song> {
        val lang = LANGS.firstOrNull { it.label == label } ?: LANGS.first()
        if (enrich.available) {
            val tracks: List<Suggestion> = when (label) {
                "All" -> enrich.geoTopTracks("india", 30, CHART_TTL)
                "English" -> enrich.globalTopTracks(40, CHART_TTL)
                else -> {
                    val lists = lang.tags.map { runCatching { enrich.tagTopTracks(it, 30, CHART_TTL) }.getOrDefault(emptyList()) }
                    val mixed = ArrayList<Suggestion>()
                    for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) for (l in lists) l.getOrNull(i)?.let { mixed += it }
                    mixed
                }
            }
            val songs = resolver.resolve(tracks.distinctBy { it.title.lowercase() + it.artist.lowercase() }.take(40))
            if (songs.size >= 6) return songs.take(25)
        }
        return repo.search(if (label == "All") "top hits" else "$label top songs").take(25)
    }

    private fun withMovement(label: String, songs: List<Song>): List<ChartEntry> {
        val key = "chart_$label"
        val ref = prefs.getString("$key.ids", "").orEmpty().split(",").filter { it.isNotBlank() }
        val top = songs.take(10)
        val entries = top.mapIndexed { i, s ->
            val before = ref.indexOf(s.id)
            ChartEntry(
                s, when {
                    ref.isEmpty() -> "–"
                    before < 0 -> "NEW"
                    before > i -> "▲${before - i}"
                    before < i -> "▼${i - before}"
                    else -> "–"
                },
            )
        }
        val now = System.currentTimeMillis()
        if (ref.isEmpty() || now - prefs.getLong("$key.at", 0L) > REF_EVERY) {
            prefs.edit().putString("$key.ids", top.joinToString(",") { it.id }).putLong("$key.at", now).apply()
        }
        return entries + songs.drop(10).map { ChartEntry(it, "") }
    }

    companion object {
        private const val CHART_TTL = 3L * 3600 * 1000
        private const val REF_EVERY = 12L * 3600 * 1000
        val LANGS = listOf(
            ChartLang("All", emptyList()), ChartLang("Hindi", listOf("hindi", "bollywood")), ChartLang("English", emptyList()),
            ChartLang("Punjabi", listOf("punjabi")), ChartLang("Tamil", listOf("tamil")), ChartLang("Telugu", listOf("telugu")),
            ChartLang("Malayalam", listOf("malayalam")), ChartLang("Kannada", listOf("kannada")), ChartLang("Bengali", listOf("bengali")),
            ChartLang("Marathi", listOf("marathi")), ChartLang("Gujarati", listOf("gujarati")), ChartLang("Bhojpuri", listOf("bhojpuri")),
            ChartLang("Haryanvi", listOf("haryanvi")), ChartLang("Urdu", listOf("urdu")), ChartLang("K-Pop", listOf("k-pop")),
            ChartLang("Spanish", listOf("spanish", "latin")), ChartLang("Japanese", listOf("j-pop", "japanese")), ChartLang("Arabic", listOf("arabic")),
        )
    }
}
