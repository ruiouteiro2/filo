package com.filo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.filo.app.R

/**
 * The site sets display type in a Bodoni/Didot stack with Cormorant Garamond as its bundled
 * fallback; Cormorant is the one of those that is openly licensed, so it is the app's display
 * face. Body copy on the site is the system sans, and Android's system sans plays that role
 * here - nothing to bundle, and it matches the site's intent rather than its Windows fonts.
 */
val Cormorant = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold),
    Font(R.font.cormorant_italic, FontWeight.Medium, FontStyle.Italic),
)

object FiloType {
    /** Big numerals: the clean sans - the serif digits read as antique, not romantic. */
    val Numeral = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp)
    val Title = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp)
    val TitleItalic = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 30.sp)
    val Value = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp)
    val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val Label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.14.em)
    val Timestamp = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.03.em)

    /** Clocks: sans digits, big enough to be the point. */
    val Mono = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, letterSpacing = 0.02.em)
}

internal val FiloTypography = Typography(
    displayLarge = FiloType.Numeral,
    headlineLarge = FiloType.Title,
    titleMedium = FiloType.Value,
    bodyMedium = FiloType.Body,
    labelLarge = FiloType.Label,
    labelSmall = FiloType.Timestamp,
)
