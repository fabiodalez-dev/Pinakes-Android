package com.pinakes.app.ui.screens.staff.s090.catalogue

import androidx.annotation.StringRes
import com.pinakes.app.data.model.DuplicateMode
import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.ValueLabel
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.staff.Staff090CatalogueErrors
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.toStaffMessage

// Small helpers shared by the cataloguing screens (suppliers, MARC, copy cataloguing).

/** Our wording for a cataloguing code, then the shared staff mapping, then the server text. */
internal fun ApiResult.Failure.toCatalogueMessage(@StringRes fallback: Int, copyCataloguing: Boolean = false): StaffMessage =
    Staff090CatalogueErrors.messageRes(this, copyCataloguing)?.let { StaffMessage(it) } ?: toStaffMessage(fallback)

internal fun List<ValueLabel>.asOptions(): List<LookupOption> = map { LookupOption(it.value, it.label.ifBlank { it.value }) }

@JvmName("duplicateModesAsOptions")
internal fun List<DuplicateMode>.asOptions(): List<LookupOption> = map { LookupOption(it.value, it.label.ifBlank { it.value }) }

@JvmName("idNamesAsOptions")
internal fun List<IdName>.asOptions(): List<LookupOption> = map { LookupOption(it.id.toString(), it.name) }

/** The label of [value] among [options], or the value itself. */
internal fun List<LookupOption>.labelOf(value: String?): String = firstOrNull { it.value == value }?.label ?: value.orEmpty()
