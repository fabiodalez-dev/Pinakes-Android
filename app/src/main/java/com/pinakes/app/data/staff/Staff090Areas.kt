package com.pinakes.app.data.staff

import com.pinakes.app.data.store.InstanceFeatures

/**
 * Which Pinakes 0.9.0 staff tools (Mobile API 1.10.0) the app shows. Each one needs the staff
 * area, the server's `staff_<area>` flag and a connection: they all act on the server, so
 * offline they are hidden, never shown disabled. Whether the user may change a setting is the
 * server's answer (`can_configure`, `can_edit`, ...); [admin] only hides the screens an administrator
 * alone can use. Pure.
 */
data class Staff090Areas(
    val admin: Boolean = false,
    /** Patron type, card renewal and the other tools on a patron's record. */
    val patrons: Boolean = false,
    val patronTypes: Boolean = false,
    val patronCards: Boolean = false,
    val patronPhotos: Boolean = false,
    val ledger: Boolean = false,
    val stocktake: Boolean = false,
    val librarySettings: Boolean = false,
    val circulationRules: Boolean = false,
    val closuresCalendar: Boolean = false,
    val suppliers: Boolean = false,
    val rdaTypes: Boolean = false,
    val catalogueCards: Boolean = false,
    val marc: Boolean = false,
    val attendance: Boolean = false,
    val statistics: Boolean = false,
    val userImport: Boolean = false,
) {
    val anyPatronDesk: Boolean get() = attendance || userImport
    val anyCatalogue: Boolean get() = suppliers || marc || catalogueCards
    val anyLibrary: Boolean get() = librarySettings || circulationRules || closuresCalendar

    companion object {
        val None = Staff090Areas()

        fun of(role: String?, features: InstanceFeatures, online: Boolean): Staff090Areas {
            if (!online) return None
            val f = features.staff090
            val admin = role == "admin"
            return Staff090Areas(
                admin = admin,
                patrons = f.staffPatrons,
                patronTypes = f.staffPatrons && features.circulationRulesAvailable,
                patronCards = f.staffPatrons && f.patronCards,
                patronPhotos = f.staffPatrons && f.patronPhotos,
                ledger = f.staffLedger,
                stocktake = f.staffStocktake,
                librarySettings = f.staffLibrarySettings,
                // Patron types, loan rules and the closures calendar are configured by
                // administrators only (the web's mayConfigure()): a Staff account would get
                // nothing but a refusal, so the entries are an administrator's.
                circulationRules = f.staffCirculationRules && admin,
                closuresCalendar = f.staffClosuresCalendar && admin,
                suppliers = f.staffSuppliers,
                rdaTypes = f.rdaTypes,
                catalogueCards = f.staffCatalogueCards,
                marc = f.staffMarc,
                attendance = f.staffAttendance,
                statistics = f.staffStatistics,
                userImport = f.staffUserImport,
            )
        }
    }
}
