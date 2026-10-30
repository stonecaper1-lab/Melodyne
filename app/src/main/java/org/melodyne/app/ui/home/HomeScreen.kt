package org.melodyne.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.melodyne.app.data.repo.MoodService
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Song
import org.melodyne.app.player.Chain
import org.melodyne.app.player.OutputInspector
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.AddButton
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Cover
import org.melodyne.app.ui.ErrorCard
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalAddToPlaylist
import org.melodyne.app.ui.LocalOpenChain
import org.melodyne.app.ui.MelodyneMark
import org.melodyne.app.ui.Panel
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.SectionTitle
import org.melodyne.app.ui.SkeletonRow
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.fmtSec

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Late night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        h < 22 -> "Good evening"
        else -> "Night owl"
    }
}

@Composable
fun HomeScreen(conn: PlayerConnection, onOpenSearch: () -> Unit, onOpenAi: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val s by vm.ui.collectAsState()
    val ps by conn.state.collectAsState()
    val pb by ServiceLocator.playback.state.collectAsState()
    val update by ServiceLocator.updates.available.collectAsState()
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val openChain = LocalOpenChain.current
    val accent = LocalAccent.current
    val hello = remember { greeting() }
    val pad = Modifier.padding(horizontal = 20.dp)
    var chain by remember { mutableStateOf<Chain?>(null) }

    LaunchedEffect(Unit) { vm.refreshPersonal() }
    LaunchedEffect(pb.audiophile, ps.quality) {
        if (!pb.audiophile) {
            chain = null
        } else while (true) {
            chain = OutputInspector.chain(ctx, ps.quality, pb, ServiceLocator.eq.state.value, "")
            delay(3000)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // App bar: mark, wordmark, Audiophile switch; then greeting and search
        item {
            Column(pad.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MelodyneMark(ps.isPlaying, accent)
                    Txt("melodyne", Modifier.padding(start = 8.dp).weight(1f), size = 17.sp, weight = FontWeight.Bold, color = Amoled.Muted)
                    Chip("AUDIOPHILE", pb.audiophile) { ServiceLocator.playback.setAudiophile(!pb.audiophile) }
                }
                Txt(hello, size = 34.sp, weight = FontWeight.ExtraBold)
                chain?.let { c ->
                    Txt(
                        c.verdict + "  ·  tap for details", Modifier.clickable(onClick = openChain), size = 12.sp,
                        color = if (c.clean) Amoled.Ok else Amoled.Warn, maxLines = 2,
                    )
                }
                Txt(
                    "Search songs, artists, albums…", size = 15.sp, color = Amoled.Muted,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).border(1.dp, Amoled.SurfaceHi, RoundedCornerShape(50))
                        .clickable(onClick = onOpenSearch).padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
        update?.let { u ->
            item {
                Panel("Update available · v${u.version}", pad.padding(top = 16.dp)) {
                    Txt(
                        u.notes.lines().filter { it.isNotBlank() }.take(4).joinToString("\n").ifBlank { "A new version of Melodyne is ready." },
                        size = 13.sp, color = Amoled.Muted, maxLines = 6,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Download") {
                            val direct = u.apkUrl
                            uri.openUri(if (ServiceLocator.updates.token.isBlank() && direct != null) direct else u.pageUrl)
                        }
                        Pill("Later", filled = false) { ServiceLocator.updates.dismiss() }
                    }
                }
            }
        }
        s.error?.let { msg -> item { ErrorCard(msg, onRetry = vm::load) } }

        // Jump back in, or a first-run nudge
        item {
            val last = s.recents.firstOrNull()
            Box(pad.padding(top = 20.dp)) {
                if (last != null) HeroCard(last) { conn.play(s.recents, 0) }
                else Panel("Make your first mix") {
                    Txt("Describe a mood and Melodyne builds a playlist you can play right away.", size = 13.sp, color = Amoled.Muted, maxLines = 3)
                    Pill("Try AI Mix", onClick = onOpenAi)
                }
            }
        }

        // Moods come from Last.fm tags, never from words in a title
        item { SectionTitle("Moods") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(MoodService.ALL) { mood ->
                    val busy = s.loadingMood == mood.label
                    Txt(
                        if (busy) "${mood.label}…" else mood.label, size = 14.sp, weight = FontWeight.SemiBold,
                        color = if (busy) Color.Black else accent,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (busy) accent else Color.Transparent)
                            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .clickable { vm.playMood(mood.label) { songs -> conn.play(songs, 0) } }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    )
                }
            }
        }
        if (!s.lastFmReady) item {
            Txt("Add a free Last.fm key in Settings for smarter moods and personal rows.", pad.padding(top = 8.dp), size = 11.sp, color = Amoled.Muted, maxLines = 2)
        }

        if (s.onRepeat.isNotEmpty()) {
            item { SectionTitle("On repeat") }
            item { CircleRail(s.onRepeat) { i -> conn.play(s.onRepeat, i) } }
        }
        if (s.recents.size > 1) {
            item { SectionTitle("Recently played") }
            item { CircleRail(s.recents.drop(1)) { i -> conn.play(s.recents, i + 1) } }
        }
        if (s.because.isNotEmpty()) {
            item { SectionTitle("Because you listened to ${s.becauseArtist}") }
            item { SquareRail(s.because) { i -> conn.play(s.because, i) } }
        }

        item { SectionTitle(s.trendingTitle) }
        if (s.loading && s.trending.isEmpty()) items(6) { SkeletonRow() }
        itemsIndexed(s.trending.take(10)) { i, song -> ChartRow(i + 1, song, accent) { conn.play(s.trending, i) } }

        if (s.playlists.isNotEmpty()) {
            item { SectionTitle("Playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.playlists) { pl ->
                        Row(
                            Modifier.width(240.dp).height(72.dp).clip(RoundedCornerShape(18.dp)).background(Amoled.Surface)
                                .clickable { vm.playPlaylist(pl.id) { songs -> conn.play(songs, 0) } }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(pl.artUrl, Modifier.size(52.dp), radius = 12.dp)
                            Txt(pl.name, Modifier.padding(start = 12.dp), size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
                        }
                    }
                }
            }
        }

        if (s.trending.size > 10) {
            item { SectionTitle("More to explore") }
            itemsIndexed(s.trending.drop(10)) { i, song -> SongRow(song) { conn.play(s.trending, i + 10) } }
        }
    }
}

