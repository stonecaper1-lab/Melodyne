package org.melodyne.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.TrackOutput
import java.util.concurrent.ConcurrentSkipListMap

/**
 * The size of every compressed audio frame of one stream, keyed by its timestamp. From that, the real bitrate at any
 * moment is just "bytes in this second x 8", so a CBR 320 file reads ~320 and a VBR file visibly moves.
 * Recording is idempotent, so seeking back and re-reading frames never double-counts.
 */
class BitrateSeries {
    private val frames = ConcurrentSkipListMap<Long, Int>()
    @Volatile private var total = 0L

    fun record(timeUs: Long, size: Int) { if (frames.put(timeUs, size) == null) total += size }

    private fun lastUs(): Long = frames.lastEntry()?.key ?: 0L

    /** kbps for the whole second containing [posMs]. The second still being filled is skipped. */
    fun kbpsAt(posMs: Long): Int? {
        val last = lastUs()
        if (last <= 0L) return null
        val filling = last / 1_000_000L
        val sec = minOf(posMs / 1000L, filling - 1)
        if (sec < 0) return null
        val bytes = frames.subMap(sec * 1_000_000L, true, (sec + 1) * 1_000_000L, false).values.fold(0L) { a, v -> a + v }
        return if (bytes == 0L) null else (bytes * 8L / 1000L).toInt()
    }

    /** File bytes x 8 / duration: exact, and independent of whatever the file header claims. */
    fun averageKbps(): Int? {
        val last = lastUs()
        return if (last < 5_000_000L) null else (total * 8L * 1000L / last).toInt()
    }

    /** The last [seconds] seconds up to [posMs], oldest first, for the sparkline. */
    fun recent(posMs: Long, seconds: Int = 40): List<Int> {
        val end = posMs / 1000L
        return ((end - seconds + 1).coerceAtLeast(0L)..end).mapNotNull { kbpsAt(it * 1000L) }
    }
}

class BitrateMeter {
    private val map = object : LinkedHashMap<String, BitrateSeries>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BitrateSeries>?): Boolean = size > 6
    }

    @Synchronized fun series(url: String): BitrateSeries = map.getOrPut(url) { BitrateSeries() }
    @Synchronized fun peek(url: String): BitrateSeries? = map[url]
}

/** Wraps ExoPlayer's extractors so every audio frame's size passes through the meter on its way to the decoder. */
@UnstableApi
class MeteringExtractorsFactory(private val inner: ExtractorsFactory, private val meter: BitrateMeter) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = inner.createExtractors().map<Extractor, Extractor> { MeteringExtractor(it, BitrateSeries()) }.toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: MutableMap<String, MutableList<String>>): Array<Extractor> {
        val series = meter.series(uri.toString())
        return inner.createExtractors(uri, responseHeaders).map<Extractor, Extractor> { MeteringExtractor(it, series) }.toTypedArray()
    }
}

@UnstableApi
private class MeteringExtractor(private val d: Extractor, private val series: BitrateSeries) : Extractor by d {
    override fun init(output: ExtractorOutput) { d.init(MeteringOutput(output, series)) }
}

@UnstableApi
private class MeteringOutput(private val d: ExtractorOutput, private val series: BitrateSeries) : ExtractorOutput by d {
    override fun track(id: Int, type: Int): TrackOutput {
        val t = d.track(id, type)
        return if (type == C.TRACK_TYPE_AUDIO) MeteringTrack(t, series) else t
    }
}

@UnstableApi
private class MeteringTrack(private val d: TrackOutput, private val series: BitrateSeries) : TrackOutput by d {
    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        series.record(timeUs, size)
        d.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }
}
