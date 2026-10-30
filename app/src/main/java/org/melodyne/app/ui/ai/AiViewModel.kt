package org.melodyne.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.melodyne.app.ai.AiClient
import org.melodyne.app.ai.AiConfigStore
import org.melodyne.app.ai.Suggestion
import org.melodyne.app.data.local.LibraryStore
import org.melodyne.app.data.local.RecentStore
import org.melodyne.app.data.repo.ApiUnavailable
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.domain.MusicRepository
import org.melodyne.app.domain.Song

data class AiUi(
    val prompt: String = "",
    val count: Int = 20,
    val useRecents: Boolean = false,
    val configured: Boolean = false,
    val busy: Boolean = false,
    val status: String? = null,
    val result: List<Song> = emptyList(),
    val resultName: String = "",
    val resultPrompt: String = "",
    val error: String? = null,
)

class AiViewModel(
    private val repo: MusicRepository,
    private val ai: AiClient,
    private val cfg: AiConfigStore,
    private val library: LibraryStore,
    private val recents: RecentStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(AiUi(configured = cfg.load().ready))
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    fun refresh() = _ui.update { it.copy(configured = cfg.load().ready) }
    fun onPrompt(p: String) = _ui.update { it.copy(prompt = p) }
    fun setCount(n: Int) = _ui.update { it.copy(count = n) }
    fun toggleRecents() = _ui.update { it.copy(useRecents = !it.useRecents) }

    /** AI path: the model suggests songs, then each one is matched to a playable track. */
    fun generate() {
        val s = _ui.value
        val config = cfg.load()
        if (s.prompt.isBlank()) { _ui.update { it.copy(error = "Describe a mood, moment or taste first.") }; return }
        if (!config.ready) { _ui.update { it.copy(error = "Add your AI provider in Settings first, or try Quick mix.") }; return }
        launchMix(s.prompt) {
            val hints = if (s.useRecents) recents.all().take(10).map { "${it.title} — ${it.artist}" } else emptyList()
            _ui.update { it.copy(status = "Asking your AI for ideas…") }
            val suggestions = ai.suggest(config, s.prompt, s.count, hints)
            _ui.update { it.copy(status = "Finding ${suggestions.size} songs…") }
            resolve(suggestions)
        }
    }

    /** No AI involved: plain catalogue search for the description. Works without a key. */
    fun quickMix() {
        val s = _ui.value
        if (s.prompt.isBlank()) { _ui.update { it.copy(error = "Type what you're in the mood for first.") }; return }
        launchMix(s.prompt) {
            _ui.update { it.copy(status = "Searching…") }
            repo.search(s.prompt.trim()).distinctBy { it.id }.take(s.count)
        }
    }

    fun save() {
        val s = _ui.value
        if (s.result.isEmpty()) return
        library.create(s.resultName, s.result, "ai")
        _ui.update { it.copy(status = "Saved to your Library") }
    }

    private fun launchMix(prompt: String, block: suspend () -> List<Song>) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, result = emptyList(), status = null) }
            try {
                val songs = block()
                if (songs.isEmpty()) {
                    _ui.update { it.copy(busy = false, status = null, error = "No playable songs found. Try describing it differently.") }
                } else {
                    _ui.update {
                        it.copy(busy = false, status = null, result = songs, resultName = titleOf(prompt), resultPrompt = prompt.trim())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, status = null, error = (e as? ApiUnavailable)?.userMessage ?: e.message ?: "Something went wrong.") }
            }
        }
    }

    private suspend fun resolve(suggestions: List<Suggestion>): List<Song> {
        val gate = Semaphore(4)
        return coroutineScope {
            suggestions.map { s ->
                async { gate.withPermit { runCatching { pickBest(repo.search("${s.title} ${s.artist}"), s) }.getOrNull() } }
            }.awaitAll().filterNotNull().distinctBy { it.id }
        }
    }

    private fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
    private fun pickBest(r: List<Song>, s: Suggestion): Song? {
        val t = norm(s.title)
        return r.firstOrNull { norm(it.title).contains(t) } ?: r.firstOrNull()
    }
    private fun titleOf(prompt: String) = prompt.trim().replaceFirstChar { it.uppercase() }.take(34)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AiViewModel(ServiceLocator.repo, ServiceLocator.ai, ServiceLocator.aiConfig, ServiceLocator.library, ServiceLocator.recents)
            }
        }
    }
}
