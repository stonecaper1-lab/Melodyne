package org.melodyne.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.melodyne.app.ai.AiClient
import org.melodyne.app.ai.AiConfig
import org.melodyne.app.ai.AiConfigStore
import org.melodyne.app.data.lastfm.LastFmClient
import org.melodyne.app.data.repo.HostStore
import org.melodyne.app.data.update.UpdateChecker
import org.melodyne.app.di.ServiceLocator
import org.melodyne.app.util.CrashLog

data class SettingsUi(
    val lfConfigured: Boolean,
    val lfUser: String?,
    val busy: Boolean = false,
    val message: String? = null,
    val host: String = "",
    val ai: AiConfig = AiConfig(),
    val crash: String? = null,
    val lfKey: String = "",
    val lfSecret: String = "",
    val lfBuiltIn: Boolean = false,
    val lfStatus: String = "",
    val aiTest: String? = null,
    val aiTesting: Boolean = false,
    val updateStatus: String = "",
    val updateRepo: String = "",
    val updateToken: String = "",
)

class SettingsViewModel(
    private val lastFm: LastFmClient,
    private val hosts: HostStore,
    private val aiStore: AiConfigStore,
    private val aiClient: AiClient,
    private val updates: UpdateChecker,
    private val prefs: SharedPreferences,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        SettingsUi(
            lastFm.configured, lastFm.loggedInUser, host = hosts.custom().orEmpty(), ai = aiStore.load(), crash = CrashLog.last(prefs),
            lfKey = lastFm.savedKey, lfSecret = lastFm.savedSecret, lfBuiltIn = lastFm.hasBuiltInKey, lfStatus = lastFm.status.value,
            updateStatus = updates.status.value, updateRepo = updates.repo, updateToken = updates.token,
        )
    )
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch { lastFm.status.collect { st -> _ui.update { it.copy(lfStatus = st) } } }
        viewModelScope.launch { updates.status.collect { st -> _ui.update { it.copy(updateStatus = st) } } }
    }

    fun saveLastFmKeys(key: String, secret: String) {
        lastFm.saveKeys(key, secret)
        _ui.update {
            it.copy(lfConfigured = lastFm.configured, lfKey = lastFm.savedKey, lfSecret = lastFm.savedSecret,
                message = if (lastFm.configured) "Last.fm key saved. Now sign in." else "Enter both the API key and the secret.")
        }
    }

    fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) { _ui.update { it.copy(message = "Enter your Last.fm username and password.") }; return }
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val r = lastFm.login(user.trim(), pass)
            _ui.update {
                it.copy(
                    busy = false,
                    lfUser = r.getOrNull() ?: it.lfUser,
                    message = r.exceptionOrNull()?.message?.let { m -> "Last.fm: $m" },
                )
            }
        }
    }

    fun logout() { lastFm.logout(); _ui.update { it.copy(lfUser = null, message = null) } }

    fun saveHost(url: String) {
        val ok = hosts.setCustom(url)
        _ui.update { it.copy(host = if (ok) url.trim() else it.host, message = if (ok) "Custom source saved." else "The host must start with https://") }
    }

    fun saveAi(url: String, key: String, model: String) {
        if (url.isNotBlank() && !url.trim().startsWith("https://")) {
            _ui.update { it.copy(message = "The AI base URL must start with https://") }
            return
        }
        val c = AiConfig(url.trim().trimEnd('/'), key.trim(), model.trim())
        aiStore.save(c)
        _ui.update { it.copy(ai = c, message = if (c.ready) "AI provider saved." else "Saved. Fill in all three fields to enable AI Mix.") }
    }

    /** Sends a one-token request with what is currently typed, without saving it. */
    fun testAi(url: String, key: String, model: String) {
        val c = AiConfig(url.trim().trimEnd('/'), key.trim(), model.trim())
        if (!c.ready) { _ui.update { it.copy(aiTest = "Fill in an https base URL, a key and a model first.") }; return }
        _ui.update { it.copy(aiTesting = true, aiTest = null) }
        viewModelScope.launch {
            val r = runCatching { aiClient.test(c) }
            _ui.update { it.copy(aiTesting = false, aiTest = r.fold({ ms -> "Connected · $ms ms" }, { e -> "Failed: ${e.message}" })) }
        }
    }

    fun checkUpdates() { viewModelScope.launch { updates.check(force = true) } }

    fun saveUpdateSource(repo: String, token: String) {
        updates.saveSource(repo, token)
        _ui.update { it.copy(updateRepo = updates.repo, updateToken = updates.token, message = "Update source saved.") }
    }

    fun clearCrash() { CrashLog.clear(prefs); _ui.update { it.copy(crash = null) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    ServiceLocator.lastFm, ServiceLocator.hosts, ServiceLocator.aiConfig, ServiceLocator.ai,
                    ServiceLocator.updates, ServiceLocator.prefs,
                )
            }
        }
    }
}
