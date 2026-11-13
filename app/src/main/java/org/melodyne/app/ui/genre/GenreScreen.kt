package org.melodyne.app.ui.genre

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.AmbientGlow
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.G
import org.melodyne.app.ui.IconBtn
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.SkeletonRow
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt

/** The page behind a word in the genre cloud (or a tag on Now Playing): what it is, and songs that really carry it. */
@Composable
fun GenreScreen(tag: String, conn: PlayerConnection, onClose: () -> Unit) {
    val vm: GenreViewModel = viewModel(key = "genre:$tag", factory = GenreViewModel.factory(tag))
    val s by vm.ui.collectAsState()
    val pb by ServiceLocator.playback.state.collectAsState()
    val accent = LocalAccent.current
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Amoled.Black)) {
        if (pb.glowOn) AmbientGlow(accent)
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(G.Down, Amoled.OnBg, Modifier.graphicsLayer { rotationZ = 90f }, onClick = onClose)
                    Txt("GENRE", size = 12.sp, weight = FontWeight.SemiBold, color = Amoled.Muted)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt(tag, size = 40.sp, weight = FontWeight.Black, color = accent)
                    s.detail?.summary?.let { Txt(it, size = 13.sp, color = Amoled.Muted, maxLines = 6) }
                    if (s.detail != null && !s.detail!!.fromLastFm) {
                        Txt("Add a free Last.fm key in Settings for songs ranked by how strongly listeners tag them.", size = 11.sp, color = Amoled.Muted, maxLines = 3)
                    }
                }
            }
            val songs = s.detail?.songs.orEmpty()
            if (songs.isNotEmpty()) item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Play") { conn.play(songs, 0) }
                    Pill("Shuffle", filled = false) { conn.play(songs.shuffled(), 0) }
                }
            }
            if (s.loading) items(6) { SkeletonRow() }
            s.error?.let { e -> item { Txt(e, Modifier.padding(20.dp), size = 13.sp, color = Amoled.Err, maxLines = 5) } }
            if (!s.loading && songs.isEmpty() && s.error == null) item { Txt("No playable songs found for this genre.", Modifier.padding(20.dp), size = 13.sp, color = Amoled.Muted) }
            itemsIndexed(songs) { i, song -> SongRow(song) { conn.play(songs, i) } }
        }
    }
}
