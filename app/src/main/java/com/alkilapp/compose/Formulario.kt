package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/**
 * Componentes de formulario y cabecera del design system AlkilApp (Compose).
 * La cabecera se reutiliza en todas las pantallas con "atras"; los campos
 * estandarizan el focus coral, el texto navy y los bordes redondeados para que
 * Soporte/Perfil/Auth compartan un look coherente.
 */

/** Cabecera estandar: boton atras + titulo, con statusBarsPadding. */
@Composable
fun Cabecera(
    titulo: String,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = AlkilColores.Tarjeta, shadowElevation = 2.dp, modifier = modifier) {
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
            Text(
                text = titulo,
                color = AlkilColores.Texto,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Etiqueta de campo en el tono muted del brand. */
@Composable
fun EtiquetaCampo(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        color = AlkilColores.TextoMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** Campo de texto estandarizado (focus coral, texto navy, borde redondeado). */
@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    inputType: KeyboardType = KeyboardType.Text,
    habilitado: Boolean = true,
    esPassword: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(hint) },
        enabled = habilitado,
        singleLine = true,
        visualTransformation = if (esPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = inputType),
        shape = RoundedCornerShape(14.dp),
        colors = coloresCampo(),
        modifier = modifier.fillMaxWidth()
    )
}

/** Campo multilinea (mensaje) que crece con contenido minimo de lineas. */
@Composable
fun CampoTextoMultilinea(
    valor: String,
    onCambio: (String) -> Unit,
    hint: String,
    minLineas: Int = 5,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(hint) },
        singleLine = false,
        minLines = minLineas,
        shape = RoundedCornerShape(14.dp),
        colors = coloresCampo(),
        modifier = modifier.fillMaxWidth()
    )
}

/** Selector desplegable (reemplaza al Spinner) con el look del brand.
 *  Muestra el valor actual y abre un menu al tocar. */
@Composable
fun CampoSelector(
    valor: String,
    opciones: List<String>,
    onSeleccionar: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    var abierto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            label = { Text(hint) },
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = if (habilitado) AlkilColores.TextoSuave else AlkilColores.Borde
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = coloresCampo(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = habilitado) { abierto = true }
        )
        androidx.compose.material3.DropdownMenu(
            expanded = abierto,
            onDismissRequest = { abierto = false }
        ) {
            opciones.forEach { opcion ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opcion, color = AlkilColores.Texto) },
                    onClick = {
                        onSeleccionar(opcion)
                        abierto = false
                    }
                )
            }
        }
    }
}

/** Paleta de colores compartida de los campos (focus coral del brand).
 *  Incluye estados disabled con contraste pleno para que el texto siempre se lea. */
@Composable
private fun coloresCampo() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AlkilColores.Acento,
    focusedLabelColor = AlkilColores.Acento,
    focusedPlaceholderColor = AlkilColores.TextoMuted,
    cursorColor = AlkilColores.Acento,
    unfocusedBorderColor = AlkilColores.Borde,
    unfocusedLabelColor = AlkilColores.TextoSuave,
    unfocusedPlaceholderColor = AlkilColores.TextoMuted,
    focusedTextColor = AlkilColores.Texto,
    unfocusedTextColor = AlkilColores.Texto,
    disabledTextColor = AlkilColores.Texto,
    disabledLabelColor = AlkilColores.TextoSuave,
    disabledBorderColor = AlkilColores.Borde,
    disabledSupportingTextColor = AlkilColores.TextoSuave,
    focusedContainerColor = AlkilColores.Tarjeta,
    unfocusedContainerColor = AlkilColores.Tarjeta,
    disabledContainerColor = AlkilColores.GrisSuave
)

/** Fila de dos botones (primario coral + secundario outline) reutilizable. */
@Composable
fun FilaBotones(
    textoPrimario: String,
    onPrimario: () -> Unit,
    textoSecundario: String? = null,
    onSecundario: (() -> Unit)? = null,
    secundarioVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        BotonCta(
            texto = textoPrimario,
            onClick = onPrimario,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        )
        if (textoSecundario != null && onSecundario != null && secundarioVisible) {
            Spacer(Modifier.width(12.dp))
            BotonOutline(
                texto = textoSecundario,
                onClick = onSecundario,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
        }
    }
}

/** Boton outline compartido (contorno neutro) para acciones secundarias. */
@Composable
fun BotonOutline(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AlkilColores.Texto
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.04f))
            .border(1.2.dp, AlkilColores.Borde, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
