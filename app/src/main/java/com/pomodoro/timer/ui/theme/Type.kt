package com.pomodoro.timer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pomodoro.timer.R

val PomodoroTypography = Typography(
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 64.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
)

@OptIn(ExperimentalTextApi::class)
private val ManropeExtraBold = FontFamily(
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.W800,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

// The one deliberate typographic accent per the shared token spec (Section B): Manrope 800 with
// tabular figures so digit width never shifts as MM:SS changes, applied only to the countdown.
fun timerDigitFontStyle(base: TextStyle): TextStyle = base.copy(
    fontFamily = ManropeExtraBold,
    fontWeight = FontWeight.W800,
    fontFeatureSettings = "tnum"
)
