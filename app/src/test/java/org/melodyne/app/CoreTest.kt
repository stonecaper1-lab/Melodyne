package org.melodyne.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.melodyne.app.ai.AiClient
import org.melodyne.app.audio.EQ_BANDS
import org.melodyne.app.audio.Loudness
import org.melodyne.app.audio.PlaybackState
import org.melodyne.app.data.update.UpdateChecker
import org.melodyne.app.audio.EqMath
import org.melodyne.app.audio.EqPresets
import org.melodyne.app.data.catalog.StreamCrypto
import org.melodyne.app.data.lastfm.LastFmSigner
import org.melodyne.app.data.lastfm.ScrobbleText
import org.melodyne.app.domain.LibraryData
import org.melodyne.app.domain.LibraryOps
import org.melodyne.app.domain.LrcParser
import org.melodyne.app.domain.PlayStat
import org.melodyne.app.domain.Refine
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.SmartLists
import org.melodyne.app.domain.StreamPicker
import org.melodyne.app.domain.UserPlaylist

private fun song(id: String, title: String, artist: String = "Artist", dur: Int = 200, kbps: Int = 320) =
    Song(id, title, artist, "", dur, null, null, "https://x.test/$id.mp4", kbps, false)

class CoreTest {
    // ---- streams, lyrics, scrobbling ----
    @Test fun picksHighestBitrateAndForcesHttps() {
        val r = StreamPicker.pick(listOf(
            "12kbps" to "http://c/12.mp4", "320kbps" to "http://c/320.mp4",
            "96kbps" to "http://c/96.mp4", "160kbps" to "http://c/160.mp4",
        ))
        assertEquals("https://c/320.mp4" to 320, r)
    }

    @Test fun fallsBackToBestAvailableAndHandlesEmpty() {
        assertEquals("https://c/160.mp4" to 160, StreamPicker.pick(listOf("48kbps" to "https://c/48.mp4", "160kbps" to "https://c/160.mp4")))
        assertNull(StreamPicker.pick(emptyList()))
        assertNull(StreamPicker.pick(listOf("320kbps" to "")))
    }

    @Test fun parsesSyncedLrc() {
        val l = LrcParser.parse("[ar:X]\n[00:01.50]Hello\n[00:05.00]World\n[01:00.25]Bye")
        assertTrue(l.synced)
        assertEquals(1500L, l.lines[0].timeMs)
        assertEquals(60250L, l.lines[2].timeMs)
    }

    @Test fun plainLyricsStayPlain() {
        val l = LrcParser.parse("Line one<br>Line two")
        assertFalse(l.synced)
        assertEquals("Line one\nLine two", l.plain)
    }

    @Test fun lastFmSignatureSortsParamsAndIgnoresFormat() {
        assertEquals("1d0396bcbc2c54e569e7af9cf9c4685e", LastFmSigner.sign(mapOf("b" to "2", "a" to "1", "format" to "json"), "s"))
    }

    @Test fun decryptsStreamUrlAndPicks320() {
        val url = StreamCrypto.decrypt("uWha1yBeBEQNMTtLk+2Rs64AiVvJ5+3ESS71sWbL17qQPNKUa/7R1A==")
        assertEquals("http://cdn.example.com/123/abc_96.mp4", url)
        assertEquals("https://cdn.example.com/123/abc_320.mp4", StreamCrypto.withBitrate(url!!, 320))
        assertNull(StreamCrypto.decrypt("not-base64-!!"))
    }

    @Test fun scrobbleTextStripsDecorations() {
        assertEquals("Tum Hi Ho", ScrobbleText.title("Tum Hi Ho (From \"Aashiqui 2\")"))
        assertEquals("Kesariya", ScrobbleText.title("Kesariya [feat. Someone]"))
        assertEquals("Dil Chahta Hai (Remix)", ScrobbleText.title("Dil Chahta Hai (Remix)"))
        assertEquals("Arijit Singh", ScrobbleText.artist("Arijit Singh, Shreya Ghoshal"))
    }

