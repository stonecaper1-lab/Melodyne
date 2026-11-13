package org.melodyne.app.ui.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.melodyne.app.data.repo.ArtistDetail
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.AmbientGlow
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Cover
import org.melodyne.app.ui.G
import org.melodyne.app.ui.IconBtn
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalOpenAlbum
import org.melodyne.app.ui.LocalOpenArtist
import org.melodyne.app.ui.LocalOpenGenre
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.SkeletonRow
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt
import org.melodyne.app.ui.glass

data class ArtistUi(val loading: Boolean = true, val detail: ArtistDetail? = null)

class ArtistViewModel(private val name: String) : ViewModel() {
    private val _ui = MutableStateFlow(ArtistUi())
    val ui = _ui.asStateFlow()
    init { viewModelScope.launch { val d = runCatching { ServiceLocator.artists.artist(name) }.getOrNull(); _ui.update { ArtistUi(false, d) } } }
    companion object { fun factory(name: String) = viewModelFactory { initializer { ArtistViewModel(name) } } }
}

/** An artist's page, in four sections: Songs, Albums, Similar and About. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistScreen(name: String, conn: PlayerConnection, onClose: () -> Unit) {
    val vm: ArtistViewModel = viewModel(key = "artist:$name", factory = ArtistViewModel.factory(name))
    val s by vm.ui.collectAsState()
    val pb by ServiceLocator.playback.state.collectAsState()
    val accent = LocalAccent.current
    val openAlbum = LocalOpenAlbum.current
    val openArtist = LocalOpenArtist.current
    val openGenre = LocalOpenGenre.current
    var tab by remember(name) { mutableStateOf(0) }
    BackHandler(onBack = onClose)
    val d = s.detail

    Box(Modifier.fillMaxSize().background(Amoled.Black)) {
        if (pb.glowOn) AmbientGlow(accent)
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(G.Down, Amoled.OnBg, Modifier.graphicsLayer { rotationZ = 90f }, onClick = onClose)
                    Txt("ARTIST", size = 12.sp, weight = FontWeight.SemiBold, color = Amoled.Muted)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt(name, size = 38.sp, weight = FontWeight.Black, color = accent, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Songs", "Albums", "Similar", "About").forEachIndexed { i, l -> Chip(l, tab == i) { tab = i } }
                    }
                }
            }
            if (s.loading) items(6) { SkeletonRow() }
            if (d != null) when (tab) {
                0 -> {
                    if (d.songs.isNotEmpty()) item {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill("Play") { conn.play(d.songs, 0) }
                            Pill("Shuffle", filled = false) { conn.play(d.songs.shuffled(), 0) }
                        }
                    }
                    itemsIndexed(d.songs) { i, t -> SongRow(t) { conn.play(d.songs, i) } }
                    if (d.songs.isEmpty()) item { Txt("No songs found.", Modifier.padding(20.dp), size = 13.sp, color = Amoled.Muted) }
                }
                1 -> {
                    items(d.albums) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openAlbum(a) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Cover(a.thumbUrl, Modifier.size(64.dp), radius = 14.dp)
                            Column(Modifier.weight(1f)) {
                                Txt(a.album, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 2)
                                Txt("Open album", size = 12.sp, color = Amoled.Muted)
                            }
                        }
                    }
                    if (d.albums.isEmpty()) item { Txt("No albums listed.", Modifier.padding(20.dp), size = 13.sp, color = Amoled.Muted) }
                }
                2 -> {
                    items(d.similar) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openArtist(a.name) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Cover(a.thumb, Modifier.size(56.dp), radius = 28.dp)
                            Txt(a.name, Modifier.weight(1f), size = 16.sp, weight = FontWeight.SemiBold)
                            Txt("›", size = 22.sp, color = Amoled.Muted)
                        }
                    }
                    if (d.similar.isEmpty()) item {
                        Txt("Similar artists come from Last.fm. Add a free key in Settings.", Modifier.padding(20.dp), size = 13.sp, color = Amoled.Muted, maxLines = 3)
                    }
                }
                else -> item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Txt(d.bio ?: "No biography available. Last.fm provides these once you add a free key in Settings.", size = 14.sp, color = Amoled.Muted, maxLines = 40)
                        if (d.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            d.tags.forEach { tag ->
                                Txt("#$tag", Modifier.glass(RoundedCornerShape(50), alpha = 0.07f).clickable { openGenre(tag) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    size = 12.sp, weight = FontWeight.SemiBold, color = accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
