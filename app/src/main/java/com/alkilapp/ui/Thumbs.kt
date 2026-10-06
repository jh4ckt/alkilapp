package com.alkilapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import kotlin.math.max

/**
 * Decodificación de fotos base64 de las tarjetas a un tamaño apto para la
 * miniatura (150dp ≈ 420px en xxhdpi), en dos pasadas (bounds + inSampleSize)
 * para no pixelar ni inflar memoria. Cache compartida por todas las vistas
 * (lista Compose, adapter de favoritos).
 */
object Thumbs {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {}

    fun decodificar(foto: String): Bitmap? {
        val key = foto.take(32) + "|" + foto.length
        cache.get(key)?.let { return it }
        val bytes = try {
            Base64.decode(foto, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val objetivo = dp(420)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = factorMuestra(bounds.outWidth, bounds.outHeight, objetivo)
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val escala = objetivo.toFloat() / max(bmp.width, bmp.height)
        val thumb = if (escala >= 1f) bmp else {
            val w = (bmp.width * escala).toInt().coerceAtLeast(1)
            val h = (bmp.height * escala).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
        }
        cache.put(key, thumb)
        return thumb
    }

    /** Mayor potencia de 2 que deja la imagen decodificada >= `objetivo` px. */
    private fun factorMuestra(ancho: Int, alto: Int, objetivo: Int): Int {
        var muestra = 1
        var mayor = max(ancho, alto)
        while (mayor / 2 >= objetivo) {
            mayor /= 2
            muestra *= 2
        }
        return muestra
    }

    private fun dp(valor: Int): Int =
        (valor * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
