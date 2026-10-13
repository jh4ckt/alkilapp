package com.alkilapp.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/** Estado visible de "Mi perfil". */
data class MiPerfilUiState(
    val nombre: String = "",
    val email: String = "",
    val emailVerificado: Boolean = false,
    val foto: Bitmap? = null,
    val telefono: String = "",
    val tipo: String = "dueno",
    val zonaDep: String = "",
    val zonaCiudad: String = "",
    val enEdicion: Boolean = false
)

private val HeroGradiente = listOf(
    Color(0xFFF0E7D9), Color(0xFFF8F1E5), Color(0xFFFFFAF2)
)

/**
 * "Mi perfil" en Compose: hero con avatar (foto o inicial) + boton de foto,
 * tarjeta de datos (email con chip de verificacion + telefono), tarjeta de
 * preferencias (tipo/departamento/ciudad con selectores) y acciones
 * editar/guardar/cerrar sesion. Reemplaza activity_mi_perfil.xml.
 */
@Composable
fun PantallaMiPerfil(
    ui: MiPerfilUiState,
    opcionesTipo: List<String>,
    departamentos: List<String>,
    ciudades: List<String>,
    etiquetaTipo: (String) -> String,
    onTelefono: (String) -> Unit,
    onTipo: (String) -> Unit,
    onDepartamento: (String) -> Unit,
    onCiudad: (String) -> Unit,
    onEditar: () -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    onFoto: () -> Unit,
    onCerrarSesion: () -> Unit,
    onAtras: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlkilColores.Fondo)
    ) {
        Cabecera(titulo = stringResource(R.string.mi_perfil_titulo), onAtras = onAtras)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ===== Hero: avatar + nombre + boton foto =====
            HeroPerfil(
                ui = ui,
                onFoto = onFoto
            )

            // ===== Datos =====
            TarjetaPerfil {
                Text(
                    text = stringResource(R.string.perfil_seccion_datos),
                    color = AlkilColores.Texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                EtiquetaCampo(stringResource(R.string.perfil_email_label))
                Text(
                    text = ui.email,
                    color = AlkilColores.Texto,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(8.dp))
                val colorChip = if (ui.emailVerificado) AlkilColores.Verde else AlkilColores.DoradoTexto
                val fondoChip =
                    if (ui.emailVerificado) AlkilColores.VerdeSuave else AlkilColores.DoradoSuave
                val textoChip = stringResource(
                    if (ui.emailVerificado) R.string.perfil_email_verificado
                    else R.string.perfil_email_pendiente
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(fondoChip)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(text = textoChip, color = colorChip, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(14.dp))
                EtiquetaCampo(stringResource(R.string.perfil_telefono_label))
                CampoTexto(
                    valor = ui.telefono,
                    onCambio = onTelefono,
                    hint = stringResource(R.string.perfil_telefono_hint),
                    habilitado = ui.enEdicion,
                    inputType = KeyboardType.Phone,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.perfil_datos_fijos),
                    color = AlkilColores.TextoMuted,
                    fontSize = 12.sp
                )
            }

            // ===== Preferencias =====
            TarjetaPerfil(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = stringResource(R.string.perfil_seccion_preferencias),
                    color = AlkilColores.Texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                CampoSelector(
                    valor = etiquetaTipo(ui.tipo),
                    opciones = opcionesTipo.map { etiquetaTipo(it) },
                    onSeleccionar = { etiqueta -> onTipo(tipoDeEtiqueta(etiqueta)) },
                    hint = stringResource(R.string.perfil_tipo_label),
                    habilitado = ui.enEdicion
                )
                Spacer(Modifier.height(12.dp))
                CampoSelector(
                    valor = ui.zonaDep,
                    opciones = departamentos,
                    onSeleccionar = onDepartamento,
                    hint = stringResource(R.string.perfil_zona_departamento_label),
                    habilitado = ui.enEdicion
                )
                Spacer(Modifier.height(12.dp))
                CampoSelector(
                    valor = ui.zonaCiudad,
                    opciones = ciudades,
                    onSeleccionar = onCiudad,
                    hint = stringResource(R.string.perfil_zona_ciudad_label),
                    habilitado = ui.enEdicion
                )
            }

            // ===== Acciones =====
            Spacer(Modifier.height(16.dp))
            if (!ui.enEdicion) {
                BotonCta(
                    texto = stringResource(R.string.perfil_editar),
                    onClick = onEditar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            } else {
                FilaBotones(
                    textoPrimario = stringResource(R.string.perfil_guardar),
                    onPrimario = onGuardar,
                    textoSecundario = stringResource(R.string.perfil_cancelar),
                    onSecundario = onCancelar
                )
            }

            Spacer(Modifier.height(12.dp))
            BotonOutline(
                texto = stringResource(R.string.auth_cerrar_sesion),
                onClick = onCerrarSesion,
                color = AlkilColores.Rojo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroPerfil(ui: MiPerfilUiState, onFoto: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(HeroGradiente))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center
        ) {
            if (ui.foto != null) {
                Image(
                    bitmap = ui.foto!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.perfil_foto_cd),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(AlkilColores.Primario),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ui.nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = ui.nombre,
                color = AlkilColores.Texto,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.perfil_cuenta_subtitulo),
                color = AlkilColores.TextoSuave,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (ui.enEdicion) {
                BotonOutline(
                    texto = stringResource(R.string.perfil_foto_agregar),
                    onClick = onFoto,
                    color = AlkilColores.CoralTexto,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun TarjetaPerfil(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AlkilColores.Tarjeta)
            .padding(16.dp),
        content = content
    )
}

private fun tipoDeEtiqueta(etiqueta: String): String = when (etiqueta.lowercase()) {
    "dueño", "dueno" -> "dueno"
    "inquilino" -> "inquilino"
    else -> "ambos"
}
