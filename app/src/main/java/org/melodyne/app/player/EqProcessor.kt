package org.melodyne.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.melodyne.app.audio.Biquad
import org.melodyne.app.audio.EQ_BANDS
import org.melodyne.app.audio.EqMath
import org.melodyne.app.audio.Tpdf
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Ten-band graphic EQ plus stereo width and balance, inside the decode path. 16-bit PCM in, double-precision maths,
 * 16-bit PCM out (TPDF-dithered). Auto-headroom is subtracted so boosts can't clip; widening is level-compensated.
 * When everything is neutral (flat EQ, width 1, balance centred) or switched off, input is copied through untouched.
 */
@UnstableApi
class EqProcessor : BaseAudioProcessor() {
    @Volatile private var requestedGains = FloatArray(EQ_BANDS.size)
    @Volatile private var requestedOn = false
    @Volatile private var requestedWidth = 1f
    @Volatile private var requestedBalance = 0f
    @Volatile private var version = 0

    private var appliedVersion = -1
    private var channels = 2
    private var rate = 44100
    private var shaping = false
    private var eqOn = false
    private var stereoFx = false
    private var preamp = 1.0
    private var width = 1.0
    private var gainL = 1.0
    private var gainR = 1.0
    private var live = IntArray(0)
    private var filters = arrayOfNulls<Biquad>(EQ_BANDS.size)
    private var z1 = Array(2) { DoubleArray(EQ_BANDS.size) }
    private var z2 = Array(2) { DoubleArray(EQ_BANDS.size) }
    private val dither = Tpdf()

    /** Called from any thread; picked up on the audio thread at the next buffer. [width] 1 = unchanged, [balance] -1..1. */
    fun update(gains: List<Float>, enabled: Boolean, width: Float = 1f, balance: Float = 0f) {
        requestedGains = gains.toFloatArray()
        requestedOn = enabled
        requestedWidth = width
        requestedBalance = balance
        version++
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
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
        eqOn = requestedOn && g.any { abs(it) >= 0.05f }
        val w = requestedWidth.coerceIn(0.5f, 2f).toDouble()
        val b = requestedBalance.coerceIn(-1f, 1f)
        val comp = 1.0 / maxOf(1.0, w)                                   // widening can raise peaks by up to x width
        gainL = comp * (if (b > 0f) 1.0 - b else 1.0)
        gainR = comp * (if (b < 0f) 1.0 + b else 1.0)
        width = w
        stereoFx = channels == 2 && (abs(w - 1.0) > 0.01 || abs(b) > 0.01f)
        shaping = eqOn || stereoFx
        if (eqOn) {
            val designed = EqMath.designGains(g.toList(), rate.toDouble())          // so the response passes through the drawn curve
            for (i in EQ_BANDS.indices) filters[i] = EqMath.design(i, designed[i], rate.toDouble())
            live = EQ_BANDS.indices.filter { abs(designed[it]) >= 0.05f }.toIntArray()
            preamp = 10.0.pow(-EqMath.headroomDb(designed, rate.toDouble()) / 20.0)
        } else {
            live = IntArray(0)
            preamp = 1.0
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
            val y = DoubleArray(2)
            for (f in 0 until frames) {
                for (ch in 0 until channels) {
                    var x = inputBuffer.getShort().toDouble() * preamp
                    for (b in live) {
                        val q = filters[b] ?: continue
                        val o = q.b0 * x + z1[ch][b]
                        z1[ch][b] = q.b1 * x - q.a1 * o + z2[ch][b]
                        z2[ch][b] = q.b2 * x - q.a2 * o
                        x = o
                    }
                    y[ch] = x
                }
                if (stereoFx) {
                    val mid = (y[0] + y[1]) * 0.5
                    val side = (y[0] - y[1]) * 0.5 * width
                    y[0] = (mid + side) * gainL
                    y[1] = (mid - side) * gainR
                }
                for (ch in 0 until channels) out.putShort((y[ch] + dither.next()).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            inputBuffer.position(inputBuffer.limit())          // swallow any partial trailing frame
            while (out.position() < size) out.put(0.toByte())
        }
        out.flip()
    }
}
