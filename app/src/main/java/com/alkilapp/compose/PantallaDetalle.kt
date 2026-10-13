package com.alkilapp.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Todo lo que la ficha necesita para pintarse (alimentado desde la Activity). */
data class DetalleUiState(
    val propId: String = "",
    val titulo: String = "",
    val tipo: String = "",
    val direccion: String = "",
    val precioTexto: String = "",
    val operacion: String = "",
    val descripcion: String = "",
    val comodidades: List<String> = emptyList(),
    val ambientes: Int = 0,
    val superficie: Double = 0.0,
    val estado: String = "disponible",
    val estadoFinalizadoTexto: String? = null,
    val destacado: Boolean = false,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val idPropietario: String = "",
    val esMio: Boolean = false,
    val hayFotos: Boolean = false
)

/** Perfil del propietario (async). */
data class EstadoPropietario(
    val nombre: String = "",
    val inicial: Char = '?',
    val verificado: Boolean = false,
    val rating: Double = 0.0,
    val colorAvatar: Color = AlkilColores.Primario
)

/**
 * Ficha del inmueble en Compose: header (atras/compartir), banners de estado,
 * galeria (preview + tira de thumbs), tarjeta de informacion (tipo/estado/titulo/
 * precio/operacion/direccion/specs), card "Acerca de" (comodidades + descripcion),
 * tarjeta del propietario (async) y la barra inferior de acciones (denunciar/
 * finalizar / ver mapa / chatear). Reemplaza activity_propiedad_detalle.xml.
 */
