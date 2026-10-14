package com.pinakes.app.staff

import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.ui.screens.detail.BookDetailUiState
import com.pinakes.app.ui.screens.home.HomeUiState
import com.pinakes.app.ui.screens.library.LibraryUiState
import com.pinakes.app.ui.screens.search.SearchUiState
import com.pinakes.app.ui.screens.wishlist.WishlistUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Losing the connection after an online load hides loan affordances and current availability
 * at once; the raw network exception text never becomes a UI message.
 */
class ConnectivityStateTest {

    @Test fun homeShelfAvailabilityIsCurrentOnlyOnlineAndFromTheServer() {
        assertTrue(HomeUiState(connected = true, fromCache = false).availabilityCurrent)
        assertFalse(HomeUiState(connected = false, fromCache = false).availabilityCurrent)
        assertFalse(HomeUiState(connected = true, fromCache = true).availabilityCurrent)
    }

    @Test fun searchResultsLoseAvailabilityWhenDisconnectedOrCached() {
        assertTrue(SearchUiState(connected = true).availabilityCurrent)
        assertFalse(SearchUiState(connected = false).availabilityCurrent)
        assertFalse(SearchUiState(offline = true).availabilityCurrent)
    }

    @Test fun bookDetailIsOfflineWhenCachedOrDisconnected() {
        assertFalse(BookDetailUiState(connected = true).offlineNow)
        assertTrue(BookDetailUiState(connected = false).offlineNow)
        assertTrue(BookDetailUiState(offline = true).offlineNow)
    }

    @Test fun libraryActionsDisappearOffline() {
        assertTrue(LibraryUiState(connected = true).showActions)
        assertFalse(LibraryUiState(connected = false).showActions)
        assertFalse(LibraryUiState(offline = true).showActions)
    }

    @Test fun wishlistIsOfflineWhenCachedOrDisconnected() {
        assertFalse(WishlistUiState().offlineNow)
        assertTrue(WishlistUiState(connected = false).offlineNow)
    }

    @Test fun networkExceptionTextIsNeverAServerMessage() {
        val offline = ApiResult.Failure(ErrorCodes.NETWORK, "Failed to connect to /10.0.2.2:8081")
        assertTrue(offline.isOffline)
        assertNull(offline.serverMessage())
        assertEquals("Titolo obbligatorio", ApiResult.Failure("validation_error", "Titolo obbligatorio", 422).serverMessage())
        assertNull(ApiResult.Failure("validation_error", "  ", 422).serverMessage())
    }
}
