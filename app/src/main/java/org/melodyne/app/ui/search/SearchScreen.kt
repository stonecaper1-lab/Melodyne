package org.melodyne.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.ErrorCard
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.LocalOpenEaster
import org.melodyne.app.ui.LocalOpenGenre
import org.melodyne.app.ui.genre.GenreCloud
import org.melodyne.app.di.ServiceLocator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt

@Composable
fun SearchScreen(conn: PlayerConnection) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val s by vm.ui.collectAsState()
    val accent = LocalAccent.current
    val openEaster = LocalOpenEaster.current
    val openGenre = LocalOpenGenre.current
    LaunchedEffect(s.query) { if (s.query.trim() == "1509") openEaster() }

    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = s.query,
            onValueChange = vm::onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Amoled.OnBg, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchNow() }),
            modifier = Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(50))
                .background(Amoled.SurfaceHi).padding(horizontal = 18.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (s.query.isEmpty()) Txt("Search songs, artists, albums…", color = Amoled.Muted, size = 16.sp)
                    inner()
                }
            },
        )
        s.error?.let { ErrorCard(it, onRetry = vm::searchNow) }
        if (s.loading) Txt("Searching…", Modifier.padding(horizontal = 20.dp), color = Amoled.Muted)
        if (s.searched && !s.loading && s.results.isEmpty() && s.error == null) {
            Txt("No results", Modifier.padding(horizontal = 20.dp), color = Amoled.Muted)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (s.query.isBlank()) item {
                Column(Modifier.padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt("Browse genres", Modifier.padding(horizontal = 20.dp), size = 18.sp, weight = FontWeight.Bold)
                    if (s.cloud.isEmpty()) Txt("Loading genres…", Modifier.padding(horizontal = 20.dp), size = 13.sp, color = Amoled.Muted)
                    else GenreCloud(s.cloud, openGenre)
                    if (!ServiceLocator.enrichment.available) {
                        Txt("Sizes follow Last.fm's live tag chart once you add a free key in Settings.", Modifier.padding(horizontal = 20.dp), size = 11.sp, color = Amoled.Muted, maxLines = 2)
                    }
                }
            }
            itemsIndexed(s.results) { i, song -> SongRow(song) { conn.play(s.results, i) } }
        }
    }
}
