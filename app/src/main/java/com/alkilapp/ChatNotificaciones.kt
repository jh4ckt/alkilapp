package com.alkilapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alkilapp.data.ChatAlkil
import com.alkilapp.data.PerfilUsuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Pantallas en las que el usuario ya esta viendo chats (evita notificaciones duplicadas). */
object ChatVista {
    /** ChatListActivity abierta: cualquier chat se ve en la bandeja. */
    const val EN_LISTA = "@"

    @Volatile
    var actual: String = ""
}

/** Crea el canal de notificaciones y muestra el aviso de un chat nuevo. */
object NotificadorChat {
    private const val CANAL = "alkilapp_chats"

    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CANAL,
                context.getString(R.string.notif_canal_chats),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** Muestra la notificacion y al tocarla abre la conversacion del inmueble. */
    fun mostrar(context: Context, chat: ChatAlkil, nombreOtro: String) {
        val titulo = nombreOtro.ifBlank {
            chat.listingTitle.ifBlank { context.getString(R.string.notif_titulo_nuevo) }
        }
        val texto = if (nombreOtro.isBlank()) {
            chat.lastMessage.ifBlank { context.getString(R.string.notif_titulo_nuevo) }
        } else {
            context.getString(R.string.notif_desde_campo, chat.listingTitle)
        }

        val destino = Intent(context, ChatDetailActivity::class.java).apply {
            putExtra(ChatListActivity.EXTRA_CHAT_ID, chat.chatId)
            putExtra(ChatListActivity.EXTRA_LISTING, chat.listingTitle)
            putExtra(ChatListActivity.EXTRA_OTRO_UID, chat.otrosParticipantes.firstOrNull() ?: "")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendiente = PendingIntent.getActivity(
            context,
            chat.chatId.hashCode(),
            destino,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(chat.lastMessage))
            .setAutoCancel(true)
            .setContentIntent(pendiente)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(chat.chatId.hashCode(), notificacion)
        } catch (_: SecurityException) {
            // Permiso POST_NOTIFICATIONS no concedido (Android 13+): no hay notificaciones.
        }
    }
}

/**
 * Escucha los chats del usuario mientras la app este viva y avisa cuando llega
 * un mensaje nuevo (unreadCount[uid] > 0 y lastMessageAt mas reciente que lo visto).
 */
object MonitorChats {
    private var listener: ListenerRegistration? = null
    private var escuchaAuth: FirebaseAuth.AuthStateListener? = null
    private val nombres = HashMap<String, String>()
    private val notificados = HashMap<String, Long>()
    private var uidActual: String? = null
    private var seedCompletado = false

    fun iniciar(app: Application) {
        NotificadorChat.crearCanal(app)
        escuchaAuth?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == uidActual) return@AuthStateListener
            if (uid != null) conectar(app, uid) else desconectar()
        }
        escuchaAuth = authListener
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        FirebaseAuth.getInstance().currentUser?.uid?.let { conectar(app, it) }
    }

    private fun desconectar() {
        uidActual = null
        listener?.remove()
        listener = null
        notificados.clear()
        nombres.clear()
        seedCompletado = false
    }

    private fun conectar(app: Application, uid: String) {
        uidActual = uid
        notificados.clear()
        nombres.clear()
        seedCompletado = false
        listener?.remove()

        listener = FirebaseFirestore.getInstance("alkilappdb")
            .collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener

                val chats = snap.documents.mapNotNull { ChatAlkil.desde(it, uid) }
                if (!seedCompletado) {
                    // Primera lectura: registra lo ya visto sin avisar (evita re-notificar
                    // chats viejos no leidos al abrir la app).
                    for (c in chats) notificados[c.chatId] = c.lastMessageAt
                    seedCompletado = true
                    return@addSnapshotListener
                }

                for (c in chats) {
                    if (c.unreadMio <= 0) continue
                    if (ChatVista.actual == c.chatId || ChatVista.actual == ChatVista.EN_LISTA) continue
                    val previo = notificados[c.chatId] ?: 0L
                    if (c.lastMessageAt <= previo) continue
                    notificados[c.chatId] = c.lastMessageAt

                    val otro = c.otrosParticipantes.firstOrNull()
                    val nombre = otro?.let { nombres[it] } ?: ""
                    if (otro != null && !nombres.containsKey(otro)) {
                        PerfilUsuario.buscar(FirebaseFirestore.getInstance("alkilappdb"), otro) {
                            it?.let { p -> nombres[p.uid] = p.nombre }
                        }
                    }
                    NotificadorChat.mostrar(app, c, nombre)
                }
            }
    }
}