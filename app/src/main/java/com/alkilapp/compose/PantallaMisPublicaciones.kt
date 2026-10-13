package com.alkilapp.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R
import com.alkilapp.data.Propiedad
import com.alkilapp.ui.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Filtros de estado de "Mis publicaciones" (chips con conteo). */
enum class FiltroPublicaciones { TODAS, DISPONIBLES, FINALIZADAS, PAUSADAS, REVISION }

/**
 * Pantalla "Mis publicaciones" en Compose: header + stats + chips de filtro +
 * tarjetas con chip de estado, estrella de destacado, menu ⋮ y botones
 * Destacar/Editar. Reemplaza a activity_mis_publicaciones.xml y el
 * construirInmueble() imperativo.
 */
@Composable
fun PantallaMisPublicaciones(
    cuenta: String,
    inmuebles: List<Propiedad>,
    cargando: Boolean,
    errorTexto: String?,
    filtroActual: FiltroPublicaciones,
    onFiltro: (FiltroPublicaciones) -> Unit,
    onAtras: () -> Unit,
    onAbrirDetalle: (Propiedad) -> Unit,
    onEditar: (Propiedad) -> Unit,
    onToggleDestacado: (Propiedad) -> Unit,
    onPausar: (Propiedad) -> Unit,
    onReactivar: (Propiedad) -> Unit,
    onMarcarAlquilado: (Propiedad) -> Unit,
    onEliminar: (Propiedad) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        // ===== Header =====
        Surface(color = AlkilColores.Tarjeta, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAtras) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.chat_atras),
                        tint = AlkilColores.Texto
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.titulo_mis_publicaciones),
                        color = AlkilColores.Texto,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = cuenta,
                        color = AlkilColores.TextoSuave,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // ===== Stats =====
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlkilColores.Borde),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mis_pub_total_label),
                        color = AlkilColores.TextoSuave,
                        fontSize = 12.sp
                    )
                    Text(
                        text = inmuebles.size.toString(),
                        color = AlkilColores.Primario,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(AlkilColores.Borde)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.mis_pub_disponibles_label),
                        color = AlkilColores.TextoSuave,
                        fontSize = 12.sp
                    )
                    Text(
                        text = inmuebles.count { it.estadoNormalizado == "disponible" }.toString(),
                        color = AlkilColores.Verde,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ===== Chips de filtro =====
        val opciones = listOf(
            Triple(FiltroPublicaciones.TODAS, R.string.mis_pub_filtro_todas, inmuebles.size),
            Triple(
                FiltroPublicaciones.DISPONIBLES, R.string.mis_pub_filtro_disponibles,
                inmuebles.count { it.estadoNormalizado == "disponible" }
            ),
            Triple(
                FiltroPublicaciones.FINALIZADAS, R.string.mis_pub_filtro_finalizadas,
                inmuebles.count { it.estadoNormalizado == "finalizado" }
            ),
            Triple(
                FiltroPublicaciones.PAUSADAS, R.string.mis_pub_filtro_pausadas,
                inmuebles.count { it.estadoNormalizado == "pausada" }
            ),
            Triple(
                FiltroPublicaciones.REVISION, R.string.mis_pub_filtro_revision,
                inmuebles.count {
                    it.estadoNormalizado !in setOf("disponible", "finalizado", "pausada")
                }
            )
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp)
        ) {
            items(opciones, key = { it.first }) { (filtro, titulo, cantidad) ->
                ChipFiltro(
                    texto = "${stringResource(titulo)}  $cantidad",
                    activo = filtro == filtroActual,
                    onClick = { onFiltro(filtro) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // ===== Contenido =====
        val visibles = inmuebles.filter { propiedad ->
            when (filtroActual) {
                FiltroPublicaciones.TODAS -> true
                FiltroPublicaciones.DISPONIBLES -> propiedad.estadoNormalizado == "disponible"
                FiltroPublicaciones.FINALIZADAS -> propiedad.estadoNormalizado == "finalizado"
                FiltroPublicaciones.PAUSADAS -> propiedad.estadoNormalizado == "pausada"
                FiltroPublicaciones.REVISION -> propiedad.estadoNormalizado !in
                    setOf("disponible", "finalizado", "pausada")
            }
        }
        when {
            errorTexto != null -> Text(
                text = errorTexto,
                color = AlkilColores.TextoSuave,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            cargando -> Text(
                text = stringResource(R.string.mis_pub_cargando),
                color = AlkilColores.TextoSuave,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            visibles.isEmpty() -> Text(
                text = stringResource(
                    if (inmuebles.isEmpty()) R.string.mis_pub_vacio
                    else R.string.mis_pub_filtro_sin_resultados
                ),
                color = AlkilColores.TextoSuave,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 32.dp, end = 32.dp)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(visibles, key = { it.id }) { propiedad ->
                    MisPubCard(
                        p = propiedad,
                        onClick = { onAbrirDetalle(propiedad) },
                        onEditar = { onEditar(propiedad) },
                        onToggleDestacado = { onToggleDestacado(propiedad) },
                        onPausar = { onPausar(propiedad) },
                        onReactivar = { onReactivar(propiedad) },
                        onMarcarAlquilado = { onMarcarAlquilado(propiedad) },
                        onEliminar = { onEliminar(propiedad) }
                    )
                }
            }
        }
    }
}

/** Chip de filtro con conteo: activo = navy solido, normal = blanco con borde. */
@Composable
private fun ChipFiltro(texto: String, activo: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(forma)
            .background(if (activo) AlkilColores.Primario else AlkilColores.Tarjeta)
            .border(
                width = 1.dp,
                color = if (activo) AlkilColores.Primario else AlkilColores.Borde,
                shape = forma
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = texto,
            color = if (activo) Color.White else AlkilColores.TextoSuave,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Tarjeta de una publicacion propia: foto + chip estado + ⋮ + acciones. */
@Composable
private fun MisPubCard(
    p: Propiedad,
    onClick: () -> Unit,
    onEditar: () -> Unit,
    onToggleDestacado: () -> Unit,
    onPausar: () -> Unit,
    onReactivar: () -> Unit,
    onMarcarAlquilado: () -> Unit,
    onEliminar: () -> Unit
) {
    var menuAbierto by remember { mutableStateOf(false) }
    val estadoNorm = p.estadoNormalizado
    val permiteDestacar = estadoNorm != "finalizado"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
        border = androidx.compose.foundation.BorderStroke(1.dp, AlkilColores.Borde),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Foto con chip de estado y estrella de destacado
                Box(
                    Modifier
                        .size(width = 112.dp, height = 116.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(AlkilColores.PrimarioSuave)
                ) {
                    FotoMisPub(p)
                    EstadoChip(
                        p = p,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                    )
                    if (p.esDestacado) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(7.dp)
                                .size(25.dp)
                                .background(AlkilColores.Dorado, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("★", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, top = 1.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tipoMostrable(p.tipo).uppercase(),
                            color = AlkilColores.CoralTexto,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            IconButton(
                                onClick = { menuAbierto = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.mis_pub_mas_opciones),
                                    tint = AlkilColores.TextoSuave
                                )
                            }
                            DropdownMenu(
                                expanded = menuAbierto,
                                onDismissRequest = { menuAbierto = false }
                            ) {
                                if (estadoNorm == "disponible") {
                                    OpcionMenu(R.drawable.ic_pausa, R.string.mis_pub_menu_pausar) {
                                        menuAbierto = false; onPausar()
                                    }
                                }
                                if (estadoNorm == "pausada") {
                                    OpcionMenu(R.drawable.ic_play, R.string.mis_pub_menu_reactivar) {
                                        menuAbierto = false; onReactivar()
                                    }
                                }
                                if (permiteDestacar) {
                                    OpcionMenu(
                                        R.drawable.ic_star,
                                        if (p.esDestacado) R.string.mis_pub_menu_quitar_destacado
                                        else R.string.mis_pub_menu_destacar
                                    ) {
                                        menuAbierto = false; onToggleDestacado()
                                    }
                                }
                                if (estadoNorm == "disponible" || estadoNorm == "pausada") {
                                    OpcionMenu(
                                        R.drawable.ic_check,
                                        if (p.esVenta) R.string.prop_marcar_vendido
                                        else R.string.prop_marcar_alquilado
                                    ) {
                                        menuAbierto = false; onMarcarAlquilado()
                                    }
                                }
                                OpcionMenu(R.drawable.ic_basurero, R.string.mis_pub_menu_eliminar) {
                                    menuAbierto = false; onEliminar()
                                }
                            }
                        }
                    }
                    Text(
                        text = p.titulo,
                        color = AlkilColores.Texto,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "⌖  ${p.barrio.ifEmpty { p.ciudad }}",
                        color = AlkilColores.TextoSuave,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = p.precioFormateado,
                        color = AlkilColores.Primario,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 9.dp)
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(1.dp)
                    .background(AlkilColores.Borde)
            )

            Row(
                modifier = Modifier.padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (permiteDestacar) {
                    BotonAccion(
                        texto = stringResource(
                            if (p.esDestacado) R.string.mis_pub_destacada else R.string.mis_pub_destacar
                        ),
                        fondo = if (p.esDestacado) AlkilColores.Dorado else AlkilColores.DoradoSuave,
                        colorTexto = if (p.esDestacado) Color.White else AlkilColores.DoradoTexto,
                        icono = R.drawable.ic_star,
                        onClick = onToggleDestacado,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    )
                }
                BotonAccion(
                    texto = stringResource(R.string.mis_pub_editar),
                    fondo = AlkilColores.CoralSuave,
                    colorTexto = AlkilColores.CoralTexto,
                    icono = R.drawable.ic_editar,
                    onClick = onEditar,
                    modifier = Modifier
                        .weight(if (permiteDestacar) 1f else 2f)
                        .then(
                            if (permiteDestacar) Modifier.padding(start = 6.dp) else Modifier
                        )
                )
            }
        }
    }
}

@Composable
private fun OpcionMenu(icono: Int, texto: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(texto)) },
        leadingIcon = {
            Icon(
                painter = painterResource(icono),
                contentDescription = null,
                tint = AlkilColores.TextoSuave
            )
        },
        onClick = onClick
    )
}

