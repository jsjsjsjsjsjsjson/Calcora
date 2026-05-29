package dev.libchara.calcora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NordDark = darkColorScheme(
    primary = NordFrost2,
    onPrimary = NordPolarNight0,
    primaryContainer = NordFrost4,
    onPrimaryContainer = NordSnowStorm0,
    secondary = NordFrost1,
    onSecondary = NordPolarNight0,
    secondaryContainer = NordFrost3,
    onSecondaryContainer = NordSnowStorm0,
    tertiary = NordAuroraGreen,
    onTertiary = NordPolarNight0,
    surface = NordPolarNight1,
    onSurface = NordSnowStorm0,
    surfaceVariant = NordPolarNight2,
    onSurfaceVariant = NordSnowStorm1,
    background = NordPolarNight0,
    onBackground = NordSnowStorm0,
    error = NordAuroraRed,
    onError = NordSnowStorm0,
    errorContainer = Color(0xFF3B2020),
    onErrorContainer = NordSnowStorm0,
    outline = NordPolarNight3,
)

private val NordLight = lightColorScheme(
    primary = NordFrost4,
    onPrimary = NordSnowStorm2,
    primaryContainer = NordFrost2,
    onPrimaryContainer = NordPolarNight0,
    secondary = NordFrost3,
    onSecondary = NordSnowStorm2,
    secondaryContainer = NordFrost1,
    onSecondaryContainer = NordPolarNight0,
    tertiary = NordAuroraGreen,
    onTertiary = NordSnowStorm2,
    surface = NordSnowStorm2,
    onSurface = NordPolarNight0,
    surfaceVariant = NordSnowStorm1,
    onSurfaceVariant = NordPolarNight1,
    background = Color(0xFFF9FAFB),
    onBackground = NordPolarNight0,
    error = NordAuroraRed,
    onError = NordSnowStorm2,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = NordSnowStorm0,
)

@Composable
fun CalcoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NordDark
        else -> NordLight
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
