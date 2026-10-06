package com.alkilapp.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.alkilapp.data.Propiedad
import com.alkilapp.ui.Thumbs
import com.alkilapp.ui.tipoClave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Fila de la lista Compose: propiedad + estado de chips que dependen de ella
 *  (verificado/favorito/distancia) para forzar recomposición al cambiar. */
data class ItemLista(
    val propiedad: Propiedad,
    val verificado: Boolean,
    val favorito: Boolean,
    val distanciaKm: Double?
)

/**
 * Property Card en Compose: misma estructura que item_propiedad.xml
 * (media 150dp + insignias / titulo+direccion / chip verificado / specs /
 * divisor / precio + CTA) con las mejoras del design system: fondo de media
 * con gradiente suave, elevacion real y CTA coral con gradiente.
 */
@Composable
fun PropertyCard(
    item: ItemLista,
    onClick: () -> Unit,
    onFavorito: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = item.propiedad

    val destacado = p.esDestacado
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AlkilColores.Tarjeta),
        border = androidx.compose.foundation.BorderStroke(
            width = if (destacado) 2.dp else 1.dp,
            color = if (destacado) AlkilColores.Dorado else AlkilColores.Borde
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {

            // ===== Media =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(AlkilColores.PrimarioSuave, AlkilColores.Calido)
                        )
                    )
            ) {
                FotoThumb(p.fotos.firstOrNull())

                // Insignia de estado SOLO cuando hay un estado especial; para
                // "Disponible" manda la insignia de TIPO en la misma posicion.
                val estado = p.estadoNormalizado
                val textoEstado = when (estado) {
                    "disponible" -> stringResource(R.string.prop_estado_disponible)
                    "finalizado" -> stringResource(R.string.prop_estado_finalizado)
                    "under_review" -> stringResource(R.string.prop_estado_revision)
                    else -> ""
                }
                val mostrarEstado = textoEstado.isNotEmpty() && estado != "disponible"
                if (mostrarEstado) {
                    Insignia(
                        texto = textoEstado,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        fondo = AlkilColores.TextoMuted,
                        colorTexto = Color.White
                    )
                } else {
                    val tipoTexto = when (tipoClave(p.tipo)) {
                        "habitacion" -> "Habitación"
                        "departamento" -> "Departamento"
                        "casa" -> "Casa"
                        else -> "Otros"
                    }
                    Insignia(
                        texto = tipoTexto,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        fondo = AlkilColores.Tarjeta,
                        colorTexto = AlkilColores.Navy,
                        borde = AlkilColores.Borde
                    )
                }

                // Favorito (corazon). El listado no toca Firestore: pide
                // alternar al contexto (MainActivity) que persiste local + nube.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .shadow(2.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(AlkilColores.Tarjeta)
                        .clickable(onClick = onFavorito)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (item.favorito) R.drawable.ic_corazon_lleno else R.drawable.ic_corazon
                        ),
                        contentDescription = stringResource(R.string.prop_favorito_cd),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Etiqueta "Destacado"
                if (destacado) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(AlkilColores.Dorado)
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            tint = AlkilColores.Primario,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.destacado_etiqueta),
                            color = AlkilColores.Primario,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ===== Contenido =====
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = p.titulo,
                    color = AlkilColores.Texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = p.direccion.ifBlank {
                        p.tipo.replaceFirstChar { it.uppercase() } + " en " + p.barrio
                    },
                    color = AlkilColores.TextoMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Chip verificado: propietario con badge de confianza.
                if (item.verificado) {
                    Row(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(AlkilColores.VerdeSuave)
                            .padding(start = 8.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_verificado_escudo),
                            contentDescription = stringResource(R.string.verificado_chip_cd),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.verificado_chip),
                            color = AlkilColores.Verde,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Detalles: habitaciones / m2 / distancia
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (p.ambientes > 0) {
                        PillSpec(
                            icono = R.drawable.ic_cama,
                            cd = stringResource(R.string.prop_ambientes_cd),
                            texto = stringResource(R.string.prop_ambientes, p.ambientes)
                        )
                    }
                    if (p.superficieM2 > 0) {
                        Spacer(Modifier.width(6.dp))
                        PillSpec(
                            icono = R.drawable.ic_area,
                            cd = stringResource(R.string.prop_superficie_cd),
                            texto = stringResource(
                                R.string.prop_superficie,
                                String.format(Locale.US, "%.0f", p.superficieM2)
                            )
                        )
                    }
                    Text(
                        text = distanciaTexto(item.distanciaKm),
                        color = AlkilColores.TextoMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(1.dp)
                        .background(AlkilColores.Borde)
                )

                // Precio + CTA
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = p.precioFormateado,
                        color = AlkilColores.Navy,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    BotonCta(
                        texto = stringResource(R.string.prop_ver_detalles),
                        onClick = onClick
                    )
                }
            }
        }
    }
}

@Composable
private fun distanciaTexto(distanciaKm: Double?): String = when {
    distanciaKm == null -> ""
    distanciaKm < 1.0 -> stringResource(
        R.string.prop_distancia_m,
        (distanciaKm * 1000).roundToInt().toString()
    )
    else -> stringResource(
        R.string.prop_distancia_km,
        String.format("%.1f", distanciaKm)
    )
}

/** Miniatura de la foto (base64 -> Bitmap en IO, cacheada por Thumbs). */
@Composable
private fun FotoThumb(foto: String?) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = foto) {
        value = if (foto == null) null else withContext(Dispatchers.IO) {
            Thumbs.decodificar(foto)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stringResource(R.string.prop_foto_thumb_cd),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/** Pill de badge (tipo / estado) sobre la media. */
@Composable
private fun Insignia(
    texto: String,
    fondo: Color,
    colorTexto: Color,
    modifier: Modifier = Modifier,
    borde: Color? = null
) {
    val forma = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(forma)
            .background(fondo)
            .then(
                if (borde != null) Modifier.border(1.dp, borde, forma) else Modifier
            )
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(
            text = texto,
            color = colorTexto,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Chip suave de spec (habitaciones / superficie) de la tarjeta. */
@Composable
private fun PillSpec(icono: Int, texto: String, cd: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(AlkilColores.GrisSuave)
            .padding(start = 8.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icono),
            contentDescription = cd,
            tint = Color.Unspecified,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = texto,
            color = AlkilColores.TextoMuted,
            fontSize = 11.5.sp
        )
    }
}
