package com.pinakes.app.data.local

import androidx.room.Entity
import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.data.model.LoansData
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.data.model.WishlistItem
import androidx.room.PrimaryKey

/**
 * The signed-in user's loans and reservations, cached for offline reading. One table for all
 * four lists: [bucket] is `pending`, `active`, `history` or `reservation`, and [position]
 * keeps the server's order inside a bucket. Reservation-only columns stay null for loans.
 */
@Entity(tableName = "cached_loans", primaryKeys = ["bucket", "id"])
data class CachedLoan(
    val bucket: String,
    val id: Int,
    val position: Int,
    val bookId: Int,
    val title: String,
    val coverUrl: String? = null,
    val status: String = "",
    val statusLabel: String? = null,
    val requestedAt: String? = null,
    val loanedAt: String? = null,
    val dueAt: String? = null,
    val dueAttention: Boolean = false,
    val returnedAt: String? = null,
    val renewals: Int? = null,
    val cancellable: Boolean = false,
    val queuePosition: Int? = null,
    val requestedFrom: String? = null,
    val requestedTo: String? = null,
    val reservedAt: String? = null,
    val expiresAt: String? = null,
) {
    companion object {
        const val PENDING = "pending"
        const val ACTIVE = "active"
        const val HISTORY = "history"
        const val RESERVATION = "reservation"
        val LOAN_BUCKETS = listOf(PENDING, ACTIVE, HISTORY)
    }
}

/** The signed-in user's wishlist, in server order. */
@Entity(tableName = "cached_wishlist")
data class CachedWishlistItem(
    @PrimaryKey val bookId: Int,
    val position: Int,
    val title: String,
    val author: String? = null,
    val year: Int? = null,
    val coverUrl: String? = null,
    val copiesAvailable: Int = 0,
    val loanableNow: Boolean = false,
)

/** Small key/value store for sync bookkeeping (watermark, last full resync). */
@Entity(tableName = "sync_meta")
data class SyncMeta(
    @PrimaryKey val key: String,
    val value: String,
) {
    companion object {
        /** `server_time` of the last completed catalogue sync: the next `since`. */
        const val CATALOG_SERVER_TIME = "catalog_server_time"

        /** Epoch millis of the last completed full resync (forced again after 7 days). */
        const val CATALOG_LAST_FULL_AT = "catalog_last_full_at"
    }
}

fun LoanItem.toCached(bucket: String, position: Int): CachedLoan = CachedLoan(
    bucket = bucket, id = id, position = position, bookId = bookId, title = title, coverUrl = coverUrl,
    status = status, statusLabel = statusLabel, requestedAt = requestedAt, loanedAt = loanedAt, dueAt = dueAt,
    dueAttention = dueAttention, returnedAt = returnedAt, renewals = renewals, cancellable = cancellable,
)

fun CachedLoan.toLoanItem(): LoanItem = LoanItem(
    id = id, bookId = bookId, title = title, coverUrl = coverUrl, status = status, statusLabel = statusLabel,
    requestedAt = requestedAt, loanedAt = loanedAt, dueAt = dueAt, dueAttention = dueAttention,
    returnedAt = returnedAt, renewals = renewals, cancellable = cancellable,
)

fun ReservationItem.toCached(position: Int): CachedLoan = CachedLoan(
    bucket = CachedLoan.RESERVATION, id = id, position = position, bookId = bookId, title = title,
    coverUrl = coverUrl, status = status, queuePosition = queuePosition, requestedFrom = requestedFrom,
    requestedTo = requestedTo, reservedAt = reservedAt, expiresAt = expiresAt,
)

fun CachedLoan.toReservationItem(): ReservationItem = ReservationItem(
    id = id, bookId = bookId, title = title, coverUrl = coverUrl, status = status, queuePosition = queuePosition,
    requestedFrom = requestedFrom, requestedTo = requestedTo, reservedAt = reservedAt, expiresAt = expiresAt,
)

fun LoansData.toCached(): List<CachedLoan> =
    pending.mapIndexed { i, l -> l.toCached(CachedLoan.PENDING, i) } +
        active.mapIndexed { i, l -> l.toCached(CachedLoan.ACTIVE, i) } +
        history.mapIndexed { i, l -> l.toCached(CachedLoan.HISTORY, i) }

/** Rebuild the three loan lists from cached rows (rows are already in bucket/position order). */
fun List<CachedLoan>.toLoansData(): LoansData = LoansData(
    pending = filter { it.bucket == CachedLoan.PENDING }.map { it.toLoanItem() },
    active = filter { it.bucket == CachedLoan.ACTIVE }.map { it.toLoanItem() },
    history = filter { it.bucket == CachedLoan.HISTORY }.map { it.toLoanItem() },
)

fun WishlistItem.toCached(position: Int): CachedWishlistItem = CachedWishlistItem(
    bookId = bookId, position = position, title = title, author = author, year = year, coverUrl = coverUrl,
    copiesAvailable = copiesAvailable, loanableNow = loanableNow,
)

fun CachedWishlistItem.toWishlistItem(): WishlistItem = WishlistItem(
    bookId = bookId, title = title, author = author, year = year, coverUrl = coverUrl,
    copiesAvailable = copiesAvailable, loanableNow = loanableNow,
)
