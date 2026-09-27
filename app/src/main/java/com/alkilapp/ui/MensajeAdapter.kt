package com.alkilapp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.alkilapp.R
import com.alkilapp.data.Mensaje
import com.alkilapp.data.TipoMensaje
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MensajeAdapter(
    private val miUid: String,
    private val onAceptar: ((Mensaje) -> Unit)? = null,
    private val onRechazar: ((Mensaje) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_TEXTO = 0
        const val TYPE_PROPUESTA = 1
        const val TYPE_SISTEMA = 2
    }

    private val mensajes = mutableListOf<Mensaje>()
    private val horaFormato = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val coloresAvatar = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4, R.color.avatar_5
    )

    class TextoHolder(val binding: View) : RecyclerView.ViewHolder(binding)
    class PropuestaHolder(val binding: View) : RecyclerView.ViewHolder(binding)
    class SistemaHolder(val binding: View) : RecyclerView.ViewHolder(binding)

    fun submitList(nueva: List<Mensaje>) {
        mensajes.clear()
        mensajes.addAll(nueva)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (mensajes[position].tipo) {
            TipoMensaje.PROPUESTA -> TYPE_PROPUESTA
            TipoMensaje.SISTEMA -> TYPE_SISTEMA
            else -> TYPE_TEXTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PROPUESTA -> PropuestaHolder(inflater.inflate(R.layout.item_mensaje, parent, false))
            TYPE_SISTEMA -> SistemaHolder(inflater.inflate(R.layout.item_mensaje, parent, false))
            else -> TextoHolder(inflater.inflate(R.layout.item_mensaje, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = mensajes[position]
        when (holder) {
            is TextoHolder -> bindTexto(holder.itemView, m)
            is PropuestaHolder -> bindPropuesta(holder.itemView, m)
            is SistemaHolder -> bindSistema(holder.itemView, m)
        }
    }

    private fun bindTexto(itemView: View, m: Mensaje) {
        val ctx = itemView.context
        val esMio = m.senderId == miUid

        val tvBurbuja = itemView.findViewById<TextView>(R.id.tvBurbuja)
        val tvTiempo = itemView.findViewById<TextView>(R.id.tvTiempo)
        val llPropuesta = itemView.findViewById<View>(R.id.llPropuesta)
        val tvSistema = itemView.findViewById<TextView>(R.id.tvSistema)
        val llFila = itemView.findViewById<LinearLayout>(R.id.llFilaMensaje)
        val llGrupo = itemView.findViewById<LinearLayout>(R.id.llGrupoMensaje)

        // Solo mostrar burbuja de texto
        tvBurbuja.visibility = View.VISIBLE
        llPropuesta.visibility = View.GONE
        tvSistema.visibility = View.GONE

        tvBurbuja.text = m.text
        tvBurbuja.setTextColor(ctx.getColor(if (esMio) R.color.white else R.color.text_primary))
        tvBurbuja.setBackgroundResource(if (esMio) R.drawable.bg_burbuja_mia else R.drawable.bg_burbuja_otro)

        val marginal = if (esMio) Gravity.END else Gravity.START
        tvBurbuja.layoutParams = (tvBurbuja.layoutParams as LinearLayout.LayoutParams).apply { gravity = marginal }
        tvTiempo.layoutParams = (tvTiempo.layoutParams as LinearLayout.LayoutParams).apply { gravity = marginal }
        tvBurbuja.backgroundTintList = null
        if (esMio) {
            tvBurbuja.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.alkil_coral))
        }

        if (m.sentAt > 0) {
            tvTiempo.text = horaFormato.format(Date(m.sentAt))
            tvTiempo.visibility = View.VISIBLE
        } else {
            tvTiempo.visibility = View.GONE
        }

        llGrupo.gravity = if (esMio) Gravity.END else Gravity.START
        llFila.gravity = if (esMio) Gravity.END else Gravity.START
        tvBurbuja.visibility = if (m.text.isBlank()) View.GONE else View.VISIBLE
        itemView.setTag(m)
    }

    private fun bindPropuesta(itemView: View, m: Mensaje) {
        val ctx = itemView.context
        val esMio = m.senderId == miUid
        val soyElDestinatario = m.propuestoPor != null && m.propuestoPor != miUid

        val tvBurbuja = itemView.findViewById<TextView>(R.id.tvBurbuja)
        val llPropuesta = itemView.findViewById<LinearLayout>(R.id.llPropuesta)
        val tvSistema = itemView.findViewById<TextView>(R.id.tvSistema)
        val tvTiempo = itemView.findViewById<TextView>(R.id.tvTiempo)
        val tvPropMonto = itemView.findViewById<TextView>(R.id.tvPropMonto)
        val tvPropDetalle = itemView.findViewById<TextView>(R.id.tvPropDetalle)
        val llPropBotones = itemView.findViewById<LinearLayout>(R.id.llPropBotones)
        val btnAceptar = itemView.findViewById<Button>(R.id.btnPropAceptar)
        val btnRechazar = itemView.findViewById<Button>(R.id.btnPropRechazar)

        tvBurbuja.visibility = View.GONE
        llPropuesta.visibility = View.VISIBLE
        tvSistema.visibility = View.GONE

        // Monto
        val monto = m.monto?.let { String.format(Locale.US, "%.0f", it) } ?: "0"
        tvPropMonto.text = "$monto ${m.moneda}"

        // Detalle: quién propuso y estado
        val propuestoPor = m.propuestoPor ?: ""
        val estado = m.propuestaEstado?.lowercase() ?: "pendiente"
        val propositorEsMio = propuestoPor == miUid

        when (estado) {
            "aceptada" -> tvPropDetalle.text = if (propositorEsMio) "Aceptada ✓" else "Aceptaste ✓"
            "rechazada" -> tvPropDetalle.text = if (propositorEsMio) "Rechazada ✗" else "Rechazaste ✗"
            else -> tvPropDetalle.text = if (propositorEsMio) "Esperando respuesta..." else "Tu respuesta"
        }

        // Botones solo si soy el destinatario y está pendiente
        if (soyElDestinatario && estado == "pendiente") {
            llPropBotones.visibility = View.VISIBLE
            btnAceptar.setOnClickListener { onAceptar?.invoke(m) }
            btnRechazar.setOnClickListener { onRechazar?.invoke(m) }
        } else {
            llPropBotones.visibility = View.GONE
        }

        // Alineación: propuestas del dueño a la derecha si soy yo
        val marginal = if (esMio) Gravity.END else Gravity.START
        val grupo = itemView.findViewById<LinearLayout>(R.id.llGrupoMensaje)
        grupo.gravity = marginal

        if (m.sentAt > 0) {
            tvTiempo.text = horaFormato.format(Date(m.sentAt))
            tvTiempo.visibility = View.VISIBLE
            tvTiempo.layoutParams = (tvTiempo.layoutParams as LinearLayout.LayoutParams).apply { gravity = marginal }
        } else {
            tvTiempo.visibility = View.GONE
        }

        itemView.setTag(m)
    }

    private fun bindSistema(itemView: View, m: Mensaje) {
        val tvSistema = itemView.findViewById<TextView>(R.id.tvSistema)
        val tvBurbuja = itemView.findViewById<TextView>(R.id.tvBurbuja)
        val llPropuesta = itemView.findViewById<View>(R.id.llPropuesta)
        val tvTiempo = itemView.findViewById<TextView>(R.id.tvTiempo)
        val llFila = itemView.findViewById<LinearLayout>(R.id.llFilaMensaje)

        tvBurbuja.visibility = View.GONE
        llPropuesta.visibility = View.GONE
        tvSistema.visibility = View.VISIBLE

        tvSistema.text = m.text
        tvSistema.gravity = Gravity.CENTER_HORIZONTAL

        tvTiempo.visibility = if (m.sentAt > 0) View.VISIBLE else View.GONE
        if (m.sentAt > 0) tvTiempo.text = horaFormato.format(Date(m.sentAt))

        llFila.gravity = Gravity.CENTER_HORIZONTAL
        itemView.setTag(m)
    }

    override fun getItemCount(): Int = mensajes.size
}