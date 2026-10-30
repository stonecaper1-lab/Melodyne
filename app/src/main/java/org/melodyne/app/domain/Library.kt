package org.melodyne.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<Song> = emptyList(),
    /** "", "ai" or "smart": where the playlist came from. */
    val origin: String = "",
)

/** Per-song listening history, the raw material for smart playlists. */
@Serializable
data class PlayStat(
    val song: Song,
    val plays: Int,
    val first: Long,
    val last: Long,
    val night: Int = 0,
    val morning: Int = 0,
    val afternoon: Int = 0,
    val evening: Int = 0,
)

@Serializable
data class LibraryData(val playlists: List<UserPlaylist> = emptyList(), val stats: List<PlayStat> = emptyList())

@Serializable
data class LibraryBackup(val app: String = "melodyne", val version: Int = 1, val exportedAt: Long = 0, val library: LibraryData = LibraryData())

data class SmartList(val id: String, val name: String, val blurb: String, val songs: List<Song>)

object LibraryOps {
    const val MAX_STATS = 500

    fun addSongs(list: List<Song>, add: List<Song>): List<Song> {
        val have = list.map { it.id }.toHashSet()
        return list + add.filter { have.add(it.id) }
    }

    fun move(list: List<Song>, from: Int, to: Int): List<Song> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }

    private fun PlayStat.inSlot(hour: Int): PlayStat = when {
        hour >= 22 || hour < 5 -> copy(night = night + 1)
        hour < 12 -> copy(morning = morning + 1)
        hour < 17 -> copy(afternoon = afternoon + 1)
        else -> copy(evening = evening + 1)
    }

    /** Counts one listen. Most recently played first; capped so the file stays small. */
    fun recordPlay(stats: List<PlayStat>, song: Song, now: Long, hour: Int): List<PlayStat> {
        val old = stats.firstOrNull { it.song.id == song.id }
        val updated = (old?.copy(song = song, plays = old.plays + 1, last = now) ?: PlayStat(song, 1, now, now)).inSlot(hour)
        return (listOf(updated) + stats.filter { it.song.id != song.id }).take(MAX_STATS)
    }

    /** Merges an imported library into the current one. Returns the result and how many playlists were added or updated. */
    fun merge(current: LibraryData, incoming: LibraryData): Pair<LibraryData, Int> {
        var changed = 0
        val byId = current.playlists.associateBy { it.id }.toMutableMap()
        val order = current.playlists.map { it.id }.toMutableList()
        for (p in incoming.playlists) {
            val ex = byId[p.id]
            if (ex == null) { byId[p.id] = p; order += p.id; changed++ }
            else if (p.updatedAt > ex.updatedAt) { byId[p.id] = p; changed++ }
        }
        val statsById = current.stats.associateBy { it.song.id }.toMutableMap()
        for (s in incoming.stats) {
            val ex = statsById[s.song.id]
            if (ex == null || s.plays > ex.plays) statsById[s.song.id] = s
        }
        val stats = statsById.values.sortedByDescending { it.last }.take(MAX_STATS)
        return LibraryData(order.mapNotNull { byId[it] }, stats) to changed
    }
}

/** Playlists that build themselves from what you play and from track data (length). */
object SmartLists {
    private const val DAY = 86_400_000L

    fun build(stats: List<PlayStat>, now: Long = System.currentTimeMillis()): List<SmartList> {
        if (stats.isEmpty()) return emptyList()
        val out = ArrayList<SmartList>()
        fun add(id: String, name: String, blurb: String, songs: List<Song>, min: Int) {
            if (songs.size >= min) out += SmartList(id, name, blurb, songs.take(50))
        }
        val byPlays = stats.sortedWith(compareByDescending<PlayStat> { it.plays }.thenByDescending { it.last })
        fun slot(sel: (PlayStat) -> Int): List<Song> = byPlays.filter {
            val total = it.night + it.morning + it.afternoon + it.evening
            total > 0 && sel(it) >= 2 && sel(it) * 2 >= total          // at least half of this song's plays in the slot
        }.map { it.song }

        add("most", "Most played", "Your heaviest rotation.", byPlays.map { it.song }, 1)
        add("recent", "Recently played", "Pick up where you left off.", stats.sortedByDescending { it.last }.map { it.song }, 1)
        add("rediscover", "Rediscover", "Songs you played a lot, but not for two weeks.",
            byPlays.filter { it.plays >= 2 && now - it.last > 14 * DAY }.map { it.song }, 3)
        add("night", "Night owl", "What you play after dark.", slot { it.night }, 3)
        add("morning", "Morning starters", "What you reach for early.", slot { it.morning }, 3)
        add("short", "Short and sweet", "Quick hits under 3½ minutes.", byPlays.filter { it.song.durationSec in 1..209 }.map { it.song }, 3)
        add("long", "Long listens", "Six minutes and beyond.", byPlays.filter { it.song.durationSec >= 360 }.map { it.song }, 3)

        val top = stats.groupBy { TitleText.primaryArtist(it.song.artist) }
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { (_, v) -> v.sumOf { it.plays } }
        if (top != null) add("artist", "More of ${top.key}", "Your most played artist.", top.value.sortedByDescending { it.plays }.map { it.song }, 3)
        return out
    }
}
