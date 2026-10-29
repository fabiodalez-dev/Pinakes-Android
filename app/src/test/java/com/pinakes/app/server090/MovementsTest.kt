package com.pinakes.app.server090

import com.pinakes.app.R
import com.pinakes.app.data.model.MovementsPayload
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.screens.movements.BalanceKind
import com.pinakes.app.ui.screens.movements.MoneyFormat
import com.pinakes.app.ui.screens.movements.MovementsUiState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** `GET /me/movements` (Mobile API 1.9.0): parsing, the page state and money formatting. */
class MovementsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val serverBody = """{
        "currency":"EUR","balance_cents":450,"balance":"4.50","accrued_cents":120,
        "accrued":[{"loan_id":9,"title":"Il nome della rosa","due_at":"2030-03-01","days_late":6,"amount_cents":120}],
        "debt_block_threshold_cents":300,"debt_blocked":true,
        "items":[{"id":2,"type":"pagamento","type_label":"Pagamento","amount_cents":-200,"amount":"-2.00","description":"Contanti","payment_method":"contanti","loan_id":null,"book_title":null,"created_at":"2030-03-05 10:00:00"},
                 {"id":1,"type":"multa_ritardo","type_label":"Multa per ritardo","amount_cents":650,"amount":"6.50","description":"Ritardo","loan_id":7,"book_title":"Emma","created_at":"2030-03-04 09:00:00"}]
    }"""

    @Test fun theServerPayloadIsReadInFull() {
        val p = json.decodeFromString<MovementsPayload>(serverBody)
        assertEquals("EUR", p.currency)
        assertEquals(450, p.balanceCents)
        assertTrue(p.debtBlocked)
        assertEquals(300, p.debtBlockThresholdCents)
        assertEquals(2, p.items.size)
        assertEquals(-200, p.items[0].amountCents)
        assertNull(p.items[0].loanId)
        assertEquals(7, p.items[1].loanId)
        assertEquals(6, p.accrued.single().daysLate)
    }

    @Test fun theAccruedTotalFallsBackToTheRowsOnlyWhenAbsent() {
        val rowsOnly = json.decodeFromString<MovementsPayload>("""{"accrued":[{"loan_id":1,"amount_cents":120},{"loan_id":2,"amount_cents":30}]}""")
        assertEquals(150, rowsOnly.accruedTotalCents)
        val explicit = json.decodeFromString<MovementsPayload>("""{"accrued_cents":0,"accrued":[{"loan_id":1,"amount_cents":120}]}""")
        assertEquals(0, explicit.accruedTotalCents)
        assertEquals(120, json.decodeFromString<MovementsPayload>(serverBody).accruedTotalCents)
    }

    @Test fun theDecimalBalanceIsTheFallbackForAMissingBalanceCents() {
        assertEquals(450, json.decodeFromString<MovementsPayload>("""{"balance":"4.50"}""").balanceTotalCents)
        assertEquals(-200, json.decodeFromString<MovementsPayload>("""{"balance":"-2,00"}""").balanceTotalCents)
        assertEquals(0, json.decodeFromString<MovementsPayload>("""{"balance":"n/a"}""").balanceTotalCents)
        // balance_cents wins when both are there.
        assertEquals(300, json.decodeFromString<MovementsPayload>("""{"balance_cents":300,"balance":"9.99"}""").balanceTotalCents)
    }

    @Test fun thePlannedNameMovementsIsReadToo() {
        val p = json.decodeFromString<MovementsPayload>("""{"balance_cents":0,"movements":[{"id":5,"type":"condono","amount_cents":-100}]}""")
        assertEquals(5, p.items.single().id)
    }

    @Test fun aBarePayloadMissingEveryFieldStillParses() {
        val p = json.decodeFromString<MovementsPayload>("{}")
        assertEquals(0, p.balanceTotalCents)
        assertFalse(p.debtBlocked)
        assertNull(p.debtBlockThresholdCents)
        assertTrue(p.items.isEmpty())
    }

    @Test fun featureDisabledClosesThePageWithoutAnError() {
        val s = MovementsUiState.after(MovementsUiState(), ApiResult.Failure("feature_disabled", "Funzione non disponibile", 404, fromEnvelope = true))
        assertTrue(s.disabled)
        assertTrue(s.content is UiState.Loading)
    }

    @Test fun aServerWithoutTheRouteIsTreatedAsDisabled() {
        assertTrue(MovementsUiState.after(MovementsUiState(), ApiResult.Failure("not_found", "Not found.", 404)).disabled)
    }

    @Test fun aServerErrorIsShownWithItsFallback() {
        val s = MovementsUiState.after(MovementsUiState(), ApiResult.Failure("internal_error", "", 500))
        assertFalse(s.disabled)
        assertEquals(R.string.movements_error_load, (s.content as UiState.Error).messageRes)
    }

    @Test fun aFailedRefreshKeepsThePageOnScreenAndSaysSo() {
        val loaded = MovementsUiState(content = UiState.Success(MovementsPayload(balanceCents = 100)), refreshing = true)
        val s = MovementsUiState.after(loaded, ApiResult.Failure("network", "", 0))
        assertEquals(100, (s.content as UiState.Success).data.balanceCents)
        assertFalse(s.refreshing)
        assertTrue(s.refreshFailed)
        // The next successful refresh clears it.
        assertFalse(MovementsUiState.after(s, ApiResult.Success(MovementsPayload())).refreshFailed)
    }

    @Test fun rowsWithoutIdsGetDistinctKeys() {
        val p = json.decodeFromString<MovementsPayload>("""{"items":[{"type":"pagamento"},{"type":"condono"},{"id":4,"type":"storno"}],"accrued":[{},{}]}""")
        val keys = p.items.mapIndexed { i, m -> MoneyFormat.rowKey("movement", i, m.id) } +
            p.accrued.mapIndexed { i, a -> MoneyFormat.rowKey("accrued", i, a.loanId) }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("movement-4", keys[2])
    }

    @Test fun theBalanceSignPicksTheWording() {
        assertEquals(BalanceKind.Owed, MoneyFormat.balanceKind(1))
        assertEquals(BalanceKind.Settled, MoneyFormat.balanceKind(0))
        assertEquals(BalanceKind.Credit, MoneyFormat.balanceKind(-50))
    }

    @Test fun amountsAreFormattedInTheLibraryCurrency() {
        val it = MoneyFormat.format(450, "EUR", Locale.ITALY)
        assertTrue(it, it.contains("4,50") && it.contains("€"))
        val us = MoneyFormat.format(123456, "usd", Locale.US)
        assertEquals("$1,234.56", us)
    }

    @Test fun anUnknownCurrencyIsAppendedNotLost() {
        assertEquals("4.50 €", MoneyFormat.format(450, "€", Locale.US))
        assertEquals("4.50", MoneyFormat.format(450, null, Locale.US))
    }

    @Test fun theProfileReadsThePatronTypeWhenPresent() {
        val withType = json.decodeFromString<UserProfile>("""{"id":1,"patron_type":{"code":"student","name":"Studente"}}""")
        assertEquals("Studente", withType.patronType?.name)
        assertNull(json.decodeFromString<UserProfile>("""{"id":1}""").patronType)
    }
}
