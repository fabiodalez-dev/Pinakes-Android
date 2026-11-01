package com.pinakes.app.server090

import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.staff.Staff090Areas
import com.pinakes.app.data.staff.StaffAreas
import com.pinakes.app.data.store.InstanceFeatures
import com.pinakes.app.data.store.Staff090Flags
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mobile API 1.10.0: the 0.9.0 module flags and `staff_<area>` flags → [Staff090Flags] → [Staff090Areas]. */
class Staff090FlagsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val allOn = listOf(
        "stocktake", "marc", "visitor_log", "catalogue_cards", "suppliers", "rda_types", "patron_cards",
        "user_import", "patron_photos", "automatic_card_expiry", "automatic_fines", "debt_block",
        "loss_penalties", "attendance_retention", "circulation_rules", "fines", "staff_catalog",
        "staff_patrons", "staff_ledger", "staff_stocktake", "staff_library_settings", "staff_circulation_rules",
        "staff_closures_calendar", "staff_suppliers", "staff_catalogue_cards", "staff_marc",
        "staff_attendance", "staff_statistics", "staff_user_import",
    ).joinToString(",") { "\"$it\":true" }

    private fun health(features: String, catalogueMode: Boolean = false, appAccess: Boolean = true) =
        json.decodeFromString<HealthPayload>(
            """{"name":"Lib","app_access_enabled":$appAccess,"catalogue_mode":$catalogueMode,"features":{"catalog":true,"loans":true,$features}}""",
        )

    @Test fun anOlderServerLeavesEveryToolHidden() {
        val f = InstanceFeatures.from(health(""""staff_catalog":true"""))
        assertEquals(Staff090Flags.None, f.staff090)
        assertEquals(Staff090Areas.None, StaffAreas.of("admin", f, online = true).s090.copy(admin = false))
    }

    @Test fun aServerAdvertisingEverythingShowsEveryTool() {
        val areas = StaffAreas.of("admin", InstanceFeatures.from(health(allOn)), online = true).s090
        assertTrue(areas.admin)
        assertTrue(areas.patrons && areas.patronTypes && areas.patronCards && areas.patronPhotos && areas.ledger)
        assertTrue(areas.stocktake && areas.librarySettings && areas.circulationRules && areas.closuresCalendar)
        assertTrue(areas.suppliers && areas.rdaTypes && areas.catalogueCards && areas.marc)
        assertTrue(areas.attendance && areas.statistics && areas.userImport)
        assertTrue(areas.anyPatronDesk && areas.anyCatalogue && areas.anyLibrary)
    }

    @Test fun catalogueModeHidesOnlyTheCirculationTools() {
        val f = InstanceFeatures.from(health(allOn, catalogueMode = true))
        val areas = StaffAreas.of("staff", f, online = true).s090
        assertFalse(areas.ledger)
        assertFalse(areas.attendance)
        assertFalse(f.staff090.automaticFines)
        assertTrue(areas.stocktake)
        assertTrue(areas.marc)
        assertTrue(areas.userImport)
        // Configuring a module stays reachable: the server decides whether the user may.
        assertTrue(areas.circulationRules)
        assertFalse(areas.admin)
    }

    @Test fun appAccessOffHidesEverything() {
        val f = InstanceFeatures.from(health(allOn, appAccess = false))
        assertEquals(Staff090Flags.None, f.staff090)
    }

    @Test fun offlineHidesEveryToolAndAReaderNeverSeesThem() {
        val f = InstanceFeatures.from(health(allOn))
        assertEquals(Staff090Areas.None, StaffAreas.of("admin", f, online = false).s090)
        assertEquals(Staff090Areas.None, StaffAreas.of("standard", f, online = true).s090)
    }

    @Test fun patronToolsFollowTheirOwnModules() {
        val f = InstanceFeatures.from(health(""""staff_catalog":true,"staff_patrons":true,"patron_cards":true"""))
        val areas = StaffAreas.of("staff", f, online = true).s090
        assertTrue(areas.patrons)
        assertTrue(areas.patronCards)
        assertFalse(areas.patronPhotos)
        assertFalse(areas.patronTypes)
    }

    @Test fun theFlagsSurviveTheirJsonRoundTrip() {
        val flags = InstanceFeatures.from(health(allOn)).staff090
        val decoded = json.decodeFromString(Staff090Flags.serializer(), json.encodeToString(Staff090Flags.serializer(), flags))
        assertEquals(flags, decoded)
    }
}
