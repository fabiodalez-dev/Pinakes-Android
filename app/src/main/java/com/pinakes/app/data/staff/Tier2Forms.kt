package com.pinakes.app.data.staff

import com.pinakes.app.data.model.ClubState
import com.pinakes.app.data.model.StaffClub
import com.pinakes.app.data.model.StaffSubscription
import com.pinakes.app.data.model.StaffWork
import com.pinakes.app.data.model.WorkExpression
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private fun text(value: String): JsonPrimitive = JsonPrimitive(value.trim())
private fun intOrNull(value: String) = value.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull

/** Desk rules for a periodical issue status (the server re-checks). */
object IssueStatus {
    val ALL = listOf("posseduto", "mancante", "atteso", "smarrito", "reclamato", "scartato")

    /** Expected or claimed issues can be received. */
    fun receivable(status: String) = status == "atteso" || status == "reclamato"

    /** Expected or missing issues can be claimed from the supplier. */
    fun claimable(status: String) = status == "atteso" || status == "mancante" || status == "reclamato"
}

/** A subscription being edited. Every key is sent so a field can be emptied. Pure. */
data class SubscriptionForm(
    val id: Int = 0,
    val periodicalId: Int = 0,
    val supplier: String = "",
    val cost: String = "",
    val currency: String = "EUR",
    val startDate: String? = null,
    val endDate: String? = null,
    val autoRenew: Boolean = false,
    val active: Boolean = true,
    val notes: String = "",
) {
    val costValid: Boolean get() = cost.isBlank() || cost.trim().replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true
    val valid: Boolean get() = periodicalId > 0 && supplier.isNotBlank() && costValid &&
        (startDate == null || endDate == null || endDate >= startDate)

    fun toJson(): JsonObject = buildJsonObject {
        if (id <= 0) put("periodical_id", JsonPrimitive(periodicalId))
        put("supplier", text(supplier))
        put("cost", cost.trim().replace(',', '.').toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(""))
        put("currency", text(currency))
        put("start_date", JsonPrimitive(startDate.orEmpty()))
        put("end_date", JsonPrimitive(endDate.orEmpty()))
        put("auto_renew", JsonPrimitive(autoRenew))
        put("active", JsonPrimitive(active))
        put("notes", text(notes))
    }

    companion object {
        fun from(s: StaffSubscription) = SubscriptionForm(
            id = s.id, periodicalId = s.periodicalId, supplier = s.supplier,
            cost = s.cost?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
            currency = s.currency.orEmpty(), startDate = s.startDate?.take(10), endDate = s.endDate?.take(10),
            autoRenew = s.autoRenew, active = s.active, notes = s.notes.orEmpty(),
        )
    }
}

/** A book club's settings. Pure. */
data class ClubForm(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val rules: String = "",
    val color: String = "#4f46e5",
    val privacy: String = "public",
    val maxMembers: String = "",
    val active: Boolean = true,
    val moderateProposals: Boolean = false,
) {
    val colorValid: Boolean get() = Regex("^#[0-9a-fA-F]{6}$").matches(color.trim())
    val maxMembersValid: Boolean get() = maxMembers.isBlank() || maxMembers.trim().toIntOrNull()?.let { it > 0 } == true
    val valid: Boolean get() = name.isNotBlank() && name.trim().length <= 190 && colorValid && maxMembersValid && privacy in PRIVACIES

    fun toJson(): JsonObject = buildJsonObject {
        put("name", text(name))
        put("description", text(description))
        put("rules", text(rules))
        put("color", text(color))
        put("privacy", JsonPrimitive(privacy))
        put("max_members", intOrNull(maxMembers))
        put("active", JsonPrimitive(active))
        put("moderate_proposals", JsonPrimitive(moderateProposals))
    }

    companion object {
        val PRIVACIES = listOf("public", "private", "invite", "hidden")
        val ROLES = listOf("owner", "moderator", "member", "guest")
        val MEMBER_STATUSES = listOf("pending", "active", "suspended", "left", "banned")

        fun from(c: StaffClub) = ClubForm(
            id = c.id, name = c.name, description = c.description, rules = c.rules, color = c.color.ifBlank { "#4f46e5" },
            privacy = c.privacy, maxMembers = c.maxMembers?.toString().orEmpty(), active = c.active, moderateProposals = c.moderateProposals,
        )
    }
}

/** One editable workflow state. */
data class WorkflowStateDraft(
    val key: String,
    val label: String,
    val color: String,
    val voting: Boolean = false,
    val current: Boolean = false,
    val archived: Boolean = false,
    /** Every flag the server sent, so a save keeps the ones the editor does not model (e.g. `votable`). */
    val serverFlags: JsonObject = JsonObject(emptyMap()),
)

