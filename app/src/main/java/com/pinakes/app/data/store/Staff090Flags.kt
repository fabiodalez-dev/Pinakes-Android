package com.pinakes.app.data.store

import com.pinakes.app.data.model.HealthFeatures
import kotlinx.serialization.Serializable

/**
 * Pinakes 0.9.0 modules and their staff areas (Mobile API 1.10.0), as `/health` grants them.
 * Every flag already includes the app-access gate, and the circulation ones are off in
 * catalogue mode. Kept apart from [InstanceFeatures]' own fields and persisted as one JSON
 * value, so a new 0.9.0 flag is one field here and one line in [from]. Pure.
 */
@Serializable
data class Staff090Flags(
    // Modules (Settings -> Modules / Loans)
    val stocktake: Boolean = false,
    val marc: Boolean = false,
    val visitorLog: Boolean = false,
    val catalogueCards: Boolean = false,
    val suppliers: Boolean = false,
    val rdaTypes: Boolean = false,
    val patronCards: Boolean = false,
    val userImport: Boolean = false,
    val patronPhotos: Boolean = false,
    val automaticCardExpiry: Boolean = false,
    val automaticFines: Boolean = false,
    val debtBlock: Boolean = false,
    val lossPenalties: Boolean = false,
    val attendanceRetention: Boolean = false,
    // Staff areas (the server mounted the routes and the module is on)
    val staffPatrons: Boolean = false,
    val staffLedger: Boolean = false,
    val staffStocktake: Boolean = false,
    val staffLibrarySettings: Boolean = false,
    val staffCirculationRules: Boolean = false,
    val staffClosuresCalendar: Boolean = false,
    val staffSuppliers: Boolean = false,
    val staffCatalogueCards: Boolean = false,
    val staffMarc: Boolean = false,
    val staffAttendance: Boolean = false,
    val staffStatistics: Boolean = false,
    val staffUserImport: Boolean = false,
) {
    companion object {
        val None = Staff090Flags()

        fun from(f: HealthFeatures, appAccess: Boolean, circulation: Boolean): Staff090Flags {
            if (!appAccess) return None
            return Staff090Flags(
                stocktake = f.stocktake,
                marc = f.marc,
                visitorLog = f.visitorLog && circulation,
                catalogueCards = f.catalogueCards,
                suppliers = f.suppliers,
                rdaTypes = f.rdaTypes,
                patronCards = f.patronCards,
                userImport = f.userImport,
                patronPhotos = f.patronPhotos,
                automaticCardExpiry = f.automaticCardExpiry && circulation,
                automaticFines = f.automaticFines && circulation,
                debtBlock = f.debtBlock && circulation,
                lossPenalties = f.lossPenalties && circulation,
                attendanceRetention = f.attendanceRetention,
                staffPatrons = f.staffPatrons,
                staffLedger = f.staffLedger && circulation,
                staffStocktake = f.staffStocktake,
                staffLibrarySettings = f.staffLibrarySettings,
                // Configuration stays reachable with the module off (the server lets an
                // administrator configure a switched-off module, as the web does).
                staffCirculationRules = f.staffCirculationRules,
                staffClosuresCalendar = f.staffClosuresCalendar,
                staffSuppliers = f.staffSuppliers,
                staffCatalogueCards = f.staffCatalogueCards,
                staffMarc = f.staffMarc,
                staffAttendance = f.staffAttendance && circulation,
                staffStatistics = f.staffStatistics,
                staffUserImport = f.staffUserImport,
            )
        }
    }
}
