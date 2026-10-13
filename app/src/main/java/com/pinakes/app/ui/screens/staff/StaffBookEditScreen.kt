package com.pinakes.app.ui.screens.staff

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.staff.FormField
import com.pinakes.app.data.staff.StaffBookForm
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.theme.Spacing
import java.io.File

/**
 * Create or edit a catalogue record (`POST`/`PUT /staff/books`). Used for books without an
 * ISBN (sheet music, pamphlets, objects) as much as for scanned ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffBookEditScreen(
    onNavigateUp: () -> Unit,
    onDone: () -> Unit,
    onOpenExisting: (Int) -> Unit,
) {
    val vm: StaffBookEditViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    ForbiddenExit(state.forbidden, onNavigateUp)

    val message = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() } }

    LaunchedEffect(state.event) {
        when (val e = state.event) {
            is StaffEditEvent.Saved -> {
                Toast.makeText(context, context.getString(R.string.staff_book_saved), Toast.LENGTH_SHORT).show()
                vm.consumeEvent()
                onDone()
            }
            StaffEditEvent.Deleted -> {
                Toast.makeText(context, context.getString(R.string.staff_book_deleted), Toast.LENGTH_SHORT).show()
                vm.consumeEvent()
                onDone()
            }
            is StaffEditEvent.OpenExisting -> {
                vm.consumeEvent()
                onOpenExisting(e.bookId)
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(if (state.isEdit) R.string.staff_book_edit else R.string.staff_book_new),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state.isEdit && state.original != null) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_book_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        label = stringResource(R.string.staff_book_save),
                        onClick = vm::save,
                        loading = state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.loadError != null -> ErrorState(
                    message = state.loadError?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff_error_load),
                    onRetry = vm::load,
                )
                else -> BookForm(state, vm)
            }
        }
    }

    if (state.duplicate != null) DuplicateDialog(state, vm)
    if (state.picker != null) PickerSheet(state, vm)
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.staff_book_delete),
            body = stringResource(R.string.staff_book_delete_confirm, state.form.title),
            confirmLabel = stringResource(R.string.staff_book_delete),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookForm(state: StaffBookEditUiState, vm: StaffBookEditViewModel) {
    val f = state.form
    val errors = state.errors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Spacer(Modifier.height(Spacing.sm))
        state.lookupNoteRes?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val errorText = state.error ?: state.errorRes?.let { stringResource(it) }
        if (errorText != null) InlineErrorBanner(message = errorText)

        CoverSection(state, vm)

        FormSectionTitle(stringResource(R.string.staff_section_identity))
        PinakesTextField(
            value = f.title, onValueChange = { v -> vm.edit { it.copy(title = v) } },
            label = stringResource(R.string.staff_field_title), modifier = Modifier.fillMaxWidth(),
            isError = FormField.TITLE in errors, errorText = stringResource(R.string.staff_field_required), maxLength = 500,
        )
        TextRow(stringResource(R.string.staff_field_subtitle), f.subtitle) { v -> vm.edit { it.copy(subtitle = v) } }
        IdentifierField(stringResource(R.string.staff_field_isbn13), f.isbn13, FormField.ISBN13 in errors) { v -> vm.edit { it.copy(isbn13 = v) } }
        IdentifierField(stringResource(R.string.staff_field_isbn10), f.isbn10, FormField.ISBN10 in errors) { v -> vm.edit { it.copy(isbn10 = v) } }
        IdentifierField(stringResource(R.string.staff_field_ean), f.ean, FormField.EAN in errors) { v -> vm.edit { it.copy(ean = v) } }
        IdentifierField(stringResource(R.string.staff_field_issn), f.issn, FormField.ISSN in errors) { v -> vm.edit { it.copy(issn = v) } }

        FormSectionTitle(stringResource(R.string.staff_section_people))
        Text(stringResource(R.string.staff_authors), style = MaterialTheme.typography.labelLarge)
        PickedChips(f.authors, vm::removeAuthor)
        SecondaryButton(stringResource(R.string.staff_add_author), onClick = { vm.openPicker(PickerKind.AUTHORS) }, leadingIcon = Icons.Outlined.Add)
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.staff_publishers), style = MaterialTheme.typography.labelLarge)
        PickedChips(f.publishers, vm::removePublisher)
        SecondaryButton(stringResource(R.string.staff_add_publisher), onClick = { vm.openPicker(PickerKind.PUBLISHERS) }, leadingIcon = Icons.Outlined.Add)

        FormSectionTitle(stringResource(R.string.staff_section_classification))
        PickerRow(
            label = stringResource(R.string.staff_field_genre), value = f.genre?.name,
            placeholder = stringResource(R.string.staff_option_none), onClick = { vm.openPicker(PickerKind.GENRE) },
            onClear = { vm.edit { it.copy(genre = null, subgenre = null) } },
        )
        if (f.genre != null) {
            PickerRow(
                label = stringResource(R.string.staff_field_subgenre), value = f.subgenre?.name,
                placeholder = stringResource(R.string.staff_option_none), onClick = { vm.openPicker(PickerKind.SUBGENRE) },
                onClear = { vm.edit { it.copy(subgenre = null) } },
            )
        }
        LookupField(
            label = stringResource(R.string.staff_field_media_type), value = f.mediaType,
            displayValue = state.lookups.mediaTypeOptions.firstOrNull { it.value == f.mediaType }?.label ?: f.mediaType,
            options = state.lookups.mediaTypeOptions, editable = false,
            onValueChange = { v -> vm.edit { it.copy(mediaType = v) } }, modifier = Modifier.fillMaxWidth(),
        )
        LookupField(stringResource(R.string.staff_field_language), f.language, state.lookups.languageOptions,
            { v -> vm.edit { it.copy(language = v) } }, Modifier.fillMaxWidth())
        LookupField(stringResource(R.string.staff_field_format), f.format, state.lookups.formatOptions,
            { v -> vm.edit { it.copy(format = v) } }, Modifier.fillMaxWidth())
        TextRow(stringResource(R.string.staff_field_dewey), f.dewey) { v -> vm.edit { it.copy(dewey = v) } }

        FormSectionTitle(stringResource(R.string.staff_section_content))
        PinakesTextField(
            value = f.description, onValueChange = { v -> vm.edit { it.copy(description = v) } },
            label = stringResource(R.string.staff_field_description), singleLine = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        TextRow(stringResource(R.string.staff_field_keywords), f.keywords) { v -> vm.edit { it.copy(keywords = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PinakesTextField(
                value = f.year, onValueChange = { v -> vm.edit { it.copy(year = v) } }, label = stringResource(R.string.staff_field_year),
                isError = FormField.YEAR in errors, errorText = stringResource(R.string.staff_field_invalid),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), maxLength = 5,
            )
            PinakesTextField(
                value = f.pages, onValueChange = { v -> vm.edit { it.copy(pages = v) } }, label = stringResource(R.string.staff_field_pages),
                isError = FormField.PAGES in errors, errorText = stringResource(R.string.staff_field_invalid),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), maxLength = 6,
            )
        }
        TextRow(stringResource(R.string.staff_field_pub_date), f.pubDate) { v -> vm.edit { it.copy(pubDate = v) } }
        TextRow(stringResource(R.string.staff_field_edition), f.edition) { v -> vm.edit { it.copy(edition = v) } }
        TextRow(stringResource(R.string.staff_field_place), f.place) { v -> vm.edit { it.copy(place = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PinakesTextField(value = f.series, onValueChange = { v -> vm.edit { it.copy(series = v) } },
                label = stringResource(R.string.staff_field_series), modifier = Modifier.weight(2f))
            PinakesTextField(value = f.seriesNumber, onValueChange = { v -> vm.edit { it.copy(seriesNumber = v) } },
                label = stringResource(R.string.staff_field_series_number), modifier = Modifier.weight(1f))
        }

        FormSectionTitle(stringResource(R.string.staff_section_holdings))
        if (state.isEdit) {
            val original = state.original
            Text(
                stringResource(R.string.staff_copies_count, original?.copiesAvailable ?: 0, original?.copiesTotal ?: 0),
                style = MaterialTheme.typography.bodyLarge,
            )
            QuantityStepper(stringResource(R.string.staff_copies_to_add), state.addCopies, vm::setAddCopies, enabled = !state.addingCopies)
            SecondaryButton(
                label = stringResource(R.string.staff_add_copies), onClick = vm::addCopies,
                enabled = !state.addingCopies, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            QuantityStepper(stringResource(R.string.staff_copies), f.copies, { n -> vm.edit { it.copy(copies = n) } },
                range = StaffBookForm.COPIES_RANGE)
        }
        ShelfFields(state, vm)

        FormSectionTitle(stringResource(R.string.staff_section_acquisition))
        TextRow(stringResource(R.string.staff_field_acquisition_date), f.acquisitionDate, placeholder = "2026-01-31") { v -> vm.edit { it.copy(acquisitionDate = v) } }
        LookupField(stringResource(R.string.staff_field_acquisition_type), f.acquisitionType, state.lookups.acquisitionTypeOptions,
            { v -> vm.edit { it.copy(acquisitionType = v) } }, Modifier.fillMaxWidth())
        PinakesTextField(
            value = f.price, onValueChange = { v -> vm.edit { it.copy(price = v) } }, label = stringResource(R.string.staff_field_price),
            isError = FormField.PRICE in errors, errorText = stringResource(R.string.staff_field_invalid),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        TextRow(stringResource(R.string.staff_field_inventory_base), f.inventoryBase) { v -> vm.edit { it.copy(inventoryBase = v) } }
        PinakesTextField(
            value = f.notes, onValueChange = { v -> vm.edit { it.copy(notes = v) } }, label = stringResource(R.string.staff_field_notes),
            singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun TextRow(label: String, value: String, placeholder: String = "", onChange: (String) -> Unit) {
    PinakesTextField(value = value, onValueChange = onChange, label = label, placeholder = placeholder, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun IdentifierField(label: String, value: String, isError: Boolean, onChange: (String) -> Unit) {
    PinakesTextField(
        value = value, onValueChange = onChange, label = label, modifier = Modifier.fillMaxWidth(),
        isError = isError, errorText = stringResource(R.string.staff_identifier_invalid),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), maxLength = 20,
    )
}

@Composable
private fun ShelfFields(state: StaffBookEditUiState, vm: StaffBookEditViewModel) {
    val f = state.form
    val shelfOptions = state.shelves.map { LookupOption(it.id.toString(), it.display) }
    LookupField(
        label = stringResource(R.string.staff_shelf),
        value = f.shelfId?.toString().orEmpty(),
        displayValue = state.shelves.firstOrNull { it.id == f.shelfId }?.display.orEmpty(),
        options = shelfOptions, editable = false,
        onValueChange = { v -> vm.selectShelf(v.toIntOrNull()) }, modifier = Modifier.fillMaxWidth(),
    )
    if (f.shelfId != null) {
        val levelOptions = state.levels.map { level ->
            LookupOption(level.id.toString(), listOfNotNull(stringResource(R.string.staff_level_number, level.number), level.description?.takeIf { it.isNotBlank() }).joinToString(" · "))
        }
        LookupField(
            label = stringResource(R.string.staff_level),
            value = f.levelId?.toString().orEmpty(),
            displayValue = levelOptions.firstOrNull { it.value == f.levelId?.toString() }?.label.orEmpty(),
            options = levelOptions, editable = false,
            onValueChange = { v -> vm.selectLevel(v.toIntOrNull()) }, modifier = Modifier.fillMaxWidth(),
        )
        if (FormField.LEVEL in state.errors) {
            Text(stringResource(R.string.staff_level_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (f.shelfId != null && f.levelId != null) {
        val next = state.nextPosition
        Text(
            if (next?.label != null) stringResource(R.string.staff_position_auto_label, next.label)
            else stringResource(R.string.staff_position_auto),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (state.isEdit) {
        state.original?.location?.label?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverSection(state: StaffBookEditUiState, vm: StaffBookEditViewModel) {
    val context = LocalContext.current
    var urlDialog by remember { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) vm.setPendingCover(PendingCover.FromImage(Uri.parse(uri)))
    }
    fun launchCamera() {
        val uri = newCoverPhotoUri(context)
        photoUri = uri.toString()
        runCatching { takePicture.launch(uri) }.onFailure {
            Toast.makeText(context, context.getString(R.string.staff_camera_unavailable), Toast.LENGTH_SHORT).show()
        }
    }
    // The app declares CAMERA (scanner), so the system camera intent needs it granted too.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, context.getString(R.string.staff_scan_permission_rationale), Toast.LENGTH_LONG).show()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setPendingCover(PendingCover.FromImage(uri))
    }

    FormSectionTitle(stringResource(R.string.staff_section_cover))
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        val pending = state.pendingCover
        val coverModifier = Modifier.width(96.dp).aspectRatio(2f / 3f)
        when (pending) {
            is PendingCover.FromImage -> AsyncImage(
                model = pending.uri, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = coverModifier.clip(RoundedCornerShape(4.dp)),
            )
            is PendingCover.FromUrl -> BookCover(state.form.title, pending.url, coverModifier, compact = true)
            null -> BookCover(state.form.title, state.original?.coverUrl ?: state.form.coverUrl.takeIf { it.isNotBlank() }, coverModifier, compact = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (pending != null) {
                Text(stringResource(R.string.staff_cover_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PinakesTextButton(stringResource(R.string.staff_cover_discard), onClick = { vm.setPendingCover(null) })
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SecondaryButton(stringResource(R.string.staff_cover_from_url), onClick = { urlDialog = true }, leadingIcon = Icons.Outlined.Link)
        SecondaryButton(stringResource(R.string.staff_cover_take_photo), leadingIcon = Icons.Outlined.CameraAlt, onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
            else permission.launch(Manifest.permission.CAMERA)
        })
        SecondaryButton(stringResource(R.string.staff_cover_pick), leadingIcon = Icons.Outlined.Image, onClick = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
    }

    if (urlDialog) {
        var url by remember { mutableStateOf("") }
        val valid = StaffBookForm.isHttpUrl(url)
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.staff_cover_from_url), style = MaterialTheme.typography.titleMedium) },
            text = {
                PinakesTextField(
                    value = url, onValueChange = { url = it.trim() }, label = stringResource(R.string.staff_cover_url_label),
                    placeholder = "https://", isError = url.isNotBlank() && !valid, errorText = stringResource(R.string.staff_field_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                PrimaryButton(stringResource(R.string.action_confirm), enabled = valid, onClick = {
                    vm.setPendingCover(PendingCover.FromUrl(url))
                    urlDialog = false
                })
            },
            dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = { urlDialog = false }) },
        )
    }
}

/** A fresh cache file exposed through the FileProvider for the camera app to write into. */
internal fun newCoverPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "covers").apply { mkdirs() }
    // Old captures are useless once uploaded; keep the directory from growing.
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }?.forEach { it.delete() }
    val file = File(dir, "cover_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun DuplicateDialog(state: StaffBookEditUiState, vm: StaffBookEditViewModel) {
    val existing = state.duplicate ?: return
    AlertDialog(
        onDismissRequest = vm::dismissDuplicate,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.staff_book_duplicate_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.staff_book_duplicate_body), style = MaterialTheme.typography.bodyMedium)
                if (existing.title.isNotBlank()) Text(existing.title, style = MaterialTheme.typography.titleSmall)
                if (existing.copiesTotal > 0) {
                    Text(stringResource(R.string.staff_copies_total, existing.copiesTotal), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                existing.locationLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (existing.id > 0) {
                    PrimaryButton(stringResource(R.string.staff_scan_open_existing), onClick = vm::openExisting)
                    SecondaryButton(stringResource(R.string.staff_scan_add_copy), onClick = vm::addCopyToExisting, enabled = !state.saving)
                }
                PinakesTextButton(stringResource(R.string.staff_change_identifier), onClick = vm::dismissDuplicate)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(state: StaffBookEditUiState, vm: StaffBookEditViewModel) {
    val kind = state.picker ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = when (kind) {
        PickerKind.AUTHORS -> R.string.staff_add_author
        PickerKind.PUBLISHERS -> R.string.staff_add_publisher
        PickerKind.GENRE -> R.string.staff_field_genre
        PickerKind.SUBGENRE -> R.string.staff_field_subgenre
    }
    val allowNew = kind == PickerKind.AUTHORS || kind == PickerKind.PUBLISHERS
    ModalBottomSheet(onDismissRequest = vm::closePicker, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding()) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.md))
            SearchField(query = state.pickerQuery, onQueryChange = vm::onPickerQuery, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                val query = state.pickerQuery.trim()
                if (allowNew && query.isNotEmpty() && state.pickerResults.none { it.label.equals(query, ignoreCase = true) }) {
                    item(key = "new") {
                        PickerLine(stringResource(R.string.staff_entity_new, query), null, highlight = true) { vm.pick(null) }
                        HorizontalDivider()
                    }
                }
                if (state.pickerLoading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                items(state.pickerResults, key = { "${it.id}-${it.label}" }) { item ->
                    PickerLine(item.label, item.detail) { vm.pick(item) }
                }
                if (!state.pickerLoading && state.pickerResults.isEmpty() && (query.isEmpty() || !allowNew)) {
                    item(key = "empty") {
                        Text(stringResource(R.string.staff_picker_empty), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.lg))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun PickerLine(label: String, detail: String?, highlight: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.md)) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
