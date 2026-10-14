package com.pinakes.app.data.staff

import com.pinakes.app.data.store.InstanceFeatures
import com.pinakes.app.ui.common.OfflinePolicy

/**
 * Which staff areas the app shows. Each one needs a staff role (`admin`/`staff`), a server
 * with the staff area (`staff_catalog`) and its own `/health` flag. Circulation also needs a
 * connection: offline its entries are hidden, never shown disabled. Pure.
 */
data class StaffAreas(
    val staff: Boolean = false,
    val circulation: Boolean = false,
    val copies: Boolean = false,
    val series: Boolean = false,
    val merges: Boolean = false,
    val labels: Boolean = false,
    val imports: Boolean = false,
    val covers: Boolean = false,
    val desiderata: Boolean = false,
    val archives: Boolean = false,
    val periodicals: Boolean = false,
    val bookClub: Boolean = false,
    val works: Boolean = false,
    val digital: Boolean = false,
    val libraryThing: Boolean = false,
) {
    /** At least one entry of the "Books" section beyond the Phase 1 ones. */
    val anyBooksTool: Boolean get() = series || merges || imports || covers || labels

    /** At least one plugin managed natively. */
    val anyPlugin: Boolean get() = desiderata || archives || periodicals || bookClub || works

    companion object {
        val None = StaffAreas()

        fun of(role: String?, features: InstanceFeatures, online: Boolean): StaffAreas {
            val staff = StaffAccess.canManageCatalog(role, features.staffCatalogAvailable)
            if (!staff) return None
            return StaffAreas(
                staff = true,
                circulation = OfflinePolicy.showStaffCirculation(features.staffCirculationAvailable, online),
                copies = features.staffCopiesAvailable,
                series = features.staffSeriesAvailable,
                merges = features.staffMergesAvailable,
                labels = features.staffLabelsAvailable,
                imports = features.staffImportsAvailable,
                covers = features.staffCoversAvailable,
                desiderata = features.staffDesiderataAvailable,
                archives = features.staffArchivesAvailable,
                periodicals = features.staffPeriodicalsAvailable,
                bookClub = features.staffBookClubAvailable,
                works = features.staffFrbrAvailable,
                digital = features.staffDigitalLibraryAvailable,
                libraryThing = features.staffLibraryThingAvailable && features.staffImportsAvailable,
            )
        }
    }
}
