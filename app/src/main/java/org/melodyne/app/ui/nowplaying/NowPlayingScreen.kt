package org.melodyne.app.ui.nowplaying

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Lyrics
import org.melodyne.app.player.AudioQuality
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.player.PlayerUiState
import org.melodyne.app.ui.AddButton
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Cover
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.IconBtn
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalAddToPlaylist
import org.melodyne.app.ui.LocalOpenChain
import org.melodyne.app.ui.LocalOpenGenre
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Sparkline
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.fmtMs
import org.melodyne.app.ui.glass
import org.melodyne.app.ui.glassSheet
import org.melodyne.app.ui.sound.EqPanel
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(conn: PlayerConnection, ps: PlayerUiState, onClose: () -> Unit) {
    val vm: NowPlayingViewModel = viewModel(factory = NowPlayingViewModel.Factory)
    val lyrics by vm.lyrics.collectAsState()
    val info by vm.info.collectAsState()
    val eqState by ServiceLocator.eq.state.collectAsState()
    val playback = ServiceLocator.playback
    val pb by playback.state.collectAsState()
    val accent = LocalAccent.current
    val addToPlaylist = LocalAddToPlaylist.current
    val openChain = LocalOpenChain.current
    val openGenre = LocalOpenGenre.current
    LaunchedEffect(ps.songId) { vm.load(ps.currentSong) }

    var showLyrics by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    // The clock and the live bitrate live in State holders that only small children read, so the artwork and the rest
    // of the screen never recompose with them (that was the flicker).
    val pos = remember { mutableLongStateOf(0L) }
    val dur = remember { mutableLongStateOf(0L) }
    val live = remember { mutableIntStateOf(0) }
    val avg = remember { mutableIntStateOf(0) }
    val spark = remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(ps.isPlaying, ps.songId) {
        var n = 0
        while (true) {
            pos.longValue = conn.position(); dur.longValue = conn.duration()
            val series = ps.currentSong?.streamUrl?.let { ServiceLocator.bitrate.peek(it) }
            val k = series?.kbpsAt(pos.longValue) ?: 0
            if (live.intValue != k) live.intValue = k
            val a = series?.averageKbps() ?: 0
            if (avg.intValue != a) avg.intValue = a
            if (n++ % 4 == 0) spark.value = series?.recent(pos.longValue) ?: emptyList()
            delay(if (ps.isPlaying) 250 else 1000)
        }
    }
    BackHandler(enabled = showEq || showLyrics) { showEq = false; showLyrics = false }

    // Album art "expands" on open, then breathes with play/pause.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        if (entered) 1f else 0.55f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow), label = "enter",
    )
    val breathe by animateFloatAsState(
        if (ps.isPlaying) 1f else 0.92f, spring(stiffness = Spring.StiffnessMediumLow), label = "breathe",
    )

    Box(Modifier.fillMaxSize().background(Amoled.Black)) {
        // Liquid backdrop: the artwork, blurred and sunk into the dark. "Liquid glow" off keeps it pure AMOLED black.
        if (pb.glow && ps.artUrl != null) {
            AsyncImage(
                model = ps.artUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(64.dp) else Modifier).graphicsLayer { alpha = 0.5f },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color(0xE6000000), Color.Black))))
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            // Top bar: close | title | infinite, EQ, add to playlist
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(G.Down, Amoled.OnBg, onClick = onClose)
                Txt(
                    if (pb.audiophile) "AUDIOPHILE · CHAIN" else "NOW PLAYING",
                    Modifier.weight(1f).clickable(enabled = pb.audiophile, onClick = openChain),
                    size = 12.sp, color = if (pb.audiophile) accent else Amoled.Muted, weight = FontWeight.SemiBold, align = TextAlign.Center,
                )
                Chip("∞", pb.infinite) { playback.setInfinite(!pb.infinite) }
                Spacer(Modifier.width(6.dp))
                Chip("EQ", eqState.shaping && !pb.audiophile) { showEq = true }
                AddButton { ps.currentSong?.let(addToPlaylist) }
            }

            // Artwork: swipe left/right for next/previous, swipe up for lyrics. The live bitrate sits on its corner.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.aspectRatio(1f)
                        .graphicsLayer { scaleX = enter * breathe; scaleY = enter * breathe }
                        .pointerInput(Unit) {
                            var dx = 0f
                            var dy = 0f
                            val threshold = 80.dp.toPx()
                            detectDragGestures(
                                onDragStart = { dx = 0f; dy = 0f },
                                onDragCancel = { dx = 0f; dy = 0f },
                                onDragEnd = {
                                    when {
                                        abs(dx) > threshold && abs(dx) > abs(dy) -> { if (dx < 0) conn.next() else conn.prev() }
                                        dy < -threshold && abs(dy) > abs(dx) -> { showLyrics = true }
                                    }
                                },
                            ) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
                        },
                ) {
                    Cover(ps.artUrl, Modifier.fillMaxSize(), radius = 30.dp)
                    QualityBadge(ps.quality, live, avg, spark, Modifier.align(Alignment.BottomStart).padding(12.dp), onArt = true)
                }
            }

            // Title, artist, genre tags (tap one to open it) and the lyrics button
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt(ps.title, size = 26.sp, weight = FontWeight.ExtraBold)
                    Txt(ps.artist, size = 16.sp, color = Amoled.Muted)
                }
                Chip("Lyrics", showLyrics) { showLyrics = true }
            }
            info?.let { i ->
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    i.tags.forEach { tag ->
                        Txt(
                            "#$tag",
                            Modifier.glass(RoundedCornerShape(50), alpha = 0.07f).clickable { openGenre(tag) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            size = 12.sp, weight = FontWeight.SemiBold, color = accent,
                        )
                    }
                    i.listeners?.let { Txt(compact(it) + " listeners", Modifier.padding(vertical = 6.dp), size = 12.sp, color = Amoled.Muted) }
                }
            }

            SeekBar(pos, dur, accent, Modifier.padding(top = 12.dp)) { ms -> conn.seekTo(ms); pos.longValue = ms }
            TimeRow(pos, dur)

            // Transport on its own piece of glass
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp).glass(RoundedCornerShape(40.dp), alpha = 0.07f, strong = true).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(G.Prev, Amoled.OnBg, glyphSize = 32.dp, onClick = conn::prev)
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f).compositeOver(accent), accent)))
                        .clickable(onClick = conn::toggle),
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (ps.isPlaying) G.Pause else G.Play, Color.Black, 34.dp) }
                IconBtn(G.Next, Amoled.OnBg, glyphSize = 32.dp, onClick = conn::next)
            }
        }

        if (showEq || showLyrics) Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { showEq = false; showLyrics = false })

        // Equalizer sheet
        AnimatedVisibility(
            visible = showEq,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.fillMaxWidth().glassSheet().clickable(enabled = false) {}.navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EqPanel()
                Pill("Done", onClick = { showEq = false })
            }
        }

        // Lyrics sheet (LRCLIB first, time-synced when available)
        AnimatedVisibility(
            visible = showLyrics,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.7f).glassSheet().clickable(enabled = false) {}
                    .navigationBarsPadding().padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Lyrics", Modifier.weight(1f), size = 20.sp, weight = FontWeight.ExtraBold)
                    Chip("Close", false) { showLyrics = false }
                }
                LyricsPanel(lyrics, pos, accent, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0fK".format(n / 1_000.0)
    else -> "$n"
}

