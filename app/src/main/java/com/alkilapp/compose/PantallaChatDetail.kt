package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R
import com.alkilapp.data.Mensaje

private val TickLeido = Color(0xFF38BDF8)
private val CitaFondo = Color(0xFFFFF0EF)
private val CitaBorde = Color(0xFFFF6B5E)
private val SistemaFondo = Color(0xFFF2EFE9)
private val AvatarLight = Color(0xFFE2E8F0)
private val HeaderFondo = Color(0xFFFFFFFF)

/** Un item ya aplanado de la conversacion (separador de dia o mensaje). */
sealed class ChatItemUi {
    data class Dia(val texto: String) : ChatItemUi()
    data class Msg(val mensaje: Mensaje) : ChatItemUi()
}

/** Estado visible del header + barra de escritura del chat. */
data class ChatUiState(
    val nombre: String = "",
    val inicial: String = "?",
    val colorAvatar: Color = AlkilColores.Primario,
    val listingTitulo: String = "",
    val precio: String = "",
    val verificado: Boolean = false,
    val ratingTexto: String? = null,
    val cerrado: Boolean = false,
    val entradaActiva: Boolean = true,
    val mostrarBotonCita: Boolean = false
)

private val AvatarColores = listOf(
    Color(0xFF1E293B), Color(0xFFFF6B5E), Color(0xFF3AA37B),
    Color(0xFF64748B), Color(0xFF334155)
)

fun colorAvatarChat(uid: String): Color =
    AvatarColores[Math.floorMod(uid.hashCode(), AvatarColores.size)]

/**
 * Conversacion del chat en Compose: header con perfil del interlocutor + menu,
 * hilo de mensajes (texto/cita/sistema/propuesta con separadores de dia y
 * checks de entrega/lectura), aviso de chat cerrado y barra de escritura.
 * Reemplaza activity_chat_detail.xml + MensajeAdapter. La logica Firebase
 * (listeners, recibos, anti-spam, citas) sigue en ChatDetailActivity.
 */
@Composable
fun PantallaChatDetail(
    ui: ChatUiState,
    items: List<ChatItemUi>,
    miUid: String,
    listaState: LazyListState,
    textoEntrada: String,
    onTextoEntrada: (String) -> Unit,
    onEnviar: () -> Unit,
    onCita: () -> Unit,
    onAtras: () -> Unit,
    onCerrarChat: () -> Unit,
    onEliminarChat: () -> Unit,
    onAceptar: (Mensaje) -> Unit,
    onRechazar: (Mensaje) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
            .imePadding()
    ) {
        HeaderChat(
            ui = ui,
            onAtras = onAtras,
            onCerrarChat = onCerrarChat,
            onEliminarChat = onEliminarChat
        )

        // Subtitulo estatico del inmueble: titulo + precio
        if (ui.listingTitulo.isNotBlank() || ui.precio.isNotBlank()) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8EEF6))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ui.listingTitulo.isNotBlank()) {
                        Text(
                            text = ui.listingTitulo,
                            color = AlkilColores.Texto,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (ui.precio.isNotBlank()) {
                        if (ui.listingTitulo.isNotBlank()) Spacer(Modifier.width(10.dp))
                        Text(
                            text = ui.precio,
                            color = AlkilColores.Primario,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AlkilColores.Borde))
            }
        }

        LazyColumn(
            state = listaState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is ChatItemUi.Dia -> SeparadorDia(item.texto)
                    is ChatItemUi.Msg -> ItemMensaje(
                        m = item.mensaje,
                        miUid = miUid,
                        onAceptar = onAceptar,
                        onRechazar = onRechazar
                    )
                }
            }
        }

        // Aviso de inmueble alquilado / chat cerrado
        if (ui.cerrado) {
            BannerCerrado(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }

        BarraEscritura(
            texto = textoEntrada,
            onTexto = onTextoEntrada,
            onEnviar = onEnviar,
            onCita = onCita,
            mostrarCita = ui.mostrarBotonCita,
            activa = ui.entradaActiva,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun HeaderChat(
    ui: ChatUiState,
    onAtras: () -> Unit,
    onCerrarChat: () -> Unit,
    onEliminarChat: () -> Unit
) {
    var menuAbierto = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderFondo)
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAtras) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.chat_atras),
                tint = AlkilColores.Texto
            )
        }
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ui.colorAvatar),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ui.inicial,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ui.nombre,
                    color = AlkilColores.Texto,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (ui.verificado) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_verificado_escudo),
                        contentDescription = stringResource(R.string.chat_identidad_verificada),
                        tint = AlkilColores.Verde,
                        modifier = Modifier.size(14.dp)
                    )
                }
                ui.ratingTexto?.let { rating ->
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        tint = AlkilColores.CoralTexto,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = rating,
                        color = AlkilColores.CoralTexto,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuAbierto.value = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.chat_menu_opciones),
                    tint = AlkilColores.Texto
                )
            }
            DropdownMenu(
                expanded = menuAbierto.value,
                onDismissRequest = { menuAbierto.value = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_cerrar)) },
                    onClick = {
                        menuAbierto.value = false
                        onCerrarChat()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_eliminar)) },
                    onClick = {
                        menuAbierto.value = false
                        onEliminarChat()
                    }
                )
            }
        }
    }
}

