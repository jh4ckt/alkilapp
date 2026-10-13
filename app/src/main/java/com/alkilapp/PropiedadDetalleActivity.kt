package com.alkilapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.DetalleUiState
import com.alkilapp.compose.EstadoPropietario
import com.alkilapp.compose.PantallaDetalle
import com.alkilapp.compose.cargarFotoDetalle
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.data.Propiedad
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class PropiedadDetalleActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private val cargadorImagenes = Executors.newSingleThreadExecutor()

    private val coloresAvatar = arrayOf(
        "#7E57C2", "#26A69A", "#EF6C00", "#42A5F5", "#EC407A"
    )

    private var fotos: List<String> = emptyList()
    private var fotosUrl: List<String> = emptyList()
    private var propId: String = ""
    private var idPropietario: String = ""
    private var listingTitle: String = ""

    private var ui by mutableStateOf(DetalleUiState())
    private var propietario by mutableStateOf<EstadoPropietario?>(null)
    private var preview by mutableStateOf<Bitmap?>(null)
    private var thumbs by mutableStateOf<List<Bitmap?>>(emptyList())
    private var indiceActual by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlkilTheme {
                PantallaDetalle(
                    ui = ui,
                    propietario = propietario,
                    preview = preview,
                    thumbs = thumbs,
                    indiceActual = indiceActual,
                    onAtras = { finish() },
                    onCompartir = { compartirPublicacion() },
                    onFotoZoom = { abrirZoomFoto(it) },
                    onThumb = { i ->
                        indiceActual = i
                        thumbs.getOrNull(i)?.let { preview = it }
                    },
                    onVerMapa = { abrirUbicacionMapa() },
                    onChat = { abrirChatPropietario() },
                    onDenunciar = { abrirDenuncia() },
                    onFinalizar = { confirmarFinalizar() },
                    onPerfilPropietario = { abrirPerfilPropietario() }
                )
            }
        }

        // Manejar deep link: alkilapp://propiedad/{propId}
        val deepLinkId = intent.data?.lastPathSegment
        if (deepLinkId != null && deepLinkId.isNotBlank()) {
            propId = deepLinkId
            cargarDesdeFirestore(propId)
            return
        }

        propId = intent.getStringExtra(EXTRA_ID).orEmpty()
        listingTitle = intent.getStringExtra(EXTRA_TITULO).orEmpty()
        idPropietario = intent.getStringExtra(EXTRA_ID_PROPIETARIO).orEmpty()
        if (propId.isBlank()) {
            Log.e("AlkilApp", "PropiedadDetalleActivity: EXTRA_ID vacío, cerrando")
            Toast.makeText(this, "Error: ID de propiedad no recibido", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        cargarDatosDesdeIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        cargadorImagenes.shutdown()
    }

    private fun cargarDatosDesdeIntent() {
        listingTitle = intent.getStringExtra(EXTRA_TITULO).orEmpty()
        idPropietario = intent.getStringExtra(EXTRA_ID_PROPIETARIO).orEmpty()
        val estado = when (val e = intent.getStringExtra(EXTRA_ESTADO)?.trim()?.lowercase()) {
            null, "", "publicado", "activo" -> "disponible"
            else -> e
        }
        val moneda = intent.getStringExtra(EXTRA_MONEDA) ?: "USD"
        val op = intent.getStringExtra(EXTRA_OPERACION) ?: "alquiler"

        // Recibir arrays de strings (pueden venir como String[] o ArrayList<String>)
        val fotosArray = intent.getStringArrayExtra(EXTRA_FOTOS)
        val fotosUrlArray = intent.getStringArrayExtra(EXTRA_FOTOS_URL)
        fotos =
            if (fotosArray != null) fotosArray.toList() else intent.getStringArrayListExtra(
                EXTRA_FOTOS
            ) ?: emptyList()
        fotosUrl =
            if (fotosUrlArray != null) fotosUrlArray.toList() else intent.getStringArrayListExtra(
                EXTRA_FOTOS_URL
            ) ?: emptyList()
        val isFeatured = intent.getBooleanExtra(EXTRA_FEATURED, false)

        val miUid = auth.currentUser?.uid
        val esMio = idPropietario.isNotBlank() && miUid == idPropietario

        ui = DetalleUiState(
            propId = propId,
            titulo = listingTitle,
            tipo = tipoMostrable(intent.getStringExtra(EXTRA_TIPO).orEmpty()),
            direccion = direccionCompleta(),
            precioTexto = formatearPrecio(
                intent.getDoubleExtra(EXTRA_PRECIO, 0.0),
                moneda,
                op
            ),
            operacion = when (op) {
                "venta" -> getString(R.string.detalle_operacion_venta)
                "alquiler" -> getString(R.string.detalle_operacion_alquiler)
                else -> getString(R.string.detalle_operacion_desconocida)
            },
            descripcion = intent.getStringExtra(EXTRA_DESCRIPCION).orEmpty(),
            comodidades = intent.getStringArrayListExtra(EXTRA_COMODIDADES) ?: emptyList(),
            ambientes = intent.getIntExtra(EXTRA_AMBIENTES, 0),
            superficie = intent.getDoubleExtra(EXTRA_SUPERFICIE, 0.0),
            estado = estado,
            estadoFinalizadoTexto = if (estado == "finalizado") {
                if (Propiedad.normalizarOperacion(op) == "venta") {
                    getString(R.string.detalle_finalizado_banner_venta)
                } else {
                    getString(R.string.detalle_finalizado_banner_alquiler)
                }
            } else null,
            destacado = isFeatured,
            lat = intent.getDoubleExtra(EXTRA_LAT, 0.0),
            lng = intent.getDoubleExtra(EXTRA_LNG, 0.0),
            idPropietario = idPropietario,
            esMio = esMio,
            hayFotos = fotos.isNotEmpty() || fotosUrl.isNotEmpty()
        )

        // Galería (las fotos base64 ya no viajan por el intent: superaban el
        // límite de Binder y causaban TransactionTooLargeException. Se cargan por ID.)
        if (fotos.isEmpty() && fotosUrl.isEmpty() && propId.isNotBlank()) {
            cargarFotosDesdeFirestore()
        } else {
            armarGaleria()
        }

        cargarPropietario()
    }

    private fun tipoMostrable(tipo: String): String = when (tipo.trim().lowercase()) {
        "habitacion", "habitación", "cuarto" -> "HABITACIÓN"
        "departamento", "depto" -> "DEPARTAMENTO"
        "casa" -> "CASA"
        else -> tipo.ifBlank { "INMUEBLE" }.uppercase()
    }

    private fun cargarDesdeFirestore(propId: String) {
        db.collection("propiedades").document(propId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.e("AlkilApp", "Propiedad $propId no existe")
                    Toast.makeText(this, "La publicación no existe", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                val p = Propiedad.desde(doc) ?: run {
                    Log.e("AlkilApp", "Propiedad $propId no se pudo parsear")
                    Toast.makeText(this, "Error en datos de la publicación", Toast.LENGTH_LONG)
                        .show()
                    finish()
                    return@addOnSuccessListener
                }
                // Setear datos como si vinieran del intent
                listingTitle = p.titulo
                idPropietario = p.idPropietario

                fotos = p.fotos
                fotosUrl = p.photosUrl ?: emptyList()

                // Deep link: se inyectan los datos en el intent REAL de la Activity y se
                // delega en el MISMO binder que usa el listado. Antes se armaba un
                // `intentMock` que se descartaba y luego se llamaba a un binder paralelo
                // que leia `intent`: por eso el deep link abria con precio 0 y sin
                // descripcion/comodidades/mapa.
                intent.putExtra(EXTRA_TITULO, p.titulo)
                intent.putExtra(EXTRA_DESCRIPCION, p.descripcion)
                intent.putExtra(EXTRA_TIPO, p.tipo)
                intent.putExtra(EXTRA_OPERACION, p.operacion)
                intent.putExtra(EXTRA_PRECIO, p.precio)
                intent.putExtra(EXTRA_MONEDA, p.moneda)
                intent.putExtra(EXTRA_DIRECCION, p.direccion)
                intent.putExtra(EXTRA_BARRIO, p.barrio)
                intent.putExtra(EXTRA_CIUDAD, p.ciudad)
                intent.putExtra(EXTRA_LAT, p.lat)
                intent.putExtra(EXTRA_LNG, p.lng)
                intent.putExtra(EXTRA_ID_PROPIETARIO, p.idPropietario)
                intent.putExtra(EXTRA_AMBIENTES, p.ambientes)
                intent.putExtra(EXTRA_SUPERFICIE, p.superficieM2)
                // getStringArrayListExtra (el binder) NO lee un String[]: por eso las
                // comodidades nunca aparecian al abrir un anuncio.
                intent.putStringArrayListExtra(EXTRA_COMODIDADES, ArrayList(p.comodidades))
                intent.putExtra(EXTRA_FEATURED, p.esDestacado)
                intent.putExtra(EXTRA_ESTADO, p.estado)
                // Unico punto de entrada para pintar la UI.
                cargarDatosDesdeIntent()
            }
            .addOnFailureListener { e ->
                Log.e("AlkilApp", "Error cargando propiedad $propId: ${e.message}")
                Toast.makeText(this, "Error cargando la publicación", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun direccionCompleta(): String {
        val dir = intent.getStringExtra(EXTRA_DIRECCION).orEmpty()
        val barrio = intent.getStringExtra(EXTRA_BARRIO).orEmpty()
        val ciudad = intent.getStringExtra(EXTRA_CIUDAD).orEmpty()
        return listOf(dir, barrio, ciudad)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }

    private fun formatearPrecio(precio: Double, moneda: String, op: String): String {
        if (precio <= 0) return ""
        val monto = if (precio == precio.toLong().toDouble()) {
            precio.toLong().toString()
        } else {
            precio.toString()
        }
        val simbolo = if (moneda == "PEN") "S/ " else "$ "
        return if (Propiedad.normalizarOperacion(op) == "venta") "$simbolo$monto" else "$simbolo$monto / mes"
    }

    // ======================================================================
    // Galería (base64 de la app + URLs del backend)
    // ======================================================================

    private val fuentes = mutableListOf<Pair<String, Boolean>>()

    /** Carga las fotos del inmueble desde Firestore usando solo el ID, evitando
     *  enviar base64 (potencialmente >1MB) por el intent. */
    private fun cargarFotosDesdeFirestore() {
        db.collection("propiedades").document(propId).get()
            .addOnSuccessListener { doc ->
                val d = doc.data
                fotos = (d?.get("fotos") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                fotosUrl = (d?.get("photos") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                armarGaleria()
            }
            .addOnFailureListener { armarGaleria() }
    }

    private fun armarGaleria() {
        fuentes.clear()
        thumbs = emptyList()
        preview = null
        indiceActual = 0

        val items = mutableListOf<Pair<String, Boolean>>()
        for (foto in fotos) {
            if (foto.isNotBlank()) items.add(foto to true)
        }
        for (url in fotosUrl) {
            if (url.isNotBlank()) items.add(url to false)
        }
        if (items.isEmpty()) return

        fuentes.addAll(items)
        lifecycleScope.launch {
            val bitmaps = items.map { (fuente, esBase64) ->
                cargarFotoDetalle(fuente, esBase64)
            }
            val visibles = bitmaps.filterNotNull()
            thumbs = visibles
            preview = visibles.firstOrNull()
            indiceActual = 0
        }
    }

    private fun abrirZoomFoto(posicion: Int) {
        if (fuentes.isEmpty() || propId.isBlank()) return
        startActivity(
            Intent(this, FotoZoomActivity::class.java).apply {
                putExtra(FotoZoomActivity.EXTRA_PROPIEDAD_ID, propId)
                putExtra(
                    FotoZoomActivity.EXTRA_POSICION,
                    posicion.coerceIn(0, fuentes.size - 1)
                )
            }
        )
    }

    // ======================================================================
    // Chat con el propietario
    // ======================================================================

    private fun abrirChatPropietario() {
        val miUid = auth.currentUser?.uid
        if (miUid == null) {
            Toast.makeText(this, R.string.detalle_chatear_sesion, Toast.LENGTH_SHORT).show()
            return
        }
        if (auth.currentUser!!.isEmailVerified != true) {
            Toast.makeText(this, R.string.auth_email_no_verificado_chat, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, VerificacionEmailActivity::class.java))
            return
        }
        if (propId.isBlank()) {
            Toast.makeText(this, R.string.detalle_chat_error_datos, Toast.LENGTH_LONG).show()
            return
        }

        // Algunas publicaciones antiguas pueden abrirse sin idPropietario en el Intent.
        // Recargarlo evita que el CTA falle silenciosamente en ese caso.
        if (idPropietario.isBlank()) {
            db.collection("propiedades").document(propId).get()
                .addOnSuccessListener { doc ->
                    idPropietario = doc.getString("idPropietario").orEmpty()
                    if (idPropietario.isBlank()) {
                        Toast.makeText(this, R.string.detalle_chat_error_datos, Toast.LENGTH_LONG).show()
                    } else {
                        crearChatPropietario(miUid)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.detalle_chat_error, e.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            return
        }

        crearChatPropietario(miUid)
    }

    private fun crearChatPropietario(miUid: String) {
        Log.d("AlkilApp", "crearChatPropietario listing=$propId owner=$idPropietario user=$miUid")

        // Chat determinístico por inmueble Y usuario: cada usuario tiene su propio chat con el propietario.
        // Se crea con SET MERGE (no leemos antes: en un doc inexistente el get daba
        // PERMISSION_DENIED y el chat "no abría"). En un chat existente el merge no
        // pisa lastMessage/unreadCount.
        val chatId = "inm-$propId-$miUid"
        val ahora = System.currentTimeMillis()
        db.collection("chats").document(chatId)
            .set(
                hashMapOf(
                    "chatId" to chatId,
                    "listingId" to propId,
                    "listingTitle" to listingTitle,
                    "participants" to listOf(miUid, idPropietario),
                    "creatorId" to miUid,
                    "creadoEn" to ahora
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { abrirConversacion(chatId) }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.detalle_chat_error, e.localizedMessage ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ======================================================================
    // Ubicación en el mapa
    // ======================================================================

    private fun abrirUbicacionMapa() {
        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(this, R.string.ubicacion_sin_coordenadas, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, UbicacionInmuebleActivity::class.java).apply {
                putExtra(UbicacionInmuebleActivity.EXTRA_LAT, lat)
                putExtra(UbicacionInmuebleActivity.EXTRA_LNG, lng)
                putExtra(
                    UbicacionInmuebleActivity.EXTRA_TITULO,
                    direccionCompleta()
                )
            }
        )
    }

    private fun abrirConversacion(chatId: String) {
        val otro = if (auth.currentUser?.uid == idPropietario) idPropietario else {
            listOf(idPropietario, auth.currentUser?.uid)
                .filterNotNull()
                .filter { it != auth.currentUser?.uid }
                .firstOrNull() ?: idPropietario
        }
        startActivity(
            Intent(this, ChatDetailActivity::class.java).apply {
                putExtra(ChatListActivity.EXTRA_CHAT_ID, chatId)
                putExtra(ChatListActivity.EXTRA_LISTING, listingTitle)
                putExtra(ChatListActivity.EXTRA_LISTING_ID, propId)
                putExtra(ChatListActivity.EXTRA_OTRO_UID, otro)
            }
        )
    }

    // ======================================================================
    // Finalizar publicación (estado -> "finalizado", solo el dueño)
    // ======================================================================

    private fun confirmarFinalizar() {
        if (propId.isBlank()) return
        val op = intent.getStringExtra(EXTRA_OPERACION) ?: "alquiler"
        val esVenta = Propiedad.normalizarOperacion(op) == "venta"
        val tituloConfirm =
            if (esVenta) R.string.detalle_finalizar_confirm_titulo_venta else R.string.detalle_finalizar_confirm_titulo
        val bannerFinalizado =
            if (esVenta) R.string.detalle_finalizado_banner_venta else R.string.detalle_finalizado_banner_alquiler
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(tituloConfirm)
            .setMessage(R.string.detalle_finalizar_confirm_msg)
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .setPositiveButton(R.string.detalle_finalizar_si) { _, _ ->
                db.collection("propiedades").document(propId)
                    .update("estado", "finalizado")
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.detalle_finalizar_ok, Toast.LENGTH_SHORT)
                            .show()
                        ui = ui.copy(
                            estado = "finalizado",
                            estadoFinalizadoTexto = getString(bannerFinalizado)
                        )
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

    // ======================================================================
    // Denunciar anuncio (colección "reports"; la moderación es backend)
    // ======================================================================

    private fun abrirDenuncia() {
        val miUid = auth.currentUser?.uid
        if (miUid == null) {
            Toast.makeText(this, R.string.denuncia_sesion, Toast.LENGTH_SHORT).show()
            return
        }
        if (propId.isBlank()) return

        val vista = layoutInflater.inflate(R.layout.dialog_denuncia, null)
        val spMotivo = vista.findViewById<android.widget.Spinner>(R.id.spDenunciaMotivo)
        val etDetalle = vista.findViewById<android.widget.EditText>(R.id.etDenunciaDetalle)
        spMotivo.estilizar(resources.getStringArray(R.array.motivos_denuncia).toList())

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.denuncia_titulo)
            .setView(vista)
            .setNegativeButton(R.string.denuncia_cancelar, null)
            .setPositiveButton(R.string.denuncia_enviar) { _, _ ->
                val motivo = spMotivo.selectedItem.toString()
                val detalle = etDetalle.text.toString().trim()
                db.collection("reports").add(
                    hashMapOf(
                        "listingId" to propId,
                        "reporterId" to miUid,
                        "motivo" to motivo,
                        "detalle" to detalle,
                        "chapter" to "reports",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.denuncia_ok, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.denuncia_error, e.localizedMessage ?: "?"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    // ======================================================================
    // Perfil del propietario
    // ======================================================================

    private fun abrirPerfilPropietario() {
        if (idPropietario.isBlank()) return
        startActivity(
            Intent(this, PerfilPropietarioActivity::class.java).apply {
                putExtra(PerfilPropietarioActivity.EXTRA_UID, idPropietario)
                putExtra(PerfilPropietarioActivity.EXTRA_LISTING_ID, propId)
                putExtra(PerfilPropietarioActivity.EXTRA_LISTING_TITULO, listingTitle)
            }
        )
    }

    // ======================================================================
    // Propietario (perfil en "usuarios")
    // ======================================================================

    private fun cargarPropietario() {
        if (idPropietario.isBlank()) return
        PerfilUsuario.buscar(db, idPropietario) { perfil ->
            if (perfil == null) return@buscar
            propietario = EstadoPropietario(
                nombre = perfil.nombre,
                inicial = perfil.inicial,
                verificado = perfil.verificado,
                rating = perfil.rating,
                colorAvatar = Color(
                    coloresAvatar[Math.floorMod(idPropietario.hashCode(), coloresAvatar.size)]
                        .toColorInt()
                )
            )
        }
    }

    private fun compartirPublicacion() {
        val deepLink = "alkilapp://propiedad/$propId"
        val titulo = getString(R.string.detalle_compartir_titulo, listingTitle)
        val texto = getString(R.string.detalle_compartir_texto, deepLink)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, titulo)
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        val chooser = Intent.createChooser(intent, getString(R.string.detalle_compartir))
        try {
            startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(this, "No hay apps para compartir", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_ID = "det_id"
        const val EXTRA_TITULO = "det_titulo"
        const val EXTRA_DESCRIPCION = "det_descripcion"
        const val EXTRA_TIPO = "det_tipo"
        const val EXTRA_OPERACION = "det_operacion"
        const val EXTRA_PRECIO = "det_precio"
        const val EXTRA_MONEDA = "det_moneda"
        const val EXTRA_DIRECCION = "det_direccion"
        const val EXTRA_BARRIO = "det_barrio"
        const val EXTRA_CIUDAD = "det_ciudad"
        const val EXTRA_LAT = "det_lat"
        const val EXTRA_LNG = "det_lng"
        const val EXTRA_ID_PROPIETARIO = "det_id_propietario"
        const val EXTRA_AMBIENTES = "det_ambientes"
        const val EXTRA_SUPERFICIE = "det_superficie"
        const val EXTRA_COMODIDADES = "det_comodidades"
        const val EXTRA_FOTOS = "det_fotos"
        const val EXTRA_FOTOS_URL = "det_fotos_url"
        const val EXTRA_FEATURED = "det_featured"
        const val EXTRA_ESTADO = "det_estado"
    }
}
