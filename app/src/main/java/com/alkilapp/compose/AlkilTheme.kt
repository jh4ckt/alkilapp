package com.alkilapp.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.alkilapp.R

/**
 * Design system AlkilApp en Compose. Los colores vienen de los MISMOS tokens
 * de colors.xml (60-30-10: superficie neutra / estructura / coral CTAs) para
 * que las pantallas XML y las migradas a Compose hablen el mismo idioma.
 */
object AlkilColores {
    val Acento = Color(0xFFFF6B5E)        // brand_accent
    val AcentoOscuro = Color(0xFFE85548)  // brand_accent_dark
    val AcentoDeep = Color(0xFFD24534)    // brand_accent_deep (CTA con texto blanco)
    val Primario = Color(0xFF1E293B)      // brand_primary (slate)
    val PrimarioSuave = Color(0xFFF0ECE4) // brand_primary_soft
    val Navy = Color(0xFF0B2447)          // precio_navy
    val Fondo = Color(0xFFF7F4EE)         // bg_main
    val Tarjeta = Color(0xFFFFFFFF)       // surface_card
    val Borde = Color(0xFFE5DED3)         // border_subtle / divider
    val Texto = Color(0xFF1E293B)         // text_primary
    val TextoSuave = Color(0xFF5B6268)    // text_secondary
    val TextoMuted = Color(0xFF625C53)    // text_muted
    val Verde = Color(0xFF1F7A57)         // success_text
    val VerdeSuave = Color(0xFFE4F3EC)    // status_success_soft / mint_soft
    val Dorado = Color(0xFFD4A017)        // brand_gold
    val GrisSuave = Color(0xFFF2EFE9)     // alkil_gray_soft (pills de specs)
    val Calido = Color(0xFFF4EFE5)        // surface_warm
}

@Composable
fun AlkilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colorResource(R.color.brand_primary),
            onPrimary = Color.White,
            primaryContainer = colorResource(R.color.brand_primary),
            onPrimaryContainer = Color.White,
            secondary = colorResource(R.color.brand_accent),
            onSecondary = Color.White,
            secondaryContainer = colorResource(R.color.brand_accent_soft),
            onSecondaryContainer = colorResource(R.color.brand_accent_dark),
            background = colorResource(R.color.bg_main),
            onBackground = colorResource(R.color.text_primary),
            surface = colorResource(R.color.surface_card),
            onSurface = colorResource(R.color.text_primary),
            surfaceVariant = colorResource(R.color.brand_primary_soft),
            onSurfaceVariant = colorResource(R.color.text_secondary),
            outline = colorResource(R.color.border_subtle),
            outlineVariant = colorResource(R.color.border_subtle),
            error = colorResource(R.color.coral_text)
        ),
        content = content
    )
}
