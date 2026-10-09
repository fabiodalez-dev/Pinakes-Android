package com.pinakes.app.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.*
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.*
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DesiderataViewModel @Inject constructor(private val source: CollectionsSource, private val saved: SavedStateHandle) : ViewModel() {
    private val pager = CollectionPager(viewModelScope, source::wanted, WantedBook::id)
    val state = pager.state
    val query = saved.getStateFlow("query", "")
    private val healthState = MutableStateFlow<CollectionHealth?>(null)
    val health = healthState.asStateFlow()
    init { refresh() }
    fun changeQuery(value: String) {
        saved["query"] = value
        // Reset immediately so an old response cannot remain under a new query.
        pager.reload(value, debounce = true)
    }
    fun refresh() {
        pager.reload(query.value)
        viewModelScope.launch { (source.desiderataHealth() as? ApiResult.Success)?.let { healthState.value = it.data } }
    }
    fun more() = pager.more()
}

@HiltViewModel
class ArchivesViewModel @Inject constructor(private val source: CollectionsSource, private val saved: SavedStateHandle) : ViewModel() {
    private val pager = CollectionPager(viewModelScope, source::archives, ArchiveRecord::id)
    val state = pager.state
    val query = saved.getStateFlow("query", "")
    val level = saved.getStateFlow("level", "")
    val from = saved.getStateFlow("from", "")
    val to = saved.getStateFlow("to", "")
    val parentId = saved.get<Int>(Routes.ARG_ARCHIVE_PARENT)?.takeIf { it > 0 }
    private val healthState = MutableStateFlow<CollectionHealth?>(null)
    val health = healthState.asStateFlow()
    val invalidDates = MutableStateFlow(false)
    init { refresh() }
    fun changeQuery(value: String) { saved["query"] = value; reload(debounce = true) }
    fun changeLevel(value: String) { saved["level"] = if (level.value == value) "" else value; refresh() }
    fun changeFrom(value: String) { saved["from"] = value }
    fun changeTo(value: String) { saved["to"] = value }
    fun clear() { saved["query"] = ""; saved["level"] = ""; saved["from"] = ""; saved["to"] = ""; refresh() }
    fun refresh() {
        reload()
        viewModelScope.launch { (source.archivesHealth() as? ApiResult.Success)?.let { healthState.value = it.data } }
    }
    private fun reload(debounce: Boolean = false) {
        val start = from.value.toIntOrNull(); val end = to.value.toIntOrNull()
        val invalid = (from.value.isNotBlank() && (start == null || start !in -32768..32767)) ||
            (to.value.isNotBlank() && (end == null || end !in -32768..32767)) || (start != null && end != null && start > end)
        invalidDates.value = invalid
        if (invalid) return
        pager.reload(ArchiveFilters(query.value, level.value.takeIf { it.isNotBlank() }, start, end, parentId), debounce)
    }
    fun more() = pager.more()
}

@HiltViewModel
class WantedBookViewModel @Inject constructor(private val source: CollectionsSource, saved: SavedStateHandle) : ViewModel() {
    private val id = saved.get<Int>(Routes.ARG_WANTED_ID) ?: 0
    private val mutableState = MutableStateFlow<ApiResult<WantedBook>?>(null)
    val state = mutableState.asStateFlow()
    private var generation = 0
    init { refresh() }
    fun refresh() { val request = ++generation; mutableState.value = null; viewModelScope.launch { val result = source.wantedBook(id); if (request == generation) mutableState.value = result } }
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(private val source: CollectionsSource, saved: SavedStateHandle) : ViewModel() {
    private val id = saved.get<Int>(Routes.ARG_ARCHIVE_ID) ?: 0
    private val mutableState = MutableStateFlow<ApiResult<ArchiveRecord>?>(null)
    val state = mutableState.asStateFlow()
    private var generation = 0
    init { refresh() }
    fun refresh() { val request = ++generation; mutableState.value = null; viewModelScope.launch { val result = source.archive(id); if (request == generation) mutableState.value = result } }
}
