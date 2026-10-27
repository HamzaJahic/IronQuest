package com.ironquest.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.AppearanceSettings
import com.ironquest.app.domain.appearance.ThemeMode

private val LocalIronColors = staticCompositionLocalOf { IronColors.of(AppearanceSettings.Default.palette, dark = false) }
private val LocalIronType = staticCompositionLocalOf { IronType.of(AppearanceSettings.Default.style) }
private val LocalIronShapes = staticCompositionLocalOf { IronShapes.of(AppearanceSettings.Default.style) }
private val LocalIronStyle = staticCompositionLocalOf { IronStyleSpec.of(AppearanceSettings.Default.style) }

/**
 * The app theme: style × palette × light/dark from [settings], provided through
 * `IronTheme.colors / .type / .shapes / .style` and mapped onto MaterialTheme so stock M3
 * widgets (dialogs, text fields, snackbars) match.
 */
@Composable
fun IronTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = IronColors.of(settings.palette, dark)
    val type = IronType.of(settings.style)
    val shapes = IronShapes.of(settings.style)
    val spec = IronStyleSpec.of(settings.style)
    val colorScheme = remember(colors) { colors.toColorScheme() }
    val typography = remember(type) { type.toTypography() }
    val materialShapes = remember(settings.style) {
        val cornerRadius = when (settings.style) {
            AppStyle.DIARY -> 10.dp
            AppStyle.BOLD -> 2.dp
            AppStyle.SOFT -> 22.dp
        }
        Shapes(
            extraSmall = RoundedCornerShape(cornerRadius / 2),
            small = RoundedCornerShape(cornerRadius),
            medium = RoundedCornerShape(cornerRadius),
            large = shapes.sheet as? RoundedCornerShape ?: RoundedCornerShape(cornerRadius),
            extraLarge = shapes.sheet as? RoundedCornerShape ?: RoundedCornerShape(cornerRadius),
        )
    }
    CompositionLocalProvider(
        LocalIronColors provides colors,
        LocalIronType provides type,
        LocalIronShapes provides shapes,
        LocalIronStyle provides spec,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = materialShapes) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

object IronTheme {
    val colors: IronColors
        @Composable @ReadOnlyComposable
        get() = LocalIronColors.current
    val type: IronType
        @Composable @ReadOnlyComposable
        get() = LocalIronType.current
    val shapes: IronShapes
        @Composable @ReadOnlyComposable
        get() = LocalIronShapes.current
    val style: IronStyleSpec
        @Composable @ReadOnlyComposable
        get() = LocalIronStyle.current
}

/**
 * Sets status/navigation bar icon contrast. `true` = the content behind the bar is dark, so
 * icons are light. Edge-to-edge is set up in MainActivity; this only flips the icon colour.
 */
@Composable
fun SystemBarIcons(darkBehindStatusBar: Boolean, darkBehindNavigationBar: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkBehindStatusBar
            isAppearanceLightNavigationBars = !darkBehindNavigationBar
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun IronColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = upBg,
        onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = ink,
        onSecondary = paper,
        secondaryContainer = line,
        onSecondaryContainer = ink,
        tertiary = reward,
        onTertiary = paper,
        tertiaryContainer = holdBg,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = raised,
        onSurfaceVariant = muted,
        surfaceTint = paper,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = deload,
        onError = paper,
        errorContainer = deloadBg,
        onErrorContainer = deload,
        outline = line,
        outlineVariant = line,
        scrim = base.scrim,
        surfaceBright = raised,
        surfaceDim = paper,
        surfaceContainerLowest = paper,
        surfaceContainerLow = paper,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
    )
}

private fun IronType.toTypography(): Typography {
    val defaults = Typography()
    return Typography(
        displayLarge = pageTitle,
        displayMedium = pageTitle,
        displaySmall = pageTitle,
        headlineLarge = pageTitle,
        headlineMedium = pageTitle,
        headlineSmall = sectionHead.copy(fontSize = defaults.headlineSmall.fontSize, lineHeight = defaults.headlineSmall.lineHeight),
        titleLarge = sectionHead.copy(fontSize = defaults.titleLarge.fontSize, lineHeight = defaults.titleLarge.lineHeight),
        titleMedium = bodyStrong.copy(fontSize = defaults.titleMedium.fontSize, lineHeight = defaults.titleMedium.lineHeight),
        titleSmall = bodyStrong,
        bodyLarge = body.copy(fontSize = defaults.bodyLarge.fontSize, lineHeight = defaults.bodyLarge.lineHeight),
        bodyMedium = body,
        bodySmall = secondary,
        labelLarge = button.copy(fontSize = defaults.labelLarge.fontSize, lineHeight = defaults.labelLarge.lineHeight),
        labelMedium = label.copy(fontSize = defaults.labelMedium.fontSize, lineHeight = defaults.labelMedium.lineHeight),
        labelSmall = label,
    )
}
