package com.alkilapp.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

/**
 * Aplica los insets de las barras del sistema para que NINGUN control quede debajo de la
 * barra de estado ni de los botones/gestos de navegacion del telefono.
 *
 * Con targetSdk 36 (Android 16) el modo edge-to-edge es obligatorio y
 * android:fitsSystemWindows="true" ya no protege nada, por eso hay que escuchar los insets
 * a mano en cada ventana.
 *
 * Si el primer hijo del root es una tarjeta de cabecera (MaterialCardView), el inset superior
 * se pliega DENTRO de esa cabecera (margen superior negativo + padding) para que su color siga
 * cubriendo la barra de estado en lugar de dejar una franja vacia arriba.
 *
 * Se usa el maximo entre systemBars() y mandatorySystemGestures() porque en algunos equipos la
 * barra de navegacion se reporta como no visible pero su zona de gestos sigue existiendo.
 */
fun aplicarInsetsSistema(root: View) {
    val cabecera = (root as? ViewGroup)
        ?.takeIf { it.childCount > 0 }
        ?.getChildAt(0)
        ?.takeIf { it is MaterialCardView }

    val padIzq = root.paddingLeft
    val padArr = root.paddingTop
    val padDer = root.paddingRight
    val padAba = root.paddingBottom

    val cabPadArr = cabecera?.paddingTop ?: 0
    val cabMargen = cabecera?.layoutParams as? ViewGroup.MarginLayoutParams
    val cabMargenArr = cabMargen?.topMargin ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val top = InsetsUtils.arriba(insets)
        val bottom = InsetsUtils.abajo(insets)

        if (cabecera == null) {
            root.setPadding(padIzq, padArr + top, padDer, padAba + bottom)
        } else {
            root.setPadding(padIzq, padArr, padDer, padAba + bottom)
            cabecera.setPadding(
                cabecera.paddingLeft,
                cabPadArr + top,
                cabecera.paddingRight,
                cabecera.paddingBottom
            )
            cabMargen?.let {
                it.topMargin = cabMargenArr - top
                cabecera.layoutParams = it
            }
        }
        insets
    }
    ViewCompat.requestApplyInsets(root)
}

/** Insets crudos de las barras del sistema (en px). */
object InsetsUtils {

    fun arriba(insets: WindowInsetsCompat): Int {
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val corte = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        return maxOf(barras.top, corte.top)
    }

    fun abajo(insets: WindowInsetsCompat): Int {
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val gestos = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        return maxOf(barras.bottom, gestos.bottom)
    }
}
