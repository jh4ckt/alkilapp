package com.alkilapp.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.alkilapp.R
import com.alkilapp.data.Mensaje
import com.alkilapp.databinding.ItemMensajeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MensajeAdapter(
    private val miUid: String
) : RecyclerView.Adapter<MensajeAdapter.ViewHolder>() {

    private val mensajes = mutableListOf<Mensaje>()
    private val horaFormato = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ViewHolder(val binding: ItemMensajeBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitList(nueva: List<Mensaje>) {
        mensajes.clear()
        mensajes.addAll(nueva)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMensajeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val m = mensajes[position]
        val b = holder.binding
        val ctx = holder.itemView.context
        val esMio = m.senderId == miUid

        b.tvBurbuja.text = m.text
        b.tvBurbuja.setTextColor(
            ctx.getColor(if (esMio) R.color.white else R.color.text_primary)
        )
        b.tvBurbuja.setBackgroundResource(
            if (esMio) R.drawable.bg_burbuja_mia else R.drawable.bg_burbuja_otro
        )

        val marginal = if (esMio) Gravity.END else Gravity.START
        b.tvBurbuja.layoutParams = (b.tvBurbuja.layoutParams as LinearLayout.LayoutParams).apply {
            gravity = marginal
        }
        b.tvTiempo.layoutParams = (b.tvTiempo.layoutParams as LinearLayout.LayoutParams).apply {
            gravity = marginal
        }
        b.tvBurbuja.backgroundTintList = null
        if (esMio) {
            b.tvBurbuja.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.alkil_primary)
            )
        }

        if (m.sentAt > 0) {
            b.tvTiempo.text = horaFormato.format(Date(m.sentAt))
            b.tvTiempo.visibility = View.VISIBLE
        } else {
            b.tvTiempo.visibility = View.GONE
        }

        b.llFilaMensaje.gravity = if (esMio) Gravity.END else Gravity.START
        b.tvBurbuja.visibility = if (m.text.isBlank()) View.GONE else View.VISIBLE
        b.root.setTag(m)
    }

    override fun getItemCount(): Int = mensajes.size
}