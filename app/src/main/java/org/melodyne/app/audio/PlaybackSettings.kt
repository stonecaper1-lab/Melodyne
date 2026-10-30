package org.melodyne.app.audio

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackState(val audiophile: Boolean = false, val normalize: Boolean = true) {
    /** Normalisation is always off in Audiophile Mode, whatever the saved preference says. */
    val normalizeEffective: Boolean get() = normalize && !audiophile
}

/** Audiophile switch and Audio Tuning options, persisted like a profile. */
class PlaybackSettings(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(PlaybackState(prefs.getBoolean("audiophile", false), prefs.getBoolean("normalize", true)))
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun setAudiophile(on: Boolean) {
        prefs.edit().putBoolean("audiophile", on).apply()
        _state.update { it.copy(audiophile = on) }
    }

    fun setNormalize(on: Boolean) {
        prefs.edit().putBoolean("normalize", on).apply()
        _state.update { it.copy(normalize = on) }
    }
}

/** Bridge between the audio thread (LoudnessProcessor) and the rest of the app. */
class NormalizerController(private val prefs: SharedPreferences) {
    @Volatile var enabled: Boolean = false
    @Volatile var currentKey: String? = null

    private val _gainDb = MutableStateFlow<Float?>(null)
    /** Gain being applied right now, or null while a song is still being measured. */
    val gainDb: StateFlow<Float?> = _gainDb.asStateFlow()

    fun cached(key: String): Float? = if (prefs.contains("gain_$key")) prefs.getFloat("gain_$key", 0f) else null
    fun remember(key: String, db: Float) { prefs.edit().putFloat("gain_$key", db).apply() }
    fun publish(db: Float?) { _gainDb.value = db }
}
