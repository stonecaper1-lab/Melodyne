package org.melodyne.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val artUrl: String?,
    val thumbUrl: String?,
    val streamUrl: String,
    /** Bitrate the API advertised for [streamUrl]. The player later verifies it. */
    val streamKbps: Int,
    val hasLyrics: Boolean,
    val albumId: String = "",
    /** Music director(s), comma-separated. Empty when the source doesn't list one. */
    val composer: String = "",
)

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val lines: List<LyricLine>, val plain: String) {
    val synced: Boolean get() = lines.size >= 3
}

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    suspend fun lyrics(songId: String): Lyrics?
    /** Every song on an album, in track order. Empty when the source can't say. */
    suspend fun albumSongs(albumId: String): List<Song>
}
