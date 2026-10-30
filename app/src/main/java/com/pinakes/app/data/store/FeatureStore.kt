package com.pinakes.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pinakes.app.data.model.HealthPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Instance feature flags that gate the UI for CATALOGUE-ONLY MODE.
 *
 * Sourced from `/health` (`features` + `catalogue_mode`) and persisted so they survive process
 * death and remain available app-wide. Exposed as a reactive [StateFlow] so the navigation and
 * screens recompose when the server-side mode changes.
 *
 * Robustness: when the flags have never been fetched (first run, `/health` unreachable) the
 * defaults keep the operational app features enabled — the app must never lock the user out
 * because discovery failed. Public registration is different: it stays hidden until `/health`
 * explicitly advertises `registration_enabled=true`.
 */
data class InstanceFeatures(
    val catalogueMode: Boolean = false,
    val catalog: Boolean = true,
    val loans: Boolean = true,
    val reservations: Boolean = true,
    val wishlist: Boolean = true,
    val messages: Boolean = true,
    val notifications: Boolean = true,
    val push: Boolean = true,
    val reviews: Boolean = true,
    val registrationEnabled: Boolean = false,
    /**
     * Whether the instance exposes the optional Book Club plugin's mobile surface.
     * Unlike the flags above it is NOT sourced from `/health` but from a separate
     * `GET /api/v1/bookclub/health` probe (2xx → on, 404 → off), and like
     * [registrationEnabled] it stays hidden until discovery confirms it.
     */
    val bookClubAvailable: Boolean = false,
    /**
     * Whether the instance exposes the optional Periodicals ("Emeroteca") plugin's mobile
     * surface. Same lifecycle as [bookClubAvailable]: sourced from a separate
     * `GET /api/v1/periodicals/health` probe (2xx → on, 404 → off), hidden until confirmed.
     */
    val periodicalsAvailable: Boolean = false,
    val archivesAvailable: Boolean = false,
    val desiderataAvailable: Boolean = false,
    /** Staff content management (Mobile API 1.6.0 `staff_catalog`); hidden until advertised. */
    val staffCatalogAvailable: Boolean = false,
    /** Delta catalogue sync (`catalog_sync`); without it the app keeps the first-page snapshot. */
    val catalogSyncAvailable: Boolean = false,
    /** Personal loans ICS feed (`calendar_feed`, false in catalogue mode). */
    val calendarFeedAvailable: Boolean = false,
    // Mobile API 1.7.0 staff areas (`staff_*` in `/health`); hidden until advertised.
    // Circulation is also off in catalogue mode, whatever the server sends.
    val staffCirculationAvailable: Boolean = false,
    val staffCopiesAvailable: Boolean = false,
    val staffSeriesAvailable: Boolean = false,
    val staffMergesAvailable: Boolean = false,
    val staffLabelsAvailable: Boolean = false,
    val staffImportsAvailable: Boolean = false,
    val staffCoversAvailable: Boolean = false,
    val staffDesiderataAvailable: Boolean = false,
    val staffArchivesAvailable: Boolean = false,
    val staffPeriodicalsAvailable: Boolean = false,
    val staffBookClubAvailable: Boolean = false,
    val staffFrbrAvailable: Boolean = false,
    val staffDigitalLibraryAvailable: Boolean = false,
    val staffLibraryThingAvailable: Boolean = false,
    /**
     * Pinakes 0.9.0 toggles (`features.fines`, `closures_calendar`, `circulation_rules`). Hidden
     * until a server advertises them: an older server has no ledger, no closures calendar and
     * no patron-type rules, and the app then behaves exactly as before.
     */
    val finesAvailable: Boolean = false,
    /**
     * Informational: the calendar honours `closed_dates` whatever this says, because the server
     * already sends an empty list with the toggle off, and a Mobile API 1.9.0 server sends closures
     * before the flag existed (ignoring them would offer days the server then moves).
     */
    val closuresCalendarAvailable: Boolean = false,
    val circulationRulesAvailable: Boolean = false,
    /** `features.consultation_only`: consultation-only copies apply on their own switch. */
    val consultationOnlyAvailable: Boolean = false,
) {
    /** Library tab (loans + reservations) is shown only when at least one of them is enabled. */
    val showLibrary: Boolean get() = loans || reservations

    /** Any borrow/reserve action available at all. */
    val canBorrow: Boolean get() = loans || reservations

    val showWishlist: Boolean get() = wishlist

    /** Book reviews (read + write) surfaced on book detail and the "My reviews" page. */
    val showReviews: Boolean get() = reviews

    /**
     * Whether the book page asks the server up front if the reader may borrow or join the
     * waitlist: patron-type rules close the waitlist, and consultation-only copies close the
     * loan, each on its own switch since Pinakes 0.9.0.
     */
    val viewerRulesAvailable: Boolean get() = circulationRulesAvailable || consultationOnlyAvailable

    /** The balance and movements page (fines ledger on, loans offered). */
    val showMovements: Boolean get() = finesAvailable && loans && !catalogueMode

    companion object {
        /** Safe default before/without `/health`: app features enabled, registration hidden. */
        val AllEnabled = InstanceFeatures()

        /**
         * The flags a `/health` payload grants. Pure, so the mapping is unit-tested without the
         * encrypted preferences; the plugin probes ([bookClub], [periodicals]) keep their own value.
         */
        fun from(health: HealthPayload, bookClub: Boolean = false, periodicals: Boolean = false): InstanceFeatures {
            val f = health.features
            val app = health.appAccessEnabled
            val circulation = !health.catalogueMode
            return InstanceFeatures(
                catalogueMode = health.catalogueMode,
                catalog = f.catalog,
                loans = f.loans,
                reservations = f.reservations,
                wishlist = f.wishlist,
                messages = f.messages,
                notifications = f.notifications,
                push = f.push,
                reviews = f.reviews,
                registrationEnabled = health.registrationEnabled,
                bookClubAvailable = bookClub,
                periodicalsAvailable = periodicals,
                archivesAvailable = f.archives && app,
                desiderataAvailable = f.desiderata && app,
                staffCatalogAvailable = f.staffCatalog && app,
                catalogSyncAvailable = f.catalogSync && app,
                calendarFeedAvailable = f.calendarFeed && app && circulation,
                staffCirculationAvailable = f.staffCirculation && app && circulation,
                staffCopiesAvailable = f.staffCopies && app,
                staffSeriesAvailable = f.staffSeries && app,
                staffMergesAvailable = f.staffMerges && app,
                staffLabelsAvailable = f.staffLabels && app,
                staffImportsAvailable = f.staffImports && app,
                staffCoversAvailable = f.staffCovers && app,
                staffDesiderataAvailable = f.staffDesiderata && app,
                staffArchivesAvailable = f.staffArchives && app,
                staffPeriodicalsAvailable = f.staffPeriodicals && app,
                staffBookClubAvailable = f.staffBookClub && app,
                staffFrbrAvailable = f.staffFrbr && app,
                staffDigitalLibraryAvailable = f.staffDigitalLibrary && app,
                staffLibraryThingAvailable = f.staffLibraryThing && app,
                // The server already turns these off in catalogue mode; the app does too, so a
                // stale or inconsistent payload can never show circulation in a read-only catalogue.
                finesAvailable = f.fines && app && circulation,
                closuresCalendarAvailable = f.closuresCalendar && app && circulation,
                circulationRulesAvailable = f.circulationRules && app && circulation,
                consultationOnlyAvailable = f.consultationOnly && app && circulation,
            )
        }
    }
}

class FeatureStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _features = MutableStateFlow(read())
    val features: StateFlow<InstanceFeatures> = _features.asStateFlow()

    /**
     * Persist + publish the flags from a fresh `/health` payload. The Book Club and
     * Periodicals flags have their own probe lifecycle ([setBookClubAvailable],
     * [setPeriodicalsAvailable]) and are preserved, not clobbered.
     */
    fun update(health: HealthPayload) {
        val value = InstanceFeatures.from(
            health,
            bookClub = prefs.getBoolean(KEY_BOOK_CLUB, false),
            periodicals = prefs.getBoolean(KEY_PERIODICALS, false),
        )
        prefs.edit()
            .putBoolean(KEY_KNOWN, true)
            .putBoolean(KEY_CATALOGUE_MODE, value.catalogueMode)
            .putBoolean(KEY_CATALOG, value.catalog)
            .putBoolean(KEY_LOANS, value.loans)
            .putBoolean(KEY_RESERVATIONS, value.reservations)
            .putBoolean(KEY_WISHLIST, value.wishlist)
            .putBoolean(KEY_MESSAGES, value.messages)
            .putBoolean(KEY_NOTIFICATIONS, value.notifications)
            .putBoolean(KEY_PUSH, value.push)
            .putBoolean(KEY_REVIEWS, value.reviews)
            .putBoolean(KEY_REGISTRATION_ENABLED, value.registrationEnabled)
            .putBoolean("archives", value.archivesAvailable)
            .putBoolean("desiderata", value.desiderataAvailable)
            .putBoolean(KEY_STAFF_CATALOG, value.staffCatalogAvailable)
            .putBoolean(KEY_CATALOG_SYNC, value.catalogSyncAvailable)
            .putBoolean(KEY_CALENDAR_FEED, value.calendarFeedAvailable)
            .putBoolean(KEY_STAFF_CIRCULATION, value.staffCirculationAvailable)
            .putBoolean(KEY_STAFF_COPIES, value.staffCopiesAvailable)
            .putBoolean(KEY_STAFF_SERIES, value.staffSeriesAvailable)
            .putBoolean(KEY_STAFF_MERGES, value.staffMergesAvailable)
            .putBoolean(KEY_STAFF_LABELS, value.staffLabelsAvailable)
            .putBoolean(KEY_STAFF_IMPORTS, value.staffImportsAvailable)
            .putBoolean(KEY_STAFF_COVERS, value.staffCoversAvailable)
            .putBoolean(KEY_STAFF_DESIDERATA, value.staffDesiderataAvailable)
            .putBoolean(KEY_STAFF_ARCHIVES, value.staffArchivesAvailable)
            .putBoolean(KEY_STAFF_PERIODICALS, value.staffPeriodicalsAvailable)
            .putBoolean(KEY_STAFF_BOOKCLUB, value.staffBookClubAvailable)
            .putBoolean(KEY_STAFF_FRBR, value.staffFrbrAvailable)
            .putBoolean(KEY_STAFF_DIGITAL, value.staffDigitalLibraryAvailable)
            .putBoolean(KEY_STAFF_LIBRARYTHING, value.staffLibraryThingAvailable)
            .putBoolean(KEY_FINES, value.finesAvailable)
            .putBoolean(KEY_CLOSURES_CALENDAR, value.closuresCalendarAvailable)
            .putBoolean(KEY_CIRCULATION_RULES, value.circulationRulesAvailable)
            .putBoolean(KEY_CONSULTATION_ONLY, value.consultationOnlyAvailable)
            .apply()
        _features.value = value
    }

    /** Persist + publish the Book Club plugin availability (from its own health probe). */
    fun setBookClubAvailable(available: Boolean) {
        prefs.edit().putBoolean(KEY_BOOK_CLUB, available).apply()
        _features.update { it.copy(bookClubAvailable = available) }
    }

    /** Persist + publish the Periodicals plugin availability (from its own health probe). */
    fun setPeriodicalsAvailable(available: Boolean) {
        prefs.edit().putBoolean(KEY_PERIODICALS, available).apply()
        _features.update { it.copy(periodicalsAvailable = available) }
    }

    /** Reset to all-enabled (e.g. when forgetting the instance). */
    fun clear() {
        prefs.edit().clear().apply()
        _features.value = InstanceFeatures.AllEnabled
    }

    /** True once a real `/health` has been observed at least once. */
    fun isKnown(): Boolean = prefs.getBoolean(KEY_KNOWN, false)

    private fun read(): InstanceFeatures {
        if (!prefs.getBoolean(KEY_KNOWN, false)) return InstanceFeatures.AllEnabled
        return InstanceFeatures(
            catalogueMode = prefs.getBoolean(KEY_CATALOGUE_MODE, false),
            catalog = prefs.getBoolean(KEY_CATALOG, true),
            loans = prefs.getBoolean(KEY_LOANS, true),
            reservations = prefs.getBoolean(KEY_RESERVATIONS, true),
            wishlist = prefs.getBoolean(KEY_WISHLIST, true),
            messages = prefs.getBoolean(KEY_MESSAGES, true),
            notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            push = prefs.getBoolean(KEY_PUSH, true),
            reviews = prefs.getBoolean(KEY_REVIEWS, true),
            registrationEnabled = prefs.getBoolean(KEY_REGISTRATION_ENABLED, false),
            bookClubAvailable = prefs.getBoolean(KEY_BOOK_CLUB, false),
            periodicalsAvailable = prefs.getBoolean(KEY_PERIODICALS, false),
            archivesAvailable = prefs.getBoolean("archives", false),
            desiderataAvailable = prefs.getBoolean("desiderata", false),
            staffCatalogAvailable = prefs.getBoolean(KEY_STAFF_CATALOG, false),
            catalogSyncAvailable = prefs.getBoolean(KEY_CATALOG_SYNC, false),
            calendarFeedAvailable = prefs.getBoolean(KEY_CALENDAR_FEED, false),
            staffCirculationAvailable = prefs.getBoolean(KEY_STAFF_CIRCULATION, false),
            staffCopiesAvailable = prefs.getBoolean(KEY_STAFF_COPIES, false),
            staffSeriesAvailable = prefs.getBoolean(KEY_STAFF_SERIES, false),
            staffMergesAvailable = prefs.getBoolean(KEY_STAFF_MERGES, false),
            staffLabelsAvailable = prefs.getBoolean(KEY_STAFF_LABELS, false),
            staffImportsAvailable = prefs.getBoolean(KEY_STAFF_IMPORTS, false),
            staffCoversAvailable = prefs.getBoolean(KEY_STAFF_COVERS, false),
            staffDesiderataAvailable = prefs.getBoolean(KEY_STAFF_DESIDERATA, false),
            staffArchivesAvailable = prefs.getBoolean(KEY_STAFF_ARCHIVES, false),
            staffPeriodicalsAvailable = prefs.getBoolean(KEY_STAFF_PERIODICALS, false),
            staffBookClubAvailable = prefs.getBoolean(KEY_STAFF_BOOKCLUB, false),
            staffFrbrAvailable = prefs.getBoolean(KEY_STAFF_FRBR, false),
            staffDigitalLibraryAvailable = prefs.getBoolean(KEY_STAFF_DIGITAL, false),
            staffLibraryThingAvailable = prefs.getBoolean(KEY_STAFF_LIBRARYTHING, false),
            finesAvailable = prefs.getBoolean(KEY_FINES, false),
            closuresCalendarAvailable = prefs.getBoolean(KEY_CLOSURES_CALENDAR, false),
            circulationRulesAvailable = prefs.getBoolean(KEY_CIRCULATION_RULES, false),
            consultationOnlyAvailable = prefs.getBoolean(KEY_CONSULTATION_ONLY, false),
        )
    }

    companion object {
        private const val FILE_NAME = "pinakes_features_secure"
        private const val KEY_KNOWN = "known"
        private const val KEY_CATALOGUE_MODE = "catalogue_mode"
        private const val KEY_CATALOG = "f_catalog"
        private const val KEY_LOANS = "f_loans"
        private const val KEY_RESERVATIONS = "f_reservations"
        private const val KEY_WISHLIST = "f_wishlist"
        private const val KEY_MESSAGES = "f_messages"
        private const val KEY_NOTIFICATIONS = "f_notifications"
        private const val KEY_PUSH = "f_push"
        private const val KEY_REVIEWS = "f_reviews"
        private const val KEY_REGISTRATION_ENABLED = "registration_enabled"
        private const val KEY_BOOK_CLUB = "f_book_club"
        private const val KEY_PERIODICALS = "f_periodicals"
        private const val KEY_STAFF_CATALOG = "f_staff_catalog"
        private const val KEY_CATALOG_SYNC = "f_catalog_sync"
        private const val KEY_CALENDAR_FEED = "f_calendar_feed"
        private const val KEY_STAFF_CIRCULATION = "f_staff_circulation"
        private const val KEY_STAFF_COPIES = "f_staff_copies"
        private const val KEY_STAFF_SERIES = "f_staff_series"
        private const val KEY_STAFF_MERGES = "f_staff_merges"
        private const val KEY_STAFF_LABELS = "f_staff_labels"
        private const val KEY_STAFF_IMPORTS = "f_staff_imports"
        private const val KEY_STAFF_COVERS = "f_staff_covers"
        private const val KEY_STAFF_DESIDERATA = "f_staff_desiderata"
        private const val KEY_STAFF_ARCHIVES = "f_staff_archives"
        private const val KEY_STAFF_PERIODICALS = "f_staff_periodicals"
        private const val KEY_STAFF_BOOKCLUB = "f_staff_bookclub"
        private const val KEY_STAFF_FRBR = "f_staff_frbr"
        private const val KEY_STAFF_DIGITAL = "f_staff_digital_library"
        private const val KEY_STAFF_LIBRARYTHING = "f_staff_librarything"
        private const val KEY_FINES = "f_fines"
        private const val KEY_CLOSURES_CALENDAR = "f_closures_calendar"
        private const val KEY_CIRCULATION_RULES = "f_circulation_rules"
        private const val KEY_CONSULTATION_ONLY = "f_consultation_only"
    }
}
