package com.pinakes.app.staff

import com.pinakes.app.data.model.ProfileFieldRule
import com.pinakes.app.data.model.RegistrationFieldsPayload
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.screens.profile.ProfileFieldVisibility
import com.pinakes.app.ui.screens.profile.ProfileFieldVisibility.GENDER
import com.pinakes.app.ui.screens.profile.ProfileFieldVisibility.TAX_CODE
import com.pinakes.app.ui.screens.profile.ProfileUiState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The admin can hide gender and tax code; older servers say nothing and keep them visible. */
class ProfileFieldVisibilityTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun visibleWhenTheServerSaysNothing() {
        assertTrue(ProfileFieldVisibility.isVisible(GENDER))
        assertTrue(ProfileFieldVisibility.isVisible(TAX_CODE, emptyList(), emptyMap(), null))
    }

    @Test fun anySourceHidesTheField() {
        assertFalse(ProfileFieldVisibility.isVisible(GENDER, hiddenFields = listOf("sesso")))
        assertFalse(ProfileFieldVisibility.isVisible(TAX_CODE, profileFields = mapOf("cod_fiscale" to ProfileFieldRule(visible = false))))
        assertFalse(ProfileFieldVisibility.isVisible(GENDER, healthShows = false))
        // Hiding one field leaves the other alone.
        assertTrue(ProfileFieldVisibility.isVisible(TAX_CODE, hiddenFields = listOf("sesso")))
    }

    @Test fun explicitlyVisibleStaysVisible() {
        assertTrue(ProfileFieldVisibility.isVisible(GENDER, emptyList(), mapOf("sesso" to ProfileFieldRule(visible = true)), true))
    }

    @Test fun profileStateFollowsMeAndTheSchema() {
        val hidden = ProfileUiState(profile = UiState.Success(UserProfile(hiddenFields = listOf("sesso", "cod_fiscale"))))
        assertFalse(hidden.showGender)
        assertFalse(hidden.showTaxCode)
        val schemaHidden = ProfileUiState(profileFields = mapOf("cod_fiscale" to ProfileFieldRule(visible = false)))
        assertTrue(schemaHidden.showGender)
        assertFalse(schemaHidden.showTaxCode)
        assertTrue(ProfileUiState().showGender && ProfileUiState().showTaxCode)
    }

    @Test fun payloadsDecodeTheNewKeysAndToleratePreviousServers() {
        val me = json.decodeFromString<UserProfile>("""{"id":1,"nome":"A","hidden_fields":["sesso","cod_fiscale"]}""")
        assertEquals(listOf("sesso", "cod_fiscale"), me.hiddenFields)
        assertNull(me.sesso)
        val old = json.decodeFromString<UserProfile>("""{"id":1,"nome":"A","sesso":"F"}""")
        assertTrue(old.hiddenFields.isEmpty())
        val reg = json.decodeFromString<RegistrationFieldsPayload>(
            """{"registration_enabled":true,"profile_fields":{"sesso":{"visible":false,"configurable":true},"data_nascita":{"visible":true,"configurable":false}}}"""
        )
        assertEquals(false, reg.profileFields["sesso"]?.visible)
        assertTrue(json.decodeFromString<RegistrationFieldsPayload>("""{"registration_enabled":true}""").profileFields.isEmpty())
    }
}
