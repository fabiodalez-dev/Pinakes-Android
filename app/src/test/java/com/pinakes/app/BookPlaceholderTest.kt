package com.pinakes.app

import androidx.compose.ui.graphics.Color
import com.pinakes.app.ui.components.BookPlaceholderInk
import com.pinakes.app.ui.components.BookPlaceholderPapers
import com.pinakes.app.ui.components.bookCoverImageUrl
import com.pinakes.app.ui.components.bookPlaceholderTone
import com.pinakes.app.ui.theme.contrastRatio
import com.pinakes.app.ui.theme.mix
import org.junit.Assert.*
import org.junit.Test

class BookPlaceholderTest {
    @Test fun bindingColoursMatchTheBrowserUtf8Keys() {
        assertEquals(1, bookPlaceholderTone("Il nome della rosa"))
        assertEquals(3, bookPlaceholderTone("Grüße 日本語"))
        assertEquals(0, bookPlaceholderTone("L’isola del tesoro"))
        assertEquals(1, bookPlaceholderTone("  Cien años de soledad  "))
    }

    @Test fun legacyPlaceholdersUseTheTypesetCover() {
        listOf(null, "", "  ", "https://library.example/uploads/placeholder.jpg",
            "https://library.example/Placeholder.PNG?v=1#cover", "/uploads/placeholder.svg")
            .forEach { assertNull(bookCoverImageUrl(it)) }
    }

    @Test fun realArtworkIsNotMistakenForAPlaceholder() {
        listOf("https://library.example/placeholder-history.jpg", "https://library.example/placeholder/7.jpg",
            "https://library.example/7.jpg?title=placeholder.jpg")
            .forEach { assertEquals(it, bookCoverImageUrl(" $it ")) }
    }

    @Test fun inkStaysReadableEvenUnderTheBrowserGlossAndClothTexture() {
        BookPlaceholderPapers.forEach { paper ->
            // The browser's 22% white gloss is stronger than the native gloss.
            val lightestPaper = mix(Color.White, paper, .24f)
            assertTrue("Cover ink must retain AA contrast", contrastRatio(BookPlaceholderInk, lightestPaper) >= 4.5f)
        }
    }
}
