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
    val unreadMio: Int,
    val estado: String = "abierto",
    val acuerdo: Map<String, Any>? = null
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
                unreadMio = unread.toInt(),
                estado = d["estado"] as? String ?: "abierto",
                acuerdo = d["acuerdo"] as? Map<String, Any>
            )
        }
    }
}

/** Tipo de mensaje en el chat. */
enum class TipoMensaje {
    TEXTO, PROPUESTA, SISTEMA
}

/** Un mensaje dentro de un chat (subcolección "chats/{chatId}/messages"). */
data class Mensaje(
    val messageId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val tipo: TipoMensaje = TipoMensaje.TEXTO,
    val propuestaId: String? = null,
    val propuestoPor: String? = null,
    val monto: Double? = null,
    val moneda: String = "PEN",
    val propuestaEstado: String? = null,
    val respondidoPor: String? = null,
    val respondidoAt: Long? = null
) {
    companion object {
        fun desde(doc: DocumentSnapshot): Mensaje? {
            val d = doc.data ?: return null
            val sentAt = (d["sentAt"] as? Timestamp)?.toDate()?.time
                ?: (d["sentAt"] as? Date)?.time ?: 0L
            val tipo = TipoMensaje.valueOf((d["tipo"] as? String)?.uppercase() ?: "TEXTO")
            return Mensaje(
                messageId = d["messageId"] as? String ?: doc.id,
                senderId = d["senderId"] as? String ?: "",
                text = d["text"] as? String ?: "",
                sentAt = sentAt,
                tipo = tipo,
                propuestaId = d["propuestaId"] as? String,
                propuestoPor = d["propuestoPor"] as? String,
                monto = (d["monto"] as? Number)?.toDouble(),
                moneda = d["moneda"] as? String ?: "PEN",
                propuestaEstado = d["propuestaEstado"] as? String,
                respondidoPor = d["respondidoPor"] as? String,
                respondidoAt = (d["respondidoAt"] as? Timestamp)?.toDate()?.time
                    ?: (d["respondidoAt"] as? Date)?.time
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