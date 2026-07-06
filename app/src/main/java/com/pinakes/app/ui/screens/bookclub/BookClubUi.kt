package com.pinakes.app.ui.screens.bookclub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.pinakes.app.R

/**
 * Parse a `#RRGGBB` (or `#AARRGGBB`) club/state colour into a Compose [Color].
 * Falls back to [fallback] on any malformed value so the UI never crashes on server data.
 */
fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrDefault(fallback)
}

/** Localized label for a club membership role. */
@StringRes
fun roleLabelRes(role: String): Int = when (role) {
    "owner" -> R.string.book_club_role_owner
    "moderator" -> R.string.book_club_role_moderator
    "guest" -> R.string.book_club_role_guest
    else -> R.string.book_club_role_member
}

/** Localized label for a club privacy setting. */
@StringRes
fun privacyLabelRes(privacy: String): Int = when (privacy) {
    "public" -> R.string.book_club_privacy_public
    "private" -> R.string.book_club_privacy_private
    "invite" -> R.string.book_club_privacy_invite
    "hidden" -> R.string.book_club_privacy_hidden
    else -> R.string.book_club_privacy_public
}

/** Open a web URL (deep-links the flows the Book Club API marks as web-only). */
fun openWeb(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
