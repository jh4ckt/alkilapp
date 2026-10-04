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
    private val onClick: (ChatAlkil) -> Unit,
    private val miUid: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    private val grupos = mutableMapOf<String, List<ChatAlkil>>()
    private val perfiles = mutableMapOf<String, PerfilUsuario>()
    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

    class HeaderViewHolder(val binding: android.view.View) : RecyclerView.ViewHolder(binding)
    class ItemViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitGrupos(nuevosGrupos: Map<String, List<ChatAlkil>>) {
        grupos.clear()
        grupos.putAll(nuevosGrupos)
        notifyDataSetChanged()
    }

    fun setPerfil(uid: String, perfil: PerfilUsuario?) {
        if (perfil != null) perfiles[uid] = perfil
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        var currentPos = 0
        for ((_, chatsDelGrupo) in grupos) {
            // Header
            if (position == currentPos) return TYPE_HEADER
            currentPos++
            // Items
            if (position < currentPos + chatsDelGrupo.size) return TYPE_ITEM
            currentPos += chatsDelGrupo.size
        }
        return TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_chat_header, parent, false))
            TYPE_ITEM -> ItemViewHolder(ItemChatBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var currentPos = 0
        for ((listingId, chatsDelGrupo) in grupos) {
            // Header
            if (position == currentPos) {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.itemView.findViewById<TextView>(R.id.tvHeaderTitle).text =
                    chatsDelGrupo.first().listingTitle
                return
            }
            currentPos++

            // Items
            if (position < currentPos + chatsDelGrupo.size) {
                val itemIndex = position - currentPos
                val chat = chatsDelGrupo[itemIndex]
                val itemHolder = holder as ItemViewHolder
                val b = itemHolder.binding
                val perfil = chat.otrosParticipantes.firstOrNull()?.let { perfiles[it] }

                val esIniciadoPorMi = chat.creatorId == miUid

                b.tvNombre.text = perfil?.nombre ?: "..."
                b.tvAvatar.text = perfil?.inicial.toString()
                b.tvAvatar.backgroundTintList = ColorStateList.valueOf(
                    holder.itemView.context.getColor(colorDeAvatar(perfil?.uid ?: chat.chatId))
                )

                b.ivVerificado.visibility = if (perfil?.verificado == true) View.VISIBLE else View.GONE
                b.tvListing.text = chat.listingTitle

                b.tvUltimo.text = chat.lastMessage.ifBlank { holder.itemView.context.getString(R.string.chat_sin_mensajes) }
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

                b.tvIniciadoPorTi.visibility = if (esIniciadoPorMi) View.VISIBLE else View.GONE

                b.root.setOnClickListener { onClick(chat) }
                return
            }
            currentPos += chatsDelGrupo.size
        }
    }

    override fun getItemCount(): Int {
        var total = 0
        for ((_, chatsDelGrupo) in grupos) {
            total += 1 + chatsDelGrupo.size // header + items
        }
        return total
    }

    private fun colorDeAvatar(clave: String): Int {
        return coloresAvatar[Math.floorMod(clave.hashCode(), coloresAvatar.size)]
    }

    private val horaFormato = SimpleDateFormat("h:mm a", Locale.US)
    private val diaFormato = SimpleDateFormat("dd MMM", Locale("es", "PE"))
    private val diaNombreFormato = SimpleDateFormat("EEEE", Locale("es", "PE"))

    private fun formatearHora(millis: Long): String {
        val fecha = Date(millis)
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val hoy = Calendar.getInstance()
        val diffDias = hoy.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR)
        val diffAnios = hoy.get(Calendar.YEAR) - cal.get(Calendar.YEAR)

        return when {
            esHoy(millis) -> horaFormato.format(Date(millis))
            diffAnios == 0 && diffDias == 1 -> "Ayer ${horaFormato.format(Date(millis))}"
            diffAnios == 0 && diffDias <= 3 -> "${diaNombreFormato.format(Date(millis)).capitalize()} ${horaFormato.format(Date(millis))}"
            else -> diaFormato.format(Date(millis))
        }
    }

    private fun esHoy(millis: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = millis }
        val c2 = Calendar.getInstance()
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}