/**
 * Exact stream quality. The big number is counted from the compressed audio frames as they play (updated every
 * second, so a variable-bitrate file visibly moves); "avg" is file size / duration. Tap for the plain-language
 * explanation and the last 40 seconds as a line.
 */
@Composable
fun QualityBadge(
    q: AudioQuality?, live: IntState, avg: IntState, spark: State<List<Int>>,
    modifier: Modifier = Modifier, onArt: Boolean = false,
) {
    val accent = LocalAccent.current
    var open by remember { mutableStateOf(false) }
    val shown by animateIntAsState(live.intValue, tween(450), label = "kbps")
    val measured = live.intValue > 0
    val text = if (measured) "$shown kbps ${q?.codec ?: "AAC"}" else q?.label() ?: "Loading stream…"
    val detail = listOfNotNull(
        avg.intValue.takeIf { it > 0 }?.let { "avg $it" },
        q?.detail()?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .semantics { contentDescription = "Audio quality $text $detail" }
            .then(if (onArt) Modifier.clip(shape).background(Color(0xCC000000)) else Modifier)
            .glass(shape, alpha = 0.06f, strong = true)
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (measured || q?.verified == true) Amoled.Ok else Amoled.Warn))
            Spacer(Modifier.width(8.dp))
            Txt(text, size = 12.sp, color = accent, weight = FontWeight.Bold)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Txt(detail, size = 11.sp, color = Amoled.Muted)
            }
        }
        if (open && q != null) {
            Sparkline(spark.value, accent, Modifier.fillMaxWidth().height(34.dp))
            Txt(
                if (measured) "Counted from the audio frames as they play, updated every second. avg = file size ÷ duration."
                else "The server's label. The stream hasn't confirmed it yet.",
                size = 11.sp, color = Amoled.Muted, maxLines = 3,
            )
        }
    }
}

