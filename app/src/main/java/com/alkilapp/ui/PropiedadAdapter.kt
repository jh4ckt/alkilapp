package com.alkilapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alkilapp.R
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ItemPropiedadBinding
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class PropiedadAdapter(
    private val onClick: (Propiedad) -> Unit
) : RecyclerView.Adapter<PropiedadAdapter.ViewHolder>() {

    private var fullList: List<Propiedad> = emptyList()
    private val items = mutableListOf<Propiedad>()
    private var query: String = ""
    private var filtroDepartamento: String? = null
    private var filtroDistrito: String? = null
    private var latUsuario: Double? = null
    private var lngUsuario: Double? = null
    private var propietariosVerificados: Map<String, Boolean> = emptyMap()

    private val cacheFotos = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {}

    class ViewHolder(val binding: ItemPropiedadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPropiedadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val binding = holder.binding
        binding.tvTitulo.text = item.titulo
        binding.tvDireccion.text = item.direccion.takeIf { it.isNotBlank() }
            ?: item.tipo.replaceFirstChar { it.uppercase() } + " en " + item.barrio
        binding.tvPrecio.text = item.precioFormateado

        val foto = item.fotos.firstOrNull()
        if (foto != null) {
            binding.ivFoto.visibility = View.VISIBLE
            binding.ivFoto.setImageBitmap(decodificarThumb(foto))
        } else {
            binding.ivFoto.visibility = View.GONE
            binding.ivFoto.setImageDrawable(null)
        }

        val distancia = distanciaKm(item)
        binding.tvDistancia.text = when {
            distancia == null -> ""
            distancia < 1.0 ->
                binding.root.context.getString(
                    R.string.prop_distancia_m,
                    (distancia * 1000).roundToInt().toString()
                )
            else ->
                binding.root.context.getString(
                    R.string.prop_distancia_km,
                    String.format("%.1f", distancia)
                )
        }

        // Destacado: borde dorado sutil + etiqueta flotante (estilo "Plus" de Airbnb).
        val destacado = item.esDestacado
        val ctx = binding.root.context
        binding.tvEtiquetaDestacado.visibility = if (destacado) View.VISIBLE else View.GONE
        binding.root.strokeColor = ctx.getColor(
            if (destacado) R.color.alkil_gold else R.color.divider
        )
        binding.root.strokeWidth =
            if (destacado) dp(2) else 0

        // Propietario verificado: chip de confianza.
        val verificado = propietariosVerificados[item.idPropietario] == true
        binding.rowVerificado.visibility = if (verificado) View.VISIBLE else View.GONE

        binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    /** Lista que está mostrando el adapter tras aplicar búsqueda y filtros. */
    fun visibles(): List<Propiedad> = items.toList()

    fun submitList(nueva: List<Propiedad>) {
        fullList = nueva
        aplicar()
    }

    fun filter(texto: String) {
        query = texto.trim().lowercase()
        aplicar()
    }

    fun setFiltros(departamento: String?, distrito: String?) {
        filtroDepartamento = departamento
        filtroDistrito = distrito
        aplicar()
    }

    fun setUbicacion(lat: Double?, lng: Double?) {
        latUsuario = lat
        lngUsuario = lng
        aplicar()
    }

    /** Mapa uid-propietario -> ¿verificado? (para el chip de confianza en la tarjeta). */
    fun setPropietariosVerificados(mapa: Map<String, Boolean>) {
        propietariosVerificados = mapa
        aplicar()
    }

    private fun dp(valor: Int): Int =
        (valor * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    /** Distancia en km desde la ubicación del usuario hasta la propiedad (null si no hay dato). */
    private fun distanciaKm(p: Propiedad): Double? {
        val lat = latUsuario ?: return null
        val lng = lngUsuario ?: return null
        if (p.lat == 0.0 && p.lng == 0.0) return null
        val radioTierra = 6371.0
        val dLat = Math.toRadians(p.lat - lat)
        val dLng = Math.toRadians(p.lng - lng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(p.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * radioTierra * asin(sqrt(a))
    }

    private fun decodificarThumb(foto: String): Bitmap? {
        val key = foto.take(32) + "|" + foto.length
        cacheFotos.get(key)?.let { return it }
        val bytes = try {
            Base64.decode(foto, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val ladoMax = 144
        val escala = ladoMax.toFloat() / max(bmp.width, bmp.height)
        val w = (bmp.width * escala).toInt().coerceAtLeast(1)
        val h = (bmp.height * escala).toInt().coerceAtLeast(1)
        val thumb = if (w == bmp.width && h == bmp.height) bmp else {
            Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
        }
        cacheFotos.put(key, thumb)
        return thumb
    }

    private fun aplicar() {
        items.clear()
        val filtradas = fullList.filter { p ->
            val buscaOk = query.isEmpty() ||
                listOf(p.titulo, p.direccion, p.barrio, p.tipo, p.ciudad)
                    .any { it.lowercase().contains(query) }
            val deptoOk = filtroDepartamento == null ||
                p.ciudad.equals(filtroDepartamento, ignoreCase = true)
            val distritoOk = filtroDistrito == null ||
                p.barrio.equals(filtroDistrito, ignoreCase = true)
            buscaOk && deptoOk && distritoOk
        }
        items.addAll(
            if (latUsuario == null || lngUsuario == null) {
                filtradas
            } else {
                filtradas.sortedWith(
                    compareBy<Propiedad> { if (it.esDestacado) 0 else 1 }
                        .thenBy { distanciaKm(it) ?: Double.MAX_VALUE }
                )
            }
        )
        notifyDataSetChanged()
    }
}