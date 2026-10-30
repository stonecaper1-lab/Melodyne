package org.melodyne.app.data.lastfm

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.melodyne.app.domain.TitleText
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.io.IOException
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

interface LastFmApi {
    @FormUrlEncoded
    @POST("2.0/")
    suspend fun post(@FieldMap fields: Map<String, String>): Response<ResponseBody>
}

/** Last.fm request signing: md5 of name+value pairs sorted by name, followed by the shared secret. */
object LastFmSigner {
    fun sign(params: Map<String, String>, secret: String): String {
        val base = params.filterKeys { it != "format" && it != "callback" }
            .toSortedMap()
            .entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** Catalogue titles carry decorations Last.fm treats as a different track, e.g. `Tum Hi Ho (From "Aashiqui 2")`. */
object ScrobbleText {
    fun title(raw: String): String = TitleText.strip(raw)
    /** Last.fm wants the primary artist; the catalogue joins all credited artists with commas. */
    fun artist(raw: String): String = TitleText.primaryArtist(raw).ifBlank { raw.trim() }
}

@Serializable
data class PendingScrobble(val artist: String, val track: String, val album: String?, val ts: Long, val dur: Int)

/** Session key, the user's own API key/secret, and scrobbles waiting for a connection. The password is never stored. */
class SessionStore(private val prefs: SharedPreferences) {
    private val queueSer = ListSerializer(PendingScrobble.serializer())

    val user: String? get() = prefs.getString("lf_user", null)
    val key: String? get() = prefs.getString("lf_sk", null)
    val apiKey: String get() = prefs.getString("lf_api_key", "").orEmpty()
    val apiSecret: String get() = prefs.getString("lf_api_secret", "").orEmpty()

    fun save(user: String, key: String) = prefs.edit().putString("lf_user", user).putString("lf_sk", key).apply()
    fun clearSession() = prefs.edit().remove("lf_user").remove("lf_sk").apply()
    fun saveKeys(key: String, secret: String) =
        prefs.edit().putString("lf_api_key", key).putString("lf_api_secret", secret).apply()

    fun pending(): List<PendingScrobble> = runCatching {
        Json.decodeFromString(queueSer, prefs.getString("lf_queue", null) ?: "[]")
    }.getOrDefault(emptyList())

    fun setPending(list: List<PendingScrobble>) {
        prefs.edit().putString("lf_queue", Json.encodeToString(queueSer, list.takeLast(50))).apply()
    }
}

class LastFmClient(
    private val api: LastFmApi,
    private val store: SessionStore,
    private val defaultKey: String,
    private val defaultSecret: String,
) {
    private class LfError(val code: Int, message: String) : IOException(message)
    private enum class Outcome { OK, RETRY, DROP }

    private val _status = MutableStateFlow(if (store.key != null) "Signed in. Waiting for the next song." else "Not signed in")
    val status: StateFlow<String> = _status.asStateFlow()

    private val key: String get() = store.apiKey.ifBlank { defaultKey }
    private val secret: String get() = store.apiSecret.ifBlank { defaultSecret }
    val configured: Boolean get() = key.isNotBlank() && secret.isNotBlank()
    val loggedInUser: String? get() = store.user?.takeIf { store.key != null }
    fun currentKey(): String = key
    val savedKey: String get() = store.apiKey
    val savedSecret: String get() = store.apiSecret
    val hasBuiltInKey: Boolean get() = defaultKey.isNotBlank() && defaultSecret.isNotBlank()

    fun saveKeys(apiKey: String, apiSecret: String) {
        store.saveKeys(apiKey.trim(), apiSecret.trim())
        _status.value = if (configured) "API key saved. Sign in to start scrobbling." else "Not signed in"
    }

    /** POSTs a signed call. Throws [LfError] for Last.fm errors and IOException for transport problems. */
    private suspend fun signed(params: Map<String, String>): JsonObject {
        val p = params + ("api_key" to key)
        val body = p + ("api_sig" to LastFmSigner.sign(p, secret)) + ("format" to "json")
        val r = api.post(body)
        val txt = (if (r.isSuccessful) r.body() else r.errorBody())?.string().orEmpty()
        val o = runCatching { Json.parseToJsonElement(txt).jsonObject }.getOrNull()
            ?: throw IOException("HTTP ${r.code()} with an unreadable reply")
        o["error"]?.let { e ->
            throw LfError(e.jsonPrimitive.content.toIntOrNull() ?: -1, o["message"]?.jsonPrimitive?.content ?: "Last.fm error")
        }
        return o
    }

    private fun friendly(e: Throwable): String = when {
        e is LfError && e.code == 4 -> "Wrong username or password."
        e is LfError && (e.code == 10 || e.code == 26) -> "Last.fm rejected the API key. Check the key and secret above."
        e is LfError && e.code == 9 -> "Your Last.fm session expired. Sign in again."
        e is LfError && (e.code == 11 || e.code == 16 || e.code == 29) -> "Last.fm is busy right now. Will retry."
        e is LfError -> "Last.fm: ${e.message}"
        e is IOException -> "Can't reach Last.fm (${e.message.orEmpty().take(60)})."
        else -> e.message ?: "Unknown error"
    }

    /** Mobile auth: trades username and password once for a long-lived session key. */
    suspend fun login(user: String, pass: String): Result<String> = try {
        check(configured) { "Enter your Last.fm API key and secret first." }
        val o = signed(mapOf("method" to "auth.getMobileSession", "username" to user, "password" to pass))
        val s = o["session"]?.jsonObject ?: throw IOException("Unexpected reply from Last.fm")
        val name = s["name"]!!.jsonPrimitive.content
        store.save(name, s["key"]!!.jsonPrimitive.content)
        _status.value = "Signed in as $name. Waiting for the next song."
        Result.success(name)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(friendly(e)))
    }

    fun logout() { store.clearSession(); _status.value = "Not signed in" }

    suspend fun nowPlaying(artist: String, track: String, album: String?, durationSec: Int) {
        val sk = store.key ?: return
        if (!configured || artist.isBlank() || track.isBlank()) return
        val p = mutableMapOf(
            "method" to "track.updateNowPlaying", "artist" to ScrobbleText.artist(artist),
            "track" to ScrobbleText.title(track), "sk" to sk,
        )
        album?.takeIf { it.isNotBlank() }?.let { p["album"] = it }
        if (durationSec > 0) p["duration"] = durationSec.toString()
        try { signed(p) } catch (e: CancellationException) { throw e } catch (e: Exception) { onFailure(e) }
    }

    suspend fun scrobble(artist: String, track: String, album: String?, startedAtSec: Long, durationSec: Int) {
        if (store.key == null) { _status.value = "Not signed in, so “$track” wasn't scrobbled."; return }
        if (!configured || artist.isBlank() || track.isBlank()) return
        val item = PendingScrobble(ScrobbleText.artist(artist), ScrobbleText.title(track), album?.takeIf { it.isNotBlank() }, startedAtSec, durationSec)
        when (submit(item)) {
            Outcome.OK -> flushQueue()
            Outcome.RETRY -> store.setPending(store.pending() + item)
            Outcome.DROP -> Unit
        }
    }

    private suspend fun flushQueue() {
        val cutoff = System.currentTimeMillis() / 1000 - 13L * 24 * 3600     // Last.fm refuses scrobbles older than 14 days
        val queue = store.pending().filter { it.ts > cutoff }
        if (queue.isEmpty()) { store.setPending(emptyList()); return }
        val left = ArrayList(queue)
        for (p in queue) {
            if (submit(p) == Outcome.RETRY) break
            left.remove(p)
        }
        store.setPending(left)
    }

    private suspend fun submit(p: PendingScrobble): Outcome {
        val sk = store.key ?: return Outcome.DROP
        val params = mutableMapOf(
            "method" to "track.scrobble", "artist" to p.artist, "track" to p.track,
            "timestamp" to p.ts.toString(), "sk" to sk,
        )
        p.album?.let { params["album"] = it }
        if (p.dur > 0) params["duration"] = p.dur.toString()
        return try {
            val attr = signed(params)["scrobbles"]?.jsonObject?.get("@attr")?.jsonObject
            val accepted = attr?.get("accepted")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            _status.value = if (accepted > 0) "Scrobbled “${p.track}” at $at" else "Last.fm skipped “${p.track}” (it filtered the entry)"
            Outcome.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    private fun onFailure(e: Exception): Outcome {
        _status.value = friendly(e)
        if (e is LfError && e.code == 9) store.clearSession()
        val temporary = e is IOException && (e !is LfError || e.code in setOf(11, 16, 29))
        return if (temporary) Outcome.RETRY else Outcome.DROP
    }
}
