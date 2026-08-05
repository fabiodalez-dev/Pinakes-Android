package com.pinakes.app

import com.pinakes.app.data.model.BuiltinFieldRule
import com.pinakes.app.data.model.CustomFieldValue
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.screens.profile.ProfileUiState
import com.pinakes.app.ui.screens.profile.isCanonicalProfileGender
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the profile-edit contract shared with PATCH /me. */
class ProfileUiStateTest {

    private fun ready(customFields: List<CustomFieldValue> = emptyList()) = ProfileUiState(
        profile = UiState.Success(UserProfile(customFields = customFields)),
        editNome = "Mario",
        editCognome = "Rossi",
        editTelefono = "3331234567",
        editIndirizzo = "Via Roma 1",
    )

    @Test fun nomeRemainsRequired() {
        assertTrue(ready().copy(editNome = "").hasBlankRequiredProfileField())
    }

    @Test fun schemaRequiredBuiltinIsValidated() {
        val state = ready().copy(
            editTelefono = "",
            builtinFields = mapOf("telefono" to BuiltinFieldRule(required = true, configurable = true)),
        )
        assertTrue(state.hasBlankRequiredProfileField())
    }

    @Test fun unloadedSchemaDoesNotInventBuiltinRequirements() {
        assertFalse(ready().copy(editTelefono = "", editIndirizzo = "").hasBlankRequiredProfileField())
    }

    @Test fun newlyRequiredCustomFieldDoesNotBlockExistingMember() {
        val requiredButEmpty = CustomFieldValue(id = 7, label = "Matricola", required = true, value = "")
        val state = ready(listOf(requiredButEmpty)).copy(editCustomValues = mapOf(7 to ""))
        assertFalse(state.hasBlankRequiredProfileField())
    }

    @Test fun profileGenderValuesMatchServerEnum() {
        assertTrue(listOf("", "M", "F", "Altro").all(::isCanonicalProfileGender))
        assertFalse(isCanonicalProfileGender("Maschio"))
        assertFalse(isCanonicalProfileGender("Other"))
    }
}
