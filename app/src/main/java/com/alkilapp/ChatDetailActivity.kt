package com.alkilapp

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Mensaje
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.data.TipoMensaje
import com.alkilapp.databinding.ActivityChatDetailBinding
import com.alkilapp.ui.MensajeAdapter
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
        binding.btnChatPropuesta.setOnClickListener { mostrarDialogoPropuesta() }
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

        // Obtener idPropietario del inmueble para controlar visibilidad del botón "Propuesta"
        if (listingId.isNotBlank()) {
            db.collection("propiedades").document(listingId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        idPropietarioDelInmueble = doc.getString("idPropietario") ?: ""
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
        val miUid = auth.currentUser?.uid ?: run { binding.btnChatPropuesta.visibility = View.GONE; return }
        val esPropietario = miUid == idPropietarioDelInmueble

        // Obtener estado del chat y de la propiedad
        db.collection("chats").document(chatId).get().addOnSuccessListener { chatSnap ->
            val chatEstado = (chatSnap.data?.get("estado") as? String)?.orEmpty() ?: "abierto"
            val chatAbierto = chatEstado == "abierto"

            db.collection("propiedades").document(listingId).get().addOnSuccessListener { propSnap ->
                val propEstado = (propSnap.data?.get("estado") as? String)?.lowercase().orEmpty() ?: "disponible"
                val propDisponible = propEstado != "finalizado"

                val mostrar = !esPropietario && chatAbierto && propDisponible && listingId.isNotBlank()
                binding.btnChatPropuesta.visibility = if (mostrar) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ChatVista.actual = chatId
        escucharMensajes()
        escucharChat()
        marcarLeido()
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
        val dialog = AlertDialog.Builder(this)
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

    private fun marcarLeido() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return
        db.collection("chats").document(chatId)
            .update(mapOf("unreadCount.$miUid" to 0))
    }
}