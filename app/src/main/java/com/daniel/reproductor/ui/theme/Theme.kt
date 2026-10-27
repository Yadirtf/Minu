package com.daniel.reproductor.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * Aplica el estilo elegido. [artworkColor] es el color dominante de la portada que suena
 * (lo usa Aurora para teñir toda la app).
 */
@Composable
fun ReproductorTheme(
    style: AppStyle = AppStyles.Neon,
    artworkColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val resolved = when (style.id) {
        StyleId.MATERIAL -> {
            val dark = systemDark
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark) darkColorScheme() else lightColorScheme()
            style.copy(isDark = dark, colors = colors)
        }
        StyleId.AURORA -> style.copy(colors = auroraColors(style.colors, artworkColor))
        else -> style
    }

    val colors = resolved.colors.animated()
    val typography = remember(resolved.id) { resolved.typography() }
    val shapes = remember(resolved.id) { resolved.shapes() }

    CompositionLocalProvider(LocalAppStyle provides resolved) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}

/** Aurora: el acento sale de la portada, aclarado para que se lea sobre fondo oscuro. */
private fun auroraColors(base: ColorScheme, art: Color?): ColorScheme {
    if (art == null) return base
    var accent: Color = art
    // Asegura contraste suficiente sobre el fondo oscuro
    while (accent.luminance() < 0.35f) accent = Color.White.copy(alpha = 0.25f).compositeOver(accent)
    val background = art.copy(alpha = 0.18f).compositeOver(Color(0xFF07070D))
    return base.copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.5f) Color(0xFF101014) else Color.White,
        primaryContainer = accent.copy(alpha = 0.22f),
        background = background,
        surface = background,
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(600)
    val primary by animateColorAsState(primary, spec, label = "primary")
    val primaryContainer by animateColorAsState(primaryContainer, spec, label = "primaryContainer")
    val background by animateColorAsState(background, spec, label = "background")
    val surface by animateColorAsState(surface, spec, label = "surface")
    val onBackground by animateColorAsState(onBackground, spec, label = "onBackground")
    val onSurface by animateColorAsState(onSurface, spec, label = "onSurface")
    return copy(
        primary = primary,
        primaryContainer = primaryContainer,
        background = background,
        surface = surface,
        onBackground = onBackground,
        onSurface = onSurface,
    )
}
