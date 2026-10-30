package org.melodyne.app.data.lastfm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import org.melodyne.app.ai.Suggestion
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText
import retrofit2.http.GET
import retrofit2.http.QueryMap
import java.io.File
import java.security.MessageDigest

interface LastFmReadApi {
    @GET("2.0/")
    suspend fun get(@QueryMap q: Map<String, String>): ResponseBody
}

/** What Last.fm knows about a track. It only ever augments a catalogue song; the song's own id is never touched. */
data class Enriched(val tags: List<String> = emptyList(), val listeners: Long? = null, val bio: String? = null)

/**
 * Read-only Last.fm lookups (track.getInfo, track.getTopTags, artist.getInfo, artist.getSimilar, tag.getTopTracks,
 * geo.getTopTracks). Every answer is cached on disk for 24 hours; an expired answer is still used if the network fails.
 */
class EnrichmentService(
    private val api: LastFmReadApi,
    private val keyProvider: () -> String,
    private val cacheDir: File,
    private val json: Json,
) {
    val available: Boolean get() = keyProvider().isNotBlank()
    private val ttlMs = 24L * 3600 * 1000

    private fun cacheFile(params: Map<String, String>): File {
        val raw = params.filterKeys { it != "api_key" }.toSortedMap().entries.joinToString("&") { it.key + "=" + it.value }
        val name = MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir, "lfm/$name.json")
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private suspend fun call(method: String, vararg extra: Pair<String, String>): JsonObject? {
        val key = keyProvider()
        if (key.isBlank()) return null
        val params = mapOf("method" to method, "api_key" to key, "format" to "json", "autocorrect" to "1") + extra
        val file = cacheFile(params)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMs
        if (fresh) parse(file.readText())?.let { return it }
        val net = try {
            withContext(Dispatchers.IO) { api.get(params).string() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val obj = net?.let { parse(it) }
        if (obj != null && obj["error"] == null) {
            runCatching { file.parentFile?.mkdirs(); file.writeText(net.orEmpty()) }
            return obj
        }
        return if (file.exists()) parse(file.readText()) else null
    }

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    /** Last.fm sends a list as an array, or as a bare object when there is only one item. */
    private fun JsonElement?.items(): List<JsonObject> = when (this) {
        is JsonArray -> this.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    suspend fun similarArtists(artist: String, limit: Int = 8): List<String> =
        call("artist.getSimilar", "artist" to artist, "limit" to limit.toString())
            ?.obj("similarartists")?.get("artist").items().mapNotNull { it.str("name") }

    /** Tags that clearly describe the track: Last.fm's own weight must exceed 25 (out of 100). */
    suspend fun trackTags(title: String, artist: String): List<String> =
        call("track.getTopTags", "track" to title, "artist" to artist)
            ?.obj("toptags")?.get("tag").items()
            .filter { (it.str("count")?.toIntOrNull() ?: 0) > 25 }.mapNotNull { it.str("name") }.take(4)

    suspend fun trackListeners(title: String, artist: String): Long? =
        call("track.getInfo", "track" to title, "artist" to artist)?.obj("track")?.str("listeners")?.toLongOrNull()

    suspend fun artistInfo(artist: String): Pair<List<String>, String?> {
        val a = call("artist.getInfo", "artist" to artist)?.obj("artist")
        val tags = a?.obj("tags")?.get("tag").items().mapNotNull { it.str("name") }.take(4)
        val bio = a?.obj("bio")?.str("summary")?.let { plain(it) }?.takeIf { it.isNotBlank() }
        return tags to bio
    }

    suspend fun tagTopTracks(tag: String, limit: Int = 30): List<Suggestion> =
        call("tag.getTopTracks", "tag" to tag, "limit" to limit.toString())
            ?.obj("tracks")?.get("track").items().mapNotNull(::asSuggestion)

    suspend fun geoTopTracks(country: String, limit: Int = 25): List<Suggestion> =
        call("geo.getTopTracks", "country" to country, "limit" to limit.toString())
            ?.obj("tracks")?.get("track").items().mapNotNull(::asSuggestion)

    private fun asSuggestion(t: JsonObject): Suggestion? {
        val name = t.str("name") ?: return null
        return Suggestion(name, t.obj("artist")?.str("name") ?: "")
    }

    suspend fun enrich(song: Song): Enriched = coroutineScope {
        val title = TitleText.strip(song.title)
        val artist = TitleText.primaryArtist(song.artist)
        val tags = async { trackTags(title, artist) }
        val listeners = async { trackListeners(title, artist) }
        val info = async { artistInfo(artist) }
        val (artistTags, bio) = info.await()
        Enriched(tags = tags.await().ifEmpty { artistTags }, listeners = listeners.await(), bio = bio)
    }

    private fun plain(html: String): String =
        html.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'")
            .replace(Regex("Read more on Last\\.fm.*$"), "").trim().take(400)
}
