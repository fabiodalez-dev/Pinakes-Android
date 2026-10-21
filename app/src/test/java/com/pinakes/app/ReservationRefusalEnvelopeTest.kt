package com.pinakes.app

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.ReservationRequest
import com.pinakes.app.data.network.parseErrorBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the server says to the app on POST /reservations (Mobile API 1.8.2): a `not_available`
 * refusal carries its own sentence as the message and the longest loan still possible in meta;
 * the request carries `end_date` only when the sheet shortened the loan; the availability
 * payload names both durations.
 */
class ReservationRefusalEnvelopeTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test fun aNotAvailableRefusalCarriesTheSentenceAndTheFigures() {
        val body = """{"data":null,"meta":{"max_duration_days":4,"max_end_date":"2030-03-08","first_conflict_date":"2030-03-09"},
            "error":{"code":"not_available","message":"Durata massima del prestito: 4 giorni (restituzione entro il 08-03-2030). Dal 09-03-2030 il libro è già prenotato."}}"""
        val failure = parseErrorBody(body, 409)
        assertEquals("not_available", failure.code)
        assertTrue(failure.fromEnvelope)
        assertTrue(failure.message.contains("4 giorni"))
        assertEquals("2030-03-08", failure.meta?.maxEndDate)
        assertEquals(4, failure.meta?.maxDurationDays)
        assertEquals("2030-03-09", failure.meta?.firstConflictDate)
    }

    @Test fun aRefusalWithoutFiguresLeavesThemNull() {
        val body = """{"data":null,"meta":{},"error":{"code":"not_available","message":"Il libro non è disponibile per il periodo richiesto."}}"""
        val failure = parseErrorBody(body, 409)
        assertEquals("not_available", failure.code)
        assertNull(failure.meta?.maxEndDate)
        assertNull(failure.meta?.maxDurationDays)
    }

    @Test fun anOverLongReturnDateIsAValidationRefusalWithItsOwnCode() {
        val failure = parseErrorBody("""{"data":null,"meta":{},"error":{"code":"max_duration_exceeded","message":"x"}}""", 422)
        assertEquals("max_duration_exceeded", failure.code)
        assertEquals(422, failure.httpStatus)
    }

    @Test fun theRequestCarriesEndDateOnlyWhenTheSheetShortenedTheLoan() {
        val plain = json.encodeToString(ReservationRequest(bookId = 7, desiredDate = "2030-03-04"))
        assertFalse(plain.contains("end_date"))
        val capped = json.encodeToString(ReservationRequest(bookId = 7, desiredDate = "2030-03-04", endDate = "2030-03-08"))
        assertTrue(capped.contains("\"end_date\":\"2030-03-08\""))
    }

    @Test fun theAvailabilityPayloadNamesBothDurationsAndOlderServersOmitTheMaximum() {
        val both = json.decodeFromString<AvailabilityCalendar>("""{"total_copies":1,"loan_duration_days":21,"max_loan_duration_days":90}""")
        assertEquals(21, both.loanDurationDays)
        assertEquals(90, both.maxLoanDurationDays)
        assertNull(json.decodeFromString<AvailabilityCalendar>("""{"total_copies":1,"loan_duration_days":21}""").maxLoanDurationDays)
    }
}
