package org.melodyne.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Live levels for the skins. The meter only works while a skin is on screen ([watching]). */
object LevelBus {
    @Volatile var watching = false
    val bands = FloatArray(16)
    @Volatile var peakL = 0f
    @Volatile var peakR = 0f
}

/**
 * Last processor in the chain: copies audio through untouched and, only while a skin is watching, measures it.
 * A 1024-point FFT folded into 16 log-spaced bands drives the spectrum; peaks drive the VU needles.
 */
@UnstableApi
class MeterProcessor : BaseAudioProcessor() {
    private var channels = 2
    private val ring = FloatArray(N)
    private var filled = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }
    private val edges = IntArray(17) { (N / 2.0).pow(it / 16.0).toInt().coerceIn(1, N / 2) }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        filled = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        if (LevelBus.watching) analyze(inputBuffer.duplicate().order(inputBuffer.order()))
        out.put(inputBuffer)
        out.flip()
    }

    private fun analyze(b: ByteBuffer) {
        val frames = b.remaining() / (2 * channels)
        var pl = 0f
        var pr = 0f
        for (i in 0 until frames) {
            val l = b.getShort() / 32768f
            val r = if (channels == 2) b.getShort() / 32768f else l
            if (abs(l) > pl) pl = abs(l)
            if (abs(r) > pr) pr = abs(r)
            ring[filled++] = (l + r) * 0.5f
            if (filled == N) { spectrum(); filled = 0 }
        }
        LevelBus.peakL = pl
        LevelBus.peakR = pr
    }

    private fun spectrum() {
        for (i in 0 until N) { re[i] = ring[i] * window[i]; im[i] = 0f }
        fft()
        for (band in 0 until 16) {
            val from = edges[band]
            val to = maxOf(edges[band + 1], from + 1)
            var peak = 0f
            for (k in from until minOf(to, N / 2)) peak = maxOf(peak, sqrt(re[k] * re[k] + im[k] * im[k]))
            val db = 20f * log10(peak / (N / 4f) + 1e-6f)                // 0 dB = a full-scale sine
            LevelBus.bands[band] = ((db + 70f) / 70f).coerceIn(0f, 1f)
        }
    }

    private fun fft() {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= N) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < N) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val c = i + k + len / 2
                    val vr = re[c] * cr - im[c] * ci
                    val vi = re[c] * ci + im[c] * cr
                    re[c] = re[a] - vr; im[c] = im[a] - vi
                    re[a] = re[a] + vr; im[a] = im[a] + vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object { const val N = 1024 }
}
