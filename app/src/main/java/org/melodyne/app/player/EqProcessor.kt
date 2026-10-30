package org.melodyne.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.melodyne.app.audio.Biquad
import org.melodyne.app.audio.EQ_BANDS
import org.melodyne.app.audio.EqMath
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Seven-band parametric EQ inside the decode path. 16-bit PCM in, double-precision filtering,
 * 16-bit PCM out. Auto-headroom is subtracted so boosts can't clip. When the curve is flat or the
 * EQ is off, input is copied through untouched (bit for bit); no effect is attached to the device.
 */
@UnstableApi
class EqProcessor : BaseAudioProcessor() {
    @Volatile private var requestedGains = FloatArray(EQ_BANDS.size)
    @Volatile private var requestedOn = false
    @Volatile private var version = 0

    private var appliedVersion = -1
    private var channels = 2
    private var rate = 44100
    private var shaping = false
    private var preamp = 1.0
    private var live = IntArray(0)
    private var filters = arrayOfNulls<Biquad>(EQ_BANDS.size)
    private var z1 = Array(2) { DoubleArray(EQ_BANDS.size) }
    private var z2 = Array(2) { DoubleArray(EQ_BANDS.size) }

    /** Called from any thread; picked up on the audio thread at the next buffer. */
    fun update(gains: List<Float>, enabled: Boolean) {
        requestedGains = gains.toFloatArray()
        requestedOn = enabled
        version++
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Anything other than 16-bit mono/stereo is left alone (processor stays inactive for it).
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        appliedVersion = -1
        clearState()
        return inputAudioFormat
    }

    override fun onFlush() { clearState() }
    override fun onReset() { clearState() }

    private fun clearState() {
        z1 = Array(2) { DoubleArray(EQ_BANDS.size) }
        z2 = Array(2) { DoubleArray(EQ_BANDS.size) }
    }

    private fun refresh() {
        val v = version
        if (v == appliedVersion) return
        val g = requestedGains
        shaping = requestedOn && g.any { abs(it) >= 0.05f }
        if (shaping) {
            val list = g.toList()
            for (i in EQ_BANDS.indices) filters[i] = EqMath.design(i, g[i], rate.toDouble())
            live = EQ_BANDS.indices.filter { abs(g[it]) >= 0.05f }.toIntArray()
            preamp = 10.0.pow(-EqMath.headroomDb(list, rate.toDouble()) / 20.0)
        }
        appliedVersion = v
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        refresh()
        if (!shaping) {
            out.put(inputBuffer)
        } else {
            val frames = size / (2 * channels)
            for (f in 0 until frames) {
                for (ch in 0 until channels) {
                    var x = inputBuffer.getShort().toDouble() * preamp
                    for (b in live) {
                        val q = filters[b] ?: continue
                        val y = q.b0 * x + z1[ch][b]
                        z1[ch][b] = q.b1 * x - q.a1 * y + z2[ch][b]
                        z2[ch][b] = q.b2 * x - q.a2 * y
                        x = y
                    }
                    out.putShort(x.roundToInt().coerceIn(-32768, 32767).toShort())
                }
            }
            inputBuffer.position(inputBuffer.limit())          // swallow any partial trailing frame
            while (out.position() < size) out.put(0.toByte())
        }
        out.flip()
    }
}
