package org.melodyne.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Song
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.Field
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.Pill
import org.melodyne.app.ui.Txt

@Composable
private fun DialogCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Amoled.Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Txt(title, size = 18.sp, weight = FontWeight.Bold)
            Field(text, { text = it }, "Playlist name")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(confirm) { if (text.isNotBlank()) onConfirm(text.trim()) }
                Pill("Cancel", filled = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Txt(title, size = 18.sp, weight = FontWeight.Bold)
            Txt(body, size = 14.sp, color = Amoled.Muted, maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(confirm, onClick = onConfirm)
                Pill("Cancel", filled = false, onClick = onDismiss)
            }
        }
    }
}

/** Opened by the "+" next to any song. Pick a playlist, or make a new one with the song already in it. */
@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val lib = ServiceLocator.library
    val data by lib.data.collectAsState()
    val ctx = LocalContext.current
    var naming by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    if (naming) {
        NameDialog("New playlist", "", "Create and add", onConfirm = { name ->
            lib.create(name, listOf(song))
            Toast.makeText(ctx, "Added to $name", Toast.LENGTH_SHORT).show()
            onDismiss()
        }, onDismiss = { naming = false })
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Txt("Add to playlist", size = 18.sp, weight = FontWeight.Bold)
            Txt(song.title, size = 13.sp, color = Amoled.Muted)
            Txt(
                "+  New playlist", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Amoled.SurfaceHi)
                    .clickable { naming = true }.padding(14.dp),
                size = 15.sp, weight = FontWeight.SemiBold, color = accent,
            )
            LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.playlists) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Amoled.SurfaceHi).clickable {
                            val added = lib.addSongs(p.id, listOf(song))
                            Toast.makeText(ctx, if (added > 0) "Added to ${p.name}" else "Already in ${p.name}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(p.name, Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
                        Txt("${p.songs.size}", size = 12.sp, color = Amoled.Muted)
                    }
                }
            }
            Pill("Close", filled = false, onClick = onDismiss)
        }
    }
}
