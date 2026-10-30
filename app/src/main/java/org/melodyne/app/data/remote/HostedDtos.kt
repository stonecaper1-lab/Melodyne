package org.melodyne.app.data.remote

import kotlinx.serialization.Serializable

/** Custom sources wrap everything in { success, data }. */
@Serializable
data class Envelope<T>(val success: Boolean = true, val data: T? = null, val message: String? = null)

@Serializable data class UrlDto(val quality: String = "", val url: String = "")
@Serializable data class NamedDto(val id: String = "", val name: String = "")
@Serializable data class ArtistsDto(val primary: List<NamedDto> = emptyList(), val all: List<NamedDto> = emptyList())

@Serializable
data class SongDto(
    val id: String,
    val name: String = "",
    val duration: Int? = null,
    val hasLyrics: Boolean = false,
    val album: NamedDto? = null,
    val artists: ArtistsDto? = null,
    val image: List<UrlDto> = emptyList(),
    val downloadUrl: List<UrlDto> = emptyList(),
)

@Serializable data class SearchData(val results: List<SongDto> = emptyList())

@Serializable
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val image: List<UrlDto> = emptyList(),
    val songs: List<SongDto> = emptyList(),
)

@Serializable data class PlaylistSearchData(val results: List<PlaylistDto> = emptyList())
@Serializable data class LyricsDto(val lyrics: String? = null, val snippet: String? = null)