@Composable
private fun TimeRow(pos: LongState, dur: LongState) {
    Row(Modifier.fillMaxWidth()) {
        Txt(fmtMs(pos.longValue), size = 12.sp, color = Amoled.Muted)
        Spacer(Modifier.weight(1f))
        Txt(fmtMs(dur.longValue), size = 12.sp, color = Amoled.Muted)
    }
}

@Composable
private fun SeekBar(pos: LongState, durState: LongState, accent: Color, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    val dur = durState.longValue
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (dur > 0) (pos.longValue.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { f -> if (dur > 0) onSeek((f * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = 4.dp.toPx()
            val y = size.height / 2 - t / 2
            drawRoundRect(Color(0xFF2A2A2A), Offset(0f, y), Size(size.width, t), CornerRadius(t / 2))
            drawRoundRect(accent, Offset(0f, y), Size(size.width * frac, t), CornerRadius(t / 2))
            drawCircle(accent, 7.dp.toPx(), Offset(size.width * frac, size.height / 2))
        }
    }
}

@Composable
private fun LyricsPanel(lyrics: Lyrics?, pos: LongState, accent: Color, modifier: Modifier) {
    when {
        lyrics == null || (lyrics.lines.isEmpty() && lyrics.plain.isBlank()) ->
            Box(modifier, contentAlignment = Alignment.Center) { Txt("No lyrics available", color = Amoled.Muted) }

        lyrics.synced -> {
            // Reading the clock inside derivedStateOf means only a change of line recomposes this panel.
            val active by remember(lyrics) { derivedStateOf { lyrics.lines.indexOfLast { it.timeMs <= pos.longValue }.coerceAtLeast(0) } }
            val list = rememberLazyListState()
            LaunchedEffect(active) { list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
            LazyColumn(modifier, list, contentPadding = PaddingValues(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(lyrics.lines) { i, line ->
                    val c by animateColorAsState(if (i == active) accent else Color(0xFF5A5A5A), label = "lyric")
                    Txt(line.text.ifBlank { "♪" }, size = 22.sp, color = c, weight = FontWeight.Bold, maxLines = 6, lineHeight = 28.sp)
                }
            }
        }

        else -> Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
            Txt(lyrics.plain, size = 18.sp, color = Amoled.OnBg, maxLines = Int.MAX_VALUE, lineHeight = 26.sp)
        }
    }
}
