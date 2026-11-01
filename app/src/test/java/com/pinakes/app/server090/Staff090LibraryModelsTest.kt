package com.pinakes.app.server090

import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.Staff090LibraryCalendar
import com.pinakes.app.data.model.Staff090LibraryCards
import com.pinakes.app.data.model.Staff090LibraryClosure
import com.pinakes.app.data.model.Staff090LibraryCountries
import com.pinakes.app.data.model.Staff090LibraryCustomHoliday
import com.pinakes.app.data.model.Staff090LibraryIcsImported
import com.pinakes.app.data.model.Staff090LibraryLoans
import com.pinakes.app.data.model.Staff090LibraryModules
import com.pinakes.app.data.model.Staff090LibraryPatronType
import com.pinakes.app.data.model.Staff090LibraryPresetApplied
import com.pinakes.app.data.model.Staff090LibraryRules
import com.pinakes.app.data.model.Staff090LibraryTypeDeleted
import com.pinakes.app.data.model.Staff090LibraryTypes
import com.pinakes.app.data.model.Staff090LibraryYearlySaved
import com.pinakes.app.data.store.InstanceFeatures
import com.pinakes.app.data.staff.Staff090Areas
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library settings areas (Mobile API 1.10.0) decode the bodies the server really sends:
 * every sample under `test/resources/server090/library` was captured from a Pinakes 0.9.0
 * instance (feat/mobile-staff-090) with curl, as an administrator and as a staff member.
 */
class Staff090LibraryModelsTest {

    // Same configuration as NetworkModule.json.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    private fun raw(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/server090/library/$name.json")) { "missing sample $name" }.bufferedReader().use { it.readText() }

    private fun <T> data(name: String, serializer: KSerializer<T>): T =
        requireNotNull(json.decodeFromString(Envelope.serializer(serializer), raw(name)).data) { "no data in $name" }

    @Test fun healthAdvertisesTheThreeAreasAndTheyShowForStaff() {
        val health = data("s090_library_health", HealthPayload.serializer())
        assertTrue(health.features.staffLibrarySettings && health.features.staffCirculationRules && health.features.staffClosuresCalendar)
        val areas = Staff090Areas.of("staff", InstanceFeatures.from(health), online = true)
        assertTrue(areas.librarySettings && areas.circulationRules && areas.closuresCalendar && areas.anyLibrary)
        assertFalse(Staff090Areas.of("staff", InstanceFeatures.from(health), online = false).anyLibrary)
    }

    @Test fun modulesAsAdministratorAndAsStaff() {
        val admin = data("s090_library_modules_admin", Staff090LibraryModules.serializer())
        assertTrue(admin.canConfigure)
        assertEquals(365, admin.attendanceRetentionDays)
        assertEquals(3650, admin.attendanceRetentionMaxDays)
        val photos = admin.modules.single { it.key == "patron_photos" }
        assertEquals("patron_cards", photos.inheritsFrom)
        val fines = admin.modules.single { it.key == "automatic_fines" }
        assertEquals("fines", fines.parent)
        assertEquals("loans", fines.group)
        assertTrue(fines.circulation)
        assertEquals(setOf("modules", "loans"), admin.modules.map { it.group }.toSet())
        val staff = data("s090_library_modules_staff", Staff090LibraryModules.serializer())
        assertFalse(staff.canConfigure)
        assertEquals(admin.modules.map { it.key }, staff.modules.map { it.key })
        val saved = data("s090_library_modules_saved", Staff090LibraryModules.serializer())
        assertTrue(saved.modules.single { it.key == "suppliers" }.stored)
        assertEquals(400, saved.attendanceRetentionDays)
    }

    @Test fun loansKeepTypedValuesAndTheEditableFieldsPerRole() {
        val admin = data("s090_library_loans_admin", Staff090LibraryLoans.serializer())
        assertEquals("EUR", admin.currency)
        assertEquals(30, (admin.settings["loan_duration_days"] as JsonPrimitive).intOrNull)
        assertEquals("Europe/Rome", (admin.settings["app_timezone"] as JsonPrimitive).content)
        assertTrue("daily_fine_cents" in admin.settings)
        assertEquals(listOf("threshold", "deduct"), admin.options["fine_grace_mode"])
        assertEquals(999999L, admin.limits["money_max_cents"]?.max)
        assertTrue(admin.editableFields.containsAll(admin.adminOnlyFields))
        val staff = data("s090_library_loans_staff", Staff090LibraryLoans.serializer())
        assertFalse(staff.canConfigure)
        assertTrue(staff.adminOnlyFields.none { it in staff.editableFields })
        assertTrue("max_renewals" in staff.editableFields)
        val saved = data("s090_library_loans_saved", Staff090LibraryLoans.serializer())
        assertEquals(150, (saved.settings["daily_fine_cents"] as JsonPrimitive).intOrNull)
    }

