package com.alkilapp

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Mensaje
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.data.TipoMensaje
import com.alkilapp.databinding.ActivityChatDetailBinding
import com.alkilapp.ui.MensajeAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.WriteBatch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ChatDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatDetailBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaMensajes: ListenerRegistration? = null
    private var escuchaChat: ListenerRegistration? = null
    private var escuchaInmueble: ListenerRegistration? = null

    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

private var chatId: String = ""
    private var otroUid: String = ""
    private var listingId: String = ""
    private var idPropietarioDelInmueble: String = ""

    // --- Anti-spam -------------------------------------------------------
    // 1) El MISMO texto no se puede reenviar dentro de 30 s (evita el doble tap).
    // 2) Si una palabra se repite mas de MAX_REPETICIONES veces seguidas, el chat
    //    queda bloqueado durante BLOQUEO_SPAM_MS (5 min) para cortar el spam.
    private var ultimoMensajeEnviado: String = ""
    private var ultimoMensajeTimestamp: Long = 0
    private val DEBOUNCE_MS = 30_000L
    private val BLOQUEO_SPAM_MS = 5 * 60 * 1000L
    private val VENTANA_ANTISPAM = 12
    private val MAX_REPETICIONES = 5
    private val historialEnviado = ArrayList<Pair<Long, String>>()
    private var bloqueoSpamHasta: Long = 0
    private var chatCerradoPorInmueble: Boolean = false
    private val handlerUI = Handler(Looper.getMainLooper())
    private val MAX_RECEIPTOS = 400

    private val adapter by lazy { MensajeAdapter(
        miUid = auth.currentUser?.uid ?: "",
        onAceptar = { m -> aceptarCita(m) },
        onRechazar = { m -> rechazarCita(m) }
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra(ChatListActivity.EXTRA_CHAT_ID).orEmpty()
        otroUid = intent.getStringExtra(ChatListActivity.EXTRA_OTRO_UID).orEmpty()
        listingId = intent.getStringExtra(ChatListActivity.EXTRA_LISTING_ID).orEmpty()
            .ifBlank { chatId.removePrefix("inm-") }
        binding.tvChatListing.text = intent.getStringExtra(ChatListActivity.EXTRA_LISTING)

        binding.btnChatDetailBack.setOnClickListener { finish() }
        binding.btnChatMenu.setOnClickListener { mostrarMenuOpciones() }
        binding.btnSolicitarCita.setOnClickListener { mostrarDialogoCita() }
        binding.btnEnviar.setOnClickListener { enviarMensaje() }
        binding.etEntrada.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                enviarMensaje()
                true
            } else false
        }

        binding.rvMensajes.layoutManager = LinearLayoutManager(this)
        binding.rvMensajes.adapter = adapter

        if (otroUid.isNotBlank()) cargarPerfil(otroUid)

        // Obtener idPropietario del inmueble para controlar visibilidad del botón "Solicitar alquiler"
        if (listingId.isNotBlank()) {
            db.collection("propiedades").document(listingId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        idPropietarioDelInmueble = doc.getString("idPropietario") ?: ""
                        // Obtener precio y moneda para mostrar en encabezado
                        val precio = (doc.getDouble("precio") ?: 0.0)
                        val moneda = (doc.getString("moneda") ?: "PEN")
                        val op = (doc.getString("operacion") ?: "alquiler")
                        val precioFormateado = formatearPrecioChat(precio, moneda, op)
                        binding.tvChatPrecio.text = precioFormateado
                        binding.tvChatPrecio.visibility = View.VISIBLE
                        actualizarBotonPropuesta()
                    }
                }
            vigilarEstadoInmueble()
        }
    }

    /**
     * Si la publicacion queda finalizada/alquilada (estado "finalizado"),
     * el chat se cierra para el solicitante: se muestra el aviso y se
     * deshabilita la barra de escritura para no permitir mas comunicacion.
     */
    private fun vigilarEstadoInmueble() {
        // Se guarda la suscripcion: sin remove() en onStop el listener seguia
        // vivo con la pantalla fuera y el usuario dentro del chat.
        escuchaInmueble?.remove()
        escuchaInmueble = db.collection("propiedades").document(listingId)
            .addSnapshotListener { snap, _ ->
                if (snap?.exists() == true) {
                    val estado = (snap.data?.get("estado") as? String)?.trim()?.lowercase().orEmpty()
                    val cerrado = estado == "finalizado"
                    chatCerradoPorInmueble = cerrado
                    binding.cardChatCerrado.visibility = if (cerrado) View.VISIBLE else View.GONE
                    actualizarEntrada()
                    actualizarBotonPropuesta()
                }
            }
    }

    /** Controla visibilidad del botón "Solicitar alquiler":
     * - Solo visible si NO soy el propietario del inmueble
     * - Chat estado == "abierto" (no "cerrado" ni "acuerdo_cerrado")
     * - Propiedad no está "finalizado"
     */
    private fun actualizarBotonPropuesta() {
        val miUid = auth.currentUser?.uid ?: run { binding.btnSolicitarCita.visibility = View.GONE; return }
        val esPropietario = miUid == idPropietarioDelInmueble

        // Obtener estado del chat y de la propiedad
        db.collection("chats").document(chatId).get().addOnSuccessListener { chatSnap ->
            val chatEstado = (chatSnap.data?.get("estado") as? String)?.orEmpty() ?: "abierto"
            val chatAbierto = chatEstado == "abierto"

            db.collection("propiedades").document(listingId).get().addOnSuccessListener { propSnap ->
                val propEstado = (propSnap.data?.get("estado") as? String)?.lowercase().orEmpty() ?: "disponible"
                val propDisponible = propEstado != "finalizado"

                val mostrar = !esPropietario && chatAbierto && propDisponible && listingId.isNotBlank()
                binding.btnSolicitarCita.visibility = if (mostrar) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ChatVista.actual = chatId
        escucharMensajes()
        this.escucharChat()
        this.marcarLeido()
    }

    override fun onStop() {
        super.onStop()
        ChatVista.actual = ""
        escuchaMensajes?.remove()
        escuchaChat?.remove()
        escuchaInmueble?.remove()
        escuchaInmueble = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerUI.removeCallbacksAndMessages(null)
    }

    /** Carga el avatar/nombre/verificado/rating del interlocutor. */
    private fun cargarPerfil(uid: String) {
        PerfilUsuario.buscar(db, uid) { perfil ->
            if (perfil == null) return@buscar
            binding.tvChatNombre.text = perfil.nombre
            binding.tvChatAvatar.text = perfil.inicial.toString()
            binding.tvChatAvatar.backgroundTintList = ColorStateList.valueOf(
                coloresAvatar[Math.floorMod(uid.hashCode(), coloresAvatar.size)]
            )
            binding.ivChatVerificado.visibility = if (perfil.verificado) View.VISIBLE else View.GONE
            binding.tvChatVerificadoLinea.visibility = if (perfil.verificado) View.VISIBLE else View.GONE
            if (perfil.rating > 0) {
                binding.tvChatRating.text = String.format(Locale.US, "%.1f", perfil.rating)
                binding.tvChatRating.visibility = View.VISIBLE
            } else {
                binding.tvChatRating.visibility = View.GONE
            }
        }
    }

    private fun escucharMensajes() {
        escuchaMensajes?.remove()
        escuchaMensajes = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("sentAt")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val lista = snap.documents.mapNotNull { Mensaje.desde(it) }
                val lm = binding.rvMensajes.layoutManager as LinearLayoutManager
                // OJO: findLastVisibleItemPosition (no "Completely"): la ultima
                // burbuja casi siempre queda CLIPADA por la barra de escritura,
                // asi que con findLastCompletelyVisibleItemPosition() el flag
                // "leyendo" nunca era true y el readAt no se marcaba nunca.
                val enElFondo = lm.itemCount == 0 ||
                    lm.findLastVisibleItemPosition() >= adapter.itemCount - 1
                adapter.submitList(lista)
                if (enElFondo && adapter.itemCount > 0) {
                    binding.rvMensajes.post {
                        binding.rvMensajes.scrollToPosition(adapter.itemCount - 1)
                    }
                }
                marcarRecibos(lista, enElFondo)
            }
    }

    /**
     * Checks estilo WhatsApp: el RECEPTOR marca "deliveredAt" en cuanto el
     * mensaje entra con el chat en pantalla, y "readAt" cuando ademas esta
     * viendo el final de la conversacion. Solo se escribe lo que falta, y las
     * reglas de Firestore impiden que el autor se marque sus propios mensajes.
     */
    private fun marcarRecibos(lista: List<Mensaje>, leyendo: Boolean) {
        val miUid = auth.currentUser?.uid ?: return
        if (ChatVista.actual != chatId) return
        val pendientes = lista.filter { it.senderId != miUid && it.sentAt > 0L }
        if (pendientes.isEmpty()) return
        val ultimaId = pendientes.last().messageId
        val aMarcar = pendientes.filter { m ->
            (m.deliveredAt == null) ||
                (leyendo && m.readAt == null && m.messageId == ultimaId)
        }.take(MAX_RECEIPTOS)
        if (aMarcar.isEmpty()) return

        val ref = db.collection("chats").document(chatId).collection("messages")
        var batch: WriteBatch? = null
        aMarcar.forEach { m ->
            val campos = hashMapOf<String, Any>()
            if (m.deliveredAt == null) campos["deliveredAt"] = FieldValue.serverTimestamp()
            if (leyendo && m.readAt == null && m.messageId == ultimaId) {
                campos["readAt"] = FieldValue.serverTimestamp()
            }
            if (campos.isEmpty()) return@forEach
            if (batch == null) batch = db.batch()
            batch?.update(ref.document(m.messageId), campos)
        }
        batch?.commit()
    }

    /** Mantiene el encabezado sincronizado con el título del inmueble y actualiza botón propuesta. */
    private fun escucharChat() {
        escuchaChat?.remove()
        escuchaChat = db.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                if (snap?.exists() == true) {
                    val titulo = snap.data?.get("listingTitle") as? String
                    if (!titulo.isNullOrBlank()) binding.tvChatListing.text = titulo
                    actualizarBotonPropuesta()
                }
            }
    }

    private fun enviarMensaje() {
        if (!binding.etEntrada.isEnabled) return
        val texto = binding.etEntrada.text.toString().trim()
        val miUid = auth.currentUser?.uid ?: return
        if (texto.isEmpty() || chatId.isBlank()) return

        val ahora = System.currentTimeMillis()

        // 1) Bloqueo por spam todavia vigente.
        if (ahora < bloqueoSpamHasta) {
            val mins = ((bloqueoSpamHasta - ahora + 59_999L) / 60_000L).toInt()
            Toast.makeText(this, getString(R.string.chat_spam_bloqueo_activo, mins), Toast.LENGTH_LONG).show()
            return
        }

        // 2) Reenvio del mismo texto dentro de la ventana de 30 s.
        if (texto == ultimoMensajeEnviado && (ahora - ultimoMensajeTimestamp) < DEBOUNCE_MS) {
            val seg = ((DEBOUNCE_MS - (ahora - ultimoMensajeTimestamp) + 999L) / 1000L).toInt()
            Toast.makeText(this, getString(R.string.chat_msj_duplicado, seg), Toast.LENGTH_SHORT).show()
            return
        }

        // 3) Palabra repetida mas de 5 veces seguidas -> bloqueo de 5 min.
        historialEnviado.add(ahora to texto)
        while (historialEnviado.size > VENTANA_ANTISPAM) historialEnviado.removeAt(0)
        val palabras = historialEnviado.flatMap { palabrasDe(it.second) }
        val seRepite = palabras.groupingBy { it }.eachCount()
            .any { it.value > MAX_REPETICIONES }
        if (seRepite) {
            activarBloqueoSpam()
            return
        }

        ultimoMensajeEnviado = texto
        ultimoMensajeTimestamp = ahora

        val ref = db.collection("chats").document(chatId)
            .collection("messages").document()
        db.collection("chats").document(chatId)
            .collection("messages")
            .document(ref.id)
            .set(
                hashMapOf(
                    "messageId" to ref.id,
                    "senderId" to miUid,
                    "text" to texto,
                    "tipo" to "texto",
                    "sentAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                val updates = hashMapOf<String, Any>(
                    "lastMessage" to texto,
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
                if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
                db.collection("chats").document(chatId).update(updates)
            }
        binding.etEntrada.text?.clear()
    }

    /** Palabras "significativas" de un mensaje (ignora relleno de 1-2 letras). */
    private fun palabrasDe(texto: String): List<String> =
        texto.lowercase(Locale("es", "PE"))
            .split(Regex("[^a-z0-9\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00fc]+"))
            .filter { it.length >= 3 }

    /** Bloquea la escritura 5 minutos y avisa al usuario. */
    private fun activarBloqueoSpam() {
        bloqueoSpamHasta = System.currentTimeMillis() + BLOQUEO_SPAM_MS
        historialEnviado.clear()
        ultimoMensajeEnviado = ""
        ultimoMensajeTimestamp = 0
        actualizarEntrada()
        Toast.makeText(this, R.string.chat_spam_bloqueado, Toast.LENGTH_LONG).show()
        handlerUI.postDelayed({
            if (System.currentTimeMillis() >= bloqueoSpamHasta) {
                bloqueoSpamHasta = 0
                actualizarEntrada()
            }
        }, BLOQUEO_SPAM_MS)
    }

    /**
     * Unica fuente de verdad de la barra de escritura: se deshabilita si el
     * inmueble esta alquilado/vendido, si el chat se cerro o si hay bloqueo
     * anti-spam vigente.
     */
    private fun actualizarEntrada() {
        val activa = !chatCerradoPorInmueble && System.currentTimeMillis() >= bloqueoSpamHasta
        binding.etEntrada.isEnabled = activa
        binding.btnEnviar.isEnabled = activa
    }

    /** Muestra diálogo para crear y enviar una cita de visita. */
    private fun mostrarDialogoCita() {
        if (auth.currentUser == null) return

        val view = layoutInflater.inflate(R.layout.dialog_cita, null)
        val etFecha = view.findViewById<TextInputEditText>(R.id.etCitaFecha)
        val etHora = view.findViewById<TextInputEditText>(R.id.etCitaHora)
        val etMensaje = view.findViewById<TextInputEditText>(R.id.etCitaMensaje)

        etFecha.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.cita_fecha))
                .build()
            picker.addOnPositiveButtonClickListener { seleccion ->
                etFecha.setText(formatearFechaCita(seleccion))
            }
            picker.show(supportFragmentManager, "FECHA_CITA")
        }

        etHora.setOnClickListener {
            // OJO: el listener de MaterialTimePicker recibe un View, por eso se lee
            // la hora del propio picker.
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText(getString(R.string.cita_hora))
                .build()
            picker.addOnPositiveButtonClickListener {
                etHora.setText(String.format(Locale.US, "%02d:%02d", picker.hour, picker.minute))
            }
            picker.show(supportFragmentManager, "HORA_CITA")
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nueva_cita)
            .setView(view)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.cita_enviar, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fecha = etFecha.text?.toString()?.trim().orEmpty()
                val hora = etHora.text?.toString()?.trim().orEmpty()
                if (fecha.isEmpty() || hora.isEmpty()) {
                    Toast.makeText(this, getString(R.string.cita_fecha_hora_requerida), Toast.LENGTH_SHORT).show()
                } else {
                    val mensaje = etMensaje.text?.toString()?.trim().orEmpty()
                    enviarCita(fecha, hora, if (mensaje.isEmpty()) getString(R.string.cita_tipo) else mensaje)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /** El date picker devuelve la medianoche UTC en milisegundos: se formatea como
     *  dd/MM/yyyy en UTC para que no se corra un dia por el cambio de zona. */
    private fun formatearFechaCita(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(millis))
    }

    /** Envía una solicitud de cita como mensaje tipo "cita". */
    private fun enviarCita(fecha: String, hora: String, texto: String) {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        val ref = db.collection("chats").document(chatId)
            .collection("messages").document()

        ref.set(
            hashMapOf(
                "messageId" to ref.id,
                "senderId" to miUid,
                "text" to texto,
                "tipo" to "cita",
                "citaId" to ref.id,
                "solicitadoPor" to miUid,
                "fecha" to fecha,
                "hora" to hora,
                "citaEstado" to "pendiente",
                "sentAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            val updates = hashMapOf<String, Any>(
                "lastMessage" to getString(R.string.cita_resumen, fecha, hora),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "citaPendiente" to hashMapOf(
                    "citaId" to ref.id,
                    "solicitadoPor" to miUid,
                    "fecha" to fecha,
                    "hora" to hora,
                    "estado" to "pendiente"
                )
            )
            if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
            db.collection("chats").document(chatId).update(updates)
            Toast.makeText(this, R.string.chat_cita_enviada, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, getString(R.string.cita_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
        }
    }

/** Acepta una cita pendiente (solo el propietario; el chat sigue abierto). */
    private fun aceptarCita(m: Mensaje) {
        val miUid = auth.currentUser?.uid ?: return
        val citaId = m.citaId ?: return
        if (chatId.isBlank()) return

        db.collection("chats").document(chatId)
            .collection("messages").document(citaId)
            .update(
                hashMapOf(
                    "citaEstado" to "aceptada",
                    "respondidoPor" to miUid,
                    "respondidoAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener {
                val updates = hashMapOf<String, Any>(
                    "cita" to hashMapOf(
                        "estado" to "aceptada",
                        "citaId" to citaId,
                        "fecha" to m.fecha,
                        "hora" to m.hora,
                        "aceptadoPor" to miUid,
                        "aceptadoAt" to FieldValue.serverTimestamp()
                    ),
                    "lastMessage" to getString(R.string.cita_resumen_aceptada, m.fecha ?: "", m.hora ?: ""),
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
                if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
                db.collection("chats").document(chatId).update(updates)
                    .addOnSuccessListener { enviarMensajeSistema(getString(R.string.chat_cita_aceptada)) }
            }.addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.cita_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
            }
    }

    /** Rechaza una cita pendiente (el chat sigue abierto y se puede re-agendar). */
    private fun rechazarCita(m: Mensaje) {
        val miUid = auth.currentUser?.uid ?: return
        val citaId = m.citaId ?: return
        if (chatId.isBlank()) return

        db.collection("chats").document(chatId)
            .collection("messages").document(citaId)
            .update(
                hashMapOf(
                    "citaEstado" to "rechazada",
                    "respondidoPor" to miUid,
                    "respondidoAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener {
                val updates = hashMapOf<String, Any>(
                    "citaPendiente" to hashMapOf(
                        "citaId" to citaId,
                        "solicitadoPor" to m.solicitadoPor,
                        "fecha" to m.fecha,
                        "hora" to m.hora,
                        "estado" to "rechazada"
                    ),
                    "lastMessage" to getString(R.string.cita_resumen_rechazada, m.fecha ?: "", m.hora ?: ""),
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
                if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
                db.collection("chats").document(chatId).update(updates)
                enviarMensajeSistema(getString(R.string.chat_cita_rechazada))
            }.addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.cita_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
            }
    }

    /** Envía un mensaje de tipo "sistema" (avisos como cierre, aceptación, etc.). */
    private fun enviarMensajeSistema(texto: String) {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        val ref = db.collection("chats").document(chatId)
            .collection("messages").document()

        db.collection("chats").document(chatId)
            .collection("messages")
            .document(ref.id)
            .set(
                hashMapOf(
                    "messageId" to ref.id,
                    "senderId" to miUid,
                    "text" to texto,
                    "tipo" to "sistema",
                    "sentAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                val updates = hashMapOf<String, Any>(
                    "lastMessage" to texto,
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
                if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
                db.collection("chats").document(chatId).update(updates)
            }
    }

    /** Muestra menú de opciones del chat (cerrar chat, eliminar, etc.). */
    private fun mostrarMenuOpciones() {
        val popup = PopupMenu(this@ChatDetailActivity, binding.btnChatMenu)
        popup.menuInflater.inflate(R.menu.menu_chat, popup.menu)
        popup.setOnMenuItemClickListener { item: android.view.MenuItem ->
            when (item.itemId) {
                R.id.menu_cerrar_chat -> {
                    confirmarCerrarChat()
                    true
                }
                R.id.menu_eliminar_chat -> {
                    confirmarEliminarChat()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /** Confirma y cierra el chat (cambia estado a "cerrado", no borra historial). */
    private fun confirmarCerrarChat() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_cerrar_confirm_titulo)
            .setMessage(R.string.chat_cerrar_confirm_msg)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.chat_cerrar) { _, _ ->
                cerrarChat()
            }
            .show()
    }

    /** Cierra el chat cambiando su estado a "cerrado" (no borra historial). */
    private fun cerrarChat() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        db.collection("chats").document(chatId)
            .update(mapOf(
                "estado" to "cerrado",
                "lastMessage" to getString(R.string.chat_cerrado_sistema),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
            .addOnSuccessListener {
                // Enviar mensaje de sistema
                enviarMensajeSistema(getString(R.string.chat_cerrado_sistema))
                // Actualizar UI
                binding.cardChatCerrado.visibility = View.VISIBLE
                chatCerradoPorInmueble = true
                actualizarEntrada()
                binding.btnSolicitarCita.visibility = View.GONE
                Toast.makeText(this, R.string.chat_cerrar_ok, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.chat_cerrar_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
            }
    }

    /** Confirma la ocultación del chat para el usuario actual. */
    private fun confirmarEliminarChat() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_eliminar_confirm_titulo)
            .setMessage(R.string.chat_eliminar_confirm_msg)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.chat_eliminar) { _, _ ->
                eliminarChat()
            }
            .show()
    }

    /** Oculta el chat solo para el usuario actual (soft delete: NADA se borra de la
     *  base, el historial queda disponible para auditoría y soporte). */
    private fun eliminarChat() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        db.collection("chats").document(chatId)
            .update(mapOf("deletedForUsers" to FieldValue.arrayUnion(miUid)))
            .addOnSuccessListener {
                Toast.makeText(this, R.string.chat_eliminar_ok, Toast.LENGTH_SHORT).show()
                finish() // Cerrar actividad y volver a la lista
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.chat_eliminar_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
            }
    }

    private fun marcarLeido() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return
        db.collection("chats").document(chatId)
            .update(mapOf("unreadCount.$miUid" to 0))
    }

    /** Formatea el precio para mostrar en el encabezado del chat. */
    private fun formatearPrecioChat(precio: Double, moneda: String, op: String): String {
        if (precio <= 0) return ""
        val monto = if (precio == precio.toLong().toDouble()) {
            precio.toLong().toString()
        } else {
            precio.toString()
        }
        val simbolo = if (moneda == "PEN") "S/ " else "$ "
        return if (com.alkilapp.data.Propiedad.normalizarOperacion(op) == "venta") "$simbolo$monto" else "$simbolo$monto / mes"
    }
}