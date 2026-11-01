package com.pinakes.app.data.staff

import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.Supplier
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// Pure rules of the 0.9.0 cataloguing screens (suppliers, book supplier/RDA fields, MARC import,
// copy cataloguing). No Android, no network: unit-tested in server090.

// =============================================================================================
// Suppliers
// =============================================================================================

/** The supplier form's fields, with the column limits the server enforces (422 past them). */
enum class SupplierField(val key: String, val maxLength: Int) {
    NAME("name", 255),
    CONTACT_PERSON("contact_person", 150),
    EMAIL("email", 255),
    PHONE("phone", 30),
    WEBSITE("website", 255),
    ADDRESS("address", 2000),
    VAT_NUMBER("vat_number", 20),
    NOTES("notes", 5000),
}

/** What a field fails on before the request is sent; the server's own text wins after it. */
enum class SupplierProblem { REQUIRED, INVALID }

/** The create/edit form of a supplier. Every value is the text as typed. */
data class SupplierForm(
    val name: String = "",
    val contactPerson: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
    val address: String = "",
    val vatNumber: String = "",
    val notes: String = "",
) {
    fun value(field: SupplierField): String = when (field) {
        SupplierField.NAME -> name
        SupplierField.CONTACT_PERSON -> contactPerson
        SupplierField.EMAIL -> email
        SupplierField.PHONE -> phone
        SupplierField.WEBSITE -> website
        SupplierField.ADDRESS -> address
        SupplierField.VAT_NUMBER -> vatNumber
        SupplierField.NOTES -> notes
    }

    fun with(field: SupplierField, text: String): SupplierForm {
        val v = text.take(field.maxLength)
        return when (field) {
            SupplierField.NAME -> copy(name = v)
            SupplierField.CONTACT_PERSON -> copy(contactPerson = v)
            SupplierField.EMAIL -> copy(email = v)
            SupplierField.PHONE -> copy(phone = v)
            SupplierField.WEBSITE -> copy(website = v)
            SupplierField.ADDRESS -> copy(address = v)
            SupplierField.VAT_NUMBER -> copy(vatNumber = v)
            SupplierField.NOTES -> copy(notes = v)
        }
    }

    /**
     * The checks the app can make on its own: a name, an e-mail that looks like one and a
     * website without spaces (the server adds `https://` to a bare domain and checks the rest).
     */
    fun validate(): Map<SupplierField, SupplierProblem> = buildMap {
        if (name.isBlank()) put(SupplierField.NAME, SupplierProblem.REQUIRED)
        if (email.isNotBlank() && !EMAIL.matches(email.trim())) put(SupplierField.EMAIL, SupplierProblem.INVALID)
        if (website.isNotBlank() && website.trim().contains(' ')) put(SupplierField.WEBSITE, SupplierProblem.INVALID)
        SupplierField.entries.forEach { f -> if (value(f).trim().length > f.maxLength) put(f, SupplierProblem.INVALID) }
    }

    val valid: Boolean get() = validate().isEmpty()

    /** `POST /staff/suppliers`: the name and every other non-blank field, trimmed. */
    fun createJson(): JsonObject = buildJsonObject {
        SupplierField.entries.forEach { f ->
            val v = value(f).trim()
            if (f == SupplierField.NAME || v.isNotEmpty()) put(f.key, JsonPrimitive(v))
        }
    }

    /**
     * `PUT /staff/suppliers/{id}`: only the fields that changed from [original]. A cleared field
     * goes as JSON null (an absent key keeps the stored value).
     */
    fun patchJson(original: Supplier): JsonObject {
        val before = from(original)
        return buildJsonObject {
            SupplierField.entries.forEach { f ->
                val v = value(f).trim()
                if (v != before.value(f).trim()) put(f.key, if (v.isEmpty() && f != SupplierField.NAME) JsonNull else JsonPrimitive(v))
            }
        }
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun from(s: Supplier) = SupplierForm(
            name = s.name, contactPerson = s.contactPerson.orEmpty(), email = s.email.orEmpty(), phone = s.phone.orEmpty(),
            website = s.website.orEmpty(), address = s.address.orEmpty(), vatNumber = s.vatNumber.orEmpty(), notes = s.notes.orEmpty(),
        )

        /** A server field key of `meta.fields` → our field (null for an unknown key). */
        fun fieldOf(key: String): SupplierField? = SupplierField.entries.firstOrNull { it.key == key }
    }
}

/** Which supplier controls the screen shows: the server's `can_*` answers, never a guess. */
data class SupplierControls(val create: Boolean, val edit: Boolean, val delete: Boolean) {
    companion object {
        fun of(canCreate: Boolean, supplier: Supplier?): SupplierControls =
            SupplierControls(create = canCreate, edit = supplier?.canEdit == true, delete = supplier?.canDelete == true)
    }
}

// =============================================================================================
// Book form: supplier and RDA types
// =============================================================================================

