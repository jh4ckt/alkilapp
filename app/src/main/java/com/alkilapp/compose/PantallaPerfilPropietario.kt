package com.alkilapp.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R
import com.alkilapp.data.Propiedad

/** Una resena ya parseada para la UI. */
data class ResenaUi(val autor: String, val rating: Int, val comentario: String)

/** Estado visible del perfil publico de un propietario. */
data class PerfilPropietarioUiState(
    val encontrado: Boolean = true,
    val nombre: String = "",
    val inicial: String = "?",
    val colorInicial: Color = AlkilColores.Primario,
    val rol: String = "",
    val verificado: Boolean = false,
    val textoVerificado: String = "",
    val ratingTexto: String? = null,
    val miembroDesde: String? = null,
    val foto: Bitmap? = null,
    val puedeChatear: Boolean = false,
    val puedeResenar: Boolean = false,
    val esDueno: Boolean = false
)

private val AvatarColores = listOf(
    Color(0xFF1E293B), Color(0xFFFF6B5E), Color(0xFF3AA37B),
    Color(0xFF64748B), Color(0xFF334155)
)

private val HeroGradiente = listOf(
    Color(0xFFF0E7D9), Color(0xFFF8F1E5), Color(0xFFFFFAF2)
)
private val ReviewGradiente = listOf(Color(0xFFF3EDE2), Color(0xFFFFFAF2))
private val InmuebleGradiente = listOf(Color(0xFFF1EBDD), Color(0xFFFBF8F0))

fun colorAvatarPorUid(uid: String): Color =
    AvatarColores[Math.floorMod(uid.hashCode(), AvatarColores.size)]

/**
 * Perfil publico de un propietario en Compose (estilo dialog centrado con scrim).
 * Muestra avatar+nombre+rol+verificacion+rating, su lista de inmuebles publicados
 * (con acciones de dueño), las resenas, y el boton de chat. Reemplaza
 * activity_perfil_propietario.xml. Los dialogos (resena, confirmaciones) se
 * quedan como MaterialAlertDialogBuilder en la Activity.
 */
