package com.daniel.reproductor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.BuildConfig
import com.daniel.reproductor.ui.theme.LocalAppStyle

/**
 * Panel lateral al tocar el avatar, al estilo de Spotify: tu perfil arriba y debajo
 * los accesos que antes estaban sueltos en Inicio (temas y configuración).
 */
@Composable
fun ProfileDrawer(
    summary: ProfileSummary,
    onViewProfile: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme

    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = c.surfaceContainerLow,
        drawerContentColor = c.onSurface,
        windowInsets = WindowInsets(0),
        modifier = Modifier.width(312.dp),
    ) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            ProfileHeader(summary, onViewProfile)
            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

            summary.partnerName?.let { PartnerPresence(it, summary.partnerOnline) }

            DrawerAction(Icons.Outlined.Palette, style.label("Temas"), trailing = style.name, onClick = onOpenThemes)
            DrawerAction(Icons.Outlined.Settings, style.label("Configuración"), onClick = onOpenSettings)

            Spacer(Modifier.weight(1f))
            Text(
                "Minu ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun ProfileHeader(summary: ProfileSummary, onViewProfile: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onViewProfile).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(summary, size = 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                summary.displayName?.takeIf { summary.signedIn } ?: "Tu perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (summary.signedIn) "Ver perfil" else "Inicia sesión o crea tu cuenta",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PartnerPresence(partner: String, online: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (online) c.primary else c.outline, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            if (online) "$partner está en línea" else "Con $partner",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, trailing: String? = null, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
    }
}
