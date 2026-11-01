package com.daniel.reproductor.ui.together.minu

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.reproductor.cloud.HelpLines
import com.daniel.reproductor.ui.together.THREAD_RED

/**
 * Cuando algo suena a peligro, Minu deja de jugar: sin animaciones ni bromas, solo un hilo quieto y las
 * líneas de ayuda con un botón para llamar. Nunca bloquea: si venía de un envío, la persona elige si lo
 * manda igual. Se abre encima de cualquier hoja.
 *
 * [onProceed] null = se abrió desde "Si algo no está bien" (sin envío pendiente).
 */
@Composable
fun MinuCareDialog(help: HelpLines, onDismiss: () -> Unit, onProceed: (() -> Unit)? = null) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(20.dp)) {
            MinuCareContent(help, onDismiss, onProceed)
        }
    }
}

@Composable
fun MinuCareContent(help: HelpLines, onDismiss: () -> Unit, onProceed: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StillThread(Modifier.size(120.dp, 56.dp))
        Text(
            if (onProceed != null) MinuVoice.CARE_TITLE else "Si algo no está bien",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Text(
            if (onProceed != null) MinuVoice.CARE_BODY
            else "Si alguna vez sientes miedo, te hacen daño o piensas en hacerte daño, no tienes que vivirlo solo. Estas líneas te escuchan, gratis y en reserva.",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        help.lines.forEach { line ->
            Surface(color = c.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(line.note, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${line.number}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) {
                        Icon(Icons.Default.Call, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(line.number)
                    }
                }
            }
        }
        Text(help.elsewhere, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(MinuVoice.CARE_PRIVATE, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        if (onProceed != null) {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Volver a mi mensaje") }
            TextButton(onClick = onProceed, modifier = Modifier.fillMaxWidth()) { Text("Enviarlo de todos modos", color = c.onSurfaceVariant) }
        } else {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
        }
    }
}

/** Un hilo rojo quieto que forma un corazón abierto: Minu se aparta y deja el espacio en calma. */
@Composable
private fun StillThread(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // El hilo entra por la izquierda, dibuja un corazón y sigue su camino
            moveTo(0f, h * 0.85f)
            quadraticTo(w * 0.3f, h * 0.85f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.32f, h * 0.62f, w * 0.3f, h * 0.06f, w * 0.44f, h * 0.08f)
            quadraticTo(w * 0.5f, h * 0.1f, w * 0.5f, h * 0.3f)
            quadraticTo(w * 0.5f, h * 0.1f, w * 0.56f, h * 0.08f)
            cubicTo(w * 0.7f, h * 0.06f, w * 0.68f, h * 0.62f, w * 0.5f, h * 0.92f)
            quadraticTo(w * 0.7f, h * 0.85f, w, h * 0.85f)
        }
        drawPath(path, THREAD_RED, style = Stroke(5f, cap = StrokeCap.Round))
        drawCircle(THREAD_RED.copy(alpha = 0.18f), h * 0.45f, Offset(w / 2f, h * 0.42f))
    }
}
