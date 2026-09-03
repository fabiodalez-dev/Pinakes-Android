package com.pinakes.app.ui.navigation

import android.net.Uri

/** Centralized navigation route keys. Nested routes carry typed args via path segments. */
object Routes {
    // Top-level auth graph
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"

    // Bottom-nav destinations
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val WISHLIST = "wishlist"
    const val PROFILE = "profile"

    // Nested
    const val NOTIFICATIONS = "notifications"
    const val CONTACT = "contact"
    const val MY_REVIEWS = "my-reviews"

    const val BOOK_DETAIL = "book/{bookId}"
    fun bookDetail(bookId: Int): String = "book/$bookId"
    const val ARG_BOOK_ID = "bookId"

    // Book Club (optional plugin)
    const val BOOK_CLUB = "book-club"
    const val CLUB_DETAIL = "book-club/{slug}"
    fun clubDetail(slug: String): String = "book-club/${Uri.encode(slug)}"
    const val ARG_CLUB_SLUG = "slug"

    // Periodicals / Emeroteca (optional plugin)
    const val PERIODICALS = "periodicals"
    const val PERIODICAL_DETAIL = "periodicals/{periodicalId}"
    fun periodicalDetail(id: Int): String = "periodicals/$id"
    const val ARG_PERIODICAL_ID = "periodicalId"

    // The display year rides along as a nav arg so the issues screen can title itself
    // ("Year 1998") without re-fetching the masthead detail.
    const val PERIODICAL_YEAR_ISSUES = "periodicals/years/{yearId}/{year}"
    fun periodicalYearIssues(yearId: Int, year: Int): String = "periodicals/years/$yearId/$year"
    const val ARG_PERIODICAL_YEAR_ID = "yearId"
    const val ARG_PERIODICAL_YEAR = "year"

    const val PERIODICAL_ISSUE = "periodicals/issues/{issueId}"
    fun periodicalIssue(id: Int): String = "periodicals/issues/$id"
    const val ARG_PERIODICAL_ISSUE_ID = "issueId"

    /** Graph hosting the bottom-nav + nested authed screens. */
    const val MAIN_GRAPH = "main"
}