@Composable
private fun SeparadorDia(texto: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(AlkilColores.PrimarioSuave)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = texto,
                color = AlkilColores.TextoSuave,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ItemMensaje(
    m: Mensaje,
    miUid: String,
    onAceptar: (Mensaje) -> Unit,
    onRechazar: (Mensaje) -> Unit
) {
    when (m.tipo) {
        com.alkilapp.data.TipoMensaje.SISTEMA -> ItemSistema(m)
        com.alkilapp.data.TipoMensaje.CITA -> ItemCita(m, miUid, onAceptar, onRechazar)
        com.alkilapp.data.TipoMensaje.PROPUESTA -> ItemPropuesta(m, miUid, onAceptar, onRechazar)
        else -> ItemTexto(m, miUid)
    }
}

@Composable
private fun ItemTexto(m: Mensaje, miUid: String) {
    val esMio = m.senderId == miUid
    if (m.text.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (esMio) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    if (esMio) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
                    else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
                )
                .background(if (esMio) AlkilColores.Acento else AlkilColores.Tarjeta)
                .then(
                    if (esMio) Modifier
                    else Modifier.border(1.dp, AlkilColores.Borde, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp))
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = m.text,
                color = if (esMio) Color.White else AlkilColores.Texto,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }
        if (m.sentAt > 0) {
            TextoTiempo(m, esMio, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ItemSistema(m: Mensaje) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SistemaFondo)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = m.text,
                color = AlkilColores.TextoSuave,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        if (m.sentAt > 0) {
            TextoTiempo(m, false, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ItemCita(
    m: Mensaje,
    miUid: String,
    onAceptar: (Mensaje) -> Unit,
    onRechazar: (Mensaje) -> Unit
) {
    val esMio = m.senderId == miUid
    val estado = m.citaEstado?.lowercase() ?: "pendiente"
    val soyDestinatario = m.solicitadoPor != null && m.solicitadoPor != miUid
    val textoEstado = when (estado) {
        "aceptada" -> stringResource(R.string.cita_estado_aceptada)
        "rechazada" -> stringResource(
            if (m.solicitadoPor == miUid) R.string.cita_estado_rechazada else R.string.cita_estado_rechazaste
        )
        else -> stringResource(R.string.cita_estado_pendiente)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (esMio) Alignment.End else Alignment.Start
    ) {
        TarjetaEspecial(
            maxAncho = 260.dp,
            textoEstado = textoEstado
        ) {
            Text(
                text = stringResource(R.string.cita_tipo),
                color = AlkilColores.Primario,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = m.fecha ?: "",
                    color = AlkilColores.Texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = m.hora ?: "",
                    color = AlkilColores.Texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = textoEstado,
                color = AlkilColores.TextoSuave,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (soyDestinatario && estado == "pendiente") {
                FilaBotonesCita(
                    onAceptar = { onAceptar(m) },
                    onRechazar = { onRechazar(m) }
                )
            }
        }
        if (m.sentAt > 0) {
            TextoTiempo(m, esMio, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ItemPropuesta(
    m: Mensaje,
    miUid: String,
    onAceptar: (Mensaje) -> Unit,
    onRechazar: (Mensaje) -> Unit
) {
    val esMio = m.senderId == miUid
    val estado = m.propuestaEstado?.lowercase() ?: "pendiente"
    val propuestoPor = m.propuestoPor ?: ""
    val propositorEsMio = propuestoPor == miUid
    val soyDestinatario = m.propuestoPor != null && m.propuestoPor != miUid
    val monto = m.monto?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "0"
    val textoEstado = when (estado) {
        "aceptada" -> if (propositorEsMio) "Aceptada ✓" else "Aceptaste ✓"
        "rechazada" -> if (propositorEsMio) "Rechazada ✗" else "Rechazaste ✗"
        else -> if (propositorEsMio) "Esperando respuesta..." else "Tu respuesta"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (esMio) Alignment.End else Alignment.Start
    ) {
        TarjetaEspecial(maxAncho = 260.dp, textoEstado = textoEstado) {
            Text(
                text = stringResource(R.string.propuesta_alquiler),
                color = AlkilColores.Primario,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$monto ${m.moneda}",
                color = AlkilColores.Texto,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = textoEstado,
                color = AlkilColores.TextoSuave,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (soyDestinatario && estado == "pendiente") {
                FilaBotonesCita(
                    onAceptar = { onAceptar(m) },
                    onRechazar = { onRechazar(m) }
                )
            }
        }
        if (m.sentAt > 0) {
            TextoTiempo(m, esMio, Modifier.padding(top = 2.dp))
        }
    }
}

/** Card especial (cita/propuesta) con borde coral y fondo tenue. */
@Composable
private fun TarjetaEspecial(
    maxAncho: androidx.compose.ui.unit.Dp,
    textoEstado: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = maxAncho)
            .clip(RoundedCornerShape(12.dp))
            .background(CitaFondo)
            .border(1.dp, CitaBorde, RoundedCornerShape(12.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun FilaBotonesCita(onAceptar: () -> Unit, onRechazar: () -> Unit) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        BotonMini(
            texto = stringResource(R.string.aceptar),
            fondo = Brush.horizontalGradient(listOf(AlkilColores.Verde, Color(0xFF34B07E))),
            onClick = onAceptar,
            modifier = Modifier.padding(end = 6.dp)
        )
        BotonMini(
            texto = stringResource(R.string.rechazar),
            fondo = Brush.horizontalGradient(listOf(AlkilColores.Rojo, Color(0xFFF06565))),
            onClick = onRechazar
        )
    }
}

@Composable
private fun BotonMini(
    texto: String,
    fondo: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text = texto, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Hora + checks estilo WhatsApp (✓ enviado / ✓✓ entregado / ✓✓ leído en color). */
@Composable
private fun TextoTiempo(m: Mensaje, esMio: Boolean, modifier: Modifier = Modifier) {
    val hora = remember(m.sentAt) {
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(m.sentAt))
    }
    if (!esMio || m.sentAt <= 0) {
        Text(text = hora, color = AlkilColores.TextoSuave, fontSize = 10.sp, modifier = modifier)
        return
    }
    val leido = m.readAt != null
    val entregado = m.deliveredAt != null
    val ticks = if (entregado) "✓✓" else "✓"
    val anotado: AnnotatedString = buildAnnotatedString {
        append(hora)
        append("  ")
        withStyle(SpanStyle(color = if (leido) TickLeido else AlkilColores.TextoSuave)) {
            append(ticks)
        }
    }
    Text(text = anotado, fontSize = 10.sp, modifier = modifier)
}

@Composable
private fun BannerCerrado(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AlkilColores.CoralSuave)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = stringResource(R.string.chat_cerrado_inmueble),
            tint = AlkilColores.CoralTexto,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.chat_cerrado_inmueble),
            color = AlkilColores.CoralTexto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}

@Composable
private fun BarraEscritura(
    texto: String,
    onTexto: (String) -> Unit,
    onEnviar: () -> Unit,
    onCita: () -> Unit,
    mostrarCita: Boolean,
    activa: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AlkilColores.Tarjeta)
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mostrarCita) {
            IconButton(
                onClick = onCita,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_solicitar_alquiler),
                    contentDescription = stringResource(R.string.agendar_cita),
                    tint = AlkilColores.Acento
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(AlkilColores.Fondo)
                .border(1.dp, AlkilColores.Borde, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (texto.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_hint_escribir),
                    color = AlkilColores.TextoSuave,
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = texto,
                onValueChange = { if (it.length <= 1000) onTexto(it) },
                enabled = activa,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = AlkilColores.Texto,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(AlkilColores.Acento),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { onEnviar() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(
            onClick = onEnviar,
            enabled = activa,
            modifier = Modifier.padding(start = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (activa) AlkilColores.Acento else AlkilColores.Borde),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.chat_enviar),
                    tint = Color.White
                )
            }
        }
    }
}
