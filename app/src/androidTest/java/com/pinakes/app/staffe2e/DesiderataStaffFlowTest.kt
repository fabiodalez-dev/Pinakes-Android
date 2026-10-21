package com.pinakes.app.staffe2e

import com.pinakes.app.R
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Donation offers: receive an offer for a wanted title, receive one into a picked record, a closed offer. */
class DesiderataStaffFlowTest : AppFlowTest() {

    private fun open(configure: FakePinakes.() -> Unit) {
        launch {
            json("GET staff/desiderata/offers", "offers_pending")
            configure()
        }
        openStaffHub()
        click(text(R.string.staff_desiderata))
        waitText("Un libro qualunque")
    }

    @Test fun receiveAnOfferForAWantedTitle() {
        open {
            json("POST staff/desiderata/offers/41/receive", "offer_41_received")
            json("GET staff/books/5", "staff_book_5_received")
        }
        clickTag("offer-41")
        clickTag("offer-receive")
        assertEquals("{}", lastBody("POST", "staff/desiderata/offers/41/receive"))
        // The new copy opens on the record, ready for its code and place.
        waitFor(hasTestTag("copy-save"))
    }

    @Test fun anOfferWithoutATitleIsReceivedIntoAPickedRecord() {
        open {
            json("POST staff/desiderata/offers/42/receive", "offer_42_received")
            json("GET staff/books/5", "staff_book_5_received")
        }
        clickTag("offer-42")
        clickTag("offer-receive")
        waitText(text(R.string.staff_offer_receive_into))
        clickTag("pick-5")
        assertEquals("""{"received_book_id":5}""", lastBody("POST", "staff/desiderata/offers/42/receive"))
        waitFor(hasTestTag("copy-save"))
    }

    @Test fun aWantedTitleIsReceivedOnlyAfterConfirming() {
        open {
            json("GET staff/desiderata/wanted", "desiderata_wanted")
            json("POST staff/desiderata/wanted/77/receive", "desiderata_wanted_77_received", 201)
            json("GET staff/books/77", "staff_book_77_received")
        }
        click(text(R.string.staff_offers_wanted))
        waitText("Non diciamo no da sole")
        // The row's button is an action, not a status: a tap only asks.
        click(text(R.string.staff_wanted_mark_received))
        waitText(text(R.string.staff_wanted_receive_confirm_title))
        assertTrue(api.recorded("POST", "staff/desiderata/wanted/77/receive").isEmpty())
        click(text(R.string.action_cancel))
        assertTrue(api.recorded("POST", "staff/desiderata/wanted/77/receive").isEmpty())
        click(text(R.string.staff_wanted_mark_received))
        waitText(text(R.string.staff_wanted_receive_confirm_title))
        compose.onAllNodesWithText(text(R.string.staff_wanted_mark_received)).onLast().performClick()
        waitRequest("POST", "staff/desiderata/wanted/77/receive")
        assertEquals(1, api.recorded("POST", "staff/desiderata/wanted/77/receive").size)
        // Received: the record opens on the new copy's editor, for its status and notes. The
        // server gives the copy its code, which no client changes afterwards (web included).
        waitFor(hasTestTag("copy-save"))
        waitText(text(R.string.staff_copy_code_value, "LIB-77-C901"))
    }

    @Test fun aClosedOfferSaysSo() {
        open {
            body("POST staff/desiderata/offers/41/accept", 409, """{"data":null,"meta":{},"error":{"code":"offer_closed","message":"x"}}""")
        }
        clickTag("offer-41")
        click(text(R.string.staff_offer_accept))
        waitText(text(R.string.staff_err_offer_closed))
    }
}
