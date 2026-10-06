package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R
import com.alkilapp.data.Propiedad

/**
 * Listado de inmuebles del bottom sheet en Compose (LazyColumn): reemplaza a
 * rvDepartamentos + llSheetVacio. El estado vacio de zona vive aqui mismo.
 */
@Composable
fun ListaInmuebles(
    items: List<ItemLista>,
    mostrarVacio: Boolean,
    onItem: (Propiedad) -> Unit,
    onFavorito: (Propiedad) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mostrarVacio) {
        EstadoVacio(modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AlkilColores.Tarjeta),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
    ) {
        items(items, key = { it.propiedad.id }) { item ->
            PropertyCard(
                item = item,
                onClick = { onItem(item.propiedad) },
                onFavorito = { onFavorito(item.propiedad) }
            )
        }
    }
}

/** Estado vacio: la zona del usuario no tiene inmuebles y no hay filtros activos. */
@Composable
private fun EstadoVacio(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlkilColores.Tarjeta),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sheet_zona_vacio_titulo),
                color = AlkilColores.Texto,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.sheet_zona_vacio_mensaje),
                color = AlkilColores.TextoSuave,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
