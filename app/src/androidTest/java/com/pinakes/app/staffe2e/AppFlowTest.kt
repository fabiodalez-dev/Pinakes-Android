package com.pinakes.app.staffe2e

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pinakes.app.MainActivity
import com.pinakes.app.R
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.sync.CatalogSyncWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Rule

/**
 * Drives the real app (MainActivity, real Hilt graph, real ViewModels, repositories and Room)
 * against [FakePinakes] on the device loopback. The session is set up the way login leaves it
 * (instance URL, token, role, `/health` flags), then the test acts like a user: tabs, rows,
 * buttons, typed text. System UI (camera permission, airplane mode) goes through UiAutomator.
 *
 * Runs on a disposable emulator: it replaces the app's stored session and offline cache.
 */
abstract class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val context get() = instrumentation.targetContext
    protected val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    protected lateinit var api: FakePinakes
    private var scenario: ActivityScenario<MainActivity>? = null

    protected val deps: CatalogSyncWorker.Deps by lazy {
        EntryPointAccessors.fromApplication(context.applicationContext as Application, CatalogSyncWorker.Deps::class.java)
    }

    protected fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Sign in as [role] on a fresh fake instance and open the app. */
    protected fun launch(role: String = "admin", health: String = "health_staff", configure: FakePinakes.() -> Unit = {}) {
        setAirplaneMode(false)
        api = FakePinakes().apply(configure)
        api.json("GET health", health)
        runBlocking { deps.authRepository().forgetInstance() }
        val session = deps.session()
        session.saveInstance(api.origin, api.apiBaseUrl, "Test Library", allowInsecure = true)
        session.saveToken("test-token")
        session.saveRole(role)
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }
        deps.featureStore().update(json.decodeFromString<Envelope<HealthPayload>>(FakePinakes.fixture(health)).data!!)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasText(text(R.string.nav_profile)))
    }

    @After
    fun tearDown() {
        setAirplaneMode(false)
        scenario?.close()
        if (::api.isInitialized) api.shutdown()
    }

    protected fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun waitGone(matcher: androidx.compose.ui.test.SemanticsMatcher, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    protected fun waitText(value: String, timeoutMs: Long = 10_000) = waitFor(hasText(value), timeoutMs)

    protected fun exists(value: String, substring: Boolean = false): Boolean =
        compose.onAllNodes(hasText(value, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun node(value: String): SemanticsNodeInteraction = compose.onAllNodes(hasText(value)).onFirst()

    protected fun click(value: String) {
        waitText(value)
        node(value).performScrollToSafely().performClick()
        compose.waitForIdle()
    }

    /**
     * Click [value] until [expected] shows (a tap can land while the list is still settling,
     * e.g. right after the profile finished loading). Fails after [attempts].
     */
    protected fun clickUntil(value: String, expected: String, attempts: Int = 3) {
        repeat(attempts) {
            click(value)
            if (runCatching { waitText(expected, 5_000) }.isSuccess) return
        }
        waitText(expected, 1)
    }

    protected fun clickDescription(description: String) {
        waitFor(hasContentDescription(description))
        compose.onAllNodes(hasContentDescription(description)).onFirst().performClick()
        compose.waitForIdle()
    }

    protected fun openTab(labelRes: Int) = click(text(labelRes))

    protected fun SemanticsNodeInteraction.performScrollToSafely(): SemanticsNodeInteraction =
        runCatching { performScrollTo() }.getOrDefault(this)

    /** Wait until a request matching [method] + [path] reached the fake server. */
    protected fun waitRequest(method: String, path: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { api.recorded(method, path).isNotEmpty() }
    }

    /** Wait for the offline catalogue to hold the fixture rows (the first sync after launch). */
    protected fun waitCatalogueCached() {
        compose.waitUntil(15_000) { runBlocking { deps.catalogRepository().hasCachedCatalog() } }
    }

    /** Deny the system camera prompt if it shows (it does on the first scanner visits). */
    protected fun denyCameraPromptIfShown() {
        val deny = device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_deny_button")), 3_000)
            ?: device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button")), 500)
        deny?.click()
        device.waitForIdle()
    }

    protected fun setAirplaneMode(on: Boolean) {
        val mode = if (on) "enable" else "disable"
        instrumentation.uiAutomation.executeShellCommand("cmd connectivity airplane-mode $mode").close()
        Thread.sleep(1_500)
    }
}
