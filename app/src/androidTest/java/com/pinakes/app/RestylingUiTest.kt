package com.pinakes.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.ui.components.*
import com.pinakes.app.ui.screens.detail.DetailContent
import com.pinakes.app.ui.screens.home.HomeContent
import com.pinakes.app.ui.screens.home.HomeUiState
import com.pinakes.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RestylingUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun tallCoverKeepsTheTopAndBottomOfTheArtwork() {
        val bitmap = Bitmap.createBitmap(100, 250, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        for (y in 0 until 15) for (x in 0 until 100) bitmap.setPixel(x, y, android.graphics.Color.RED)
        for (y in 235 until 250) for (x in 0 until 100) bitmap.setPixel(x, y, android.graphics.Color.GREEN)
        val file = File(context.cacheDir, "tall-cover.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { PinakesTheme { BookCover("Tall cover", file.toURI().toString(), Modifier.size(100.dp, 150.dp).testTag("cover")) } }
        // Coil decodes asynchronously. Wait for the expected artwork, not a fixed delay.
        compose.waitUntil(10000) {
            val pixels = compose.onNodeWithTag("cover").captureToImage().toPixelMap()
            val top = pixels[pixels.width / 2, (pixels.height * .03f).toInt()]
            top.red > .7f && top.green < .3f
        }
        val pixels = compose.onNodeWithTag("cover").captureToImage().toPixelMap()
        val bottom = pixels[pixels.width / 2, (pixels.height * .97f).toInt()]
        assertTrue("Publisher band at the bottom is cropped", bottom.green > .7f && bottom.red < .3f)
    }

    @Test fun tintedCardSamplesTheLoadedCover() {
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val file = File(context.cacheDir, "red-cover.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { PinakesTheme(palette = ThemePalette(cardStyle = CardStyle.Tinted)) {
            BookCardGrid("Red cover", "Author", file.toURI().toString(), modifier = Modifier.width(180.dp).testTag("card"))
        } }
        compose.waitUntil(10000) {
            val pixels = compose.onNodeWithTag("card").captureToImage().toPixelMap()
            val panel = pixels[pixels.width / 2, (12 * context.resources.displayMetrics.density).toInt()]
            // The real book shadow can darken the panel; assert the cover's hue.
            panel.red > panel.green + .10f && kotlin.math.abs(panel.green - panel.blue) < .02f && panel.green < .90f
        }
    }

    @Test fun legacyImageUsesTheBindingColourAndAnnouncesTheTitleOnce() {
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val file = File(context.cacheDir, "placeholder.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var tone: Color? = null
        val title = "Il nome della rosa"
        compose.setContent { PinakesTheme {
            BookCover(title, file.toURI().toString(), Modifier.size(160.dp, 240.dp),
                author = "Umberto Eco", publisher = "Bompiani", onTone = { tone = it })
        } }
        compose.waitUntil(10000) { tone != null }
        compose.runOnIdle { assertEquals(BookPlaceholderPapers[bookPlaceholderTone(title)], tone) }
        compose.onAllNodesWithContentDescription(title).assertCountEquals(1)
        // The merged accessibility tree stops at the decorative binding.
        compose.onNodeWithText("Umberto Eco").assertDoesNotExist()
    }

    @Test fun gridFallbackKeepsMetadataAndOpensTheBook() {
        var clicked = false
        compose.setContent { PinakesTheme { Surface { Row(Modifier.width(328.dp)) {
            BookCardGrid("A very long title with København, Grüße and 日本語", "", null,
                AvailabilityStatus.Unavailable, onClick = { clicked = true }, modifier = Modifier.weight(1f), mediaType = "DVD")
            Spacer(Modifier.width(14.dp))
            BookCardGrid("Another book", "Author", null, modifier = Modifier.weight(1f))
        } } } }
        compose.onNodeWithText(text(R.string.restyle_unknown_author), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("DVD", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("A very long title", substring = true)).performClick()
        assertTrue(clicked)
    }

    @Test fun catalogViewToggleKeepsItsSelectedSemantics() {
        compose.setContent { PinakesTheme { var grid by remember { mutableStateOf(true) }; CatalogViewToggle(grid) { grid = it } } }
        compose.onNodeWithContentDescription(text(R.string.restyle_grid)).assertIsSelected()
        compose.onNodeWithContentDescription(text(R.string.restyle_list)).performClick().assertIsSelected()
        compose.onNodeWithContentDescription(text(R.string.restyle_grid)).assertIsNotSelected()
    }

    @Test fun detailWithNoDigitalFilesOmitsTheDigitalSectionAndRetainsActions() {
        var borrowed = false
        compose.setContent { PinakesTheme { DetailContent(BookDetail(title = "Blank book"), false, false,
            canBorrow = true, showWishlist = true, showReviews = false,
            onReserve = { borrowed = true }, onToggleWishlist = {}, onShowMessage = {}) } }
        compose.onNodeWithText(text(R.string.restyle_digital)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.book_reserve)).performScrollTo().performClick()
        assertTrue(borrowed)
        compose.onNodeWithText(text(R.string.book_wishlist)).performScrollTo().assertIsDisplayed()
    }

    @Test fun digitalFilesRemainSeparateOnANarrowScreen() {
        compose.setContent { PinakesTheme { Column(Modifier.width(328.dp)) {
            DigitalFileCard("PDF", "a-long-digital-edition-name.pdf", text(R.string.restyle_digital_edition)) {
                PrimaryButton(text(R.string.book_read), {}, dark = true)
            }
            DigitalFileCard("MP3", "audiobook.mp3", text(R.string.restyle_audiobook)) {
                SecondaryButton("Play", {})
            }
        } } }
        compose.onNodeWithText("PDF").assertIsDisplayed()
        compose.onNodeWithText("MP3").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.book_read)).assertIsDisplayed()
    }

    @Test fun narrowSearchFieldKeepsItsPlaceholderOnOneLine() {
        compose.setContent { PinakesTheme { SearchField("", {}, Modifier.width(180.dp).testTag("search"),
            placeholder = "Search the entire catalog by title, author or publisher") } }
        val bounds = compose.onNodeWithTag("search").getUnclippedBoundsInRoot()
        assertTrue("Placeholder expanded the search field to multiple lines", (bounds.bottom - bounds.top) <= 60.dp)
    }

    @Test fun emptyHomeKeepsItsCatalogActionReachableOnAShortPhone() {
        var browsed = false
        compose.setContent { PinakesTheme { Surface(Modifier.size(360.dp, 480.dp)) {
            HomeContent(HomeUiState(libraryName = "Library", loading = false), false, {}, { browsed = true }, {}, {})
        } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.home_browse_catalog)))
        compose.onNodeWithText(text(R.string.home_browse_catalog)).performClick()
        assertTrue(browsed)
    }

    @Test fun homeSearchSurvivesTheLoadingPhaseTransition() {
        var state by mutableStateOf(HomeUiState(libraryName = "Library", loading = true))
        compose.setContent { PinakesTheme { HomeContent(state, false, {}, {}, {}, {}) } }
        compose.onNode(hasSetTextAction()).performTextInput("Eco")
        compose.runOnIdle { state = state.copy(loading = false) }
        compose.onNode(hasSetTextAction()).assertTextEquals("Eco")
    }

    @Test fun oceanThemeUsesItsButtonTextPair() = themedButtons(ThemePalette(Color(0xFF0284C7), Color(0xFF0C4A6E), Color(0xFF0EA5E9)))
    @Test fun minimalThemeUsesItsButtonTextPair() = themedButtons(ThemePalette(Color(0xFF404040), Color.Black, Color(0xFF808080)))
    @Test fun darkThemeKeepsTheControlsReadable() = themedButtons(ThemePalette(), ThemeMode.DARK)

    private fun themedButtons(palette: ThemePalette, mode: ThemeMode = ThemeMode.LIGHT) {
        compose.setContent { PinakesTheme(mode = mode, palette = palette) { Column {
            PrimaryButton("Borrow", {}, modifier = Modifier.testTag("primary"))
            SecondaryButton("Wishlist", {})
            AvailabilityChip(AvailabilityStatus.Available)
        } } }
        compose.onNodeWithText("Borrow").assertIsDisplayed()
        val pixels = compose.onNodeWithTag("primary").captureToImage().toPixelMap()
        val expected = PinakesColors(palette, mode == ThemeMode.DARK).button
        val actual = pixels[pixels.width / 2, 5]
        assertEquals(expected.red, actual.red, .02f)
        assertEquals(expected.blue, actual.blue, .02f)
    }
}
