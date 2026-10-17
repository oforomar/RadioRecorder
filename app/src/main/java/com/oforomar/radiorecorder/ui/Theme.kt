package com.oforomar.radiorecorder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

val RecordRed = Color(0xFFC62828)

private val Light = lightColorScheme(
    primary = Color(0xFF0F5B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A6D1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3B5),
    onSecondaryContainer = Color(0xFF2A1F00),
    background = Color(0xFFFBF8F1),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFE6E2D6),
    onSurfaceVariant = Color(0xFF45483F),
    surfaceContainer = Color(0xFFF2EEE4),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD3BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0F5B4A),
    onPrimaryContainer = Color(0xFFCDE8DF),
    secondary = Color(0xFFE1C77E),
    onSecondary = Color(0xFF3D2F00),
    secondaryContainer = Color(0xFF574500),
    onSecondaryContainer = Color(0xFFF3E3B5),
    background = Color(0xFF121411),
    onBackground = Color(0xFFE3E3DD),
    surface = Color(0xFF121411),
    onSurface = Color(0xFFE3E3DD),
    surfaceContainer = Color(0xFF1E201C),
    surfaceContainerHigh = Color(0xFF282A26),
)

/** Everything a notch bigger than Material defaults — the app is meant for all ages. */
private val BigType = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodySmall = t.bodySmall.copy(fontSize = 15.sp),
        labelLarge = t.labelLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = BigType,
    ) {
        // The whole app is Arabic, whatever the phone's language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
