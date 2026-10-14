package com.pinakes.app.ui.screens.staff.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.DuplicateAuthor
import com.pinakes.app.data.model.FlatGenre
import com.pinakes.app.data.model.flatten
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MergeTab(val label: Int) { AUTHORS(R.string.staff_authors), PUBLISHERS(R.string.staff_publishers), GENRES(R.string.staff_genres) }

/** The records about to be merged into one: the primary keeps its id; the others disappear. */
data class MergeSelection(val items: List<PickItem> = emptyList(), val primaryId: Int? = null, val newName: String = "") {
    val ready: Boolean get() = items.size >= 2 && primaryId != null && items.any { it.id == primaryId }
}

data class MergeUiState(
    val tab: MergeTab = MergeTab.AUTHORS,
    val duplicates: List<List<DuplicateAuthor>> = emptyList(),
    val duplicatesLoading: Boolean = true,
    val selection: MergeSelection = MergeSelection(),
    val picking: Boolean = false,
    val confirming: Boolean = false,
    val genres: List<FlatGenre> = emptyList(),
    val genreSource: FlatGenre? = null,
    val genreTarget: FlatGenre? = null,
    val genrePicking: Boolean? = null,
    val busy: Boolean = false,
    val error: StaffMessage? = null,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffMergeViewModel @Inject constructor(
    private val content: StaffContentRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    val search = SearchController(viewModelScope, minLength = 2) { q ->
        when (_state.value.tab) {
            MergeTab.PUBLISHERS -> when (val res = staff.publishers(q, 30)) {
                is ApiResult.Success -> ApiResult.Success(res.data.map { PickItem(it.id, it.name, it.website) })
                is ApiResult.Failure -> res
            }
            else -> when (val res = staff.authors(q, 30)) {
                is ApiResult.Success -> ApiResult.Success(res.data.map { PickItem(it.id, it.display, listOfNotNull(it.born, it.died).joinToString("–").ifBlank { null }) })
                is ApiResult.Failure -> res
            }
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init { loadDuplicates() }

    fun loadDuplicates() {
        _state.update { it.copy(duplicatesLoading = true) }
        viewModelScope.launch {
            val res = content.authorDuplicates()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(duplicates = res.data.filter { g -> g.size >= 2 }, duplicatesLoading = false)
                    is ApiResult.Failure -> it.copy(duplicatesLoading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun selectTab(tab: MergeTab) {
        _state.update { it.copy(tab = tab, selection = MergeSelection(), error = null) }
        search.clear()
        if (tab == MergeTab.GENRES && _state.value.genres.isEmpty()) viewModelScope.launch {
            when (val res = staff.genres()) {
                is ApiResult.Success -> _state.update { it.copy(genres = res.data.flatten()) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load)) }
            }
        }
    }

    /** A duplicate group goes to the confirm sheet; the record with most books is the default primary. */
    fun useGroup(group: List<DuplicateAuthor>) = _state.update {
        it.copy(
            selection = MergeSelection(
                items = group.map { a -> PickItem(a.id, a.name, null) },
                primaryId = group.maxByOrNull { a -> a.booksCount }?.id,
            ),
            confirming = true, error = null,
        )
    }

    fun openPicker() = _state.update { it.copy(picking = true) }
    fun closePicker() { _state.update { it.copy(picking = false) }; search.clear() }

    fun toggle(item: PickItem) = _state.update { s ->
        val items = if (s.selection.items.any { it.id == item.id }) s.selection.items.filterNot { it.id == item.id } else s.selection.items + item
        s.copy(selection = s.selection.copy(items = items, primaryId = s.selection.primaryId?.takeIf { id -> items.any { it.id == id } } ?: items.firstOrNull()?.id))
    }

    fun remove(item: PickItem) = toggle(item)
    fun setPrimary(id: Int) = _state.update { it.copy(selection = it.selection.copy(primaryId = id)) }
    fun setNewName(value: String) = _state.update { it.copy(selection = it.selection.copy(newName = value)) }
    fun openConfirm() = _state.update { it.copy(confirming = it.selection.ready, picking = false) }
    fun closeConfirm() = _state.update { it.copy(confirming = false, error = null) }

    fun merge() {
        val s = _state.value
        if (!s.selection.ready || s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val ids = s.selection.items.map { it.id }
            val res = if (s.tab == MergeTab.PUBLISHERS) content.mergePublishers(ids, s.selection.primaryId, s.selection.newName)
            else content.mergeAuthors(ids, s.selection.primaryId, s.selection.newName)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, confirming = false, selection = MergeSelection(), message = StaffMessage(R.string.staff_merge_done, args = listOf(ids.size)))
                    // A retried merge whose records are already gone: nothing left to do.
                    is ApiResult.Failure -> if (res.code == "merge_error" && !res.meta?.missing.isNullOrEmpty()) {
                        it.copy(busy = false, confirming = false, selection = MergeSelection(), message = StaffMessage(R.string.staff_merge_already_done))
                    } else {
                        it.copy(busy = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save))
                    }
                }
            }
            if (s.tab == MergeTab.AUTHORS && (res is ApiResult.Success || (res as? ApiResult.Failure)?.code == "merge_error")) loadDuplicates()
        }
    }

    fun pickGenre(source: Boolean?) = _state.update { it.copy(genrePicking = source) }

    fun setGenre(genre: FlatGenre) = _state.update {
        if (it.genrePicking == true) it.copy(genreSource = genre, genrePicking = null, error = null) else it.copy(genreTarget = genre, genrePicking = null, error = null)
    }

    fun mergeGenre() {
        val source = _state.value.genreSource ?: return
        val target = _state.value.genreTarget ?: return
        if (source.id == target.id || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val res = content.mergeGenre(source.id, target.id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        busy = false, genreSource = null, genreTarget = null, genres = it.genres.filterNot { g -> g.id == source.id },
                        message = StaffMessage(R.string.staff_merge_genre_done, args = listOf(res.data.booksUpdated, res.data.childrenMoved)),
                    )
                    is ApiResult.Failure -> it.copy(
                        busy = false, forbidden = res.isForbidden(),
                        error = if (res.meta?.reason == "descendant") StaffMessage(R.string.staff_merge_genre_descendant) else res.toStaffMessage(R.string.staff_error_save),
                    )
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Merge duplicate authors (suggested groups or a manual pick), publishers, and genres. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffMergeScreen(onNavigateUp: () -> Unit) {
    val vm: StaffMergeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.merges, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_merges), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                MergeTab.entries.forEach { tab -> Tab(selected = tab == state.tab, onClick = { vm.selectTab(tab) }, text = { Text(stringResource(tab.label)) }) }
            }
            if (state.tab == MergeTab.GENRES) GenreMerge(state, vm) else RecordMerge(state, vm)
        }
    }

    if (state.picking) {
        val s by vm.search.state.collectAsStateWithLifecycle()
        SearchPickerSheet(
            title = stringResource(R.string.staff_merge_pick), placeholder = stringResource(if (state.tab == MergeTab.PUBLISHERS) R.string.staff_search_publishers else R.string.staff_search_authors),
            state = s, onQuery = vm.search::onQuery, onPick = vm::toggle, onDismiss = vm::closePicker,
            selected = state.selection.items.map { it.id }.toSet(), onConfirm = vm::closePicker, hint = stringResource(R.string.staff_search_min_chars),
        )
    }
    if (state.confirming) MergeConfirmSheet(state, vm)
    state.genrePicking?.let { source ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { vm.pickGenre(null) }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(Spacing.lg)) {
                item { Text(stringResource(if (source) R.string.staff_merge_genre_source else R.string.staff_merge_genre_target), style = MaterialTheme.typography.headlineSmall) }
                items(state.genres, key = { it.id }) { g ->
                    Surface(onClick = { vm.setGenre(g) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(start = (g.depth * 16).dp, top = Spacing.xs, bottom = Spacing.xs)) {
                        Text(g.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordMerge(state: MergeUiState, vm: StaffMergeViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
        item(key = "manual") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
                Text(stringResource(R.string.staff_merge_manual_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.selection.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.remove(item) }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_remove)) }
                    }
                }
                SecondaryButton(stringResource(R.string.staff_merge_pick), onClick = vm::openPicker, modifier = Modifier.fillMaxWidth())
                PrimaryButton(stringResource(R.string.staff_merge_review), onClick = vm::openConfirm, enabled = state.selection.items.size >= 2, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.tab == MergeTab.AUTHORS) {
            item(key = "dup-title") { FormSectionTitle(stringResource(R.string.staff_merge_suggested)) }
            if (!state.duplicatesLoading && state.duplicates.isEmpty()) item(key = "dup-empty") {
                Text(stringResource(R.string.staff_merge_no_duplicates), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(state.duplicates, key = { i, g -> "g$i-${g.first().id}" }) { index, group ->
                Surface(
                    onClick = { vm.useGroup(group) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("dup-group-$index"),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            group.forEach { a ->
                                Text(stringResource(R.string.staff_merge_author_books, a.name, a.booksCount), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Icon(Icons.AutoMirrored.Outlined.MergeType, contentDescription = stringResource(R.string.staff_merge_review), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreMerge(state: MergeUiState, vm: StaffMergeViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.staff_merge_genre_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { InlineErrorBanner(message = it.resolve()) }
        PickerRow(stringResource(R.string.staff_merge_genre_source), state.genreSource?.path, stringResource(R.string.staff_merge_genre_pick), onClick = { vm.pickGenre(true) })
        PickerRow(stringResource(R.string.staff_merge_genre_target), state.genreTarget?.path, stringResource(R.string.staff_merge_genre_pick), onClick = { vm.pickGenre(false) })
        PrimaryButton(
            stringResource(R.string.staff_merge_action), onClick = vm::mergeGenre, loading = state.busy,
            enabled = state.genreSource != null && state.genreTarget != null && state.genreSource.id != state.genreTarget.id, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeConfirmSheet(state: MergeUiState, vm: StaffMergeViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!state.busy) vm.closeConfirm() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff_merge_review), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff_merge_primary_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { InlineErrorBanner(message = it.resolve()) }
            state.selection.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = item.id == state.selection.primaryId, onClick = { vm.setPrimary(item.id) })
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
            PinakesTextField(value = state.selection.newName, onValueChange = vm::setNewName, label = stringResource(R.string.staff_merge_new_name),
                supportingText = stringResource(R.string.staff_merge_new_name_hint), modifier = Modifier.fillMaxWidth())
            PrimaryButton(stringResource(R.string.staff_merge_action), onClick = vm::merge, loading = state.busy, enabled = state.selection.ready,
                modifier = Modifier.fillMaxWidth().testTag("merge-confirm"))
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
