package ru.acted.beatcharts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.acted.beatcharts.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

val googleSans = FontFamily(
    Font(R.font.googlesans_regular),
    Font(R.font.googlesans_medium, weight = FontWeight.Medium),
    Font(R.font.googlesans_bold, weight = FontWeight.Bold),
    Font(R.font.googlesans_italic, style = FontStyle.Italic),
    Font(R.font.googlesans_mediumitalic, weight = FontWeight.Medium, style = FontStyle.Italic),
    Font(R.font.googlesans_bolditalic, weight = FontWeight.Bold, style = FontStyle.Italic)
)