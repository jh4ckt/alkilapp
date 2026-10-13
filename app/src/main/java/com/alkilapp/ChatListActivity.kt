package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.ElemChat
import com.alkilapp.compose.PantallaChatList
import com.alkilapp.data.ChatAlkil
import com.alkilapp.data.PerfilUsuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatListActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaChats: ListenerRegistration? = null
    private val uidsCargados = mutableSetOf<String>()

    private var elementos by mutableStateOf<List<ElemChat>>(emptyList())
    private var perfiles by mutableStateOf<Map<String, PerfilUsuario>>(emptyMap())
    private var miUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        miUid = auth.currentUser?.uid ?: ""
        setContent {
            AlkilTheme {
                PantallaChatList(
                    elementos = elementos,
                    perfiles = perfiles,
                    miUid = miUid,
                    onChat = { chat -> abrirChat(chat) },
                    onAtras = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ChatVista.actual = ChatVista.EN_LISTA
        escucharChats()
    }

    override fun onStop() {
        super.onStop()
        ChatVista.actual = ""
        escuchaChats?.remove()
    }

    private fun escucharChats() {
        escuchaChats?.remove()
        val uid = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        // Solo los chats donde yo participo Y no los he "eliminado" (soft delete).
        escuchaChats = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener

                val chats = snap?.documents?.mapNotNull { ChatAlkil.desde(it, uid) }
                    ?.filter { chat -> !chat.deletedForUsers.contains(uid) }
                    ?.sortedByDescending { it.lastMessageAt }
                    ?: emptyList()

                // Agrupar chats por listingId (inmueble)
                val grupos = chats.groupBy { it.listingId }
                    .mapValues { (_, chatsDelGrupo) ->
                        chatsDelGrupo.sortedByDescending { it.lastMessageAt }
                    }
                    .toSortedMap()

                // Aplanar a encabezado + filas para el LazyColumn
                val lista = mutableListOf<ElemChat>()
                for ((_, chatsDelGrupo) in grupos) {
                    chatsDelGrupo.firstOrNull()?.let {
                        lista.add(ElemChat.Encabezado(it.listingTitle))
                    }
                    chatsDelGrupo.forEach { lista.add(ElemChat.Fila(it)) }
                }
                elementos = lista

                // Carga perfiles de la otra persona (una vez por uid por sesión).
                for (uidOtro in chats.flatMap { it.otrosParticipantes }) {
                    if (uidsCargados.add(uidOtro)) {
                        PerfilUsuario.buscar(db, uidOtro) { perfil ->
                            if (perfil != null) {
                                perfiles = perfiles + (uidOtro to perfil)
                            }
                        }
                    }
                }
            }
    }

    private fun abrirChat(chat: ChatAlkil) {
        val otro = chat.otrosParticipantes.firstOrNull() ?: return
        val intent = Intent(this, ChatDetailActivity::class.java).apply {
            putExtra(EXTRA_CHAT_ID, chat.chatId)
            putExtra(EXTRA_LISTING, chat.listingTitle)
            putExtra(EXTRA_LISTING_ID, chat.listingId)
            putExtra(EXTRA_OTRO_UID, otro)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_LISTING = "chat_listing"
        const val EXTRA_LISTING_ID = "chat_listing_id"
        const val EXTRA_OTRO_UID = "chat_otro_uid"
    }
}
