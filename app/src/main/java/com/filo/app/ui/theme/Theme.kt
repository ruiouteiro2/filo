package com.filo.app.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val FiloColorScheme = darkColorScheme(
    primary = Crimson,
    onPrimary = Ink,
    secondary = RoseAsh,
    onSecondary = Ink,
    background = Ink,
    onBackground = Bone,
    surface = Surface,
    onSurface = Bone,
    surfaceVariant = Surface,
    onSurfaceVariant = Ash,
    error = Ember,
    onError = Bone,
    outline = Line,
)

/** True when the user has turned animations off system wide. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun FiloTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        // Dark only, fixed palette. Dynamic colour is deliberately never consulted.
        MaterialTheme(colorScheme = FiloColorScheme, typography = FiloTypography, content = content)
    }
}
