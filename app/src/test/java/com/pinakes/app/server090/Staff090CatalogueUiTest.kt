package com.pinakes.app.server090

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.R
import com.pinakes.app.data.model.CopyCatalogingResult
import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.RdaEffective
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.Supplier
import com.pinakes.app.data.staff.BookCataloguing
import com.pinakes.app.ui.screens.staff.s090.catalogue.ResultRow
import com.pinakes.app.ui.screens.staff.s090.catalogue.Staff090CatalogueBookFields
import com.pinakes.app.ui.screens.staff.s090.catalogue.SupplierRow
import com.pinakes.app.ui.theme.PinakesTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The cataloguing pieces drawn alone: the book form's supplier/RDA block, a supplier row, a copy cataloguing result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Staff090CatalogueUiTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun list(raw: String): List<JsonElement> = Json.decodeFromString(raw)

    private val lookupsOn = StaffLookups(
        suppliers = listOf(IdName(21, "Libreria Cat090 Prova"), IdName(22, "Donatore")),
        rdaContentTypes = list("""[{"value":"txt","label":"Testo"},{"value":"spw","label":"Parola parlata"}]"""),
        rdaMediaTypes = list("""[{"value":"n","label":"Senza mediazione"},{"value":"s","label":"Audio"}]"""),
        rdaCarrierTypes = list("""[{"value":"nc","label":"Volume"},{"value":"cd","label":"Disco per computer"}]"""),
    )

    private val book = StaffBook(
        id = 9, title = "Libro", supplierId = 21, supplier = IdName(21, "Libreria Cat090 Prova"),
        rdaContentType = "spw", rdaCarrierType = "cd", rdaEffective = RdaEffective("spw", "n", "cd"),
    )

    @Test fun moduleOffShowsNoField() {
        compose.setContent { PinakesTheme { Column { Staff090CatalogueBookFields(StaffLookups(), book, BookCataloguing.from(book)) {} } } }
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("book-supplier")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("book-rda-content")).fetchSemanticsNodes().size)
    }

    @Test fun supplierAndRdaShowStoredOverridesAndTheDerivedValue() {
        compose.setContent { PinakesTheme { Column { Staff090CatalogueBookFields(lookupsOn, book, BookCataloguing.from(book)) {} } } }
        compose.onNodeWithText("Libreria Cat090 Prova").assertIsDisplayed()
        compose.onNodeWithText("Parola parlata").assertIsDisplayed()
        // Media type has no override: it shows what the server derives from the media type.
        compose.onNodeWithText(text(R.string.staff090_catalogue_rda_derived_value, "Senza mediazione")).assertIsDisplayed()
        compose.onNodeWithText("Disco per computer").assertIsDisplayed()
    }

    @Test fun pickingAnotherSupplierAndGoingBackToDerived() {
        var value by mutableStateOf(BookCataloguing.from(book))
        compose.setContent { PinakesTheme { Column { Staff090CatalogueBookFields(lookupsOn, book, value) { value = it } } } }
        compose.onNodeWithTag("book-supplier").performClick()
        compose.onNodeWithText("Donatore").performClick()
        compose.runOnIdle { assertEquals(22, value.supplierId) }
        compose.onNodeWithTag("book-rda-content").performClick()
        // The stored override had no derived label: the generic one is offered.
        compose.onNodeWithText(text(R.string.staff090_catalogue_rda_derived)).performClick()
        compose.runOnIdle { assertEquals(null, value.rdaContentType) }
    }

    @Test fun supplierRowMenuOnlyWithTheServersPermission() {
        val supplier = Supplier(id = 21, name = "Libreria Cat090 Prova", email = "ordini@example.org", bookCount = 2, canEdit = true, canDelete = true)
        var deleted = 0
        var current by mutableStateOf(supplier)
        compose.setContent { PinakesTheme { SupplierRow(current, deleting = false, onOpen = {}, onDelete = { deleted++ }) } }
        compose.onNodeWithText(text(R.string.staff090_catalogue_supplier_books, 2), substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.cd_more_options)).performClick()
        compose.onNodeWithText(text(R.string.staff090_catalogue_supplier_delete)).performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
        current = supplier.copy(canEdit = false, canDelete = false)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription(text(R.string.cd_more_options)).fetchSemanticsNodes().isEmpty())
    }

    @Test fun resultRowSaysWhereItCameFromAndToggles() {
        var toggles = 0
        val result = CopyCatalogingResult(
            id = "6eb1848e432e386c", server = "Questa installazione (Pinakes)", title = "Libro Cat090 Prova",
            authors = "Rossi, Mario", year = "2024", existingId = 338681,
        )
        compose.setContent { PinakesTheme { ResultRow(result, selected = false) { toggles++ } } }
        compose.onNodeWithText("Questa installazione (Pinakes)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Rossi, Mario · 2024").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.staff090_catalogue_in_catalogue)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.staff090_catalogue_copy_not_selected)).assertIsDisplayed()
        compose.onNodeWithTag("copy-result-6eb1848e432e386c").performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
    }
}
