package com.pinakes.app.ui.screens.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.CustomFieldDef
import com.pinakes.app.data.model.CustomFieldValue
import com.pinakes.app.data.model.DeviceItem
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.screens.login.CustomFieldInput
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PasswordField
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing
import com.pinakes.app.ui.theme.ThemeMode

@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenMyReviews: () -> Unit,
    onOpenBookClub: () -> Unit,
) {
    val app: AppViewModel = hiltViewModel()
    val vm: ProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val features by app.features.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val snackbarMessage = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val p = state.profile) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.profile_loading))
                is UiState.Error -> ErrorState(message = p.resolvedMessage(), onRetry = vm::load)
                is UiState.Success -> ProfileContent(
                    profile = p.data,
                    devices = state.devices,
                    loggingOut = state.loggingOut,
                    onEdit = vm::startEdit,
                    onChangePassword = vm::startChangePassword,
                    onRevokeDevice = vm::revokeDevice,
                    onLogout = { vm.logout(onLoggedOut) },
                    onOpenNotifications = onOpenNotifications,
                    onOpenContact = onOpenContact,
                    onOpenMyReviews = onOpenMyReviews,
                    onOpenBookClub = onOpenBookClub,
                    showNotifications = features.notifications,
                    showContact = features.messages,
                    showReviews = features.showReviews,
                    showBookClub = features.bookClubAvailable,
                )
            }
        }
    }

    if (state.editing) {
        val editingProfile = (state.profile as? UiState.Success)?.data
        EditProfileDialog(
            nome = state.editNome,
            cognome = state.editCognome,
            telefono = state.editTelefono,
            indirizzo = state.editIndirizzo,
            dataNascita = state.editDataNascita,
            codFiscale = state.editCodFiscale,
            sesso = state.editSesso,
            telefonoRequired = vm.builtinRequired("telefono"),
            indirizzoRequired = vm.builtinRequired("indirizzo"),
            customFields = editingProfile?.customFields.orEmpty(),
            customValues = state.editCustomValues,
            saving = state.savingProfile,
            errorRes = state.editErrorRes,
            onNome = vm::onEditNome,
            onCognome = vm::onEditCognome,
            onTelefono = vm::onEditTelefono,
            onIndirizzo = vm::onEditIndirizzo,
            onDataNascita = vm::onEditDataNascita,
            onCodFiscale = vm::onEditCodFiscale,
            onSesso = vm::onEditSesso,
            onCustomField = vm::onEditCustomField,
            onSave = vm::saveEdit,
            onDismiss = vm::cancelEdit,
        )
    }

    if (state.changingPassword) {
        ChangePasswordDialog(
            current = state.pwCurrent,
            new = state.pwNew,
            confirm = state.pwConfirm,
            error = state.pwError ?: state.pwErrorRes?.let { stringResource(it) },
            saving = state.savingPassword,
            onCurrent = vm::onPwCurrent,
            onNew = vm::onPwNew,
            onConfirm = vm::onPwConfirm,
            onSave = vm::savePassword,
            onDismiss = vm::cancelChangePassword,
        )
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    devices: List<DeviceItem>,
    loggingOut: Boolean,
    onEdit: () -> Unit,
    onChangePassword: () -> Unit,
    onRevokeDevice: (Int) -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenMyReviews: () -> Unit,
    onOpenBookClub: () -> Unit,
    showNotifications: Boolean,
    showContact: Boolean,
    showReviews: Boolean,
    showBookClub: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        // Avatar + name
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = (profile.nome.take(1) + profile.cognome.take(1)).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                profile.fullName.ifBlank { profile.email },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                profile.tipoUtente.replaceFirstChar { it.uppercase() } +
                    if (!profile.emailVerificata) stringResource(R.string.profile_email_unverified) else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Read-only membership info (only shown when the server provides it).
        val cardNumber = profile.codiceTessera?.takeIf { it.isNotBlank() }
        val cardExpiry = profile.cardExpiresAt?.takeIf { it.isNotBlank() }
        if (cardNumber != null || cardExpiry != null) {
            Spacer(Modifier.height(Spacing.xl))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    cardNumber?.let { ReadOnlyRow(stringResource(R.string.profile_card_number), it) }
                    cardExpiry?.let { ReadOnlyRow(stringResource(R.string.profile_card_expires), DateFormat.date(it)) }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        // Actions
        ActionRow(Icons.Outlined.Edit, stringResource(R.string.profile_action_edit), onClick = onEdit)
        ActionRow(Icons.Outlined.Lock, stringResource(R.string.profile_action_change_password), onClick = onChangePassword)
        if (showReviews) {
            ActionRow(Icons.Outlined.RateReview, stringResource(R.string.profile_action_my_reviews), onClick = onOpenMyReviews)
        }
        if (showBookClub) {
            ActionRow(Icons.Outlined.Groups, stringResource(R.string.profile_action_book_club), onClick = onOpenBookClub)
        }
        if (showNotifications) {
            ActionRow(Icons.Outlined.Notifications, stringResource(R.string.profile_action_notifications), onClick = onOpenNotifications)
        }
        if (showContact) {
            ActionRow(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.profile_action_message_library), onClick = onOpenContact)
        }

        Spacer(Modifier.height(Spacing.xl))

        // Theme switcher
        ThemeSection()

        Spacer(Modifier.height(Spacing.xl))

        // Language switcher
        LanguageSection()

        Spacer(Modifier.height(Spacing.xl))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DevicesOther, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.profile_devices_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.sm))
        if (devices.isEmpty()) {
            Text(stringResource(R.string.profile_no_other_devices), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            devices.forEach { d -> DeviceRow(d, onRevoke = { onRevokeDevice(d.id) }) }
        }

        Spacer(Modifier.height(Spacing.xxl))

        PrimaryButton(
            label = stringResource(R.string.profile_sign_out),
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            loading = loggingOut,
            leadingIcon = Icons.AutoMirrored.Outlined.Logout,
        )
        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * In-app language switcher. Lets the user pick System default / Italiano / English / Français /
 * Deutsch; the choice is applied immediately via AppCompatDelegate.setApplicationLocales and
 * persisted by the AppLocalesMetadataHolderService (autoStoreLocales) declared in the manifest.
 */
@Composable
private fun LanguageSection() {
    var dialogOpen by remember { mutableStateOf(false) }

    // Current per-app locale ("" = follow system). Read from AppCompat so the row reflects
    // whatever is currently applied (it survives process death via autoStoreLocales).
    val currentTag = remember {
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .substringBefore('-')   // "it-IT" → "it"
            .lowercase()
    }

    val options = listOf(
        "" to stringResource(R.string.profile_language_system),
        "it" to stringResource(R.string.language_it),
        "en" to stringResource(R.string.language_en),
        "fr" to stringResource(R.string.language_fr),
        "de" to stringResource(R.string.language_de),
    )
    val currentLabel = options.firstOrNull { it.first == currentTag }?.second
        ?: stringResource(R.string.profile_language_system)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.profile_section_language), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(Spacing.sm))
    Surface(
        onClick = { dialogOpen = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(currentLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.profile_section_language), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    options.forEach { (tag, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(tag)
                                    AppCompatDelegate.setApplicationLocales(locales)
                                    dialogOpen = false
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == currentTag, onClick = null)
                            Spacer(Modifier.width(Spacing.md))
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { PinakesTextButton(label = stringResource(R.string.action_cancel), onClick = { dialogOpen = false }) },
        )
    }
}

