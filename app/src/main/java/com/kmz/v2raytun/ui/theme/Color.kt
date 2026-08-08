package com.kmz.v2raytun.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hand-tuned fallback palette for devices without dynamic color (below Android 12).
 *
 * Seeded from the launcher's brand blue (#0F3D5C) and expanded into a Material 3 tonal
 * set. On Android 12+ these are overridden by the wallpaper-derived scheme in [Theme].
 */

// Light scheme.
val md_primary = Color(0xFF0F5D82)
val md_onPrimary = Color(0xFFFFFFFF)
val md_primaryContainer = Color(0xFFC5E7FF)
val md_onPrimaryContainer = Color(0xFF001E2C)

val md_secondary = Color(0xFF4E616D)
val md_onSecondary = Color(0xFFFFFFFF)
val md_secondaryContainer = Color(0xFFD1E5F4)
val md_onSecondaryContainer = Color(0xFF091E28)

val md_tertiary = Color(0xFF615A7C)
val md_onTertiary = Color(0xFFFFFFFF)
val md_tertiaryContainer = Color(0xFFE7DEFF)
val md_onTertiaryContainer = Color(0xFF1D1736)

val md_error = Color(0xFFBA1A1A)
val md_onError = Color(0xFFFFFFFF)
val md_errorContainer = Color(0xFFFFDAD6)
val md_onErrorContainer = Color(0xFF410002)

// Matches @color/window_background (values/colors.xml) so there is no first-frame flash.
val md_background = Color(0xFFFFFBFE)
val md_onBackground = Color(0xFF191C1E)
val md_surface = Color(0xFFFFFBFE)
val md_onSurface = Color(0xFF191C1E)
val md_surfaceVariant = Color(0xFFDCE3E9)
val md_onSurfaceVariant = Color(0xFF41484D)
val md_outline = Color(0xFF71787E)
val md_outlineVariant = Color(0xFFC0C7CD)

// Dark scheme.
val md_primary_dark = Color(0xFF87CEFF)
val md_onPrimary_dark = Color(0xFF003549)
val md_primaryContainer_dark = Color(0xFF004C69)
val md_onPrimaryContainer_dark = Color(0xFFC5E7FF)

val md_secondary_dark = Color(0xFFB5C9D7)
val md_onSecondary_dark = Color(0xFF20333E)
val md_secondaryContainer_dark = Color(0xFF374955)
val md_onSecondaryContainer_dark = Color(0xFFD1E5F4)

val md_tertiary_dark = Color(0xFFCBC1E9)
val md_onTertiary_dark = Color(0xFF322C4C)
val md_tertiaryContainer_dark = Color(0xFF494263)
val md_onTertiaryContainer_dark = Color(0xFFE7DEFF)

val md_error_dark = Color(0xFFFFB4AB)
val md_onError_dark = Color(0xFF690005)
val md_errorContainer_dark = Color(0xFF93000A)
val md_onErrorContainer_dark = Color(0xFFFFDAD6)

// Matches @color/window_background (values-night/colors.xml).
val md_background_dark = Color(0xFF141218)
val md_onBackground_dark = Color(0xFFE2E2E6)
val md_surface_dark = Color(0xFF141218)
val md_onSurface_dark = Color(0xFFE2E2E6)
val md_surfaceVariant_dark = Color(0xFF41484D)
val md_onSurfaceVariant_dark = Color(0xFFC0C7CD)
val md_outline_dark = Color(0xFF8B9297)
val md_outlineVariant_dark = Color(0xFF41484D)