    @Test fun cards() {
        val c = data("s090_library_cards_admin", Staff090LibraryCards.serializer())
        assertTrue(c.moduleEnabled)
        assertEquals("classic", c.settings.layout)
        assertEquals("code128", c.settings.codeType)
        assertTrue(c.settings.showPhoto)
        assertEquals(listOf("classic", "minimal", "portrait"), c.options["layout"])
        assertEquals(60, c.limits.titleMax)
        assertEquals("Tessera di prova", data("s090_library_cards_saved", Staff090LibraryCards.serializer()).settings.title)
    }

    @Test fun patronTypesWithGlobalsEffectiveAndLimits() {
        val t = data("s090_library_types_two", Staff090LibraryTypes.serializer())
        assertEquals(1, t.defaultTypeId)
        assertEquals(30, t.globals.loanDurationDays)
        assertNull(t.globals.renewalDays)
        assertEquals(2, t.types.size)
        val standard = t.types.single { it.isDefault }
        assertNull(standard.maxLoans)
        assertEquals(30, standard.effective?.renewalDays)
        val created = data("s090_library_type_created", Staff090LibraryPatronType.serializer())
        assertEquals("ricercatori_app", created.code)
        assertEquals(5, created.maxLoans)
        assertEquals(50, created.dailyFineCents)
        assertEquals(50, created.effective?.dailyFineCents)
        val updated = data("s090_library_type_updated", Staff090LibraryPatronType.serializer())
        assertNull(updated.maxLoans)
        assertEquals(60, updated.loanDurationDays)
        assertEquals(36500L, t.limits["card_duration_days"]?.max)
        val moved = data("s090_library_types_moved", Staff090LibraryTypes.serializer())
        assertEquals("Ricercatori app", moved.types.first().name)
        assertEquals(0, data("s090_library_type_deleted", Staff090LibraryTypeDeleted.serializer()).usersReassigned)
        assertFalse(data("s090_library_types_admin_module_off", Staff090LibraryTypes.serializer()).moduleEnabled)
    }

    @Test fun theReplayOfACreateSaysSo() {
        val env = json.decodeFromString(Envelope.serializer(Staff090LibraryPatronType.serializer()), raw("s090_library_type_replayed"))
        assertEquals(true, env.meta?.replayed)
    }

    @Test fun rulesMatrixCellsWithInheritedValues() {
        val r = data("s090_library_rules_saved", Staff090LibraryRules.serializer())
        assertEquals(listOf("libro", "disco", "audiolibro", "dvd", "altro"), r.media.map { it.key })
        assertEquals(0, r.rows.first().typeId)
        assertEquals(r.rows.size * r.media.size, r.cells.size)
        val dvd = r.cells.single { it.typeId == 0 && it.media == "dvd" }
        assertTrue(dvd.hasRule)
        assertEquals(false, dvd.loanable)
        assertEquals(7, dvd.loanDurationDays)
        assertTrue(dvd.inherited.loanable)
        assertEquals(30, dvd.inherited.loanDurationDays)
        val book = r.cells.single { it.typeId == 1 && it.media == "libro" }
        assertNull(book.loanable)
        assertEquals(25, book.dailyFineCents)
        assertTrue(data("s090_library_rules", Staff090LibraryRules.serializer()).cells.none { it.hasRule })
    }

