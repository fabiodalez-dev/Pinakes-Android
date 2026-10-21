package com.pinakes.app.ui.screens.staff.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.pinakes.app.data.model.StaffOffer
import com.pinakes.app.data.model.StaffWantedBook
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffPluginsRepository
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tabs: the offer statuses, then the library's wanted titles. */
enum class DesiderataTab(val wire: String, val label: Int) {
    PENDING("pending", R.string.staff_offers_pending),
    ACCEPTED("accepted", R.string.staff_offers_accepted),
    RECEIVED("received", R.string.staff_offers_received),
    REJECTED("rejected", R.string.staff_offers_rejected),
    ALL("all", R.string.staff_offers_all),
    WANTED("wanted", R.string.staff_offers_wanted),
}

/** Which offer actions make sense for a status; the server answers 409 `offer_closed` otherwise. */
object OfferActions {
    fun canAccept(o: StaffOffer) = o.status == "pending"
    fun canReject(o: StaffOffer) = o.status == "pending" || o.status == "accepted"
    fun canReceive(o: StaffOffer) = o.status == "pending" || o.status == "accepted"
    /** Without a linked wanted title the server needs the catalogue record the copy goes to. */
    fun needsBook(o: StaffOffer) = o.book == null || o.book.id <= 0
}

data class DesiderataUiState(
    val tab: DesiderataTab = DesiderataTab.PENDING,
    val query: String = "",
    val offers: List<StaffOffer> = emptyList(),
    val wanted: List<StaffWantedBook> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val selected: StaffOffer? = null,
    val confirmDelete: StaffOffer? = null,
    /** A wanted title waiting for "mark received" to be confirmed: it adds a copy at once. */
    val confirmReceive: StaffWantedBook? = null,
    /** A receipt created this copy of this record: the screen opens its editor (code, place). */
    val openCopy: Pair<Int, Int>? = null,
    val pickingBookFor: StaffOffer? = null,
    val busy: Boolean = false,
    val message: StaffMessage? = null,
)

