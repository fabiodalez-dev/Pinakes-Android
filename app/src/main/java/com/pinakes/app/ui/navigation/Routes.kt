package com.pinakes.app.ui.navigation

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

    const val BOOK_DETAIL = "book/{bookId}"
    fun bookDetail(bookId: Int): String = "book/$bookId"
    const val ARG_BOOK_ID = "bookId"

    /** Graph hosting the bottom-nav + nested authed screens. */
    const val MAIN_GRAPH = "main"
}
