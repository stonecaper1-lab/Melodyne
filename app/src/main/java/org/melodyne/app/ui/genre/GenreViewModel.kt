package org.melodyne.app.ui.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.melodyne.app.data.repo.ApiUnavailable
import org.melodyne.app.data.repo.GenreDetail
import org.melodyne.app.data.repo.GenreService
import org.melodyne.app.di.ServiceLocator

data class GenreUi(val loading: Boolean = true, val detail: GenreDetail? = null, val error: String? = null)

class GenreViewModel(private val tag: String, private val genres: GenreService) : ViewModel() {
    private val _ui = MutableStateFlow(GenreUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { genres.detail(tag) }
                .onSuccess { d -> _ui.update { GenreUi(false, d) } }
                .onFailure { e -> _ui.update { GenreUi(false, null, (e as? ApiUnavailable)?.userMessage ?: "Couldn't load this genre.") } }
        }
    }

    companion object {
        fun factory(tag: String) = viewModelFactory { initializer { GenreViewModel(tag, ServiceLocator.genres) } }
    }
}