    @Test fun calendarDaysReasonsClosuresAndCustomHolidays() {
        val c = data("s090_library_calendar_full", Staff090LibraryCalendar.serializer())
        assertEquals("2026-12", c.month)
        assertEquals("2026-11", c.prevMonth)
        assertEquals(31, c.days.size)
        assertEquals(listOf(0), c.closedWeekdays)
        val sunday = c.days.single { it.date == "2026-12-06" }
        assertEquals(0, sunday.weekday)
        assertEquals("weekly", sunday.reasons.single().type)
        assertNull(sunday.reasons.single().numericId)
        val christmas = c.days.single { it.date == "2026-12-25" }
        assertEquals("closure", christmas.reasons.single().type)
        assertEquals(c.closures.single { !it.yearly }.id, christmas.reasons.single().numericId)
        val yearly = c.closures.single { it.yearly }
        assertFalse(yearly.active)
        assertTrue(yearly.editable)
        assertEquals(3, c.customHolidays.size)
        val dates = c.customHolidays.single { it.kind == "dates" }
        assertEquals(listOf("2026-12-28", "2027-01-04"), dates.dates)
        val hebrew = c.customHolidays.single { it.kind == "calendar" }
        assertEquals("hebrew", hebrew.calendar)
        assertEquals(2, hebrew.durationDays)
        assertTrue(hebrew.nextDates.isNotEmpty())
        assertEquals(listOf("fixed", "nth_weekday", "easter", "orthodox_easter", "calendar", "dates"), c.customHolidayOptions.kinds)
        assertEquals(listOf(1, 2, 3, 4, 5, -1), c.customHolidayOptions.occurrences)
        assertEquals(13, c.customHolidayOptions.calendars.single { it.code == "hebrew" }.months.size)
        assertEquals(1_048_576, c.limits.icsMaxBytes)
        assertTrue(c.branches.isEmpty())
        assertEquals("IT", c.holidayPreset.country)
    }

    @Test fun aPresetHolidayClosureIsNotEditableAndHasATextRuleId() {
        val c = data("s090_library_calendar_january_preset", Staff090LibraryCalendar.serializer())
        val preset = c.closures.first { it.presetRule != null }
        assertFalse(preset.editable)
        assertTrue(c.holidayPreset.saved)
        val newYear = c.days.single { it.date == "2026-01-01" }
        val reason = newYear.reasons.first { it.type == "holiday" }
        assertNull(reason.numericId)   // a holiday rule id (text), not a closure row id
        assertEquals("new_year", (reason.id as JsonPrimitive).content)
    }

    @Test fun holidayPreviewAndPresetApplied() {
        val it = data("s090_library_countries_it", Staff090LibraryCountries.serializer())
        assertTrue(it.countries.size > 200)
        assertEquals(2027, it.year)
        assertEquals(1900L, it.years?.min)
        assertTrue(it.holidays.any { h -> h.id == "IT:new_year" && h.yearly })
        assertEquals(it.holidays, it.preset.holidays)
        val applied = data("s090_library_preset_applied", Staff090LibraryPresetApplied.serializer())
        assertTrue(applied.message.isNotBlank())
        assertTrue(applied.holidayPreset.saved)
    }

    @Test fun calendarWritesAnswerTheirResource() {
        val created = data("s090_library_closure_created", Staff090LibraryClosure.serializer())
        assertEquals("2026-12-24", created.startDate)
        assertEquals("2026-12-26", created.endDate)
        assertNull(created.branchId)
        assertEquals("Chiusura natalizia (prova app)", data("s090_library_closure_updated", Staff090LibraryClosure.serializer()).description)
        assertTrue(data("s090_library_closure_deleted", DeleteResult.serializer()).deleted)
        val yearly = data("s090_library_yearly_saved", Staff090LibraryYearlySaved.serializer())
        assertEquals(1, yearly.changed)
        assertFalse(yearly.yearlyClosures.single().active)
        assertEquals(listOf(0), data("s090_library_weekdays_saved", Staff090LibraryCalendar.serializer()).closedWeekdays)
        val custom = data("s090_library_custom_created", Staff090LibraryCustomHoliday.serializer())
        assertEquals("fixed", custom.kind)
        assertEquals(12, custom.month)
        assertEquals(7, custom.day)
        assertFalse(data("s090_library_custom_toggled", Staff090LibraryCustomHoliday.serializer()).active)
        val updated = data("s090_library_custom_updated", Staff090LibraryCustomHoliday.serializer())
        assertEquals("nth_weekday", updated.kind)
        assertEquals(-1, updated.occurrence)
        assertEquals(1, updated.weekday)
        val ics = data("s090_library_ics_imported", Staff090LibraryIcsImported.serializer())
        assertEquals(1, ics.added)
        assertEquals(1, ics.addedIds.size)
    }
}
