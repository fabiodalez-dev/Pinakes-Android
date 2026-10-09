package com.pinakes.app.ui.screens.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import com.pinakes.app.ui.components.BookQuickFacts
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.bookCoverImageUrl
import com.pinakes.app.ui.components.DigitalFileCard
import com.pinakes.app.ui.components.MediaTypeIcon
import com.pinakes.app.ui.theme.LocalPinakesColors
import com.pinakes.app.ui.theme.Fraunces
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.pinakes.app.R
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.CirculationRequestKind
import com.pinakes.app.data.model.PersonalHistory
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.AudioPlayer
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.HtmlText
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.CitationButton
import com.pinakes.app.ui.components.MetadataRow
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onNavigateUp: () -> Unit,
    onFindWorks: (String, Int?) -> Unit = { _, _ -> },
) {
    val app: AppViewModel = hiltViewModel()
    val features by app.features.collectAsStateWithLifecycle()
    val vm: BookDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val snackbarMessage = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    // Use the authoritative server outcome. With #384 an apparent immediate
    // loan may correctly become a FIFO reservation; never tell the user it was
    // a loan merely because that was the button label before submission.
    val requestedConfirmation = state.requestConfirmation?.let { confirmation ->
        when (confirmation.kind) {
            CirculationRequestKind.Reservation -> stringResource(
                R.string.snackbar_reservation_for_date,
                formatDisplayDate(confirmation.desiredDate),
            )
            CirculationRequestKind.LoanReadyForPickup ->
                stringResource(R.string.snackbar_loan_ready_for_pickup)
            CirculationRequestKind.LoanPending -> stringResource(
                R.string.snackbar_request_for_date,
                formatDisplayDate(confirmation.desiredDate),
            )
            CirculationRequestKind.Unknown -> stringResource(R.string.snackbar_request_submitted)
        }
    }
    LaunchedEffect(requestedConfirmation) {
        requestedConfirmation?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeRequestConfirmation()
        }
    }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.title_book),
                onNavigateUp = onNavigateUp,
                actions = {
                    // Wishlist toggle is hidden when the wishlist feature is disabled.
                    if (features.showWishlist) {
                        IconButton(onClick = vm::toggleWishlist, enabled = !state.wishlistBusy) {
                            Icon(
                                imageVector = if (state.wishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (state.wishlisted) stringResource(R.string.cd_remove_from_wishlist) else stringResource(R.string.cd_add_to_wishlist),
                                tint = if (state.wishlisted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.book_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::load)
                is UiState.Success -> DetailContent(
                    book = content.data,
                    wishlisted = state.wishlisted,
                    reserveBusy = state.reserveBusy,
                    canBorrow = features.canBorrow,
                    showWishlist = features.showWishlist,
                    showReviews = features.showReviews,
                    onReserve = vm::openLoanSheet,
                    onToggleWishlist = vm::toggleWishlist,
                    onFindWorks = onFindWorks,
                    onShowMessage = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } },
                )
            }
        }
    }

    // Loan/reservation sheet is only ever shown when borrowing is enabled.
    if (state.showLoanSheet && features.canBorrow) {
        LoanRequestSheet(
            loading = state.availabilityLoading,
            calendar = state.availability,
            fallback = state.availabilityFallback,
            submitting = state.reserveBusy,
            onDismiss = vm::dismissLoanSheet,
            onConfirm = vm::reserve,
        )
    }
}

