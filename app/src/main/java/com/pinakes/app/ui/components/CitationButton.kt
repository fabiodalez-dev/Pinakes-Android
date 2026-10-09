package com.pinakes.app.ui.components

import android.widget.Toast
import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.pinakes.app.R
import com.pinakes.app.data.model.ArticleCitation
import com.pinakes.app.ui.screens.bookclub.openWeb
import com.pinakes.app.ui.theme.Spacing

/** Server-formatted references keep the website and app's bibliographic rules identical. */
@Composable
fun CitationButton(citations: List<ArticleCitation>, risUrl: String? = null, marcXmlUrl: String? = null) {
    var open by remember { mutableStateOf(false) }
    var selected by remember(citations) { mutableStateOf(citations.firstOrNull()?.key) }
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val copied = stringResource(R.string.citation_copied)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (citations.isNotEmpty()) TextButton({ open = true }) { Text(stringResource(R.string.citation_cite)) }
        risUrl?.let { url -> TextButton({ openWeb(context, url) }) { Text(stringResource(R.string.citation_ris)) } }
        marcXmlUrl?.let { url -> TextButton({ openWeb(context, url) }) { Text("MARCXML") } }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.citation_cite)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    citations.forEach { citation -> FilterChip(selected == citation.key, { selected = citation.key }, { Text(citation.label) }) }
                }
                citations.firstOrNull { it.key == selected }?.let { citation ->
                    SelectionContainer { HtmlText(citation.html ?: android.text.TextUtils.htmlEncode(citation.text), style = MaterialTheme.typography.bodyLarge) }
                    TextButton({ clipboard.setPrimaryClip(citation.html?.let { ClipData.newHtmlText(citation.label, citation.text, it) } ?: ClipData.newPlainText(citation.label, citation.text)); if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show() }) {
                        Text(stringResource(R.string.citation_copy))
                    }
                }
            }
        }, confirmButton = { TextButton({ open = false }) { Text(stringResource(R.string.cover_viewer_close)) } })
}
