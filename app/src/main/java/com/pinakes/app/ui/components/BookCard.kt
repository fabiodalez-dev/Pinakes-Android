package com.pinakes.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.scale
import com.pinakes.app.R
import com.pinakes.app.ui.theme.CardStyle
import com.pinakes.app.ui.theme.Fraunces
import com.pinakes.app.ui.theme.LocalPinakesColors

/** A complete cover, including unusually tall books and square record sleeves. */
@Composable
fun BookCover(title: String, coverUrl: String?, modifier: Modifier = Modifier, compact: Boolean = false, onTone: ((Color) -> Unit)? = null) {
    val colors = LocalPinakesColors.current
    val context = LocalContext.current
    val image = remember(coverUrl, onTone != null) { ImageRequest.Builder(context).data(coverUrl).allowHardware(onTone == null).build() }
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp, bottomStart = 2.dp)
    Box(modifier.shadow(if (compact) 3.dp else 12.dp, shape, ambientColor = Color(0xFF320F23), spotColor = Color(0xFF320F23))) {
        // Paper block, peeking from the fore-edge of the book.
        Canvas(Modifier.fillMaxSize().padding(top = 3.dp, bottom = 3.dp)) {
            drawRect(Color(0xFFF6F2EE))
            val step = 2.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0xFFE4DDD6), Offset(size.width - 6.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                y += step
            }
        }
        Box(Modifier.fillMaxSize().padding(end = 4.dp).clip(shape).background(colors.coverBlank)) {
            val blank: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 6.dp else 16.dp, vertical = if (compact) 8.dp else 44.dp),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Text(title, fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                        fontSize = if (compact) 11.sp else 17.sp, lineHeight = if (compact) 13.sp else 21.sp,
                        color = Color.White, maxLines = if (compact) 3 else 5, overflow = TextOverflow.Ellipsis)
                    if (!compact) Column {
                        Box(Modifier.width(22.dp).height(2.dp).background(colors.accent))
                        Spacer(Modifier.height(8.dp))
                        Text("PINAKES", color = Color.White.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 1.2.sp)
                    }
                }
            }
            SubcomposeAsyncImage(model = image, contentDescription = title, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, loading = { blank() }, error = { blank() },
                onSuccess = { success ->
                    if (onTone != null) (success.result.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
                        val sample = bitmap.scale(12, 12)
                        var red = 0L; var green = 0L; var blue = 0L; var count = 0
                        for (y in 0 until 12) for (x in 0 until 12) {
                            val pixel = sample.getPixel(x, y)
                            if (android.graphics.Color.alpha(pixel) > 128) {
                                red += android.graphics.Color.red(pixel); green += android.graphics.Color.green(pixel)
                                blue += android.graphics.Color.blue(pixel); count++
                            }
                        }
                        if (count > 0) onTone(Color(red.toFloat() / count / 255f, green.toFloat() / count / 255f, blue.toFloat() / count / 255f))
                        if (sample !== bitmap) sample.recycle()
                    }
                })
            // Spine and gloss have no interaction or accessibility semantics.
            Box(Modifier.fillMaxHeight().width(if (compact) 5.dp else 14.dp).background(Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = .28f), Color.White.copy(alpha = .28f), Color.Transparent))))
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent, Color.Black.copy(alpha = .06f))))
                drawRect(Color.White.copy(alpha = .15f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
        }
    }
}

/** Compact catalog row. Account lists keep their separate MediaRow actions. */
@Composable
fun BookCard(
    title: String, author: String, coverUrl: String?, status: AvailabilityStatus = AvailabilityStatus.Available,
    year: String? = null, publisher: String? = null, onClick: () -> Unit = {}, modifier: Modifier = Modifier,
    subtitle: String? = null, mediaType: String? = null,
) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(title, coverUrl, Modifier.width(64.dp).height(96.dp), compact = true)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BookTitle(title)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AuthorLabel(author)
            val meta = listOfNotNull(year?.takeIf { it.isNotBlank() }, publisher?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvailabilityChip(status)
                MediaTypeIcon(mediaType)
            }
        }
    }
}

/** The classic web card: book, status pill, title and metadata, with no outer card. */
@Composable
fun BookCardGrid(
    title: String, author: String, coverUrl: String?, status: AvailabilityStatus = AvailabilityStatus.Available,
    onClick: () -> Unit = {}, modifier: Modifier = Modifier, subtitle: String? = null,
    publisher: String? = null, year: String? = null, mediaType: String? = null,
) {
    val colors = LocalPinakesColors.current
    var coverTone by remember(coverUrl, colors.accent) { mutableStateOf(colors.accent) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateFloatAsState(if (pressed) -4f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)), label = "cover_lift")
    Column(modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().then(if (colors.cardStyle == CardStyle.Tinted)
            Modifier.background(com.pinakes.app.ui.theme.mix(coverTone, colors.surface, .16f), RoundedCornerShape(16.dp)).padding(18.dp) else Modifier)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).graphicsLayer { translationY = lift.dp.toPx() }) {
                BookCover(title, coverUrl, Modifier.fillMaxSize(), onTone = if (colors.cardStyle == CardStyle.Tinted) ({ coverTone = it }) else null)
                AvailabilityChip(status, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) { MediaTypeIcon(mediaType, onCover = true) }
            }
        }
        Spacer(Modifier.height(14.dp))
        BookTitle(title)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        AuthorLabel(author)
        val meta = listOfNotNull(year?.takeIf { it.isNotBlank() }, publisher?.takeIf { it.isNotBlank() }).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.labelMedium, color = colors.muted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 18.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = colors.accentText)
            Text(stringResource(R.string.restyle_details), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = colors.accentText)
        }
    }
}

@Composable
private fun BookTitle(title: String) = Text(title, fontFamily = Fraunces, fontWeight = FontWeight.Medium,
    fontSize = 17.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
private fun AuthorLabel(author: String) = Text(author.ifBlank { stringResource(R.string.restyle_unknown_author) },
    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
fun MediaTypeIcon(mediaType: String?, onCover: Boolean = false) {
    val icon = when (mediaType?.lowercase()) {
        "cd", "vinile", "vinyl", "audio", "audiocassetta" -> Icons.Outlined.Album
        "dvd", "blu-ray", "bluray", "video", "vhs" -> Icons.Outlined.Movie
        else -> return
    }
    Surface(shape = RoundedCornerShape(50), color = if (onCover) Color.White else MaterialTheme.colorScheme.surface) {
        Icon(icon, contentDescription = mediaType, tint = if (onCover) Color(0xFF1B1720) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(5.dp).size(18.dp))
    }
}
