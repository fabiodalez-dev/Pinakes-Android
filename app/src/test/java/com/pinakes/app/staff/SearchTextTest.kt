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
}
