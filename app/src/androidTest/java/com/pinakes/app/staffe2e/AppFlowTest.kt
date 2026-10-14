package com.pinakes.app.staffe2e

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
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

    /** Like [waitFor] on the merged tree (a field's label or a button's text belong to the merged node). */
    protected fun waitForMerged(matcher: androidx.compose.ui.test.SemanticsMatcher, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
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

    /** Profile → Staff area (the hub). */
    protected fun openStaffHub() {
        openTab(R.string.nav_profile)
        clickUntil(text(R.string.profile_action_staff), text(R.string.staff_scan_subtitle))
    }

    /** Scroll the lazy list holding [value] until it is composed (items below the fold are not in the tree). */
    protected fun scrollListTo(value: String) {
        val list = compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
        compose.waitUntil(10_000) { list.fetchSemanticsNodes().isNotEmpty() }
        val count = list.fetchSemanticsNodes().size
        for (i in 0 until count) {
            val done = runCatching { list[i].performScrollToNode(hasText(value)) }.isSuccess
            if (done) return
        }
    }

    protected fun clickTag(tag: String) {
        waitFor(androidx.compose.ui.test.hasTestTag(tag))
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).onFirst().performScrollToSafely().performClick()
        compose.waitForIdle()
    }

    /** Click the button labelled [value] inside the open dialog (not the identical one behind it). */
    protected fun clickInDialog(value: String) {
        val matcher = hasText(value) and androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())
        waitForMerged(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
    }

    /** Type into the text field whose label (or placeholder) is [label]. */
    protected fun typeInto(label: String, value: String) {
        val matcher = androidx.compose.ui.test.hasSetTextAction() and hasText(label, substring = true)
        waitForMerged(matcher)
        compose.onAllNodes(matcher).onFirst().performScrollToSafely().performTextInput(value)
        compose.waitForIdle()
    }

    /** Type into the text field tagged [tag] (search fields without a label). */
    protected fun typeIntoTag(tag: String, value: String) {
        val matcher = androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasTestTag(tag)
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performTextInput(value)
        compose.waitForIdle()
    }

    /** The UTF-8 body of the last [method] [path] request. */
    protected fun lastBody(method: String, path: String): String {
        waitRequest(method, path)
        return api.recorded(method, path).last().body.clone().readUtf8()
    }

    protected fun setAirplaneMode(on: Boolean) {
        val mode = if (on) "enable" else "disable"
        instrumentation.uiAutomation.executeShellCommand("cmd connectivity airplane-mode $mode").close()
        Thread.sleep(1_500)
    }

    protected fun publishDownload(name: String, content: String, mime: String = "text/csv"): android.net.Uri {
        check(android.os.Build.VERSION.SDK_INT >= 29)
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore refused the download")
        resolver.openOutputStream(uri)!!.use { it.write(content.toByteArray()) }
        return uri
    }

    /** Find [name] in the system document picker (Recent, else the Downloads root) and pick it. */
    protected fun pickInDocumentsUi(name: String) {
        val file = device.wait(Until.findObject(By.text(name)), 6_000) ?: run {
            device.wait(Until.findObject(By.desc("Show roots")), 3_000)?.click()
            device.wait(Until.findObject(By.text("Downloads")), 3_000)?.click()
            device.wait(Until.findObject(By.text(name)), 6_000)
        }
        org.junit.Assert.assertNotNull("$name not shown by the document picker", file)
        file!!.click()
        device.waitForIdle()
    }
}
