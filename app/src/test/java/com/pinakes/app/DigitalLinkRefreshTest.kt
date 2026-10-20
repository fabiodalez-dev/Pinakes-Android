package com.pinakes.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.DigitalAttachment
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.screens.detail.DetailContent
import com.pinakes.app.ui.theme.PinakesTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DigitalLinkRefreshTest {
    private val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val signed = DigitalAttachment("https://library.example/a.epub?pk_exp=1000&pk_uid=3&pk_sig=old", access = "granted")
    private val fresh = signed.copy(url = signed.url.replace("1000", "4102444800").replace("old", "new"))
    private val messages = mutableListOf<String>()

    private fun show(file: DigitalAttachment = signed, refresh: suspend (DigitalAttachment) -> ApiResult<DigitalAttachment>) {
        compose.setContent { PinakesTheme {
            DetailContent(BookDetail(id = 7, title = "A digital book", digitalAttachments = listOf(file)),
                wishlisted = false, reserveBusy = false, canBorrow = false, showWishlist = false, showReviews = false,
                onReserve = {}, onToggleWishlist = {}, onShowMessage = { messages += it }, onRefreshFile = refresh)
        } }
    }

    private fun open() = compose.onNodeWithText(app.getString(R.string.ebook_open_external)).performScrollTo().performClick()
    private fun nextOpenedUrl() = shadowOf(app).nextStartedActivity?.dataString

    @Test fun expiredLinksOpenTheirRenewedAddressInTheSameTap() {
        var calls = 0
        show { calls++; ApiResult.Success(fresh) }
        open()
        compose.waitForIdle()
        assertEquals(1, calls)
        assertEquals(fresh.url, nextOpenedUrl())
        assertTrue(messages.isEmpty())
    }

    @Test fun evenFutureDatedLinksUseTheServerInsteadOfThePhonesClock() {
        var calls = 0
        show(fresh) { calls++; ApiResult.Success(fresh) }
        open()
        compose.waitForIdle()
        assertEquals(1, calls)
        assertEquals(fresh.url, nextOpenedUrl())
    }

    @Test fun pendingRenewalDisablesRepeatedTapsWithoutClaimingSuccess() {
        val response = CompletableDeferred<ApiResult<DigitalAttachment>>()
        var calls = 0
        show { calls++; response.await() }
        open()
        compose.onNodeWithTag("digital-open").assertIsNotEnabled()
        assertNull(nextOpenedUrl())
        assertTrue(messages.isEmpty())
        compose.runOnIdle { response.complete(ApiResult.Success(fresh)) }
        compose.waitForIdle()
        assertEquals(1, calls)
        assertEquals(fresh.url, nextOpenedUrl())
    }

    @Test fun failedRenewalKeepsTheFileAndAllowsRetry() {
        var calls = 0
        show {
            if (++calls == 1) ApiResult.Failure(ErrorCodes.NETWORK, "offline") else ApiResult.Success(fresh)
        }
        open()
        compose.onNodeWithText(app.getString(R.string.ebook_open_external)).assertIsDisplayed().assertIsEnabled()
        assertEquals(listOf(app.getString(R.string.digital_link_refresh_error)), messages)
        assertNull(nextOpenedUrl())
        open()
        compose.waitForIdle()
        assertEquals(2, calls)
        assertEquals(fresh.url, nextOpenedUrl())
    }

    @Test fun revokedAccessNeverOpensTheOldSignedAddress() {
        show { ApiResult.Failure(ErrorCodes.FORBIDDEN, "", 403) }
        open()
        compose.waitForIdle()
        assertNull(nextOpenedUrl())
        assertEquals(listOf(app.getString(R.string.digital_locked_other)), messages)
    }

    @Test fun olderUnsignedLinksOpenWithoutARefreshRequest() {
        val old = signed.copy(url = "https://library.example/legacy.epub", access = null)
        var calls = 0
        show(old) { calls++; ApiResult.Success(fresh) }
        open()
        compose.waitForIdle()
        assertEquals(0, calls)
        assertEquals(old.url, nextOpenedUrl())
    }

    @Test fun refreshedBookDataPreservesThePositionOfTheOpenButton() {
        var book by mutableStateOf(BookDetail(id = 7, title = "Book", digitalAttachments = listOf(signed)))
        compose.setContent { PinakesTheme {
            DetailContent(book, wishlisted = false, reserveBusy = false, canBorrow = false,
                showWishlist = false, showReviews = false, onReserve = {}, onToggleWishlist = {}, onShowMessage = {},
                onRefreshFile = { book = book.copy(digitalAttachments = listOf(fresh)); ApiResult.Success(fresh) })
        } }
        val button = compose.onNodeWithText(app.getString(R.string.ebook_open_external)).performScrollTo()
        val before = button.getBoundsInRoot()
        button.performClick()
        compose.waitForIdle()
        button.assertIsDisplayed()
        assertEquals(before.top.value, button.getBoundsInRoot().top.value, .5f)
        assertEquals(fresh.url, nextOpenedUrl())
    }

    @Test fun signedAudioWaitsForTheReadersTapInsteadOfStartingAStalePlayer() {
        val audio = signed.copy(url = signed.url.replace("epub", "mp3"), kind = "audio")
        compose.setContent { PinakesTheme {
            DetailContent(BookDetail(id = 7, title = "Audio", hasAudio = true, audioUrl = audio.url,
                digitalAttachments = listOf(audio)), wishlisted = false, reserveBusy = false, canBorrow = false,
                showWishlist = false, showReviews = false, onReserve = {}, onToggleWishlist = {}, onShowMessage = {})
        } }
        compose.onNodeWithText(app.getString(R.string.collection_play)).performScrollTo().assertIsDisplayed()
    }

    @Test fun signatureChangesKeepFileIdentityButOtherParametersDoNot() {
        assertEquals(signed.resourceKey, fresh.resourceKey)
        assertNotEquals(signed.resourceKey, signed.copy(url = signed.url + "&edition=2").resourceKey)
        assertNotEquals(signed.resourceKey, signed.copy(url = signed.url.replace("a.epub", "b.epub")).resourceKey)
        assertEquals(signed.copy(url = signed.url + "&b=2&a=1").resourceKey,
            fresh.copy(url = fresh.url + "&a=1&b=2").resourceKey)
    }
}
