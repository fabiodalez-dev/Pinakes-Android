package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Envelope ----------
@Serializable
data class Envelope<T>(
    val data: T? = null,
    val meta: Meta? = null,
    val error: ApiError? = null,
)

@Serializable
data class Meta(
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("total_count") val totalCount: Int? = null,
    val https: Boolean? = null,
    val warning: String? = null, // "insecure_transport" on /health
)

@Serializable
data class ApiError(
    val code: String = "unknown",   // invalid_credentials | app_disabled | rate_limited | ...
    val message: String = "",
)

// ---------- Health / discovery ----------
@Serializable
data class HealthPayload(
    val name: String = "Pinakes",
    val logo: String? = null,
    val version: String = "",
    @SerialName("api_version") val apiVersion: String = "v1",
    val features: HealthFeatures = HealthFeatures(),
    // When true the instance is a read-only browsable catalog: loans, reservations and
    // wishlist are disabled (and the matching booleans in [features] are false).
    @SerialName("catalogue_mode") val catalogueMode: Boolean = false,
    @SerialName("app_access_enabled") val appAccessEnabled: Boolean = false,
    @SerialName("registration_enabled") val registrationEnabled: Boolean = false,
    @SerialName("private_mode") val privateMode: Boolean = false,
    @SerialName("vapid_public_key") val vapidPublicKey: String? = null,
)

@Serializable
data class HealthFeatures(
    val catalog: Boolean = false,
    val loans: Boolean = false,
    val reservations: Boolean = false,
    val wishlist: Boolean = false,
    val messages: Boolean = false,
    val notifications: Boolean = false,
    val push: Boolean = false,
)

// ---------- Auth ----------
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_id") val deviceId: String,
    val platform: String = "android", // "android" | "ios" | "other"
)

@Serializable
data class LoginResponse(
    val token: String,        // store securely — returned once
    val user: UserProfile,
)

@Serializable
data class RegisterRequest(
    val nome: String,
    val cognome: String,
    val email: String,
    // Backend (AuthController) requires these too and 422s without them:
    // privacy must be accepted, phone + address are mandatory, and the password
    // must be confirmed (8-72 chars, upper+lower+digit).
    val telefono: String,
    val indirizzo: String,
    val password: String,
    @SerialName("password_confirm") val passwordConfirm: String,
    @SerialName("privacy_acceptance") val privacyAcceptance: Boolean,
)

@Serializable
data class ForgotRequest(val email: String)

// ---------- User ----------
@Serializable
data class UserProfile(
    val id: Int = 0,
    val nome: String = "",
    val cognome: String = "",
    val email: String = "",
    @SerialName("tipo_utente") val tipoUtente: String = "utente", // utente | staff | admin
    @SerialName("email_verificata") val emailVerificata: Boolean = false,
    val stato: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
) {
    val fullName: String get() = listOf(nome, cognome).filter { it.isNotBlank() }.joinToString(" ")
}

@Serializable
data class UpdateProfileRequest(
    val nome: String? = null,
    val cognome: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String, // min 8
)

// ---------- Devices ----------
@Serializable
data class DeviceItem(
    val id: Int,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val platform: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
)

// ---------- Book ----------
// Field names mirror the LIVE API JSON (English, snake_case → camelCase via
// @SerialName). The OpenAPI contract file is stale on this point; the running
// CatalogController is the source of truth.
@Serializable
data class BookSummary(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val author: String? = null,        // single label, may be null
    val publisher: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val language: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val isbn13: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null, // absolute
    @SerialName("copies_total") val copiesTotal: Int = 0,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    @SerialName("loanable_now") val loanableNow: Boolean = false,
) {
    val authorsLabel: String get() = author.orEmpty()

    /** A title is available when a copy can be borrowed right now. */
    val available: Boolean get() = loanableNow || copiesAvailable > 0
}

// BookDetail mirrors the detail endpoint: summary fields + richer metadata,
// nested availability/genre/location objects, author/publisher lists, and the
// authed-user personal_history.
@Serializable
data class BookDetail(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val year: Int? = null,
    val language: String? = null,
    val pages: Int? = null,
    val description: String? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val ean: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val format: String? = null,
    val series: String? = null,
    val condition: String? = null,            // physical condition, when present
    @SerialName("cover_url") val coverUrl: String? = null,
    val genre: GenreLabel? = null,
    val publisher: String? = null,
    val authors: List<AuthorRef> = emptyList(),
    val availability: Availability = Availability(),
    val location: BookLocation? = null,
    @SerialName("personal_history") val personalHistory: PersonalHistory? = null,
    // Digital assets (live API keys). Nullable URLs + boolean flags.
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("has_audio") val hasAudio: Boolean = false,
    @SerialName("ebook_url") val ebookUrl: String? = null,
    @SerialName("ebook_format") val ebookFormat: String? = null, // "pdf" | "epub" | ...
    @SerialName("has_ebook") val hasEbook: Boolean = false,
) {
    val authorsLabel: String get() = authors.joinToString(", ") { it.name }
    val genreLabel: String? get() = genre?.name
    val locationLabel: String? get() = location?.label

    val copiesTotal: Int get() = availability.copiesTotal
    val copiesAvailable: Int get() = availability.copiesAvailable
    val loanableNow: Boolean get() = availability.loanableNow
    val available: Boolean get() = availability.loanableNow || availability.copiesAvailable > 0
}

@Serializable
data class Availability(
    @SerialName("copies_total") val copiesTotal: Int = 0,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    @SerialName("loanable_now") val loanableNow: Boolean = false,
    // Why a title isn't free, for colour-coding: available | on_loan | reserved | unavailable.
    val state: String? = null,
)

