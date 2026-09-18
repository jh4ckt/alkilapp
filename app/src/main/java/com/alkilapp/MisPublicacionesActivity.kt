package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityMisPublicacionesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Pantalla estática "Mis publicaciones": lista los inmuebles del usuario con su estado. */
class MisPublicacionesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMisPublicacionesBinding
    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisPublicacionesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMisPubBack.setOnClickListener { finish() }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, R.string.mis_pub_sesion, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.tvMisPubCuenta.text =
            getString(R.string.mis_pub_cuenta, auth.currentUser?.email ?: uid)
        escucharInmuebles(uid)
    }

    private fun escucharInmuebles(uid: String) {
        db.collection("propiedades")
            .whereEqualTo("idPropietario", uid)
            .addSnapshotListener { snap, error ->
                binding.tvMisPubCargando.visibility = View.GONE
                if (error != null) {
                    Log.w("AlkilApp", "MisPublicaciones error uid=$uid: ${error.message}")
                    binding.tvMisPubVacio.visibility = View.VISIBLE
                    binding.tvMisPubVacio.text =
                        getString(R.string.mis_pub_error, error.message ?: "?")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val docs = snap.documents.mapNotNull { Propiedad.desde(it) }
                    .sortedWith(
                        compareByDescending<Propiedad> { it.estadoNormalizado == "disponible" }
                            .thenBy { it.precio }
                    )
                Log.i("AlkilApp", "MisPublicaciones uid=$uid docs=${snap.size()} mostrando=${docs.size}")
                binding.llMisPublicaciones.removeAllViews()
                if (docs.isEmpty()) {
                    binding.tvMisPubVacio.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                binding.tvMisPubVacio.visibility = View.GONE
                docs.forEach { p -> binding.llMisPublicaciones.addView(construirInmueble(p)) }
            }
    }

    private fun construirInmueble(p: Propiedad): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
            radius = 14.dp.toFloat()
            cardElevation = 1.dp.toFloat()
            setCardBackgroundColor(getColor(R.color.surface))
            isClickable = true
            isFocusable = true
            setOnClickListener { abrirDetalle(p) }
        }

        val cont = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
        }

        cont.addView(TextView(this).apply {
            text = p.titulo
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        fila.addView(TextView(this).apply {
            text = p.precioFormateado
            textSize = 14f
            setTextColor(getColor(R.color.alkil_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val chip = estadoChip(p)
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 10.dp }
        fila.addView(chip)
        cont.addView(fila)

        cont.addView(TextView(this).apply {
            text = "${tipoMostrable(p.tipo)} · ${p.barrio.ifEmpty { p.ciudad }}"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
        })

        card.addView(cont)
        return card
    }

    private fun tipoMostrable(tipo: String): String = when (tipo.trim().lowercase()) {
        "habitacion", "habitación", "cuarto" -> "Habitacion"
        "departamento", "depto" -> "Departamento"
        "casa" -> "Casa"
        else -> "Otros"
    }

    private fun estadoChip(p: Propiedad): TextView {
        val (etiqueta, colorText, colorBg) = when (p.estadoNormalizado) {
            "disponible" -> Triple(
                getString(R.string.prop_estado_disponible),
                R.color.alkil_coral_dark,
                R.color.alkil_coral_soft
            )
            "finalizado" -> Triple(
                getString(R.string.prop_estado_finalizado),
                R.color.text_secondary,
                R.color.divider
            )
            else -> Triple(
                getString(R.string.prop_estado_revision),
                R.color.gold_text,
                R.color.alkil_gold_soft
            )
        }
        return TextView(this).apply {
            text = etiqueta
            textSize = 12f
            setTextColor(getColor(colorText))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_chip_info)
            backgroundTintList = ColorStateList.valueOf(getColor(colorBg))
            setPadding(10.dp, 3.dp, 10.dp, 3.dp)
        }
    }

    private fun abrirDetalle(p: Propiedad) {
        Log.i("AlkilApp", "MisPublicaciones: abriendo detalle id=${p.id} titulo=${p.titulo}")
        Toast.makeText(this, "Abriendo ${p.titulo}", Toast.LENGTH_SHORT).show()
        Intent(this, PropiedadDetalleActivity::class.java).apply {
            putExtra(PropiedadDetalleActivity.EXTRA_ID, p.id)
            putExtra(PropiedadDetalleActivity.EXTRA_TITULO, p.titulo)
            putExtra(PropiedadDetalleActivity.EXTRA_DESCRIPCION, p.descripcion)
            putExtra(PropiedadDetalleActivity.EXTRA_TIPO, p.tipo)
            putExtra(PropiedadDetalleActivity.EXTRA_OPERACION, p.operacion)
            putExtra(PropiedadDetalleActivity.EXTRA_PRECIO, p.precio)
            putExtra(PropiedadDetalleActivity.EXTRA_MONEDA, p.moneda)
            putExtra(PropiedadDetalleActivity.EXTRA_DIRECCION, p.direccion)
            putExtra(PropiedadDetalleActivity.EXTRA_BARRIO, p.barrio)
            putExtra(PropiedadDetalleActivity.EXTRA_CIUDAD, p.ciudad)
            putExtra(PropiedadDetalleActivity.EXTRA_LAT, p.lat)
            putExtra(PropiedadDetalleActivity.EXTRA_LNG, p.lng)
            putExtra(PropiedadDetalleActivity.EXTRA_ID_PROPIETARIO, p.idPropietario)
            putExtra(PropiedadDetalleActivity.EXTRA_AMBIENTES, p.ambientes)
            putExtra(PropiedadDetalleActivity.EXTRA_SUPERFICIE, p.superficieM2)
            putExtra(PropiedadDetalleActivity.EXTRA_COMODIDADES, p.comodidades.toTypedArray())
            putExtra(PropiedadDetalleActivity.EXTRA_FOTOS, p.fotos.toTypedArray())
            putExtra(PropiedadDetalleActivity.EXTRA_FOTOS_URL, p.photosUrl.toTypedArray())
            putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, p.esDestacado)
            putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, p.estado)
        }.also { startActivity(it) }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}