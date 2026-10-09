package com.pinakes.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pinakes.app.R

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)
val Fraunces = FontFamily(
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_italic_medium, FontWeight.Medium, FontStyle.Italic),
)
val PinakesFontFamily = Geist

private fun heading(size: Int, line: Int) = TextStyle(fontFamily = Fraunces,
    fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-size * .02).sp)
private fun ui(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Geist,
    fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

/** Publication titles stay distinct from interface labels in compact rows. */
val PublicationTitleStyle = heading(17, 21)

val PinakesTypography = Typography(
    displayLarge = heading(54, 58), displayMedium = heading(44, 48), displaySmall = heading(40, 44),
    headlineLarge = heading(36, 40), headlineMedium = heading(32, 36), headlineSmall = heading(28, 32),
    titleLarge = heading(24, 28), titleMedium = ui(16, 24, FontWeight.Medium), titleSmall = ui(14, 20, FontWeight.Medium),
    bodyLarge = ui(16, 27), bodyMedium = ui(14, 22), bodySmall = ui(13, 18),
    labelLarge = ui(15, 20, FontWeight.SemiBold), labelMedium = ui(12, 16, FontWeight.Medium),
    labelSmall = ui(11, 16, FontWeight.SemiBold),
)
