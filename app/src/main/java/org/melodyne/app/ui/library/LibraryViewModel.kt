package org.melodyne.app.ui.library

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.melodyne.app.data.local.LibraryStore
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.SmartList
import org.melodyne.app.domain.SmartLists
import org.melodyne.app.domain.UserPlaylist

data class OpenRef(val id: String, val smart: Boolean)

data class LibraryUi(
    val playlists: List<UserPlaylist> = emptyList(),
    val smart: List<SmartList> = emptyList(),
    val opened: OpenRef? = null,
    val message: String? = null,
)

class LibraryViewModel(private val lib: LibraryStore) : ViewModel() {
    private val _ui = MutableStateFlow(LibraryUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            lib.data.collect { d -> _ui.update { it.copy(playlists = d.playlists, smart = SmartLists.build(d.stats)) } }
        }
    }

    fun openList(id: String, smart: Boolean) = _ui.update { it.copy(opened = OpenRef(id, smart), message = null) }
    fun close() = _ui.update { it.copy(opened = null) }
    fun create(name: String) { lib.create(name) }
    fun rename(id: String, name: String) = lib.rename(id, name)
    fun delete(id: String) { lib.delete(id); close() }
    fun move(id: String, from: Int, to: Int) = lib.move(id, from, to)
    fun remove(id: String, index: Int) = lib.removeAt(id, index)

    /** Freezes a smart playlist's current songs into a normal, editable playlist. */
    fun saveSmart(s: SmartList) {
        val id = lib.create(s.name, s.songs, "smart")
        _ui.update { it.copy(opened = OpenRef(id, false), message = "Saved “${s.name}” as a playlist.") }
    }

    fun export(cr: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val out = cr.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
                    out.use { it.write(lib.exportJson().toByteArray(Charsets.UTF_8)) }
                }.fold({ "Library exported." }, { "Export failed: ${it.message}" })
            }
            _ui.update { it.copy(message = msg) }
        }
    }

    fun importFrom(cr: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val text = cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Couldn't read the file")
                    lib.importJson(text)
                }.fold({ n -> if (n == 0) "Nothing new to import." else "Imported $n playlist${if (n == 1) "" else "s"}." }, { "Import failed: ${it.message}" })
            }
            _ui.update { it.copy(message = msg) }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { LibraryViewModel(ServiceLocator.library) } }
    }
}
