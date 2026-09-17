package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityPerfilPropietarioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
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

        binding.btnPerfilBack.setOnClickListener { finish() }

        val esMio = auth.currentUser?.uid == uid
        if (esMio || listingId.isBlank()) {
            binding.btnPerfilChat.visibility = View.GONE
        } else {
            binding.btnPerfilChat.setOnClickListener { abrirChat() }
        }

        if (uid.isBlank()) {
            binding.tvPerfilNoEncontrado.visibility = View.VISIBLE
            return
        }

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

                val telefono = (d["phoneNumber"] as? String) ?: (d["telefono"] as? String)
                if (!telefono.isNullOrBlank()) {
                    binding.tvPerfilTelefono.text = telefono
                    binding.tvPerfilTelefono.visibility = View.VISIBLE
                }
                val email = (d["email"] as? String) ?: (d["correo"] as? String)
                if (!email.isNullOrBlank()) {
                    binding.tvPerfilEmail.text = email
                    binding.tvPerfilEmail.visibility = View.VISIBLE
                }

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

        if (miUid == uid && p.estado == "disponible" && p.id.isNotBlank()) {
            val btn = com.google.android.material.button.MaterialButton(this).apply {
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
            btn.layoutParams = lpB
            cont.addView(btn)
        }

        card.addView(cont)
        return card
    }

    private fun estadoChip(p: Propiedad): TextView {
        val (etiqueta, colorText, colorBg) = when (p.estado) {
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
                R.color.alkil_gold,
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
                putExtra(PropiedadDetalleActivity.EXTRA_CONTACTO, p.contacto)
                putExtra(PropiedadDetalleActivity.EXTRA_AMBIENTES, p.ambientes)
                putExtra(PropiedadDetalleActivity.EXTRA_SUPERFICIE, p.superficieM2)
                putStringArrayListExtra(
                    PropiedadDetalleActivity.EXTRA_COMODIDADES,
                    ArrayList(p.comodidades)
                )
                putStringArrayListExtra(PropiedadDetalleActivity.EXTRA_FOTOS, ArrayList(p.fotos))
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
        const val EXTRA_CONTACTO = "perf_contacto"
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