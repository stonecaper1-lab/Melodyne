package org.melodyne.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.melodyne.app.data.repo.ApiUnavailable
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song

data class SearchUi(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
)

class SearchViewModel(private val repo: MusicRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    /** Debounced search-as-you-type. */
    fun onQuery(q: String) {
        _ui.update { it.copy(query = q) }
        job?.cancel()
        if (q.isBlank()) { _ui.update { SearchUi() }; return }
        job = viewModelScope.launch { delay(350); run(q) }
    }

    fun searchNow() {
        val q = _ui.value.query
        if (q.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch { run(q) }
    }

    private suspend fun run(q: String) {
        _ui.update { it.copy(loading = true, error = null) }
        runCatching { repo.search(q.trim()) }
            .onSuccess { r -> _ui.update { it.copy(loading = false, results = r, searched = true) } }
            .onFailure { e ->
                _ui.update { it.copy(loading = false, error = (e as? ApiUnavailable)?.userMessage ?: "Search failed.") }
            }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SearchViewModel(ServiceLocator.repo) } }
    }
}
