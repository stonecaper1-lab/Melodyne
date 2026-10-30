package org.melodyne.app.domain

import kotlin.math.abs

object TitleText {
    private val decoration = Regex("""\s*[(\[]\s*(?:from|feat\.?|ft\.?|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
    private val dashFrom = Regex("""\s+-\s+from\b.*$""", RegexOption.IGNORE_CASE)

    /** Removes film/feature decorations: `Tum Hi Ho (From "Aashiqui 2")` -> `Tum Hi Ho`. Remix/lofi/reprise tags stay. */
    fun strip(t: String): String = t.replace(decoration, "").replace(dashFrom, "").trim().ifBlank { t.trim() }
    fun key(t: String): String = strip(t).lowercase().filter { it.isLetterOrDigit() }
    fun artistKeys(artist: String): Set<String> = artist.split(",", "&").map { key(it) }.filter { it.isNotEmpty() }.toSet()
    fun primaryArtist(artist: String): String = artist.substringBefore(",").trim()
}

/** Cleans a raw result list: one entry per real song, best matches first. */
object Refine {
    private fun same(a: Song, b: Song): Boolean =
        TitleText.key(a.title) == TitleText.key(b.title) &&
            TitleText.artistKeys(a.artist).intersect(TitleText.artistKeys(b.artist)).isNotEmpty() &&
            abs(a.durationSec - b.durationSec) <= 8

    fun songs(query: String, input: List<Song>, limit: Int = 25): List<Song> {
        val out = ArrayList<Song>()
        for (s in input) {
            val i = out.indexOfFirst { same(it, s) }
            if (i < 0) out += s else if (s.streamKbps > out[i].streamKbps) out[i] = s   // keep the best-quality copy
        }
        val q = TitleText.key(query)
        fun rank(s: Song): Int {
            val t = TitleText.key(s.title)
            return when { q.isEmpty() -> 3; t == q -> 0; t.startsWith(q) -> 1; t.contains(q) -> 2; else -> 3 }
        }
        return out.sortedBy { rank(it) }.take(limit)    // sortedBy is stable: relevance order is kept within a rank
    }
}
