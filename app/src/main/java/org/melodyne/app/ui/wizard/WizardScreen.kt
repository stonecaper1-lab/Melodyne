package org.melodyne.app.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.ui.AmbientGlow
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.MelodyneMark
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.glass

private data class TourPage(val kicker: String, val title: String, val body: String, val points: List<Pair<String, String>>)

private val PAGES = listOf(
    TourPage(
        "WELCOME", "Music, without the noise",
        "Melodyne is a free, tiny music player. No accounts, no ads, no trackers, and your library never leaves your phone.",
        listOf(
            "Measured, not claimed" to "What you see about the sound is counted from the audio itself.",
            "Nothing added by default" to "Your music isn't boosted, squeezed or re-encoded unless you ask.",
            "Honest limits" to "We tell you what the source can and can't do, instead of hiding it.",
        ),
    ),
    TourPage(
        "THE NUMBERS", "What you see is what you hear",
        "The bitrate on Now Playing is counted from every audio frame as it plays, updated each second. Tap it for a live chart and the true average.",
        listOf(
            "Live kbps" to "A variable-bitrate song visibly moves; a constant one stays put.",
            "Real average" to "File size divided by duration, independent of what the file header claims.",
            "Streams up to 320 kbps AAC" to "The best this source offers, picked automatically.",
        ),
    ),
    TourPage(
        "AUDIOPHILE MODE", "Keep the signal clean",
        "Audiophile Mode bypasses the EQ, volume normalisation and crossfade, so the song reaches your phone's output exactly as decoded.",
        listOf(
            "Signal chain" to "Source, decode, processing, OS path and output, with the point where it stops being clean.",
            "USB DAC, Android 14+" to "Melodyne asks for Android's bit-perfect path, so nothing resamples it.",
            "What it can't do" to "A 320 kbps AAC stream is lossy; we can only avoid making it worse. Bluetooth always re-encodes.",
        ),
    ),
    TourPage(
        "SHAPE THE SOUND", "Tools that don't hurt the sound",
        "A seven-band EQ with automatic headroom, dithered output, volume normalisation that can be gentle, and crossfade when you want it.",
        listOf(
            "Car & Bluetooth" to "A curve, plus stereo width, for road noise and Bluetooth coding.",
            "Dynamics readout" to "See when a song is a heavily compressed master.",
            "Crossfade" to "Two songs blend, then it's skipped for the last song and repeat-one.",
        ),
    ),
    TourPage(
        "DISCOVER", "Music that keeps coming",
        "Infinite play queues similar songs before the queue ends. Moods and the genre cloud come from what listeners really tag.",
        listOf(
            "Top 10 in every language" to "Refreshed through the day, with arrows for what moved.",
            "Artists, albums, composers" to "Tap a name on Now Playing to explore.",
            "AI Mix" to "Describe a moment and get a playlist (bring your own key).",
        ),
    ),
    TourPage(
        "YOURS", "Make it yours",
        "Playlists and smart playlists live in the app's own storage and can be exported any time. Skins turn the player into a retro favourite.",
        listOf(
            "Skins that work" to "Classic Amp, Cassette and Receiver show real spectrum, levels and state.",
            "Liquid glass" to "Or switch the glow off for pure AMOLED black.",
            "Last.fm" to "Add a free key in Settings to unlock tags, charts and scrobbling.",
        ),
    ),
)

/** The welcome tour. It appears once per install (and once after the update that adds it); Settings can replay it. */
@Composable
fun WizardScreen(onFinish: () -> Unit, onOpenSettings: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pb by ServiceLocator.playback.state.collectAsState()
    val accent = LocalAccent.current
    val last = page == PAGES.lastIndex
    BackHandler { if (page > 0) page-- else onFinish() }

    Box(Modifier.fillMaxSize().background(Amoled.Black).clickable(enabled = false) {}) {
        AmbientGlow(accent)
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MelodyneMark(true, accent)
                Spacer(Modifier.weight(1f))
                Txt("Skip", Modifier.clickable(onClick = onFinish).padding(10.dp), size = 14.sp, color = Amoled.Muted)
            }
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(200))) },
                label = "tour",
            ) { p ->
                val t = PAGES[p]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt(t.kicker, Modifier.padding(top = 12.dp), size = 12.sp, weight = FontWeight.ExtraBold, color = accent)
                    Txt(t.title, size = 32.sp, weight = FontWeight.Black, maxLines = 3)
                    Txt(t.body, size = 15.sp, color = Amoled.Muted, maxLines = 8)
                    t.points.forEach { (head, text) ->
                        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.07f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(head, size = 15.sp, weight = FontWeight.Bold)
                            Txt(text, size = 13.sp, color = Amoled.Muted, maxLines = 4)
                        }
                    }
                    if (p == 2) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Txt("Try it now", size = 13.sp, color = Amoled.Muted)
                            Chip(if (pb.audiophile) "Audiophile: On" else "Audiophile: Off", pb.audiophile) { ServiceLocator.playback.setAudiophile(!pb.audiophile) }
                        }
                    }
                    if (p == 5) Pill("Add my Last.fm key", filled = false) { onOpenSettings(); onFinish() }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.padding(horizontal = 4.dp).size(if (i == page) 10.dp else 7.dp).clip(CircleShape).background(if (i == page) accent else Amoled.SurfaceHi))
                }
            }
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) Pill("Back", filled = false) { page-- }
                Spacer(Modifier.weight(1f))
                Pill(if (last) "Get started" else "Next") { if (last) onFinish() else page++ }
            }
        }
    }
}
