package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Staff content management (Mobile API 1.6.0, `/staff/...`). Field names mirror the frozen
// server contract; every field has a default so an older or newer server never breaks
// decoding (the shared Json has ignoreUnknownKeys + coerceInputValues + isLenient, so
// numbers sent as strings and the reverse are both accepted).

/** `{id, name}` pair used for genres, publishers and pickers. */
@Serializable
data class IdName(val id: Int = 0, val name: String = "")

// ---------- Lookups ----------

/** One selectable value of a lookup list (`{value,label}` or `{code,label}`). */
data class LookupOption(val value: String, val label: String)

/**
 * `GET /staff/lookups`. Each list is kept as raw JSON and normalised by [options]: the server
 * sends `{value,label}` objects (languages use `{code,label}`), but a plain string list is
 * accepted too.
 */
@Serializable
data class StaffLookups(
    val languages: List<JsonElement> = emptyList(),
    @SerialName("media_types") val mediaTypes: List<JsonElement> = emptyList(),
    val formats: List<JsonElement> = emptyList(),
    @SerialName("acquisition_types") val acquisitionTypes: List<JsonElement> = emptyList(),
    @SerialName("contributor_roles") val contributorRoles: List<JsonElement> = emptyList(),
    @SerialName("copy_states") val copyStates: List<JsonElement> = emptyList(),
) {
    val languageOptions: List<LookupOption> get() = options(languages)
    val mediaTypeOptions: List<LookupOption> get() = options(mediaTypes)
    val formatOptions: List<LookupOption> get() = options(formats)
    val acquisitionTypeOptions: List<LookupOption> get() = options(acquisitionTypes)
    val copyStateOptions: List<LookupOption> get() = options(copyStates)

    companion object {
        fun options(raw: List<JsonElement>): List<LookupOption> = raw.mapNotNull { it.toLookupOption() }
    }
}

internal fun JsonElement.primitiveText(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonElement.toLookupOption(): LookupOption? = when (this) {
    is JsonPrimitive -> primitiveText()?.let { LookupOption(it, it) }
    is JsonObject -> {
        val value = (this["value"] ?: this["code"] ?: this["id"])?.primitiveText()
        val label = (this["label"] ?: this["name"])?.primitiveText()
        when {
            value != null -> LookupOption(value, label ?: value)
            label != null -> LookupOption(label, label)
            else -> null
        }
    }
    else -> null
}

/** A display label for JSON that may be a string, a `{name}`/`{label}` object or a list of either. */
internal fun JsonElement?.displayText(): String? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> primitiveText()
    is JsonObject -> (this["label"] ?: this["name"] ?: this["title"])?.primitiveText()
    is JsonArray -> mapNotNull { it.displayText() }.joinToString(", ").ifBlank { null }
}

// ---------- ISBN lookup ----------

@Serializable
data class IsbnIdentifier(
    val input: String = "",
    val isbn10: String? = null,
    val isbn13: String? = null,
    val ean: String? = null,
    val kind: String = "isbn", // isbn | ean
)

/** A catalogue record that already carries the scanned identifier (duplicate). */
@Serializable
data class ExistingBook(
    val id: Int = 0,
    val title: String = "",
    val isbn10: String? = null,
    val isbn13: String? = null,
    val ean: String? = null,
    @SerialName("copies_total") val copiesTotal: Int = 0,
    // `location` is a label on the lookup, an object elsewhere: keep it raw.
    val location: JsonElement? = null,
) {
    val locationLabel: String? get() = location.displayText()
}

/** Normalised scraper result (all hooks applied server-side). */
@Serializable
data class IsbnPreview(
    val title: String = "",
    val subtitle: String? = null,
    val authors: List<JsonElement> = emptyList(),
    val publisher: String? = null,
    val year: String? = null,
    @SerialName("pub_date") val pubDate: String? = null,
    val pages: String? = null,
    val language: String? = null,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val ean: String? = null,
    val series: String? = null,
    val edition: String? = null,
    val place: String? = null,
    val keywords: String? = null,
    val price: String? = null,
    val format: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val dewey: String? = null,
    val translator: String? = null,
    val illustrator: String? = null,
    @SerialName("author_bio") val authorBio: String? = null,
    val source: String? = null,
) {
    val authorNames: List<String> get() = authors.mapNotNull { it.displayText() }.distinct()
}

@Serializable
data class IsbnLookup(
    val identifier: IsbnIdentifier = IsbnIdentifier(),
    val found: Boolean = false,
    val existing: ExistingBook? = null,
    val preview: IsbnPreview? = null,
    @SerialName("sources_checked") val sourcesChecked: List<JsonElement> = emptyList(),
)

// ---------- Staff book (edit model) ----------

@Serializable
data class StaffAuthorRef(
    val id: Int = 0,
    val name: String = "",
    // principale | co-autore | traduttore | illustratore | curatore | colorista
    val role: String = "principale",
)

@Serializable
data class StaffLocation(
    @SerialName("shelf_id") val shelfId: Int? = null,
    @SerialName("shelf_code") val shelfCode: String? = null,
    @SerialName("level_id") val levelId: Int? = null,
    @SerialName("level_number") val levelNumber: Int? = null,
    val position: Int? = null,
    val label: String? = null,
)

@Serializable
data class StaffCopy(
    val id: Int = 0,
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    val status: String? = null,
    val notes: String? = null,
)