@Serializable
data class AuthorRef(
    val id: Int = 0,
    val name: String = "",
    val role: String? = null,
)

@Serializable
data class GenreLabel(
    val id: Int? = null,
    val name: String? = null,
    val parent: String? = null,
    val grandparent: String? = null,
    val subgenre: String? = null,
)

@Serializable
data class BookLocation(
    val label: String? = null,
    @SerialName("shelf_id") val shelfId: Int? = null,
    @SerialName("shelf_unit_id") val shelfUnitId: Int? = null,
    val position: Int? = null,
)

@Serializable
data class PersonalHistory(
    @SerialName("has_read") val hasRead: Boolean = false,
    @SerialName("has_reserved") val hasReserved: Boolean = false,
    @SerialName("has_wishlisted") val hasWishlisted: Boolean = false,
    @SerialName("has_active_loan") val hasActiveLoan: Boolean = false,
    @SerialName("has_pending_request") val hasPendingRequest: Boolean = false,
)

@Serializable
data class GenreNode(
    val id: Int = 0,
    // The API serializes the genre label as "name" (English); the old "nome"
    // never deserialized, so genre filter chips rendered blank.
    val name: String = "",
    val children: List<GenreNode> = emptyList(),
)

// ---------- Loans / reservations ----------
@Serializable
data class LoansData(
    val pending: List<LoanItem> = emptyList(),
    val active: List<LoanItem> = emptyList(),
    val history: List<LoanItem> = emptyList(),
)

@Serializable
data class LoanItem(
    val id: Int = 0,
    @SerialName("book_id") val bookId: Int = 0,
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String = "", // in_corso | concluso | in_scadenza | scaduto | prenotato | in_attesa
    @SerialName("loaned_at") val loanedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("returned_at") val returnedAt: String? = null,
    val renewals: Int? = null,
)

@Serializable
data class ReservationItem(
    val id: Int = 0,
    @SerialName("book_id") val bookId: Int = 0,
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String = "",
    @SerialName("queue_position") val queuePosition: Int? = null,
    @SerialName("requested_from") val requestedFrom: String? = null,
    @SerialName("requested_to") val requestedTo: String? = null,
    @SerialName("reserved_at") val reservedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

// ---------- Availability calendar ----------
// Mirrors GET /catalog/books/{id}/availability — ~180 days of per-day availability
// used to drive the colored loan-request calendar (matches the website).
@Serializable
data class AvailabilityCalendar(
    @SerialName("total_copies") val totalCopies: Int = 0,
    @SerialName("earliest_available") val earliestAvailable: String? = null, // "yyyy-MM-dd"
    @SerialName("unavailable_dates") val unavailableDates: List<String> = emptyList(),
    val days: List<AvailabilityDay> = emptyList(),
)

@Serializable
data class AvailabilityDay(
    val date: String = "",          // "yyyy-MM-dd"
    val available: Int = 0,
    val loaned: Int = 0,
    val reserved: Int = 0,
    val state: String = "free",     // "free" | "partial" | "full"
)

@Serializable
data class ReservationRequest(
    @SerialName("book_id") val bookId: Int,
    // Single desired start date "yyyy-MM-dd"; the backend computes the return as +1 month
    // (same as the website). This is the authoritative field the request flow sends.
    @SerialName("desired_date") val desiredDate: String? = null,
    @SerialName("start_date") val startDate: String? = null, // legacy/compat, yyyy-MM-dd
    @SerialName("end_date") val endDate: String? = null,     // legacy/compat, yyyy-MM-dd
)

// ---------- Wishlist ----------
@Serializable
data class WishlistItem(
    @SerialName("book_id") val bookId: Int = 0,
    val title: String = "",
    val author: String? = null,
    val year: Int? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("copies_available") val copiesAvailable: Int = 0,
    @SerialName("loanable_now") val loanableNow: Boolean = false,
) {
    val available: Boolean get() = loanableNow || copiesAvailable > 0
}

@Serializable
data class WishlistAddRequest(@SerialName("book_id") val bookId: Int)

// ---------- Messaging / notifications ----------
@Serializable
data class MessageRequest(
    val subject: String, // max 255
    val body: String,    // max 5000
)

@Serializable
data class NotificationItem(
    val id: String = "", // opaque
    val type: String = "", // loan_due | loan_overdue | reservation_ready | new_message | book_available
    val title: String = "",
    val message: String = "",
    @SerialName("book_id") val bookId: Int? = null,
    val date: String? = null,
    // The derived in-app feed has no read-state; default keeps UI styling stable.
    val read: Boolean = false,
)

// ---------- Push ----------
@Serializable
data class PushSubscribeRequest(
    val provider: String, // "unifiedpush" | "fcm"
    val endpoint: String? = null,        // UnifiedPush distributor endpoint
    @SerialName("public_key") val publicKey: String? = null, // WebPush ECDH pub
    val auth: String? = null,            // WebPush auth secret
    @SerialName("registration_id") val registrationId: String? = null, // FCM
)

@Serializable
data class PushPrefs(
    @SerialName("loan_due") val loanDue: Boolean? = null,
    @SerialName("loan_overdue") val loanOverdue: Boolean? = null,
    @SerialName("reservation_ready") val reservationReady: Boolean? = null,
    @SerialName("new_message") val newMessage: Boolean? = null,
    @SerialName("book_available") val bookAvailable: Boolean? = null,
    @SerialName("quiet_start") val quietStart: String? = null, // "HH:MM"
    @SerialName("quiet_end") val quietEnd: String? = null,     // "HH:MM"
)
