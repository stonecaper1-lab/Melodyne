package org.melodyne.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.melodyne.app.audio.Loudness
import org.melodyne.app.audio.NormalizerController
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Volume normalisation in the PCM domain (no re-encoding). While a song plays, its loudness is measured with
 * BS.1770 K-weighting and absolute gating over the first few seconds; the gain that brings it to -14 LUFS is then
 * faded in over two seconds and remembered for that song. Off = input copied through untouched.
 */
@UnstableApi
class LoudnessProcessor(private val ctl: NormalizerController) : BaseAudioProcessor() {
    private var channels = 2
    private var rate = 44100
    private var s1 = DoubleArray(5)
    private var s2 = DoubleArray(5)
    private var z = Array(2) { DoubleArray(4) }          // per channel: z1,z2 of stage 1, z1,z2 of stage 2

    private var key: String? = null
    private var measuring = false
    private var blockFrames = 4410
    private var blockSum = 0.0
    private var blockCount = 0
    private var totalBlocks = 0
    private var gatedBlocks = 0
    private var gatedEnergy = 0.0
    private var peak = 0.0

    private var cur = 1.0
    private var target = 1.0
    private var step = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        s1 = Loudness.stage1(rate.toDouble())
        s2 = Loudness.stage2(rate.toDouble())
        blockFrames = rate / 10
        key = null
        return inputAudioFormat
    }

    override fun onFlush() { resetFilters() }
    override fun onReset() { resetFilters() }

    private fun resetFilters() { z = Array(2) { DoubleArray(4) } }

    private fun startMeasuring() {
        measuring = true; blockSum = 0.0; blockCount = 0; totalBlocks = 0; gatedBlocks = 0; gatedEnergy = 0.0; peak = 0.0
        resetFilters()
    }

    private fun fadeTo(linear: Double, frames: Int) {
        target = linear
        step = (target - cur) / frames.coerceAtLeast(1)
    }

    /** Detects a song change (set by the service) and picks up a remembered gain if there is one. */
    private fun trackCheck() {
        val k = ctl.currentKey
        if (k == key) return
        key = k
        val remembered = k?.let { ctl.cached(it) }
        if (remembered != null) {
            measuring = false
            fadeTo(10.0.pow(remembered / 20.0), rate / 2)
            ctl.publish(remembered)
        } else {
            startMeasuring()
            fadeTo(1.0, rate / 2)
            ctl.publish(null)
        }
    }

    private fun finishMeasuring() {
        measuring = false
        if (gatedBlocks < 10) { ctl.publish(0f); return }                  // too quiet to judge: leave it alone
        val integrated = Loudness.lufs(gatedEnergy / gatedBlocks)
        val db = Loudness.gainDb(integrated, peak).toFloat()
        key?.let { ctl.remember(it, db) }
        fadeTo(10.0.pow(db / 20.0), rate * 2)
        ctl.publish(db)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        if (!ctl.enabled) {
            cur = 1.0; target = 1.0; step = 0.0; key = null; measuring = false
            ctl.publish(null)
            out.put(inputBuffer)
            out.flip()
            return
        }
        trackCheck()
        val frames = size / (2 * channels)
        for (f in 0 until frames) {
            if (cur != target) {
                cur += step
                if ((step > 0 && cur >= target) || (step < 0 && cur <= target)) cur = target
            }
            for (ch in 0 until channels) {
                val raw = inputBuffer.getShort().toDouble() / 32768.0
                if (measuring) {
                    val a = abs(raw); if (a > peak) peak = a
                    val y1 = s1[0] * raw + z[ch][0]
                    z[ch][0] = s1[1] * raw - s1[3] * y1 + z[ch][1]
                    z[ch][1] = s1[2] * raw - s1[4] * y1
                    val y2 = s2[0] * y1 + z[ch][2]
                    z[ch][2] = s2[1] * y1 - s2[3] * y2 + z[ch][3]
                    z[ch][3] = s2[2] * y1 - s2[4] * y2
                    blockSum += y2 * y2
                }
                out.putShort((raw * cur * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            if (measuring && ++blockCount >= blockFrames) {
                val ms = blockSum / blockFrames
                blockSum = 0.0; blockCount = 0; totalBlocks++
                if (ms > 0.0 && Loudness.lufs(ms) > -70.0) { gatedEnergy += ms; gatedBlocks++ }
                if (gatedBlocks >= 60 || (totalBlocks >= 200 && gatedBlocks >= 10) || totalBlocks >= 400) finishMeasuring()
            }
        }
        inputBuffer.position(inputBuffer.limit())
        while (out.position() < size) out.put(0.toByte())
        out.flip()
    }
}
