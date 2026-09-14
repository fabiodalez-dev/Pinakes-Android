package com.pinakes.app

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.PeriodicalsHealth
import com.pinakes.app.data.model.StandaloneArticle
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StandaloneArticleContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun oldServerDoesNotAdvertiseArticles() {
        val legacy = json.decodeFromString<Envelope<PeriodicalsHealth>>("""{"data":{"status":"ok"},"error":null}""")
        val current = json.decodeFromString<Envelope<PeriodicalsHealth>>("""{"data":{"status":"ok","capabilities":{"standalone_articles":true}},"error":null}""")
        assertFalse(legacy.data!!.capabilities.standaloneArticles)
        assertTrue(current.data!!.capabilities.standaloneArticles)
    }

    @Test fun italianWireFieldsPreserveTheIssue412Citation() {
        val result = json.decodeFromString<Envelope<List<StandaloneArticle>>>("""
            {"data":[{"id":42,"titolo":"Intertextuality in Daniel Kehlmann's Novel Tyll",
            "autori":"Schweissinger, Marc J.","contenitore_titolo":"International Journal of Language and Literature",
            "contenitore_tipo":"rivista","data_pubblicazione_testo":"June 2019","anno_pubblicazione":2019,
            "volume":"7","numero":"1","pagine":"138–148","testata_id":null,"fascicolo_id":null,
            "abstract":"Description","has_public_pdf":false,"pdf_url":null,"kind":"autonomo"}],
            "meta":{"next_cursor":"42"},"error":null}
        """.trimIndent())
        val article = result.data!!.single()
        assertEquals("June 2019", article.dateLabel)
        assertEquals("138–148", article.pages)
        assertEquals("7", article.volume)
        assertEquals("1", article.number)
        assertEquals("Schweissinger, Marc J.", article.authors)
        assertEquals("Description", article.description)
        assertNull(article.mastheadId)
        assertEquals("42", result.meta!!.nextCursor)
    }

    @Test fun dateAndPagesAreNeverCoercedToIsoOrIntegers() {
        val article = StandaloneArticle(publicationYear = 2019, pages = "iv–x, 138–148")
        assertEquals("2019", article.dateLabel)
        assertEquals("iv–x, 138–148", article.pages)
        assertNull(StandaloneArticle().dateLabel)
    }

    @Test fun pdfRequiresBothPublicFlagAndValidWebUrl() {
        val article = StandaloneArticle(hasPublicPdf = true, pdfUrl = "https://library.example/subdir/emeroteca/articolo/42/pdf")
        assertTrue(article.canOpenPdf)
        assertFalse(article.copy(hasPublicPdf = false).canOpenPdf)
        assertFalse(article.copy(pdfUrl = null).canOpenPdf)
        assertFalse(article.copy(pdfUrl = "javascript:alert(1)").canOpenPdf)
        assertFalse(article.copy(pdfUrl = "/storage/private.pdf").canOpenPdf)
    }
}
