package com.daniel.reproductor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.StyleId

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Inicio", Icons.Outlined.Home, Icons.Filled.Home),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic),
    SEARCH("Buscar", Icons.Outlined.Search, Icons.Filled.Search),
    TOGETHER("Juntos", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
}

/** Barra inferior con Inicio, Playlists y Buscar, dibujada distinto en cada estilo. */
@Composable
fun StyledBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme
    when (style.id) {
        StyleId.MATERIAL -> NavigationBar {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(if (tab == selected) tab.selectedIcon else tab.icon, null) },
                    label = { Text(tab.label) },
                )
            }
        }
        StyleId.AURORA -> Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().shadow(12.dp, CircleShape).clip(CircleShape)
                    .background(c.background).background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape).padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Tab.entries.forEach { tab ->
                    val on = tab == selected
                    val bg by animateColorAsState(if (on) c.primary else Color.Transparent, label = "tab")
                    Row(
                        Modifier.clip(CircleShape).background(bg).noRippleClick { onSelect(tab) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (on) tab.selectedIcon else tab.icon, tab.label, tint = if (on) c.onPrimary else Color.White.copy(alpha = 0.7f))
                        if (on) {
                            Spacer(Modifier.width(6.dp))
                            Text(tab.label, color = c.onPrimary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        StyleId.TERMINAL -> Column(Modifier.background(c.background).navigationBarsPadding()) {
            HorizontalDivider(color = c.primary, thickness = 1.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEach { tab ->
                    val on = tab == selected
                    Text(
                        "[${tab.label.uppercase()}]",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (on) c.onPrimary else c.primary,
                        modifier = Modifier.background(if (on) c.primary else Color.Transparent)
                            .noRippleClick { onSelect(tab) }.padding(horizontal = 6.dp),
                    )
                }
            }
        }
        StyleId.BRUTAL -> Row(Modifier.background(c.onBackground).navigationBarsPadding().fillMaxWidth().height(64.dp)) {
            Tab.entries.forEachIndexed { i, tab ->
                val on = tab == selected
                if (i > 0) Box(Modifier.width(2.dp).height(64.dp).background(c.background))
                Column(
                    Modifier.weight(1f).height(64.dp).background(if (on) c.primary else c.onBackground).noRippleClick { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (on) tab.selectedIcon else tab.icon, null, tint = c.background)
                    Text(tab.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.background)
                }
            }
        }
        else -> { // Neón y Vinilo
            val neon = style.id == StyleId.NEON
            Column(Modifier.background(if (neon) c.surfaceContainerLow else c.surfaceContainer).navigationBarsPadding()) {
                HorizontalDivider(color = c.outlineVariant)
                Row(Modifier.fillMaxWidth().height(64.dp)) {
                    Tab.entries.forEach { tab ->
                        val on = tab == selected
                        val scale by animateFloatAsState(if (on) 1.12f else 1f, label = "scale")
                        val tint = if (on) c.primary else c.onSurfaceVariant
                        Column(
                            Modifier.weight(1f).height(64.dp).noRippleClick { onSelect(tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (on && neon) Box(Modifier.size(28.dp).glow(c.primary, 10.dp, 0.45f))
                                Icon(if (on) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.scale(scale))
                            }
                            Text(style.label(tab.label), style = MaterialTheme.typography.labelMedium, color = tint)
                            if (!neon) {
                                Box(
                                    Modifier.padding(top = 2.dp).width(if (on) 22.dp else 0.dp).height(2.dp)
                                        .clip(RoundedCornerShape(1.dp)).background(c.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
