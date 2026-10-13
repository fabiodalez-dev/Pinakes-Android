package com.pinakes.app.staff

import com.pinakes.app.data.staff.StaffAreas
import com.pinakes.app.data.store.InstanceFeatures
import com.pinakes.app.ui.common.OfflinePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Staff areas = role × `/health` flags × connectivity; circulation disappears offline. */
class StaffAreasTest {

    private val all = InstanceFeatures(
        staffCatalogAvailable = true, staffCirculationAvailable = true, staffCopiesAvailable = true, staffSeriesAvailable = true,
        staffMergesAvailable = true, staffLabelsAvailable = true, staffImportsAvailable = true, staffCoversAvailable = true,
        staffDesiderataAvailable = true, staffArchivesAvailable = true, staffLibraryThingAvailable = true,
    )

    @Test fun staffOnlineSeesEverythingAdvertised() {
        val a = StaffAreas.of("staff", all, online = true)
        assertTrue(a.staff && a.circulation && a.copies && a.series && a.merges && a.labels && a.imports && a.covers)
        assertTrue(a.desiderata && a.archives && a.libraryThing && a.anyBooksTool && a.anyPlugin)
    }

    @Test fun offlineHidesOnlyCirculation() {
        val a = StaffAreas.of("admin", all, online = false)
        assertFalse(a.circulation)
        assertTrue(a.staff && a.series && a.imports && a.archives)
    }

    @Test fun borrowersAndUnknownRolesSeeNothing() {
        listOf(null, "", "standard", "premium").forEach { role ->
            assertEquals(role.toString(), StaffAreas.None, StaffAreas.of(role, all, online = true))
        }
    }

    @Test fun withoutTheStaffAreaNothingShows() {
        assertEquals(StaffAreas.None, StaffAreas.of("admin", all.copy(staffCatalogAvailable = false), online = true))
    }

    @Test fun eachFlagGatesItsArea() {
        val none = StaffAreas.of("admin", InstanceFeatures(staffCatalogAvailable = true), online = true)
        assertTrue(none.staff)
        assertFalse(none.circulation || none.series || none.imports || none.desiderata || none.archives || none.anyBooksTool || none.anyPlugin)
        // LibraryThing is an import kind: no imports area, no LibraryThing either.
        assertFalse(StaffAreas.of("admin", all.copy(staffImportsAvailable = false), true).libraryThing)
    }

    @Test fun offlinePolicyForCirculation() {
        assertTrue(OfflinePolicy.showStaffCirculation(available = true, online = true))
        assertFalse(OfflinePolicy.showStaffCirculation(available = true, online = false))
        assertFalse(OfflinePolicy.showStaffCirculation(available = false, online = true))
    }
}
