package com.pinakes.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pinakes.app.R

internal fun isPrincipalAuthorRole(role: String?): Boolean = role in setOf("principale", "autore", "author")

/** Contributor entities share the author registry, but keep their bibliographic role (#237). */
@Composable
fun contributorRoleLabel(role: String?): String = when (role) {
    "principale", "autore", "author" -> stringResource(R.string.credit_author)
    "co-autore" -> stringResource(R.string.credit_coauthor)
    "traduttore" -> stringResource(R.string.credit_translator)
    "illustratore" -> stringResource(R.string.credit_illustrator)
    "curatore" -> stringResource(R.string.credit_editor)
    "colorista" -> stringResource(R.string.credit_colorist)
    else -> role?.takeIf(String::isNotBlank)?.replace('_', ' ') ?: stringResource(R.string.credit_contributor)
}
