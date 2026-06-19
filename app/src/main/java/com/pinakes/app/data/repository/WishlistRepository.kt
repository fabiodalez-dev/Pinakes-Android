package com.pinakes.app.data.repository

import com.pinakes.app.data.model.WishlistAddRequest
import com.pinakes.app.data.model.WishlistItem
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/** The user's wishlist: list, add (idempotent, 201) and remove (idempotent) a book. */
class WishlistRepository(private val network: NetworkModule) {

    suspend fun wishlist(): ApiResult<List<WishlistItem>> {
        val api = network.api()
        return apiCall { api.wishlist() }
    }

    suspend fun add(bookId: Int): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.addWishlist(WishlistAddRequest(bookId)) }
    }

    suspend fun remove(bookId: Int): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.removeWishlist(bookId) }
    }
}
