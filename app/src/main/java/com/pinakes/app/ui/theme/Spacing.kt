package com.pinakes.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 4dp-grid spacing tokens for Pinakes.
 *
 * Usage: Spacing.lg  (= 16.dp) for screen edge padding and card inner padding.
 */
object Spacing {
    val xxs  =  2.dp
    val xs   =  4.dp
    val sm   =  8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 24.dp
    val xxl  = 32.dp
    val xxxl = 48.dp
}

// Semantic aliases
val ScreenPadding   = Spacing.lg   // 16.dp — screen horizontal edges
val CardPadding     = Spacing.lg   // 16.dp — card inner padding
val ListItemGap     = Spacing.md   // 12.dp — vertical gap between list items
val SectionGap      = Spacing.xl   // 24.dp — gap between major sections
