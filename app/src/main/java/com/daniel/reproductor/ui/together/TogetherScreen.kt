package com.daniel.reproductor.ui.together

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.ListenMode
import com.daniel.reproductor.cloud.PartnerInfo
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.EmptyState
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.profile.ProfileButton
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.theme.LocalAppStyle
import java.text.DateFormat
import java.util.Date

/** Pestaña Juntos: vincular a tu pareja, dedicarle canciones y escuchar música a la vez. */
@Composable
fun TogetherScreen(vm: TogetherViewModel, lib: LibraryViewModel, player: PlayerState?, onOpenAccount: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val partner by vm.partner.collectAsStateWithLifecycle()
    val dedications by vm.dedications.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var dedicating by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileButton(Modifier.padding(end = 14.dp))
                ScreenTitle("Juntos", subtitle = "Tu música, compartida")
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        val user = account
        val status = partner
        when {
            user == null -> item {
                EmptyState(
                    Icons.Default.Favorite, "Comparte tu música",
                    "Crea tu cuenta para vincularte con tu pareja, dedicarle canciones y escuchar juntos. " +
                        "También respalda tu música en la nube.",
                )
                PillButton("Crear cuenta o iniciar sesión", Icons.Default.Cloud, onOpenAccount, Modifier.fillMaxWidth())
            }
            status == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            status.status == "none" -> item { LinkPartner(vm, user.username) }
            status.status == "sent" -> item {
                InfoCard(
                    "Esperando a ${status.partner?.displayName}",
                    "Le enviaste una invitación. Cuando abra Minu y la acepte, quedarán vinculados.",
                ) { TextButton(onClick = vm::unlinkPartner) { Text("Cancelar invitación") } }
            }
            status.status == "received" -> item {
                InfoCard(
                    "${status.partner?.displayName} quiere vincularse contigo 💞",
                    "Podrán dedicarse canciones y escuchar música al mismo tiempo.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Aceptar", Icons.Default.Favorite, vm::acceptPartner, Modifier.weight(1f))
                        PillButton("Rechazar", Icons.Default.LinkOff, vm::unlinkPartner, Modifier.weight(1f), primary = false)
                    }
                }
            }
            else -> {
                val p = status.partner ?: return@LazyColumn
                item { CoupleCard(user.displayName.ifBlank { user.username }, p) }
                item { ListenCard(vm, p) }
                item { ReactionRow(vm) }
                item {
                    PillButton("Dedicar una canción", Icons.Default.Send, { dedicating = true }, Modifier.fillMaxWidth())
                }
                if (dedications.isNotEmpty()) {
                    item { Text(LocalAppStyle.current.label("Dedicatorias"), style = MaterialTheme.typography.titleLarge) }
                    items(dedications, key = { it.id }) { d -> DedicationCard(d, vm) }
                }
                item {
                    TextButton(onClick = vm::unlinkPartner, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LinkOff, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Desvincular")
                    }
                }
            }
        }
    }

    if (dedicating) {
        DedicateSheet(
            lib = lib, player = player,
            partnerOnline = partner?.partner?.online == true,
            partnerName = partner?.partner?.displayName.orEmpty(),
            onDismiss = { dedicating = false },
            onSend = { song, message, autoplay -> vm.sendDedication(song, message, autoplay); dedicating = false },
        )
    }
}