@HiltViewModel
class StaffDesiderataViewModel @Inject constructor(
    private val plugins: StaffPluginsRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DesiderataUiState())
    val state: StateFlow<DesiderataUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    val books = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = staff.books(q, cursor = null, limit = 20)) {
            is ApiResult.Success -> ApiResult.Success(res.data.items.map { PickItem(it.id, it.title, it.author) })
            is ApiResult.Failure -> res
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init { reload() }

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.offers.isEmpty() && it.wanted.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val s = _state.value
            if (s.tab == DesiderataTab.WANTED) {
                val res = plugins.wanted(s.query)
                if (gen != generation) return@launch
                _state.update {
                    when (res) {
                        is ApiResult.Success -> it.copy(wanted = res.data.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false)
                        is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                    }
                }
            } else {
                val res = plugins.offers(s.tab.wire, s.query)
                if (gen != generation) return@launch
                _state.update {
                    when (res) {
                        is ApiResult.Success -> it.copy(offers = res.data.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false)
                        is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                    }
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    /** The next page of the current tab (offers or wanted titles). */
    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        val gen = generation
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            if (s.tab == DesiderataTab.WANTED) {
                val res = plugins.wanted(s.query, cursor)
                _state.update {
                    when {
                        gen != generation -> it.copy(loadingMore = false)
                        res is ApiResult.Success -> it.copy(wanted = (it.wanted + res.data.items).distinctBy(StaffWantedBook::id), nextCursor = res.data.nextCursor, loadingMore = false)
                        res is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load))
                        else -> it.copy(loadingMore = false)
                    }
                }
            } else {
                val res = plugins.offers(s.tab.wire, s.query, cursor)
                _state.update {
                    when {
                        gen != generation -> it.copy(loadingMore = false)
                        res is ApiResult.Success -> it.copy(offers = (it.offers + res.data.items).distinctBy(StaffOffer::id), nextCursor = res.data.nextCursor, loadingMore = false)
                        res is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load))
                        else -> it.copy(loadingMore = false)
                    }
                }
            }
        }
    }

    fun setTab(tab: DesiderataTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, offers = emptyList(), wanted = emptyList(), nextCursor = null) }
        reload()
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); reload() }
    }

    fun select(offer: StaffOffer?) = _state.update { it.copy(selected = offer) }

    fun accept() = act(R.string.staff_offer_accepted) { plugins.acceptOffer(it.id) }
    fun reject() = act(R.string.staff_offer_rejected) { plugins.rejectOffer(it.id) }

    fun receive() {
        val offer = _state.value.selected ?: return
        if (OfferActions.needsBook(offer)) _state.update { it.copy(pickingBookFor = offer, selected = null) }
        else act(R.string.staff_offer_received) { plugins.receiveOffer(it.id, null) }
    }

    fun receiveInto(book: PickItem) {
        val offer = _state.value.pickingBookFor ?: return
        _state.update { it.copy(pickingBookFor = null, selected = offer) }
        books.clear()
        act(R.string.staff_offer_received) { plugins.receiveOffer(offer.id, book.id) }
    }

    fun closeBookPicker() {
        _state.update { it.copy(pickingBookFor = null) }
        books.clear()
    }

    private fun act(success: Int, call: suspend (StaffOffer) -> ApiResult<StaffOffer>) {
        val offer = _state.value.selected ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = call(offer)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, selected = null, message = StaffMessage(success), openCopy = receivedCopy(res.data))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            reload()
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = it.selected, selected = null) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = null) }

    fun delete() {
        val offer = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            val res = plugins.deleteOffer(offer.id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(message = StaffMessage(R.string.staff_offer_deleted))
                    is ApiResult.Failure -> it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            reload()
        }
    }

    fun askReceiveWanted(book: StaffWantedBook) = _state.update { it.copy(confirmReceive = book) }
    fun dismissReceiveWanted() = _state.update { it.copy(confirmReceive = null) }

    /** "Mark received" on a wanted title, once confirmed: the copy arrived outside an offer (bought, given at the desk). */
    fun receiveWanted() {
        val book = _state.value.confirmReceive ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, confirmReceive = null) }
        viewModelScope.launch {
            val res = plugins.receiveWanted(book.id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        busy = false, message = StaffMessage(R.string.staff_wanted_received, args = listOf(book.title)),
                        // A response without book_id still names the title being received.
                        openCopy = res.data.copyId?.takeIf { c -> c > 0 }?.let { c -> (res.data.bookId.takeIf { b -> b > 0 } ?: book.id) to c },
                    )
                    is ApiResult.Failure -> it.copy(
                        busy = false, forbidden = res.isForbidden(),
                        message = if (res.meta?.reason == "open_offers") StaffMessage(R.string.staff_wanted_open_offers) else res.toStaffMessage(R.string.staff_error_save),
                    )
                }
            }
            reload()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeOpenCopy() = _state.update { it.copy(openCopy = null) }

    /** The record and copy a received offer produced, when the server names both. */
    private fun receivedCopy(offer: StaffOffer): Pair<Int, Int>? {
        if (offer.status != "received") return null
        val book = offer.receivedBook?.id?.takeIf { it > 0 } ?: offer.book?.id?.takeIf { it > 0 } ?: return null
        val copy = offer.copyId?.takeIf { it > 0 } ?: return null
        return book to copy
    }
}

internal fun offerStatusLabel(status: String): Int? = when (status) {
    "pending" -> R.string.staff_offer_status_pending
    "accepted" -> R.string.staff_offer_status_accepted
    "received" -> R.string.staff_offer_status_received
    "rejected" -> R.string.staff_offer_status_rejected
    else -> null
}

