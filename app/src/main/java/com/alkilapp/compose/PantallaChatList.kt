package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R
import com.alkilapp.data.ChatAlkil
import com.alkilapp.data.PerfilUsuario
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Un elemento aplanado de la bandeja: encabezado de inmueble o fila de chat. */
sealed interface ElemChat {
    data class Encabezado(val listingTitle: String) : ElemChat
    data class Fila(val chat: ChatAlkil) : ElemChat
}

/**
 * Bandeja de chats en Compose: agrupa por inmueble (encabezado + filas),
 * avatar circular de color, escudo verificado, ultimo mensaje (bold si hay
 * no-leidos), hora relativa y badges (no-leidos / "iniciado por ti").
 * Reemplaza activity_chat_list.xml + ChatListAdapter.
 */
@Composable
fun PantallaChatList(
    elementos: List<ElemChat>,
    perfiles: Map<String, PerfilUsuario>,
    miUid: String,
    onChat: (ChatAlkil) -> Unit,
    onAtras: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        Cabecera(titulo = stringResource(R.string.chat_titulo_bandeja), onAtras = onAtras)

        if (elementos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AlkilColores.Fondo),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(AlkilColores.PrimarioSuave),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chat),
                            contentDescription = null,
                            tint = AlkilColores.Primario,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_bandeja_vacia),
                        color = AlkilColores.TextoSuave,
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp, start = 40.dp, end = 40.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = 16.dp
                )
            ) {
                items(elementos) { elem ->
                    when (elem) {
                        is ElemChat.Encabezado -> EncabezadoInmueble(elem.listingTitle)
                        is ElemChat.Fila -> FilaChat(
                            chat = elem.chat,
                            perfil = elem.chat.otrosParticipantes.firstOrNull()
                                ?.let { perfiles[it] },
                            esIniciadoPorMi = elem.chat.creatorId == miUid,
                            onClick = { onChat(elem.chat) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EncabezadoInmueble(titulo: String) {
    Text(
        text = titulo,
        color = AlkilColores.TextoSuave,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun FilaChat(
    chat: ChatAlkil,
    perfil: PerfilUsuario?,
    esIniciadoPorMi: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circular de color
            val colorAvatar = colorResource(
                coloresAvatar[Math.floorMod((perfil?.uid ?: chat.chatId).hashCode(), coloresAvatar.size)]
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(colorAvatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = perfil?.inicial?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = perfil?.nombre ?: "…",
                        color = AlkilColores.Texto,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.lastMessageAt > 0) {
                        Text(
                            text = formatearHora(chat.lastMessageAt),
                            color = AlkilColores.TextoSuave,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (perfil?.verificado == true) {
                        Icon(
                            painter = painterResource(R.drawable.ic_verificado_escudo),
                            contentDescription = stringResource(R.string.chat_identidad_verificada),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = chat.listingTitle,
                        color = AlkilColores.TextoSuave,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 3.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val hayNoLeidos = chat.unreadMio > 0
                    Text(
                        text = chat.lastMessage.ifBlank { stringResource(R.string.chat_sin_mensajes) },
                        color = if (hayNoLeidos) AlkilColores.Texto else AlkilColores.TextoSuave,
                        fontSize = 13.sp,
                        fontWeight = if (hayNoLeidos) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hayNoLeidos) {
                        BadgeCircular(texto = chat.unreadMio.toString(), fondo = AlkilColores.Acento)
                    }
                    if (esIniciadoPorMi) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chat_iniciado_por_ti),
                            color = AlkilColores.Primario,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AlkilColores.PrimarioSuave)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCircular(texto: String, fondo: Color) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(fondo),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val coloresAvatar = listOf(
    R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
)

// ===== Formato de hora (portado del adapter) =====
private val horaFormato = SimpleDateFormat("h:mm a", Locale.US)
private val diaFormato = SimpleDateFormat("dd MMM", Locale("es", "PE"))
private val diaNombreFormato = SimpleDateFormat("EEEE", Locale("es", "PE"))

private fun formatearHora(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val hoy = Calendar.getInstance()
    val diffDias = hoy.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR)
    val diffAnios = hoy.get(Calendar.YEAR) - cal.get(Calendar.YEAR)

    return when {
        esHoy(millis) -> horaFormato.format(Date(millis))
        diffAnios == 0 && diffDias == 1 -> "Ayer ${horaFormato.format(Date(millis))}"
        diffAnios == 0 && diffDias <= 3 -> "${
            diaNombreFormato.format(Date(millis)).replaceFirstChar { it.uppercase() }
        } ${horaFormato.format(Date(millis))}"

        else -> diaFormato.format(Date(millis))
    }
}

private fun esHoy(millis: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = millis }
    val c2 = Calendar.getInstance()
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
