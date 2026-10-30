package org.melodyne.app.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.melodyne.app.data.lastfm.Enriched
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.data.lyrics.LrcLibRepository
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.Lyrics
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song

class NowPlayingViewModel(
    private val repo: MusicRepository,
    private val lrclib: LrcLibRepository,
    private val enrich: EnrichmentService,
) : ViewModel() {
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics = _lyrics.asStateFlow()
    private val _info = MutableStateFlow<Enriched?>(null)
    val info = _info.asStateFlow()
    private var loadedFor: String? = null

    /** Time-synced lyrics from LRCLIB first, then whatever the catalogue has. Last.fm adds tags and listener counts. */
    fun load(song: Song?) {
        if (song?.id == loadedFor) return
        loadedFor = song?.id
        _lyrics.value = null
        _info.value = null
        if (song == null) return
        viewModelScope.launch {
            _lyrics.value = runCatching { lrclib.lyrics(song) }.getOrNull()
                ?: if (song.hasLyrics) runCatching { repo.lyrics(song.id) }.getOrNull() else null
        }
        if (enrich.available) viewModelScope.launch { _info.value = runCatching { enrich.enrich(song) }.getOrNull() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { NowPlayingViewModel(ServiceLocator.repo, ServiceLocator.lrclib, ServiceLocator.enrichment) }
        }
    }
}