/** The 0.9.0 cataloguing values of a book. Null supplier = none; null RDA type = derived. */
data class BookCataloguing(
    val supplierId: Int? = null,
    val rdaContentType: String? = null,
    val rdaMediaType: String? = null,
    val rdaCarrierType: String? = null,
) {
    companion object {
        fun from(book: StaffBook) = BookCataloguing(
            supplierId = book.supplierId?.takeIf { it > 0 },
            rdaContentType = book.rdaContentType?.takeIf { it.isNotBlank() },
            rdaMediaType = book.rdaMediaType?.takeIf { it.isNotBlank() },
            rdaCarrierType = book.rdaCarrierType?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Which 0.9.0 modules the book form edits. The server's lookups carry the lists only while
 * the module is on, so their presence is the switch: off, the keys are never sent.
 */
data class CataloguingModules(val suppliers: Boolean = false, val rda: Boolean = false) {
    companion object {
        val Off = CataloguingModules()

        fun from(lookups: StaffLookups) = CataloguingModules(
            suppliers = lookups.suppliers != null,
            rda = lookups.rdaContentTypes != null && lookups.rdaMediaTypes != null && lookups.rdaCarrierTypes != null,
        )
    }
}

/** Adds the supplier and RDA keys to the `StaffBookWrite` bodies. */
object BookCataloguingWrite {

    /** A create sends what is set: a supplier, and each RDA override that is not "derived". */
    fun putCreate(builder: JsonObjectBuilder, values: BookCataloguing, modules: CataloguingModules) {
        if (modules.suppliers) values.supplierId?.let { builder.put("supplier_id", JsonPrimitive(it)) }
        if (modules.rda) {
            values.rdaContentType?.let { builder.put("rda_content_type", JsonPrimitive(it)) }
            values.rdaMediaType?.let { builder.put("rda_media_type", JsonPrimitive(it)) }
            values.rdaCarrierType?.let { builder.put("rda_carrier_type", JsonPrimitive(it)) }
        }
    }

    /** An update sends what changed; back to none/derived goes as JSON null (clears it). */
    fun putUpdate(builder: JsonObjectBuilder, before: BookCataloguing, after: BookCataloguing, modules: CataloguingModules) {
        fun text(v: String?) = v?.let { JsonPrimitive(it) } ?: JsonNull
        if (modules.suppliers && after.supplierId != before.supplierId) {
            builder.put("supplier_id", after.supplierId?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        if (modules.rda) {
            if (after.rdaContentType != before.rdaContentType) builder.put("rda_content_type", text(after.rdaContentType))
            if (after.rdaMediaType != before.rdaMediaType) builder.put("rda_media_type", text(after.rdaMediaType))
            if (after.rdaCarrierType != before.rdaCarrierType) builder.put("rda_carrier_type", text(after.rdaCarrierType))
        }
    }
}

// =============================================================================================
// MARC import
// =============================================================================================

object MarcImportRules {
    /** The server's accepted extensions, used when /options sends none. */
    val DEFAULT_EXTENSIONS = listOf("mrc", "marc", "iso", "dat", "xml", "marcxml")

    /** The document picker cannot filter by extension: MARC has no registered mime type. */
    val PICKER_MIME_TYPES = arrayOf("*/*")

    fun acceptsFileName(name: String, extensions: List<String>): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext in extensions.ifEmpty { DEFAULT_EXTENSIONS }.map { it.lowercase() }
    }

    /** The encoding only applies to ISO 2709: a MARCXML file declares its own. */
    fun encodingApplies(fileName: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: return true
        return ext != "xml" && ext != "marcxml"
    }
}

// =============================================================================================
// Copy cataloguing
// =============================================================================================

/** One import request, compared by value so a retry of the same selection keeps its key. */
data class CopyImportSubmission(val searchId: String, val ids: List<String>, val duplicates: String, val covers: Boolean)

object CopyCatalogingRules {
    const val MAX_TERM = 200
    val MAX_RANGE = 1..25
    const val DEFAULT_MAX = 10
    const val DEFAULT_IMPORT_LIMIT = 100

    fun canSearch(servers: Set<String>, term: String, max: Int): Boolean =
        servers.isNotEmpty() && term.isNotBlank() && term.trim().length <= MAX_TERM && max in MAX_RANGE

    /** Toggle [id]; a new pick past [limit] is refused (the server imports 100 at a time). */
    fun toggle(selected: Set<String>, id: String, limit: Int = DEFAULT_IMPORT_LIMIT): Set<String> = when {
        id in selected -> selected - id
        selected.size >= limit -> selected
        else -> selected + id
    }

    /**
     * A scanned EAN-13 as a search: a Bookland code (978/979) by ISBN, an ISSN barcode (977) by
     * the ISSN it carries, anything else on every field. Null when the code is not an EAN-13.
     */
    fun fromScan(raw: String): Pair<String, String>? {
        val code = raw.filter(Char::isDigit)
        if (code.length != 13 || !IsbnCodec.checkEan13(code)) return null
        return when {
            code.startsWith("978") || code.startsWith("979") -> "isbn" to code
            code.startsWith("977") -> "issn" to issnOf(code.substring(3, 10))
            else -> "any" to code
        }
    }

    /** The ISSN of its seven digits: mod-11 check character, `X` for 10. */
    fun issnOf(seven: String): String {
        val sum = seven.mapIndexed { i, c -> (c - '0') * (8 - i) }.sum()
        val check = (11 - sum % 11) % 11
        return "${seven.take(4)}-${seven.drop(4)}${if (check == 10) "X" else check.toString()}"
    }
}
