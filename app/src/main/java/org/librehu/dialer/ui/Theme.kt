package org.librehu.dialer.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Same palette contract as LibreHU Launcher (CarPalette): effective night mode + launcher accent. */
data class DialerPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
) {
    companion object {
        fun fromLauncher(
            dark: Boolean,
            accentArgb: Int,
        ): DialerPalette {
            val accent = if (accentArgb != 0) Color(accentArgb) else Color(if (dark) 0xFF8AB4F8 else 0xFF1A73E8)
            return if (dark) {
                DialerPalette(
                    true,
                    Color(0xFF000000),
                    Color(0xFF1E1F22),
                    Color(0xFF2B2D31),
                    accent,
                    Color(0xFF202124),
                    Color(0xFFE8EAED),
                    Color(0xFF9AA0A6),
                )
            } else {
                DialerPalette(
                    false,
                    Color(0xFFF1F3F4),
                    Color(0xFFFFFFFF),
                    Color(0xFFE8EAED),
                    accent,
                    Color.White,
                    Color(0xFF202124),
                    Color(0xFF5F6368),
                )
            }
        }
    }
}

object DialerColors {
    private var base: DialerPalette by mutableStateOf(DialerPalette.fromLauncher(true, 0))

    /** The app's palette, or LibreHU Launcher's retro one when its style is retro (same light / dark only). */
    var palette: DialerPalette
        get() = RetroLook.palette?.takeIf { it.dark == base.dark }?.let { r ->
            base.copy(
                background = r.background,
                surface = r.surface,
                surfaceHigh = r.surfaceHigh,
                accent = r.accent,
                onAccent = r.onAccent,
                text = r.text,
                textDim = r.textDim,
            )
        } ?: base
        set(value) {
            base = value
        }
    val Bg get() = palette.background
    val Card get() = palette.surface
    val Raised get() = palette.surfaceHigh
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    val Text get() = palette.text
    val Muted get() = palette.textDim
    val Green = Color(0xFF1E8E3E)
    val Red = Color(0xFFD93025)
}

@Composable
fun DialerTheme(content: @Composable () -> Unit) {
    val p = DialerColors.palette
    val scheme =
        if (p.dark) {
            darkColorScheme(
                background = p.background,
                surface = p.surface,
                surfaceVariant = p.surfaceHigh,
                primary = p.accent,
                onPrimary = p.onAccent,
                onBackground = p.text,
                onSurface = p.text,
                onSurfaceVariant = p.textDim,
                secondary = p.accent,
            )
        } else {
            lightColorScheme(
                background = p.background,
                surface = p.surface,
                surfaceVariant = p.surfaceHigh,
                primary = p.accent,
                onPrimary = p.onAccent,
                onBackground = p.text,
                onSurface = p.text,
                onSurfaceVariant = p.textDim,
                secondary = p.accent,
            )
        }
    MaterialTheme(
        typography = RetroLook.typography(),
        colorScheme = scheme.withCarRoles(p.accent, p.onAccent, p.surface, p.surfaceHigh, p.text, p.textDim),
        content = content,
    )
}

/**
 * Material 3 roles the car palette does not name, derived from it. Without them dialogs, cards, switches,
 * sliders, chips and progress tracks keep Material's default purple instead of the accent.
 */
internal fun ColorScheme.withCarRoles(
    accent: Color,
    onAccent: Color,
    surface: Color,
    surfaceHigh: Color,
    text: Color,
    textDim: Color,
): ColorScheme {
    val solid = surface.copy(alpha = 1f)
    val high = surfaceHigh.copy(alpha = 1f)
    val container = lerp(solid, accent, 0.30f)
    return copy(
        primaryContainer = container,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = text,
        surfaceTint = Color.Transparent,
        surfaceBright = high,
        surfaceDim = solid,
        surfaceContainerLowest = solid,
        surfaceContainerLow = solid,
        surfaceContainer = solid,
        surfaceContainerHigh = high,
        surfaceContainerHighest = high,
        outline = textDim,
        outlineVariant = lerp(high, textDim, 0.35f),
    )
}
