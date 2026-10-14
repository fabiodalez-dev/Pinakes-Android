package com.pinakes.app.staff

import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.data.staff.PenaltyInput
import com.pinakes.app.data.staff.ReturnOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The return sheet's penalty field: decimal comma or point, two decimals, 0..99999999.99, lost/damaged only. */
class PenaltyValidationTest {

    private fun parse(raw: String, outcome: ReturnOutcome = ReturnOutcome.DAMAGED) = LoanActions.parsePenalty(raw, outcome)

    @Test fun blankIsNoPenalty() {
        assertEquals(PenaltyInput.None, parse(""))
        assertEquals(PenaltyInput.None, parse("   ", ReturnOutcome.RETURNED))
    }

    @Test fun acceptsPointAndComma() {
        assertEquals(PenaltyInput.Amount(12.5), parse("12.5"))
        assertEquals(PenaltyInput.Amount(12.5), parse("12,50"))
        assertEquals(PenaltyInput.Amount(0.0), parse("0"))
        assertEquals(PenaltyInput.Amount(99_999_999.99), parse("99999999.99", ReturnOutcome.LOST))
    }

    @Test fun rejectsMalformedOrOutOfRange() {
        listOf("-1", "1.234", "12a", "1e3", "100000000", "1.2.3", ",5", "5,").forEach {
            assertEquals(it, PenaltyInput.Invalid, parse(it))
        }
    }

    @Test fun onlyLostOrDamagedCarryAPenalty() {
        listOf(ReturnOutcome.RETURNED, ReturnOutcome.MAINTENANCE, ReturnOutcome.RESTORATION).forEach {
            assertEquals(PenaltyInput.Invalid, parse("5", it))
        }
    }
}
