package com.daniel.reproductor.ui.together

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.BuildConfig
import com.daniel.reproductor.ui.components.PillButton
import kotlinx.coroutines.launch
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.theme.LocalAppStyle
import java.text.DateFormat
import java.util.Date

/** Cuenta en la nube: iniciar sesión, crear cuenta o ver el estado de la sincronización. */
@Composable
fun AccountScreen(vm: TogetherViewModel, onBack: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.padding(top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
            ScreenTitle("Cuenta y nube")
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (account == null) SignInForm(vm) else SignedIn(vm)
        }
    }
}

@Composable
private fun SignInForm(vm: TogetherViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val style = LocalAppStyle.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val googleReady = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    var withPassword by rememberSaveable { mutableStateOf(!googleReady) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(vm.serverUrl) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    Text(
        "Con una cuenta tus playlists se guardan en la nube. Si cambias de celular, inicias sesión y vuelve toda tu música. " +
            "También la necesitas para Juntos.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))

    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text("Conectando… (si el servidor estaba dormido puede tardar un minuto)", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    if (googleReady && activity != null) {
        PillButton(
            "Continuar con Google", Icons.Default.AccountCircle,
            onClick = {
                scope.launch {
                    when (val result = GoogleSignIn.idToken(activity)) {
                        is GoogleSignIn.Result.Token -> vm.googleLogin(result.idToken)
                        is GoogleSignIn.Result.Error -> vm.showMessage(result.message)
                        GoogleSignIn.Result.Cancelled -> Unit
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { withPassword = !withPassword }) {
            Text(if (withPassword) "Ocultar usuario y contraseña" else "Usar usuario y contraseña")
        }
    }

    if (withPassword) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Iniciar sesión", "Crear cuenta").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = registering == (i == 1),
                    onClick = { registering = i == 1 },
                    shape = SegmentedButtonDefaults.itemShape(i, 2, style.cardShape),
                    icon = {},
                ) { Text(style.label(label)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Field(username, { username = it }, "Usuario")
        if (registering) Field(displayName, { displayName = it }, "Tu nombre (lo verá tu pareja)")
        Field(password, { password = it }, "Contraseña", password = true)
        if (registering) Field(invite, { invite = it }, "Código de invitación")
        Spacer(Modifier.height(12.dp))
        PillButton(
            if (registering) "Crear cuenta" else "Entrar", Icons.Default.Cloud,
            onClick = {
                if (registering) vm.register(username, password, displayName, invite) else vm.login(username, password)
            },
            modifier = Modifier.fillMaxWidth(),
            primary = !googleReady,
        )
    }

    TextButton(onClick = { advanced = !advanced }) {
        Text(if (advanced) "Ocultar servidor" else "Servidor: ${vm.serverUrl.removePrefix("https://")}")
    }
    if (advanced) {
        Field(server, { server = it; vm.serverUrl = it }, "URL del servidor (Render)", keyboard = KeyboardType.Uri)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = LocalAppStyle.current.cardShape,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun SignedIn(vm: TogetherViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme
    val user = account ?: return

    Surface(color = c.surfaceContainer, shape = LocalAppStyle.current.cardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(user.displayName.ifBlank { user.username }, style = MaterialTheme.typography.headlineSmall)
            Text("@${user.username}", color = c.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, text, tint) = when {
                    status.running || busy -> Triple(Icons.Default.Sync, "Sincronizando…", c.primary)
                    status.error != null -> Triple(Icons.Default.CloudOff, status.error.orEmpty(), c.error)
                    status.lastSyncAt > 0 -> Triple(
                        Icons.Default.CloudDone,
                        "Respaldado: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(status.lastSyncAt)),
                        c.primary,
                    )
                    else -> Triple(Icons.Default.Cloud, "Aún sin sincronizar", c.onSurfaceVariant)
                }
                Icon(icon, null, tint = tint)
                Spacer(Modifier.size(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Tus playlists se suben solas unos segundos después de cada cambio. En un celular nuevo, inicia sesión con este usuario " +
            "y todo vuelve. Las canciones descargadas se bajan de nuevo desde cada playlist.",
        style = MaterialTheme.typography.bodySmall,
        color = c.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    PillButton("Sincronizar ahora", Icons.Default.Sync, vm::syncNow, Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    PillButton("Cerrar sesión", Icons.AutoMirrored.Filled.Logout, vm::logout, Modifier.fillMaxWidth(), primary = false)
}
