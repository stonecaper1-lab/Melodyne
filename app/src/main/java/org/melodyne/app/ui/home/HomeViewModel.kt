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
import org.melodyne.app.data.repo.ChartService
import org.melodyne.app.data.repo.MoodService
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song
import org.melodyne.app.domain.TitleText

data class HomeUi(
    val loading: Boolean = true,
    val chartLang: String = "All",
    val chartLoading: Boolean = false,
    /** The chart for [chartLang]: the first ten are the Top 10, the rest feed "More to explore". */
    val trending: List<Song> = emptyList(),
    /** song id -> "NEW" / "▲2" / "▼1" / "–" */
    val moves: Map<String, String> = emptyMap(),
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
    private val charts: ChartService,
    private val moods: MoodService,
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi(recents = recents.all(), lastFmReady = enrich.available))
    val ui = _ui.asStateFlow()
    private var chartAt = 0L

    init { load() }

    /** Called every time Home is shown: history rows update as you listen, and a chart older than 30 minutes is fetched again. */
    fun refreshPersonal() {
        _ui.update { it.copy(recents = recents.all(), lastFmReady = enrich.available) }
        if (chartAt != 0L && System.currentTimeMillis() - chartAt > 30 * 60_000L) viewModelScope.launch { loadChart() }
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
            loadChart()
            _ui.update { it.copy(loading = false) }
            refreshPersonal()
        }
    }

    fun selectLang(label: String) {
        if (label == _ui.value.chartLang) return
        _ui.update { it.copy(chartLang = label, trending = emptyList(), moves = emptyMap()) }
        viewModelScope.launch { loadChart() }
    }

    private suspend fun loadChart() {
        val lang = _ui.value.chartLang
        _ui.update { it.copy(chartLoading = true) }
        runCatching { charts.top(lang) }
            .onSuccess { c ->
                chartAt = System.currentTimeMillis()
                _ui.update { s ->
                    if (s.chartLang != lang) s
                    else s.copy(chartLoading = false, trending = c.map { it.song }, moves = c.associate { it.song.id to it.move }, error = null)
                }
            }
            .onFailure { e ->
                _ui.update { it.copy(chartLoading = false, error = (e as? ApiUnavailable)?.userMessage ?: "Something went wrong.") }
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
                    ServiceLocator.enrichment, ServiceLocator.charts, ServiceLocator.moods,
                )
            }
        }
    }
}
