package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tier 2 bridges: periodicals desk, book club management and FRBR works, each behind its own flag. */
class Tier2FlowTest : AppFlowTest() {

    private fun tier2(configure: FakePinakes.() -> Unit) = launch(health = "health_staff_tier2", configure = configure)

    @Test fun withoutTheirFlagsTheTier2RowsAreHidden() {
        launch()
        openStaffHub()
        waitText(text(R.string.staff_books))
        assertFalse(exists(text(R.string.staff_periodicals)))
        assertFalse(exists(text(R.string.staff_clubs)))
        assertFalse(exists(text(R.string.staff_works)))
    }

    @Test fun aTypedIssueCodeIsReceived() {
        tier2 {
            json("GET periodicals", "periodicals_list")
            json("GET periodicals/staff/scan-lookup", "scan_issue")
            json("POST periodicals/staff/issues/4/receive", "issue_4_received")
        }
        openStaffHub()
        click(text(R.string.staff_periodicals))
        denyCameraPromptIfShown()
        typeInto(text(R.string.staff_periodicals_code), "977112228320612")
        clickDescription(text(R.string.staff_scan_lookup))
        waitRequest("GET", "periodicals/staff/scan-lookup")
        assertEquals("977112228320612", api.recorded("GET", "periodicals/staff/scan-lookup").last().requestUrl?.queryParameter("code"))
        clickTag("issue-receive")
        waitRequest("POST", "periodicals/staff/issues/4/receive")
        waitText(text(R.string.staff_issue_received, "12", "Internazionale"))
    }

    @Test fun aMastheadShowsItsIssuesAndTakesANewYear() {
        tier2 {
            json("GET periodicals", "periodicals_list")
            json("GET periodicals/16", "periodical_16")
            json("GET periodicals/years/2/issues", "year_2_issues")
            json("GET periodicals/staff/subscriptions", "subscriptions_empty")
            json("POST periodicals/staff/16/years", "year_created", status = 201)
        }
        openStaffHub()
        click(text(R.string.staff_periodicals))
        denyCameraPromptIfShown()
        scrollListTo("Internazionale")
        click("Internazionale")
        clickTag("year-2")
        waitText(text(R.string.staff_issue_number, "12"))
        assertTrue(exists(text(R.string.staff_issue_receive)))
        clickDescription(text(R.string.staff_year_add))
        compose.onAllNodes(hasSetTextAction() and hasTestTag("year-input")).onFirst().performTextReplacement("2027")
        typeInto(text(R.string.staff_year_volume), "34")
        clickTag("form-save")
        val body = lastBody("POST", "periodicals/staff/16/years")
        assertTrue(body, body.contains("\"year\":2027") && body.contains("\"volume\":\"34\""))
        waitText(text(R.string.staff_year_added))
    }

    @Test fun createAClubThenChangeAMemberRole() {
        tier2 {
            json("GET bookclub/staff/clubs", "clubs_list")
            json("POST bookclub/staff/clubs", "club_1132", status = 201)
            json("GET bookclub/staff/clubs/1132", "club_1132")
            json("PUT bookclub/staff/clubs/1132/members/5", "club_member_5_moderator")
        }
        openStaffHub()
        click(text(R.string.staff_clubs))
        clickDescription(text(R.string.staff_club_new))
        compose.onAllNodes(hasSetTextAction() and hasTestTag("club-name")).onFirst().performTextReplacement("Lettori del giovedì")
        clickTag("form-save")
        val body = lastBody("POST", "bookclub/staff/clubs")
        assertTrue(body, body.contains("\"name\":\"Lettori del giovedì\"") && body.contains("\"privacy\":\"public\""))
        waitText("Ada Rossi")
        click("Ada Rossi")
        clickTag("role-moderator")
        assertEquals("""{"role":"moderator"}""", lastBody("PUT", "bookclub/staff/clubs/1132/members/5"))
        waitText(text(R.string.staff_club_member_saved))
    }

    @Test fun editTheClubWorkflow() {
        tier2 {
            json("GET bookclub/staff/clubs", "clubs_list")
            json("GET bookclub/staff/clubs/1132", "club_1132")
            json("PUT bookclub/staff/clubs/1132/workflow", "club_1132")
        }
        openStaffHub()
        click(text(R.string.staff_clubs))
        click("Lettori del giovedì")
        click(text(R.string.staff_club_workflow_edit))
        click(text(R.string.staff_club_state_add))
        clickTag("form-save")
        val body = lastBody("PUT", "bookclub/staff/clubs/1132/workflow")
        assertEquals(4, Regex("\"label\"").findAll(body).count())
        assertTrue(body, body.contains("\"voting\":true"))
        waitText(text(R.string.staff_club_workflow_saved))
    }

    @Test fun createAWorkAddAnExpressionAndLinkABook() {
        tier2 {
            json("GET staff/works", "works_list")
            json("POST staff/works", "work_2", status = 201)
            json("GET staff/works/2", "work_2")
            json("POST staff/works/2/expressions", "expression_created", status = 201)
            json("POST staff/books/5/work", "book_work_link")
        }
        openStaffHub()
        click(text(R.string.staff_works))
        clickDescription(text(R.string.staff_work_new))
        compose.onAllNodes(hasSetTextAction() and hasTestTag("work-title")).onFirst().performTextReplacement("Odissea")
        clickTag("form-save")
        assertTrue(lastBody("POST", "staff/works").contains("\"uniform_title\":\"Odissea\""))
        waitText(text(R.string.staff_work_expressions).uppercase())
        click(text(R.string.staff_expression_add))
        compose.onAllNodes(hasSetTextAction() and hasTestTag("expression-title")).onFirst().performTextReplacement("Odissea (trad. Calzecchi Onesti)")
        clickTag("form-save")
        val expr = lastBody("POST", "staff/works/2/expressions")
        assertTrue(expr, expr.contains("\"type\":\"testo\"") && expr.contains("Calzecchi"))
        click(text(R.string.staff_work_attach))
        typeIntoTag("picker-search", "Orl")
        clickTag("pick-5")
        assertEquals("""{"work_id":2}""", lastBody("POST", "staff/books/5/work"))
        waitText(text(R.string.staff_work_book_attached))
    }
}
