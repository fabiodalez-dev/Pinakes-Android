package com.pinakes.app.server090

import com.pinakes.app.data.model.CopyUpdate
import com.pinakes.app.data.model.StaffCopy
import com.pinakes.app.ui.screens.staff.content.CopyDraft
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consultation-only copies from the copy editor (Pinakes 0.9.0): `consultation_only` on
 * `PUT /staff/copies/{id}`, sent only when it changed and the server applies the module.
 */
class StaffCopyConsultationTest {
    // The network client's settings: null fields are left out of the body.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val loaded = CopyDraft(id = 5, code = "INV1", status = "disponibile", originalStatus = "disponibile")

    @Test fun aChangedFlagIsSentWhenTheModuleIsOn() {
        assertEquals(true, loaded.copy(consultationOnly = true).consultationChange(editable = true))
        val wasConsultation = loaded.copy(consultationOnly = false, originalConsultationOnly = true)
        assertEquals(false, wasConsultation.consultationChange(editable = true))
    }

    @Test fun anUnchangedFlagANewCopyOrTheModuleOffSendsNothing() {
        assertNull(loaded.consultationChange(editable = true))
        assertNull(loaded.copy(consultationOnly = true, originalConsultationOnly = true).consultationChange(editable = true))
        assertNull(loaded.copy(consultationOnly = true).consultationChange(editable = false))
        assertNull(CopyDraft(id = 0, consultationOnly = true).consultationChange(editable = true))
    }

    @Test fun theKeyIsInTheBodyOnlyWhenSet() {
        assertFalse(json.encodeToString(CopyUpdate(notes = "n")).contains("consultation_only"))
        assertTrue(json.encodeToString(CopyUpdate(notes = "n", consultationOnly = true)).contains("\"consultation_only\":true"))
        assertTrue(json.encodeToString(CopyUpdate(consultationOnly = false)).contains("\"consultation_only\":false"))
    }

    @Test fun theServerFlagIsRead() {
        val copy = json.decodeFromString<StaffCopy>("""{"id":5,"inventory_number":"INV1","status":"disponibile","consultation_only":true}""")
        assertTrue(copy.consultationOnly)
        // An older server omits it: lendable.
        assertFalse(json.decodeFromString<StaffCopy>("""{"id":6,"status":"disponibile"}""").consultationOnly)
    }
}
