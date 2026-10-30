package org.melodyne.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Song
import org.melodyne.app.ui.audio.ChainSheet
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.player.PlayerUiState
import org.melodyne.app.ui.ai.AiScreen
import org.melodyne.app.ui.home.HomeScreen
import org.melodyne.app.ui.nowplaying.NowPlayingScreen
import org.melodyne.app.ui.search.SearchScreen
import org.melodyne.app.ui.settings.SettingsScreen
import org.melodyne.app.ui.library.AddToPlaylistDialog
import org.melodyne.app.ui.library.LibraryScreen

@Composable
fun App(conn: PlayerConnection, openSignal: Int = 0) {
    val ps by conn.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    var chainOpen by remember { mutableStateOf(false) }
    var easter by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ServiceLocator.updates.check(false) }
    val accent = rememberAccent(ps.artUrl)
    var handled by remember { mutableStateOf(0) }
    LaunchedEffect(openSignal, ps.hasItem) {
        if (openSignal > handled && ps.hasItem) { expanded = true; handled = openSignal }
    }

    CompositionLocalProvider(LocalAccent provides accent, LocalAddToPlaylist provides { song: Song -> pickerSong = song },
        LocalOpenChain provides { chainOpen = true }, LocalOpenEaster provides { easter = true }) {
        Box(Modifier.fillMaxSize().background(Amoled.Black)) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> HomeScreen(conn, onOpenSearch = { tab = 1 }, onOpenAi = { tab = 2 })
                        1 -> SearchScreen(conn)
                        2 -> AiScreen(conn, onOpenSettings = { tab = 4 })
                        3 -> LibraryScreen(conn)
                        else -> SettingsScreen()
                    }
                }
                if (ps.hasItem) MiniPlayer(ps, conn) { expanded = true }
                BottomNav(tab) { tab = it }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = slideInVertically(tween(340)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
            ) {
                NowPlayingScreen(conn, ps, onClose = { expanded = false })
            }
            pickerSong?.let { song -> AddToPlaylistDialog(song) { pickerSong = null } }
            if (chainOpen) ChainSheet { chainOpen = false }
            if (easter) EasterScreen { easter = false }
        }
    }
    BackHandler(enabled = expanded) { expanded = false }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().height(56.dp).background(Amoled.Black), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Home", "Search", "AI Mix", "Library", "Settings").forEachIndexed { i, label ->
            Column(
                Modifier.weight(1f).fillMaxSize().clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Txt(label, size = 12.sp, weight = FontWeight.SemiBold, color = if (i == selected) accent else Amoled.Muted)
                Box(Modifier.padding(top = 5.dp).size(5.dp).clip(CircleShape).background(if (i == selected) accent else Color.Transparent))
            }
        }
    }
}

@Composable
private fun MiniPlayer(ps: PlayerUiState, conn: PlayerConnection, onOpen: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().background(Amoled.Surface).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(ps.artUrl, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Txt(ps.title, size = 15.sp, weight = FontWeight.SemiBold)
            Txt(ps.error ?: ps.artist, size = 12.sp, color = if (ps.error != null) Amoled.Err else Amoled.Muted)
        }
        IconBtn(if (ps.isPlaying) G.Pause else G.Play, accent, onClick = conn::toggle)
        IconBtn(G.Next, Amoled.OnBg, onClick = conn::next)
    }
}

/** Animated accent colour sampled from the album art (Palette), brightened to stay legible on black. */
@Composable
fun rememberAccent(artUrl: String?): Color {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(Amoled.Accent) }
    LaunchedEffect(artUrl) {
        target = if (artUrl == null) Amoled.Accent else extractAccent(ctx, artUrl) ?: Amoled.Accent
    }
    return animateColorAsState(target, tween(600), label = "accent").value
}

private suspend fun extractAccent(ctx: Context, url: String): Color? = try {
    extractAccentUnsafe(ctx, url)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null // a bad image must never take the app down
}

private suspend fun extractAccentUnsafe(ctx: Context, url: String): Color? = withContext(Dispatchers.IO) {
    val req = ImageRequest.Builder(ctx).data(url).size(128).allowHardware(false).build()
    val bmp = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap() ?: return@withContext null
    val p = Palette.from(bmp).maximumColorCount(12).generate()
    val rgb = p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(0)))
    if (rgb == 0) return@withContext null
    var c = Color(rgb)
    var i = 0
    while (c.luminance() < 0.3f && i++ < 6) c = lerp(c, Color.White, 0.18f)
    c
}
