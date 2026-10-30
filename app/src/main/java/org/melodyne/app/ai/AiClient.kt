package org.melodyne.app.ai

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Bring-your-own-key: any OpenAI-compatible /chat/completions endpoint (hosted or local). */
data class AiConfig(val baseUrl: String = "", val apiKey: String = "", val model: String = "") {
    val ready: Boolean get() = baseUrl.startsWith("https://") && apiKey.isNotBlank() && model.isNotBlank()
}

class AiConfigStore(private val prefs: SharedPreferences) {
    fun load() = AiConfig(
        prefs.getString("ai_url", "").orEmpty(), prefs.getString("ai_key", "").orEmpty(), prefs.getString("ai_model", "").orEmpty(),
    )

    fun save(c: AiConfig) {
        prefs.edit().putString("ai_url", c.baseUrl.trim().trimEnd('/')).putString("ai_key", c.apiKey.trim())
            .putString("ai_model", c.model.trim()).apply()
    }
}

@Serializable
data class Suggestion(val title: String = "", val artist: String = "")

class AiClient(private val http: OkHttpClient, private val json: Json) {

    suspend fun suggest(cfg: AiConfig, prompt: String, count: Int, hints: List<String>): List<Suggestion> {
        val user = buildString {
            append("Create a playlist of exactly $count songs for this request: ").append(prompt.trim())
            if (hints.isNotEmpty()) append("\nListener's recent favourites (taste hints only, do not repeat them):\n").append(hints.joinToString("\n"))
        }
        val body = buildJsonObject {
            put("model", cfg.model)
            put("temperature", 0.8)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", SYSTEM) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            }
        }.toString()
        val req = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = withContext(Dispatchers.IO) {
            http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException("AI provider answered HTTP ${r.code}: ${t.take(140)}")
                t
            }
        }
        val content = try {
            json.parseToJsonElement(text).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
                .jsonObject["content"]!!.jsonPrimitive.content
        } catch (e: Exception) {
            throw IOException("Unexpected reply from AI provider: ${text.take(120)}")
        }
        val list = parse(content, json, count)
        if (list.isEmpty()) throw IOException("The AI didn't return any songs. Try rewording your request.")
        return list
    }

    /** One-token request to prove the base URL, key and model work together. Returns the round-trip time in ms. */
    suspend fun test(cfg: AiConfig): Long {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", 1)
            putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", "ping") }) }
        }.toString()
        val req = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val t0 = System.nanoTime()
        withContext(Dispatchers.IO) {
            http.newBuilder().readTimeout(30, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${r.body?.string().orEmpty().take(120)}")
            }
        }
        return (System.nanoTime() - t0) / 1_000_000
    }

    companion object {
        private const val SYSTEM =
            "You are a music curator for a streaming app with a large catalogue of Bollywood, regional Indian " +
                "and international songs. Reply with ONLY a JSON array, no markdown fences and no commentary. Each element is " +
                "{\"title\": string, \"artist\": string}. Use only real, well-known released songs. No duplicates."

        /** Tolerates prose or code fences around the JSON array. */
        fun parse(content: String, json: Json, limit: Int = 50): List<Suggestion> {
            val a = content.indexOf('[')
            val b = content.lastIndexOf(']')
            if (a < 0 || b <= a) return emptyList()
            return runCatching {
                json.decodeFromString(ListSerializer(Suggestion.serializer()), content.substring(a, b + 1))
            }.getOrDefault(emptyList())
                .filter { it.title.isNotBlank() }
                .distinctBy { (it.title + it.artist).lowercase() }
                .take(limit)
        }
    }
}
