package com.nextz.kanomtts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nextz.kanomtts.R

val Itim = FontFamily(Font(R.font.itim_regular, FontWeight.Normal))

val Sarabun = FontFamily(
    Font(R.font.sarabun_regular, FontWeight.Normal),
    Font(R.font.sarabun_medium, FontWeight.Medium),
    Font(R.font.sarabun_semibold, FontWeight.SemiBold),
    Font(R.font.sarabun_bold, FontWeight.Bold),
)

private val base = Typography()

/** Itim = brand/display/headline; Sarabun = titles/body/labels (reading default). */
val KanomTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Itim),
    displayMedium = base.displayMedium.copy(fontFamily = Itim),
    displaySmall = base.displaySmall.copy(fontFamily = Itim),
    headlineLarge = base.headlineLarge.copy(fontFamily = Itim),
    headlineMedium = base.headlineMedium.copy(fontFamily = Itim),
    headlineSmall = base.headlineSmall.copy(fontFamily = Itim),
    titleLarge = base.titleLarge.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sarabun),
    bodyMedium = base.bodyMedium.copy(fontFamily = Sarabun),
    bodySmall = base.bodySmall.copy(fontFamily = Sarabun),
    labelLarge = base.labelLarge.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
)