/** Chip de estado (disponible/finalizado/pausada/en revision). */
@Composable
private fun EstadoChip(p: Propiedad, modifier: Modifier = Modifier) {
    val (etiqueta, colorText, colorBg) = when (p.estadoNormalizado) {
        "disponible" -> Triple(
            stringResource(R.string.prop_estado_disponible),
            AlkilColores.Verde,
            AlkilColores.VerdeSuave
        )

        "finalizado" -> Triple(
            stringResource(R.string.prop_estado_finalizado),
            AlkilColores.TextoSuave,
            AlkilColores.GrisSuave
        )

        "pausada" -> Triple(
            stringResource(R.string.prop_estado_pausada),
            AlkilColores.TextoSuave,
            AlkilColores.GrisSuave
        )

        else -> Triple(
            stringResource(R.string.prop_estado_revision),
            AlkilColores.DoradoTexto,
            AlkilColores.DoradoSuave
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colorBg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = etiqueta,
            color = colorText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Boton de accion de la tarjeta (icono + texto, fondo suave). */
@Composable
private fun BotonAccion(
    texto: String,
    fondo: Color,
    colorTexto: Color,
    icono: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icono),
            contentDescription = null,
            tint = colorTexto,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = texto,
            color = colorTexto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun tipoMostrable(tipo: String): String = when (tipo.trim().lowercase()) {
    "habitacion", "habitación", "cuarto" -> "Habitacion"
    "departamento", "depto" -> "Departamento"
    "casa" -> "Casa"
    else -> "Otros"
}

/** Miniatura de la foto: base64 (cache Thumbs) o URL remota con decode simple. */
@Composable
private fun FotoMisPub(p: Propiedad) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = p.id) {
        value = withContext(Dispatchers.IO) {
            val base64 = p.fotos.firstOrNull()
            val url = p.photosUrl.firstOrNull()
            when {
                !base64.isNullOrBlank() -> Thumbs.decodificar(base64)
                !url.isNullOrBlank() -> descargarMiniatura(url)
                else -> null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stringResource(R.string.mis_pub_foto_cd, p.titulo),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

private fun descargarMiniatura(url: String): Bitmap? {
    var connection: HttpURLConnection? = null
    return try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { stream ->
            val bytes = stream.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return@use null
            var sample = 1
            val objetivo = 420
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= objetivo) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

/** Hoja con las 3 opciones de destacado (7/15/30 dias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaOpcionesDestacar(
    onOpcion: (dias: Int, precio: Double) -> Unit,
    onCerrar: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = sheetState,
        containerColor = AlkilColores.Tarjeta
    ) {
        listOf(7 to 6.90, 15 to 12.90, 30 to 24.90).forEach { (dias, precio) ->
            Text(
                text = stringResource(R.string.mis_pub_opcion_destacar, dias, precio.toString()),
                color = AlkilColores.Texto,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpcion(dias, precio) }
                    .padding(16.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
