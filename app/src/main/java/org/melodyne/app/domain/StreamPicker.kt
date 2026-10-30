package org.melodyne.app.domain

/** Picks the highest-bitrate entry of a `downloadUrl`-style list (12/48/96/160/320 kbps). */
object StreamPicker {
    /** @param entries pairs of (quality label such as "320kbps", url). Returns (https url, kbps). */
    fun pick(entries: List<Pair<String, String>>): Pair<String, Int>? =
        entries.mapNotNull { (quality, url) ->
            val kbps = quality.filter(Char::isDigit).toIntOrNull()
            if (kbps == null || url.isBlank()) null else kbps to url
        }.maxByOrNull { it.first }?.let { (kbps, url) -> toHttps(url) to kbps }

    // Cleartext traffic is disabled in the manifest, and the CDN serves https fine.
    private fun toHttps(url: String) =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
}