@Serializable
data class StaffBook(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val ean: String? = null,
    val issn: String? = null,
    val year: String? = null,
    @SerialName("pub_date") val pubDate: String? = null,
    val edition: String? = null,
    val place: String? = null,
    val pages: String? = null,
    val language: String? = null,
    val format: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val description: String? = null,
    val keywords: String? = null,
    val notes: String? = null,
    val series: String? = null,
    @SerialName("series_number") val seriesNumber: String? = null,
    val dewey: String? = null,
    val price: String? = null,
    @SerialName("acquisition_date") val acquisitionDate: String? = null,
    @SerialName("acquisition_type") val acquisitionType: String? = null,
    @SerialName("inventory_base") val inventoryBase: String? = null,
    @SerialName("copies_total") val copiesTotal: Int = 0,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("cover_is_local") val coverIsLocal: Boolean = false,
    val genre: IdName? = null,
    val subgenre: IdName? = null,
    val authors: List<StaffAuthorRef> = emptyList(),
    val publishers: List<IdName> = emptyList(),
    val location: StaffLocation? = null,
    val copies: List<StaffCopy> = emptyList(),
    @SerialName("is_wanted") val isWanted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Row of `GET /staff/books`: the public summary plus operator-only fields. */
@Serializable
data class StaffBookRow(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val isbn13: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("copies_total") val copiesTotal: Int = 0,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    @SerialName("is_wanted") val isWanted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ---------- Copies / cover / delete ----------

@Serializable
data class AddCopiesRequest(
    val quantity: Int,
    val status: String? = null,
    val notes: String? = null,
)

@Serializable
data class CreatedCopy(val id: Int = 0, @SerialName("inventory_number") val inventoryNumber: String? = null)

@Serializable
data class AddCopiesResult(
    @SerialName("copies_total") val copiesTotal: Int = 0,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    val created: List<CreatedCopy> = emptyList(),
)

@Serializable
data class CoverUrlRequest(@SerialName("cover_url") val coverUrl: String)

@Serializable
data class CoverResult(@SerialName("cover_url") val coverUrl: String? = null)

@Serializable
data class DeleteResult(val id: Int = 0, val deleted: Boolean = false)

// ---------- Authors / publishers ----------

/** Search row of `GET /staff/authors`. */
@Serializable
data class StaffAuthorRow(
    val id: Int = 0,
    val name: String = "",
    val pseudonym: String? = null,
    val label: String? = null,
    val born: String? = null,
    val died: String? = null,
) {
    val display: String get() = label?.takeIf { it.isNotBlank() } ?: name
}

@Serializable
data class StaffAuthor(
    val id: Int = 0,
    val name: String = "",
    val pseudonym: String? = null,
    val born: String? = null,
    val died: String? = null,
    val nationality: String? = null,
    val biography: String? = null,
    val website: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("gnd_id") val gndId: String? = null,
)

@Serializable
data class StaffAuthorWrite(
    val name: String,
    val pseudonym: String? = null,
    val born: String? = null,
    val died: String? = null,
    val nationality: String? = null,
    val biography: String? = null,
    val website: String? = null,
    @SerialName("gnd_id") val gndId: String? = null,
)

@Serializable
data class StaffPublisher(
    val id: Int = 0,
    val name: String = "",
    val website: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class StaffPublisherWrite(
    val name: String,
    val website: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

// ---------- Genres ----------

@Serializable
data class StaffGenreNode(
    val id: Int = 0,
    val name: String = "",
    @SerialName("parent_id") val parentId: Int? = null,
    val children: List<StaffGenreNode> = emptyList(),
)

@Serializable
data class StaffGenreWrite(
    val name: String,
    @SerialName("parent_id") val parentId: Int? = null,
)

@Serializable
data class StaffGenreCreated(
    val id: Int = 0,
    val name: String = "",
    @SerialName("parent_id") val parentId: Int? = null,
    val created: Boolean = true,
)

/** A genre flattened for pickers, with its ancestor path for the label. */
data class FlatGenre(val id: Int, val name: String, val path: String, val depth: Int, val parentId: Int?)

fun List<StaffGenreNode>.flatten(): List<FlatGenre> {
    val out = ArrayList<FlatGenre>()
    fun walk(nodes: List<StaffGenreNode>, prefix: String, depth: Int, parent: Int?) {
        for (n in nodes) {
            val path = if (prefix.isEmpty()) n.name else "$prefix › ${n.name}"
            out += FlatGenre(n.id, n.name, path, depth, parent)
            walk(n.children, path, depth + 1, n.id)
        }
    }
    walk(this, "", 0, null)
    return out
}

// ---------- Shelves ----------

@Serializable
data class ShelfLevel(
    val id: Int = 0,
    val number: Int = 0,
    val description: String? = null,
    val order: Int? = null,
)

@Serializable
data class Shelf(
    val id: Int = 0,
    val code: String = "",
    val name: String? = null,
    val order: Int? = null,
    val levels: List<ShelfLevel> = emptyList(),
) {
    val display: String get() = listOfNotNull(code.takeIf { it.isNotBlank() }, name?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

@Serializable
data class ShelfWrite(val code: String, val name: String? = null, val order: Int? = null)

@Serializable
data class ShelfLevelWrite(val number: Int, val description: String? = null, val order: Int? = null)

@Serializable
data class NextPosition(
    val position: Int? = null,
    val label: String? = null,
    @SerialName("level_number") val levelNumber: Int? = null,
    @SerialName("shelf_code") val shelfCode: String? = null,
)
