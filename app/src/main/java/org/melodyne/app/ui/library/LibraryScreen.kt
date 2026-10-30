package org.melodyne.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.melodyne.app.domain.Song
import org.melodyne.app.player.PlayerConnection
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Chip
import org.melodyne.app.ui.Cover
import org.melodyne.app.ui.G
import org.melodyne.app.ui.Glyph
import org.melodyne.app.ui.IconBtn
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.SectionTitle
import org.melodyne.app.ui.SongRow
import org.melodyne.app.ui.Txt

private sealed interface LibDialog {
    object New : LibDialog
    data class Rename(val id: String, val name: String) : LibDialog
    data class Delete(val id: String, val name: String) : LibDialog
}

@Composable
fun LibraryScreen(conn: PlayerConnection) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val s by vm.ui.collectAsState()
    val cr = LocalContext.current.contentResolver
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(cr, uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(cr, uri)
    }
    var dialog by remember { mutableStateOf<LibDialog?>(null) }
    BackHandler(enabled = s.opened != null) { vm.close() }

    val opened = s.opened
    val custom = opened?.takeIf { !it.smart }?.let { o -> s.playlists.firstOrNull { it.id == o.id } }
    val smart = opened?.takeIf { it.smart }?.let { o -> s.smart.firstOrNull { it.id == o.id } }

    when {
        custom != null -> Detail(
            title = custom.name, subtitle = "${custom.songs.size} songs", songs = custom.songs, editable = true,
            message = s.message, onBack = vm::close,
            onPlay = { i -> conn.play(custom.songs, i) }, onShuffle = { conn.play(custom.songs.shuffled(), 0) },
            onMove = { from, to -> vm.move(custom.id, from, to) }, onRemove = { i -> vm.remove(custom.id, i) },
            extra = {
                Chip("Rename", false) { dialog = LibDialog.Rename(custom.id, custom.name) }
                Chip("Delete", false) { dialog = LibDialog.Delete(custom.id, custom.name) }
            },
        )
        smart != null -> Detail(
            title = smart.name, subtitle = smart.blurb, songs = smart.songs, editable = false,
            message = s.message, onBack = vm::close,
            onPlay = { i -> conn.play(smart.songs, i) }, onShuffle = { conn.play(smart.songs.shuffled(), 0) },
            onMove = { _, _ -> }, onRemove = { },
            extra = { Chip("Save as playlist", true) { vm.saveSmart(smart) } },
        )
        else -> Home(
            s.playlists.map { Triple(it.id, it.name, "${it.songs.size} songs") },
            s.smart.map { Triple(it.id, it.name, "${it.songs.size} songs · ${it.blurb}") },
            s.message,
            onNew = { dialog = LibDialog.New },
            onImport = { importer.launch(arrayOf("*/*")) },
            onExport = { exporter.launch("melodyne-library.json") },
            onOpen = { id, isSmart -> vm.openList(id, isSmart) },
        )
    }

    when (val d = dialog) {
        LibDialog.New -> NameDialog("New playlist", "", "Create", { name -> vm.create(name); dialog = null }, { dialog = null })
        is LibDialog.Rename -> NameDialog("Rename playlist", d.name, "Save", { name -> vm.rename(d.id, name); dialog = null }, { dialog = null })
        is LibDialog.Delete -> ConfirmDialog(
            "Delete “${d.name}”?", "The playlist is removed from this phone. Your songs are not affected.", "Delete",
            { vm.delete(d.id); dialog = null }, { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun Home(
    playlists: List<Triple<String, String, String>>,
    smart: List<Triple<String, String, String>>,
    message: String?,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onOpen: (String, Boolean) -> Unit,
) {
    val accent = LocalAccent.current
    val pad = Modifier.padding(horizontal = 20.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 20.dp)) {
        item {
            Column(pad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt("Library", size = 28.sp, weight = FontWeight.ExtraBold)
                Txt("Your playlists live in this app's own storage. Clearing the cache doesn't touch them.", size = 12.sp, color = Amoled.Muted, maxLines = 3)
            }
        }
        item {
            Row(pad.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("New playlist", true, onClick = onNew)
                Chip("Import", false, onClick = onImport)
                Chip("Export", false, onClick = onExport)
            }
        }
        message?.let { m -> item { Txt(m, pad.padding(top = 12.dp), size = 13.sp, color = accent, maxLines = 3) } }

        item { SectionTitle("Smart playlists") }
        if (smart.isEmpty()) item { Txt("Play a few songs and Melodyne starts building playlists from your habits.", pad, size = 13.sp, color = Amoled.Muted, maxLines = 3) }
        items(smart) { (id, name, sub) -> ListRow(name, sub, outlined = true) { onOpen(id, true) } }

        item { SectionTitle("Your playlists") }
        if (playlists.isEmpty()) item { Txt("No playlists yet. Tap + next to any song, or use New playlist.", pad, size = 13.sp, color = Amoled.Muted, maxLines = 3) }
        items(playlists) { (id, name, sub) -> ListRow(name, sub, outlined = false) { onOpen(id, false) } }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, outlined: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .then(if (outlined) Modifier.border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier.background(Amoled.SurfaceHi)),
            contentAlignment = Alignment.Center,
        ) { Txt(title.take(1).uppercase(), size = 18.sp, weight = FontWeight.ExtraBold, color = accent) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            Txt(subtitle, size = 12.sp, color = Amoled.Muted)
        }
    }
}

@Composable
private fun Detail(
    title: String,
    subtitle: String,
    songs: List<Song>,
    editable: Boolean,
    message: String?,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    extra: @Composable () -> Unit,
) {
    val accent = LocalAccent.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(G.Down, Amoled.OnBg, Modifier.graphicsLayer { rotationZ = 90f }, onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Txt(title, size = 24.sp, weight = FontWeight.ExtraBold)
                    Txt(subtitle, size = 12.sp, color = Amoled.Muted, maxLines = 2)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Play") { onPlay(0) }
                Pill("Shuffle", filled = false, onClick = onShuffle)
            }
        }
        item { Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { extra() } }
        message?.let { m -> item { Txt(m, Modifier.padding(horizontal = 20.dp, vertical = 10.dp), size = 13.sp, color = accent, maxLines = 3) } }
        if (songs.isEmpty()) item {
            Txt("Empty for now. Tap + next to any song to add it here.", Modifier.padding(20.dp), size = 13.sp, color = Amoled.Muted, maxLines = 3)
        }
        itemsIndexed(songs) { i, song ->
            if (!editable) {
                SongRow(song) { onPlay(i) }
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(start = 20.dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(song.thumbUrl, Modifier.size(46.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(song.title, size = 15.sp, weight = FontWeight.Medium)
                        Txt(song.artist, size = 12.sp, color = Amoled.Muted)
                    }
                    ReorderButton(up = true, enabled = i > 0) { onMove(i, i - 1) }
                    ReorderButton(up = false, enabled = i < songs.lastIndex) { onMove(i, i + 1) }
                    Txt("×", Modifier.clickable { onRemove(i) }.padding(horizontal = 10.dp, vertical = 4.dp), size = 24.sp, color = Amoled.Err)
                }
            }
        }
    }
}

@Composable
private fun ReorderButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(G.Down, if (enabled) Amoled.OnBg else Color(0xFF333333), 20.dp, Modifier.graphicsLayer { rotationZ = if (up) 180f else 0f })
    }
}
