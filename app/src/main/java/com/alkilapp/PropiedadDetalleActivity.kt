package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityPropiedadDetalleBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PropiedadDetalleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPropiedadDetalleBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private val cargadorImagenes = Executors.newSingleThreadExecutor()
    private val handlerUi = Handler(Looper.getMainLooper())

    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

    private var fotos: List<String> = emptyList()
    private var fotosUrl: List<String> = emptyList()
    private var propId: String = ""
    private var idPropietario: String = ""
    private var listingTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPropiedadDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manejar deep link: alkilapp://propiedad/{propId}
        val deepLinkId = intent.data?.lastPathSegment
        if (deepLinkId != null && deepLinkId.isNotBlank()) {
            propId = deepLinkId
            // Cargar el resto desde Firestore
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
        fotos = if (fotosArray != null) fotosArray.toList() else intent.getStringArrayListExtra(EXTRA_FOTOS) ?: emptyList()
        fotosUrl = if (fotosUrlArray != null) fotosUrlArray.toList() else intent.getStringArrayListExtra(EXTRA_FOTOS_URL) ?: emptyList()
        val isFeatured = intent.getBooleanExtra(EXTRA_FEATURED, false)

        binding.btnDetalleBack.setOnClickListener { finish() }
        binding.btnDetalleCompartir.setOnClickListener { compartirPublicacion() }

        // Información principal
        binding.tvDetTitulo.text = listingTitle
        binding.tvDetTipo.text = tipoMostrable(intent.getStringExtra(EXTRA_TIPO).orEmpty())
        binding.tvDetDireccion.text = direccionCompleta()
        binding.tvDetPrecio.text = formatearPrecio(
            intent.getDoubleExtra(EXTRA_PRECIO, 0.0),
            moneda,
            op
        )
        // Etiqueta de operación (Venta/Alquiler)
        binding.tvDetOperacion.text = when (op) {
            "venta" -> getString(R.string.detalle_operacion_venta)
            "alquiler" -> getString(R.string.detalle_operacion_alquiler)
            else -> getString(R.string.detalle_operacion_desconocida)
        }
        binding.tvDetOperacion.visibility = View.VISIBLE

        binding.tvDetallePill.visibility = if (isFeatured) View.VISIBLE else View.GONE

        val (estadoLabel, estadoText, estadoFondo) = when (estado) {
            "disponible" -> Triple(
                getString(R.string.prop_estado_disponible), R.color.success_text, R.color.alkil_mint_soft
            )
            "finalizado" -> Triple(
                getString(R.string.prop_estado_finalizado), R.color.text_secondary, R.color.alkil_gray_soft
            )
            "pausada" -> Triple(
                getString(R.string.prop_estado_pausada), R.color.text_secondary, R.color.alkil_gray_soft
            )
            else -> Triple(
                getString(R.string.prop_estado_revision), R.color.gold_text, R.color.alkil_gold_soft
            )
        }
        binding.tvDetEstado.text = estadoLabel
        binding.tvDetEstado.setTextColor(getColor(estadoText))
        binding.tvDetEstado.backgroundTintList = ColorStateList.valueOf(getColor(estadoFondo))

        // Chips de info: ambientes / superficie
        val ambientes = intent.getIntExtra(EXTRA_AMBIENTES, 0)
        val superficie = intent.getDoubleExtra(EXTRA_SUPERFICIE, 0.0)
        binding.tvDetAmbientes.visibility =
            if (ambientes > 0) View.VISIBLE else View.GONE
        if (ambientes > 0) binding.tvDetAmbientes.text = getString(R.string.detalle_amb, ambientes)
        binding.tvDetSuperficie.visibility =
            if (superficie > 0) View.VISIBLE else View.GONE
        if (superficie > 0) {
            binding.tvDetSuperficie.text =
                getString(R.string.detalle_superficie, superficie.toInt().toString())
        }

        val comodidades = intent.getStringArrayListExtra(EXTRA_COMODIDADES) ?: emptyList()
        binding.tvDetComodidades.visibility =
            if (comodidades.isNotEmpty()) View.VISIBLE else View.GONE
        if (comodidades.isNotEmpty()) {
            binding.tvDetComodidades.text = getString(
                R.string.detalle_comodidades,
                comodidades.joinToString(" · ")
            )
        }

        val descripcion = intent.getStringExtra(EXTRA_DESCRIPCION).orEmpty()
        binding.tvDetDescripcion.visibility =
            if (descripcion.isNotBlank()) View.VISIBLE else View.GONE
        binding.tvDetDescripcion.text = descripcion
        binding.cardDetAcerca.visibility = if (comodidades.isNotEmpty() || descripcion.isNotBlank()) {
            View.VISIBLE
        } else View.GONE

        // Galería (las fotos base64 ya no viajan por el intent: superaban el
        // límite de Binder y causaban TransactionTooLargeException. Se cargan por ID.)
        if (fotos.isEmpty() && fotosUrl.isEmpty() && propId.isNotBlank()) {
            cargarFotosDesdeFirestore()
        } else {
            armarGaleria()
        }
        binding.ivPreview.setOnClickListener { abrirZoomFoto(indiceActual) }

        // Ubicación en el mapa
        binding.btnVerMapa.setOnClickListener { abrirUbicacionMapa() }
        binding.btnVerMapa.visibility =
            if (intent.getDoubleExtra(EXTRA_LAT, 0.0) == 0.0 &&
                intent.getDoubleExtra(EXTRA_LNG, 0.0) == 0.0
            ) View.GONE else View.VISIBLE

        // Chat con el propietario
        binding.btnChatPropietario.setOnClickListener { abrirChatPropietario() }
        val miUid = auth.currentUser?.uid
        val esMio = idPropietario.isNotBlank() && miUid == idPropietario
        if (esMio) {
            binding.btnChatPropietario.visibility = View.GONE
        }

        // Denunciar el anuncio
        binding.btnDenunciar.setOnClickListener { abrirDenuncia() }
        if (esMio) {
            binding.btnDenunciar.visibility = View.GONE
        }

        // Publicación finalizada: banner de aviso y sin botón de chat
        if (estado == "finalizado") {
            binding.tvDetFinalizado.visibility = View.VISIBLE
            binding.btnChatPropietario.visibility = View.GONE
        }

        // Publicación en revisión (por admin): banner de aviso, no se puede reactivar desde la app
        if (estado == "under_review") {
            binding.tvDetRevision.visibility = View.VISIBLE
        }

        // Publicación pausada (por usuario): se muestra como disponible para el dueño pero sin chat
        if (estado == "pausada") {
            binding.tvDetPausada.visibility = View.VISIBLE
            binding.btnChatPropietario.visibility = View.GONE
        }

        // Finalizar la publicación (solo el dueño y mientras esté disponible o pausada)
        binding.btnFinalizarPub.visibility =
            if (esMio && (estado == "disponible" || estado == "pausada")) View.VISIBLE else View.GONE
        binding.btnFinalizarPub.setOnClickListener { confirmarFinalizar() }

        // Tarjeta del propietario → perfil completo
        binding.cardPropietario.setOnClickListener {
            abrirPerfilPropietario()
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
                Toast.makeText(this, "Error en datos de la publicación", Toast.LENGTH_LONG).show()
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
            fotos = p.fotos
            fotosUrl = p.photosUrl ?: emptyList()
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

    private val fuentes = mutableListOf<String>()
    private var indiceActual = 0

    /** Una imagen de la galería: su fuente original (base64 o URL) y el bitmap ya
     *  decodificado en el caso de base64 (la URL se descarga aparte). */
    private class FotoLista(val fuente: String, val bmp: Bitmap?)

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
        binding.llThumbs.removeAllViews()
        binding.tvSinFotos.visibility = View.GONE
        val items = mutableListOf<FotoLista>()
        for (foto in fotos) {
            items.add(FotoLista("b64:$foto", decodificarBase64(foto)))
        }
        for (url in fotosUrl) {
            if (url.isNotBlank()) items.add(FotoLista(url, null))
        }
        val validos = items.filter { it.bmp != null || !it.fuente.startsWith("b64:") }
        if (validos.isEmpty()) {
            binding.tvSinFotos.visibility = View.VISIBLE
            return
        }

        fuentes.addAll(validos.map { it.fuente })
        var pendientes = validos.size
        val marcarProgreso = {
            pendientes--
            if (pendientes <= 0) {
                binding.hsvThumbs.visibility =
                    if (binding.llThumbs.childCount > 1) View.VISIBLE else View.GONE
                if (binding.llThumbs.childCount == 0) binding.tvSinFotos.visibility = View.VISIBLE
            }
        }

        validos.forEachIndexed { indice, foto ->
            if (foto.bmp != null) {
                agregarImagenGaleria(foto.bmp!!, indice)
                marcarProgreso()
            } else {
                cargarUrl(foto.fuente, indice, marcarProgreso)
            }
        }
    }

    private fun agregarImagenGaleria(bmp: Bitmap, indice: Int) {
        if (indice == 0) {
            binding.ivPreview.setImageBitmap(bmp)
            binding.ivPreview.background = ColorDrawable(Color.TRANSPARENT)
        }
        val madre = 56.dp
        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(madre, madre).apply {
                marginEnd = 8.dp
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bmp)
            setBackgroundResource(R.drawable.bg_thumb_vacia)
            isClickable = true
            alpha = if (indice == 0) 1.0f else 0.6f
            setOnClickListener {
                binding.ivPreview.setImageBitmap(bmp)
                binding.ivPreview.background = ColorDrawable(Color.TRANSPARENT)
                indiceActual = indice
                actualizarIndice(indice)
            }
        }
        binding.llThumbs.addView(thumb)
    }

    private fun cargarUrl(url: String, indice: Int, alTerminar: () -> Unit) {
        cargadorImagenes.execute {
            val bmp = descargarBitmap(url)
            handlerUi.post {
                if (bmp != null) agregarImagenGaleria(bmp, indice)
                alTerminar()
            }
        }
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

    private fun decodificarBase64(foto: String): Bitmap? {
        val bytes = try {
            Base64.decode(foto, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun actualizarIndice(seleccionado: Int) {
        for (i in 0 until binding.llThumbs.childCount) {
            binding.llThumbs.getChildAt(i).alpha = if (i == seleccionado) 1.0f else 0.6f
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
        if (idPropietario.isBlank() || propId.isBlank()) return

        // Chat determinístico por inmueble Y usuario: cada usuario tiene su propio chat con el propietario.
        // Se crea con SET MERGE (no leemos antes: en un doc inexistente el get daba
        // PERMISSION_DENIED y el chat "no abría"). En un chat existente el merge no
        // pisa lastMessage/unreadCount.
        val chatId = "inm-$propId-$miUid"
        db.collection("chats").document(chatId)
            .set(
                hashMapOf(
                    "chatId" to chatId,
                    "listingId" to propId,
                    "listingTitle" to listingTitle,
                    "participants" to listOf(miUid, idPropietario)
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
        val tituloConfirm = if (esVenta) R.string.detalle_finalizar_confirm_titulo_venta else R.string.detalle_finalizar_confirm_titulo
        val bannerFinalizado = if (esVenta) R.string.detalle_finalizado_banner_venta else R.string.detalle_finalizado_banner_alquiler
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(tituloConfirm)
            .setMessage(R.string.detalle_finalizar_confirm_msg)
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .setPositiveButton(R.string.detalle_finalizar_si) { _, _ ->
                db.collection("propiedades").document(propId)
                    .update("estado", "finalizado")
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.detalle_finalizar_ok, Toast.LENGTH_SHORT).show()
                        binding.btnFinalizarPub.visibility = View.GONE
                        binding.tvDetFinalizado.text = getString(bannerFinalizado)
                        binding.tvDetFinalizado.visibility = View.VISIBLE
                        binding.btnChatPropietario.visibility = View.GONE
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
        spMotivo.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.motivos_denuncia)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

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
            binding.cardPropietario.visibility = View.VISIBLE
            binding.tvDetAvatar.text = perfil.inicial.toString()
            binding.tvDetAvatar.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    getColor(coloresAvatar[Math.floorMod(idPropietario.hashCode(), coloresAvatar.size)])
                )
            binding.tvDetOwnerName.text = perfil.nombre
            binding.ivDetVerificado.visibility =
                if (perfil.verificado) View.VISIBLE else View.GONE
            binding.tvDetVerificadoLinea.visibility =
                if (perfil.verificado) View.VISIBLE else View.GONE
            if (perfil.rating > 0) {
                binding.tvDetOwnerRating.text = String.format("%.1f", perfil.rating)
                binding.tvDetOwnerRating.visibility = View.VISIBLE
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

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
