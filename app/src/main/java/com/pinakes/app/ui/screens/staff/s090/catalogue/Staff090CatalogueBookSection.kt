package com.pinakes.app.ui.screens.staff.s090.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090CataloguePaths
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.BookCataloguing
import com.pinakes.app.data.staff.CataloguingModules
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The 0.9.0 cataloguing parts of the staff book screen: the supplier and RDA fields of the form,
// and the catalogue card / MARC export of the record.

/** The three RDA kinds: JSON key of the effective triple and the list in the lookups. */
private enum class RdaKind { CONTENT, MEDIA, CARRIER }

/**
 * Supplier picker (module `suppliers`) and the RDA content / media / carrier selects (module
 * `rda_types`) of the book form. Each is shown only while the server's lookups carry its
 * list; "derived" (null) names the value the server derives from the media type.
 */
@Composable
fun Staff090CatalogueBookFields(lookups: StaffLookups, original: StaffBook?, value: BookCataloguing, onChange: (BookCataloguing) -> Unit) {
    val modules = CataloguingModules.from(lookups)
    if (modules.suppliers) {
        val suppliers = lookups.suppliers.orEmpty().asOptions()
        val current = value.supplierId?.toString().orEmpty()
        LookupField(
            label = stringResource(R.string.staff090_catalogue_supplier), value = current,
            // A supplier the list no longer has (just deleted elsewhere) still shows its name.
            displayValue = suppliers.firstOrNull { it.value == current }?.label ?: original?.supplier?.takeIf { it.id == value.supplierId }?.name ?: current,
            options = suppliers, editable = false,
            onValueChange = { v -> onChange(value.copy(supplierId = v.toIntOrNull()?.takeIf { it > 0 })) },
            modifier = Modifier.fillMaxWidth().testTag("book-supplier"),
        )
    }
    if (modules.rda) {
        Text(stringResource(R.string.staff090_catalogue_rda_title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.staff090_catalogue_rda_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RdaKind.entries.forEach { kind ->
            val raw = when (kind) {
                RdaKind.CONTENT -> lookups.rdaContentTypes
                RdaKind.MEDIA -> lookups.rdaMediaTypes
                RdaKind.CARRIER -> lookups.rdaCarrierTypes
            }
            val list = StaffLookups.options(raw.orEmpty())
            val effective = when (kind) {
                RdaKind.CONTENT -> original?.rdaEffective?.contentType
                RdaKind.MEDIA -> original?.rdaEffective?.mediaType
                RdaKind.CARRIER -> original?.rdaEffective?.carrierType
            }
            val stored = when (kind) {
                RdaKind.CONTENT -> value.rdaContentType
                RdaKind.MEDIA -> value.rdaMediaType
                RdaKind.CARRIER -> value.rdaCarrierType
            }
            val ownOverride = when (kind) {
                RdaKind.CONTENT -> original?.rdaContentType
                RdaKind.MEDIA -> original?.rdaMediaType
                RdaKind.CARRIER -> original?.rdaCarrierType
            }
            // The effective value is the derived one only where the stored record has no override.
            val derivedLabel = rdaDerivedLabel(list, effective.takeIf { original != null && ownOverride == null })
            val options = listOf(LookupOption("", derivedLabel)) + list
            LookupField(
                label = stringResource(
                    when (kind) {
                        RdaKind.CONTENT -> R.string.staff090_catalogue_rda_content
                        RdaKind.MEDIA -> R.string.staff090_catalogue_rda_media
                        RdaKind.CARRIER -> R.string.staff090_catalogue_rda_carrier
                    },
                ),
                value = stored.orEmpty(), displayValue = if (stored == null) derivedLabel else list.labelOf(stored),
                options = options, editable = false, allowClear = false,
                onValueChange = { v ->
                    val code = v.takeIf { it.isNotBlank() }
                    onChange(
                        when (kind) {
                            RdaKind.CONTENT -> value.copy(rdaContentType = code)
                            RdaKind.MEDIA -> value.copy(rdaMediaType = code)
                            RdaKind.CARRIER -> value.copy(rdaCarrierType = code)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("book-rda-${kind.name.lowercase()}"),
            )
        }
    }
}

/** "Derived (Text)" with the server's derived value, or "Derived from the media type" when unknown. */
@Composable
private fun rdaDerivedLabel(list: List<LookupOption>, derived: String?): String {
    val label = derived?.let { code -> list.firstOrNull { it.value == code }?.label ?: code }
    return if (label != null) stringResource(R.string.staff090_catalogue_rda_derived_value, label)
    else stringResource(R.string.staff090_catalogue_rda_derived)
}

// =============================================================================================
// Catalogue card and MARC export of one record
// =============================================================================================

data class BookToolsUiState(
    val downloading: Boolean = false,
    val file: File? = null,
    val mime: String = "application/pdf",
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class Staff090CatalogueBookToolsViewModel @Inject constructor(
    private val files: StaffFilesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val bookId: Int = savedState.get<Int>(Routes.ARG_BOOK_ID) ?: 0
    private val _state = MutableStateFlow(BookToolsUiState())
    val state: StateFlow<BookToolsUiState> = _state.asStateFlow()

    fun catalogueCard() = download(Staff090CataloguePaths.catalogueCard(bookId), "scheda_$bookId.pdf", "application/pdf")

    fun exportMarc(format: String) {
        val path = Staff090CataloguePaths.marcExport(format, listOf(bookId)) ?: return
        download(path, Staff090CataloguePaths.exportFallbackName(format), Staff090CataloguePaths.exportMime(format))
    }

    private fun download(path: String, name: String, mime: String) {
        if (bookId <= 0 || _state.value.downloading) return
        _state.update { it.copy(downloading = true) }
        viewModelScope.launch {
            val res = files.download(path, name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(downloading = false, file = res.data, mime = mime)
                    is ApiResult.Failure -> it.copy(downloading = false, forbidden = res.isForbidden(), message = res.toCatalogueMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeFile() = _state.update { it.copy(file = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/**
 * The record's catalogue card PDF (`staff_catalogue_cards`) and its MARC export (`staff_marc`).
 * The staff book list has no selection mode, so the multi-book versions live in the MARC
 * screen's export tab.
 */
@Composable
fun Staff090CatalogueBookTools(onMessage: (String) -> Unit, onForbidden: () -> Unit) {
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    if (!areas.s090.catalogueCards && !areas.s090.marc) return
    val vm: Staff090CatalogueBookToolsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val message = state.message?.resolve()
    LaunchedEffect(state.message) { if (!message.isNullOrBlank()) { onMessage(message); vm.consumeMessage() } }
    LaunchedEffect(state.forbidden) { if (state.forbidden) onForbidden() }
    var chooseFormat by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("catalogue-tools")) {
        FormSectionTitle(stringResource(R.string.staff090_section_cataloguing))
        if (areas.s090.catalogueCards) {
            SecondaryButton(
                stringResource(R.string.staff090_catalogue_card), onClick = vm::catalogueCard, enabled = !state.downloading,
                leadingIcon = Icons.Outlined.Print, modifier = Modifier.fillMaxWidth().testTag("book-catalogue-card"),
            )
        }
        if (areas.s090.marc) {
            SecondaryButton(
                stringResource(R.string.staff090_catalogue_marc_export_book), onClick = { chooseFormat = true }, enabled = !state.downloading,
                leadingIcon = Icons.Outlined.Download, modifier = Modifier.fillMaxWidth().testTag("book-marc-export"),
            )
        }
    }
    if (chooseFormat) {
        var format by rememberSaveable { mutableStateOf(Staff090CataloguePaths.FORMATS.first()) }
        AlertDialog(
            onDismissRequest = { chooseFormat = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.staff090_catalogue_marc_format), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Staff090CataloguePaths.FORMATS.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = format == f, role = Role.RadioButton, onClick = { format = f }).padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = format == f, onClick = null)
                            Text(stringResource(formatLabel(f)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
            },
            confirmButton = { PrimaryButton(stringResource(R.string.staff090_catalogue_marc_export_run), onClick = { chooseFormat = false; vm.exportMarc(format) }) },
            dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = { chooseFormat = false }) },
        )
    }
    state.file?.let { ExportFileDialog(it, state.mime, stringResource(R.string.staff090_section_cataloguing), onDismiss = vm::closeFile) }
}

private fun formatLabel(format: String): Int = when (format) {
    "marcxml" -> R.string.staff090_catalogue_format_marcxml
    "unimarc" -> R.string.staff090_catalogue_format_unimarc
    else -> R.string.staff090_catalogue_format_marc21
}
