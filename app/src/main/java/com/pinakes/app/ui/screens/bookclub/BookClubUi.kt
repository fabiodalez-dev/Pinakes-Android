package com.pinakes.app.ui.screens.bookclub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.pinakes.app.R
import com.pinakes.app.ui.common.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Parse a `#RRGGBB` (or `#AARRGGBB`) club/state colour into a Compose [Color].
 * Falls back to [fallback] on any malformed value so the UI never crashes on server data.
 */
fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrDefault(fallback)
}

private val clubDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault())

/**
 * Book Club timestamps come straight from the DB as `yyyy-MM-dd HH:mm:ss` (local wall-clock, no
 * zone), unlike the core API's ISO-8601 UTC. Render those directly; delegate anything else to the
 * shared [DateFormat] so ISO strings and date-only values still format correctly.
 */
fun clubDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    if (raw.contains(' ') && !raw.contains('T')) {
        val normalized = if (raw.length == 16) "$raw:00" else raw // "yyyy-MM-dd HH:mm" → add seconds
        val parsed = runCatching {
            LocalDateTime.parse(normalized.replace(' ', 'T')).format(clubDateTimeFormatter)
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return DateFormat.dateTime(raw)
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
