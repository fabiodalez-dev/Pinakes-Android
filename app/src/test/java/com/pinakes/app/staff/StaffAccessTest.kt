package com.pinakes.app.staff

import com.pinakes.app.data.staff.StaffAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffAccessTest {

    @Test fun adminAndStaffAreStaff() {
        assertTrue(StaffAccess.isStaff("admin"))
        assertTrue(StaffAccess.isStaff("staff"))
        assertTrue(StaffAccess.isStaff(" Staff "))
    }

    @Test fun readersAndUnknownAreNot() {
        assertFalse(StaffAccess.isStaff("utente"))
        assertFalse(StaffAccess.isStaff("premium"))
        assertFalse(StaffAccess.isStaff(""))
        assertFalse(StaffAccess.isStaff(null))
    }

    @Test fun staffAreaNeedsBothRoleAndServerSupport() {
        assertTrue(StaffAccess.canManageCatalog("admin", serverSupportsStaff = true))
        assertFalse(StaffAccess.canManageCatalog("admin", serverSupportsStaff = false))
        assertFalse(StaffAccess.canManageCatalog("utente", serverSupportsStaff = true))
    }
}
