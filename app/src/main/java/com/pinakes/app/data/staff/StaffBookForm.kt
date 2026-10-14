package com.pinakes.app.data.staff

import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.IsbnPreview
import com.pinakes.app.data.model.StaffBook
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An author or publisher chosen in the form: an existing record (id > 0) or a new name. */
data class PickedEntity(val id: Int, val name: String) {
    val isNew: Boolean get() = id <= 0
}

/** Form fields that can fail validation, so the screen can point at the right one. */
enum class FormField { TITLE, ISBN13, ISBN10, EAN, ISSN, YEAR, PAGES, COPIES, PRICE, COVER_URL, LEVEL }

/** Roles the edit form treats as "authors"; the others are preserved contributors. */
private val AUTHOR_ROLES = setOf("principale", "co-autore", "autore", "")

/**
 * The editable state of the staff book form (create and edit share it). Every text field is
 * a plain string so the screen can bind it directly; [validate] and [StaffBookWrite] turn it
 * into a request. Pure: no Android, no network.
 */
data class StaffBookForm(
    val title: String = "",
    val subtitle: String = "",
    val isbn13: String = "",
    val isbn10: String = "",
    val ean: String = "",
    val issn: String = "",
    val year: String = "",
    val pubDate: String = "",
    val edition: String = "",
    val place: String = "",
    val pages: String = "",
    val language: String = "",
    val format: String = "",
    val mediaType: String = "",
    val description: String = "",
    val keywords: String = "",
    val notes: String = "",
    val series: String = "",
    val seriesNumber: String = "",
    val dewey: String = "",
    val price: String = "",
    val acquisitionDate: String = "",
    val acquisitionType: String = "",
    val inventoryBase: String = "",
    /** Create only: the edit screen adds copies through the copies endpoint. */
    val copies: Int = 1,
    val authors: List<PickedEntity> = emptyList(),
    val publishers: List<PickedEntity> = emptyList(),
    val genre: IdName? = null,
    val subgenre: IdName? = null,
    val shelfId: Int? = null,
    val levelId: Int? = null,
    /** Create only: a remote cover (scraper or typed URL) the server downloads. */
    val coverUrl: String = "",
    /** Create only: author biography from the scraper (`scraped.author_bio`). */
    val authorBio: String = "",
) {
    /** Every field that currently blocks saving. [isEdit] skips create-only checks. */
    fun validate(isEdit: Boolean = false): Set<FormField> = buildSet {
        if (title.isBlank()) add(FormField.TITLE)
        if (!IsbnCodec.isValidIsbn13OrBlank(isbn13)) add(FormField.ISBN13)
        if (!IsbnCodec.isValidIsbn10OrBlank(isbn10)) add(FormField.ISBN10)
        if (!IsbnCodec.isValidEanOrBlank(ean)) add(FormField.EAN)
        if (!IsbnCodec.isValidIssnOrBlank(issn)) add(FormField.ISSN)
        if (year.isNotBlank() && !YEAR.matches(year.trim())) add(FormField.YEAR)
        if (pages.isNotBlank() && (pages.trim().toIntOrNull()?.let { it <= 0 } != false)) add(FormField.PAGES)
        if (price.isNotBlank() && !PRICE.matches(price.trim())) add(FormField.PRICE)
        if (!isEdit && copies !in COPIES_RANGE) add(FormField.COPIES)
        if (!isEdit && coverUrl.isNotBlank() && !isHttpUrl(coverUrl)) add(FormField.COVER_URL)
        // A shelf needs a level: the server places a book on a level, and a half-filled
        // location must never be sent (it would clear the stored one).
        if (shelfId != null && levelId == null) add(FormField.LEVEL)
    }

    fun canSave(isEdit: Boolean = false): Boolean = validate(isEdit).isEmpty()

    companion object {
        val COPIES_RANGE = 1..100
        private val YEAR = Regex("^-?\\d{1,4}$")
        private val PRICE = Regex("^\\d{1,7}([.,]\\d{1,2})?$")

        fun isHttpUrl(value: String): Boolean {
            val v = value.trim().lowercase()
            return (v.startsWith("https://") || v.startsWith("http://")) && v.length > 10 && ' ' !in v
        }

        /** The form for an existing record (edit screen). */
        fun from(book: StaffBook): StaffBookForm = StaffBookForm(
            title = book.title,
            subtitle = book.subtitle.orEmpty(),
            isbn13 = book.isbn13.orEmpty(),
            isbn10 = book.isbn10.orEmpty(),
            ean = book.ean.orEmpty(),
            issn = book.issn.orEmpty(),
            year = book.year.orEmpty(),
            pubDate = book.pubDate.orEmpty(),
            edition = book.edition.orEmpty(),
            place = book.place.orEmpty(),
            pages = book.pages.orEmpty(),
            language = book.language.orEmpty(),
            format = book.format.orEmpty(),
            mediaType = book.mediaType.orEmpty(),
            description = book.description.orEmpty(),
            keywords = book.keywords.orEmpty(),
            notes = book.notes.orEmpty(),
            series = book.series.orEmpty(),
            seriesNumber = book.seriesNumber.orEmpty(),
            dewey = book.dewey.orEmpty(),
            price = book.price.orEmpty(),
            acquisitionDate = book.acquisitionDate.orEmpty(),
            acquisitionType = book.acquisitionType.orEmpty(),
            inventoryBase = book.inventoryBase.orEmpty(),
            copies = book.copiesTotal.coerceAtLeast(1),
            authors = book.authors.filter { it.role.lowercase() in AUTHOR_ROLES }.map { PickedEntity(it.id, it.name) },
            publishers = book.publishers.map { PickedEntity(it.id, it.name) },
            genre = book.genre?.takeIf { it.id > 0 },
            subgenre = book.subgenre?.takeIf { it.id > 0 },
            // A location without a level cannot be edited as such: treat it as "no change"
            // (the screen still shows the stored label) instead of an invalid form.
            shelfId = book.location?.takeIf { it.levelId != null }?.shelfId,
            levelId = book.location?.takeIf { it.shelfId != null }?.levelId,
        )

        /**
         * A new record prefilled from the scraper. A non-book EAN goes into [ean]; a
         * Bookland code into the ISBN fields. Scraped authors and publisher are sent as new
         * names: the server matches them to existing records (find-or-create), like the web form.
         */
        fun fromPreview(preview: IsbnPreview?, identifier: Identifier?): StaffBookForm {
            val p = preview
            val isbn13 = (identifier as? Identifier.Isbn13)?.isbn13 ?: p?.isbn13.orEmpty()
            val isbn10 = (identifier as? Identifier.Isbn13)?.isbn10 ?: p?.isbn10.orEmpty()
            val ean = (identifier as? Identifier.Ean)?.ean ?: p?.ean.orEmpty()
            return StaffBookForm(
                title = p?.title.orEmpty(),
                subtitle = p?.subtitle.orEmpty(),
                isbn13 = isbn13,
                isbn10 = isbn10,
                ean = ean,
                year = p?.year?.takeIf { YEAR.matches(it.trim()) }.orEmpty(),
                pubDate = p?.pubDate.orEmpty(),
                edition = p?.edition.orEmpty(),
                place = p?.place.orEmpty(),
                pages = p?.pages?.trim()?.takeIf { it.toIntOrNull()?.let { n -> n > 0 } == true }.orEmpty(),
                language = p?.language.orEmpty(),
                format = p?.format.orEmpty(),
                mediaType = p?.mediaType.orEmpty(),
                description = p?.description.orEmpty(),
                keywords = p?.keywords.orEmpty(),
                series = p?.series.orEmpty(),
                dewey = p?.dewey.orEmpty(),
                price = p?.price?.trim()?.takeIf { PRICE.matches(it) }.orEmpty(),
                authors = p?.authorNames.orEmpty().map { PickedEntity(0, it) },
                publishers = listOfNotNull(p?.publisher?.takeIf { it.isNotBlank() }?.let { PickedEntity(0, it) }),
                coverUrl = p?.coverUrl?.takeIf { isHttpUrl(it) }.orEmpty(),
                authorBio = p?.authorBio.orEmpty(),
            )
        }

        /** A new manual record seeded with a typed or scanned identifier only. */
        fun fromIdentifier(identifier: Identifier?): StaffBookForm = fromPreview(null, identifier)
    }
}

