package org.melodyne.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.melodyne.app.data.lastfm.EnrichmentService
import org.melodyne.app.data.local.LibraryStore
import org.melodyne.app.data.local.RecentStore
import org.melodyne.app.data.repo.ApiUnavailable
import org.melodyne.app.data.repo.MoodService
import org.melodyne.app.data.repo.TrackResolver
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.PlaylistInfo
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText

data class HomeUi(
    val loading: Boolean = true,
    val trending: List<Song> = emptyList(),
    val trendingTitle: String = "Chart",
    val playlists: List<PlaylistInfo> = emptyList(),
    val recents: List<Song> = emptyList(),
    val onRepeat: List<Song> = emptyList(),
    val becauseArtist: String? = null,
    val because: List<Song> = emptyList(),
    val error: String? = null,
    val loadingMood: String? = null,
    val lastFmReady: Boolean = false,
)

class HomeViewModel(
    private val repo: MusicRepository,
    private val recents: RecentStore,
    private val library: LibraryStore,
    private val enrich: EnrichmentService,
    private val resolver: TrackResolver,
    private val moods: MoodService,
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi(recents = recents.all(), lastFmReady = enrich.available))
    val ui = _ui.asStateFlow()

    init { load() }

    /** Called every time Home is shown: history-based rows update as you listen. */
    fun refreshPersonal() {
        _ui.update { it.copy(recents = recents.all(), lastFmReady = enrich.available) }
        val stats = library.data.value.stats
        val now = System.currentTimeMillis()
        val repeat = stats.filter { it.plays >= 2 && now - it.last < 30L * 86_400_000L }
            .sortedByDescending { it.plays }.take(12).map { it.song }
        _ui.update { it.copy(onRepeat = if (repeat.size >= 3) repeat else emptyList()) }

        val seed = stats.maxByOrNull { it.last }?.song ?: return
        val artist = TitleText.primaryArtist(seed.artist)
        if (artist.isBlank() || !enrich.available || _ui.value.becauseArtist == artist) return
        viewModelScope.launch {
            val similar = runCatching { enrich.similarArtists(artist, 6) }.getOrDefault(emptyList())
            if (similar.isEmpty()) return@launch
            val own = TitleText.key(artist)
            val songs = similar.map { name ->
                async {
                    val key = TitleText.key(name)
                    runCatching { repo.search(name).filter { key in TitleText.artistKeys(it.artist) }.take(2) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().filter { own !in TitleText.artistKeys(it.artist) }.distinctBy { it.id }.take(14)
            if (songs.size >= 4) _ui.update { it.copy(becauseArtist = artist, because = songs) }
        }
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val chart = async { runCatching { trending() } }
            val lists = async { runCatching { repo.searchPlaylists("top") } }
            val c = chart.await()
            val p = lists.await()
            _ui.update {
                it.copy(
                    loading = false,
                    trending = c.getOrNull()?.second ?: it.trending,
                    trendingTitle = c.getOrNull()?.first ?: it.trendingTitle,
                    playlists = p.getOrDefault(it.playlists),
                    error = c.exceptionOrNull()?.let { e -> (e as? ApiUnavailable)?.userMessage ?: "Something went wrong." },
                )
            }
            refreshPersonal()
        }
    }

    /** With a Last.fm key the chart is what India is actually listening to, matched to the catalogue; otherwise a plain search. */
    private suspend fun trending(): Pair<String, List<Song>> {
        if (enrich.available) {
            val songs = resolver.resolve(enrich.geoTopTracks("india", 25))
            if (songs.size >= 6) return "Trending in India" to songs
        }
        return "Chart" to repo.search("top hits")
    }

    fun playPlaylist(id: String, onReady: (List<Song>) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.playlist(id) }
                .onSuccess { onReady(it.songs) }
                .onFailure { e ->
                    _ui.update { it.copy(error = (e as? ApiUnavailable)?.userMessage ?: "Couldn't open playlist.") }
                }
        }
    }

    fun playMood(label: String, onReady: (List<Song>) -> Unit) {
        if (_ui.value.loadingMood != null) return
        _ui.update { it.copy(loadingMood = label, error = null) }
        viewModelScope.launch {
            runCatching { moods.songs(label) }
                .onSuccess { r -> _ui.update { it.copy(loadingMood = null) }; if (r.isNotEmpty()) onReady(r) }
                .onFailure { e ->
                    _ui.update { it.copy(loadingMood = null, error = (e as? ApiUnavailable)?.userMessage ?: "Couldn't load that mood.") }
                }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    ServiceLocator.repo, ServiceLocator.recents, ServiceLocator.library,
                    ServiceLocator.enrichment, ServiceLocator.resolver, ServiceLocator.moods,
                )
            }
        }
    }
}
