package com.alkilapp

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Mensaje
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.databinding.ActivityChatDetailBinding
import com.alkilapp.ui.MensajeAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

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

    private val adapter by lazy { MensajeAdapter(auth.currentUser?.uid ?: "") }

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
        if (listingId.isNotBlank()) vigilarEstadoInmueble()
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

    /** Mantiene el encabezado sincronizado con el título del inmueble. */
    private fun escucharChat() {
        escuchaChat?.remove()
        escuchaChat = db.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                if (snap?.exists() == true) {
                    val titulo = snap.data?.get("listingTitle") as? String
                    if (!titulo.isNullOrBlank()) binding.tvChatListing.text = titulo
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

    private fun marcarLeido() {
        val miUid = auth.currentUser?.uid ?: return
        if (chatId.isBlank()) return
        db.collection("chats").document(chatId)
            .update(mapOf("unreadCount.$miUid" to 0))
    }
}