/**
 * Loan-request sheet. While [loading], shows a spinner. With a [calendar], renders the colored
 * [LoanCalendar] (availability-tinted month grid). If [fallback] (availability fetch failed),
 * shows a plain today-or-future [DatePicker] and a note. Returns the chosen date as yyyy-MM-dd.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanRequestSheet(
    loading: Boolean,
    calendar: AvailabilityCalendar?,
    fallback: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (isoDate: String) -> Unit,
) {
    val today = LocalDate.now()
    // Pre-select earliest_available when offered, else null until the user picks.
    var selectedIso by remember(calendar) {
        mutableStateOf(calendar?.earliestAvailable?.takeIf { it.isNotBlank() })
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selectedIso?.let(onConfirm) },
                enabled = selectedIso != null && !submitting,
            ) { Text(stringResource(R.string.book_loan_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_loan_cancel)) }
        },
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg).fillMaxWidth()) {
            Text(
                stringResource(R.string.book_loan_dialog_title),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                loading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            stringResource(R.string.book_loan_availability_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                calendar != null && !fallback -> {
                    LoanCalendar(
                        calendar = calendar,
                        selected = selectedIso,
                        onSelect = { selectedIso = it },
                    )
                }

                else -> {
                    // Fallback: plain today-or-future picker.
                    FallbackDatePicker(
                        today = today,
                        selectedIso = selectedIso,
                        onSelect = { selectedIso = it },
                    )
                    Text(
                        stringResource(R.string.book_loan_availability_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }

            // Period hint / prompt.
            val hint = selectedIso?.let {
                stringResource(R.string.book_loan_period_hint, formatDisplayDate(it))
            } ?: stringResource(R.string.book_loan_pick_prompt)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
        }
    }
}

/** Plain Material3 date picker limited to today-or-future, used only when availability load fails. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FallbackDatePicker(
    today: LocalDate,
    selectedIso: String?,
    onSelect: (String) -> Unit,
) {
    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val initialMillis = selectedIso?.let {
        runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    } ?: todayUtcMillis
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
        },
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { onSelect(millisToIsoDate(it)) }
    }
    DatePicker(state = datePickerState, title = null, headline = null, showModeToggle = false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailContent(
    book: BookDetail,
    wishlisted: Boolean,
    reserveBusy: Boolean,
    canBorrow: Boolean,
    showWishlist: Boolean,
    showReviews: Boolean,
    onReserve: () -> Unit,
    onToggleWishlist: () -> Unit,
    onShowMessage: (String) -> Unit,
    onFindWorks: (String, Int?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var showCover by remember { mutableStateOf(false) }
    var selectedPdf by remember(book.id) { mutableStateOf<String?>(null) }
    var selectedAudio by remember(book.id) { mutableStateOf<String?>(book.audioUrl?.takeIf { book.hasAudio }) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Brush.verticalGradient(listOf(LocalPinakesColors.current.heroWash, LocalPinakesColors.current.background), endY = 1200f))
            .padding(Spacing.lg),
    ) {
        val colors = LocalPinakesColors.current
        BookCover(book.title, book.coverUrl, Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(2f / 3f)
            .align(Alignment.CenterHorizontally).then(if (bookCoverImageUrl(book.coverUrl) != null)
                Modifier.clickable(onClickLabel = stringResource(R.string.cd_cover_zoom)) { showCover = true } else Modifier),
            author = book.authorsLabel, publisher = book.publisher)
        Spacer(Modifier.height(32.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()) {
            Surface(shape = RoundedCornerShape(50), color = colors.surface, border = BorderStroke(1.dp, colors.line)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaTypeIcon(book.mediaType)
                    Text(book.mediaType?.takeIf { it.isNotBlank() } ?: stringResource(R.string.title_book), style = MaterialTheme.typography.labelMedium)
                }
            }
            book.publisher?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.accentText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            book.year?.let { Text(if (book.publisher.isNullOrBlank()) "$it" else "· $it", color = colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Text(book.title, style = MaterialTheme.typography.headlineLarge, color = colors.ink)
        if (!book.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(book.subtitle!!, style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Italic, color = colors.muted)
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (book.authors.isEmpty()) Text(stringResource(R.string.restyle_unknown_author), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            book.authors.forEach { author ->
                Surface(shape = RoundedCornerShape(50), color = colors.surface, border = BorderStroke(1.dp, colors.line)) {
                    Row(Modifier.clickable { onFindWorks(author.name, author.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val initials = author.name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).map { it.first() }.joinToString("")
                        Box(Modifier.size(24.dp).background(colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                            Text(initials, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentStrong)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(author.name + (author.role?.takeIf { it.isNotBlank() && it != "autore" && it != "author" }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        CitationButton(book.citations, book.risUrl)
        val genrePath = book.genrePath.takeIf { it.isNotEmpty() }?.joinToString(" › ") { it.name } ?: book.genre?.let { genre ->
            listOfNotNull(genre.grandparent, genre.parent, genre.name, genre.subgenre)
                .filter { it.isNotBlank() }.distinct().joinToString(" › ")
        }
        genrePath?.takeIf { it.isNotBlank() }?.let { genre ->
            Spacer(Modifier.height(16.dp))
            GenrePath(genre)
        }
        Spacer(Modifier.height(24.dp))
        Surface(shape = MaterialTheme.shapes.large, color = colors.surface, border = BorderStroke(1.dp, colors.line),
            shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            val availableLabel = stringResource(R.string.book_copies_available_of, book.copiesAvailable, book.copiesTotal)
            val (availStatus, availLabel) = when (book.availability.state) {
                "available" -> AvailabilityStatus.Available to stringResource(R.string.availability_available)
                "on_loan" -> AvailabilityStatus.Unavailable to stringResource(R.string.book_on_loan)
                "reserved" -> AvailabilityStatus.DueSoon to stringResource(R.string.availability_reserved)
                "unavailable" -> AvailabilityStatus.Unavailable to stringResource(R.string.availability_unavailable)
                else -> if (book.available) AvailabilityStatus.Available to stringResource(R.string.availability_available)
                    else AvailabilityStatus.Unavailable to stringResource(R.string.book_on_loan)
            }
            AvailabilityChip(availStatus, availLabel)
            Text(availableLabel, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Spacer(Modifier.height(16.dp))

        // Personal-status banner: makes the user's own relationship to this book explicit,
        // so a generic "available" chip isn't the only signal. The wishlist-only variant is
        // suppressed when the wishlist feature is off.
        PersonalStatusBanner(book.personalHistory, wishlisted, showWishlist)

        // Personal history chips
        book.personalHistory?.let { ph ->
            val chips = buildList {
                if (canBorrow && ph.hasActiveLoan) add(stringResource(R.string.book_history_on_loan_to_you) to AvailabilityStatus.LoanActive)
                if (canBorrow && ph.hasPendingRequest) add(stringResource(R.string.book_history_pending) to AvailabilityStatus.ReservedReady)
                if (canBorrow && ph.hasReserved) add(stringResource(R.string.book_history_reserved) to AvailabilityStatus.ReservedReady)
                if (showWishlist && (ph.hasWishlisted || wishlisted)) add(stringResource(R.string.book_history_wishlisted) to AvailabilityStatus.Digital)
                if (ph.hasRead) add(stringResource(R.string.book_history_previously_read) to AvailabilityStatus.Available)
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { (label, status) -> AvailabilityChip(status = status, label = label) }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }

        // Actions — the loan/reserve button and the wishlist toggle are each gated by their
        // feature flag. In CATALOGUE-ONLY MODE the whole row collapses (book stays readable).
        // The action stays available even when the user already has a loan/reservation/pending
        // request: the personal-status banner shows that state, and the date calendar (opened by
        // this button) is the gate — it paints already-borrowed/reserved days red and pre-selects
        // the first free day, so a request can only target a date when the title is actually free.
        // The label adapts: "Request loan" when a copy is free now, "Reserve" when it isn't.
        val showLoanButton = canBorrow
        if (showLoanButton || showWishlist) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showLoanButton) {
                    PrimaryButton(
                        label = if (book.available) stringResource(R.string.book_request_loan) else stringResource(R.string.book_reserve),
                        onClick = onReserve,
                        loading = reserveBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showWishlist) {
                    SecondaryButton(
                        label = if (wishlisted) stringResource(R.string.book_wishlisted) else stringResource(R.string.book_wishlist),
                        onClick = onToggleWishlist,
                        leadingIcon = if (wishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        }
        }
        Spacer(Modifier.height(24.dp))
        BookQuickFacts(listOfNotNull(
            book.year?.let { stringResource(R.string.book_meta_year) to it.toString() },
            book.pages?.let { stringResource(R.string.book_meta_pages) to it.toString() },
            book.format?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.book_meta_format) to it },
            book.isbn13?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.book_meta_isbn13) to it },
        ))
        Spacer(Modifier.height(48.dp))

        val digitalFiles = book.digitalFiles
        if (digitalFiles.isNotEmpty()) {
            SectionTitle(stringResource(R.string.restyle_digital))
            Spacer(Modifier.height(16.dp))
        }
        digitalFiles.forEach { file ->
            val audio = file.kind == "audio"
            DigitalFileCard(type = file.format.ifBlank { if (audio) "Audio" else "eBook" },
                name = file.label.ifBlank { digitalFilename(file.url, book.title) },
                kind = stringResource(if (audio) R.string.book_section_audiobook else if (file.kind == "supplement") R.string.book_section_related_documents else R.string.book_section_ebook)) {
                if (audio && selectedAudio == file.url) {
                    androidx.compose.runtime.key(file.url) { AudioPlayer(audioUrl = file.url) }
                } else PrimaryButton(label = stringResource(if (audio) R.string.collection_play else if (file.isPdf) R.string.book_read else R.string.ebook_open_external),
                    onClick = {
                        if (audio) selectedAudio = file.url
                        else if (file.isPdf) selectedPdf = file.url
                        else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.url))) }
                            .onFailure { onShowMessage(context.getString(R.string.ebook_error)) }
                    }, modifier = Modifier.fillMaxWidth(), dark = true)
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(32.dp))

        if (!book.description.isNullOrBlank()) {
            SectionTitle(stringResource(R.string.book_section_about))
            Spacer(Modifier.height(Spacing.sm))
            HtmlText(
                html = book.description!!,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        SectionTitle(stringResource(R.string.book_section_details))
        Spacer(Modifier.height(Spacing.sm))
        // Metadata grouped into one rounded card: a clean key/value list reads
        // as a single "spec sheet" rather than rows floating on the background.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
                // Blank guards: the API can send an empty string (not null) for a
                // missing field — don't render a labelled row with no value.
                (book.publishers.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.name } ?: book.publisher)?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_publisher), it) }
                book.edition?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_edition), it) }
                book.publicationPlace?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_publication_place), it) }
                book.year?.let { MetadataRow(stringResource(R.string.book_meta_year), it.toString()) }
                book.language?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_language), it) }
                book.pages?.let { MetadataRow(stringResource(R.string.book_meta_pages), it.toString()) }
                book.format?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_format), it) }
                book.series?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_series), it) }
                book.isbn13?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_isbn13), it) }
                book.isbn10?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_isbn10), it) }
                book.ean?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_ean), it) }
                book.condition?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_condition), it) }
                book.locationLabel?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.book_meta_shelf), it) }
                MetadataRow(stringResource(R.string.book_meta_copies), stringResource(R.string.book_copies_value, book.copiesAvailable, book.copiesTotal))
            }
        }

        // Reviews — aggregate rating, the user's own review (borrowers can write/edit/delete),
        // and other users' reviews. Gated by the instance `reviews` feature flag.
        if (showReviews) {
            Spacer(Modifier.height(Spacing.xl))
            BookReviewsSection(
                bookId = book.id,
                canReviewFallback = book.personalHistory?.hasRead == true,
                onShowMessage = onShowMessage,
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
    }

    // Full-screen zoomable cover overlay.
    if (showCover && bookCoverImageUrl(book.coverUrl) != null) {
        ZoomableCoverDialog(
            imageUrl = book.coverUrl!!,
            contentDescription = book.title,
            onDismiss = { showCover = false },
        )
    }

    // In-app PDF reader overlay.
    selectedPdf?.let { pdf ->
        PdfReaderDialog(
            pdfUrl = pdf,
            onDismiss = { selectedPdf = null },
        )
    }
}

/**
 * Personal-status banner driven by [personalHistory]. Priority: active loan (primary-tinted)
 * → pending request (neutral secondary-tinted) → reservation (tertiary-tinted) → wishlist
 * (subtle neutral). Renders nothing when the user has no relationship to the book.
 */
