package com.alkilapp.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/** Estado visible del formulario de verificacion de identidad. */
data class VerifUiState(
    val tipo: String = "DNI",
    val numero: String = "",
    val estadoTexto: String? = null,
    val fotoFrente: Bitmap? = null,
    val fotoReverso: Bitmap? = null,
    val camposHabilitados: Boolean = true,
    val fotosHabilitadas: Boolean = true,
    val enviarHabilitado: Boolean = true,
    val textoBoton: String = ""
)

/**
 * Verificacion de identidad en Compose: selector de tipo de documento,
 * numero (con filtro DNI 8 digitos en la Activity), foto del frente y del
 * reverso, y boton de envio. Banner de estado cuando ya esta verificado /
 * en revision / rechazado. Reemplaza activity_verificacion.xml.
 */
@Composable
fun PantallaVerificacion(
    ui: VerifUiState,
    tipos: List<String>,
    onTipo: (String) -> Unit,
    onNumero: (String) -> Unit,
    onElegirFrente: () -> Unit,
    onElegirReverso: () -> Unit,
    onEnviar: () -> Unit,
    onAtras: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        Cabecera(titulo = stringResource(R.string.verif_titulo), onAtras = onAtras)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.verif_subtitulo),
                color = AlkilColores.TextoMuted,
                fontSize = 14.sp
            )

            // Banner de estado
            ui.estadoTexto?.let { estado ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AlkilColores.PrimarioSuave)
                        .padding(14.dp)
                ) {
                    Text(text = estado, color = AlkilColores.Primario, fontSize = 13.sp)
                }
            }

            // Tipo de documento
            Text(
                text = stringResource(R.string.verif_tipo_documento),
                color = AlkilColores.Texto,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            CampoSelector(
                valor = ui.tipo,
                opciones = tipos,
                onSeleccionar = onTipo,
                hint = stringResource(R.string.verif_tipo_documento),
                habilitado = ui.camposHabilitados,
                modifier = Modifier.padding(top = 6.dp)
            )

            // Numero de documento
            CampoTexto(
                valor = ui.numero,
                onCambio = onNumero,
                hint = stringResource(R.string.verif_numero),
                habilitado = ui.camposHabilitados,
                modifier = Modifier.padding(top = 14.dp)
            )

            // Foto frente
            Text(
                text = stringResource(R.string.verif_foto_label),
                color = AlkilColores.Texto,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            if (ui.fotoFrente != null) {
                Image(
                    bitmap = ui.fotoFrente!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.verif_foto_label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
            BotonOutline(
                texto = stringResource(R.string.verif_foto_elegir),
                onClick = onElegirFrente,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(48.dp)
                    .alpha(if (ui.fotosHabilitadas) 1f else 0.5f)
            )

            // Foto reverso
            Text(
                text = stringResource(R.string.verif_foto_reverso_label),
                color = AlkilColores.Texto,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (ui.fotoReverso != null) {
                Image(
                    bitmap = ui.fotoReverso!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.verif_foto_reverso_label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
            BotonOutline(
                texto = stringResource(R.string.verif_foto_reverso_elegir),
                onClick = onElegirReverso,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(48.dp)
                    .alpha(if (ui.fotosHabilitadas) 1f else 0.5f)
            )

            // Enviar
            BotonCta(
                texto = ui.textoBoton.ifBlank { stringResource(R.string.verif_enviar) },
                onClick = onEnviar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(50.dp)
                    .alpha(if (ui.enviarHabilitado) 1f else 0.5f)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