@Composable
private fun LinkPartner(vm: TogetherViewModel, myUsername: String) {
    var username by rememberSaveable { mutableStateOf("") }
    InfoCard(
        "Vincula a tu pareja 💞",
        "Escribe su usuario de Minu. Tu usuario es @$myUsername: compártelo para que te encuentre.",
    ) {
        OutlinedTextField(
            value = username, onValueChange = { username = it }, label = { Text("Usuario de tu pareja") },
            singleLine = true, shape = LocalAppStyle.current.cardShape, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PillButton("Enviar invitación", Icons.Default.PersonAdd, { vm.requestPartner(username) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoCard(title: String, text: String, content: @Composable () -> Unit) {
    val style = LocalAppStyle.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = style.cardShape,
        border = if (style.border > 0.dp) androidx.compose.foundation.BorderStroke(style.border, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Tarjeta de la pareja: iniciales, corazón que late y si está conectada. */
@Composable
private fun CoupleCard(me: String, partner: PartnerInfo) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    val beat by rememberInfiniteTransition(label = "heart").animateFloat(
        1f, 1.18f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "beat",
    )
    Box(
        Modifier.fillMaxWidth().clip(style.cardShape)
            .background(Brush.linearGradient(listOf(c.primary, c.secondary)))
            .padding(22.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me, online = true)
                Icon(
                    Icons.Default.Favorite, null, tint = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp).size(34.dp).scale(if (partner.online) beat else 1f),
                )
                Avatar(partner.displayName, online = partner.online)
            }
            Spacer(Modifier.height(14.dp))
            Text("$me & ${partner.displayName}", color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                if (partner.online) "${partner.displayName} está en Minu ahora" else "${partner.displayName} no tiene Minu abierta ahora",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Avatar(name: String, online: Boolean) {
    Box {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), color = Color.White, fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                .background(if (online) Color(0xFF3DDC84) else Color(0xFF9E9E9E))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

/** Escuchar juntos: invitar, unirse, tomar el control o salir. */
@Composable
private fun ListenCard(vm: TogetherViewModel, partner: PartnerInfo) {
    val listen by vm.listen.collectAsStateWithLifecycle()
    val name = partner.displayName
    when (listen.mode) {
        ListenMode.NONE -> InfoCard(
            "Escuchar juntos 🎧",
            "Lo que suene en tu celular sonará en el de $name, al mismo tiempo. Pueden turnarse el control.",
        ) {
            PillButton(
                "Invitar a $name", Icons.Default.Headphones, vm::inviteListen, Modifier.fillMaxWidth(),
            )
            if (!partner.online) {
                Spacer(Modifier.height(8.dp))
                Text("$name debe tener Minu abierta para unirse.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ListenMode.INVITING -> InfoCard("Esperando a $name…", "Le llegó tu invitación para escuchar juntos.") {
            TextButton(onClick = vm::leaveListen) { Text("Cancelar") }
        }
        ListenMode.INVITED -> InfoCard("$name te invita a escuchar juntos 🎧", "Escucharás lo mismo que suena en su celular.") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Unirme", Icons.Default.Headphones, vm::acceptListen, Modifier.weight(1f))
                PillButton("Ahora no", Icons.Default.LinkOff, vm::leaveListen, Modifier.weight(1f), primary = false)
            }
        }
        ListenMode.ACTIVE -> InfoCard(
            "Escuchando juntos 🎶",
            if (listen.iAmDj) "Tú controlas la música: lo que pongas suena en el celular de $name."
            else "$name controla la música. Puedes reaccionar o tomar el control.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!listen.iAmDj) PillButton("Tomar el control", Icons.Default.SportsEsports, vm::takeControl, Modifier.weight(1f))
                PillButton("Salir", Icons.Default.LinkOff, vm::leaveListen, Modifier.weight(1f), primary = false)
            }
        }
    }
}

@Composable
private fun ReactionRow(vm: TogetherViewModel) {
    Column {
        Text(LocalAppStyle.current.label("Mándale una reacción"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(TogetherViewModel.EMOJIS) { emoji ->
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { vm.sendLiveReaction(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun DedicationCard(d: Dedication, vm: TogetherViewModel) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    Surface(color = c.surfaceContainer, shape = style.cardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (d.incoming) "${d.fromName} te dedicó" else "Le dedicaste",
                style = MaterialTheme.typography.labelLarge, color = c.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(d.song.thumbnailUrl, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(d.song.uploader, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { vm.play(d) }) { Icon(Icons.Default.PlayArrow, "Reproducir", tint = c.primary) }
            }
            if (d.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("“${d.message}”", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(d.createdAt)) +
                        if (!d.incoming && d.seenAt != null) " · Visto ✓✓" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                Text(d.reactions.takeLast(6).joinToString("") { it.emoji }, fontSize = 18.sp)
            }
            AnimatedVisibility(d.incoming) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TogetherViewModel.EMOJIS.take(4).forEach { emoji ->
                        Text(
                            emoji, fontSize = 22.sp,
                            modifier = Modifier.clip(CircleShape).clickable { vm.react(d, emoji) }.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}
