package com.alkilapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView con zoom por pellizco, doble toque y arrastre.
 * Sin librerías externas: matriz + ScaleGestureDetector + GestureDetector.
 * Cuando está en escala 1:1 deja que el ViewPager2 padre haga el swipe horizontal.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matriz = Matrix()
    private var escala = MIN_ESCALA
    private var ultimoX = 0f
    private var ultimoY = 0f

    private val detectorEscala = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var escalaBase = MIN_ESCALA

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                escalaBase = escala
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val nueva = (escalaBase * detector.scaleFactor).coerceIn(MIN_ESCALA, MAX_ESCALA)
                val ratio = nueva / escala
                if (ratio != 1f) {
                    escala = nueva
                    matriz.postScale(ratio, ratio, detector.focusX, detector.focusY)
                    limitarTraslacion()
                    imageMatrix = matriz
                }
                return true
            }
        }
    )

    private val detectorGestos = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (escala > MIN_ESCALA + 0.05f) {
                    restablecerAjuste()
                } else {
                    aplicarZoom(3f, e.x, e.y)
                }
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                ultimoX = e.x
                ultimoY = e.y
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        restablecerAjuste()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        restablecerAjuste()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return false
        detectorEscala.onTouchEvent(event)
        detectorGestos.onTouchEvent(event)

        val enEscala = escala > MIN_ESCALA + 0.05f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ultimoX = event.x
                ultimoY = event.y
                if (enEscala) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (enEscala) {
                    val dx = event.x - ultimoX
                    val dy = event.y - ultimoY
                    ultimoX = event.x
                    ultimoY = event.y
                    matriz.postTranslate(dx, dy)
                    limitarTraslacion()
                    imageMatrix = matriz
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return enEscala
    }

    fun restablecer() {
        escala = MIN_ESCALA
        restablecerAjuste()
    }

    private fun aplicarZoom(objetivo: Float, px: Float, py: Float) {
        val destino = objetivo.coerceIn(MIN_ESCALA, MAX_ESCALA)
        if (destino == escala) return
        val ratio = destino / escala
        escala = destino
        matriz.postScale(ratio, ratio, px, py)
        limitarTraslacion()
        imageMatrix = matriz
    }

    /** Ajusta la imagen al tamaño de la vista (letterbox) y resetea el zoom. */
    private fun restablecerAjuste() {
        escala = MIN_ESCALA
        val d = drawable ?: run {
            matriz.reset()
            imageMatrix = matriz
            return
        }
        val anchoOrigen = d.intrinsicWidth.toFloat()
        val altoOrigen = d.intrinsicHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (anchoOrigen <= 0f || altoOrigen <= 0f || vw <= 0f || vh <= 0f) return

        val f = min(vw / anchoOrigen, vh / altoOrigen)
        matriz.reset()
        matriz.postScale(f, f)
        matriz.postTranslate((vw - anchoOrigen * f) / 2f, (vh - altoOrigen * f) / 2f)
        imageMatrix = matriz
    }

    /** Impide que la imagen se salga de los bordes o que queden bordes vacíos. */
    private fun limitarTraslacion() {
        val d = drawable ?: return
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matriz.mapRect(rect)
        val vw = width.toFloat()
        val vh = height.toFloat()

        var dx = 0f
        if (rect.width() <= vw) {
            dx = (vw - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0f) dx = -rect.left
            if (rect.right < vw) dx = vw - rect.right
        }
        var dy = 0f
        if (rect.height() <= vh) {
            dy = (vh - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0f) dy = -rect.top
            if (rect.bottom < vh) dy = vh - rect.bottom
        }
        if (dx != 0f || dy != 0f) matriz.postTranslate(dx, dy)
    }

    private companion object {
        const val MIN_ESCALA = 1f
        const val MAX_ESCALA = 6f
    }
}