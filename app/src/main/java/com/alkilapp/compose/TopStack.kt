package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/**
 * Stack superior de la pantalla principal en Compose: fila de menu + busqueda
 * + boton publicar, chips rapidos de tipo y pill de contador. Reemplaza a
 * filaTop/scrollChipsTipo/tvContadorMapa de activity_main.xml.
 *
 * @param onAltoFila avisa la altura de la fila de busqueda (el mapa la usa
 *  como topInset, igual que antes con filaTop.measuredHeight).
 */
@Composable
fun AlkilTopStack(
    busqueda: String,
    onBusquedaChanged: (String) -> Unit,
    filtroBotonActivo: Boolean,
    filtroTipo: String?,
    onChipTipo: (String?) -> Unit,
    contador: Int,
    onMenu: () -> Unit,
    onFiltros: () -> Unit,
    onPublicar: () -> Unit,
    onAltoFila: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {

        // ===== Fila: menu + busqueda/filtro + publicar =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp)
                .onSizeChanged { onAltoFila(it.height) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Menu (hamburguesa)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AlkilColores.Tarjeta)
                    .border(1.dp, AlkilColores.Borde, RoundedCornerShape(14.dp))
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_hamburger),
                    contentDescription = stringResource(R.string.menu_principal_cd),
                    tint = AlkilColores.Primario,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Barra de busqueda y filtro
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AlkilColores.Tarjeta)
                    .border(1.dp, AlkilColores.Borde, RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_search),
                        contentDescription = null,
                        tint = AlkilColores.Navy,
                        modifier = Modifier.size(24.dp)
                    )
                    BasicTextField(
                        value = busqueda,
                        onValueChange = onBusquedaChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AlkilColores.Navy,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(AlkilColores.Navy),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (busqueda.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        color = AlkilColores.TextoSuave,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onFiltros),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_filter),
                            contentDescription = stringResource(R.string.filtros_titulo),
                            tint = if (filtroBotonActivo) AlkilColores.Primario
                            else AlkilColores.TextoSuave,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Publicar inmueble
            FloatingActionButton(
                onClick = onPublicar,
                modifier = Modifier.padding(start = 8.dp),
                containerColor = AlkilColores.Primario,
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.prop_agregar_titulo),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        // ===== Chips rapidos de tipo =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, end = 52.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipTipo(
                texto = stringResource(R.string.chips_tipo_todo),
                seleccionado = filtroTipo == null,
                onClick = { onChipTipo(null) }
            )
            ChipTipo(
                texto = "Departamento",
                seleccionado = filtroTipo == "departamento",
                onClick = { onChipTipo("departamento") }
            )
            ChipTipo(
                texto = "Casa",
                seleccionado = filtroTipo == "casa",
                onClick = { onChipTipo("casa") }
            )
            ChipTipo(
                texto = "Habitación",
                seleccionado = filtroTipo == "habitacion",
                onClick = { onChipTipo("habitacion") }
            )
        }

        // ===== Contador de resultados (invisible con 0, como tvContadorMapa) =====
        if (contador > 0) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PillContador(
                    texto = stringResource(R.string.mapa_contador, contador),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}
