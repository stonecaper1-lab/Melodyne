package org.melodyne.app.data.local

import kotlinx.serialization.Serializable
import org.melodyne.app.domain.Song

/** Shape of AI mixes saved by v0.2/0.3; read once and migrated into the library. */
@Serializable
data class SavedPlaylist(val id: String, val name: String, val prompt: String, val songs: List<Song>)
