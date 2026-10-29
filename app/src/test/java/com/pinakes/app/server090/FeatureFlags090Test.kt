package com.pinakes.app.server090

import com.pinakes.app.data.model.HealthFeatures
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.store.InstanceFeatures
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `/health.features.{fines,closures_calendar,circulation_rules}` (Pinakes 0.9.0) → app flags. */
class FeatureFlags090Test {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun health(features: String, catalogueMode: Boolean = false) = json.decodeFromString<HealthPayload>(
        """{"name":"Lib","app_access_enabled":true,"catalogue_mode":$catalogueMode,"features":{"catalog":true,"loans":true,"reservations":true,$features}}""",
    )

    @Test fun anOlderServerWithoutTheKeysLeavesAllThreeOff() {
        val f = InstanceFeatures.from(health(""""wishlist":true"""))
        assertFalse(f.finesAvailable)
        assertFalse(f.closuresCalendarAvailable)
        assertFalse(f.circulationRulesAvailable)
        assertFalse(f.showMovements)
    }

    @Test fun aServerAdvertisingTheFlagsTurnsThemOn() {
        val f = InstanceFeatures.from(health(""""fines":true,"closures_calendar":true,"circulation_rules":true"""))
        assertTrue(f.finesAvailable)
        assertTrue(f.closuresCalendarAvailable)
        assertTrue(f.circulationRulesAvailable)
        assertTrue(f.showMovements)
    }

    @Test fun eachFlagIsReadOnItsOwn() {
        val f = InstanceFeatures.from(health(""""fines":false,"closures_calendar":true,"circulation_rules":false"""))
        assertFalse(f.finesAvailable)
        assertTrue(f.closuresCalendarAvailable)
        assertFalse(f.circulationRulesAvailable)
    }

    @Test fun catalogueModeWinsOverAnyAdvertisedFlag() {
        val f = InstanceFeatures.from(health(""""fines":true,"closures_calendar":true,"circulation_rules":true""", catalogueMode = true))
        assertFalse(f.finesAvailable)
        assertFalse(f.closuresCalendarAvailable)
        assertFalse(f.circulationRulesAvailable)
        assertFalse(f.showMovements)
    }

    @Test fun appAccessOffHidesTheThreeAreasLikeEveryOtherArea() {
        val payload = json.decodeFromString<HealthPayload>(
            """{"app_access_enabled":false,"features":{"loans":true,"fines":true,"closures_calendar":true,"circulation_rules":true}}""",
        )
        val f = InstanceFeatures.from(payload)
        assertFalse(f.finesAvailable)
        assertFalse(f.closuresCalendarAvailable)
        assertFalse(f.circulationRulesAvailable)
        assertFalse(f.showMovements)
    }

    @Test fun theMovementsPageNeedsLoansToo() {
        val f = InstanceFeatures(finesAvailable = true, loans = false)
        assertFalse(f.showMovements)
        assertTrue(InstanceFeatures(finesAvailable = true, loans = true).showMovements)
    }

    @Test fun theDefaultsBeforeAnyHealthKeepTheNewAreasHidden() {
        val f = InstanceFeatures.AllEnabled
        assertTrue(f.loans)
        assertFalse(f.finesAvailable)
        assertFalse(f.showMovements)
    }

    @Test fun theMappingKeepsThePluginProbesAndTheExistingFlags() {
        val payload = HealthPayload(
            appAccessEnabled = true,
            features = HealthFeatures(loans = true, staffCirculation = true, calendarFeed = true, fines = true),
        )
        val f = InstanceFeatures.from(payload, bookClub = true, periodicals = false)
        assertTrue(f.bookClubAvailable)
        assertFalse(f.periodicalsAvailable)
        assertTrue(f.staffCirculationAvailable)
        assertTrue(f.calendarFeedAvailable)
        assertEquals(true, f.finesAvailable)
    }
}
