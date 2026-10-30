package org.melodyne.app.domain

/** Parses LRC ("[mm:ss.xx] text") into timed lines; anything else becomes plain text. */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val meta = Regex("""^\[[a-zA-Z]+:.*]$""")
    private val br = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Lyrics {
        val text = raw.replace(br, "\n").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
        val timed = ArrayList<LyricLine>()
        for (line in text.lines()) {
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val body = line.replace(stamp, "").trim()
            for (m in stamps) {
                val (mm, ss, frac) = m.destructured
                val ms = (mm.toLong() * 60 + ss.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong()
                timed += LyricLine(ms, body)
            }
        }
        val plain = text.lines()
            .map { it.replace(stamp, "").trim() }
            .filterNot { meta.matches(it) }
            .joinToString("\n").trim()
        return Lyrics(timed.sortedBy { it.timeMs }, plain)
    }
}
