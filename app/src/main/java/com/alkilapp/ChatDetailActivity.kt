package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.ChatItemUi
import com.alkilapp.compose.ChatUiState
import com.alkilapp.compose.PantallaChatDetail
import com.alkilapp.compose.colorAvatarChat
import com.alkilapp.data.Mensaje
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.data.TipoMensaje
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
import kotlinx.coroutines.launch

class ChatDetailActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaMensajes: ListenerRegistration? = null
    private var escuchaChat: ListenerRegistration? = null
    private var escuchaInmueble: ListenerRegistration? = null

    private var chatId: String = ""
    private var otroUid: String = ""
    private var listingId: String = ""
    private var idPropietarioDelInmueble: String = ""

    // --- Anti-spam -------------------------------------------------------
    private var ultimoMensajeEnviado: String = ""
    private var ultimoMensajeTimestamp: Long = 0
    private val DEBOUNCE_MS = 30_000L
    private val BLOQUEO_SPAM_MS = 5 * 60 * 1000L
    private val VENTANA_ANTISPAM = 12
    private val MAX_REPETICIONES = 5
    private val historialEnviado = ArrayList<Pair<Long, String>>()
    private var bloqueoSpamHasta: Long = 0
    private var chatCerradoPorInmueble: Boolean = false
    private var forzarScrollFondo: Boolean = false
    private val handlerUI = Handler(Looper.getMainLooper())
    private val MAX_RECEIPTOS = 400

    private val listaState = LazyListState()
    private var ui by mutableStateOf(ChatUiState())
    private var items by mutableStateOf<List<ChatItemUi>>(emptyList())
    private var textoEntrada by mutableStateOf("")
    private val listaMensajes = ArrayList<Mensaje>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatId = intent.getStringExtra(ChatListActivity.EXTRA_CHAT_ID).orEmpty()
        otroUid = intent.getStringExtra(ChatListActivity.EXTRA_OTRO_UID).orEmpty()
        listingId = intent.getStringExtra(ChatListActivity.EXTRA_LISTING_ID).orEmpty()
            .ifBlank { chatId.removePrefix("inm-") }
        val listing = intent.getStringExtra(ChatListActivity.EXTRA_LISTING).orEmpty()

        ui = ui.copy(listingTitulo = listing)

        setContent {
            AlkilTheme {
                PantallaChatDetail(
                    ui = ui,
                    items = items,
                    miUid = auth.currentUser?.uid ?: "",
                    listaState = listaState,
                    textoEntrada = textoEntrada,
                    onTextoEntrada = { textoEntrada = it },
                    onEnviar = { enviarMensaje() },
                    onCita = { mostrarDialogoCita() },
                    onAtras = { finish() },
                    onCerrarChat = { confirmarCerrarChat() },
                    onEliminarChat = { confirmarEliminarChat() },
                    onAceptar = { m -> aceptarCita(m) },
                    onRechazar = { m -> rechazarCita(m) }
                )
            }
        }

        if (otroUid.isNotBlank()) cargarPerfil(otroUid)

        if (listingId.isNotBlank()) {
            db.collection("propiedades").document(listingId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        idPropietarioDelInmueble = doc.getString("idPropietario") ?: ""
                        val precio = (doc.getDouble("precio") ?: 0.0)
                        val moneda = (doc.getString("moneda") ?: "PEN")
                        val op = (doc.getString("operacion") ?: "alquiler")
                        val precioFormateado = formatearPrecioChat(precio, moneda, op)
                        ui = ui.copy(precio = precioFormateado)
                        actualizarBotonPropuesta()
                    }
                }
            vigilarEstadoInmueble()
        }
    }

    private fun vigilarEstadoInmueble() {
        escuchaInmueble?.remove()
        escuchaInmueble = db.collection("propiedades").document(listingId)
            .addSnapshotListener { snap, _ ->
                if (snap?.exists() == true) {
                    val estado = (snap.data?.get("estado") as? String)?.trim()?.lowercase().orEmpty()
                    val cerrado = estado == "finalizado"
                    chatCerradoPorInmueble = cerrado
                    actualizarEntrada()
                    actualizarBotonPropuesta()
                }
            }
    }

    private fun actualizarBotonPropuesta() {
        val miUid = auth.currentUser?.uid ?: run {
            ui = ui.copy(mostrarBotonCita = false); return
        }
        val esPropietario = miUid == idPropietarioDelInmueble

        db.collection("chats").document(chatId).get().addOnSuccessListener { chatSnap ->
            val chatEstado = (chatSnap.data?.get("estado") as? String)?.orEmpty() ?: "abierto"
            val chatAbierto = chatEstado == "abierto"

            db.collection("propiedades").document(listingId).get().addOnSuccessListener { propSnap ->
                val propEstado = (propSnap.data?.get("estado") as? String)?.lowercase().orEmpty() ?: "disponible"
                val propDisponible = propEstado != "finalizado"

                val mostrar = !esPropietario && chatAbierto && propDisponible && listingId.isNotBlank()
                ui = ui.copy(mostrarBotonCita = mostrar)
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

    private fun cargarPerfil(uid: String) {
        PerfilUsuario.buscar(db, uid) { perfil ->
            if (perfil == null) return@buscar
            ui = ui.copy(
                nombre = perfil.nombre,
                inicial = perfil.inicial.toString(),
                colorAvatar = colorAvatarChat(uid),
                verificado = perfil.verificado,
                ratingTexto = if (perfil.rating > 0) String.format(Locale.US, "%.1f", perfil.rating) else null
            )
        }
    }

    // ---- Items aplanados (dia header + mensajes) -------------------------

    private fun diaClave(millis: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(java.util.Calendar.YEAR) * 1000L + c.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun esHoy(millis: Long): Boolean {
        val c1 = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val c2 = java.util.Calendar.getInstance()
        return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
                c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun formatearDia(millis: Long): String {
        val fecha = java.util.Date(millis)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val hoy = java.util.Calendar.getInstance()
        val diffDias = hoy.get(java.util.Calendar.DAY_OF_YEAR) - cal.get(java.util.Calendar.DAY_OF_YEAR)
        val diffAnios = hoy.get(java.util.Calendar.YEAR) - cal.get(java.util.Calendar.YEAR)
        val diaFormato = java.text.SimpleDateFormat("dd MMM", Locale("es", "PE"))
        val diaNombreFormato = java.text.SimpleDateFormat("EEEE", Locale("es", "PE"))

        return when {
            esHoy(millis) -> "Hoy"
            diffAnios == 0 && diffDias == 1 -> "Ayer"
            diffAnios == 0 && diffDias <= 3 -> diaNombreFormato.format(fecha)
                .replaceFirstChar { it.uppercase(Locale("es", "PE")) }
            else -> diaFormato.format(fecha)
        }
    }

    private fun construirItems(mensajes: List<Mensaje>): List<ChatItemUi> {
        val out = ArrayList<ChatItemUi>()
        var ultimoDiaClave: Long? = null
        for (m in mensajes) {
            if (m.sentAt > 0) {
                val clave = diaClave(m.sentAt)
                if (clave != ultimoDiaClave) {
                    ultimoDiaClave = clave
                    out.add(ChatItemUi.Dia(formatearDia(m.sentAt).uppercase(Locale("es", "PE"))))
                }
            }
            out.add(ChatItemUi.Msg(m))
        }
        return out
    }

    /** true si el ultimo item esta visible (usuario en el fondo de la conversacion). */
    private fun enElFondo(): Boolean {
        val layout = listaState.layoutInfo
        val ultimoVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
        return layout.totalItemsCount == 0 || ultimoVisible >= items.size - 1
    }

    private fun escucharMensajes() {
        escuchaMensajes?.remove()
        escuchaMensajes = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("sentAt")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val lista = snap.documents.mapNotNull { Mensaje.desde(it) }
                listaMensajes.clear()
                listaMensajes.addAll(lista)
                val irAlFondo = enElFondo() || forzarScrollFondo
                forzarScrollFondo = false
                val ordenados = lista.sortedWith(compareBy { if (it.sentAt > 0) it.sentAt else Long.MAX_VALUE })
                items = construirItems(ordenados)
                if (irAlFondo && items.isNotEmpty()) {
                    val destino = items.size - 1
                    lifecycleScope.launch { listaState.scrollToItem(destino) }
                }
                marcarRecibos(lista, irAlFondo)
            }
    }

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

    private fun escucharChat() {
        escuchaChat?.remove()
        escuchaChat = db.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                if (snap?.exists() == true) {
                    val titulo = snap.data?.get("listingTitle") as? String
                    if (!titulo.isNullOrBlank()) ui = ui.copy(listingTitulo = titulo)
                    actualizarBotonPropuesta()
                }
            }
    }

    private fun enviarMensaje() {
        if (!ui.entradaActiva) return
        val texto = textoEntrada.trim()
        val miUid = auth.currentUser?.uid ?: return
        if (texto.isEmpty() || chatId.isBlank()) return

        val ahora = System.currentTimeMillis()

        if (ahora < bloqueoSpamHasta) {
            val mins = ((bloqueoSpamHasta - ahora + 59_999L) / 60_000L).toInt()
            Toast.makeText(this, getString(R.string.chat_spam_bloqueo_activo, mins), Toast.LENGTH_LONG).show()
            return
        }

        if (texto == ultimoMensajeEnviado && (ahora - ultimoMensajeTimestamp) < DEBOUNCE_MS) {
            val seg = ((DEBOUNCE_MS - (ahora - ultimoMensajeTimestamp) + 999L) / 1000L).toInt()
            Toast.makeText(this, getString(R.string.chat_msj_duplicado, seg), Toast.LENGTH_SHORT).show()
            return
        }

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
        forzarScrollFondo = true

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
        textoEntrada = ""
    }

    private fun palabrasDe(texto: String): List<String> =
        texto.lowercase(Locale("es", "PE"))
            .split(Regex("[^a-z0-9áéíóúñü]+"))
            .filter { it.length >= 3 }

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

    private fun actualizarEntrada() {
        val activa = !chatCerradoPorInmueble && System.currentTimeMillis() >= bloqueoSpamHasta
        ui = ui.copy(entradaActiva = activa, cerrado = chatCerradoPorInmueble)
    }

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

    private fun formatearFechaCita(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(millis))
    }

    private fun enviarCita(fecha: String, hora: String, texto: String) {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        forzarScrollFondo = true
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

    private fun enviarMensajeSistema(texto: String) {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        forzarScrollFondo = true
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
                enviarMensajeSistema(getString(R.string.chat_cerrado_sistema))
                chatCerradoPorInmueble = true
                actualizarEntrada()
                ui = ui.copy(mostrarBotonCita = false)
                Toast.makeText(this, R.string.chat_cerrar_ok, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.chat_cerrar_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
            }
    }

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

    private fun eliminarChat() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        db.collection("chats").document(chatId)
            .update(mapOf("deletedForUsers" to FieldValue.arrayUnion(miUid)))
            .addOnSuccessListener {
                Toast.makeText(this, R.string.chat_eliminar_ok, Toast.LENGTH_SHORT).show()
                finish()
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
