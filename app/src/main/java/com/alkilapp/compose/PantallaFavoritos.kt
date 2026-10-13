package com.alkilapp.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R
import com.alkilapp.data.Propiedad

/**
 * Pantalla "Favoritos" en Compose: header (atras + corazon + titulo + refresh
 * girando + contador pill) y la lista reusando ListaInmuebles/PropertyCard.
 * El vacio conserva el copy y la alineacion top de la version XML.
 */
@Composable
fun PantallaFavoritos(
    cargando: Boolean,
    sincronizando: Boolean,
    items: List<ItemLista>,
    onAtras: () -> Unit,
    onActualizar: () -> Unit,
    onItem: (Propiedad) -> Unit,
    onQuitarFavorito: (Propiedad) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        Surface(
            color = AlkilColores.Tarjeta,
            shadowElevation = 2.dp
        ) {
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
                Icon(
                    painter = painterResource(R.drawable.ic_corazon_lleno),
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier
                        .size(22.dp)
                        .padding(start = 8.dp)
                )
                Text(
                    text = stringResource(R.string.favoritos_titulo),
                    color = AlkilColores.Texto,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                val rotacion = rememberInfiniteTransition(label = "fav-spin").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing)
                    ),
                    label = "fav-spin-angle"
                ).value
                IconButton(onClick = onActualizar, enabled = !sincronizando) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.favoritos_actualizar),
                        tint = AlkilColores.TextoSuave,
                        modifier = Modifier.rotate(if (sincronizando) rotacion else 0f)
                    )
                }
                if (items.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(width = 20.dp, height = 20.dp)
                            .background(AlkilColores.Acento, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = items.size.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        when {
            // La lista ya cargada se queda visible mientras se recarga.
            items.isNotEmpty() -> ListaInmuebles(
                items = items,
                mostrarVacio = false,
                onItem = onItem,
                onFavorito = onQuitarFavorito
            )

            cargando -> Text(
                text = stringResource(R.string.favoritos_cargando),
                color = AlkilColores.TextoSuave,
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp)
            )

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AlkilColores.Fondo),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(AlkilColores.CoralSuave),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_corazon),
                            contentDescription = null,
                            tint = AlkilColores.Acento,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.favoritos_vacio),
                        color = AlkilColores.TextoSuave,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
        }
    }
}
