package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componentes base del design system AlkilApp (Compose): los mismos patrones
 * de los drawables XML (bg_chip_tipo, bg_contador, CTA coral) para que toda la
 * app se estandarice sobre estos.
 */

/** Chip rapido de tipo: seleccionado = navy solido, normal = blanco con borde. */
@Composable
fun ChipTipo(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forma = RoundedCornerShape(18.dp)
    val fondo = if (seleccionado) AlkilColores.Navy else AlkilColores.Tarjeta
    val colorTexto = if (seleccionado) Color.White else AlkilColores.Navy
    Box(
        modifier = modifier
            .clip(forma)
            .background(fondo)
            .border(
                width = 1.dp,
                color = if (seleccionado) AlkilColores.Navy else AlkilColores.Borde,
                shape = forma
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = texto,
            color = colorTexto,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Pill navy flotante con el contador de resultados sobre el mapa. */
@Composable
fun PillContador(texto: String, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .shadow(4.dp, forma, clip = false)
            .clip(forma)
            .background(AlkilColores.Navy)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        androidx.compose.material3.Text(
            text = texto,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** CTA coral con gradiente (brand_accent_deep -> brand_accent_dark) y estado
 *  presionado atenuado. Reemplaza a los MaterialButton planos de color solido. */
@Composable
fun BotonCta(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val forma = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(forma)
            .background(
                Brush.horizontalGradient(
                    listOf(AlkilColores.AcentoDeep, AlkilColores.AcentoOscuro)
                )
            )
            .clickable(
                interactionSource = interaccion,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .graphicsLayer { alpha = if (presionado) 0.85f else 1f }
            .heightIn(min = 38.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = texto,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
