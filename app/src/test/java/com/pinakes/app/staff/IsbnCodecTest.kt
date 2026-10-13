package com.pinakes.app.staff

import com.pinakes.app.data.staff.Identifier
import com.pinakes.app.data.staff.IsbnCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnCodecTest {

    @Test fun ean13ChecksumValidAndInvalid() {
        assertTrue(IsbnCodec.checkEan13("9788804668237"))
        assertFalse(IsbnCodec.checkEan13("9788804668238"))
        assertTrue(IsbnCodec.checkEan13("4006381333931"))   // non-book EAN
        assertFalse(IsbnCodec.checkEan13("978880466823"))   // 12 digits
        assertFalse(IsbnCodec.checkEan13("97888046682A7"))
    }

    @Test fun scanOfBooklandBecomesIsbn13WithIsbn10() {
        val id = IsbnCodec.fromScan("9788804668237")
        assertEquals(Identifier.Isbn13("9788804668237", "8804668237"), id)
        assertEquals("9788804668237", id?.lookupCode)
    }

    @Test fun scanOf979HasNoIsbn10() {
        val id = IsbnCodec.fromScan("9791032305690") as Identifier.Isbn13
        assertNull(id.isbn10)
    }

    @Test fun scanOfNonBookEanIsEan() {
        assertEquals(Identifier.Ean("4006381333931"), IsbnCodec.fromScan("4006381333931"))
    }

    @Test fun scanRejectsBadChecksumAndPartialReads() {
        assertNull(IsbnCodec.fromScan("9788804668238"))
        assertNull(IsbnCodec.fromScan("88046682"))
        assertNull(IsbnCodec.fromScan(null))
        assertNull(IsbnCodec.fromScan("978-8804668237"))   // scans are digits only
    }

    @Test fun manualIsbn10WithXConvertsTo13AndKeepsBoth() {
        val id = IsbnCodec.fromManual("0-8044-2957-X") as Identifier.Isbn13
        assertEquals("080442957X", id.isbn10)
        assertEquals("9780804429573", id.isbn13)
        assertTrue(IsbnCodec.checkEan13(id.isbn13))
    }

    @Test fun manualLowercaseXIsAccepted() {
        assertEquals("080442957X", (IsbnCodec.fromManual("080442957x") as Identifier.Isbn13).isbn10)
    }

    @Test fun manualAcceptsDashesAndSpaces() {
        assertEquals(Identifier.Isbn13("9788804668237", "8804668237"), IsbnCodec.fromManual(" 978-88-04 66823-7 "))
    }

    @Test fun manualAcceptsEan() {
        assertEquals(Identifier.Ean("4006381333931"), IsbnCodec.fromManual("4006381333931"))
    }

    @Test fun manualRejectsGarbage() {
        assertNull(IsbnCodec.fromManual(""))
        assertNull(IsbnCodec.fromManual("hello"))
        assertNull(IsbnCodec.fromManual("0804429571"))       // wrong ISBN-10 check digit
        assertNull(IsbnCodec.fromManual("X804429573"))       // X only allowed last
        assertNull(IsbnCodec.fromManual("97888046682370"))   // 14 digits
    }

    @Test fun isbn10And13ConversionsAreInverse() {
        assertEquals("9788804668237", IsbnCodec.isbn10To13("8804668237"))
        assertEquals("8804668237", IsbnCodec.isbn13To10("9788804668237"))
        assertNull(IsbnCodec.isbn13To10("9791032305690"))
    }

    @Test fun formValidatorsAllowBlankAndRejectWrongCodes() {
        assertTrue(IsbnCodec.isValidIsbn13OrBlank(""))
        assertTrue(IsbnCodec.isValidIsbn13OrBlank("978-88-04-66823-7"))
        assertFalse(IsbnCodec.isValidIsbn13OrBlank("4006381333931"))   // valid EAN, not an ISBN
        assertTrue(IsbnCodec.isValidIsbn10OrBlank("080442957X"))
        assertFalse(IsbnCodec.isValidIsbn10OrBlank("0804429571"))
        assertTrue(IsbnCodec.isValidEanOrBlank("4006381333931"))
        assertTrue(IsbnCodec.isValidIssnOrBlank("0317-8471"))
        assertTrue(IsbnCodec.isValidIssnOrBlank("2049-3630"))
        assertFalse(IsbnCodec.isValidIssnOrBlank("0317-8472"))
    }
}
