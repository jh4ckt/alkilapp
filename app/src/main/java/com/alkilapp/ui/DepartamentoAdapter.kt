package com.alkilapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alkilapp.data.Departamento
import com.alkilapp.databinding.ItemDepartamentoBinding

class DepartamentoAdapter(
    private val onClick: (Departamento) -> Unit
) : RecyclerView.Adapter<DepartamentoAdapter.ViewHolder>() {

    private var fullList: List<Departamento> = emptyList()
    private val items = mutableListOf<Departamento>()

    class ViewHolder(val binding: ItemDepartamentoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDepartamentoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTitulo.text = item.titulo
        holder.binding.tvDireccion.text = item.direccion
        holder.binding.tvPrecio.text = item.precio
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(nueva: List<Departamento>) {
        fullList = nueva
        filter("")
    }

    fun filter(texto: String) {
        val filtro = texto.trim().lowercase()
        items.clear()
        items.addAll(
            if (filtro.isEmpty()) fullList
            else fullList.filter {
                it.titulo.lowercase().contains(filtro) || it.direccion.lowercase().contains(filtro)
            }
        )
        notifyDataSetChanged()
    }
}