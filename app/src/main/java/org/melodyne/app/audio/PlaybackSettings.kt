package org.melodyne.app.audio

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackState(
    val audiophile: Boolean = false,
    val normalize: Boolean = true,
    val crossfade: Boolean = false,
    val crossfadeSec: Int = 6,
    val equalPower: Boolean = true,
    val infinite: Boolean = true,
    val glow: Boolean = true,
    /** Extra stereo width, 0..0.5 (0 = untouched). */
    val width: Float = 0f,
    /** -1 (left) .. 1 (right). */
    val balance: Float = 0f,
    val skin: String = "liquid",
    /** Normalisation only turns loud songs down, never boosts quiet ones. */
    val normGentle: Boolean = true,
) {
    /** Normalisation and crossfade are always off in Audiophile Mode, whatever the saved preference says. */
    val normalizeEffective: Boolean get() = normalize && !audiophile
    val crossfadeEffective: Boolean get() = crossfade && !audiophile
}

/** Audiophile switch, Audio Tuning, infinite play and appearance, persisted like a profile. */
class PlaybackSettings(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(
        PlaybackState(
            audiophile = prefs.getBoolean("audiophile", false),
            normalize = prefs.getBoolean("normalize", true),
            crossfade = prefs.getBoolean("crossfade", false),
            crossfadeSec = prefs.getInt("crossfade_sec", 6).coerceIn(1, 12),
            equalPower = prefs.getBoolean("equal_power", true),
            infinite = prefs.getBoolean("infinite", true),
            glow = prefs.getBoolean("glow", true),
            width = prefs.getFloat("width", 0f).coerceIn(0f, 0.5f),
            balance = prefs.getFloat("balance", 0f).coerceIn(-1f, 1f),
            skin = prefs.getString("skin", "liquid") ?: "liquid",
            normGentle = prefs.getBoolean("norm_gentle", true),
        )
    )
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private fun change(block: (PlaybackState) -> PlaybackState) {
        val n = block(_state.value)
        _state.update { n }
        prefs.edit()
            .putBoolean("audiophile", n.audiophile).putBoolean("normalize", n.normalize).putBoolean("crossfade", n.crossfade)
            .putInt("crossfade_sec", n.crossfadeSec).putBoolean("equal_power", n.equalPower)
            .putBoolean("infinite", n.infinite).putBoolean("glow", n.glow)
            .putFloat("width", n.width).putFloat("balance", n.balance).putString("skin", n.skin).putBoolean("norm_gentle", n.normGentle).apply()
    }

    fun setAudiophile(on: Boolean) = change { it.copy(audiophile = on) }
    fun setNormalize(on: Boolean) = change { it.copy(normalize = on) }
    fun setCrossfade(on: Boolean) = change { it.copy(crossfade = on) }
    fun setCrossfadeSec(s: Int) = change { it.copy(crossfadeSec = s.coerceIn(1, 12)) }
    fun setEqualPower(on: Boolean) = change { it.copy(equalPower = on) }
    fun setInfinite(on: Boolean) = change { it.copy(infinite = on) }
    fun setGlow(on: Boolean) = change { it.copy(glow = on) }
    fun setWidth(w: Float) = change { it.copy(width = w.coerceIn(0f, 0.5f)) }
    fun setBalance(b: Float) = change { it.copy(balance = b.coerceIn(-1f, 1f)) }
    fun setSkin(id: String) = change { it.copy(skin = id) }
    fun setNormGentle(on: Boolean) = change { it.copy(normGentle = on) }

    /** The welcome tour shows once per install (and once after the update that adds it), then only when asked for. */
    fun wizardSeen(): Boolean = prefs.getBoolean("wizard_seen_v1", false)
    fun markWizardSeen() { prefs.edit().putBoolean("wizard_seen_v1", true).apply() }

    /** One-tap profiles: Normal, Audiophile, Party. */
    fun preset(name: String) = change {
        when (name) {
            "audiophile" -> it.copy(audiophile = true)
            "party" -> it.copy(audiophile = false, normalize = true, crossfade = true, crossfadeSec = 6)
            else -> it.copy(audiophile = false, normalize = true, crossfade = false)
        }
    }
}

/** Bridge between the audio thread (LoudnessProcessor) and the rest of the app. */
class NormalizerController(private val prefs: SharedPreferences) {
    @Volatile var enabled: Boolean = false
    @Volatile var currentKey: String? = null
    @Volatile var maxBoostDb: Double = Loudness.MAX_GAIN_DB

    private val _gainDb = MutableStateFlow<Float?>(null)
    /** Gain being applied right now, or null while a song is still being measured. */
    val gainDb: StateFlow<Float?> = _gainDb.asStateFlow()

    fun cached(key: String): Float? = if (prefs.contains("gain_$key")) prefs.getFloat("gain_$key", 0f) else null
    fun remember(key: String, db: Float) { prefs.edit().putFloat("gain_$key", db).apply() }
    fun publish(db: Float?) { _gainDb.value = db }

    private val _plr = MutableStateFlow<Float?>(null)
    /** Peak-to-loudness ratio of the song being played, once measured. */
    val plr: StateFlow<Float?> = _plr.asStateFlow()
    fun publishPlr(v: Float?) { _plr.value = v }
}

/** Fade shapes for crossfade. Equal-power keeps loudness steady through the middle of the fade. */
object CrossfadeCurve {
    /** Returns (gain for the incoming song, gain for the outgoing song) at progress [p] in 0..1. */
    fun gains(p: Float, equalPower: Boolean): Pair<Float, Float> {
        val x = p.coerceIn(0f, 1f)
        return if (equalPower) {
            kotlin.math.sin(x * Math.PI / 2).toFloat() to kotlin.math.cos(x * Math.PI / 2).toFloat()
        } else x to 1f - x
    }
}
