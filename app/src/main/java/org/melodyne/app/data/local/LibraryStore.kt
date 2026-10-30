package org.melodyne.app.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.melodyne.app.domain.LibraryBackup
import org.melodyne.app.domain.LibraryData
import org.melodyne.app.domain.LibraryOps
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.UserPlaylist
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Playlists and listening history in their own file inside app storage (not the cache), so
 * "Clear cache" can't touch them. Writes are atomic (temp file, then rename) and the previous
 * version is kept as library.bak.json; a damaged main file falls back to it automatically.
 * The file is also opted into Android's own backup (see backup_rules.xml).
 */
class LibraryStore(private val dir: File, private val json: Json, legacy: SharedPreferences) {
    private val main = File(dir, "library.json")
    private val backup = File(dir, "library.bak.json")
    private val writer = Executors.newSingleThreadExecutor()
    private val _data = MutableStateFlow(load(legacy))
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    private fun read(f: File): LibraryData? = runCatching {
        if (f.exists()) json.decodeFromString(LibraryData.serializer(), f.readText()) else null
    }.getOrNull()

    private fun load(legacy: SharedPreferences): LibraryData {
        var d = read(main) ?: read(backup) ?: LibraryData()
        val old = legacy.getString("saved_playlists", null)
        if (old != null) {
            val mixes = runCatching { json.decodeFromString(ListSerializer(SavedPlaylist.serializer()), old) }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            d = d.copy(playlists = d.playlists + mixes.map { UserPlaylist(it.id, it.name, it.id.toLongOrNull() ?: now, now, it.songs, "ai") })
            legacy.edit().remove("saved_playlists").apply()
            persist(d)
        }
        return d
    }

    private fun persist(d: LibraryData) {
        val text = json.encodeToString(LibraryData.serializer(), d)
        writer.execute {
            runCatching {
                dir.mkdirs()
                val tmp = File(dir, "library.tmp")
                tmp.writeText(text)
                if (main.exists()) main.copyTo(backup, overwrite = true)
                if (!tmp.renameTo(main)) { main.writeText(text); tmp.delete() }
            }
        }
    }

    @Synchronized
    private fun change(block: (LibraryData) -> LibraryData) {
        val n = block(_data.value)
        _data.value = n
        persist(n)
    }

    private fun edit(id: String, block: (UserPlaylist) -> UserPlaylist) = change { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) block(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun create(name: String, songs: List<Song> = emptyList(), origin: String = ""): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        change { it.copy(playlists = listOf(UserPlaylist(id, name.trim(), now, now, LibraryOps.addSongs(emptyList(), songs), origin)) + it.playlists) }
        return id
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim()) }
    fun delete(id: String) = change { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    /** Returns how many songs were actually added (duplicates are skipped). */
    fun addSongs(id: String, songs: List<Song>): Int {
        val before = _data.value.playlists.firstOrNull { it.id == id }?.songs?.size ?: return 0
        edit(id) { it.copy(songs = LibraryOps.addSongs(it.songs, songs)) }
        return (_data.value.playlists.firstOrNull { it.id == id }?.songs?.size ?: before) - before
    }

    fun removeAt(id: String, index: Int) = edit(id) { p -> p.copy(songs = p.songs.filterIndexed { i, _ -> i != index }) }
    fun move(id: String, from: Int, to: Int) = edit(id) { it.copy(songs = LibraryOps.move(it.songs, from, to)) }

    fun recordPlay(song: Song) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        change { it.copy(stats = LibraryOps.recordPlay(it.stats, song, now, hour)) }
    }

    fun exportJson(): String = json.encodeToString(
        LibraryBackup.serializer(), LibraryBackup(exportedAt = System.currentTimeMillis(), library = _data.value),
    )

    /** Returns how many playlists were added or updated. */
    fun importJson(text: String): Int {
        val backup = try {
            json.decodeFromString(LibraryBackup.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This isn't a Melodyne library file.")
        }
        require(backup.app == "melodyne") { "This isn't a Melodyne library file." }
        var changed = 0
        change { cur -> val (merged, n) = LibraryOps.merge(cur, backup.library); changed = n; merged }
        return changed
    }
}
