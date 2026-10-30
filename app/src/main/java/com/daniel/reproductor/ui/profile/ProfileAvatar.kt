package com.daniel.reproductor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.StyleId

/** Lo que el avatar y el menú de perfil necesitan saber de la cuenta. */
@Immutable
data class ProfileSummary(
    val displayName: String? = null,
    val partnerName: String? = null,
    val partnerOnline: Boolean = false,
) {
    val signedIn get() = !displayName.isNullOrBlank()
    val initial: String? get() = displayName?.trim()?.firstOrNull()?.uppercase()
}

/** Entrada al menú de perfil, disponible en la cabecera de cada pestaña sin pasar callbacks a mano. */
@Immutable
data class ProfileEntry(val summary: ProfileSummary, val open: () -> Unit)

val LocalProfileEntry = compositionLocalOf<ProfileEntry?> { null }

/** Avatar de la cabecera: al tocarlo abre el menú de perfil. No dibuja nada fuera de las pestañas principales. */
@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val entry = LocalProfileEntry.current ?: return
    ProfileAvatar(entry.summary, size = 38.dp, modifier = modifier, onClick = entry.open)
}

/**
 * Inicial del nombre sobre un degradado del color del estilo. Neón, Vinilo, Aurora y Material usan un círculo;
 * Terminal y Brutal, que no tienen curvas, un cuadro con borde.
 * Un punto verde indica que tu pareja está conectada.
 */
@Composable
fun ProfileAvatar(summary: ProfileSummary, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme
    val square = style.id == StyleId.TERMINAL || style.id == StyleId.BRUTAL
    val shape = if (square) RectangleShape else CircleShape
    val fill = when (style.id) {
        StyleId.TERMINAL -> Modifier.background(c.background).border(1.dp, c.primary, shape)
        StyleId.BRUTAL -> Modifier.background(c.primary).border(2.dp, c.onBackground, shape)
        else -> Modifier.background(Brush.linearGradient(listOf(c.primary, c.tertiary)))
    }
    val content = if (style.id == StyleId.TERMINAL) c.primary else c.onPrimary

    Box(modifier.size(size)) {
        val click = onClick?.let { Modifier.clickable(role = Role.Button, onClickLabel = "Abrir perfil", onClick = it) } ?: Modifier
        Box(Modifier.matchParentSize().clip(shape).then(fill).then(click), contentAlignment = Alignment.Center) {
            val initial = summary.initial
            if (initial != null) {
                Text(
                    initial,
                    color = content,
                    fontFamily = style.display,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.44f * style.displayScale).sp,
                )
            } else {
                Icon(Icons.Default.Person, null, tint = content, modifier = Modifier.size(size * 0.56f))
            }
        }
        if (summary.partnerOnline) {
            val dot = size * 0.28f
            // Anillo del color del fondo para que el punto se despegue del avatar
            Box(Modifier.align(Alignment.BottomEnd).size(dot).background(c.background, shape).padding(dot * 0.16f)) {
                Box(Modifier.matchParentSize().background(OnlineGreen, shape))
            }
        }
    }
}

private val OnlineGreen = Color(0xFF2ECC71)