@Composable
private fun PersonalStatusBanner(personalHistory: PersonalHistory?, wishlisted: Boolean, showWishlist: Boolean) {
    val ph = personalHistory
    val hasPending = ph?.hasPendingRequest == true
    val hasReservation = ph?.hasReserved == true
    val onWishlist = showWishlist && (ph?.hasWishlisted == true || wishlisted)

    val (title, body, container, onContainer, icon) = when {
        ph?.hasActiveLoan == true -> BannerSpec(
            title = stringResource(R.string.book_personal_on_loan_title),
            body = stringResource(R.string.book_personal_on_loan_body),
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.AutoMirrored.Outlined.MenuBook,
        )
        hasPending -> BannerSpec(
            title = stringResource(R.string.book_personal_pending_title),
            body = stringResource(R.string.book_personal_pending_body),
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Outlined.HourglassEmpty,
        )
        hasReservation -> BannerSpec(
            title = stringResource(R.string.book_personal_reserved_title),
            body = stringResource(R.string.book_personal_reserved_body),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Outlined.Schedule,
        )
        onWishlist -> BannerSpec(
            title = stringResource(R.string.book_personal_wishlist_title),
            body = null,
            container = MaterialTheme.colorScheme.surfaceVariant,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Filled.Favorite,
        )
        else -> return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                if (!body.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(body, style = MaterialTheme.typography.bodySmall, color = onContainer)
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
}

private data class BannerSpec(
    val title: String,
    val body: String?,
    val container: androidx.compose.ui.graphics.Color,
    val onContainer: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun GenrePath(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.LocalOffer, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Consistent, high-contrast section heading for the book-detail screen. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun digitalFilename(url: String, fallback: String): String =
    Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: fallback

private val displayDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

/** "2026-06-20" (yyyy-MM-dd) → locale-formatted date for display. Falls back to the raw input. */
private fun formatDisplayDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(displayDateFormatter) }.getOrDefault(iso)

/** DatePicker UTC-midnight millis → yyyy-MM-dd. */
private fun millisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toString()
