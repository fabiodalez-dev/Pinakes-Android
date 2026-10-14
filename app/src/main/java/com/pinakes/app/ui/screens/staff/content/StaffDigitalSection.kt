package com.pinakes.app.ui.screens.staff.content

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The digital kinds of the Digital Library plugin. */
enum class DigitalKind(val wire: String, val mimes: Array<String>) {
    EBOOK("ebook", arrayOf("application/pdf", "application/epub+zip")),
    AUDIO("audio", arrayOf("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/ogg", "audio/wav")),
}

data class DigitalUiState(val uploading: DigitalKind? = null, val message: StaffMessage? = null, val forbidden: Boolean = false)

/**
 * Attach an eBook or an audiobook to a record (Digital Library plugin, `staff_digital_library`).
 * The file is streamed from the document provider, never held in memory, on the long-timeout client.
 */
@HiltViewModel
class StaffDigitalViewModel @Inject constructor(
    private val content: StaffContentRepository,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val bookId: Int = savedState.get<Int>(Routes.ARG_BOOK_ID) ?: 0
    private val _state = MutableStateFlow(DigitalUiState())
    val state: StateFlow<DigitalUiState> = _state.asStateFlow()

    fun upload(kind: DigitalKind, uri: Uri?) {
        if (uri == null || bookId <= 0 || _state.value.uploading != null) return
        _state.update { it.copy(uploading = kind) }
        viewModelScope.launch {
            val resolver = context.contentResolver
            var name = "${kind.wire}.bin"
            var size = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
                    }
                }
            }
            val res = content.uploadDigital(bookId, kind.wire, name, resolver.getType(uri), size) {
                resolver.openInputStream(uri) ?: error("unreadable")
            }
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(uploading = null, message = StaffMessage(if (kind == DigitalKind.EBOOK) R.string.staff_digital_ebook_done else R.string.staff_digital_audio_done))
                    is ApiResult.Failure -> it.copy(uploading = null, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_digital_failed))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@Composable
fun StaffDigitalSection(onMessage: (String) -> Unit, onForbidden: () -> Unit) {
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    if (!areas.digital) return
    val vm: StaffDigitalViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val message = state.message?.resolve()
    LaunchedEffect(state.message) { if (!message.isNullOrBlank()) { onMessage(message); vm.consumeMessage() } }
    LaunchedEffect(state.forbidden) { if (state.forbidden) onForbidden() }
    val ebook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.upload(DigitalKind.EBOOK, it) }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.upload(DigitalKind.AUDIO, it) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("digital-section")) {
        FormSectionTitle(stringResource(R.string.staff_digital_title))
        Text(stringResource(R.string.staff_digital_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SecondaryButton(
            stringResource(if (state.uploading == DigitalKind.EBOOK) R.string.staff_digital_uploading else R.string.staff_digital_ebook),
            onClick = { ebook.launch(DigitalKind.EBOOK.mimes) }, enabled = state.uploading == null,
            leadingIcon = Icons.AutoMirrored.Outlined.MenuBook, modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            stringResource(if (state.uploading == DigitalKind.AUDIO) R.string.staff_digital_uploading else R.string.staff_digital_audio),
            onClick = { audio.launch(DigitalKind.AUDIO.mimes) }, enabled = state.uploading == null,
            leadingIcon = Icons.Outlined.Headphones, modifier = Modifier.fillMaxWidth(),
        )
    }
}
