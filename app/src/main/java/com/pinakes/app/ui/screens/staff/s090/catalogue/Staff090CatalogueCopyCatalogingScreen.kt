package com.pinakes.app.ui.screens.staff.s090.catalogue

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.CopyCatalogingAvailability
import com.pinakes.app.data.model.CopyCatalogingImport
import com.pinakes.app.data.model.CopyCatalogingImportRequest
import com.pinakes.app.data.model.CopyCatalogingResult
import com.pinakes.app.data.model.CopyCatalogingSearch
import com.pinakes.app.data.model.CopyCatalogingSearchRequest
import com.pinakes.app.data.model.CopyCatalogingServers
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090CatalogueRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.CopyCatalogingRules
import com.pinakes.app.data.staff.CopyImportSubmission
import com.pinakes.app.data.staff.ScanDebouncer
import com.pinakes.app.data.staff.Staff090CatalogueErrors
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.QuantityStepper
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.scan.BarcodeAnalyzer
import com.pinakes.app.ui.screens.staff.scan.ScannerBox
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CopyCatalogingUiState(
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    /** From /staff/marc/options; false shows the reason instead of the form. */
    val availability: CopyCatalogingAvailability? = null,
    val config: CopyCatalogingServers? = null,
    val servers: Set<String> = emptySet(),
    val index: String = "isbn",
    val term: String = "",
    val max: Int = CopyCatalogingRules.DEFAULT_MAX,
    val scanning: Boolean = false,
    val searching: Boolean = false,
    val searchError: StaffMessage? = null,
    val search: CopyCatalogingSearch? = null,
    val selected: Set<String> = emptySet(),
    val duplicates: String = "skip",
    val covers: Boolean = false,
    val importing: Boolean = false,
    val importError: StaffMessage? = null,
    val result: CopyCatalogingImport? = null,
    val forbidden: Boolean = false,
    val message: StaffMessage? = null,
) {
    val canSearch: Boolean get() = !searching && CopyCatalogingRules.canSearch(servers, term, max)
    val importLimit: Int get() = config?.maxImportIds ?: CopyCatalogingRules.DEFAULT_IMPORT_LIMIT
}

/**
 * Copy cataloguing (module `marc` + z39-server plugin): search other catalogues (SRU servers,
 * SBN, national catalogues, this installation) and import the chosen records. The search is
 * kept on the server under `search_id`; the import names it and carries an Idempotency-Key kept
 * across retries of the same selection.
 */
