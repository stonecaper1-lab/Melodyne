package org.melodyne.app.data.local

import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.melodyne.app.domain.Song

/** Last 20 played songs, kept as JSON in SharedPreferences. */
class RecentStore(private val prefs: SharedPreferences, private val json: Json) {
    private val ser = ListSerializer(Song.serializer())

    fun all(): List<Song> = runCatching {
        json.decodeFromString(ser, prefs.getString(KEY, null) ?: "[]")
    }.getOrDefault(emptyList())

    fun add(song: Song) {
        val list = (listOf(song) + all().filterNot { it.id == song.id }).take(20)
        prefs.edit().putString(KEY, json.encodeToString(ser, list)).apply()
    }

    private companion object { const val KEY = "recents" }
}
