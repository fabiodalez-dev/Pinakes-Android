package com.pinakes.app.data.repository

import com.pinakes.app.data.model.BookReviews
import com.pinakes.app.data.model.MyReview
import com.pinakes.app.data.model.Review
import com.pinakes.app.data.model.ReviewRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/** One page of the user's own reviews plus the cursor for the next page. */
data class MyReviewsPage(
    val items: List<MyReview>,
    val nextCursor: String?,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/**
 * Book reviews (star + text). Mirrors the website's review feature:
 * - read a title's aggregate rating + everyone's reviews,
 * - a borrower can create/edit/delete their own review,
 * - list all of the user's own reviews for the "My reviews" page.
 */
class ReviewsRepository(private val network: NetworkModule) {

    /** Aggregate + the user's own review + a page of other reviews for [bookId]. */
    suspend fun bookReviews(bookId: Int, cursor: String? = null, limit: Int = 20): ApiResult<BookReviews> {
        val api = network.api()
        return apiCall { api.bookReviews(bookId, cursor = cursor, limit = limit.coerceIn(1, 50)) }
    }

    /** Create or edit the current user's review for [bookId]. */
    suspend fun submit(bookId: Int, rating: Int, text: String?): ApiResult<Review> {
        val api = network.api()
        val body = ReviewRequest(rating = rating.coerceIn(1, 5), text = text?.trim()?.takeIf { it.isNotEmpty() })
        return apiCall { api.submitReview(bookId, body) }
    }

    /** Delete the current user's review for [bookId] (idempotent). */
    suspend fun delete(bookId: Int): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.deleteReview(bookId) }
    }

    /** One cursor-paginated page of the user's own reviews. */
    suspend fun myReviews(cursor: String? = null, limit: Int = 20): ApiResult<MyReviewsPage> {
        val api = network.api()
        return when (val res = apiCall { api.myReviews(cursor = cursor, limit = limit.coerceIn(1, 50)) }) {
            is ApiResult.Success -> ApiResult.Success(
                MyReviewsPage(items = res.data, nextCursor = res.meta?.nextCursor),
                res.meta,
            )
            is ApiResult.Failure -> res
        }
    }
}
