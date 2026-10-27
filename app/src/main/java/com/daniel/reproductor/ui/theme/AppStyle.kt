package com.daniel.reproductor.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.daniel.reproductor.R

/** Cada estilo cambia colores, fuentes, formas y el diseño del reproductor completo. */
enum class StyleId { NEON, VINYL, AURORA, TERMINAL, BRUTAL, MATERIAL }

@Immutable
data class AppStyle(
    val id: StyleId,
    val name: String,
    val tagline: String,
    val isDark: Boolean,
    val colors: ColorScheme,
    val display: FontFamily,
    val body: FontFamily,
    /** Escala para fuentes "display" que son ópticamente pequeñas (VT323). */
    val displayScale: Float = 1f,
    val artShape: Shape,
    val cardShape: CornerBasedShape,
    val border: Dp = 0.dp,
    val glow: Boolean = false,
    val upperCaseLabels: Boolean = false,
) {
    fun label(text: String) = if (upperCaseLabels) text.uppercase() else text
}

val LocalAppStyle = staticCompositionLocalOf { AppStyles.Neon }

private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

object Fonts {
    val rajdhani = FontFamily(
        Font(R.font.rajdhani_medium, FontWeight.Normal),
        Font(R.font.rajdhani_medium, FontWeight.Medium),
        Font(R.font.rajdhani_bold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    )
    val dmSerif = FontFamily(
        Font(R.font.dm_serif_display, FontWeight.Normal),
        Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val lora = variable(R.font.lora, 400, 500, 600, 700)
    val outfit = variable(R.font.outfit, 300, 400, 500, 600, 700, 800)
    val vt323 = FontFamily(Font(R.font.vt323))
    val jetbrains = variable(R.font.jetbrains_mono, 400, 500, 700)
    val archivoBlack = FontFamily(Font(R.font.archivo_black))
    val spaceGrotesk = variable(R.font.space_grotesk, 400, 500, 700)
}

object AppStyles {

    val Neon = AppStyle(
        id = StyleId.NEON,
        name = "Neón",
        tagline = "Negro profundo y acentos que brillan, al estilo Poweramp.",
        isDark = true,
        colors = darkColorScheme(
            primary = Color(0xFF00E5FF), onPrimary = Color(0xFF001014),
            primaryContainer = Color(0xFF003B44), onPrimaryContainer = Color(0xFF9CF4FF),
            secondary = Color(0xFFFF2E88), onSecondary = Color.White,
            secondaryContainer = Color(0xFF3A0B22), onSecondaryContainer = Color(0xFFFFB3D2),
            tertiary = Color(0xFFB6FF3B),
            background = Color(0xFF07080C), onBackground = Color(0xFFE8ECF2),
            surface = Color(0xFF0B0D12), onSurface = Color(0xFFE8ECF2),
            surfaceVariant = Color(0xFF161A22), onSurfaceVariant = Color(0xFF8A93A6),
            surfaceContainer = Color(0xFF11141B), surfaceContainerHigh = Color(0xFF181C25),
            surfaceContainerHighest = Color(0xFF1F2430), surfaceContainerLow = Color(0xFF0D1015),
            outline = Color(0xFF2A2F3A), outlineVariant = Color(0xFF1E222B),
            error = Color(0xFFFF5370),
        ),
        display = Fonts.rajdhani, body = Fonts.rajdhani,
        artShape = RoundedCornerShape(10.dp), cardShape = RoundedCornerShape(14.dp),
        glow = true,
    )

    val Vinyl = AppStyle(
        id = StyleId.VINYL,
        name = "Vinilo",
        tagline = "Papel cálido, tipografía serif y un disco que gira.",
        isDark = false,
        colors = lightColorScheme(
            primary = Color(0xFFB4441C), onPrimary = Color(0xFFFFF6EA),
            primaryContainer = Color(0xFFF2C9A8), onPrimaryContainer = Color(0xFF3E1404),
            secondary = Color(0xFF2F4A3A), onSecondary = Color(0xFFF3E9D7),
            secondaryContainer = Color(0xFFCFD9C4), onSecondaryContainer = Color(0xFF14231A),
            tertiary = Color(0xFFC8962E),
            background = Color(0xFFF3E9D7), onBackground = Color(0xFF2A1E14),
            surface = Color(0xFFF3E9D7), onSurface = Color(0xFF2A1E14),
            surfaceVariant = Color(0xFFE6D6B8), onSurfaceVariant = Color(0xFF6B5845),
            surfaceContainer = Color(0xFFEBDDC3), surfaceContainerHigh = Color(0xFFE5D4B6),
            surfaceContainerHighest = Color(0xFFDDCAA7), surfaceContainerLow = Color(0xFFEFE3CC),
            outline = Color(0xFFB9A07A), outlineVariant = Color(0xFFD6C29F),
        ),
        display = Fonts.dmSerif, body = Fonts.lora,
        artShape = CircleShape, cardShape = RoundedCornerShape(6.dp),
    )

    val Aurora = AppStyle(
        id = StyleId.AURORA,
        name = "Aurora",
        tagline = "Los colores salen de la portada que está sonando.",
        isDark = true,
        colors = darkColorScheme(
            primary = Color(0xFFA594FF), onPrimary = Color(0xFF14102B),
            primaryContainer = Color(0x33A594FF), onPrimaryContainer = Color(0xFFE4DEFF),
            secondary = Color(0xFFFF8FB1), onSecondary = Color(0xFF2B0A15),
            secondaryContainer = Color(0x33FF8FB1), onSecondaryContainer = Color(0xFFFFD9E4),
            tertiary = Color(0xFF7CE7D4),
            background = Color(0xFF0B0B14), onBackground = Color(0xFFF2F0FA),
            surface = Color(0xFF0B0B14), onSurface = Color(0xFFF2F0FA),
            surfaceVariant = Color(0x1FFFFFFF), onSurfaceVariant = Color(0xB3F2F0FA),
            surfaceContainer = Color(0x14FFFFFF), surfaceContainerHigh = Color(0x1FFFFFFF),
            surfaceContainerHighest = Color(0x29FFFFFF), surfaceContainerLow = Color(0x0FFFFFFF),
            outline = Color(0x33FFFFFF), outlineVariant = Color(0x1FFFFFFF),
        ),
        display = Fonts.outfit, body = Fonts.outfit,
        artShape = RoundedCornerShape(16.dp), cardShape = RoundedCornerShape(24.dp),
    )

    val Terminal = AppStyle(
        id = StyleId.TERMINAL,
        name = "Terminal",
        tagline = "Fósforo verde, pantalla CRT y barras de bloques.",
        isDark = true,
        colors = darkColorScheme(
            primary = Color(0xFF39FF14), onPrimary = Color(0xFF021002),
            primaryContainer = Color(0xFF0E2A0B), onPrimaryContainer = Color(0xFF9DFF8A),
            secondary = Color(0xFFFFB000), onSecondary = Color(0xFF1A1000),
            secondaryContainer = Color(0xFF2A1D00), onSecondaryContainer = Color(0xFFFFD27A),
            tertiary = Color(0xFF00D7FF),
            background = Color(0xFF040704), onBackground = Color(0xFFB8FFB0),
            surface = Color(0xFF040704), onSurface = Color(0xFFB8FFB0),
            surfaceVariant = Color(0xFF0B160B), onSurfaceVariant = Color(0xFF5FA85A),
            surfaceContainer = Color(0xFF081008), surfaceContainerHigh = Color(0xFF0C180C),
            surfaceContainerHighest = Color(0xFF112011), surfaceContainerLow = Color(0xFF060C06),
            outline = Color(0xFF1F4A1C), outlineVariant = Color(0xFF143014),
            error = Color(0xFFFF4040),
        ),
        display = Fonts.vt323, body = Fonts.jetbrains, displayScale = 1.3f,
        artShape = RectangleShape, cardShape = RoundedCornerShape(0.dp),
        border = 1.dp, upperCaseLabels = true,
    )

    val Brutal = AppStyle(
        id = StyleId.BRUTAL,
        name = "Brutal",
        tagline = "Tipografía enorme, bordes duros y rojo señal.",
        isDark = false,
        colors = lightColorScheme(
            primary = Color(0xFFFF3B1F), onPrimary = Color.White,
            primaryContainer = Color(0xFFFF3B1F), onPrimaryContainer = Color.White,
            secondary = Color(0xFF0A0A0A), onSecondary = Color(0xFFF2F0EB),
            secondaryContainer = Color(0xFF0A0A0A), onSecondaryContainer = Color(0xFFF2F0EB),
            tertiary = Color(0xFF1F4BFF),
            background = Color(0xFFF2F0EB), onBackground = Color(0xFF0A0A0A),
            surface = Color(0xFFF2F0EB), onSurface = Color(0xFF0A0A0A),
            surfaceVariant = Color(0xFFE2DFD7), onSurfaceVariant = Color(0xFF3C3C3C),
            surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
            surfaceContainerHighest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F5F0),
            outline = Color(0xFF0A0A0A), outlineVariant = Color(0xFF0A0A0A),
        ),
        display = Fonts.archivoBlack, body = Fonts.spaceGrotesk,
        artShape = RectangleShape, cardShape = RoundedCornerShape(0.dp),
        border = 2.dp, upperCaseLabels = true,
    )

    /** Material You: los colores se calculan del fondo de pantalla en tiempo de ejecución. */
    val Material = AppStyle(
        id = StyleId.MATERIAL,
        name = "Material You",
        tagline = "Los colores de tu fondo de pantalla, limpio y sencillo.",
        isDark = true,
        colors = darkColorScheme(),
        display = FontFamily.Default, body = FontFamily.Default,
        artShape = RoundedCornerShape(12.dp), cardShape = RoundedCornerShape(20.dp),
    )

    val all = listOf(Neon, Vinyl, Aurora, Terminal, Brutal, Material)

    fun byId(id: StyleId) = all.first { it.id == id }
}

/** Tipografía Material con las fuentes del estilo. */
fun AppStyle.typography(): Typography {
    val base = Typography()
    fun TextStyle.display(): TextStyle = copy(
        fontFamily = display,
        fontSize = fontSize * displayScale,
        lineHeight = lineHeight * displayScale,
        letterSpacing = if (id == StyleId.BRUTAL) (-0.02).em else letterSpacing,
    )
    fun TextStyle.body(): TextStyle = copy(fontFamily = body)
    return Typography(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.display(),
        titleMedium = base.titleMedium.body().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.body().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.body(),
        labelSmall = base.labelSmall.body(),
    )
}

fun AppStyle.shapes(): Shapes = Shapes(
    extraSmall = cardShape,
    small = cardShape,
    medium = cardShape,
    large = cardShape,
    extraLarge = cardShape,
)
