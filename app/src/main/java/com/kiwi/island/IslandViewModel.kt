package com.kiwi.island

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IslandViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val pinned = MutableStateFlow<String?>(null)

    val expanded = MutableStateFlow(false)
    val cutout = MutableStateFlow<CutoutInfo?>(null)

    private val loaded = MutableStateFlow(false)

    val settings: StateFlow<IslandSettings> =
        store.flow.onEach { loaded.value = true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, IslandSettings())

    /** Suspends until the first real value has come from DataStore, so the overlay never flashes defaults. */
    suspend fun awaitSettings() {
        loaded.first { it }
    }

    val activities: StateFlow<List<IslandActivity>> =
        combine(ActivityRepository.activities, settings, pinned) { all, s, pin ->
            val list = all.filter { it.category.name !in s.disabled }
            val callActive = list.any { it.category == Category.Call }
            val first = if (callActive) null else list.firstOrNull { it.id == pin }
            if (first != null) listOf(first) + (list - first) else list
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Forget the pin once the pinned activity is gone, so it doesn't jump the queue when it returns.
        viewModelScope.launch {
            ActivityRepository.activities.collect { all ->
                val pin = pinned.value
                if (pin != null && all.none { it.id == pin }) pinned.value = null
            }
        }
    }

    fun setExpanded(value: Boolean) {
        expanded.value = value
    }

    fun pin(id: String) {
        pinned.value = id
    }
}
