package com.pinakes.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinakes.app.ui.theme.Fraunces
import java.util.Locale

val BookPlaceholderPapers = listOf(Color(0xFF3C2D40), Color(0xFF2F413A), Color(0xFF2E3D4E), Color(0xFF52322B))
val BookPlaceholderInk = Color(0xFFF6EDDE)

/** The browser uses the same UTF-8 key, so a title keeps its binding colour. */
fun bookPlaceholderTone(title: String): Int = title.trim().toByteArray(Charsets.UTF_8).fold(0) { tone, byte ->
    (tone * 31 + (byte.toInt() and 255)) % BookPlaceholderPapers.size
}

/** Legacy API records can still point at the server's generic placeholder. */
fun bookCoverImageUrl(url: String?): String? = url?.trim()?.takeIf { value ->
    value.isNotEmpty() && value.substringBefore('?').substringBefore('#').substringAfterLast('/')
        .lowercase(Locale.ROOT) !in setOf("placeholder.jpg", "placeholder.png", "placeholder.svg")
}

/** A typeset cloth binding, drawn without image downloads or generated bitmaps. */
@Composable
internal fun BookPlaceholder(title: String, author: String?, publisher: String?, compact: Boolean, onTone: ((Color) -> Unit)?) {
    val paper = remember(title) { BookPlaceholderPapers[bookPlaceholderTone(title)] }
    LaunchedEffect(paper, onTone != null) { onTone?.invoke(paper) }
    BoxWithConstraints(Modifier.fillMaxSize().background(paper).clearAndSetSemantics { }) {
        val width = maxWidth.value
        val small = compact || width < 112f
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.width * .07f
            drawRect(BookPlaceholderInk.copy(alpha = .22f), Offset(inset, inset),
                Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(.7.dp.toPx()))
            for (i in 1..20) {
                val x = size.width * i / 21
                drawLine(BookPlaceholderInk.copy(alpha = .025f), Offset(x, 0f), Offset(x, size.height), .5.dp.toPx())
            }
            val diameter = size.width * .9f
            drawArc(BookPlaceholderInk.copy(alpha = .13f), 180f, 180f, false,
                Offset(size.width * .33f, size.height - diameter * .33f), Size(diameter, diameter), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(start = (width * .12f).dp, end = (width * .10f).dp,
            top = (width * if (small) .18f else .27f).dp, bottom = (width * .12f).dp)) {
            if (!small && !author.isNullOrBlank()) {
                Text(author, color = BookPlaceholderInk, fontSize = (width * .065f).coerceIn(9f, 18f).sp,
                    lineHeight = (width * .085f).coerceIn(12f, 23f).sp, letterSpacing = .45.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height((width * .055f).dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(title, fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                    color = BookPlaceholderInk, fontSize = (width * .12f).coerceIn(8f, 38f).sp,
                    lineHeight = (width * .14f).coerceIn(10f, 44f).sp,
                    maxLines = if (small) 4 else 6, overflow = TextOverflow.Ellipsis)
            }
            if (!small) {
                Spacer(Modifier.height((width * .055f).dp))
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawLine(BookPlaceholderInk.copy(alpha = .6f), Offset.Zero, Offset(size.width * .24f, 0f), 1.dp.toPx())
                }
                if (!publisher.isNullOrBlank()) Text(publisher, color = BookPlaceholderInk,
                    fontSize = (width * .055f).coerceIn(8f, 15f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
