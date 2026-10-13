package com.pinakes.app.staff

import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.IsbnPreview
import com.pinakes.app.data.model.StaffAuthorRef
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffLocation
import com.pinakes.app.data.staff.FormField
import com.pinakes.app.data.staff.Identifier
import com.pinakes.app.data.staff.PickedEntity
import com.pinakes.app.data.staff.StaffBookForm
import com.pinakes.app.data.staff.StaffBookWrite
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffBookFormStateTest {

    private val valid = StaffBookForm(title = "Spartito", copies = 1)

    @Test fun titleIsRequired() {
        assertTrue(FormField.TITLE in StaffBookForm().validate())
        assertTrue(FormField.TITLE in StaffBookForm(title = "   ").validate())
        assertTrue(valid.canSave())
    }

    @Test fun copiesMustBeOneToHundredOnCreateOnly() {
        assertTrue(FormField.COPIES in valid.copy(copies = 0).validate())
        assertTrue(FormField.COPIES in valid.copy(copies = 101).validate())
        assertTrue(valid.copy(copies = 100).canSave())
        assertTrue(valid.copy(copies = 0).canSave(isEdit = true))
    }

    @Test fun identifiersAreValidatedButOptional() {
        assertTrue(valid.copy(isbn13 = "978-88-04-66823-7").canSave())
        assertFalse(valid.copy(isbn13 = "9788804668238").canSave())
        assertFalse(valid.copy(isbn10 = "0804429571").canSave())
        assertFalse(valid.copy(ean = "123").canSave())
        assertFalse(valid.copy(issn = "0317-8472").canSave())
        assertEquals(setOf(FormField.ISBN13, FormField.EAN), valid.copy(isbn13 = "1", ean = "2").validate())
    }

    @Test fun numbersAreChecked() {
        assertFalse(valid.copy(year = "19x9").canSave())
        assertTrue(valid.copy(year = "-500").canSave())   // antiquity
        assertFalse(valid.copy(pages = "0").canSave())
        assertFalse(valid.copy(pages = "abc").canSave())
        assertTrue(valid.copy(price = "12,50").canSave())
        assertFalse(valid.copy(price = "12.505").canSave())
    }

    @Test fun createSendsOnlyFilledFields() {
        val body = StaffBookWrite.create(
            valid.copy(
                subtitle = "", isbn13 = "978-88-04-66823-7", year = "2015", pages = "320", copies = 2,
                authors = listOf(PickedEntity(4, "Eco"), PickedEntity(0, "New Author")),
                publishers = listOf(PickedEntity(0, "Bompiani")),
                genre = IdName(3, "Romanzo"), shelfId = 1, levelId = 2,
                coverUrl = "https://covers.example.org/x.jpg", authorBio = "Bio",
            )
        )
        assertEquals("Spartito", body.str("title"))
        assertFalse("subtitle" in body)
        assertEquals("9788804668237", body.str("isbn13"))   // separators stripped
        assertEquals(2015, body.num("year"))
        assertEquals(320, body.num("pages"))
        assertEquals(2, body.num("copies"))
        assertEquals(listOf(4), body.ints("author_ids"))
        assertEquals(listOf("New Author"), body.strs("authors_new"))
        assertFalse("publisher_ids" in body)
        assertEquals(listOf("Bompiani"), body.strs("publishers_new"))
        assertEquals(3, body.num("genre_id"))
        assertEquals(1, body.num("shelf_id"))
        assertEquals(2, body.num("level_id"))
        assertEquals(0, body.num("position"))
        assertEquals("https://covers.example.org/x.jpg", body.str("cover_url"))
        assertEquals("Bio", body["scraped"]!!.jsonObject.str("author_bio"))
    }

    @Test fun createWithoutLevelSendsNoLocation() {
        val body = StaffBookWrite.create(valid.copy(shelfId = 1, levelId = null))
        assertFalse("shelf_id" in body)
        assertFalse("position" in body)
    }

    private val original = StaffBook(
        id = 10, title = "Orlando", subtitle = "A Biography", isbn13 = "9780156701600", year = "1928", pages = "333",
        language = "English", copiesTotal = 2,
        authors = listOf(
            StaffAuthorRef(1, "Virginia Woolf", "principale"),
            StaffAuthorRef(7, "Nadia Fusini", "traduttore"),
        ),
        publishers = listOf(IdName(5, "Hogarth")),
        genre = IdName(3, "Romanzo"),
        location = StaffLocation(shelfId = 1, levelId = 2, position = 4),
    )

    @Test fun formFromRecordKeepsAuthorsButNotContributors() {
        val f = StaffBookForm.from(original)
        assertEquals(listOf(PickedEntity(1, "Virginia Woolf")), f.authors)
        assertEquals(1, f.shelfId)
        assertEquals(2, f.levelId)
    }

    @Test fun unchangedEditSendsAnEmptyPatch() {
        assertEquals(JsonObject(emptyMap()), StaffBookWrite.update(original, StaffBookForm.from(original)))
    }

    @Test fun editSendsOnlyTheChangedFields() {
        val patch = StaffBookWrite.update(original, StaffBookForm.from(original).copy(title = "Orlando (new)", subtitle = ""))
        assertEquals(setOf("title", "subtitle"), patch.keys)
        assertEquals("", patch.str("subtitle"))   // cleared text is sent as ""
    }

    @Test fun clearedNumberIsSentAsNull() {
        val patch = StaffBookWrite.update(original, StaffBookForm.from(original).copy(year = ""))
        assertEquals(JsonNull, patch["year"])
    }

    @Test fun changedAuthorsResendPreservedContributors() {
        val patch = StaffBookWrite.update(
            original,
            StaffBookForm.from(original).copy(authors = listOf(PickedEntity(1, "Virginia Woolf"), PickedEntity(0, "Second"))),
        )
        assertEquals(listOf(1), patch.ints("author_ids"))
        assertEquals(listOf("Second"), patch.strs("authors_new"))
        assertEquals(listOf(7), patch["contributors"]!!.jsonObject.ints("translator_ids"))
        assertFalse("publisher_ids" in patch)
    }

    @Test fun removingLocationSendsNulls() {
        val patch = StaffBookWrite.update(original, StaffBookForm.from(original).copy(shelfId = null, levelId = null))
        assertEquals(JsonNull, patch["shelf_id"])
        assertEquals(JsonNull, patch["level_id"])
    }

    @Test fun previewPrefillUsesTheScannedIdentifier() {
        val f = StaffBookForm.fromPreview(
            IsbnPreview(title = "Il nome della rosa", authors = listOf(JsonPrimitive("Umberto Eco")), publisher = "Bompiani",
                year = "1980", pages = "503", coverUrl = "https://c/x.jpg", isbn13 = "0000000000000"),
            Identifier.Isbn13("9788804668237", "8804668237"),
        )
        assertEquals("9788804668237", f.isbn13)
        assertEquals("8804668237", f.isbn10)
        assertEquals(listOf(PickedEntity(0, "Umberto Eco")), f.authors)
        assertEquals(listOf(PickedEntity(0, "Bompiani")), f.publishers)
        assertEquals("1980", f.year)
        assertTrue(f.canSave())
    }

    @Test fun nonBookEanGoesToTheEanField() {
        val f = StaffBookForm.fromIdentifier(Identifier.Ean("4006381333931"))
        assertEquals("4006381333931", f.ean)
        assertEquals("", f.isbn13)
        assertFalse(f.canSave())   // still needs a title
    }

    @Test fun scraperJunkIsDroppedNotBlockingSave() {
        val f = StaffBookForm.fromPreview(IsbnPreview(title = "X", year = "circa 1900", pages = "xii, 200", price = "EUR 12"), null)
        assertEquals("", f.year)
        assertEquals("", f.pages)
        assertEquals("", f.price)
        assertTrue(f.canSave())
    }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun JsonObject.num(k: String) = this[k]!!.jsonPrimitive.int
    private fun JsonObject.ints(k: String) = (this[k] as JsonArray).map { it.jsonPrimitive.int }
    private fun JsonObject.strs(k: String) = this[k]!!.jsonArray.map { it.jsonPrimitive.content }
}
