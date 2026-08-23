package com.filo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.filo.app.R

// Bundled as static TTF instances cut from the upstream variable fonts, so that the same
// files work in Compose and inside RemoteViews layouts for the widgets.
val FrauncesTitle = FontFamily(Font(R.font.fraunces_title, FontWeight.SemiBold))
val Karla = FontFamily(
    Font(R.font.karla_regular, FontWeight.Normal),
    Font(R.font.karla_semibold, FontWeight.SemiBold),
    Font(R.font.karla_bold, FontWeight.Bold),
)

/**
 * Numerals are Karla Bold, not Fraunces. Fraunces draws quirky display figures that read as
 * a mistake at 64sp; Karla has plain lining numerals that just say the number.
 */
val NumeralFamily = Karla

object FiloType {
    val Numeral = TextStyle(fontFamily = NumeralFamily, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-0.02).em)
    val Title = TextStyle(fontFamily = FrauncesTitle, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp)
    val Value = TextStyle(fontFamily = Karla, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
    val Body = TextStyle(fontFamily = Karla, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val Label = TextStyle(fontFamily = Karla, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
    val Timestamp = TextStyle(fontFamily = Karla, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em)

    /** Clocks and the invite code: numerals that need to line up, so tabular and wide. */
    val Mono = TextStyle(fontFamily = Karla, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.06.em)
}

internal val FiloTypography = Typography(
    displayLarge = FiloType.Numeral,
    headlineLarge = FiloType.Title,
    titleMedium = FiloType.Value,
    bodyMedium = FiloType.Body,
    labelLarge = FiloType.Label,
    labelSmall = FiloType.Timestamp,
)
