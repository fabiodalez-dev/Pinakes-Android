package com.pinakes.app.ui.screens.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.MovementsPayload
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovementsUiState(
    val content: UiState<MovementsPayload> = UiState.Loading,
    val refreshing: Boolean = false,
    /**
     * The library turned the fines module off (404 `feature_disabled`, or a server without the
     * route): the page leaves at once instead of showing an error, as the profile entry is gone too.
     */
    val disabled: Boolean = false,
    /** A pull-to-refresh failed: the page keeps the last balance and says it may be out of date. */
    val refreshFailed: Boolean = false,
) {
    companion object {
        /**
         * The state after a fetch. Pure: a 404 (`feature_disabled` or `not_found` from a server
         * without the route) means "no ledger here", never an error; a failed refresh keeps the
         * page already on screen.
         */
        fun after(previous: MovementsUiState, result: ApiResult<MovementsPayload>): MovementsUiState = when (result) {
            is ApiResult.Success -> previous.copy(content = UiState.Success(result.data), refreshing = false, disabled = false, refreshFailed = false)
            is ApiResult.Failure -> when {
                result.code == "feature_disabled" || result.httpStatus == 404 -> previous.copy(refreshing = false, disabled = true)
                previous.content is UiState.Success -> previous.copy(refreshing = false, refreshFailed = true)
                else -> previous.copy(content = UiState.Error(result.message, result.code, R.string.movements_error_load), refreshing = false)
            }
        }
    }
}

/** What the reader owes, in one place: the sign decides the wording of the balance card. */
enum class BalanceKind { Owed, Settled, Credit }

object MoneyFormat {

    /**
     * A LazyColumn key for a ledger row: its id when the server sent one, else its position.
     * Ids default to 0 when omitted, and Compose throws on a duplicate key.
     */
    fun rowKey(prefix: String, index: Int, id: Int): String = if (id > 0) "$prefix-$id" else "$prefix-at-$index"

    fun balanceKind(cents: Int): BalanceKind = when {
        cents > 0 -> BalanceKind.Owed
        cents < 0 -> BalanceKind.Credit
        else -> BalanceKind.Settled
    }

    /**
     * [cents] in [currency] (an ISO 4217 code such as "EUR") for [locale]. A currency the
     * platform does not know (a symbol such as "€" typed in the settings) is appended to the
     * plain decimal instead, so the amount is never lost to a formatting failure.
     */
    fun format(cents: Int, currency: String?, locale: Locale): String {
        val amount = BigDecimal.valueOf(cents.toLong(), 2)
        val code = currency?.trim().orEmpty()
        val iso = runCatching { Currency.getInstance(code.uppercase(Locale.ROOT)) }.getOrNull()
        if (iso != null) {
            val nf = NumberFormat.getCurrencyInstance(locale)
            nf.currency = iso
            nf.minimumFractionDigits = 2
            nf.maximumFractionDigits = 2
            return nf.format(amount)
        }
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val plain = nf.format(amount)
        return if (code.isEmpty()) plain else "$plain $code"
    }
}

/** Balance and movements of the fines ledger (Pinakes 0.9.0). */
@HiltViewModel
class MovementsViewModel @Inject constructor(
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MovementsUiState())
    val state: StateFlow<MovementsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(content = UiState.Loading) }
        fetch()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        fetch()
    }

    fun consumeRefreshFailed() = _state.update { it.copy(refreshFailed = false) }

    private fun fetch() {
        viewModelScope.launch {
            val res = library.movements()
            _state.update { MovementsUiState.after(it, res) }
        }
    }
}