/** Donation offers (accept, reject, receive into the catalogue, delete) and the wanted titles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDesiderataScreen(
    onNavigateUp: () -> Unit,
    onOpenBook: (Int) -> Unit,
    onOpenCopy: (bookId: Int, copyId: Int) -> Unit = { book, _ -> onOpenBook(book) },
) {
    val vm: StaffDesiderataViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.desiderata, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    // A receipt made a copy: go straight to it, to give it its code and place.
    LaunchedEffect(state.openCopy) { state.openCopy?.let { (book, copy) -> vm.consumeOpenCopy(); onOpenCopy(book, copy) } }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_desiderata), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = Spacing.lg) {
                DesiderataTab.entries.forEach { tab -> Tab(selected = tab == state.tab, onClick = { vm.setTab(tab) }, text = { Text(stringResource(tab.label)) }) }
            }
            SearchField(
                query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff_offers_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                val wantedTab = state.tab == DesiderataTab.WANTED
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = if (wantedTab) state.wanted.isEmpty() else state.offers.isEmpty(),
                    emptyTitle = stringResource(if (wantedTab) R.string.staff_wanted_empty else R.string.staff_offers_empty),
                    emptyIcon = Icons.Outlined.VolunteerActivism, onRetry = vm::refresh,
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                        if (wantedTab) {
                            items(state.wanted, key = { "w${it.id}" }) { w ->
                                MediaRow(
                                    title = w.title, coverUrl = w.coverUrl, line1 = w.author,
                                    line2 = stringResource(R.string.staff_wanted_open_offers_count, w.openOffers),
                                    onClick = { onOpenBook(w.id) },
                                    trailing = { PinakesTextButton(stringResource(R.string.staff_wanted_mark_received), onClick = { vm.askReceiveWanted(w) }, enabled = !state.busy) },
                                    modifier = Modifier.padding(vertical = Spacing.xs).testTag("wanted-${w.id}"),
                                )
                            }
                        } else {
                            items(state.offers, key = { "o${it.id}" }) { offer ->
                                Surface(
                                    onClick = { vm.select(offer) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("offer-${offer.id}"),
                                ) {
                                    Column(Modifier.padding(Spacing.md)) {
                                        Text(offer.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(offer.author, offer.donorName?.let { stringResource(R.string.staff_offer_from, it) }).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            listOfNotNull(offerStatusLabel(offer.status)?.let { stringResource(it) } ?: offer.statusLabel ?: offer.status,
                                                offer.createdAt?.let(DateFormat::date)).joinToString(" · "),
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.nextCursor != null) item(key = "more") {
                            LaunchedEffect(if (wantedTab) state.wanted.size else state.offers.size) { vm.loadMore() }
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { offer -> OfferSheet(offer, state.busy, vm, onOpenBook) }
    state.confirmReceive?.let { book ->
        ConfirmDialog(
            title = stringResource(R.string.staff_wanted_receive_confirm_title), body = stringResource(R.string.staff_wanted_receive_confirm_body, book.title),
            confirmLabel = stringResource(R.string.staff_wanted_mark_received), onConfirm = vm::receiveWanted, onDismiss = vm::dismissReceiveWanted,
        )
    }
    state.confirmDelete?.let { offer ->
        ConfirmDialog(
            title = stringResource(R.string.staff_offer_delete), body = stringResource(R.string.staff_offer_delete_body, offer.title),
            confirmLabel = stringResource(R.string.staff_offer_delete), onConfirm = vm::delete, onDismiss = vm::dismissDelete,
        )
    }
    if (state.pickingBookFor != null) {
        val s by vm.books.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.books.start(state.pickingBookFor?.title.orEmpty()) }
        SearchPickerSheet(
            title = stringResource(R.string.staff_offer_receive_into), placeholder = stringResource(R.string.staff_books_search), state = s,
            onQuery = vm.books::onQuery, onPick = vm::receiveInto, onDismiss = vm::closeBookPicker, hint = stringResource(R.string.staff_offer_receive_into_hint),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferSheet(offer: StaffOffer, busy: Boolean, vm: StaffDesiderataViewModel, onOpenBook: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!busy) vm.select(null) }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(offer.title, style = MaterialTheme.typography.headlineSmall)
            InfoLine(stringResource(R.string.staff_offer_author), offer.author)
            InfoLine(stringResource(R.string.staff_offer_publisher), offer.publisher)
            InfoLine("ISBN", offer.isbn)
            InfoLine(stringResource(R.string.staff_offer_donor), listOfNotNull(offer.donorName, offer.donorEmail).joinToString(" · ").ifBlank { null })
            InfoLine(stringResource(R.string.staff_field_notes), offer.notes)
            InfoLine(stringResource(R.string.staff_offer_for_title), offer.book?.title)
            InfoLine(stringResource(R.string.staff_offer_received_into), offer.receivedBook?.title)
            if (OfferActions.canReceive(offer)) {
                PrimaryButton(stringResource(R.string.staff_offer_receive), onClick = vm::receive, loading = busy, modifier = Modifier.fillMaxWidth().testTag("offer-receive"))
            }
            if (OfferActions.canAccept(offer)) SecondaryButton(stringResource(R.string.staff_offer_accept), onClick = vm::accept, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (OfferActions.canReject(offer)) SecondaryButton(stringResource(R.string.staff_offer_reject), onClick = vm::reject, enabled = !busy, modifier = Modifier.fillMaxWidth())
            (offer.receivedBook ?: offer.book)?.takeIf { it.id > 0 }?.let { b ->
                SecondaryButton(stringResource(R.string.staff_open_book), onClick = { vm.select(null); onOpenBook(b.id) }, modifier = Modifier.fillMaxWidth())
            }
            PinakesTextButton(stringResource(R.string.staff_offer_delete), onClick = vm::askDelete, enabled = !busy)
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
