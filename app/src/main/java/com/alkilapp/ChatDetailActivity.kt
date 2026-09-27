package com.alkilapp

import android.content.res.ColorStateList
import android.os.Bundle
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ChatDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatDetailBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaMensajes: ListenerRegistration? = null
    private var escuchaChat: ListenerRegistration? = null

    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

private var chatId: String = ""
    private var otroUid: String = ""
    private var listingId: String = ""
    private var idPropietarioDelInmueble: String = ""

    // Debounce para evitar duplicados (5 segundos)
    private var ultimoMensajeEnviado: String = ""
    private var ultimoMensajeTimestamp: Long = 0
    private val DEBOUNCE_MS = 5000L // 5 segundos

    private val adapter by lazy { MensajeAdapter(
        miUid = auth.currentUser?.uid ?: "",
        onAceptar = { m -> aceptarPropuesta(m) },
        onRechazar = { m -> rechazarPropuesta(m) }
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
        binding.btnSolicitarAlquiler.setOnClickListener { mostrarDialogoPropuesta() }
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
        db.collection("propiedades").document(listingId)
            .addSnapshotListener { snap, _ ->
                if (snap?.exists() == true) {
                    val estado = (snap.data?.get("estado") as? String)?.trim()?.lowercase().orEmpty()
                    val cerrado = estado == "finalizado"
                    binding.cardChatCerrado.visibility = if (cerrado) View.VISIBLE else View.GONE
                    binding.etEntrada.isEnabled = !cerrado
                    binding.btnEnviar.isEnabled = !cerrado
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
        val miUid = auth.currentUser?.uid ?: run { binding.btnSolicitarAlquiler.visibility = View.GONE; return }
        val esPropietario = miUid == idPropietarioDelInmueble

        // Obtener estado del chat y de la propiedad
        db.collection("chats").document(chatId).get().addOnSuccessListener { chatSnap ->
            val chatEstado = (chatSnap.data?.get("estado") as? String)?.orEmpty() ?: "abierto"
            val chatAbierto = chatEstado == "abierto"

            db.collection("propiedades").document(listingId).get().addOnSuccessListener { propSnap ->
                val propEstado = (propSnap.data?.get("estado") as? String)?.lowercase().orEmpty() ?: "disponible"
                val propDisponible = propEstado != "finalizado"

                val mostrar = !esPropietario && chatAbierto && propDisponible && listingId.isNotBlank()
                binding.btnSolicitarAlquiler.visibility = if (mostrar) View.VISIBLE else View.GONE
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
                val enElFondo = lm.itemCount == 0 ||
                    lm.findLastCompletelyVisibleItemPosition() >= adapter.itemCount - 1
                adapter.submitList(lista)
                if (enElFondo && adapter.itemCount > 0) {
                    binding.rvMensajes.post {
                        binding.rvMensajes.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
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

        // Debounce: evitar envío duplicado del mismo texto en ventana corta
        val ahora = System.currentTimeMillis()
        if (texto == ultimoMensajeEnviado && (ahora - ultimoMensajeTimestamp) < DEBOUNCE_MS) {
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

    /** Muestra diálogo para crear y enviar una propuesta de alquiler. */
    private fun mostrarDialogoPropuesta() {
        val miUid = auth.currentUser?.uid ?: return
        val dialog = MaterialAlertDialogBuilder(this)
        val view = layoutInflater.inflate(R.layout.dialog_propuesta, null)
        val etMonto = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPropMonto)
        val etMensaje = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPropMensaje)

        dialog.setView(view)
            .setTitle(R.string.nueva_propuesta)
            .setPositiveButton(R.string.enviar) { _, _ ->
                val montoStr = etMonto.text.toString().trim()
                val mensaje = etMensaje.text.toString().trim()
                if (montoStr.isNotEmpty()) {
                    enviarPropuesta(montoStr.toDouble(), if (mensaje.isEmpty()) getString(R.string.propuesta_alquiler) else mensaje)
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /** Envía una propuesta de alquiler como mensaje tipo "propuesta". */
    private fun enviarPropuesta(monto: Double, texto: String) {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        val propuestaId = db.collection("chats").document(chatId)
            .collection("messages").document().id

        val ref = db.collection("chats").document(chatId)
            .collection("messages").document(propuestaId)

        db.collection("chats").document(chatId)
            .collection("messages")
            .document(propuestaId)
            .set(
                hashMapOf(
                    "messageId" to propuestaId,
                    "senderId" to miUid,
                    "text" to texto,
                    "tipo" to "propuesta",
                    "propuestaId" to propuestaId,
                    "propuestoPor" to miUid,
                    "monto" to monto,
                    "moneda" to "PEN",
                    "propuestaEstado" to "pendiente",
                    "sentAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                val updates = hashMapOf<String, Any>(
                    "lastMessage" to "Propuesta: $monto PEN",
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
                if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
                // Actualizar el chat con la propuesta pendiente
                db.collection("chats").document(chatId)
                    .update(updates + mapOf("acuerdoPendiente" to hashMapOf(
                        "propuestaId" to propuestaId,
                        "propuestoPor" to miUid,
                        "monto" to monto
                    )))
            }
    }

    /** Acepta una propuesta pendiente. */
    private fun aceptarPropuesta(m: Mensaje) {
        val miUid = auth.currentUser?.uid ?: return
        if (m.propuestaId == null || chatId.isBlank()) return

        val ref = db.collection("chats").document(chatId)
            .collection("messages").document(m.propuestaId!!)

        ref.update(
            hashMapOf(
                "propuestaEstado" to "aceptada",
                "respondidoPor" to miUid,
                "respondidoAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            // Marcar el chat como acuerdo_cerrado y notificar al otro
            val updates = hashMapOf<String, Any>(
                "estado" to "acuerdo_cerrado",
                "acuerdo" to hashMapOf(
                    "estado" to "aceptada",
                    "propuestaId" to m.propuestaId!!,
                    "monto" to m.monto,
                    "moneda" to m.moneda,
                    "aceptadoPor" to miUid,
                    "aceptadoAt" to FieldValue.serverTimestamp()
                ),
                "lastMessage" to "Acuerdo aceptado",
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
            if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
            db.collection("chats").document(chatId).update(updates)
                .addOnSuccessListener {
                    // Enviar mensaje de sistema
                    enviarMensajeSistema(getString(R.string.chat_acuerdo_aceptado))
                    // Registrar acuerdoPendiente en la propiedad (para pausa)
                    if (listingId.isNotBlank()) {
                        db.collection("propiedades").document(listingId)
                            .update(mapOf(
                                "acuerdoPendiente" to hashMapOf(
                                    "chatId" to chatId,
                                    "propuestaId" to m.propuestaId!!,
                                    "estado" to "aceptada"
                                )
                            ))
                    }
                }
        }
    }

    /** Rechaza una propuesta pendiente. */
    private fun rechazarPropuesta(m: Mensaje) {
        val miUid = auth.currentUser?.uid ?: return
        if (m.propuestaId == null || chatId.isBlank()) return

        val ref = db.collection("chats").document(chatId)
            .collection("messages").document(m.propuestaId!!)

        ref.update(
            hashMapOf(
                "propuestaEstado" to "rechazada",
                "respondidoPor" to miUid,
                "respondidoAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            val updates = hashMapOf<String, Any>(
                "lastMessage" to "Propuesta rechazada",
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
            if (otroUid.isNotBlank()) updates["unreadCount.$otroUid"] = FieldValue.increment(1)
            db.collection("chats").document(chatId).update(updates)
            enviarMensajeSistema(getString(R.string.chat_propuesta_rechazada))
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
                binding.etEntrada.isEnabled = false
                binding.btnEnviar.isEnabled = false
                binding.btnSolicitarAlquiler.visibility = View.GONE
                Toast.makeText(this, R.string.chat_cerrar_ok, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.chat_cerrar_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
            }
    }

    /** Confirma y elimina el chat permanentemente (borra el documento y subcolección). */
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

    /** Elimina el chat permanentemente de Firestore (documento + subcolección messages). */
    private fun eliminarChat() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return

        // Eliminar subcolección messages primero (batch)
        db.collection("chats").document(chatId).collection("messages")
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                // Eliminar documento del chat
                batch.delete(db.collection("chats").document(chatId))
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.chat_eliminar_ok, Toast.LENGTH_SHORT).show()
                        finish() // Cerrar actividad y volver a la lista
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, getString(R.string.chat_eliminar_error, e.localizedMessage ?: "?"), Toast.LENGTH_LONG).show()
                    }
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
        return if (op == "venta") "$simbolo$monto" else "$simbolo$monto / mes"
    }
}