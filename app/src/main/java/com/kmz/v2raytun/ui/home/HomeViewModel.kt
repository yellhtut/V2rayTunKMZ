package com.kmz.v2raytun.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.repo.ProfileRepository
import com.kmz.v2raytun.util.ClipboardImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-shot message for the snackbar. [id] lets the UI distinguish repeats of the same text. */
data class UiMessage(val id: Long, val text: String)

class HomeViewModel(
    application: Application,
    private val repository: ProfileRepository,
) : AndroidViewModel(application) {

    val profiles: StateFlow<List<Profile>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The server the connect action will act on. Null until the user taps one. */
    private val _selectedProfileId = MutableStateFlow<Long?>(null)
    val selectedProfileId: StateFlow<Long?> = _selectedProfileId.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var messageCounter = 0L

    /**
     * Imports whatever the caller read from the clipboard.
     *
     * The read itself happens in the UI layer: from Android 10 the clipboard is only
     * readable by the focused app, so it has to be driven by a user action there rather
     * than from a ViewModel.
     */
    fun importFromClipboard(text: String?) {
        viewModelScope.launch {
            val result = ClipboardImport.parse(text)

            if (result.isEmpty) {
                emit("Clipboard is empty")
                return@launch
            }
            if (result.profiles.isEmpty()) {
                val firstReason = result.failures.firstOrNull()?.reason
                emit(
                    if (result.skippedCount == 1 && firstReason != null) {
                        "Could not import: $firstReason"
                    } else {
                        "No valid share links found (${result.skippedCount} skipped)"
                    },
                )
                return@launch
            }

            repository.addAll(result.profiles)
            emit(
                buildString {
                    append("Imported ${result.importedCount}")
                    if (result.skippedCount > 0) append(", skipped ${result.skippedCount}")
                },
            )
        }
    }

    fun select(profile: Profile) {
        _selectedProfileId.value = profile.id
    }

    /**
     * Starts the tunnel for the selected server.
     *
     * TODO(Phase 5): stand up the VpnService and hand it the selected profile. The core
     * that does the tunnelling isn't bundled yet, so for now we acknowledge the tap
     * honestly rather than flipping to a "connected" state that would be a lie.
     */
    fun connect() {
        val id = _selectedProfileId.value
        val profile = profiles.value.firstOrNull { it.id == id }
        if (profile == null) {
            emit("Select a server first")
            return
        }
        emit("Can't connect yet — the tunnel engine ships in Phase 5")
    }

    fun delete(profile: Profile) {
        viewModelScope.launch {
            repository.delete(profile)
            if (_selectedProfileId.value == profile.id) _selectedProfileId.value = null
            emit("Deleted ${profile.name}")
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun emit(text: String) {
        _message.value = UiMessage(messageCounter++, text)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(application, ProfileRepository.from(application)) as T
            }
    }
}
