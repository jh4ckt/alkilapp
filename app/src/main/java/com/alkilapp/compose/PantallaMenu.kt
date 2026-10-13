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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/** Estado de la cabecera del drawer lateral (sesion + badges). */
data class MenuUiState(
    val nombre: String = "",
    val email: String = "",
    val inicial: String = "?",
    val invitado: Boolean = true,
    val sesionActiva: Boolean = false,
    val foto: Bitmap? = null,
    val badgeChats: Int = 0,
    val badgeFavoritos: Int = 0
)

private val ColorOnDark = Color(0xFFFFFFFF)

/**
 * Menu lateral (hamburguesa) en Compose: cabecera con la sesion activa (foto
 * circular o inicial), filas de navegacion con badges (chats sin leer /
 * favoritos), acciones principales en coral (publicar / mis publicaciones /
 * verificar identidad) y el footer de firma. Reemplaza panel_menu.xml.
 */
@Composable
fun PantallaMenu(
    ui: MenuUiState,
    onPerfilHeader: () -> Unit,
    onPerfil: () -> Unit,
    onChats: () -> Unit,
    onFavoritos: () -> Unit,
    onFiltros: () -> Unit,
    onPublicar: () -> Unit,
    onMisPublicaciones: () -> Unit,
    onVerificar: () -> Unit,
    onSoporte: () -> Unit,
    onSalir: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(AlkilColores.Tarjeta)
    ) {
        // ===== Cabecera: quien esta conectado =====
        CabeceraMenu(
            ui = ui,
            onClick = onPerfilHeader,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(AlkilColores.Primario, AlkilColores.PrimarioDark),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(1200f, 400f)
                    )
                )
                .statusBarsPadding()
                .clickable(onClick = onPerfilHeader)
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 22.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(10.dp))

            FilaMenu(
                icono = R.drawable.ic_person,
                texto = stringResource(R.string.mi_perfil_titulo),
                colorIcono = AlkilColores.Primario,
                colorTexto = AlkilColores.Texto,
                onClick = onPerfil
            )
            FilaMenu(
                icono = R.drawable.ic_chat,
                texto = stringResource(R.string.nav_chats),
                colorIcono = AlkilColores.Primario,
                colorTexto = AlkilColores.Texto,
                badge = ui.badgeChats,
                onClick = onChats
            )
            FilaMenu(
                icono = R.drawable.ic_corazon,
                texto = stringResource(R.string.nav_favoritos),
                colorIcono = AlkilColores.Primario,
                colorTexto = AlkilColores.Texto,
                badge = ui.badgeFavoritos,
                onClick = onFavoritos
            )
            FilaMenu(
                icono = R.drawable.ic_filter,
                texto = stringResource(R.string.nav_filtros),
                colorIcono = AlkilColores.Primario,
                colorTexto = AlkilColores.Texto,
                onClick = onFiltros
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(1.dp)
                    .background(AlkilColores.Borde)
            )

            FilaMenu(
                icono = R.drawable.ic_add,
                texto = stringResource(R.string.prop_agregar_titulo),
                colorIcono = AlkilColores.CoralTexto,
                colorTexto = AlkilColores.CoralTexto,
                negrita = true,
                onClick = onPublicar
            )
            FilaMenu(
                icono = R.drawable.ic_lista,
                texto = stringResource(R.string.ver_mis_publicaciones),
                colorIcono = AlkilColores.CoralTexto,
                colorTexto = AlkilColores.CoralTexto,
                negrita = true,
                onClick = onMisPublicaciones
            )
            FilaMenu(
                icono = R.drawable.ic_verificado_escudo,
                texto = stringResource(R.string.nav_verificar_identidad),
                colorIcono = AlkilColores.CoralTexto,
                colorTexto = AlkilColores.CoralTexto,
                negrita = true,
                onClick = onVerificar
            )
            FilaMenu(
                icono = R.drawable.ic_help,
                texto = stringResource(R.string.nav_soporte),
                colorIcono = AlkilColores.Primario,
                colorTexto = AlkilColores.Texto,
                onClick = onSoporte
            )
            if (ui.sesionActiva) {
                FilaMenu(
                    icono = R.drawable.ic_logout,
                    texto = stringResource(R.string.nav_cerrar_sesion),
                    colorIcono = AlkilColores.Rojo,
                    colorTexto = AlkilColores.Rojo,
                    onClick = onSalir
                )
            }

            Text(
                text = stringResource(R.string.nav_footer),
                color = AlkilColores.TextoMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun CabeceraMenu(ui: MenuUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            if (ui.foto != null) {
                Image(
                    bitmap = ui.foto!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.nav_foto_perfil),
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
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x4DFFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ui.inicial,
                        color = ColorOnDark,
                        fontSize = 22.sp,
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
                color = ColorOnDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!ui.invitado) {
                Text(
                    text = ui.email,
                    color = AlkilColores.OnDarkSuave,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.nav_invitado_hint),
                    color = AlkilColores.OnDarkSuave,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun FilaMenu(
    icono: Int,
    texto: String,
    colorIcono: Color,
    colorTexto: Color,
    negrita: Boolean = false,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icono),
            contentDescription = texto,
            tint = colorIcono,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = texto,
            color = colorTexto,
            fontSize = 15.sp,
            fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AlkilColores.Acento)
                    .padding(horizontal = 6.dp)
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    color = ColorOnDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
