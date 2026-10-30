package com.pinakes.app.staff

import com.pinakes.app.data.local.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test fun foldRemovesAccentsAndCase() {
        assertEquals("citta perche", SearchText.fold("Città Perché"))
        assertEquals("uber kastner", SearchText.fold("Über Kästner"))
        assertEquals("ærø", SearchText.fold("ÆRØ"))
        assertEquals("", SearchText.fold(null))
    }

    @Test fun keyHoldsTextAndCompactIdentifiers() {
        val key = SearchText.key("La Città", null, "Calvino", "Einaudi", "978-88-04-66823-7", null, null)
        assertTrue(key.contains("la citta"))
        assertTrue(key.contains("calvino"))
        assertTrue(key.contains("9788804668237"))
    }

    @Test fun queryPatternEscapesLikeMetacharacters() {
        assertEquals("100\\%", SearchText.queryPattern("100%"))
        assertEquals("a\\_b", SearchText.queryPattern("a_b"))
        assertEquals("c:\\\\x", SearchText.queryPattern("C:\\X"))
    }

    @Test fun identifierLikeQueriesLoseSeparators() {
        assertEquals("9788804668237", SearchText.queryPattern(" 978-88-04-66823-7 "))
        assertEquals("080442957x", SearchText.queryPattern("0-8044-2957-X"))
        // Short numbers and mixed text keep their spaces and dashes.
        assertEquals("1-2", SearchText.queryPattern("1-2"))
        assertEquals("harry potter 7", SearchText.queryPattern("Harry Potter 7"))
    }

    @Test fun quotedQueriesSearchTheWordsOffline() {
        // Pinakes #501: quotes ask the server for the exact spelling. Offline the key folds
        // accents, so the quotes go and the words are searched as they would be unquoted.
        assertEquals("rolf haffner", SearchText.queryPattern("\"Rolf Häffner\""))
        assertEquals("haffner roma", SearchText.queryPattern("\u201EHäffner\u201C Roma"))
        assertEquals("haffner roma", SearchText.queryPattern("\u201CHäffner\u201D Roma"))
        assertEquals("haffner", SearchText.queryPattern("\u00ABHäffner\u00BB"))
        // An unclosed quote is dropped too.
        assertEquals("haffner", SearchText.queryPattern("\"Häffner"))
        assertEquals("plain words", SearchText.withoutSearchQuotes("plain words"))
    }

    @Test fun anInchMarkAfterADigitStays() {
        assertEquals("disco 12\"", SearchText.queryPattern("Disco 12\""))
        assertEquals("schermo 15\u201D", SearchText.queryPattern("Schermo 15\u201D"))
        // Inside a quoted part the quote after a digit closes it, as on the server.
        assertEquals("catch 22", SearchText.queryPattern("\"Catch 22\""))
    }
}
