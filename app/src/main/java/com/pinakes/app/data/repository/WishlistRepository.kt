package com.pinakes.app.data.repository

import com.pinakes.app.data.local.LibraryDao
import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toWishlistItem
import com.pinakes.app.data.model.WishlistAddRequest
import com.pinakes.app.data.model.WishlistItem
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/** The user's wishlist: list, add (idempotent, 201) and remove (idempotent) a book. */
class WishlistRepository(
    private val network: NetworkModule,
    /** Offline copy of the list; null in tests that only exercise the network path. */
    private val cache: LibraryDao? = null,
) {

    /** The wishlist; a successful fetch is written through to the offline cache. */
    suspend fun wishlist(): ApiResult<List<WishlistItem>> {
        val api = network.api()
        val res = apiCall { api.wishlist() }
        if (res is ApiResult.Success) {
            runCatching { cache?.replaceWishlist(res.data.mapIndexed { i, w -> w.toCached(i) }) }
        }
        return res
    }

    /** The last wishlist the server returned, or null when nothing was ever cached. */
    suspend fun wishlistCached(): List<WishlistItem>? =
        cache?.wishlist()?.takeIf { it.isNotEmpty() }?.map { it.toWishlistItem() }

    /** Background refresh (sync worker); failures are ignored. */
    suspend fun refreshCache() {
        wishlist()
    }

    /**
     * Adds the title. The caller refreshes the offline copy afterwards (refreshCache), so the
     * confirmation does not wait for a full list re-fetch.
     */
    suspend fun add(bookId: Int): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.addWishlist(WishlistAddRequest(bookId)) }
    }

    /** Removes, and drops the title from the offline copy at once. */
    suspend fun remove(bookId: Int): ApiResult<Unit> {
        val api = network.api()
        val res = apiCall { api.removeWishlist(bookId) }
        if (res is ApiResult.Success) runCatching { cache?.deleteWishlistItem(bookId) }
        return res
    }
}
