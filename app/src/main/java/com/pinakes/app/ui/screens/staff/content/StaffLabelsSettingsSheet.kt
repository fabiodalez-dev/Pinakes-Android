package com.pinakes.app.ui.screens.staff.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LabelSettings
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The label form: dimensions as typed text (mm), validated 10..100 before saving. */
data class LabelForm(
    val width: String = "",
    val height: String = "",
    val padding: String = "",
    val showAppName: Boolean = true,
    val showTitle: Boolean = true,
    val showSubtitle: Boolean = false,
    val showAuthorPublisher: Boolean = true,
    val showDewey: Boolean = true,
) {
    fun dimension(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    /** Width and height in 10..100 mm; padding in 0..20 mm (blank keeps the stored value). */
    val widthValid: Boolean get() = dimension(width)?.let { it in 10.0..100.0 } == true
    val heightValid: Boolean get() = dimension(height)?.let { it in 10.0..100.0 } == true
    val paddingValid: Boolean get() = padding.isBlank() || dimension(padding)?.let { it in 0.0..20.0 } == true
    val valid: Boolean get() = widthValid && heightValid && paddingValid

    fun toSettings(): LabelSettings = LabelSettings(
        width = dimension(width), height = dimension(height), padding = dimension(padding),
        showAppName = showAppName, showTitle = showTitle, showSubtitle = showSubtitle,
        showAuthorPublisher = showAuthorPublisher, showDewey = showDewey,
    )

    companion object {
        private fun fmt(v: Double?): String = v?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()

        fun from(s: LabelSettings): LabelForm = LabelForm(
            width = fmt(s.width), height = fmt(s.height), padding = fmt(s.padding),
            showAppName = s.showAppName ?: true, showTitle = s.showTitle ?: true, showSubtitle = s.showSubtitle ?: false,
            showAuthorPublisher = s.showAuthorPublisher ?: true, showDewey = s.showDewey ?: true,
        )
    }
}

data class LabelsUiState(
    val form: LabelForm? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: StaffMessage? = null,
    val saved: Boolean = false,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffLabelsViewModel @Inject constructor(private val content: StaffContentRepository) : ViewModel() {
    private val _state = MutableStateFlow(LabelsUiState())
    val state: StateFlow<LabelsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val res = content.labelSettings()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(form = LabelForm.from(res.data), loading = false)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    /** The sheet closed after a save; the ViewModel outlives it (hub-scoped). */
    fun consumeSaved() = _state.update { it.copy(saved = false) }

    fun edit(transform: (LabelForm) -> LabelForm) = _state.update { s -> s.copy(form = s.form?.let(transform), error = null) }

    fun save() {
        val form = _state.value.form ?: return
        if (!form.valid || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val res = content.saveLabelSettings(form.toSettings())
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(saving = false, form = LabelForm.from(res.data), saved = true)
                    is ApiResult.Failure -> it.copy(saving = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }
}

/** Label size and content, shared by every label PDF the library prints. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffLabelsSettingsSheet(onDismiss: () -> Unit) {
    val vm: StaffLabelsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved, state.forbidden) {
        if (state.saved || state.forbidden) {
            vm.consumeSaved()
            onDismiss()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff_labels_settings), style = MaterialTheme.typography.headlineSmall)
            val form = state.form
            state.error?.let { InlineErrorBanner(message = it.resolve(), onRetry = if (form == null) vm::load else null) }
            when {
                state.loading -> LoadingState(modifier = Modifier.heightIn(max = 160.dp))
                form != null -> {
                    Text(stringResource(R.string.staff_labels_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MmField(stringResource(R.string.staff_labels_width), form.width, !form.widthValid, Modifier.weight(1f)) { v -> vm.edit { it.copy(width = v) } }
                        MmField(stringResource(R.string.staff_labels_height), form.height, !form.heightValid, Modifier.weight(1f)) { v -> vm.edit { it.copy(height = v) } }
                    }
                    MmField(stringResource(R.string.staff_labels_padding), form.padding, !form.paddingValid, Modifier.fillMaxWidth()) { v -> vm.edit { it.copy(padding = v) } }
                    SwitchRow(stringResource(R.string.staff_labels_show_app_name), form.showAppName, { v -> vm.edit { it.copy(showAppName = v) } })
                    SwitchRow(stringResource(R.string.staff_labels_show_title), form.showTitle, { v -> vm.edit { it.copy(showTitle = v) } })
                    SwitchRow(stringResource(R.string.staff_labels_show_subtitle), form.showSubtitle, { v -> vm.edit { it.copy(showSubtitle = v) } })
                    SwitchRow(stringResource(R.string.staff_labels_show_author_publisher), form.showAuthorPublisher, { v -> vm.edit { it.copy(showAuthorPublisher = v) } })
                    SwitchRow(stringResource(R.string.staff_labels_show_dewey), form.showDewey, { v -> vm.edit { it.copy(showDewey = v) } })
                    PrimaryButton(stringResource(R.string.action_save), onClick = vm::save, loading = state.saving, enabled = form.valid, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun MmField(label: String, value: String, error: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    PinakesTextField(
        value = value, onValueChange = onChange, label = label, isError = error,
        errorText = stringResource(R.string.staff_labels_range), maxLength = 6,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}
