package org.melodyne.app.audio

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EqState(
    val enabled: Boolean = true,
    val presetId: String = "flat",
    val custom: List<Float> = List(EQ_BANDS.size) { 0f },
) {
    val gains: List<Float> get() = if (presetId == CUSTOM_ID) custom else EqPresets.byId(presetId).gains

    /** True when the curve would change the audio at all. */
    val shaping: Boolean get() = enabled && gains.any { kotlin.math.abs(it) >= 0.05f }
}

/** Shared by the Now Playing sheet (writes) and the playback service (applies it to the audio path). */
class EqController(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private fun load(): EqState {
        val saved = prefs.getString("eq_custom", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
        return EqState(
            enabled = prefs.getBoolean("eq_on", true),
            presetId = prefs.getString("eq_preset", "flat") ?: "flat",
            custom = if (saved != null && saved.size == EQ_BANDS.size) saved else List(EQ_BANDS.size) { 0f },
        )
    }

    fun setPreset(id: String) {
        prefs.edit().putString("eq_preset", id).apply()
        _state.update { it.copy(presetId = id) }
    }

    /** Touching any band turns the current curve into the editable Custom curve. */
    fun setBand(index: Int, db: Float) {
        _state.update { s ->
            val g = s.gains.toMutableList()
            g[index] = db.coerceIn(-EQ_MAX_DB, EQ_MAX_DB)
            prefs.edit().putString("eq_custom", g.joinToString(",")).putString("eq_preset", CUSTOM_ID).apply()
            s.copy(presetId = CUSTOM_ID, custom = g)
        }
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("eq_on", on).apply()
        _state.update { it.copy(enabled = on) }
    }
}
