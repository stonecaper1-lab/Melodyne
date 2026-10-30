package org.melodyne.app.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import org.melodyne.app.audio.EqState
import org.melodyne.app.audio.PlaybackState

data class ChainLine(val stage: String, val value: String)
data class Chain(val lines: List<ChainLine>, val clean: Boolean, val verdict: String)

/**
 * Answers one question honestly: what happens to the audio after the app hands it to Android?
 * Apps cannot switch off Android's mixer, so this reports what is knowable and, on Android 14+
 * with a USB DAC, asks the system for its bit-perfect path.
 */
object OutputInspector {
    private val musicAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    enum class Kind { USB, BLUETOOTH, WIRED, SPEAKER }

    private fun outputs(am: AudioManager): List<AudioDeviceInfo> = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

    private fun usbDevice(am: AudioManager): AudioDeviceInfo? =
        outputs(am).firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

    /** Best guess at where sound is going: USB, then Bluetooth, then wired, otherwise the phone speaker. */
    fun route(am: AudioManager): Pair<Kind, String> {
        val list = outputs(am)
        return when {
            usbDevice(am) != null -> Kind.USB to "USB audio device"
            list.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET } -> Kind.BLUETOOTH to "Bluetooth"
            list.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> Kind.WIRED to "Wired headphones"
            else -> Kind.SPEAKER to "Phone speaker"
        }
    }

    fun outputRateHz(am: AudioManager): Int = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0

    /** Android 14+: with a USB DAC connected, request the system's bit-perfect mixer (no resampling, no volume mixing). */
    fun applyBitPerfect(ctx: Context, wanted: Boolean) {
        if (Build.VERSION.SDK_INT < 34) return
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val usb = usbDevice(am) ?: return
            if (!wanted) { am.clearPreferredMixerAttributes(musicAttrs, usb); return }
            val choice = am.getSupportedMixerAttributes(usb).firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.encoding == AudioFormat.ENCODING_PCM_16BIT && it.format.sampleRate == 44100
            } ?: return
            am.setPreferredMixerAttributes(musicAttrs, usb, choice)
        }
    }

    private fun bitPerfectActive(am: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return runCatching {
            val usb = usbDevice(am) ?: return false
            am.getPreferredMixerAttributes(musicAttrs, usb)?.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        }.getOrDefault(false)
    }

    fun chain(ctx: Context, q: AudioQuality?, pb: PlaybackState, eq: EqState, presetName: String): Chain {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val (kind, routeName) = route(am)
        val src = q?.sampleRateHz ?: 0
        val out = outputRateHz(am)
        val bitPerfect = bitPerfectActive(am)
        val resampled = !bitPerfect && src > 0 && out > 0 && src != out
        val khz = { hz: Int -> "%.1f".format(hz / 1000f) }

        val active = buildList {
            if (eq.shaping && !pb.audiophile) add("EQ ($presetName)")
            if (pb.normalizeEffective) add("volume normalisation")
        }
        val processing = when {
            pb.audiophile -> "None · EQ bypassed · no normalisation · no crossfade"
            active.isEmpty() -> "None"
            else -> active.joinToString(" + ")
        }
        val osLine = when {
            bitPerfect -> "Bit-perfect USB path · Android resampling bypassed: YES"
            resampled -> "Shared mixer · Android resampling bypassed: NO"
            src > 0 && out > 0 -> "Shared mixer · rates match, nothing to resample · bypassed: YES"
            else -> "Shared mixer · waiting for a stream"
        }
        val outLine = when {
            kind == Kind.BLUETOOTH -> "$routeName · re-encoded by your phone before it leaves"
            resampled -> "$routeName · PCM ${khz(out)} kHz"
            src > 0 -> "$routeName · PCM ${khz(src)} kHz"
            else -> routeName
        }
        val problems = buildList {
            if (!pb.audiophile && active.isNotEmpty()) add("processing is on ($processing)")
            if (resampled) add("Resampled to ${khz(out)} kHz by Android")
            if (kind == Kind.BLUETOOTH) add("Bluetooth re-encodes the audio")
        }
        val lines = listOf(
            ChainLine("SOURCE", if (q != null) "${q.codec} ${q.kbps} kbps${if (src > 0) " · ${khz(src)} kHz" else ""}" else "Waiting for a stream"),
            ChainLine("DECODE", "Single stage · ${q?.codec ?: "AAC"} → PCM"),
            ChainLine("PROCESSING", processing),
            ChainLine("OS", osLine),
            ChainLine("OUTPUT", outLine),
        )
        return Chain(lines, problems.isEmpty(), if (problems.isEmpty()) "CLEAN CHAIN · no added loss" else "CHAIN BROKEN: ${problems.first()}")
    }
}
