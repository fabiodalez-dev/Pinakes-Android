package com.pinakes.app.staff

import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.staff.CopyCodeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copy codes are sent the way the server stores them; export file names are safe to share. */
class CopyCodeCodecTest {

    @Test fun trimsAndStripsControlCharacters() {
        assertEquals("INV-0011", CopyCodeCodec.normalize("  INV-0011\n"))
        assertEquals("AB12", CopyCodeCodec.normalize("A\u0000B\u001D1\t2"))
        assertEquals("", CopyCodeCodec.normalize(" \r\n "))
        assertFalse(CopyCodeCodec.isValid("\u0007"))
        assertTrue(CopyCodeCodec.isValid("x"))
    }

    @Test fun capsAtOneHundredCharacters() {
        assertEquals(100, CopyCodeCodec.normalize("x".repeat(150)).length)
    }

    @Test fun recognisesBookIdentifiers() {
        assertTrue(CopyCodeCodec.looksLikeBookIdentifier("9788804668237"))
        assertTrue(CopyCodeCodec.looksLikeBookIdentifier("9791234567896"))
        assertFalse(CopyCodeCodec.looksLikeBookIdentifier("INV-0011"))
        assertFalse(CopyCodeCodec.looksLikeBookIdentifier("4006381333931"))
    }

    @Test fun exportFileNames() {
        assertEquals("prestito_7_20261014.pdf", StaffFilesRepository.filenameFrom("""attachment; filename="prestito_7_20261014.pdf""""))
        assertEquals("labels.pdf", StaffFilesRepository.filenameFrom("inline; filename=labels.pdf"))
        assertEquals("errori è.csv", StaffFilesRepository.filenameFrom("attachment; filename*=UTF-8''errori%20%C3%A8.csv"))
        assertNull(StaffFilesRepository.filenameFrom(null))
        assertEquals("passwd", StaffFilesRepository.safeName("../../etc/passwd"))
        assertEquals("errori_.csv", StaffFilesRepository.safeName("errori è.csv").replace("__", "_"))
        assertEquals("export", StaffFilesRepository.safeName("..."))
    }
}
