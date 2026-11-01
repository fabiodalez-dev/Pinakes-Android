package com.pinakes.app.server090

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.ui.screens.staff.s090.patrons.OpenJobRow
import com.pinakes.app.ui.theme.PinakesTheme
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

/** The open patron imports on the import screen, from the list the server answered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "en-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Staff090OpenImportsUiTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val jobs: List<UserImportJob> = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        .decodeFromString<Envelope<List<UserImportJob>>>(javaClass.getResourceAsStream("/server090/patrons/ui_list.json")!!.bufferedReader().readText()).data!!

    private val taps = mutableListOf<String>()

    private fun show(busy: String? = null) = compose.setContent {
        PinakesTheme {
            Column { jobs.forEach { j -> OpenJobRow(j, busy, onResume = { taps += "resume ${j.fileName}" }, onCancel = { taps += "cancel ${j.fileName}" }) } }
        }
    }

    @Test fun eachOpenJobSaysWhereItStandsAndOffersResumeAndCancel() {
        show()
        compose.onNodeWithText("readers-b.csv").assertExists()
        compose.onNodeWithText("Started · Rows: 0 of 2").assertExists()
        compose.onNodeWithText("Not started yet · rows: 2").assertExists()
        compose.onNodeWithTag("open-resume-${jobs[1].id}").performClick()
        compose.onNodeWithTag("open-cancel-${jobs[0].id}").performClick()
        assertEquals(listOf("resume readers-a.csv", "cancel readers-b.csv"), taps)
    }

    @Test fun whileOneJobIsBusyTheOthersWait() {
        show(busy = jobs[0].id)
        compose.onNodeWithTag("open-resume-${jobs[1].id}").assertIsNotEnabled()
        compose.onNodeWithTag("open-cancel-${jobs[1].id}").assertIsNotEnabled()
    }
}
