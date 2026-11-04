package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

/**
 * IvanaFin: colours matching the ElegantFin Jellyfin web theme
 * (https://github.com/lscambo13/ElegantFin) with Lewis's custom CSS overrides:
 * pure black backgrounds, neutral grey glass surfaces/borders, white focus outline.
 *
 * CSS variable -> value used here:
 *   --textColor #d1d5db, --dimTextColor #9ca3af, --fullContrastColor #fff
 *   --borderColor hsl(0,0%,32%) #525252, --darkerBorderColor hsl(0,0%,22%) #383838
 *   --selectorBackgroundColor hsl(0,0%,27%) #454545
 *   --lighterGradientPointAlpha hsla(0,0%,7%) #121212, gradient points #000
 *   --cardHoverBorderColor #fff, --accentColor #5d55e7, --btnDeleteColor #a91d1d
 */
val ElegantFinThemeColors =
    object : ThemeColors {
        val text = Color(0xFFD1D5DB)
        val dimText = Color(0xFF9CA3AF)
        val white = Color(0xFFFFFFFF)
        val black = Color(0xFF000000)
        val border = Color(0xFF525252)
        val darkerBorder = Color(0xFF383838)
        val selector = Color(0xFF454545)
        val glass = Color(0xFF121212)
        val accent = Color(0xFF5D55E7)

        val primaryDark = text
        val onPrimaryDark = black
        val primaryContainerDark = selector
        val onPrimaryContainerDark = white
        val secondaryDark = dimText
        val onSecondaryDark = black
        val secondaryContainerDark = darkerBorder
        val onSecondaryContainerDark = text
        val tertiaryDark = text
        val onTertiaryDark = black
        val tertiaryContainerDark = border
        val onTertiaryContainerDark = white
        val errorDark = Color(0xFFD95454)
        val onErrorDark = white
        val errorContainerDark = Color(0xFFA91D1D)
        val onErrorContainerDark = white
        val backgroundDark = black
        val onBackgroundDark = text
        val surfaceDark = black
        val onSurfaceDark = text
        val surfaceVariantDark = glass
        val onSurfaceVariantDark = dimText
        val outlineDark = border
        val outlineVariantDark = darkerBorder
        val scrimDark = black
        val inverseSurfaceDark = text
        val inverseOnSurfaceDark = black
        val inversePrimaryDark = accent
        val surfaceDimDark = black
        val surfaceBrightDark = Color(0xFF262626)
        val surfaceContainerLowestDark = Color(0xFF0A0A0A)
        val surfaceContainerLowDark = glass
        val surfaceContainerDark = Color(0xFF171717)
        val surfaceContainerHighDark = Color(0xFF1F1F1F)
        val surfaceContainerHighestDark = Color(0xFF262626)

        // App always runs dark; reuse OLED's light schemes.
        override val lightSchemeMaterial: ColorScheme = OledThemeColors.lightSchemeMaterial
        override val lightScheme = OledThemeColors.lightScheme

        override val darkSchemeMaterial =
            androidx.compose.material3.darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                outline = outlineDark,
                outlineVariant = outlineVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
                surfaceDim = surfaceDimDark,
                surfaceBright = surfaceBrightDark,
                surfaceContainerLowest = surfaceContainerLowestDark,
                surfaceContainerLow = surfaceContainerLowDark,
                surfaceContainer = surfaceContainerDark,
                surfaceContainerHigh = surfaceContainerHighDark,
                surfaceContainerHighest = surfaceContainerHighestDark,
            )

        override val darkScheme =
            darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
                // TV Material uses `border` for focused-card outlines (ElegantFin --cardHoverBorderColor)
                border = white,
                borderVariant = darkerBorder,
            )
    }
