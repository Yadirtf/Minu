package com.daniel.reproductor.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.ui.theme.LocalAppStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    vm: SettingsViewModel,
    onDismiss: () -> Unit,
    onOpenStyles: () -> Unit,
    onOpenResolverTest: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val style = LocalAppStyle.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = style.cardShape,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(style.label("Ajustes"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.padding(4.dp))
            SettingRow(Icons.Default.Cloud, "Cuenta y nube", "Respalda tu música y recupérala en otro celular") {
                onDismiss(); onOpenAccount()
            }
            SettingRow(Icons.Default.Palette, "Estilo de la app", style.name) { onDismiss(); onOpenStyles() }
            SettingRow(Icons.Default.Wifi, "Descargar solo con Wi-Fi", "Las descargas esperan a tener Wi-Fi",
                trailing = { Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnly) }) { vm.setWifiOnly(!wifiOnly) }
            SettingRow(Icons.Default.Upload, "Exportar playlists", "Guarda un respaldo en JSON") {
                exportLauncher.launch(SettingsViewModel.backupFileName())
            }
            SettingRow(Icons.Default.Download, "Importar playlists", "Desde un respaldo en JSON") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            SettingRow(Icons.Default.BugReport, "Probar resolver", "Pega un link y mira la URL del audio") {
                onDismiss(); onOpenResolverTest()
            }
            SettingRow(Icons.Default.Info, "NewPipe Extractor", vm.extractorVersion, onClick = null)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