/**
 * In-app theme switcher. Light / Dark / System default — applied immediately and persisted via
 * [com.pinakes.app.data.store.ThemeStore]. Default is Light. Mirrors [LanguageSection].
 */
@Composable
private fun ThemeSection() {
    val app: AppViewModel = hiltViewModel()
    val mode by app.themeMode.collectAsStateWithLifecycle()
    var dialogOpen by remember { mutableStateOf(false) }

    val options = listOf(
        ThemeMode.LIGHT to stringResource(R.string.profile_theme_light),
        ThemeMode.DARK to stringResource(R.string.profile_theme_dark),
        ThemeMode.SYSTEM to stringResource(R.string.profile_theme_system),
    )
    val currentLabel = options.firstOrNull { it.first == mode }?.second
        ?: stringResource(R.string.profile_theme_light)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.BrightnessMedium, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.profile_section_theme), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(Spacing.sm))
    Surface(
        onClick = { dialogOpen = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(currentLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.profile_section_theme), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    app.setThemeMode(value)
                                    dialogOpen = false
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == mode, onClick = null)
                            Spacer(Modifier.width(Spacing.md))
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { PinakesTextButton(label = stringResource(R.string.action_cancel), onClick = { dialogOpen = false }) },
        )
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceItem, onRevoke: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    (device.deviceName ?: stringResource(R.string.profile_unknown_device)) +
                        if (device.isCurrent) stringResource(R.string.profile_this_device) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val last = device.lastUsedAt ?: device.createdAt
                Text(stringResource(R.string.profile_device_last_used, DateFormat.dateTime(last)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!device.isCurrent) {
                TextButton(onClick = onRevoke) { Text(stringResource(R.string.profile_sign_out)) }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    nome: String,
    cognome: String,
    telefono: String,
    indirizzo: String,
    dataNascita: String,
    codFiscale: String,
    sesso: String,
    telefonoRequired: Boolean,
    indirizzoRequired: Boolean,
    customFields: List<CustomFieldValue>,
    customValues: Map<Int, String>,
    saving: Boolean,
    errorRes: Int?,
    onNome: (String) -> Unit,
    onCognome: (String) -> Unit,
    onTelefono: (String) -> Unit,
    onIndirizzo: (String) -> Unit,
    onDataNascita: (String) -> Unit,
    onCodFiscale: (String) -> Unit,
    onSesso: (String) -> Unit,
    onCustomField: (Int, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.profile_edit_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PinakesTextField(value = nome, onValueChange = onNome, label = stringResource(R.string.profile_first_name), modifier = Modifier.fillMaxWidth())
                PinakesTextField(value = cognome, onValueChange = onCognome, label = stringResource(R.string.profile_last_name), modifier = Modifier.fillMaxWidth())
                PinakesTextField(
                    value = telefono,
                    onValueChange = onTelefono,
                    label = requiredLabel(stringResource(R.string.register_phone), telefonoRequired),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                )
                PinakesTextField(
                    value = indirizzo,
                    onValueChange = onIndirizzo,
                    label = requiredLabel(stringResource(R.string.register_address), indirizzoRequired),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    value = dataNascita,
                    label = stringResource(R.string.profile_birth_date),
                    onValueChange = onDataNascita,
                )
                PinakesTextField(value = codFiscale, onValueChange = onCodFiscale, label = stringResource(R.string.profile_tax_code), modifier = Modifier.fillMaxWidth())
                PinakesTextField(value = sesso, onValueChange = onSesso, label = stringResource(R.string.profile_gender), modifier = Modifier.fillMaxWidth())
                // Instance-defined custom fields, pre-filled from the user's current values.
                customFields.forEach { f ->
                    CustomFieldInput(
                        def = CustomFieldDef(id = f.id, label = f.label, type = f.type, required = f.required),
                        value = customValues[f.id].orEmpty(),
                        onValueChange = { onCustomField(f.id, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { PrimaryButton(label = stringResource(R.string.action_save), onClick = onSave, loading = saving) },
        dismissButton = { PinakesTextButton(label = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

/** Appends a " *" marker to a field label when the instance requires it. */
private fun requiredLabel(label: String, required: Boolean): String = if (required) "$label *" else label

/**
 * A read/tap field showing a yyyy-MM-dd date; tapping opens a Material3 date picker (any date,
 * past-friendly for a birth date). Empty value shows nothing selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(value: String, label: String, onValueChange: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { pickerOpen = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { stringResource(R.string.profile_date_none) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    if (pickerOpen) {
        val initialMillis = value.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    pickerOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    // Clear an existing birth date (PATCH /me accepts an empty
                    // value → the field is cleared server-side). Only offered
                    // when there is a date to remove.
                    if (value.isNotBlank()) {
                        TextButton(onClick = { onValueChange(""); pickerOpen = false }) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                    TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    current: String,
    new: String,
    confirm: String,
    error: String?,
    saving: Boolean,
    onCurrent: (String) -> Unit,
    onNew: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.profile_change_password_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PasswordField(value = current, onValueChange = onCurrent, label = stringResource(R.string.profile_current_password), modifier = Modifier.fillMaxWidth())
                PasswordField(value = new, onValueChange = onNew, label = stringResource(R.string.profile_new_password), modifier = Modifier.fillMaxWidth())
                PasswordField(value = confirm, onValueChange = onConfirm, label = stringResource(R.string.profile_confirm_new_password), modifier = Modifier.fillMaxWidth(), isError = error != null, errorText = error.orEmpty())
            }
        },
        confirmButton = { PrimaryButton(label = stringResource(R.string.action_update), onClick = onSave, loading = saving) },
        dismissButton = { PinakesTextButton(label = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
