package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
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
        open { json("POST staff/desiderata/offers/41/receive", "offer_41_received") }
        clickTag("offer-41")
        clickTag("offer-receive")
        assertEquals("{}", lastBody("POST", "staff/desiderata/offers/41/receive"))
        waitText(text(R.string.staff_offer_received))
    }

    @Test fun anOfferWithoutATitleIsReceivedIntoAPickedRecord() {
        open { json("POST staff/desiderata/offers/42/receive", "offer_42_received") }
        clickTag("offer-42")
        clickTag("offer-receive")
        waitText(text(R.string.staff_offer_receive_into))
        clickTag("pick-5")
        assertEquals("""{"received_book_id":5}""", lastBody("POST", "staff/desiderata/offers/42/receive"))
        waitText(text(R.string.staff_offer_received))
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
