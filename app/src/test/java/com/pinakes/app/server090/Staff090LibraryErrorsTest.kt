package com.pinakes.app.server090

import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.repository.Staff090LibraryRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.Staff090LibraryErrors
import com.pinakes.app.ui.screens.staff.s090.library.exitMessage
import com.pinakes.app.ui.screens.staff.s090.library.toLibraryMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error bodies of the library settings areas, as the server sent them (captured samples). */
class Staff090LibraryErrorsTest {

    private fun raw(name: String) = javaClass.getResourceAsStream("/server090/library/$name.json")!!.bufferedReader().readText()
    private fun failure(name: String, status: Int): ApiResult.Failure = parseErrorBody(raw(name), status)

    @Test fun ourCodesHaveOurWording() {
        assertEquals(R.string.staff090_library_err_always_closed, Staff090LibraryErrors.messageRes("calendar_always_closed"))
        assertEquals(R.string.staff090_library_err_preset_closure, Staff090LibraryErrors.messageRes("preset_closure"))
        assertEquals(R.string.staff090_library_err_schema_not_ready, Staff090LibraryErrors.messageRes("schema_not_ready"))
        assertEquals(R.string.staff090_library_err_ics_too_large, Staff090LibraryErrors.messageRes("file_too_large"))
        assertNull(Staff090LibraryErrors.messageRes("validation"))
        assertEquals(R.string.staff090_library_err_preset_closure, failure("s090_library_closure_preset_refused", 409).toLibraryMessage(R.string.staff_error_save).res)
    }

    @Test fun theDefaultTypeRefusalShowsTheServerMessage() {
        val f = failure("s090_library_type_delete_default", 409)
        assertEquals("default_type", f.code)
        val message = f.toLibraryMessage(R.string.staff_error_save)
        assertNull(message.res)
        assertTrue(message.text!!.startsWith("La tipologia predefinita"))
        assertNull(f.exitMessage())
    }

    @Test fun anAdministratorOnlyAreaClosesWithTheServerMessage() {
        val forbidden = failure("s090_library_types_staff_refused", 403)
        assertTrue(Staff090LibraryErrors.closesScreen(forbidden))
        assertEquals("Operazione riservata agli amministratori", forbidden.exitMessage()?.text)
        val disabled = failure("s090_library_types_staff_disabled", 404)
        assertEquals("feature_disabled", disabled.code)
        assertTrue(Staff090LibraryErrors.closesScreen(disabled))
        assertEquals("Funzione non disponibile su questa istanza.", disabled.exitMessage()?.text)
        // A 404 of a single record or a validation error leaves the screen open.
        assertFalse(Staff090LibraryErrors.closesScreen(failure("s090_library_type_missing", 404)))
        assertFalse(Staff090LibraryErrors.closesScreen(failure("s090_library_loans_invalid", 422)))
        // An HTML 403 page (no envelope) is not taken as the server's verdict.
        assertFalse(Staff090LibraryErrors.closesScreen(parseErrorBody("<html>Forbidden</html>", 403)))
    }

    /**
     * The admin-only refusal uses the same `forbidden` the staff role gate uses: that is why
     * the repository double-checks before dropping the role (see Staff090LibraryRepository).
     */
    @Test fun anAdministratorOnlyRefusalLooksLikeTheRoleGate() {
        assertTrue(failure("s090_library_types_staff_refused", 403).isForbidden())
    }

    @Test fun fieldMessagesComeFromMetaFields() {
        assertEquals(mapOf("loan_duration_days" to "Valore non valido."), Staff090LibraryRepository.fieldErrors(raw("s090_library_loans_invalid")))
        assertEquals(mapOf("daily_fine_cents" to "Operazione riservata agli amministratori"), Staff090LibraryRepository.fieldErrors(raw("s090_library_loans_staff_refused")))
        assertEquals(mapOf("cells.0.max_loans" to "Valore non valido."), Staff090LibraryRepository.fieldErrors(raw("s090_library_rules_invalid")))
        assertEquals("Indica il nome della festività.", Staff090LibraryRepository.fieldErrors(raw("s090_library_custom_invalid"))["name"])
        assertEquals(setOf("ics_file"), Staff090LibraryRepository.fieldErrors(raw("s090_library_ics_invalid")).keys)
        assertTrue(Staff090LibraryRepository.fieldErrors(raw("s090_library_types_staff_refused")).isEmpty())
        assertTrue(Staff090LibraryRepository.fieldErrors(null).isEmpty())
        assertTrue(Staff090LibraryRepository.fieldErrors("<html>").isEmpty())
    }
}
