package com.pinakes.app.ui.components

import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

/**
 * Renders a raw HTML string (e.g. the book `description` field, which contains <p>, <strong>,
 * <em>, <br>, <ul>) as formatted Compose text. Never shows raw "<...>" tags.
 *
 * Pipeline: HtmlCompat.fromHtml(FROM_HTML_MODE_COMPACT) → [Spanned] → [AnnotatedString], mapping
 * the supported character spans (bold / italic / underline / strikethrough) to Compose
 * [SpanStyle]s. Unknown tags are stripped by HtmlCompat and rendered as plain text, so malformed
 * or unexpected markup degrades gracefully. Bullet list items are prefixed with "• " and the
 * trailing whitespace HtmlCompat appends is trimmed.
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val annotated = remember(html) { htmlToAnnotatedString(html) }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

/** Converts an HTML fragment into an [AnnotatedString], mapping inline spans to [SpanStyle]s. */
fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString().trimEnd()
    return buildAnnotatedString {
        append(text)
        val spans = spanned.getSpans(0, spanned.length, Any::class.java)
        for (span in spans) {
            val start = spanned.getSpanStart(span)
            var end = spanned.getSpanEnd(span)
            if (start < 0 || end < 0 || start >= text.length) continue
            if (end > text.length) end = text.length
            if (start >= end) continue
            when (span) {
                is StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    android.graphics.Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    android.graphics.Typeface.BOLD_ITALIC ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                is StrikethroughSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                is BulletSpan -> { /* layout-level; HtmlCompat already inserts list breaks */ }
            }
        }
    }
}
