package org.melodyne.app.ui.album

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.melodyne.app.data.repo.PersonDetail
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Song
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.AmbientGlow
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Cover
import org.melodyne.app.ui.G
import org.melodyne.app.ui.IconBtn
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalOpenGenre
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.SkeletonRow
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.glass

data class AlbumUi(val loading: Boolean = true, val songs: List<Song> = emptyList())
data class ComposerUi(val loading: Boolean = true, val detail: PersonDetail? = null)

class AlbumViewModel(private val song: Song) : ViewModel() {
    private val _ui = MutableStateFlow(AlbumUi())
    val ui = _ui.asStateFlow()
    init { viewModelScope.launch { val t = runCatching { ServiceLocator.albums.tracks(song) }.getOrDefault(listOf(song)); _ui.update { AlbumUi(false, t) } } }

    companion object { fun factory(song: Song) = viewModelFactory { initializer { AlbumViewModel(song) } } }
}

class ComposerViewModel(private val name: String) : ViewModel() {
    private val _ui = MutableStateFlow(ComposerUi())
    val ui = _ui.asStateFlow()
    init { viewModelScope.launch { val d = runCatching { ServiceLocator.people.composer(name) }.getOrNull(); _ui.update { ComposerUi(false, d) } } }

    companion object { fun factory(name: String) = viewModelFactory { initializer { ComposerViewModel(name) } } }
}

@Composable
private fun PageHeader(label: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(G.Down, Amoled.OnBg, Modifier.graphicsLayer { rotationZ = 90f }, onClick = onClose)
        Txt(label, size = 12.sp, weight = FontWeight.SemiBold, color = Amoled.Muted)
    }
}

/** The album a song belongs to: cover, title, and every track. */
@Composable
fun AlbumScreen(song: Song, conn: PlayerConnection, onClose: () -> Unit) {
    val vm: AlbumViewModel = viewModel(key = "album:${song.albumId}:${song.album}", factory = AlbumViewModel.factory(song))
    val s by vm.ui.collectAsState()
    val pb by ServiceLocator.playback.state.collectAsState()
    val accent = LocalAccent.current
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Amoled.Black)) {
        if (pb.glow) AmbientGlow(accent)
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { PageHeader("ALBUM", onClose) }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Cover(song.artUrl, Modifier.size(132.dp), radius = 22.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Txt(song.album, size = 24.sp, weight = FontWeight.ExtraBold, maxLines = 3, color = accent)
                        Txt(song.artist, size = 14.sp, color = Amoled.Muted, maxLines = 2)
                        if (song.composer.isNotBlank()) Txt("Music: ${song.composer}", size = 12.sp, color = Amoled.Muted, maxLines = 2)
                        if (!s.loading) Txt("${s.songs.size} tracks", size = 12.sp, color = Amoled.Muted)
                    }
                }
            }
            if (s.songs.isNotEmpty()) item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Play") { conn.play(s.songs, 0) }
                    Pill("Shuffle", filled = false) { conn.play(s.songs.shuffled(), 0) }
                }
            }
            if (s.loading) items(5) { SkeletonRow() }
            itemsIndexed(s.songs) { i, t -> SongRow(t) { conn.play(s.songs, i) } }
        }
    }
}

/** A composer's page: what Last.fm says about them, their genres (tap to open) and songs they're credited on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(name: String, conn: PlayerConnection, onClose: () -> Unit) {
    val vm: ComposerViewModel = viewModel(key = "composer:$name", factory = ComposerViewModel.factory(name))
    val s by vm.ui.collectAsState()
    val pb by ServiceLocator.playback.state.collectAsState()
    val openGenre = LocalOpenGenre.current
    val accent = LocalAccent.current
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Amoled.Black)) {
        if (pb.glow) AmbientGlow(accent)
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { PageHeader("COMPOSER", onClose) }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt(name, size = 36.sp, weight = FontWeight.Black, color = accent, maxLines = 2)
                    s.detail?.bio?.let { Txt(it, size = 13.sp, color = Amoled.Muted, maxLines = 7) }
                    s.detail?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag ->
                                Txt("#$tag", Modifier.glass(RoundedCornerShape(50), alpha = 0.07f).clickable { openGenre(tag) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    size = 12.sp, weight = FontWeight.SemiBold, color = accent)
                            }
                        }
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
            if (s.loading) items(5) { SkeletonRow() }
            if (!s.loading && songs.isEmpty()) item { Txt("No songs found for this composer.", Modifier.padding(20.dp), size = 13.sp, color = Amoled.Muted) }
            itemsIndexed(songs) { i, t -> SongRow(t) { conn.play(songs, i) } }
        }
    }
}
