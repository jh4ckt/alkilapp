package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityMisPublicacionesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.util.HashMap

/** Pantalla estática "Mis publicaciones": lista los inmuebles del usuario con su estado. */
class MisPublicacionesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMisPublicacionesBinding
    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private val billingManager by lazy { com.alkilapp.billing.BillingManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisPublicacionesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMisPubBack.setOnClickListener { finish() }

        // Inicializar Billing Manager
        billingManager.initialize { Log.d("AlkilAppBilling", "Billing listo en MisPublicaciones") }

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

        // Header con título + menú
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = p.titulo
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_more_vert)
            setColorFilter(getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
            setScaleType(ImageView.ScaleType.CENTER)
            setOnClickListener { v -> mostrarMenuOpciones(v, p) }
            contentDescription = "Más opciones"
        })
        cont.addView(header)

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
            // NO pasar fotos base64 por el intent: supera el límite de Binder
            // (TransactionTooLargeException). El detalle las carga por ID desde Firestore.
            putExtra(PropiedadDetalleActivity.EXTRA_FOTOS_URL, p.photosUrl.toTypedArray())
            putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, p.esDestacado)
            putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, p.estado)
        }.also { startActivity(it) }
    }

    private fun mostrarMenuOpciones(anchor: View, p: Propiedad) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_mis_publicaciones, popup.menu)
        val menu = popup.menu
        val estadoNorm = p.estadoNormalizado
        // Mostrar/ocultar opciones según estado
        // Pausar: solo si disponible
        menu.findItem(R.id.menu_pausar).isVisible = estadoNorm == "disponible"
        // Reactivar: solo si pausada por usuario (estado "pausada")
        menu.findItem(R.id.menu_reactivar).isVisible = estadoNorm == "pausada"
        // Marcar como alquilado: solo si disponible o pausada
        menu.findItem(R.id.menu_marcar_alquilado).isVisible = estadoNorm == "disponible" || estadoNorm == "pausada"
        // Destacar: solo si la publicación no está finalizada; cambia título según estado
        menu.findItem(R.id.menu_destacar).isVisible = estadoNorm != "finalizado"
        menu.findItem(R.id.menu_destacar).title = if (p.esDestacado) "Quitar destacado" else "Destacar publicacion"
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_editar -> {
                    editarPublicacion(p)
                    true
                }
                R.id.menu_destacar -> {
                    toggleDestacado(p)
                    true
                }
                R.id.menu_pausar -> {
                    pausarPublicacion(p)
                    true
                }
                R.id.menu_reactivar -> {
                    reactivarPublicacion(p)
                    true
                }
                R.id.menu_marcar_alquilado -> {
                    marcarComoAlquilado(p)
                    true
                }
                R.id.menu_eliminar -> {
                    confirmarEliminar(p)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun editarPublicacion(p: Propiedad) {
        Intent(this, RegistrarPropiedadActivity::class.java).apply {
            putExtra(RegistrarPropiedadActivity.EXTRA_EDIT_MODE, true)
            putExtra(RegistrarPropiedadActivity.EXTRA_PROPIEDAD_ID, p.id)
        }.also { startActivity(it) }
    }

    private fun toggleDestacado(p: Propiedad) {
        if (p.esDestacado) {
            quitarDestacado(p)
        } else {
            mostrarDialogoDestacar(p)
        }
    }

    private fun mostrarDialogoDestacar(p: Propiedad) {
        val opciones = arrayOf(
            "7 días - S/ 6.90",
            "15 días - S/ 12.90",
            "30 días - S/ 24.90"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Destacar publicación")
            .setMessage("Elige la duración del destacado:")
            .setItems(opciones) { _, which ->
                val dias = when (which) {
                    0 -> 7
                    1 -> 15
                    else -> 30
                }
                val precio = when (which) {
                    0 -> 6.90
                    1 -> 12.90
                    else -> 24.90
                }
                iniciarPagoDestacado(p, dias, precio)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarPagoDestacado(p: Propiedad, dias: Int, precio: Double) {
        val sku = when (dias) {
            7 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_7D
            15 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_15D
            else -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_30D
        }

        // Primero guardamos la solicitud pendiente en Firestore
        db.collection("propiedades").document(p.id)
            .set(mapOf(
                "solicitudDestacar" to true,
                "destacadoDias" to dias,
                "destacadoPrecio" to precio,
                "destacadoSku" to sku,
                "solicitudDestacarEn" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ), SetOptions.merge())
            .addOnSuccessListener {
                // Lanzar flujo de compra de Google Play Billing
                billingManager.launchPurchaseFlow(sku, object : com.alkilapp.billing.BillingManager.PurchaseCallback {
                    override fun onSuccess(productId: String, purchaseToken: String, orderId: String?) {
                        // Compra exitosa: activar destacado en Firestore
                        val hasta = java.util.Date(System.currentTimeMillis() + dias * 24L * 3600 * 1000)
                        val datosDestacado = HashMap<String, Any>().apply {
                                put("isFeatured", true)
                                put("featuredUntil", hasta)
                                put("destacadoDias", dias)
                                put("destacadoDiasRestantes", dias)
                                put("destacadoEstado", "aprobado")
                                put("solicitudDestacar", false)
                                put("destacadoAprobadoEn", com.google.firebase.firestore.FieldValue.serverTimestamp())
                                put("destacadoAprobadoPor", "google-play-billing")
                                put("purchaseToken", purchaseToken)
                                put("orderId", orderId ?: "")
                            }
                            db.collection("propiedades").document(p.id)
                                .set(datosDestacado, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(this@MisPublicacionesActivity, "¡Destacado activado por $dias días!", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this@MisPublicacionesActivity, "Compra OK pero error guardando: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }

                    override fun onError(message: String) {
                        // Error en compra: limpiar solicitud pendiente
                        val datosLimpieza = HashMap<String, Any?>().apply {
                            put("solicitudDestacar", false)
                            put("destacadoSku", null)
                        }
                        db.collection("propiedades").document(p.id)
                            .set(datosLimpieza, SetOptions.merge())
                        Toast.makeText(this@MisPublicacionesActivity, "Error en compra: $message", Toast.LENGTH_LONG).show()
                    }
                })
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error guardando solicitud: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun quitarDestacado(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .set(mapOf(
                "isFeatured" to false,
                "featuredUntil" to null,
                "destacadoDiasRestantes" to 0,
                "destacadoEstado" to "retirado",
                "solicitudDestacar" to false
            ), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Destacado retirado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun pausarPublicacion(p: Propiedad) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pausar publicacion")
            .setMessage("La publicacion dejara de mostrarse en el listado. Podras reactivarla cuando quieras.")
            .setPositiveButton("Pausar") { _, _ ->
                db.collection("propiedades").document(p.id)
                    .set(mapOf("estado" to "pausada"), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Publicacion pausada", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun reactivarPublicacion(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .set(mapOf("estado" to "disponible"), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Publicacion reactivada (disponible)", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun marcarComoAlquilado(p: Propiedad) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Marcar como alquilado")
            .setMessage("La publicacion pasara a estado 'Finalizado' y no se mostrara en el listado. ¿Confirmar?")
            .setPositiveButton("Si, alquilado") { _, _ ->
                db.collection("propiedades").document(p.id)
                    .set(mapOf("estado" to "finalizado"), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Publicacion finalizada", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarEliminar(p: Propiedad) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Eliminar publicacion")
            .setMessage("¿Eliminar definitivamente \"${p.titulo}\"? Esta accion no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> eliminarPublicacion(p) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarPublicacion(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Publicacion eliminada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}