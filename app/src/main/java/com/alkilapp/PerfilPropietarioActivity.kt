package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityPerfilPropietarioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.util.HashMap
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Perfil público de un arrendador/arrendatario (doc en "usuarios/{uid}").
 * Se abre desde la ficha del inmueble (tarjeta del propietario). Muestra
 * datos de contacto, nivel de confianza/verificación, rating y reseñas, y
 * permite iniciar el chat del inmueble desde acá.
 */
class PerfilPropietarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilPropietarioBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private val billingManager by lazy { com.alkilapp.billing.BillingManager(this) }

    private val cargadorImagenes = Executors.newSingleThreadExecutor()
    private val handlerUi = Handler(Looper.getMainLooper())

    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

    private var uid: String = ""
    private var listingId: String = ""
    private var listingTitulo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilPropietarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        listingId = intent.getStringExtra(EXTRA_LISTING_ID).orEmpty()
        listingTitulo = intent.getStringExtra(EXTRA_LISTING_TITULO).orEmpty()

        binding.tvPerfilInicial.backgroundTintList = ColorStateList.valueOf(
            coloresAvatar[Math.floorMod(uid.hashCode(), coloresAvatar.size)]
        )

        // Cerrar al tocar overlay o botón cerrar
        binding.viewOverlay.setOnClickListener { finish() }
        binding.btnPerfilClose.setOnClickListener { finish() }
        // Evitar que click en la tarjeta cierre
        binding.cardPerfil.setOnClickListener { }

        val esMio = auth.currentUser?.uid == uid
        if (esMio || listingId.isBlank()) {
            binding.btnPerfilChat.visibility = View.GONE
        } else {
            binding.btnPerfilChat.setOnClickListener { abrirChat() }
        }

        if (esMio || uid.isBlank()) {
            binding.btnPerfilResena.visibility = View.GONE
        } else {
            binding.btnPerfilResena.visibility = View.VISIBLE
            binding.btnPerfilResena.setOnClickListener { abrirResena() }
        }

        if (uid.isBlank()) {
            binding.tvPerfilNoEncontrado.visibility = View.VISIBLE
            return
        }

        // Inicializar Billing Manager
        billingManager.initialize { Log.d("AlkilAppBilling", "Billing listo en PerfilPropietario") }

        cargarUsuario()
        escucharReviews()
        escucharInmuebles()
    }

    // ======================================================================

    private fun cargarUsuario() {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                val d = doc.data
                if (d == null) {
                    binding.tvPerfilNoEncontrado.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }
                val nombre = (d["name"] as? String)
                    ?: (d["nombre"] as? String)
                    ?: (d["email"] as? String)?.substringBefore("@")
                    ?: uid.take(6)
                binding.tvPerfilNombre.text = nombre
                binding.tvPerfilInicial.text = nombre.trim().firstOrNull()?.uppercaseChar().toString()

                val rol = (d["role"] as? String)
                    ?: (d["tipoUsuario"] as? String)
                    ?: ""
                binding.tvPerfilRol.text = etiquetaRol(rol)
                binding.tvPerfilRol.visibility = View.VISIBLE

                val identityVerified = (d["verification"] as? Map<*, *>)
                    ?.get("identityVerified") as? Boolean ?: false
                val badge = d["verificationBadge"] == true
                val verificado = identityVerified || badge
                if (verificado) {
                    binding.ivPerfilVerificado.visibility = View.VISIBLE
                    binding.tvPerfilVerificado.text = getString(R.string.perfil_verificado_ok)
                    binding.tvPerfilVerificado.visibility = View.VISIBLE
                    val trust = d["trustLevel"] as? String ?: ""
                    if (trust.isNotBlank()) {
                        binding.tvPerfilVerificado.text =
                            "${etiquetaTrust(trust)} · ${getString(R.string.perfil_verificado_ok)}"
                    }
                }

                val rating = (d["rating"] as? Number)?.toDouble() ?: 0.0
                val total = (d["totalRatings"] as? Number)?.toInt() ?: 0
                if (rating > 0) {
                    binding.tvPerfilRating.text = getString(
                        R.string.perfil_rating,
                        String.format(Locale.US, "%.1f", rating),
                        total
                    )
                    binding.tvPerfilRating.visibility = View.VISIBLE
                }

                // Por privacidad no se muestran telefono ni correo del propietario:
                // el contacto se hace por el chat interno.
                binding.tvPerfilTelefono.visibility = View.GONE
                binding.tvPerfilEmail.visibility = View.GONE

                val creado = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                if (creado != null) {
                    binding.tvPerfilMiembroDesde.text = getString(
                        R.string.perfil_miembro_desde,
                        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(creado)
                    )
                    binding.tvPerfilMiembroDesde.visibility = View.VISIBLE
                }

                val foto = d["profilePicture"] as? String
                val b64 = d["fotoBase64"] as? String
                if (!foto.isNullOrBlank()) {
                    cargarFoto(foto)
                } else if (!b64.isNullOrBlank()) {
                    cargarFotoBase64(b64)
                }
            }
            .addOnFailureListener {
                binding.tvPerfilNoEncontrado.visibility = View.VISIBLE
            }
    }

    private fun etiquetaRol(rol: String): String = when (rol.lowercase()) {
        "propietario", "propietaria", "dueno", "dueño", "arrendador" ->
            getString(R.string.perfil_rol_propietario)
        "inquilino", "inquilina", "arrendatario" ->
            getString(R.string.perfil_rol_inquilino)
        "ambos", "propietario e inquilino" ->
            getString(R.string.perfil_rol_ambos)
        else -> rol.replaceFirstChar { it.uppercaseChar() }
    }

    private fun etiquetaTrust(trust: String): String = when (trust.lowercase()) {
        "basic" -> getString(R.string.perfil_trust_basic)
        "verified_premium" -> getString(R.string.perfil_trust_premium)
        else -> getString(R.string.perfil_trust_none)
    }

    private fun cargarFoto(url: String) {
        cargadorImagenes.execute {
            val bmp = descargarBitmap(url)
            handlerUi.post {
                if (bmp != null) {
                    binding.ivPerfilFoto.setImageBitmap(bmp)
                    binding.ivPerfilFoto.visibility = View.VISIBLE
                    binding.tvPerfilInicial.visibility = View.GONE
                }
            }
        }
    }

    private fun cargarFotoBase64(b64: String) {
        val bytes = try {
            android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        binding.ivPerfilFoto.setImageBitmap(bmp)
        binding.ivPerfilFoto.visibility = View.VISIBLE
        binding.tvPerfilInicial.visibility = View.GONE
    }

    private fun descargarBitmap(urlString: String): Bitmap? {
        return try {
            val conexion = URL(urlString).openConnection() as HttpURLConnection
            conexion.connectTimeout = 10000
            conexion.readTimeout = 10000
            val stream = conexion.inputStream
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            conexion.disconnect()
            bmp
        } catch (_: Exception) {
            null
        }
    }

    // ---- Reseñas ---------------------------------------------------------

    private fun escucharReviews() {
        db.collection("usuarios").document(uid).collection("reviews")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                binding.llPerfilReviews.removeAllViews()
                val docs = snap.documents
                if (docs.isEmpty()) {
                    binding.tvPerfilReviewsVacio.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                binding.tvPerfilReviewsVacio.visibility = View.GONE
                docs.forEach { doc ->
                    val d = doc.data ?: return@forEach
                    binding.llPerfilReviews.addView(construirReview(d))
                }
            }
    }

    private fun construirReview(d: Map<String, Any>): View {
        val autor = (d["authorName"] as? String) ?: "—"
        val rating = (d["rating"] as? Number)?.toInt() ?: 0
        val comentario = (d["comment"] as? String) ?: ""

        val cont = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp, 0, 10.dp)
        }

        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val estrellas = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (i in 0 until 5) {
            val img = ImageViewRenderer.estrella(this, i < rating)
            estrellas.addView(img)
        }
        val tvAutor = TextView(this).apply {
            text = getString(R.string.perfil_review_autor, autor)
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            gravity = android.view.Gravity.END
        }
        fila.addView(estrellas)
        fila.addView(tvAutor)
        cont.addView(fila)

        if (comentario.isNotBlank()) {
            val tvCom = TextView(this).apply {
                text = comentario
                textSize = 13f
                setTextColor(getColor(R.color.text_primary))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 6.dp
                layoutParams = lp
            }
            cont.addView(tvCom)
        }

        cont.background = getDrawable(R.drawable.bg_input_detalle)
        cont.setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        return cont
    }

    // ---- Dejar reseña ----------------------------------------------------

    private fun abrirResena() {
        val miUid = auth.currentUser?.uid
        if (miUid == null) {
            Toast.makeText(this, R.string.resena_sesion, Toast.LENGTH_SHORT).show()
            return
        }
        if (uid.isBlank() || uid == miUid) return

        val vista = layoutInflater.inflate(R.layout.dialog_resena, null)
        val contEstrellas = vista.findViewById<LinearLayout>(R.id.llResenaEstrellas)
        val etComentario = vista.findViewById<EditText>(R.id.etResenaComentario)

        var seleccion = 0
        val estrellas = ArrayList<ImageView>()
        for (i in 1..5) {
            val img = ImageView(this)
            val tam = 38.dp
            img.layoutParams = LinearLayout.LayoutParams(tam, tam)
            img.setImageResource(R.drawable.ic_star)
            img.contentDescription = getString(R.string.resena_estrella_cd, i)
            img.setOnClickListener {
                seleccion = i
                pintarEstrellas(estrellas, seleccion)
            }
            contEstrellas.addView(img)
            estrellas.add(img)
        }
        pintarEstrellas(estrellas, seleccion)

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.resena_titulo)
            .setView(vista)
            .setNegativeButton(R.string.resena_cancelar, null)
            .setPositiveButton(R.string.resena_enviar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (seleccion <= 0) {
                    Toast.makeText(this, R.string.resena_falta_rating, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialogo.dismiss()
                guardarResena(miUid, seleccion, etComentario.text.toString().trim())
            }
        }
        dialogo.show()
    }

    private fun pintarEstrellas(estrellas: List<ImageView>, seleccion: Int) {
        estrellas.forEachIndexed { indice, img ->
            img.imageTintList = ColorStateList.valueOf(
                if (indice < seleccion) getColor(R.color.alkil_gold)
                else getColor(R.color.divider)
            )
        }
    }

    /**
     * Guarda la reseña en usuarios/{uid}/reviews/{miUid} (una por usuario;
     * volver a calificar la actualiza) y recalcula el promedio en el doc del
     * propietario (campos "rating" y "totalRatings").
     */
    private fun guardarResena(miUid: String, estrellas: Int, comentario: String) {
        val usuario = auth.currentUser
        val nombre = usuario?.displayName?.takeIf { it.isNotBlank() }
            ?: usuario?.email?.substringBefore("@")
            ?: getString(R.string.perfil_rol_propietario)

        db.collection("usuarios").document(uid).collection("reviews").get()
            .addOnSuccessListener { snap ->
                var suma = 0.0
                var total = 0
                snap.documents.forEach { doc ->
                    if (doc.id.equals(miUid, ignoreCase = true)) return@forEach
                    suma += (doc["rating"] as? Number)?.toDouble() ?: 0.0
                    total++
                }
                suma += estrellas
                total++
                val promedio = Math.round(suma / total * 10.0) / 10.0

                val lote = db.batch()
                lote.set(
                    db.collection("usuarios").document(uid)
                        .collection("reviews").document(miUid),
                    hashMapOf(
                        "authorId" to miUid,
                        "authorName" to nombre,
                        "rating" to estrellas,
                        "comment" to comentario,
                        "listingId" to listingId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                lote.update(
                    db.collection("usuarios").document(uid),
                    mapOf("rating" to promedio, "totalRatings" to total)
                )
                lote.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.resena_ok, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.resena_error, e.localizedMessage ?: "?"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.resena_error, e.localizedMessage ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---- Inmuebles que publicó este usuario ------------------------------

    private fun escucharInmuebles() {
        if (uid.isBlank()) return
        val miUid = auth.currentUser?.uid
        db.collection("propiedades")
            .whereEqualTo("idPropietario", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val docs = snap.documents.mapNotNull { Propiedad.desde(it) }
                    .sortedBy { it.precio }
                binding.llPerfilInmuebles.removeAllViews()
                if (docs.isEmpty()) {
                    binding.tvPerfilInmueblesVacio.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                binding.tvPerfilInmueblesVacio.visibility = View.GONE
                docs.forEach { p -> binding.llPerfilInmuebles.addView(construirInmueble(p, miUid)) }
            }
    }

    private fun construirInmueble(p: Propiedad, miUid: String?): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
            radius = 14.dp.toFloat()
            cardElevation = 1.dp.toFloat()
            setCardBackgroundColor(getColor(R.color.surface))
            isClickable = true
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

        val estadoNorm = p.estadoNormalizado
        val esDisponible = estadoNorm == "disponible"
        val esPausada = estadoNorm == "pausada"
        if (miUid == uid && (esDisponible || esPausada) && p.id.isNotBlank()) {
            // Botón "Marcar como alquilado" (disponible y pausada)
            val btnFinalizar = com.google.android.material.button.MaterialButton(this).apply {
                text = getString(R.string.prop_finalizar_btn)
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(getColor(R.color.alkil_rojo))
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.alkil_coral_soft))
                setOnClickListener { confirmarFinalizar(p) }
            }
            val lpB = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
            btnFinalizar.layoutParams = lpB
            cont.addView(btnFinalizar)

            if (esDisponible) {
                // Botón "Pausar publicación" (solo disponible)
                val btnPausar = com.google.android.material.button.MaterialButton(this).apply {
                    text = "Pausar publicacion"
                    textSize = 13f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    setTextColor(getColor(R.color.alkil_primary))
                    backgroundTintList = ColorStateList.valueOf(getColor(R.color.alkil_coral_soft))
                    setOnClickListener { pausarPublicacion(p) }
                }
                val lpP = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
                btnPausar.layoutParams = lpP
                cont.addView(btnPausar)
            } else if (esPausada) {
                // Botón "Reactivar publicación" (solo pausada)
                val btnReactivar = com.google.android.material.button.MaterialButton(this).apply {
                    text = "Reactivar publicacion"
                    textSize = 13f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    setTextColor(getColor(R.color.alkil_primary))
                    backgroundTintList = ColorStateList.valueOf(getColor(R.color.alkil_coral_soft))
                    setOnClickListener { reactivarPublicacion(p) }
                }
                val lpR = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
                btnReactivar.layoutParams = lpR
                cont.addView(btnReactivar)
            }

            // Botón "Destacar publicación" - solicita al admin
            val btnDestacar = com.google.android.material.button.MaterialButton(this).apply {
                text = getString(R.string.perfil_destacar_publicacion)
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(getColor(R.color.alkil_primary))
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.alkil_gold_soft))
                strokeColor = ColorStateList.valueOf(getColor(R.color.alkil_gold))
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                setOnClickListener { solicitarDestacar(p) }
            }
            val lpD = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
            btnDestacar.layoutParams = lpD
            cont.addView(btnDestacar)
        }

        card.addView(cont)
        return card
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

    private fun confirmarFinalizar(p: Propiedad) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detalle_finalizar_confirm_titulo)
            .setMessage(R.string.detalle_finalizar_confirm_msg)
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .setPositiveButton(R.string.detalle_finalizar_si) { _, _ ->
                db.collection("propiedades").document(p.id)
                    .update("estado", "finalizado")
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.detalle_finalizar_ok, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.detalle_finalizar_error, e.localizedMessage ?: "?"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    private fun pausarPublicacion(p: Propiedad) {
        MaterialAlertDialogBuilder(this)
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

    private fun solicitarDestacar(p: Propiedad) {
        val opciones = arrayOf(
            "7 días - S/ 6.90",
            "15 días - S/ 12.90",
            "30 días - S/ 24.90"
        )
        MaterialAlertDialogBuilder(this)
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
                val sku = when (dias) {
                    7 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_7D
                    15 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_15D
                    else -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_30D
                }

                // Guardar solicitud pendiente en Firestore
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
                                        Toast.makeText(this@PerfilPropietarioActivity, "¡Destacado activado por $dias días!", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this@PerfilPropietarioActivity, "Compra OK pero error guardando: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }

                            override fun onError(message: String) {
                                val datosLimpieza = HashMap<String, Any?>().apply {
                                    put("solicitudDestacar", false)
                                    put("destacadoSku", null)
                                }
                                db.collection("propiedades").document(p.id)
                                    .set(datosLimpieza, SetOptions.merge())
                                Toast.makeText(this@PerfilPropietarioActivity, "Error en compra: $message", Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error guardando solicitud: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    

    /** Abre la ficha del inmueble con las mismas extras que el listado principal. */
    private fun abrirDetalle(p: Propiedad) {
        startActivity(
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
                putStringArrayListExtra(
                    PropiedadDetalleActivity.EXTRA_COMODIDADES,
                    ArrayList(p.comodidades)
                )
                // Las fotos base64 NO viajan por el intent (TransactionTooLargeException).
                // PropiedadDetalleActivity las carga por ID desde Firestore.
                putStringArrayListExtra(
                    PropiedadDetalleActivity.EXTRA_FOTOS_URL,
                    ArrayList(p.photosUrl)
                )
                putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, p.esDestacado)
                putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, p.estado)
            }
        )
    }

    // ---- Chat desde el perfil ---------------------------------------------

    private fun abrirChat() {
        val miUid = auth.currentUser?.uid ?: return
        if (listingId.isBlank() || uid.isBlank()) return
        val chatId = "inm-$listingId"
        db.collection("chats").document(chatId)
            .set(
                hashMapOf(
                    "chatId" to chatId,
                    "listingId" to listingId,
                    "listingTitle" to listingTitulo,
                    "participants" to listOf(miUid, uid)
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                startActivity(
                    Intent(this, ChatDetailActivity::class.java).apply {
                        putExtra(ChatListActivity.EXTRA_CHAT_ID, chatId)
                        putExtra(ChatListActivity.EXTRA_LISTING, listingTitulo)
                        putExtra(ChatListActivity.EXTRA_OTRO_UID, uid)
                    }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage ?: "error", Toast.LENGTH_LONG).show()
            }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_UID = "perf_uid"
        const val EXTRA_LISTING_ID = "perf_listing_id"
        const val EXTRA_LISTING_TITULO = "perf_listing_titulo"
    }
}

/** Mini-helper para estrella (llena o hueca). */
private object ImageViewRenderer {
    fun estrella(context: android.content.Context, llena: Boolean): android.widget.ImageView {
        val img = android.widget.ImageView(context)
        val tam = (16 * context.resources.displayMetrics.density).toInt()
        img.layoutParams = LinearLayout.LayoutParams(tam, tam)
        img.setImageResource(R.drawable.ic_star)
        img.imageTintList = android.content.res.ColorStateList.valueOf(
            if (llena) context.getColor(R.color.alkil_gold)
            else context.getColor(R.color.divider)
        )
        return img
    }
}