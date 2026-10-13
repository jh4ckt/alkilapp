package com.alkilapp.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alkilapp.R

/**
 * Cabecera del bottom sheet en Compose (handle + titulo + subtitulo). El
 * contenido vive aqui, pero el drag/tap sigue siendo el setOnTouchListener
 * de sheetHeader en MainActivity (la logica manual de translationY no se toca).
 */
@Composable
fun CabeceraSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // Handle de arrastre
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp)
                .size(width = 42.dp, height = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AlkilColores.OnDarkSuave)
        )
        Text(
            text = stringResource(R.string.nearby_title),
            color = AlkilColores.Texto,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 3.dp)
        )
        Text(
            text = stringResource(R.string.nearby_subtitle),
            color = AlkilColores.TextoSuave,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)
        )
    }
}
