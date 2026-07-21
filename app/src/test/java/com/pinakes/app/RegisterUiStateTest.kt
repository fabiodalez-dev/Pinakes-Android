package com.pinakes.app

import com.pinakes.app.data.model.BuiltinFieldRule
import com.pinakes.app.data.model.CustomFieldDef
import com.pinakes.app.ui.screens.login.RegisterUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the registration form's submit gate + required-field
 * validation (issue #255 / the #26 review hardening). These are the exact
 * functions RegisterViewModel.submit() calls, so they guard the real behaviour
 * without needing the async ViewModel / repository.
 */
class RegisterUiStateTest {

    private fun ready() = RegisterUiState(
        nome = "Mario", email = "m@x.it", password = "Abcdef12",
        passwordConfirm = "Abcdef12", privacyAccepted = true,
        schemaLoading = false,
    )

    // ── builtinRequired: default required-when-absent (conservative signup) ──────
    @Test fun builtinRequiredDefaultsTrueWhenAbsent() {
        assertTrue(RegisterUiState().builtinRequired("cognome"))
    }

    @Test fun builtinRequiredHonoursFalse() {
        val s = RegisterUiState(builtinFields = mapOf("telefono" to BuiltinFieldRule(required = false, configurable = true)))
        assertFalse(s.builtinRequired("telefono"))
    }

    @Test fun builtinRequiredHonoursTrue() {
        val s = RegisterUiState(builtinFields = mapOf("indirizzo" to BuiltinFieldRule(required = true, configurable = true)))
        assertTrue(s.builtinRequired("indirizzo"))
    }

    // ── canSubmit: the submit gate ──────────────────────────────────────────────
    @Test fun canSubmitFalseWhileSchemaLoading() {
        assertFalse(ready().copy(schemaLoading = true).canSubmit())
    }

    @Test fun canSubmitFalseWhileLoading() {
        assertFalse(ready().copy(loading = true).canSubmit())
    }

    @Test fun canSubmitFalseWhenAlreadySent() {
        assertFalse(ready().copy(sent = true).canSubmit())
    }

    @Test fun canSubmitTrueWhenReady() {
        assertTrue(ready().canSubmit())
    }

    // ── hasBlankRequiredField: core + config-required built-ins + custom ─────────
    @Test fun blankNomeIsMissing() {
        assertTrue(ready().copy(nome = "").hasBlankRequiredField())
    }

    @Test fun blankEmailIsMissing() {
        assertTrue(ready().copy(email = "").hasBlankRequiredField())
    }

    @Test fun requiredCognomeBlankIsMissing() {
        val s = ready().copy(
            cognome = "",
            builtinFields = mapOf("cognome" to BuiltinFieldRule(required = true, configurable = true)),
        )
        assertTrue(s.hasBlankRequiredField())
    }

    @Test fun optionalTelefonoBlankIsNotMissing() {
        val s = ready().copy(
            telefono = "",
            builtinFields = mapOf("telefono" to BuiltinFieldRule(required = false, configurable = true)),
        )
        assertFalse(s.hasBlankRequiredField())
    }

    @Test fun requiredCustomFieldEmptyIsMissing() {
        val s = ready().copy(
            customFields = listOf(CustomFieldDef(id = 7, label = "Matricola", type = "number", required = true)),
            customValues = emptyMap(),
        )
        assertTrue(s.hasBlankRequiredField())
    }

    @Test fun requiredCheckboxUncheckedIsMissing() {
        val s = ready().copy(
            customFields = listOf(CustomFieldDef(id = 9, label = "Consenso", type = "checkbox", required = true)),
            customValues = mapOf(9 to ""),
        )
        assertTrue(s.hasBlankRequiredField())
    }

    @Test fun requiredCheckboxCheckedIsFilled() {
        val s = ready().copy(
            customFields = listOf(CustomFieldDef(id = 9, label = "Consenso", type = "checkbox", required = true)),
            customValues = mapOf(9 to "1"),
        )
        assertFalse(s.hasBlankRequiredField())
    }

    @Test fun optionalCustomFieldEmptyIsNotMissing() {
        val s = ready().copy(
            customFields = listOf(CustomFieldDef(id = 3, label = "Telegram", type = "text", required = false)),
            customValues = emptyMap(),
        )
        assertFalse(s.hasBlankRequiredField())
    }

    @Test fun allRequiredFilledIsNotMissing() {
        assertFalse(ready().hasBlankRequiredField())
    }
}
