package com.pinakes.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Read-only star row for an average/individual rating. Renders full / half / empty stars for a
 * fractional [rating] in 0..5 (half-star resolution, matching the website's average display).
 */
@Composable
fun StarRating(
    rating: Double,
    modifier: Modifier = Modifier,
    starSize: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    // Round to the nearest half star so an average like 3.7 shows 3.5 (3 full + 1 half).
    val halves = (rating.coerceIn(0.0, 5.0) * 2).roundToInt()
    Row(modifier) {
        for (i in 1..5) {
            val icon = when {
                halves >= i * 2 -> Icons.Filled.Star
                halves == i * 2 - 1 -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/**
 * Interactive 1–5 star picker for composing a review. Tapping a star sets [rating]; the row is
 * accessible (each star is a selectable with a "N stars" label).
 */
@Composable
fun StarRatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    starSize: Dp = 36.dp,
) {
    Row(modifier) {
        for (i in 1..5) {
            val label = stringResource(R.string.review_rating_stars, i)
            Icon(
                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = label,
                tint = if (i <= rating) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(end = Spacing.xs)
                    .size(starSize)
                    .selectable(
                        selected = i == rating,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onRatingChange(i) },
                    ),
            )
        }
    }
}
