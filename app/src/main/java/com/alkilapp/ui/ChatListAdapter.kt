package com.alkilapp.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alkilapp.R
import com.alkilapp.data.ChatAlkil
import com.alkilapp.data.PerfilUsuario
import com.alkilapp.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private val onClick: (ChatAlkil) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    private val chats = mutableListOf<ChatAlkil>()
    private val perfiles = mutableMapOf<String, PerfilUsuario>()
    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

    class ViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitList(nueva: List<ChatAlkil>) {
        chats.clear()
        chats.addAll(nueva)
        notifyDataSetChanged()
    }

    fun setPerfil(uid: String, perfil: PerfilUsuario?) {
        if (perfil != null) perfiles[uid] = perfil
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]
        val b = holder.binding
        val perfil = chat.otrosParticipantes.firstOrNull()?.let { perfiles[it] }

        b.tvNombre.text = perfil?.nombre ?: "..."
        b.tvAvatar.text = perfil?.inicial.toString()
        b.tvAvatar.backgroundTintList = ColorStateList.valueOf(
            holder.itemView.context.getColor(colorDeAvatar(perfil?.uid ?: chat.chatId))
        )

        b.ivVerificado.visibility = if (perfil?.verificado == true) View.VISIBLE else View.GONE
        b.tvListing.text = chat.listingTitle

        b.tvUltimo.text = chat.lastMessage.ifBlank { "Sin mensajes aún" }
        b.tvUltimo.setTextColor(
            holder.itemView.context.getColor(
                if (chat.unreadMio > 0) R.color.text_primary else R.color.text_secondary
            )
        )
        b.tvUltimo.typeface =
            if (chat.unreadMio > 0) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT

        b.tvHora.text = if (chat.lastMessageAt > 0) formatearHora(chat.lastMessageAt) else ""

        if (chat.unreadMio > 0) {
            b.tvBadge.visibility = View.VISIBLE
            b.tvBadge.text = chat.unreadMio.toString()
        } else {
            b.tvBadge.visibility = View.GONE
        }

        b.root.setOnClickListener { onClick(chat) }
    }

    override fun getItemCount(): Int = chats.size

    private fun colorDeAvatar(clave: String): Int {
        return coloresAvatar[Math.floorMod(clave.hashCode(), coloresAvatar.size)]
    }

    private val horaFormato = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val diaFormato = SimpleDateFormat("dd MMM", Locale("es", "PE"))

    private fun formatearHora(millis: Long): String {
        val fecha = Date(millis)
        return if (esHoy(millis)) horaFormato.format(fecha) else diaFormato.format(fecha)
    }

    private fun esHoy(millis: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = millis }
        val c2 = Calendar.getInstance()
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}