/**
 * Builds the JSON bodies of `POST /staff/books` and `PUT /staff/books/{id}` (the
 * `StaffBookWrite` contract, English keys; the server maps them to the admin form).
 *
 * - [create] sends every non-blank field plus copies, people, classification, location and
 *   the remote cover.
 * - [update] is a true patch: only fields that differ from [StaffBookForm.from] of the
 *   original record are sent. A cleared text field is sent as `""`, a cleared number as
 *   `null`; the server merges the patch over the current record, so nothing else moves.
 *   A location is sent whole (shelf + level + next position) or, when the user cleared the
 *   shelf, as explicit nulls; a shelf without a level is never sent.
 *   When the authors change, the original translators/illustrators/editors/colorists are
 *   re-sent in `contributors` so the rebuild of the author links keeps them.
 */
object StaffBookWrite {

    fun create(form: StaffBookForm): JsonObject = buildJsonObject {
        textFields(form).forEach { (key, value) -> if (value.isNotBlank()) put(key, value.trim()) }
        numberField(form.year)?.let { put("year", it) }
        numberField(form.pages)?.let { put("pages", it) }
        put("copies", form.copies.coerceIn(StaffBookForm.COPIES_RANGE))
        putPeople("author_ids", "authors_new", form.authors)
        putPeople("publisher_ids", "publishers_new", form.publishers)
        form.genre?.let { put("genre_id", it.id) }
        form.subgenre?.let { put("subgenre_id", it.id) }
        if (form.shelfId != null && form.levelId != null) {
            put("shelf_id", form.shelfId)
            put("level_id", form.levelId)
            put("position", 0) // next free position, computed server-side
        }
        if (StaffBookForm.isHttpUrl(form.coverUrl)) put("cover_url", form.coverUrl.trim())
        if (form.authorBio.isNotBlank()) put("scraped", buildJsonObject { put("author_bio", form.authorBio.trim()) })
    }