@Composable
private fun CircleRail(songs: List<Song>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(songs) { i, song ->
            Column(
                Modifier.width(88.dp).clickable { onPlay(i) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Cover(song.thumbUrl, Modifier.size(88.dp), radius = 44.dp)
                Txt(song.title, size = 12.sp, weight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SquareRail(songs: List<Song>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(songs) { i, song ->
            Column(Modifier.width(128.dp).clickable { onPlay(i) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Cover(song.thumbUrl, Modifier.size(128.dp), radius = 18.dp)
                Txt(song.title, size = 13.sp, weight = FontWeight.Medium)
                Txt(song.artist, size = 11.sp, color = Amoled.Muted)
            }
        }
    }
}

@Composable
private fun HeroCard(song: Song, onPlay: () -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = onPlay)) {
        Cover(song.artUrl, Modifier.fillMaxSize(), radius = 0.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000), Color.Black))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp, end = 84.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt("JUMP BACK IN", size = 11.sp, weight = FontWeight.ExtraBold, color = accent)
            Txt(song.title, size = 22.sp, weight = FontWeight.ExtraBold)
            Txt(song.artist, size = 14.sp, color = Amoled.Muted)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(18.dp).size(54.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Play, Color.Black, 26.dp) }
    }
}

@Composable
private fun ChartRow(rank: Int, song: Song, accent: Color, onClick: () -> Unit) {
    val addToPlaylist = LocalAddToPlaylist.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("%02d".format(rank), Modifier.width(40.dp), size = 20.sp, weight = FontWeight.Black, color = if (rank <= 3) accent else Color(0xFF4A4A4A))
        Cover(song.thumbUrl, Modifier.size(48.dp), radius = 24.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(song.title, size = 16.sp, weight = FontWeight.Medium)
            Txt(song.artist, size = 13.sp, color = Amoled.Muted)
        }
        Txt(fmtSec(song.durationSec), size = 12.sp, color = Amoled.Muted)
        AddButton { addToPlaylist(song) }
    }
}
