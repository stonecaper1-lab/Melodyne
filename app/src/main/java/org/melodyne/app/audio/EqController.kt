package org.melodyne.app.audio

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A curve you saved under your own name. */
@Serializable
data class UserEq(val id: String, val name: String, val gains: List<Float>)

data class EqState(
    val enabled: Boolean = true,
    val presetId: String = "flat",
    val custom: List<Float> = List(EQ_BANDS.size) { 0f },
    val users: List<UserEq> = emptyList(),
) {
    val gains: List<Float>
        get() = when {
            presetId == CUSTOM_ID -> custom
            presetId.startsWith(USER_PREFIX) -> users.firstOrNull { it.id == presetId }?.gains ?: custom
            else -> EqPresets.byId(presetId).gains
        }

    val presetName: String
        get() = when {
            presetId == CUSTOM_ID -> "Custom"
            presetId.startsWith(USER_PREFIX) -> users.firstOrNull { it.id == presetId }?.name ?: "Custom"
            else -> EqPresets.byId(presetId).name
        }

    /** Every preset you can step through: the built-in ones, then yours. */
    val presetIds: List<String> get() = EqPresets.all.map { it.id } + users.map { it.id }

    /** True when the curve would change the audio at all. */
    val shaping: Boolean get() = enabled && gains.any { kotlin.math.abs(it) >= 0.05f }
}

/** Shared by the Now Playing sheet (writes) and the playback service (applies it to the audio path). */
class EqController(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val usersSerializer = ListSerializer(UserEq.serializer())
    private val _state = MutableStateFlow(load())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private fun load(): EqState {
        val zeros = List(EQ_BANDS.size) { 0f }
        val saved = prefs.getString("eq_custom", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
        val custom = when {
            saved == null -> zeros
            saved.size == EQ_BANDS.size -> saved
            saved.size == EqLegacy.OLD_BANDS.size -> EqLegacy.resample(saved)          // a curve saved by the old seven-band equaliser
            else -> zeros
        }
        val users = runCatching { json.decodeFromString(usersSerializer, prefs.getString("eq_user", "[]") ?: "[]") }
            .getOrDefault(emptyList()).filter { it.gains.size == EQ_BANDS.size }
        return EqState(
            enabled = prefs.getBoolean("eq_on", true),
            presetId = prefs.getString("eq_preset", "flat") ?: "flat",
            custom = custom,
            users = users,
        )
    }

    fun setPreset(id: String) {
        prefs.edit().putString("eq_preset", id).apply()
        _state.value = _state.value.copy(presetId = id)
    }

    /** Replaces the whole curve with these ten gains. It becomes the editable Custom curve. */
    fun setBands(gains: List<Float>) {
        if (gains.size != EQ_BANDS.size) return
        val g = gains.map { it.coerceIn(-EQ_MAX_DB, EQ_MAX_DB) }
        prefs.edit().putString("eq_custom", g.joinToString(",")).putString("eq_preset", CUSTOM_ID).apply()
        _state.value = _state.value.copy(presetId = CUSTOM_ID, custom = g)
    }

    /** Touching any band turns the current curve into the editable Custom curve. */
    fun setBand(index: Int, db: Float) {
        val g = _state.value.gains.toMutableList()
        if (index !in g.indices) return
        g[index] = db
        setBands(g)
    }

    /** The three lowest bands together, tapering off towards the mids (for the receiver skin's bass knob). */
    fun setBass(db: Float) {
        val g = _state.value.gains.toMutableList()
        listOf(1f, 1f, 0.6f).forEachIndexed { k, w -> g[k] = db * w }
        setBands(g)
    }

    /** The three highest bands together, tapering off towards the mids (for the treble knob). */
    fun setTreble(db: Float) {
        val g = _state.value.gains.toMutableList()
        val n = g.size
        listOf(0.6f, 1f, 1f).forEachIndexed { k, w -> g[n - 3 + k] = db * w }
        setBands(g)
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("eq_on", on).apply()
        _state.value = _state.value.copy(enabled = on)
    }

    /** Saves the curve currently shown under [name] and selects it. Returns its id. */
    fun saveUser(name: String): String {
        val s = _state.value
        val id = USER_PREFIX + System.currentTimeMillis()
        val entry = UserEq(id, name.trim().ifBlank { "My curve" }.take(24), s.gains)
        val users = s.users + entry
        prefs.edit().putString("eq_user", json.encodeToString(usersSerializer, users)).putString("eq_preset", id).apply()
        _state.value = s.copy(users = users, presetId = id)
        return id
    }

    fun deleteUser(id: String) {
        val s = _state.value
        val gone = s.users.firstOrNull { it.id == id } ?: return
        val users = s.users - gone
        val editor = prefs.edit().putString("eq_user", json.encodeToString(usersSerializer, users))
        if (s.presetId == id) {
            editor.putString("eq_custom", gone.gains.joinToString(",")).putString("eq_preset", CUSTOM_ID).apply()
            _state.value = s.copy(users = users, presetId = CUSTOM_ID, custom = gone.gains)       // the curve stays; it just isn't saved any more
        } else {
            editor.apply()
            _state.value = s.copy(users = users)
        }
    }
}
