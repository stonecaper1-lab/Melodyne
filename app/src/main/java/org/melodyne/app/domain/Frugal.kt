package org.melodyne.app.domain

/** Helpers for Frugal mode, which trades sound quality for mobile data and battery. */
object Frugal {
    private val pattern = Regex("_\\d+\\.mp4")

    /** Swaps the bitrate in a catalogue stream URL (..._320.mp4 becomes ..._96.mp4). Other URLs are left alone. */
    fun streamUrl(url: String, kbps: Int): String = if (pattern.containsMatchIn(url)) pattern.replace(url, "_$kbps.mp4") else url

    fun mbPerMinute(kbps: Int): Double = kbps * 60.0 / 8.0 / 1000.0
}
