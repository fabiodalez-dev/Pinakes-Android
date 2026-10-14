package com.pinakes.app.staff

import com.pinakes.app.data.model.ClubState
import com.pinakes.app.data.model.PeriodicalScan
import com.pinakes.app.data.model.StaffClub
import com.pinakes.app.data.model.StaffWork
import com.pinakes.app.data.staff.ClubForm
import com.pinakes.app.data.staff.ExpressionForm
import com.pinakes.app.data.staff.IssueStatus
import com.pinakes.app.data.staff.StaffAreas
import com.pinakes.app.data.staff.SubscriptionForm
import com.pinakes.app.data.staff.WorkForm
import com.pinakes.app.data.staff.WorkflowDraft
import com.pinakes.app.data.store.InstanceFeatures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tier 2 bridges: tolerant parsing of the real shapes, and the form bodies sent back. */
class Tier2FormsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun scanLookupShapes() {
        val issue = json.decodeFromString<PeriodicalScan>(
            """{"found":true,"match":"issue","action":"receive","issue":{"id":4,"numero":"12","stato":"atteso","annata_id":2,"anno":2026,"testata_id":16,"titolo":"Internazionale","url":"x"},"message":"m"}"""
        )
        assertEquals("12", issue.issue?.number)
        assertEquals(16, issue.issue?.periodicalId)
        val title = json.decodeFromString<PeriodicalScan>("""{"found":true,"match":"title","action":null,"title":{"id":16,"titolo":"Internazionale"}}""")
        assertEquals("Internazionale", title.title?.title)
        val none = json.decodeFromString<PeriodicalScan>("""{"found":false,"match":"none","action":null,"message":"Nessuna corrispondenza"}""")
        assertFalse(none.found)
    }

    @Test fun issueStatusRules() {
        assertTrue(IssueStatus.receivable("atteso") && IssueStatus.receivable("reclamato"))
        assertFalse(IssueStatus.receivable("posseduto"))
        assertTrue(IssueStatus.claimable("mancante"))
        assertFalse(IssueStatus.claimable("posseduto") || IssueStatus.claimable("scartato"))
    }

    @Test fun subscriptionBody() {
        val form = SubscriptionForm(periodicalId = 16, supplier = " Edicola ", cost = "12,50", startDate = "2026-01-01", endDate = "2026-12-31")
        assertTrue(form.valid)
        val body = form.toJson()
        assertEquals(16, body["periodical_id"]!!.jsonPrimitive.int)
        assertEquals("Edicola", body["supplier"]!!.jsonPrimitive.content)
        assertEquals(12.5, body["cost"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertFalse(SubscriptionForm(id = 3, periodicalId = 16, supplier = "x").toJson().containsKey("periodical_id"))
        assertFalse(form.copy(endDate = "2025-01-01").valid)
        assertFalse(form.copy(cost = "abc").valid)
    }

    @Test fun clubWorkflowParsesEmptyFlagsAndRoundTrips() {
        val club = json.decodeFromString<StaffClub>(
            """{"id":1,"name":"C","privacy":"public","workflow":[{"key":"proposed","label":"Proposto","color":"#6b7280","flags":{"votable":true}},
               {"key":"voting","label":"In votazione","color":"#8b5cf6","flags":{"voting":true}},{"key":"selected","label":"Scelto","color":"#0ea5e9","flags":[]}],
               "members":[{"id":5,"user_id":3,"name":"Ada","email":"a@x","role":"member","role_name":"Membro","status":"active"}]}"""
        )
        assertTrue(club.workflow[1].flag("voting"))
        assertFalse(club.workflow[2].flag("voting"))
        val draft = WorkflowDraft.from(club.workflow)
        val moved = draft.move(2, -1)
        assertEquals(listOf("proposed", "selected", "voting"), moved.states.map { it.key })
        assertEquals(draft, draft.move(0, -1))
        assertEquals(2, draft.remove(0).states.size)
        assertEquals(2, WorkflowDraft(draft.states.take(2)).remove(0).states.size)
        val states = draft.add("Nuovo").toJson()["states"]!!.jsonArray
        assertEquals(4, states.size)
        assertEquals("", states[3].jsonObject["key"]!!.jsonPrimitive.content)
        assertTrue(states[1].jsonObject["flags"]!!.jsonObject["voting"]!!.jsonPrimitive.boolean)
        assertFalse(WorkflowDraft(draft.states.take(1)).valid)
    }

    @Test fun clubFormValidation() {
        assertTrue(ClubForm(name = "Lettori").valid)
        assertFalse(ClubForm(name = "Lettori", color = "red").valid)
        assertFalse(ClubForm(name = "Lettori", maxMembers = "0").valid)
        assertEquals(JsonNull, ClubForm(name = "L").toJson()["max_members"])
        assertEquals(12, ClubForm(name = "L", maxMembers = "12").toJson()["max_members"]!!.jsonPrimitive.int)
    }

    @Test fun workAndExpressionBodies() {
        val w = json.decodeFromString<StaffWork>("""{"id":2,"uniform_title":"Odissea","main_author_id":7,"created_from":-725,"expressions":[{"id":3,"work_id":2,"type":"traduzione","language":"ita","year":1963}]}""")
        val form = WorkForm.from(w)
        assertEquals("-725", form.createdFrom)
        assertTrue(form.valid)
        val body = form.copy(createdTo = "").toJson()
        assertEquals(-725, body["created_from"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, body["created_to"])
        assertFalse(WorkForm().valid)
        val e = ExpressionForm.from(w.expressions.single())
        assertEquals("traduzione", e.type)
        assertEquals(1963, e.toJson()["year"]!!.jsonPrimitive.int)
        assertFalse(e.copy(type = "film").valid)
    }

    @Test fun tier2AreasNeedTheirFlags() {
        val f = InstanceFeatures(staffCatalogAvailable = true, staffPeriodicalsAvailable = true, staffBookClubAvailable = true, staffFrbrAvailable = true)
        val a = StaffAreas.of("staff", f, online = true)
        assertTrue(a.periodicals && a.bookClub && a.works && a.anyPlugin)
        val none = StaffAreas.of("staff", InstanceFeatures(staffCatalogAvailable = true), online = true)
        assertFalse(none.periodicals || none.bookClub || none.works || none.anyPlugin)
    }
}
