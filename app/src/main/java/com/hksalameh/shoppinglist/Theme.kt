package com.hksalameh.shoppinglist

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3F1D0),
    onPrimaryContainer = Color(0xFF0B3A29),
    secondary = Color(0xFF5B614C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7CD),
    onSecondaryContainer = Color(0xFF1B1F12),
    tertiary = Color(0xFF7A5735),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2D1602),
    background = Color(0xFFF8FAF5),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFF8FAF5),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDFE4DD),
    onSurfaceVariant = Color(0xFF434843),
    outline = Color(0xFF737973),
    outlineVariant = Color(0xFFC3C9C2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF97D5B5),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF0F5138),
    onPrimaryContainer = Color(0xFFB3F1D0),
    secondary = Color(0xFFC4CBAF),
    onSecondary = Color(0xFF2E3324),
    secondaryContainer = Color(0xFF444A39),
    onSecondaryContainer = Color(0xFFE0E7CD),
    tertiary = Color(0xFFEDBE91),
    onTertiary = Color(0xFF462A11),
    tertiaryContainer = Color(0xFF60401F),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF434843),
    onSurfaceVariant = Color(0xFFC3C9C2),
    outline = Color(0xFF8D938D),
    outlineVariant = Color(0xFF434843)
)

@Composable
fun ShoppingListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