@HiltViewModel
class Staff090CatalogueCopyCatalogingViewModel @Inject constructor(
    private val repo: Staff090CatalogueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CopyCatalogingUiState())
    val state: StateFlow<CopyCatalogingUiState> = _state.asStateFlow()
    private val keys = SubmissionKeys()
    private val debouncer = ScanDebouncer()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val options = repo.marcOptions()
            if (options is ApiResult.Failure) {
                _state.update { it.copy(loading = false, forbidden = options.isForbidden(), error = options.toCatalogueMessage(R.string.staff_error_load, copyCataloguing = true)) }
                return@launch
            }
            val availability = (options as ApiResult.Success).data.copyCataloging
            if (!availability.available) {
                _state.update { it.copy(loading = false, availability = availability) }
                return@launch
            }
            when (val res = repo.copyCatalogingServers()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false, availability = availability, config = res.data,
                        index = res.data.indexes.firstOrNull { i -> i.value == it.index }?.value ?: res.data.indexes.firstOrNull()?.value ?: it.index,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    // The plugin went away between the two calls: show its reason, not an error.
                    if (res.code == "copy_cataloging_unavailable") it.copy(loading = false, availability = CopyCatalogingAvailability(false, "plugin_inactive"))
                    else it.copy(loading = false, forbidden = res.isForbidden(), error = res.toCatalogueMessage(R.string.staff_error_load, copyCataloguing = true))
                }
            }
        }
    }

    fun toggleServer(key: String) = _state.update { s -> s.copy(servers = if (key in s.servers) s.servers - key else s.servers + key) }
    fun setIndex(v: String) = _state.update { it.copy(index = v) }
    fun setTerm(v: String) = _state.update { it.copy(term = v.take(CopyCatalogingRules.MAX_TERM), searchError = null) }
    fun setMax(v: Int) = _state.update { it.copy(max = v.coerceIn(CopyCatalogingRules.MAX_RANGE)) }
    fun setScanning(v: Boolean) = _state.update { it.copy(scanning = v) }

    /** A barcode under the camera: two identical reads, then it becomes the search term. */
    fun onDecoded(raw: String) {
        if (!_state.value.scanning) return
        val (index, term) = CopyCatalogingRules.fromScan(raw) ?: return
        debouncer.offer(term, SystemClock.elapsedRealtime()) ?: return
        val known = _state.value.config?.indexes?.any { it.value == index } != false
        _state.update { it.copy(index = if (known) index else "any", term = term, scanning = false, searchError = null) }
        if (_state.value.canSearch) search()
    }

    fun search() {
        val s = _state.value
        if (!s.canSearch) return
        _state.update { it.copy(searching = true, searchError = null, result = null, importError = null) }
        viewModelScope.launch {
            val request = CopyCatalogingSearchRequest(servers = s.config?.servers?.map { it.key }?.filter { it in s.servers } ?: s.servers.toList(), index = s.index, term = s.term.trim(), max = s.max)
            when (val res = repo.copyCatalogingSearch(request)) {
                is ApiResult.Success -> _state.update { it.copy(searching = false, search = res.data, selected = emptySet()) }
                is ApiResult.Failure -> _state.update {
                    it.copy(searching = false, forbidden = res.isForbidden(), searchError = res.toCatalogueMessage(R.string.staff090_catalogue_copy_search_failed, copyCataloguing = true))
                }
            }
        }
    }

    fun toggleResult(id: String) = _state.update { s -> s.copy(selected = CopyCatalogingRules.toggle(s.selected, id, s.importLimit)) }
    fun setDuplicates(v: String) = _state.update { it.copy(duplicates = v) }
    fun setCovers(v: Boolean) = _state.update { it.copy(covers = v) }

    fun import() {
        val s = _state.value
        val search = s.search ?: return
        if (s.importing || s.selected.isEmpty()) return
        // Result order, so the same selection is the same request whatever the tap order.
        val ids = search.results.map { it.id }.filter { it in s.selected }
        val submission = CopyImportSubmission(search.searchId, ids, s.duplicates, s.covers)
        _state.update { it.copy(importing = true, importError = null) }
        viewModelScope.launch {
            val res = repo.copyCatalogingImport(CopyCatalogingImportRequest(search.searchId, ids, s.duplicates, s.covers), keys.keyFor(submission))
            when (res) {
                is ApiResult.Success -> {
                    keys.reset()
                    _state.update { it.copy(importing = false, result = res.data, selected = emptySet()) }
                }
                is ApiResult.Failure -> {
                    if (res.code == SubmissionKeys.CONFLICT) keys.reset()
                    _state.update {
                        it.copy(importing = false, forbidden = res.isForbidden(), importError = res.toCatalogueMessage(R.string.staff090_catalogue_copy_import_failed, copyCataloguing = true))
                    }
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Staff090CatalogueCopyCatalogingScreen(onNavigateUp: () -> Unit, onOpenBook: (Int) -> Unit) {
    val vm: Staff090CatalogueCopyCatalogingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.marc, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_copy_cataloging), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val config = state.config
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(message = state.error?.resolve().orEmpty(), onRetry = vm::load, modifier = Modifier.padding(padding))
            state.availability?.available == false || config == null -> EmptyState(
                title = stringResource(R.string.staff090_catalogue_copy_unavailable_title),
                subtitle = stringResource(Staff090CatalogueErrors.copyCatalogingReasonRes(state.availability?.reason)),
                icon = Icons.Outlined.TravelExplore, modifier = Modifier.padding(padding).testTag("copy-unavailable"),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                item(key = "form") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FormSectionTitle(stringResource(R.string.staff090_catalogue_copy_servers))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            config.servers.forEach { server ->
                                FilterChip(
                                    selected = server.key in state.servers, onClick = { vm.toggleServer(server.key) }, label = { Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                    modifier = Modifier.testTag("copy-server-${server.key}"),
                                )
                            }
                        }
                        val indexes = config.indexes.asOptions()
                        LookupField(
                            label = stringResource(R.string.staff090_catalogue_copy_index), value = state.index, displayValue = indexes.labelOf(state.index),
                            options = indexes, editable = false, allowClear = false, onValueChange = vm::setIndex, modifier = Modifier.fillMaxWidth(),
                        )
                        PinakesTextField(
                            value = state.term, onValueChange = vm::setTerm, label = stringResource(R.string.staff090_catalogue_copy_term),
                            maxLength = CopyCatalogingRules.MAX_TERM,
                            keyboardOptions = KeyboardOptions(keyboardType = if (state.index == "isbn" || state.index == "issn") KeyboardType.Ascii else KeyboardType.Text, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                            trailingIcon = {
                                IconButton(onClick = { vm.setScanning(!state.scanning) }) {
                                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = stringResource(R.string.staff090_catalogue_copy_scan))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("copy-term"),
                        )
                        if (state.scanning) {
                            ScannerBox(
                                formats = BarcodeAnalyzer.ISBN_FORMATS, onCode = vm::onDecoded, looking = false,
                                permissionText = stringResource(R.string.staff090_catalogue_copy_scan_permission),
                            )
                        }
                        QuantityStepper(stringResource(R.string.staff090_catalogue_copy_max), state.max, vm::setMax, range = CopyCatalogingRules.MAX_RANGE)
                        state.searchError?.let { InlineErrorBanner(message = it.resolve()) }
                        PrimaryButton(
                            stringResource(R.string.staff090_catalogue_copy_search), onClick = vm::search, loading = state.searching, enabled = state.canSearch,
                            leadingIcon = Icons.Outlined.Search, modifier = Modifier.fillMaxWidth().testTag("copy-search"),
                        )
                    }
                }
                state.search?.let { search ->
                    item(key = "report") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FormSectionTitle(stringResource(R.string.staff090_catalogue_copy_results))
                            InfoCard {
                                search.servers.forEach { r ->
                                    InfoLine(r.name.ifBlank { r.key }, r.error ?: stringResource(R.string.staff090_catalogue_copy_server_count, r.count, r.total))
                                }
                            }
                            if (search.results.isEmpty()) {
                                Text(stringResource(R.string.staff090_catalogue_copy_no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(search.results, key = { "r${it.id}" }) { result ->
                        ResultRow(result, selected = result.id in state.selected) { vm.toggleResult(result.id) }
                    }
                    if (search.results.isNotEmpty()) {
                        item(key = "import") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                                val modes = config.duplicateModes.asOptions()
                                LookupField(
                                    label = stringResource(R.string.staff090_catalogue_marc_duplicates), value = state.duplicates, displayValue = modes.labelOf(state.duplicates),
                                    options = modes, editable = false, allowClear = false, onValueChange = vm::setDuplicates, modifier = Modifier.fillMaxWidth(),
                                )
                                config.duplicateModes.firstOrNull { it.value == state.duplicates }?.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SwitchRow(stringResource(R.string.staff090_catalogue_copy_covers), state.covers, vm::setCovers)
                                state.importError?.let { InlineErrorBanner(message = it.resolve()) }
                                PrimaryButton(
                                    stringResource(R.string.staff090_catalogue_copy_import, state.selected.size), onClick = vm::import, loading = state.importing,
                                    enabled = state.selected.isNotEmpty(), leadingIcon = Icons.Outlined.Download, modifier = Modifier.fillMaxWidth().testTag("copy-import"),
                                )
                            }
                        }
                    }
                }
                state.result?.let { result ->
                    item(key = "result") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FormSectionTitle(stringResource(R.string.staff090_catalogue_copy_result))
                            InfoCard(Modifier.testTag("copy-result")) {
                                InfoLine(stringResource(R.string.staff_import_imported), result.imported.toString())
                                InfoLine(stringResource(R.string.staff_import_updated), result.updated.toString())
                                InfoLine(stringResource(R.string.staff090_catalogue_marc_skipped), result.skipped.toString())
                                InfoLine(stringResource(R.string.staff_import_errors), result.failed.toString())
                                InfoLine(stringResource(R.string.staff090_catalogue_marc_covers), result.covers.toString().takeIf { result.covers > 0 })
                            }
                        }
                    }
                    items(result.items, key = { "i${it.id}" }) { item ->
                        val action = stringResource(actionLabel(item.action))
                        val title = item.title.ifBlank { item.id }
                        val bookId = item.bookId
                        if (bookId != null && bookId > 0) {
                            StaffActionRow(Icons.AutoMirrored.Outlined.MenuBook, title, listOfNotNull(action, item.message).joinToString(" · ")) { onOpenBook(bookId) }
                        } else {
                            InfoCard(Modifier.padding(vertical = Spacing.xs)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(action, item.message).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        item.warnings.forEach { w -> Text(w, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.lg)) }
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }
}

@Composable
internal fun ResultRow(result: CopyCatalogingResult, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("copy-result-${result.id}"),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = stringResource(if (selected) R.string.staff090_catalogue_copy_selected else R.string.staff090_catalogue_copy_not_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(result.title.ifBlank { result.id }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                result.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    listOfNotNull(result.authors, result.year, result.publisher).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(result.isbn, result.server).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (result.existingId != null) {
                    Text(stringResource(R.string.staff090_catalogue_in_catalogue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** The import outcome of one record, in the app's words. */
internal fun actionLabel(action: String): Int = when (action) {
    "created" -> R.string.staff090_catalogue_copy_action_created
    "updated" -> R.string.staff090_catalogue_copy_action_updated
    "skipped" -> R.string.staff090_catalogue_copy_action_skipped
    else -> R.string.staff090_catalogue_copy_action_failed
}