    fun update(original: StaffBook, form: StaffBookForm): JsonObject {
        val before = StaffBookForm.from(original)
        return buildJsonObject {
            val old = textFields(before).toMap()
            textFields(form).forEach { (key, value) ->
                if (value.trim() != old[key].orEmpty().trim()) put(key, value.trim())
            }
            if (form.year.trim() != before.year.trim()) put("year", numberField(form.year) ?: JsonNull)
            if (form.pages.trim() != before.pages.trim()) put("pages", numberField(form.pages) ?: JsonNull)
            if (form.authors != before.authors) {
                putPeople("author_ids", "authors_new", form.authors, alwaysBoth = true)
                put("contributors", preservedContributors(original))
            }
            if (form.publishers != before.publishers) {
                putPeople("publisher_ids", "publishers_new", form.publishers, alwaysBoth = true)
            }
            if (form.genre?.id != before.genre?.id) put("genre_id", form.genre?.id?.let { JsonPrimitive(it) } ?: JsonNull)
            if (form.subgenre?.id != before.subgenre?.id) put("subgenre_id", form.subgenre?.id?.let { JsonPrimitive(it) } ?: JsonNull)
            if (form.shelfId != before.shelfId || form.levelId != before.levelId) {
                when {
                    form.shelfId != null && form.levelId != null -> {
                        put("shelf_id", form.shelfId)
                        put("level_id", form.levelId)
                        put("position", 0)
                    }
                    // Only an explicit clear (shelf set to none) removes the location.
                    form.shelfId == null && form.levelId == null -> {
                        put("shelf_id", JsonNull)
                        put("level_id", JsonNull)
                    }
                    // Shelf without level: blocked by validate(); never send a partial location.
                    else -> Unit
                }
            }
        }
    }

    private fun textFields(f: StaffBookForm): List<Pair<String, String>> = listOf(
        "title" to f.title,
        "subtitle" to f.subtitle,
        "isbn13" to IsbnCodec.normalize(f.isbn13),
        "isbn10" to IsbnCodec.normalize(f.isbn10),
        "ean" to IsbnCodec.normalize(f.ean),
        "issn" to f.issn.trim().uppercase(),
        "pub_date" to f.pubDate,
        "edition" to f.edition,
        "place" to f.place,
        "language" to f.language,
        "format" to f.format,
        "media_type" to f.mediaType,
        "description" to f.description,
        "keywords" to f.keywords,
        "notes" to f.notes,
        "series" to f.series,
        "series_number" to f.seriesNumber,
        "dewey" to f.dewey,
        "price" to f.price.replace(',', '.'),
        "acquisition_date" to f.acquisitionDate,
        "acquisition_type" to f.acquisitionType,
        "inventory_base" to f.inventoryBase,
    )

    private fun numberField(raw: String): JsonPrimitive? =
        raw.trim().toIntOrNull()?.let { JsonPrimitive(it) }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putPeople(
        idsKey: String,
        newKey: String,
        people: List<PickedEntity>,
        alwaysBoth: Boolean = false,
    ) {
        val ids = people.filter { !it.isNew }.map { it.id }.distinct()
        val names = people.filter { it.isNew }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        if (alwaysBoth || ids.isNotEmpty()) put(idsKey, JsonArray(ids.map { JsonPrimitive(it) }))
        if (alwaysBoth || names.isNotEmpty()) put(newKey, JsonArray(names.map { JsonPrimitive(it) }))
    }

    private fun preservedContributors(original: StaffBook): JsonObject {
        fun ids(vararg roles: String) = buildJsonArray {
            original.authors.filter { it.role.lowercase() in roles && it.id > 0 }.map { it.id }.distinct().forEach { add(JsonPrimitive(it)) }
        }
        return buildJsonObject {
            put("translator_ids", ids("traduttore"))
            put("illustrator_ids", ids("illustratore"))
            put("editor_ids", ids("curatore"))
            put("colorist_ids", ids("colorista"))
        }
    }
}