@Composable
fun PantallaPerfilPropietario(
    ui: PerfilPropietarioUiState,
    inmuebles: List<Propiedad>,
    resenas: List<ResenaUi>,
    onCerrar: () -> Unit,
    onChat: () -> Unit,
    onResena: () -> Unit,
    onInmueble: (Propiedad) -> Unit,
    onFinalizar: (Propiedad) -> Unit,
    onPausar: (Propiedad) -> Unit,
    onReactivar: (Propiedad) -> Unit,
    onDestacar: (Propiedad) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        Cabecera(
            titulo = stringResource(R.string.perfil_titulo),
            onAtras = onCerrar
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // ===== Hero: avatar + nombre + rol + rating =====
                Hero(ui, onResena = onResena)

                // ===== Inmuebles publicados =====
                Text(
                    text = stringResource(R.string.perfil_inmuebles),
                    color = AlkilColores.Texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                if (inmuebles.isEmpty()) {
                    TextoVacio(stringResource(R.string.perfil_inmuebles_vacio))
                } else {
                    inmuebles.forEach { p ->
                        TarjetaInmueble(
                            p = p,
                            esDueno = ui.esDueno,
                            onClick = { onInmueble(p) },
                            onFinalizar = { onFinalizar(p) },
                            onPausar = { onPausar(p) },
                            onReactivar = { onReactivar(p) },
                            onDestacar = { onDestacar(p) },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // ===== Resenas =====
                Text(
                    text = stringResource(R.string.perfil_reviews),
                    color = AlkilColores.Texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                if (resenas.isEmpty()) {
                    TextoVacio(stringResource(R.string.perfil_reviews_vacio))
                } else {
                    resenas.forEach { r ->
                        TarjetaResena(r, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                // ===== Boton chatear =====
                if (ui.puedeChatear) {
                    BotonCta(
                        texto = stringResource(R.string.perfil_chatear),
                        onClick = onChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(48.dp)
                    )
                }

                if (!ui.encontrado) {
                    Text(
                        text = stringResource(R.string.perfil_no_encontrado),
                        color = AlkilColores.TextoSuave,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(ui: PerfilPropietarioUiState, onResena: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(HeroGradiente))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar 80dp
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            if (ui.foto != null) {
                Image(
                    bitmap = ui.foto!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.perfil_foto_cd),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFE98270), Color(0xFFC74A3D)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ui.inicial,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = ui.nombre,
            color = AlkilColores.Texto,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Tipo de usuario (linea 1)
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(AlkilColores.Primario, Color(0xFF3A4A63))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = ui.rol,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Verificado (linea 2)
        if (ui.verificado) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AlkilColores.Verde, Color(0xFF34B07E))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_verificado_escudo),
                    contentDescription = stringResource(R.string.chat_identidad_verificada),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = ui.textoVerificado,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Rating
        ui.ratingTexto?.let { rating ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = AlkilColores.CoralTexto,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = rating,
                    color = AlkilColores.CoralTexto,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Miembro desde
        ui.miembroDesde?.let { desde ->
            Text(
                text = desde,
                color = AlkilColores.TextoSuave,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        // Boton reseña (solo si aplica)
        if (ui.puedeResenar) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF9A6B00), Color(0xFFC9992E))
                        )
                    )
                    .clickable(onClick = onResena)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.resena_btn),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TextoVacio(texto: String) {
    Text(
        text = texto,
        color = AlkilColores.TextoSuave,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    )
}

/** Tarjeta de un inmueble publicado (con acciones del dueño si es suyo). */
@Composable
private fun TarjetaInmueble(
    p: Propiedad,
    esDueno: Boolean,
    onClick: () -> Unit,
    onFinalizar: () -> Unit,
    onPausar: () -> Unit,
    onReactivar: () -> Unit,
    onDestacar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(InmuebleGradiente))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(
            text = p.titulo,
            color = AlkilColores.Texto,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = p.precioFormateado,
                color = AlkilColores.Primario,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            ChipEstado(p.estadoNormalizado)
        }

        // Direccion (box informativo)
        val direccionCompleta = listOf(p.direccion, p.barrio, p.ciudad)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        if (direccionCompleta.isNotBlank()) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = null,
                    tint = AlkilColores.TextoSuave,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = direccionCompleta,
                    color = AlkilColores.TextoSuave,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }

        val estadoNorm = p.estadoNormalizado
        val esDisponible = estadoNorm == "disponible"
        val esPausada = estadoNorm == "pausada"
        if (esDueno && (esDisponible || esPausada) && p.id.isNotBlank()) {
            BotonAccion(
                texto = stringResource(if (p.esVenta) R.string.prop_finalizar_btn_venta else R.string.prop_finalizar_btn),
                fondo = Brush.horizontalGradient(listOf(AlkilColores.AcentoDeep, AlkilColores.Acento)),
                onClick = onFinalizar,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (esDisponible) {
                BotonAccion(
                    texto = stringResource(R.string.perfil_pausar_publicacion),
                    fondo = Brush.horizontalGradient(listOf(AlkilColores.Primario, Color(0xFF3A4A63))),
                    onClick = onPausar,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (esPausada) {
                BotonAccion(
                    texto = stringResource(R.string.perfil_reactivar_publicacion),
                    fondo = Brush.horizontalGradient(listOf(AlkilColores.Verde, Color(0xFF34B07E))),
                    onClick = onReactivar,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            BotonAccion(
                texto = stringResource(R.string.perfil_destacar_publicacion),
                fondo = Brush.horizontalGradient(listOf(Color(0xFF9A6B00), Color(0xFFC9992E))),
                onClick = onDestacar,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ChipEstado(estado: String) {
    val texto: String
    val fondo: Brush
    when (estado) {
        "disponible" -> {
            texto = stringResource(R.string.prop_estado_disponible)
            fondo = Brush.horizontalGradient(listOf(AlkilColores.Verde, Color(0xFF34B07E)))
        }
        "finalizado" -> {
            texto = stringResource(R.string.prop_estado_finalizado)
            fondo = Brush.horizontalGradient(listOf(Color(0xFF475569), Color(0xFF7C8CA1)))
        }
        else -> {
            texto = stringResource(R.string.prop_estado_revision)
            fondo = Brush.horizontalGradient(listOf(Color(0xFF9A6B00), Color(0xFFC9992E)))
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fondo)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text = texto, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TarjetaResena(r: ResenaUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(ReviewGradiente))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row {
                repeat(5) { i ->
                    Icon(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        tint = if (i < r.rating) AlkilColores.Dorado else AlkilColores.Borde,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.perfil_review_autor, r.autor),
                color = AlkilColores.TextoSuave,
                fontSize = 12.sp
            )
        }
        if (r.comentario.isNotBlank()) {
            Text(
                text = r.comentario,
                color = AlkilColores.Texto,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Boton de accion del dueño con degradado y texto blanco. */
@Composable
private fun BotonAccion(
    texto: String,
    fondo: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = texto, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