    @Test fun aiReplyParsingToleratesProseAndFences() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val reply = """Sure! Here you go:
```json
[{"title":"Tum Hi Ho","artist":"Arijit Singh"},{"title":"tum hi ho","artist":"arijit singh"},{"title":"","artist":"x"}]
```"""
        val r = AiClient.parse(reply, json)
        assertEquals(1, r.size)
        assertEquals("Tum Hi Ho", r[0].title)
        assertTrue(AiClient.parse("no json here", json).isEmpty())
    }

    // ---- equalizer ----
    @Test fun sevenBandsAndPresetsAreConsistent() {
        assertEquals(7, EQ_BANDS.size)
        EqPresets.all.forEach { assertEquals(it.id, 7, it.gains.size) }
        assertEquals(EqPresets.all.size, EqPresets.all.map { it.id }.toSet().size)
        assertTrue(EqPresets.byId("flat").gains.all { it == 0f })
        assertEquals("flat", EqPresets.byId("nope").id)
    }

    @Test fun filterHitsRequestedGainAndHeadroomMatchesPeak() {
        val fs = 44100.0
        assertEquals(6.0, EqMath.magnitudeDb(EqMath.design(3, 6f, fs), 1000.0, fs), 0.05)
        assertEquals(0.0, EqMath.headroomDb(List(7) { 0f }), 0.0)
        assertEquals(6.0, EqMath.headroomDb(listOf(0f, 0f, 0f, 6f, 0f, 0f, 0f)), 0.15)
        assertEquals(0.0, EqMath.headroomDb(listOf(-3f, -2f, 0f, 0f, 0f, 0f, -4f)), 0.001)   // cuts need no headroom
    }

    // ---- search de-duplication ----
    @Test fun duplicateVersionsCollapseButRealVersionsStay() {
        val raw = listOf(
            song("1", "Tum Hi Ho (From \"Aashiqui 2\")", "Arijit Singh", 262, 160),
            song("2", "Tum Hi Ho", "Arijit Singh, Mithoon", 263, 320),
            song("3", "Tum Hi Ho (Reprise)", "Arijit Singh", 150),
            song("4", "Tum Hi Ho (Lofi)", "Arijit Singh", 262),
            song("5", "Tum Hi Ho", "Someone Else", 262),
        )
        val out = Refine.songs("tum hi ho", raw)
        assertEquals(listOf("2", "5", "3", "4"), out.map { it.id })       // 1 merged into 2 (better bitrate kept)
    }

    @Test fun exactTitleMatchesRankFirst() {
        val out = Refine.songs("kesariya", listOf(song("a", "Other Song"), song("b", "Kesariya Reprise", dur = 100), song("c", "Kesariya", dur = 270)))
        assertEquals("c", out.first().id)
    }

    // ---- library ----
    @Test fun addSongsSkipsDuplicatesAndMoveReorders() {
        val a = song("a", "A"); val b = song("b", "B"); val c = song("c", "C")
        assertEquals(listOf("a", "b", "c"), LibraryOps.addSongs(listOf(a, b), listOf(b, c)).map { it.id })
        assertEquals(listOf("b", "c", "a"), LibraryOps.move(listOf(a, b, c), 0, 2).map { it.id })
        assertEquals(listOf(a, b, c), LibraryOps.move(listOf(a, b, c), 5, 0))
    }

    @Test fun playsAreCountedPerTimeOfDay() {
        val s = song("a", "A")
        var stats = LibraryOps.recordPlay(emptyList(), s, 1_000L, hour = 23)
        stats = LibraryOps.recordPlay(stats, s, 2_000L, hour = 1)
        stats = LibraryOps.recordPlay(stats, s, 3_000L, hour = 9)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].plays); assertEquals(2, stats[0].night); assertEquals(1, stats[0].morning)
        assertEquals(1_000L, stats[0].first); assertEquals(3_000L, stats[0].last)
    }

    @Test fun smartPlaylistsBuildFromHabits() {
        val day = 86_400_000L
        val now = 100 * day
        fun st(id: String, plays: Int, ageDays: Int, dur: Int = 240, night: Int = 0, artist: String = "X") =
            PlayStat(song(id, "T$id", artist, dur), plays, 0, now - ageDays * day, night = night)
        val stats = listOf(
            st("1", 9, 1, night = 9), st("2", 7, 30, night = 7), st("3", 5, 40, night = 5),
            st("4", 2, 60, dur = 150), st("5", 1, 2, dur = 180), st("6", 1, 3, dur = 400),
        )
        val lists = SmartLists.build(stats, now).associateBy { it.id }
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), lists.getValue("most").songs.map { it.id })
        assertEquals(listOf("2", "3", "4"), lists.getValue("rediscover").songs.map { it.id })   // 2+ plays, idle > 14 days
        assertEquals(listOf("1", "2", "3"), lists.getValue("night").songs.map { it.id })
        assertTrue("short" !in lists || lists.getValue("short").songs.size >= 3)
        assertTrue(SmartLists.build(emptyList(), now).isEmpty())
    }

    @Test fun importMergesWithoutDuplicatingOrLosingNewerEdits() {
        val a = UserPlaylist("p1", "Old name", 1, 10, listOf(song("a", "A")))
        val aNewer = a.copy(name = "New name", updatedAt = 20)
        val b = UserPlaylist("p2", "Other", 1, 5)
        val (merged, changed) = LibraryOps.merge(LibraryData(listOf(a)), LibraryData(listOf(aNewer, b)))
        assertEquals(2, changed)
        assertEquals(listOf("New name", "Other"), merged.playlists.map { it.name })
        val (again, none) = LibraryOps.merge(merged, LibraryData(listOf(a)))        // an older copy must not win
        assertEquals(0, none)
        assertEquals("New name", again.playlists.first().name)
    }

    // ---- loudness, audiophile, updates ----
    @Test fun kWeightingMatchesTheItuReferenceAt48k() {
        val s1 = Loudness.stage1(48000.0)
        assertEquals(1.535125, s1[0], 1e-5); assertEquals(-2.691696, s1[1], 1e-5); assertEquals(1.198393, s1[2], 1e-5)
        assertEquals(-1.690659, s1[3], 1e-5); assertEquals(0.732481, s1[4], 1e-5)
        val s2 = Loudness.stage2(48000.0)
        assertEquals(-1.990047, s2[3], 1e-5); assertEquals(0.990072, s2[4], 1e-5)
    }

    @Test fun normalisationGainIsClampedAndPeakAware() {
        assertEquals(-6.0, Loudness.gainDb(-8.0, 0.5), 1e-9)                 // louder than target: turn down
        assertEquals(3.0, Loudness.gainDb(-20.0, 0.1), 1e-9)                 // quiet but safe: boost, capped at +3 dB
        assertEquals(-12.0, Loudness.gainDb(5.0, 0.5), 1e-9)                 // absurdly loud: floor at -12 dB
        assertEquals(0.415, Loudness.gainDb(-20.0, 0.9), 0.01)               // a hot peak limits the boost
    }

    @Test fun audiophileModeAlwaysSwitchesNormalisationOff() {
        assertTrue(PlaybackState(audiophile = false, normalize = true).normalizeEffective)
        assertFalse(PlaybackState(audiophile = true, normalize = true).normalizeEffective)
        assertFalse(PlaybackState(audiophile = false, normalize = false).normalizeEffective)
    }

    @Test fun versionComparisonIsNumericNotAlphabetic() {
        assertTrue(UpdateChecker.isNewer("v0.10.0", "0.9.4"))
        assertTrue(UpdateChecker.isNewer("0.5.0", "0.4.0"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.5.0", "0.5.0"))
        assertFalse(UpdateChecker.isNewer("v0.4.9", "0.5.0"))
        assertFalse(UpdateChecker.isNewer("0.5", "0.5.0"))
    }
}
