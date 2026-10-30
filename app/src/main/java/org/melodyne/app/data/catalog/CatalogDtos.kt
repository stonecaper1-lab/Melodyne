package org.melodyne.app.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.QueryMap

/** Raw reply shapes of the catalogue's web endpoint. Everything is optional on purpose. */
@Serializable data class CArtist(val id: String = "", val name: String = "")
@Serializable data class CArtistMap(
    @SerialName("primary_artists") val primary: List<CArtist> = emptyList(),
    val artists: List<CArtist> = emptyList(),
)

@Serializable
data class CMore(
    val album: String = "",
    val duration: String? = null,
    @SerialName("has_lyrics") val hasLyrics: String? = null,
    @SerialName("320kbps") val kbps320: String? = null,
    @SerialName("encrypted_media_url") val encryptedUrl: String? = null,
    val artistMap: CArtistMap? = null,
)

@Serializable
data class CSong(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val image: String = "",
    @SerialName("more_info") val more: CMore? = null,
)

@Serializable data class CSearch(val results: List<CSong> = emptyList())
@Serializable data class CPlaylist(val id: String = "", val title: String = "", val image: String = "", val list: List<CSong> = emptyList())
@Serializable data class CPlaylistSearch(val results: List<CPlaylist> = emptyList())
@Serializable data class CLyrics(val lyrics: String? = null)

interface CatalogApi {
    @GET("api.php")
    suspend fun call(@QueryMap params: Map<String, String>): ResponseBody
}
