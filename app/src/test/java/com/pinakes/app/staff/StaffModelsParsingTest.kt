package com.pinakes.app.staff

import com.pinakes.app.data.model.CalendarStatus
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.IsbnLookup
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffGenreNode
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.SyncPage
import com.pinakes.app.data.model.flatten
import com.pinakes.app.data.network.parseErrorBody
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mobile API 1.6.0 payloads decode with the app's tolerant Json settings. */
class StaffModelsParsingTest {

    // Same configuration as NetworkModule.json.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun healthFeaturesDefaultOffOnOlderServers() {
        val old = json.decodeFromString<HealthPayload>("""{"name":"Lib","features":{"catalog":true}}""")
        assertFalse(old.features.staffCatalog)
        assertFalse(old.features.catalogSync)
        assertFalse(old.features.calendarFeed)
        val new = json.decodeFromString<HealthPayload>("""{"features":{"staff_catalog":true,"catalog_sync":true,"calendar_feed":true}}""")
        assertTrue(new.features.staffCatalog && new.features.catalogSync && new.features.calendarFeed)
    }

    @Test fun lookupsAcceptObjectsAndStrings() {
        val l = json.decodeFromString<StaffLookups>(
            """{"languages":[{"code":"it","label":"Italiano"}],"media_types":[{"value":"libro","label":"Libro"}],
               "formats":["Brossura","Copertina rigida"],"acquisition_types":[{"value":"dono"}],"unknown":1}"""
        )
        assertEquals("it", l.languageOptions.single().value)
        assertEquals("Italiano", l.languageOptions.single().label)
        assertEquals("Libro", l.mediaTypeOptions.single().label)
        assertEquals(listOf("Brossura", "Copertina rigida"), l.formatOptions.map { it.label })
        assertEquals("dono", l.acquisitionTypeOptions.single().label)
    }

    @Test fun isbnLookupWithPreviewAndExisting() {
        val r = json.decodeFromString<IsbnLookup>(
            """{"identifier":{"input":"9788804668237","isbn10":"8804668237","isbn13":"9788804668237","ean":null,"kind":"isbn"},
               "found":true,"existing":{"id":12,"title":"Il nome della rosa","copies_total":3,"location":"A.1.4"},
               "preview":{"title":"Il nome della rosa","authors":["Umberto Eco",{"name":"Altro"}],"year":1980,"pages":"503",
               "price":12.5,"cover_url":"https://c/x.jpg"},"sources_checked":["google",{"name":"sbn"}]}"""
        )
        assertTrue(r.found)
        assertEquals(12, r.existing?.id)
        assertEquals("A.1.4", r.existing?.locationLabel)
        assertEquals(listOf("Umberto Eco", "Altro"), r.preview?.authorNames)
        assertEquals("1980", r.preview?.year)   // number accepted into a string field
        assertEquals("12.5", r.preview?.price)
    }

    @Test fun isbnLookupNotFound() {
        val r = json.decodeFromString<IsbnLookup>("""{"identifier":{"input":"4006381333931","kind":"ean"},"found":false,"existing":null,"preview":null}""")
        assertFalse(r.found)
        assertNull(r.preview)
        assertEquals("ean", r.identifier.kind)
    }

    @Test fun staffBookDecodesNestedObjects() {
        val b = json.decodeFromString<StaffBook>(
            """{"id":5,"title":"T","year":2001,"pages":120,"genre":{"id":3,"name":"Romanzo"},"subgenre":null,
               "authors":[{"id":1,"name":"A","role":"principale"},{"id":2,"name":"B","role":"traduttore"}],
               "publishers":[{"id":9,"name":"P"}],"location":{"shelf_id":1,"shelf_code":"A","level_id":2,"level_number":1,"position":3,"label":"A.1.3"},
               "copies":[{"id":1,"inventory_number":"LIB-5-C1","status":"disponibile"}],"is_wanted":false,"cover_is_local":true}"""
        )
        assertEquals("2001", b.year)
        assertEquals("120", b.pages)
        assertEquals(2, b.authors.size)
        assertEquals("A.1.3", b.location?.label)
        assertEquals("LIB-5-C1", b.copies.single().inventoryNumber)
    }

    @Test fun genreTreeFlattensWithPaths() {
        val tree = json.decodeFromString<List<StaffGenreNode>>(
            """[{"id":1,"name":"Narrativa","parent_id":null,"children":[{"id":2,"name":"Giallo","parent_id":1,"children":[]}]}]"""
        )
        val flat = tree.flatten()
        assertEquals(listOf(1, 2), flat.map { it.id })
        assertEquals("Narrativa › Giallo", flat[1].path)
        assertEquals(1, flat[1].parentId)
    }

    @Test fun syncPageDecodes() {
        val env = json.decodeFromString<Envelope<SyncPage>>(
            """{"data":{"items":[{"id":1,"change":"upsert","changed_at":"2026-10-13 10:00:00","book":{"id":1,"title":"T","pages":10}},
               {"id":2,"change":"delete","changed_at":"2026-10-13 10:00:01"}],"server_time":"2026-10-13 10:05:00","full_resync":false},
               "meta":{"next_cursor":"abc","has_more":true,"count":2},"error":null}"""
        )
        assertEquals(2, env.data?.items?.size)
        assertEquals("2026-10-13 10:05:00", env.data?.serverTime)
        assertEquals(true, env.meta?.hasMore)
        assertEquals("abc", env.meta?.nextCursor)
    }

    @Test fun calendarStatusDecodes() {
        val s = json.decodeFromString<CalendarStatus>("""{"enabled":true,"feed_url":"https://h/f.ics","webcal_url":"webcal://h/f.ics","created_at":"2026-10-01 09:00:00","rotated_at":null}""")
        assertTrue(s.enabled)
        assertEquals("webcal://h/f.ics", s.webcalUrl)
        assertFalse(json.decodeFromString<CalendarStatus>("""{"enabled":false}""").enabled)
    }

    @Test fun duplicateErrorKeepsTheExistingRecordInMeta() {
        val f = parseErrorBody(
            """{"data":null,"meta":{"existing":{"id":12,"title":"Il nome della rosa"}},"error":{"code":"duplicate","message":"Già presente"}}""",
            409,
        )
        assertEquals("duplicate", f.code)
        assertEquals(409, f.httpStatus)
        assertTrue(f.meta?.existing != null)
    }

    @Test fun conflictReasonIsKept() {
        val f = parseErrorBody("""{"meta":{"reason":"active_circulation"},"error":{"code":"conflict","message":"x"}}""", 409)
        assertEquals("active_circulation", f.meta?.reason)
    }
}
