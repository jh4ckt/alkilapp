package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/**
 * Formulario de Soporte e Incidentes en Compose. Campos estandarizados
 * (email deshabilitado, asunto, mensaje multilinea) + CTA coral con estado de
 * carga. Reemplaza activity_soporte.xml.
 */
@Composable
fun PantallaSoporte(
    email: String,
    asunto: String,
    mensaje: String,
    enviando: Boolean,
    onAsunto: (String) -> Unit,
    onMensaje: (String) -> Unit,
    onEnviar: () -> Unit,
    onAtras: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        Cabecera(titulo = stringResource(R.string.soporte_titulo), onAtras = onAtras)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            CardFormulario {
                CampoTexto(
                    valor = email,
                    onCambio = {},
                    hint = stringResource(R.string.soporte_email),
                    habilitado = false,
                    inputType = KeyboardType.Email
                )

                Spacer(Modifier.height(14.dp))

                CampoTexto(
                    valor = asunto,
                    onCambio = onAsunto,
                    hint = stringResource(R.string.soporte_asunto)
                )

                Spacer(Modifier.height(14.dp))

                CampoTextoMultilinea(
                    valor = mensaje,
                    onCambio = onMensaje,
                    hint = stringResource(R.string.soporte_mensaje)
                )
            }

            Spacer(Modifier.height(18.dp))

            if (enviando) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AlkilColores.Acento,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            BotonCta(
                texto = stringResource(
                    if (enviando) R.string.soporte_enviando else R.string.soporte_enviar
                ),
                onClick = { if (!enviando) onEnviar() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Text(
                text = stringResource(R.string.soporte_info),
                color = AlkilColores.TextoMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }
    }
}

/** Tarjeta contenedora de formulario (blanca, borde sutil, radio 18).
 *  Usa Column para apilar los campos verticalmente (no Box, que los superpondría). */
@Composable
fun CardFormulario(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AlkilColores.Tarjeta)
            .padding(18.dp),
        content = content
    )
}
