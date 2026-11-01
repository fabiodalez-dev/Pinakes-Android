package com.pinakes.app.server090

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090LibraryCalendar
import com.pinakes.app.data.model.Staff090LibraryCardSettings
import com.pinakes.app.data.model.Staff090LibraryClosure
import com.pinakes.app.data.model.Staff090LibraryCustomHoliday
import com.pinakes.app.data.model.Staff090LibraryDay
import com.pinakes.app.data.model.Staff090LibraryLoans
import com.pinakes.app.data.model.Staff090LibraryModules
import com.pinakes.app.data.model.Staff090LibraryRange
import com.pinakes.app.data.model.Staff090LibraryRules
import com.pinakes.app.data.model.Staff090LibraryTypes
import com.pinakes.app.ui.screens.staff.s090.library.CalendarGrid
import com.pinakes.app.ui.screens.staff.s090.library.CardsForm
import com.pinakes.app.ui.screens.staff.s090.library.CellForm
import com.pinakes.app.ui.screens.staff.s090.library.CentsInput
import com.pinakes.app.ui.screens.staff.s090.library.ClosureForm
import com.pinakes.app.ui.screens.staff.s090.library.CustomField
import com.pinakes.app.ui.screens.staff.s090.library.CustomForm
import com.pinakes.app.ui.screens.staff.s090.library.CustomForms
import com.pinakes.app.ui.screens.staff.s090.library.IntInput
import com.pinakes.app.ui.screens.staff.s090.library.LibraryMoney
import com.pinakes.app.ui.screens.staff.s090.library.LibrarySettingsUiState
import com.pinakes.app.ui.screens.staff.s090.library.LoansForm
import com.pinakes.app.ui.screens.staff.s090.library.ModulesForm
import com.pinakes.app.ui.screens.staff.s090.library.RuleNumber
import com.pinakes.app.ui.screens.staff.s090.library.RulesForm
import com.pinakes.app.ui.screens.staff.s090.library.SettingsTab
import com.pinakes.app.ui.screens.staff.s090.library.TypeForm
import com.pinakes.app.ui.screens.staff.s090.library.TypeForms
import com.pinakes.app.ui.screens.staff.s090.library.TypeNumber
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The pure side of the library settings screens: amounts, partial bodies, validation, grid. */
class Staff090LibraryFormsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }
    private val it = Locale.ITALY

    private fun <T> data(name: String, serializer: KSerializer<T>): T =
        json.decodeFromString(Envelope.serializer(serializer), javaClass.getResourceAsStream("/server090/library/$name.json")!!.bufferedReader().readText()).data!!

    // ---------- money ----------

    @Test fun amountsParseToExactCents() {
        assertEquals(CentsInput.Valid(150), LibraryMoney.parse("1.50"))
        assertEquals(CentsInput.Valid(150), LibraryMoney.parse("1,5"))
        assertEquals(CentsInput.Valid(200), LibraryMoney.parse(" 2 "))
        assertEquals(CentsInput.Valid(29), LibraryMoney.parse("0,29"))      // 0.29 * 100 is 28.999… as a double
        assertEquals(CentsInput.Valid(999999), LibraryMoney.parse("9999.99", max = 999999))
        assertEquals(CentsInput.Invalid, LibraryMoney.parse("10000", max = 999999))
        assertEquals(CentsInput.Empty, LibraryMoney.parse(""))
        listOf("1.234", "-1", "1.000,50", "abc", "1,", ",5").forEach { assertEquals(it, CentsInput.Invalid, LibraryMoney.parse(it)) }
    }

    @Test fun amountsStartFromTheStoredCentsInTheLocaleSeparator() {
        assertEquals("1,50", LibraryMoney.input(150, it))
        assertEquals("1.50", LibraryMoney.input(150, Locale.US))
        assertEquals("0,05", LibraryMoney.input(5, it))
        assertEquals("", LibraryMoney.input(null, it))
        // What the field shows parses back to the same cents.
        listOf(0, 1, 99, 150, 999999).forEach { c -> assertEquals(CentsInput.Valid(c), LibraryMoney.parse(LibraryMoney.input(c, it))) }
    }

    @Test fun wholeNumbersRespectTheServerRange() {
        val r = Staff090LibraryRange(1, 30)
        assertEquals(IntInput.Valid(3), IntInput.parse("3", r))
        assertEquals(IntInput.Invalid, IntInput.parse("0", r))
        assertEquals(IntInput.Invalid, IntInput.parse("31", r))
        assertEquals(IntInput.Empty, IntInput.parse(" ", r))
        assertEquals(IntInput.Invalid, IntInput.parse("-2", null))
        assertEquals(IntInput.Valid(-2), IntInput.parse("-2", Staff090LibraryRange(-200, 200), allowNegative = true))
    }

    // ---------- modules ----------

    @Test fun modulesSendOnlyTheChangedSwitches() {
        val m = data("s090_library_modules_admin", Staff090LibraryModules.serializer())
        val original = m.modules.associate { x -> x.key to x.stored }
        assertNull(ModulesForm.body(original, original, 365, "365"))
        val body = ModulesForm.body(original, original + ("suppliers" to true), 365, "365")!!
        assertEquals("""{"features":{"suppliers":true}}""", body.toString())
        assertEquals("""{"attendance_retention_days":400}""", ModulesForm.body(original, original, 365, "400").toString())
    }

    @Test fun moduleNotesNameTheParentAndCatalogueMode() {
        val m = data("s090_library_modules_admin", Staff090LibraryModules.serializer())
        val fines = m.modules.single { x -> x.key == "automatic_fines" }
        val notes = ModulesForm.notes(fines, m.modules, catalogueMode = true)
        assertEquals(ModulesForm.Note.NeedsParent("Registro movimenti", parentOn = false), notes[0])
        assertTrue(ModulesForm.Note.CatalogueMode in notes)
        // inherits_from equal to the parent is said once.
        assertEquals(2, notes.size)
        val photos = ModulesForm.notes(m.modules.single { x -> x.key == "patron_photos" }, m.modules, catalogueMode = false)
        assertEquals(listOf(ModulesForm.Note.Follows("Stampa delle tessere")), photos)
    }

    @Test fun theSaveActionIsHiddenWithoutPermission() {
        val staffModules = data("s090_library_modules_staff", Staff090LibraryModules.serializer())
        val staffLoans = data("s090_library_loans_staff", Staff090LibraryLoans.serializer())
        val s = LibrarySettingsUiState(modules = staffModules, loans = staffLoans, loading = false)
        assertFalse(s.copy(tab = SettingsTab.MODULES).canSave)
        assertTrue(s.copy(tab = SettingsTab.LOANS).canSave)        // Staff saves the non-money loan fields
        assertEquals(listOf(SettingsTab.MODULES, SettingsTab.LOANS), s.tabs)   // no cards: 404 for Staff with the module off
    }

    // ---------- loans ----------

    @Test fun loansSendTheChangedEditableFieldsTyped() {
        val l = data("s090_library_loans_admin", Staff090LibraryLoans.serializer())
        val original = LoansForm.initial(l, it)
        assertEquals("30", original["loan_duration_days"])
        assertEquals("false", original["auto_approve_requests"])
        assertEquals("", original["daily_fine_cents"])
        assertEquals("Europe/Rome", original["app_timezone"])
        assertNull(LoansForm.body(l, original, original))
        val edited = original + mapOf("recall_interval_days" to "8", "daily_fine_cents" to "1,50", "auto_approve_requests" to "true")
        assertEquals("""{"auto_approve_requests":true,"recall_interval_days":8,"daily_fine_cents":150}""", LoansForm.body(l, original, edited).toString())
        // Clearing an amount sends null (no amount).
        val saved = data("s090_library_loans_saved", Staff090LibraryLoans.serializer())
        val withFine = LoansForm.initial(saved, it)
        assertEquals("1,50", withFine["daily_fine_cents"])
        assertEquals("""{"daily_fine_cents":null}""", LoansForm.body(saved, withFine, withFine + ("daily_fine_cents" to "")).toString())
    }

    @Test fun staffNeverSendsAnAdministratorOnlyField() {
        val l = data("s090_library_loans_staff", Staff090LibraryLoans.serializer())
        val original = LoansForm.initial(l, it)
        val edited = original + mapOf("daily_fine_cents" to "1", "max_renewals" to "4")
        assertEquals("""{"max_renewals":4}""", LoansForm.body(l, original, edited).toString())
        assertFalse(LoansForm.editable(l, "fine_grace_mode"))
    }

    @Test fun loanValidationUsesTheServerLimits() {
        val l = data("s090_library_loans_admin", Staff090LibraryLoans.serializer())
        val values = LoansForm.initial(l, it)
        assertTrue(LoansForm.invalid(l, values).isEmpty())
        val bad = LoansForm.invalid(l, values + mapOf("pickup_expiry_days" to "31", "loan_duration_days" to "", "daily_fine_cents" to "abc", "fine_grace_mode" to "x"))
        assertEquals(setOf("pickup_expiry_days", "loan_duration_days", "daily_fine_cents", "fine_grace_mode"), bad)
        assertTrue(LoansForm.invalid(l, values + ("daily_fine_cents" to "10000,00")).contains("daily_fine_cents"))
    }

    // ---------- cards ----------

    @Test fun cardsSendTheChangedSettings() {
        val o = Staff090LibraryCardSettings(layout = "classic", codeType = "code128", format = "single", title = "", backText = "", showPhoto = true, showLogo = true, showExpiry = true)
        assertNull(CardsForm.body(o, o))
        assertNull(CardsForm.body(o, o.copy(title = "  ")))
        assertEquals("""{"code_type":"qr","title":"Tessera","show_logo":false}""", CardsForm.body(o, o.copy(codeType = "qr", title = " Tessera ", showLogo = false)).toString())
    }

    // ---------- patron types ----------

    @Test fun aNewTypeSendsWhatWasFilledAndLeavesTheRestToInherit() {
        val form = TypeForm(name = " Ricercatori ", code = "ricercatori", numbers = mapOf(TypeNumber.MAX_LOANS to "5", TypeNumber.DAILY_FINE to "0,50", TypeNumber.GRACE_DAYS to ""))
        assertEquals(
            """{"name":"Ricercatori","code":"ricercatori","reservations_enabled":true,"active":true,"max_loans":5,"daily_fine_cents":50}""",
            TypeForms.createBody(form).toString(),
        )
    }

    @Test fun anEditSendsOnlyTheChangesAndNullToInheritAgain() {
        val t = data("s090_library_type_created", com.pinakes.app.data.model.Staff090LibraryPatronType.serializer())
        val original = TypeForm.from(t, it)
        assertEquals("5", original.text(TypeNumber.MAX_LOANS))
        assertEquals("0,50", original.text(TypeNumber.DAILY_FINE))
        assertNull(TypeForms.updateBody(original, original))
        val edited = original.copy(numbers = original.numbers + (TypeNumber.MAX_LOANS to "") + (TypeNumber.LOAN_DURATION to "60"))
        assertEquals("""{"max_loans":null,"loan_duration_days":60}""", TypeForms.updateBody(original, edited).toString())
        assertEquals("""{"is_default":true}""", TypeForms.updateBody(original, original.copy(isDefault = true)).toString())
    }

    @Test fun typeValidationAndInheritedHints() {
        val types = data("s090_library_types_two", Staff090LibraryTypes.serializer())
        assertEquals(setOf("name", "code", "max_loans", "daily_fine_cents"),
            TypeForms.invalid(TypeForm(name = "", code = "Bad Code", numbers = mapOf(TypeNumber.MAX_LOANS to "1001", TypeNumber.DAILY_FINE to "1.234")), types.limits))
        assertTrue(TypeForms.invalid(TypeForm(name = "Ok", code = ""), types.limits).isEmpty())
        // An empty renewal period inherits the effective one (the loan duration), not the empty global.
        val standard = types.types.single { x -> x.isDefault }
        assertEquals(30, TypeForms.inherited(TypeNumber.RENEWAL_DAYS, standard, types.globals))
        assertNull(TypeForms.inherited(TypeNumber.RENEWAL_DAYS, null, types.globals))
        assertEquals(30, TypeForms.inherited(TypeNumber.LOAN_DURATION, null, types.globals))
    }

    // ---------- rules matrix ----------

    @Test fun onlyChangedCellsAndOnlyTheirChangedValuesAreSent() {
        val r = data("s090_library_rules_saved", Staff090LibraryRules.serializer())
        val dvd = r.cells.single { c -> c.typeId == 0 && c.media == "dvd" }
        val book = r.cells.single { c -> c.typeId == 1 && c.media == "libro" }
        val dvdForm = CellForm.from(dvd, it)
        assertEquals(false, dvdForm.loanable)
        assertEquals("7", dvdForm.text(RuleNumber.LOAN_DURATION))
        assertNull(RulesForm.cellBody(dvd, dvdForm, it))
        val edits = mapOf(
            RulesForm.key(0, "dvd") to dvdForm.copy(loanable = null, numbers = dvdForm.numbers + (RuleNumber.LOAN_DURATION to "")),
            RulesForm.key(1, "libro") to CellForm.from(book, it),   // opened, not changed
            RulesForm.key(0, "altro") to CellForm(numbers = mapOf(RuleNumber.MAX_FINE to "2")),
        )
        assertEquals(
            """[{"type_id":0,"media":"dvd","loanable":null,"loan_duration_days":null},{"type_id":0,"media":"altro","max_fine_per_loan_cents":200}]""",
            RulesForm.body(r.cells, edits, it).toString(),
        )
        assertTrue(RulesForm.customised(dvd))
        assertFalse(RulesForm.customised(r.cells.single { c -> c.typeId == 0 && c.media == "libro" }))
        assertEquals(setOf("max_loans", "daily_fine_cents"), RulesForm.invalid(CellForm(numbers = mapOf(RuleNumber.MAX_LOANS to "99999", RuleNumber.DAILY_FINE to "x")), r.limits))
    }

    // ---------- calendar ----------

    @Test fun theMonthGridStartsOnMonday() {
        val c = data("s090_library_calendar_full", Staff090LibraryCalendar.serializer())
        val weeks = CalendarGrid.weeks(c.days)
        // 1 December 2026 is a Tuesday: one blank Monday first.
        assertNull(weeks.first()[0])
        assertEquals("2026-12-01", weeks.first()[1]?.date)
        assertTrue(weeks.all { w -> w.size == 7 })
        assertEquals(31, weeks.flatten().count { d -> d != null })
        assertEquals("2026-12-06", weeks.first()[6]?.date)    // Sunday closes the row
        assertTrue(CalendarGrid.weeks(emptyList<Staff090LibraryDay>()).isEmpty())
        assertEquals("2027-01", CalendarGrid.shift("2026-12", 1))
        assertEquals("2026-11", CalendarGrid.shift("2026-12", -1))
    }

    @Test fun yearlyTogglesExcludePresetHolidaysAndOneWeekdayStaysOpen() {
        val c = data("s090_library_calendar_january_preset", Staff090LibraryCalendar.serializer())
        assertTrue(CalendarGrid.yearly(c.closures).none { x -> x.presetRule != null })
        assertTrue(CalendarGrid.weekdaysValid(setOf(0, 6)))
        assertFalse(CalendarGrid.weekdaysValid((0..6).toSet()))
    }

    @Test fun aClosureFormIsTheWholeClosure() {
        val f = ClosureForm(startDate = "2026-12-24", endDate = "2026-12-26", description = " Natale ")
        assertTrue(f.valid)
        assertEquals("""{"start_date":"2026-12-24","end_date":"2026-12-26","description":"Natale","yearly":false,"branch_id":null}""", f.body().toString())
        assertFalse(f.copy(endDate = "2026-12-23").valid)
        assertFalse(ClosureForm().valid)
        val single = ClosureForm.from(Staff090LibraryClosure(id = 3, startDate = "2026-08-15", endDate = "2026-08-15", yearly = true))
        assertNull(single.endDate)    // a one-day closure edits with an empty last day
    }

    @Test fun eachHolidayKindShowsAndSendsItsOwnFields() {
        assertEquals(setOf(CustomField.MONTH, CustomField.DAY), CustomForms.fields("fixed"))
        assertEquals(setOf(CustomField.OCCURRENCE, CustomField.WEEKDAY, CustomField.MONTH), CustomForms.fields("nth_weekday"))
        assertEquals(setOf(CustomField.OFFSET), CustomForms.fields("orthodox_easter"))
        assertEquals(setOf(CustomField.CALENDAR, CustomField.MONTH, CustomField.DAY), CustomForms.fields("calendar"))
        assertEquals(setOf(CustomField.DATES), CustomForms.fields("dates"))
        val nth = CustomForm(name = "Ultimo lunedì", kind = "nth_weekday", month = "11", weekday = 1, occurrence = -1, day = "9")
        val body = CustomForms.body(nth).toString()
        assertTrue(body, body.contains(""""month":11,"day":null,"weekday":1,"occurrence":-1,"offset_days":null,"calendar":null,"dates":[]"""))
        val dates = CustomForm(name = "Ponti", kind = "dates", dates = "2026-12-28\n2027-01-04, ")
        assertTrue(CustomForms.body(dates).toString().contains(""""dates":["2026-12-28","2027-01-04"]"""))
    }

    @Test fun holidayValidation() {
        assertTrue(CustomForms.invalid(CustomForm(name = "Patrono", kind = "fixed", month = "12", day = "7"), 14, 400, 12).isEmpty())
        assertEquals(setOf("name", "day"), CustomForms.invalid(CustomForm(name = " ", kind = "fixed", month = "2", day = "32"), 14, 400, 12))
        assertEquals(setOf("offset_days"), CustomForms.invalid(CustomForm(name = "x", kind = "easter", offset = "201"), 14, 400, 12))
        assertTrue(CustomForms.invalid(CustomForm(name = "x", kind = "easter", offset = "-2"), 14, 400, 12).isEmpty())
        assertEquals(setOf("dates"), CustomForms.invalid(CustomForm(name = "x", kind = "dates", dates = "2026-02-30"), 14, 400, 12))
        assertEquals(setOf("duration_days", "year_until"), CustomForms.invalid(CustomForm(name = "x", duration = "15", yearFrom = "2030", yearUntil = "2029"), 14, 400, 12))
        // The Hebrew calendar has a 13th month; the Gregorian one does not.
        assertTrue(CustomForms.invalid(CustomForm(name = "x", kind = "calendar", calendar = "hebrew", month = "13", day = "1"), 14, 400, 13).isEmpty())
        assertEquals(setOf("month"), CustomForms.invalid(CustomForm(name = "x", kind = "fixed", month = "13", day = "1"), 14, 400, 13))
    }

    @Test fun anExistingHolidayReopensWithItsValues() {
        val h = data("s090_library_custom_updated", Staff090LibraryCustomHoliday.serializer())
        val f = CustomForm.from(h)
        assertEquals("nth_weekday", f.kind)
        assertEquals("11", f.month)
        assertEquals(-1, f.occurrence)
        assertTrue(CustomForms.invalid(f, 14, 400, 12).isEmpty())
    }
}