@Composable
fun PantallaDetalle(
    ui: DetalleUiState,
    propietario: EstadoPropietario?,
    preview: Bitmap?,
    thumbs: List<Bitmap?>,
    indiceActual: Int,
    onAtras: () -> Unit,
    onCompartir: () -> Unit,
    onFotoZoom: (Int) -> Unit,
    onThumb: (Int) -> Unit,
    onVerMapa: () -> Unit,
    onChat: () -> Unit,
    onDenunciar: () -> Unit,
    onFinalizar: () -> Unit,
    onPerfilPropietario: () -> Unit
) {
    val scroll = rememberScrollState()
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
                    .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAtras) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.chat_atras),
                        tint = AlkilColores.Texto
                    )
                }
                Text(
                    text = stringResource(R.string.detalle_titulo),
                    color = AlkilColores.Texto,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                IconButton(onClick = onCompartir) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_share),
                        contentDescription = stringResource(R.string.detalle_compartir),
                        tint = AlkilColores.Texto
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
        ) {
            // ===== Banners de estado =====
            ui.estadoFinalizadoTexto?.let { BannerEstado(it, AlkilColores.CoralSuave, AlkilColores.CoralTexto) }
            if (ui.estado == "under_review") {
                BannerEstado(
                    stringResource(R.string.detalle_revision_banner),
                    AlkilColores.DoradoSuave,
                    AlkilColores.DoradoTexto
                )
            }
            if (ui.estado == "pausada" && ui.estadoFinalizadoTexto == null) {
                BannerEstado(
                    stringResource(R.string.detalle_pausada_banner),
                    AlkilColores.GrisSuave,
                    AlkilColores.TextoSuave
                )
            }

            // ===== Galeria =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.verticalGradient(listOf(AlkilColores.PrimarioSuave, AlkilColores.Calido))
                    )
                    .clickable { onFotoZoom(indiceActual) }
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.prop_foto_thumb_cd),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = AlkilColores.PrimarioSuave,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = stringResource(R.string.detalle_no_fotos),
                            color = AlkilColores.TextoSuave,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                if (ui.destacado) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(AlkilColores.Dorado)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.destacado_etiqueta),
                            color = AlkilColores.Primario,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tira de miniaturas
            val visibles = thumbs.filterNotNull()
            if (visibles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    visibles.forEachIndexed { i, bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onThumb(i) }
                                .border(
                                    width = if (i == indiceActual) 2.dp else 0.dp,
                                    color = AlkilColores.Navy,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentScale = ContentScale.Crop,
                            alpha = if (i == indiceActual) 1f else 0.6f
                        )
                    }
                }
            }

            // ===== Tarjeta de informacion =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ui.tipo,
                            color = AlkilColores.Navy,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        EstadoChipDetalle(ui.estado)
                    }
                    Text(
                        text = ui.titulo,
                        color = AlkilColores.Texto,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // Precio + operacion
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ui.precioTexto,
                            color = AlkilColores.Navy,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(AlkilColores.PrimarioSuave)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = ui.operacion,
                                color = AlkilColores.Primario,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    // Direccion
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location),
                            contentDescription = null,
                            tint = AlkilColores.TextoSuave,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = ui.direccion,
                            color = AlkilColores.TextoSuave,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    // Specs
                    if (ui.ambientes > 0 || ui.superficie > 0) {
                        Row(modifier = Modifier.padding(top = 12.dp)) {
                            if (ui.ambientes > 0) {
                                TileSpec(
                                    icono = R.drawable.ic_cama,
                                    texto = stringResource(R.string.detalle_amb, ui.ambientes),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            if (ui.superficie > 0) {
                                TileSpec(
                                    icono = R.drawable.ic_area,
                                    texto = stringResource(
                                        R.string.detalle_superficie,
                                        ui.superficie.toInt().toString()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ===== Acerca del inmueble =====
            if (ui.comodidades.isNotEmpty() || ui.descripcion.isNotBlank()) {
                Text(
                    text = stringResource(R.string.detalle_acerca_titulo),
                    color = AlkilColores.Texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (ui.comodidades.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                ui.comodidades.forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(AlkilColores.GrisSuave)
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = c,
                                            color = AlkilColores.Texto,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (ui.descripcion.isNotBlank()) {
                            Text(
                                text = ui.descripcion,
                                color = AlkilColores.TextoSuave,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }

            // ===== Tarjeta del propietario =====
            if (propietario != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable(onClick = onPerfilPropietario),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(propietario.colorAvatar, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = propietario.inicial.toString(),
                                color = Color.White,
                                fontSize = 20.sp,
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
                                    text = propietario.nombre,
                                    color = AlkilColores.Texto,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (propietario.verificado) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_verificado_escudo),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .size(14.dp)
                                    )
                                }
                                if (propietario.rating > 0) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f", propietario.rating),
                                        color = AlkilColores.CoralTexto,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            if (propietario.verificado) {
                                Text(
                                    text = stringResource(R.string.chat_verificado_linea),
                                    color = AlkilColores.Verde,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = AlkilColores.TextoSuave,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ===== Acciones secundarias =====
            if (!ui.esMio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    BotonSecundario(
                        texto = stringResource(R.string.denuncia_btn),
                        color = AlkilColores.CoralTexto,
                        onClick = onDenunciar,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (ui.esMio && (ui.estado == "disponible" || ui.estado == "pausada")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    BotonSecundario(
                        texto = stringResource(R.string.detalle_finalizar_btn),
                        color = AlkilColores.CoralTexto,
                        onClick = onFinalizar,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // ===== Barra inferior =====
        // Sombra elevada + borde superior sutil la separan del contenido scrollado.
        Surface(
            shadowElevation = 12.dp,
            color = AlkilColores.Tarjeta,
            border = androidx.compose.foundation.BorderStroke(1.dp, AlkilColores.Borde)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ui.lat != 0.0 || ui.lng != 0.0) {
                    BotonSecundario(
                        texto = stringResource(R.string.detalle_ver_mapa),
                        color = AlkilColores.Primario,
                        onClick = onVerMapa,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!ui.esMio && ui.estado != "finalizado" && ui.estado != "pausada") {
                    // El CTA chatear domina: mas ancho que la accion secundaria.
                    val pesoCta = if (ui.lat != 0.0 || ui.lng != 0.0) 1.35f else 1f
                    BotonCta(
                        texto = stringResource(R.string.detalle_chatear),
                        onClick = onChat,
                        modifier = Modifier.weight(pesoCta)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerEstado(texto: String, fondo: Color, colorTexto: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = texto,
            color = colorTexto,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EstadoChipDetalle(estado: String) {
    val etiqueta: String
    val fondo: Brush
    when (estado) {
        "disponible" -> {
            etiqueta = stringResource(R.string.prop_estado_disponible)
            fondo = Brush.horizontalGradient(listOf(AlkilColores.Verde, Color(0xFF34B07E)))
        }
        "finalizado" -> {
            etiqueta = stringResource(R.string.prop_estado_finalizado)
            fondo = Brush.horizontalGradient(listOf(Color(0xFF475569), Color(0xFF7C8CA1)))
        }
        "pausada" -> {
            etiqueta = stringResource(R.string.prop_estado_pausada)
            fondo = Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFF94A3B8)))
        }
        else -> {
            etiqueta = stringResource(R.string.prop_estado_revision)
            fondo = Brush.horizontalGradient(listOf(Color(0xFF9A6B00), Color(0xFFC9992E)))
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fondo)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = etiqueta, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TileSpec(icono: Int, texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AlkilColores.GrisSuave)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icono),
            contentDescription = null,
            tint = AlkilColores.TextoSuave,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = texto,
            color = AlkilColores.Texto,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun BotonSecundario(texto: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Degradado leve del propio tinte: se lee como accion clara sin competir
    // con el CTA coral.
    val fondo = Brush.horizontalGradient(
        listOf(color.copy(alpha = 0.10f), color.copy(alpha = 0.20f))
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(fondo)
            .border(1.2.dp, color.copy(alpha = 0.55f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = texto, color = color, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Decodifica una foto (base64 o URL) a Bitmap en IO. */
suspend fun cargarFotoDetalle(fuente: String, esBase64: Boolean): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (esBase64) {
                val bytes = Base64.decode(fuente, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
            } else {
                val conexion = URL(fuente).openConnection() as HttpURLConnection
                conexion.connectTimeout = 10000
                conexion.readTimeout = 10000
                val bmp = conexion.inputStream.use { BitmapFactory.decodeStream(it) }
                conexion.disconnect()
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }
