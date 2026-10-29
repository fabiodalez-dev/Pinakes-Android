package com.pinakes.app.server090

import com.pinakes.app.R
import com.pinakes.app.data.model.StaffCopy
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.staff.StaffErrors
import com.pinakes.app.ui.screens.staff.content.copySummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Staff side of the 0.9.0 circulation rules: refusal codes, eligibility reasons, copies. */
class StaffErrors090Test {

    @Test fun theNewCirculationCodesAreMapped() {
        assertEquals(R.string.staff_err_media_limit, StaffErrors.messageRes("media_limit_reached"))
        assertEquals(R.string.staff_err_not_loanable, StaffErrors.messageRes("not_loanable"))
        assertEquals(R.string.staff_err_reservations_disabled, StaffErrors.messageRes("reservations_disabled"))
        assertEquals(R.string.staff_err_reservation_limit, StaffErrors.messageRes("reservation_limit_reached"))
        assertEquals(R.string.staff_err_max_loans, StaffErrors.messageRes("max_loans_reached"))
    }

    @Test fun notEligibleUsesMetaReason() {
        val debt = parseErrorBody("""{"data":null,"meta":{"reason":"debt_block"},"error":{"code":"not_eligible","message":"x"}}""", 409)
        assertEquals(R.string.staff_err_debt_block, StaffErrors.messageRes(debt))
        val card = parseErrorBody("""{"data":null,"meta":{"reason":"card_expired"},"error":{"code":"not_eligible","message":"x"}}""", 409)
        assertEquals(R.string.staff_err_card_expired, StaffErrors.messageRes(card))
    }

    @Test fun notEligibleWithAnUnknownOrMissingReasonKeepsTheGenericText() {
        val none = parseErrorBody("""{"data":null,"meta":{},"error":{"code":"not_eligible","message":"x"}}""", 409)
        assertEquals(R.string.staff_err_not_eligible, StaffErrors.messageRes(none))
        val future = parseErrorBody("""{"data":null,"meta":{"reason":"something_new"},"error":{"code":"not_eligible","message":"x"}}""", 409)
        assertEquals(R.string.staff_err_not_eligible, StaffErrors.messageRes(future))
    }

    @Test fun anUnknownCodeIsLeftToTheServerMessage() {
        assertNull(StaffErrors.messageRes("brand_new_code"))
        assertNull(StaffErrors.eligibilityRes(null))
        assertNull(StaffErrors.eligibilityRes("brand_new_reason"))
        assertEquals(R.string.staff_err_user_suspended, StaffErrors.eligibilityRes("user_suspended"))
    }

    @Test fun aConsultationOnlyCopySaysSoBetweenStateAndNotes() {
        val copy = StaffCopy(id = 1, consultationOnly = true, notes = "Reference shelf")
        assertEquals("Available · Consultation only · Reference shelf", copySummary(copy, "Available", "Consultation only"))
        assertEquals("Available", copySummary(StaffCopy(id = 2), "Available", "Consultation only"))
    }

    @Test fun theCopyFlagDefaultsToFalseOnOlderServers() {
        val json = Json { ignoreUnknownKeys = true }
        assertFalse(json.decodeFromString<StaffCopy>("""{"id":3,"status":"disponibile"}""").consultationOnly)
        assertTrue(json.decodeFromString<StaffCopy>("""{"id":3,"consultation_only":true}""").consultationOnly)
    }
}
