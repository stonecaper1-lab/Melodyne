package org.melodyne.app.data.update

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class UpdateInfo(val version: String, val notes: String, val pageUrl: String, val apkUrl: String? = null)

/**
 * Looks at the project's latest public GitHub release (when the app opens, and from a background job) and tells the UI
 * when a newer version exists. Nothing is installed automatically: the download is started by you, and Android asks
 * before it installs.
 */
class UpdateChecker(
    private val http: OkHttpClient,
    private val json: Json,
    private val prefs: SharedPreferences,
    private val current: String,
) {
    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()
    private val _status = MutableStateFlow("Not checked yet")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        prefs.edit().remove("upd_repo").remove("upd_token").apply()          // earlier versions let you save a repository and a token; forget both
        val cached = prefs.getString("upd_info", null)?.let { runCatching { json.decodeFromString(UpdateInfo.serializer(), it) }.getOrNull() }
        if (cached != null && isNewer(cached.version, current) && prefs.getString("upd_dismissed", null) != cached.version) {
            _available.value = cached
        }
    }

    var notifyEnabled: Boolean
        get() = prefs.getBoolean("upd_notify", true)
        set(v) { prefs.edit().putBoolean("upd_notify", v).apply() }

    /** For the background job: the newest release if it is newer than this app and hasn't been waved away with "Later". */
    suspend fun newerRelease(): UpdateInfo? {
        val info = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        prefs.edit().putLong("upd_last", System.currentTimeMillis()).putString("upd_info", json.encodeToString(UpdateInfo.serializer(), info)).apply()
        if (!isNewer(info.version, current)) return null
        if (prefs.getString("upd_dismissed", null) == info.version) return null
        _available.value = info
        return info
    }

    fun dismiss() {
        _available.value?.let { prefs.edit().putString("upd_dismissed", it.version).apply() }
        _available.value = null
    }

    suspend fun check(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("upd_last", 0) < SIX_HOURS) return
        _status.value = "Checking…"
        try {
            val info = fetch()
            prefs.edit().putLong("upd_last", now).putString("upd_info", json.encodeToString(UpdateInfo.serializer(), info)).apply()
            if (isNewer(info.version, current)) {
                _status.value = "Update available: v${info.version}"
                if (force || prefs.getString("upd_dismissed", null) != info.version) _available.value = info
            } else {
                _status.value = "You're on the latest version (v$current)."
                _available.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = "Couldn't check: ${e.message}"
        }
    }

    /** The project's home, then its old name if GitHub has no release under the new one. */
    private suspend fun fetch(): UpdateInfo = try { fetchFrom(DEFAULT_REPO) } catch (e: NoRelease) { fetchFrom(LEGACY_REPO) }

    private class NoRelease(message: String) : IOException(message)

    private suspend fun fetchFrom(name: String): UpdateInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/$name/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 404 -> throw NoRelease("no release has been published yet")
                r.code == 403 || r.code == 429 -> throw IOException("GitHub is rate-limiting this phone. Try again later.")
                !r.isSuccessful -> throw IOException("HTTP ${r.code}")
            }
            val o = json.parseToJsonElement(body).jsonObject
            val tag = o.text("tag_name") ?: throw IOException("unexpected reply")
            val apks = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.text("name")?.endsWith(".apk") == true }
            val apk = (apks.firstOrNull { it.text("name") == "vina.apk" } ?: apks.firstOrNull { it.text("name") == "melodyne.apk" } ?: apks.firstOrNull())?.text("browser_download_url")
            UpdateInfo(tag.removePrefix("v"), o.text("body").orEmpty().trim(), o.text("html_url").orEmpty(), apk)
        }
    }

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this?.toList() ?: emptyList()

    companion object {
        const val DEFAULT_REPO = "stonecaper1-lab/Vina"
        /** The project used to be called Melodyne; GitHub keeps redirecting, but try the old name too if the new one 404s. */
        const val LEGACY_REPO = "stonecaper1-lab/Melodyne"
        private const val SIX_HOURS = 6L * 3600 * 1000

        /** "v0.10.1" is newer than "0.9.4"; missing parts count as zero. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split(".", "-", "+").mapNotNull { it.toIntOrNull() }
            val a = parts(remote); val b = parts(local)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
