package com.pinakes.app.staff

import com.pinakes.app.data.model.StaffAuthorWrite
import com.pinakes.app.data.model.StaffPublisherWrite
import com.pinakes.app.ui.screens.staff.RecordForm
import com.pinakes.app.ui.screens.staff.toAuthorWrite
import com.pinakes.app.ui.screens.staff.toPublisherWrite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Author/publisher saves must be able to clear a field: cleared fields go out as "". */
class RecordWritesTest {

    // Same configuration as NetworkModule.json: null keys are dropped from requests.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun clearedAuthorFieldsAreSentAsEmptyStrings() {
        val body = json.encodeToJsonElement(
            StaffAuthorWrite.serializer(),
            RecordForm(id = 3, name = " Italo Calvino ", pseudonym = "", born = "", died = "", website = " ", gndId = ""),
        ).jsonObject
        assertEquals("Italo Calvino", body.str("name"))
        for (key in listOf("pseudonym", "born", "died", "nationality", "biography", "website", "gnd_id")) {
            assertEquals("key $key", "", body.str(key))
        }
    }

    @Test fun filledAuthorFieldsAreTrimmed() {
        val body = json.encodeToJsonElement(StaffAuthorWrite.serializer(), RecordForm(name = "A", born = " 1923-10-15 ")).jsonObject
        assertEquals("1923-10-15", body.str("born"))
    }

    @Test fun clearedPublisherFieldsAreSentAsEmptyStrings() {
        val body = json.encodeToJsonElement(StaffPublisherWrite.serializer(), RecordForm(name = "Einaudi", website = "", phone = "")).jsonObject
        assertEquals(setOf("name", "website", "address", "phone", "email"), body.keys)
        assertEquals("", body.str("website"))
        assertEquals("", body.str("email"))
    }

    private fun Json.encodeToJsonElement(serializer: kotlinx.serialization.KSerializer<StaffAuthorWrite>, form: RecordForm) =
        encodeToJsonElement(serializer, form.toAuthorWrite())

    @JvmName("encodePublisher")
    private fun Json.encodeToJsonElement(serializer: kotlinx.serialization.KSerializer<StaffPublisherWrite>, form: RecordForm) =
        encodeToJsonElement(serializer, form.toPublisherWrite())

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun aFormThatWasNotLoadedCanNeverBeSubmitted() {
        // Placeholder after a failed GET /staff/authors/{id}: saving it would wipe the record.
        assertEquals(false, RecordForm(id = 3, name = "Italo Calvino", loaded = false).canSubmit)
        assertEquals(true, RecordForm(id = 3, name = "Italo Calvino").canSubmit)
        assertEquals(true, RecordForm(name = "New author").canSubmit)
        assertEquals(false, RecordForm(name = "  ").canSubmit)
    }
}
