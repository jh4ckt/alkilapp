package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.ChatAlkil
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.databinding.ActivityChatListBinding
import com.alkilapp.ui.ChatListAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaChats: ListenerRegistration? = null
    private val uidsCargados = mutableSetOf<String>()

    private val adapter by lazy {
        ChatListAdapter { chat -> abrirChat(chat) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = adapter
        binding.btnChatBack.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        escucharChats()
    }

    override fun onStop() {
        super.onStop()
        escuchaChats?.remove()
    }

    private fun escucharChats() {
        escuchaChats?.remove()
        val miUid = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        // Solo los chats donde yo participo. El fin del mensaje busca marcar leído.
        escuchaChats = db.collection("chats")
            .whereArrayContains("participants", miUid)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener

                val chats = snap?.documents?.mapNotNull { ChatAlkil.desde(it, miUid) }
                    ?.sortedByDescending { it.lastMessageAt }
                    ?: emptyList()

                adapter.submitList(chats)
                mostrarVacio(chats.isEmpty())

                // Carga perfiles de la otra persona (una vez por uid por sesión).
                for (uid in chats.flatMap { it.otrosParticipantes }) {
                    if (uidsCargados.add(uid)) {
                        PerfilUsuario.buscar(db, uid) { perfil ->
                            adapter.setPerfil(uid, perfil)
                        }
                    }
                }
            }
    }

    private fun mostrarVacio(vacio: Boolean) {
        binding.tvChatVacio.visibility = if (vacio) TextView.VISIBLE else TextView.GONE
    }

    private fun abrirChat(chat: ChatAlkil) {
        val otro = chat.otrosParticipantes.firstOrNull() ?: return
        val intent = Intent(this, ChatDetailActivity::class.java).apply {
            putExtra(EXTRA_CHAT_ID, chat.chatId)
            putExtra(EXTRA_LISTING, chat.listingTitle)
            putExtra(EXTRA_OTRO_UID, otro)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_LISTING = "chat_listing"
        const val EXTRA_OTRO_UID = "chat_otro_uid"
    }
}