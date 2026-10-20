package com.pinakes.app.staffe2e

import android.content.ClipDescription
import android.content.ClipboardManager
import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Profile → loans calendar: create, masked link, sensitive copy, confirmed regenerate/turn off. */
class CalendarFlowTest : AppFlowTest() {

    private val token = "0123456789abcdef".repeat(4)

    @Test fun createCopyRegenerateAndTurnOff() {
        launch { json("POST me/calendar/token", "calendar_enabled", 201) }
        openTab(R.string.nav_profile)
        clickUntil(text(R.string.profile_action_calendar), text(R.string.calendar_enable))
        click(text(R.string.calendar_enable))
        waitRequest("POST", "me/calendar/token")

        // Only host and tail of the secret are on screen.
        waitText("localhost/…abcdef.ics")
        assertFalse(exists(token, substring = true))

        click(text(R.string.calendar_copy_link))
        var sensitive = false
        var copied: String? = null
        instrumentation.runOnMainSync {
            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
            copied = clip?.getItemAt(0)?.text?.toString()
            sensitive = clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        }
        assertTrue("clip not flagged sensitive", sensitive)
        assertEquals("http://localhost/api/v1/calendar/feed/$token.ics", copied)

        // Regenerate asks first; cancelling sends nothing.
        click(text(R.string.calendar_regenerate))
        waitText(text(R.string.calendar_regenerate_confirm))
        click(text(R.string.action_cancel))
        assertEquals(1, api.recorded("POST", "me/calendar/token").size)

        // Turning it off asks too.
        click(text(R.string.calendar_disable))
        waitText(text(R.string.calendar_disable_confirm))
        click(text(R.string.action_cancel))
        assertTrue(api.recorded("DELETE", "me/calendar/token").isEmpty())
    }
}
