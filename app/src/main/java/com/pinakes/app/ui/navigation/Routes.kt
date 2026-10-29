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
    /** Balance and movements of the fines ledger (Pinakes 0.9.0, `features.fines`). */
    const val MOVEMENTS = "movements"

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

    const val STANDALONE_ARTICLES = "periodicals/articles?periodicalId={periodicalId}&issueId={issueId}&container={container}&keyword={keyword}&genreId={genreId}&q={q}"
    fun standaloneArticles(mastheadId: Int = 0, issueId: Int = 0, container: String = "", keyword: String = "", genreId: Int = 0, query: String = ""): String =
        "periodicals/articles?periodicalId=$mastheadId&issueId=$issueId&container=${Uri.encode(container)}&keyword=${Uri.encode(keyword)}&genreId=$genreId&q=${Uri.encode(query)}"
    const val AUTHOR_WORKS = "works?author={author}&authorId={authorId}"
    fun authorWorks(author: String, id: Int? = null) = "works?author=${Uri.encode(author)}&authorId=${id ?: 0}"
    const val STANDALONE_ARTICLE = "periodicals/articles/{articleId}"
    const val ARG_ARTICLE_ID = "articleId"
    fun standaloneArticle(id: Int): String = "periodicals/articles/$id"

    // The display year rides along as a nav arg so the issues screen can title itself
    // ("Year 1998") without re-fetching the masthead detail.
    const val PERIODICAL_YEAR_ISSUES = "periodicals/years/{yearId}/{year}"
    fun periodicalYearIssues(yearId: Int, year: Int): String = "periodicals/years/$yearId/$year"
    const val ARG_PERIODICAL_YEAR_ID = "yearId"
    const val ARG_PERIODICAL_YEAR = "year"

    const val PERIODICAL_ISSUE = "periodicals/issues/{issueId}"
    fun periodicalIssue(id: Int): String = "periodicals/issues/$id"
    const val ARG_PERIODICAL_ISSUE_ID = "issueId"

    // Staff area (Mobile API 1.6.0, admin/staff only)
    const val STAFF = "staff"
    const val STAFF_SCAN = "staff/scan"
    const val STAFF_BOOKS = "staff/books"
    const val ARG_ISBN = "isbn"
    const val STAFF_BOOK_NEW = "staff/books/new?isbn={isbn}"
    fun staffBookNew(isbn: String = ""): String = "staff/books/new?isbn=${Uri.encode(isbn)}"
    /** Optional: the copy whose editor the book page opens straight away (a copy just received). */
    const val ARG_COPY_ID = "copyId"
    const val STAFF_BOOK_EDIT = "staff/books/{bookId}/edit?copy={copyId}"
    fun staffBookEdit(bookId: Int, copyId: Int? = null): String =
        "staff/books/$bookId/edit" + (copyId?.takeIf { it > 0 }?.let { "?copy=$it" } ?: "")
    const val STAFF_AUTHORS = "staff/authors"
    const val STAFF_PUBLISHERS = "staff/publishers"
    const val ARG_RECORD_ID = "recordId"
    const val STAFF_AUTHOR = "staff/authors/{recordId}"
    fun staffAuthor(id: Int): String = "staff/authors/$id"
    const val STAFF_PUBLISHER = "staff/publishers/{recordId}"
    fun staffPublisher(id: Int): String = "staff/publishers/$id"
    const val STAFF_GENRES = "staff/genres"
    const val STAFF_SHELVES = "staff/shelves"

    // Staff area, phase 2 (Mobile API 1.7.0): circulation desk, book tools, plugin content
    const val STAFF_CIRCULATION = "staff/circulation"
    const val ARG_BUCKET = "bucket"
    const val STAFF_LOANS = "staff/loans?bucket={bucket}"
    fun staffLoans(bucket: String = "all"): String = "staff/loans?bucket=${Uri.encode(bucket)}"
    const val ARG_LOAN_ID = "loanId"
    const val STAFF_LOAN = "staff/loans/{loanId}"
    fun staffLoan(id: Int): String = "staff/loans/$id"
    const val ARG_USER_ID = "userId"
    const val ARG_COPY_CODE = "copyCode"
    const val ARG_RESERVATION_MODE = "reservation"
    const val STAFF_LOAN_NEW = "staff/new-loan?userId={userId}&bookId={bookId}&copyCode={copyCode}&reservation={reservation}"
    fun staffLoanNew(userId: Int = 0, bookId: Int = 0, copyCode: String = "", reservation: Boolean = false): String =
        "staff/new-loan?userId=$userId&bookId=$bookId&copyCode=${Uri.encode(copyCode)}&reservation=$reservation"
    const val STAFF_RESERVATIONS = "staff/reservations"
    const val STAFF_USER = "staff/users/{userId}"
    fun staffUser(id: Int): String = "staff/users/$id"
    const val STAFF_COPY_SCAN = "staff/scan-copy"
    const val ARG_SERIES_ID = "seriesId"
    const val STAFF_SERIES = "staff/series"
    const val STAFF_SERIES_DETAIL = "staff/series/{seriesId}"
    fun staffSeriesDetail(id: Int): String = "staff/series/$id"
    const val STAFF_MERGES = "staff/merges"
    const val ARG_JOB_ID = "jobId"
    const val STAFF_IMPORTS = "staff/imports"
    const val STAFF_IMPORT = "staff/imports/{jobId}"
    fun staffImport(id: String): String = "staff/imports/${Uri.encode(id)}"
    const val STAFF_COVERS = "staff/covers"
    const val STAFF_DESIDERATA = "staff/desiderata"
    const val STAFF_ARCHIVES = "staff/archives"
    const val STAFF_ARCHIVE_EDIT = "staff/archives/{archiveId}/edit"
    fun staffArchiveEdit(id: Int = 0): String = "staff/archives/$id/edit"
    const val ARG_AUTHORITY_ID = "authorityId"
    const val STAFF_AUTHORITIES = "staff/authorities"
    const val STAFF_AUTHORITY_EDIT = "staff/authorities/{authorityId}/edit"
    fun staffAuthorityEdit(id: Int = 0): String = "staff/authorities/$id/edit"

    // Staff area, Tier 2 bridges (periodicals desk, book clubs, FRBR works)
    const val STAFF_PERIODICALS = "staff/periodicals"
    const val STAFF_PERIODICAL = "staff/periodicals/{periodicalId}"
    fun staffPeriodical(id: Int): String = "staff/periodicals/$id"
    const val ARG_CLUB_ID = "clubId"
    const val STAFF_CLUBS = "staff/clubs"
    const val STAFF_CLUB = "staff/clubs/{clubId}"
    fun staffClub(id: Int): String = "staff/clubs/$id"
    const val ARG_WORK_ID = "workId"
    const val STAFF_WORKS = "staff/works"
    const val STAFF_WORK = "staff/works/{workId}"
    fun staffWork(id: Int): String = "staff/works/$id"

    /** Graph hosting the bottom-nav + nested authed screens. */
    const val MAIN_GRAPH = "main"
    const val DESIDERATA = "desiderata"
    const val WANTED_BOOK = "desiderata/{wantedId}"
    const val ARG_WANTED_ID = "wantedId"
    fun wantedBook(id: Int) = "desiderata/$id"
    const val DONATION = "donation?bookId={wantedId}"
    fun donation(id: Int = 0) = "donation?bookId=$id"
    const val ARCHIVES = "archives?parentId={archiveParentId}"
    const val ARG_ARCHIVE_PARENT = "archiveParentId"
    fun archives(parentId: Int = 0) = "archives?parentId=$parentId"
    const val ARCHIVE = "archives/{archiveId}"
    const val ARG_ARCHIVE_ID = "archiveId"
    fun archive(id: Int) = "archives/$id"
}