/** The club workflow editor: at least two states, each with a label; reorder, add, remove. Pure. */
data class WorkflowDraft(val states: List<WorkflowStateDraft>) {
    val valid: Boolean get() = states.size >= 2 && states.all { it.label.isNotBlank() && Regex("^#[0-9a-fA-F]{6}$").matches(it.color.trim()) }

    fun move(index: Int, by: Int): WorkflowDraft {
        val target = index + by
        if (index !in states.indices || target !in states.indices) return this
        val list = states.toMutableList()
        list[index] = states[target].also { list[target] = states[index] }
        return WorkflowDraft(list)
    }

    fun remove(index: Int): WorkflowDraft = if (states.size <= 2 || index !in states.indices) this else WorkflowDraft(states.filterIndexed { i, _ -> i != index })

    fun add(label: String): WorkflowDraft = WorkflowDraft(states + WorkflowStateDraft(key = "", label = label, color = "#6b7280"))

    fun update(index: Int, transform: (WorkflowStateDraft) -> WorkflowStateDraft): WorkflowDraft =
        WorkflowDraft(states.mapIndexed { i, s -> if (i == index) transform(s) else s })

    /**
     * `{states: [{key, label, color, flags}]}`; a new state has an empty key (the server derives it).
     * `flags` is the state's original object with only `voting`, `current` and `archived` overwritten,
     * so renaming a label never strips a flag the app does not edit.
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("states", JsonArray(states.map { s ->
            buildJsonObject {
                put("key", JsonPrimitive(s.key))
                put("label", text(s.label))
                put("color", text(s.color))
                put("flags", buildJsonObject {
                    s.serverFlags.forEach { (name, value) -> put(name, value) }
                    put("voting", JsonPrimitive(s.voting))
                    put("current", JsonPrimitive(s.current))
                    put("archived", JsonPrimitive(s.archived))
                })
            }
        }))
    }

    companion object {
        fun from(states: List<ClubState>) = WorkflowDraft(states.map {
            WorkflowStateDraft(
                it.key, it.label, it.color, it.flag("voting"), it.flag("current"), it.flag("archived"),
                serverFlags = it.flags as? JsonObject ?: JsonObject(emptyMap()),
            )
        })
    }
}

/** An FRBR work (WEMI "work") being edited. Pure. */
data class WorkForm(
    val id: Int = 0,
    val uniformTitle: String = "",
    val originalTitle: String = "",
    val mainAuthorId: Int? = null,
    val mainAuthorName: String = "",
    val createdFrom: String = "",
    val createdTo: String = "",
    val originalLanguage: String = "",
    val viafWorkId: String = "",
    val wikidataId: String = "",
    val notes: String = "",
) {
    private fun year(v: String) = v.isBlank() || v.trim().toIntOrNull() != null
    val valid: Boolean get() = uniformTitle.isNotBlank() && uniformTitle.trim().length <= 255 && year(createdFrom) && year(createdTo)

    fun toJson(): JsonObject = buildJsonObject {
        put("uniform_title", text(uniformTitle))
        put("original_title", text(originalTitle))
        put("main_author_id", mainAuthorId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("created_from", intOrNull(createdFrom))
        put("created_to", intOrNull(createdTo))
        put("original_language", text(originalLanguage))
        put("viaf_work_id", text(viafWorkId))
        put("wikidata_id", text(wikidataId))
        put("notes", text(notes))
    }

    companion object {
        fun from(w: StaffWork) = WorkForm(
            id = w.id, uniformTitle = w.uniformTitle.orEmpty(), originalTitle = w.originalTitle.orEmpty(),
            mainAuthorId = w.mainAuthorId, mainAuthorName = w.mainAuthorName.orEmpty(),
            createdFrom = w.createdFrom?.toString().orEmpty(), createdTo = w.createdTo?.toString().orEmpty(),
            originalLanguage = w.originalLanguage.orEmpty(), viafWorkId = w.viafWorkId.orEmpty(), wikidataId = w.wikidataId.orEmpty(),
            notes = w.notes.orEmpty(),
        )
    }
}

/** An expression of a work. Pure. */
data class ExpressionForm(
    val id: Int = 0,
    val type: String = "testo",
    val language: String = "",
    val title: String = "",
    val year: String = "",
    val notes: String = "",
) {
    val valid: Boolean get() = type in TYPES && (year.isBlank() || year.trim().toIntOrNull() != null)

    fun toJson(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(type))
        put("language", text(language))
        put("title", text(title))
        put("year", intOrNull(year))
        put("notes", text(notes))
    }

    companion object {
        val TYPES = listOf("testo", "traduzione", "revisione", "adattamento", "edizione_critica", "audio", "altro")

        fun from(e: WorkExpression) = ExpressionForm(
            id = e.id, type = e.type ?: "testo", language = e.language.orEmpty(), title = e.title.orEmpty(),
            year = e.year?.toString().orEmpty(), notes = e.notes.orEmpty(),
        )
    }
}
