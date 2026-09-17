package com.alkilapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/** Un chat entre 2+ participantes sobre un inmueble (colección "chats"). */
data class ChatAlkil(
    val chatId: String,
    val listingId: String,
    val listingTitle: String,
    val participants: List<String>,
    val otrosParticipantes: List<String>,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadMio: Int
) {
    companion object {
        fun desde(doc: DocumentSnapshot, miUid: String): ChatAlkil? {
            val d = doc.data ?: return null
            val participants = (d["participants"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val otros = participants.filterNot { it == miUid }
            val unread = (d["unreadCount"] as? Map<*, *>)?.get(miUid) as? Number ?: 0
            val lastAt = (d["lastMessageAt"] as? Timestamp)?.toDate()?.time
                ?: (d["lastMessageAt"] as? Date)?.time ?: 0L
            return ChatAlkil(
                chatId = doc.id,
                listingId = d["listingId"] as? String ?: "",
                listingTitle = d["listingTitle"] as? String ?: "",
                participants = participants,
                otrosParticipantes = otros,
                lastMessage = d["lastMessage"] as? String ?: "",
                lastMessageAt = lastAt,
                unreadMio = unread.toInt()
            )
        }
    }
}

/** Un mensaje dentro de un chat (subcolección "chats/{chatId}/messages"). */
data class Mensaje(
    val messageId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long
) {
    companion object {
        fun desde(doc: DocumentSnapshot): Mensaje? {
            val d = doc.data ?: return null
            val sentAt = (d["sentAt"] as? Timestamp)?.toDate()?.time
                ?: (d["sentAt"] as? Date)?.time ?: 0L
            return Mensaje(
                messageId = d["messageId"] as? String ?: doc.id,
                senderId = d["senderId"] as? String ?: "",
                text = d["text"] as? String ?: "",
                sentAt = sentAt
            )
        }
    }
}

/** Perfil mínimo de un participante (documento en "usuarios/{uid}"). */
data class PerfilUsuario(
    val uid: String,
    val nombre: String,
    val verificado: Boolean,
    val rating: Double
) {
    val inicial: Char get() = nombre.trim().firstOrNull()?.uppercaseChar() ?: '?'

    companion object {
        /** Lee un perfil de usuarios/{uid}; devuelve null si no existe el doc. */
        fun buscar(
            db: FirebaseFirestore,
            uid: String,
            alListo: (PerfilUsuario?) -> Unit
        ) {
            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { doc ->
                    val d = doc.data ?: run { alListo(null); return@addOnSuccessListener }
                    val nombre = (d["name"] as? String)
                        ?: (d["nombre"] as? String)
                        ?: (d["email"] as? String)?.substringBefore("@")
                        ?: uid.take(6)
                    val verificado = (d["verification"] as? Map<*, *>)
                        ?.get("identityVerified") as? Boolean ?: false
                        ||
                        d["verificationBadge"] == true
                    val rating = (d["rating"] as? Number)?.toDouble() ?: 0.0
                    alListo(PerfilUsuario(uid, nombre, verificado, rating))
                }
                .addOnFailureListener { alListo(null) }
        }
    }
}