package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.PantallaPerfilPropietario
import com.alkilapp.compose.PerfilPropietarioUiState
import com.alkilapp.compose.ResenaUi
import com.alkilapp.compose.colorAvatarPorUid
import com.alkilapp.data.Propiedad
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.util.HashMap
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Perfil publico de un arrendador/arrendatario (doc en "usuarios/{uid}").
 * Se abre desde la ficha del inmueble (tarjeta del propietario). Muestra
 * datos de contacto, nivel de confianza/verificacion, rating y resenas, y
 * permite iniciar el chat del inmueble desde aca. UI en Compose; los
 * dialogos (resena, confirmaciones) se quedan en View.
 */
class PerfilPropietarioActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private val billingManager by lazy { com.alkilapp.billing.BillingManager(this) }

    private val cargadorImagenes = Executors.newSingleThreadExecutor()
    private val handlerUi = Handler(Looper.getMainLooper())

    private var escuchaReviews: ListenerRegistration? = null
    private var escuchaInmuebles: ListenerRegistration? = null

    private var uid: String = ""
    private var listingId: String = ""
    private var listingTitulo: String = ""

    private var ui by mutableStateOf(PerfilPropietarioUiState())
    private var inmuebles by mutableStateOf<List<Propiedad>>(emptyList())
    private var resenas by mutableStateOf<List<ResenaUi>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uid = intent.getStringExtra(EXTRA_UID) ?: ""
        listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: ""
        listingTitulo = intent.getStringExtra(EXTRA_LISTING_TITULO) ?: ""

        val esMio = auth.currentUser?.uid == uid

        setContent {
            AlkilTheme {
                PantallaPerfilPropietario(
                    ui = ui,
                    inmuebles = inmuebles,
                    resenas = resenas,
                    onCerrar = { finish() },
                    onChat = { abrirChat() },
                    onResena = { abrirResena() },
                    onInmueble = { p -> abrirDetalle(p) },
                    onFinalizar = { p -> confirmarFinalizar(p) },
                    onPausar = { p -> pausarPublicacion(p) },
                    onReactivar = { p -> reactivarPublicacion(p) },
                    onDestacar = { p -> solicitarDestacar(p) }
                )
            }
        }

        if (uid.isBlank()) {
            ui = ui.copy(encontrado = false)
            return
        }

        ui = ui.copy(
            colorInicial = colorAvatarPorUid(uid),
            esDueno = esMio,
            puedeChatear = !esMio && listingId.isNotBlank(),
            puedeResenar = !esMio
        )

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
                    ui = ui.copy(encontrado = false)
                    return@addOnSuccessListener
                }
                val nombre = (d["name"] as? String)
                    ?: (d["nombre"] as? String)
                    ?: (d["email"] as? String)?.substringBefore("@")
                    ?: uid.take(6)

                val rol = (d["role"] as? String) ?: (d["tipoUsuario"] as? String) ?: ""
                val identityVerified = (d["verification"] as? Map<*, *>)
                    ?.get("identityVerified") as? Boolean ?: false
                val badge = d["verificationBadge"] == true
                val verificado = identityVerified || badge
                val trust = d["trustLevel"] as? String ?: ""
                val textoVerificado = if (verificado) {
                    if (trust.isNotBlank()) "${etiquetaTrust(trust)} · ${getString(R.string.perfil_verificado_ok)}"
                    else getString(R.string.perfil_verificado_ok)
                } else ""

                val rating = (d["rating"] as? Number)?.toDouble() ?: 0.0
                val total = (d["totalRatings"] as? Number)?.toInt() ?: 0
                val ratingTexto = if (rating > 0) {
                    getString(R.string.perfil_rating, String.format(Locale.US, "%.1f", rating), total)
                } else null

                val creado = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                val miembroDesde = if (creado != null) {
                    getString(
                        R.string.perfil_miembro_desde,
                        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(creado)
                    )
                } else null

                ui = ui.copy(
                    nombre = nombre,
                    inicial = nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    rol = etiquetaRol(rol),
                    verificado = verificado,
                    textoVerificado = textoVerificado,
                    ratingTexto = ratingTexto,
                    miembroDesde = miembroDesde
                )

                val foto = d["profilePicture"] as? String
                val b64 = d["fotoBase64"] as? String
                if (!foto.isNullOrBlank()) {
                    cargarFoto(foto)
                } else if (!b64.isNullOrBlank()) {
                    cargarFotoBase64(b64)
                }
            }
            .addOnFailureListener {
                ui = ui.copy(encontrado = false)
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
                    ui = ui.copy(foto = bmp)
                }
            }
        }
    }

    private fun cargarFotoBase64(b64: String) {
        try {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            handlerUi.post {
                ui = ui.copy(foto = bmp)
            }
        } catch (e: Exception) {
            // Ignore decode errors
        }
    }

    private fun descargarBitmap(urlString: String): Bitmap? {
        return try {
            val conexion = URL(urlString).openConnection() as HttpURLConnection
            conexion.connectTimeout = 10000
            conexion.readTimeout = 10000
            conexion.doInput = true
            val stream = conexion.inputStream
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            conexion.disconnect()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    // ---- Reseñas ---------------------------------------------------------

    private fun escucharReviews() {
        escuchaReviews?.remove()
        escuchaReviews = db.collection("usuarios").document(uid).collection("reviews")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                resenas = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ResenaUi(
                        autor = (d["authorName"] as? String) ?: "—",
                        rating = (d["rating"] as? Number)?.toInt() ?: 0,
                        comentario = (d["comment"] as? String) ?: ""
                    )
                }
            }
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
        escuchaInmuebles?.remove()
        escuchaInmuebles = db.collection("propiedades")
            .whereEqualTo("idPropietario", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w("AlkilApp", "PerfilPropietario escucharInmuebles error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                inmuebles = snap.documents.mapNotNull { Propiedad.desde(it) }
                    .filter { it.estado != "under_review" && it.estado != "finalizado" && it.estado != "pausada" }
                    .sortedBy { it.precio }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        escuchaReviews?.remove()
        escuchaReviews = null
        escuchaInmuebles?.remove()
        escuchaInmuebles = null
    }

    private fun confirmarFinalizar(p: Propiedad) {
        val tituloConfirm = if (p.esVenta) R.string.detalle_finalizar_confirm_titulo_venta else R.string.detalle_finalizar_confirm_titulo
        MaterialAlertDialogBuilder(this)
            .setTitle(tituloConfirm)
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
            .setTitle(R.string.perfil_pausar_publicacion)
            .setMessage(R.string.perfil_pausar_mensaje)
            .setPositiveButton(R.string.perfil_pausar_publicacion) { _, _ ->
                db.collection("propiedades").document(p.id)
                    .set(mapOf("estado" to "pausada"), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Publicación pausada", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(R.string.cancelar, null)
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
                val dias = when (which) { 0 -> 7; 1 -> 15; else -> 30 }
                val precio = when (which) { 0 -> 6.90; 1 -> 12.90; else -> 24.90 }
                val sku = when (dias) {
                    7 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_7D
                    15 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_15D
                    else -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_30D
                }

                db.collection("propiedades").document(p.id)
                    .set(mapOf(
                        "solicitudDestacar" to true,
                        "destacadoDias" to dias,
                        "destacadoPrecio" to precio,
                        "destacadoSku" to sku,
                        "solicitudDestacarEn" to FieldValue.serverTimestamp()
                    ), SetOptions.merge())
                    .addOnSuccessListener {
                        billingManager.launchPurchaseFlow(sku, object : com.alkilapp.billing.BillingManager.PurchaseCallback {
                            override fun onSuccess(productId: String, purchaseToken: String, orderId: String?) {
                                val hasta = Date(System.currentTimeMillis() + dias * 24L * 3600 * 1000)
                                val datosDestacado = HashMap<String, Any>().apply {
                                    put("isFeatured", true)
                                    put("featuredUntil", hasta)
                                    put("destacadoDias", dias)
                                    put("destacadoDiasRestantes", dias)
                                    put("destacadoEstado", "aprobado")
                                    put("solicitudDestacar", false)
                                    put("destacadoAprobadoEn", FieldValue.serverTimestamp())
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
        if (auth.currentUser!!.isEmailVerified != true) {
            Toast.makeText(this, R.string.auth_email_no_verificado_chat, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, VerificacionEmailActivity::class.java))
            return
        }
        if (listingId.isBlank() || uid.isBlank()) return
        val chatId = "inm-$listingId-$miUid"
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
                        putExtra(ChatListActivity.EXTRA_LISTING_ID, listingId)
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
