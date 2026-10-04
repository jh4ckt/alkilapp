package com.alkilapp

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner

/**
 * Adaptador unico para todos los Spinner de la app: fila seleccionada con
 * esquinas redondeadas y menu desplegable con opciones redondeadas.
 *
 * Usar SIEMPRE esta funcion en vez de ArrayAdapter con layouts del sistema
 * (simple_spinner_item / simple_spinner_dropdown_item), que dibujan listas
 * cuadradas sin estilo.
 */
fun spinnerAdapter(context: Context, opciones: List<String>): ArrayAdapter<String> =
    ArrayAdapter(context, R.layout.item_spinner_selected, opciones).apply {
        setDropDownViewResource(R.layout.item_spinner_dropdown)
    }

/**
 * Aplica el estilo redondeado estandar a un Spinner. El fondo del control
 * (bg_spinner_rounded) y el popup (bg_spinner_popup) se declaran en el layout
 * con android:background / android:popupBackground.
 */
fun Spinner.estilizar(opciones: List<String>) {
    adapter = spinnerAdapter(context, opciones)
}
