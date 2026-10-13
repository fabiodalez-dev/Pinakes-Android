package com.pinakes.app

import com.pinakes.app.ui.common.DateFormat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app language can change while the process keeps running (Profile → Language). Dates
 * must follow it on the next render instead of keeping the language of the first format call.
 */
class DateFormatLocaleTest {
    private lateinit var saved: Locale

    @Before fun save() { saved = Locale.getDefault() }

    @After fun restore() { Locale.setDefault(saved) }

    @Test fun date_followsALanguageChangeInTheSameProcess() {
        Locale.setDefault(Locale.US)
        val english = DateFormat.date("2026-10-09")
        Locale.setDefault(Locale.ITALY)
        val italian = DateFormat.date("2026-10-09")
        assertTrue("english was $english", english.contains("Oct"))
        assertTrue("italian was $italian", italian.contains("ott"))
    }

    @Test fun dateTime_followsALanguageChangeInTheSameProcess() {
        Locale.setDefault(Locale.US)
        val english = DateFormat.dateTime("2026-10-09 19:53:00")
        Locale.setDefault(Locale.GERMANY)
        val german = DateFormat.dateTime("2026-10-09 19:53:00")
        assertTrue("english was $english", english.contains("Oct"))
        // German MEDIUM dates are numeric (09.10.2026).
        assertTrue("german was $german", german.startsWith("09.10.2026"))
    }
}
