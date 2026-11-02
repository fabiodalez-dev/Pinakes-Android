package com.pinakes.app.server090

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertTextContains
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.R
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090Statistics
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeScanResult
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.ui.screens.staff.s090.stocktake.CloseDialog
import com.pinakes.app.ui.screens.staff.s090.stocktake.LastResultCard
import com.pinakes.app.ui.screens.staff.s090.stocktake.StatisticsBody
import com.pinakes.app.ui.screens.staff.s090.stocktake.StocktakeCounters
import com.pinakes.app.ui.theme.PinakesTheme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

/**
 * What the stocktake and statistics screens show, rendered from bodies captured from a 0.9.0
 * server: the close choices per role, the scan answer in its colour, the counters, and the
 * statistics sections that follow their module switches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Staff090StocktakeUiTest {
    // Same RuleChain as ConsultationOnlyButtonTest: registers the host activity.
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun <T> data(name: String, serializer: KSerializer<T>): T =
        json.decodeFromString(Envelope.serializer(serializer), javaClass.getResourceAsStream("/server090/stocktake/$name.json")!!.bufferedReader().readText()).data!!

    @Test fun staffClosesLeavingTheMissingCopiesAndIsNotOfferedLost() {
        val session = data("s090_stocktake_session_detail", Staff090StocktakeSession.serializer())
        compose.setContent { PinakesTheme { CloseDialog(session, "none", false, {}, {}, {}) } }
        assertEquals(0, compose.onAllNodesWithTag("close-lost").fetchSemanticsNodes().size)
        compose.onNodeWithTag("close-confirm-text").assertTextContains(context.getString(R.string.staff090_stocktake_close_confirm_none, session.summary.missing))
    }

    @Test fun anAdministratorChoosesLostAndTheConfirmationSaysHowMany() {
        val session = data("s090_stocktake_session_detail_mine", Staff090StocktakeSession.serializer())
        var picked: String? = null
        compose.setContent { PinakesTheme { CloseDialog(session, "lost", false, { picked = it }, {}, {}) } }
        compose.onNodeWithTag("close-none").assertIsDisplayed()
        compose.onNodeWithTag("close-lost").assertIsDisplayed()
        compose.onNodeWithTag("close-confirm-text").assertTextContains(context.getString(R.string.staff090_stocktake_close_confirm_lost, session.summary.missing))
        compose.onNodeWithTag("close-none").performClick()
        assertEquals("none", picked)
    }

    @Test fun theScanAnswerShowsTheOutcomeAndWhereTheCopyBelongs() {
        val options = data("s090_stocktake_options_admin", Staff090StocktakeOptions.serializer())
        val misplaced = data("s090_stocktake_scan_misplaced", Staff090StocktakeScanResult.serializer()).scan
        var opened: Int? = null
        compose.setContent { PinakesTheme { LastResultCard(misplaced, 0, options) { opened = it } } }
        compose.onNodeWithText(misplaced.outcomeLabel).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.staff090_stocktake_belongs_on, misplaced.expectedShelf!!)).assertIsDisplayed()
        compose.onNodeWithTag("stocktake-last-result").performClick()
        assertEquals(misplaced.bookId, opened)
    }

    @Test fun theCountersShowTheLiveSummary() {
        val result = data("s090_stocktake_scan_duplicate", Staff090StocktakeScanResult.serializer())
        compose.setContent { PinakesTheme { StocktakeCounters(result.summary, null) } }
        compose.onNodeWithTag("count-duplicates").assertTextContains("1", substring = true)
        compose.onNodeWithTag("count-expected").assertTextContains(result.summary.expected.toString(), substring = true)
        assertEquals(0, compose.onAllNodesWithTag("count-lost").fetchSemanticsNodes().size)
    }

    @Test fun everyModuleOnShowsVisitorsFinesAndTheirSections() {
        val stats = data("s090_statistics_full", Staff090Statistics.serializer())
        var opened: Int? = null
        compose.setContent { PinakesTheme { StatisticsBody(stats, stats.finesReport?.from, stats.finesReport?.to, { opened = it }, {}, {}, {}) } }
        compose.onNodeWithTag("stat-completed").assertTextContains(stats.overview.completedLoans.toString(), substring = true)
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasTestTag("top-book-${stats.topBooks.first().id}"))
        compose.onNodeWithTag("top-book-${stats.topBooks.first().id}").performClick()
        assertEquals(stats.topBooks.first().id, opened)
        list.performScrollToNode(hasTestTag("section-fines"))
        list.performScrollToNode(hasTestTag("visitors"))
        list.performScrollToNode(hasTestTag("fines-report"))
        list.performScrollToNode(hasTestTag("fines-csv"))
    }

    @Test fun modulesOffLeaveTheirSectionsOut() {
        val stats = data("s090_statistics_modules_off", Staff090Statistics.serializer())
        compose.setContent { PinakesTheme { StatisticsBody(stats, null, null, {}, {}, {}, {}) } }
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasTestTag("section-patron-types"))
        assertEquals(0, compose.onAllNodesWithTag("fines-report").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("visitors").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("section-fines").fetchSemanticsNodes().size)
    }
}
