package com.alkilapp.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alkilapp.R
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ItemPropiedadBinding
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class PropiedadAdapter(
    private val onClick: (Propiedad) -> Unit,
    private val onAlternarFavorito: (Propiedad) -> Unit = {}
) : RecyclerView.Adapter<PropiedadAdapter.ViewHolder>() {

    private var fullList: List<Propiedad> = emptyList()
    private val items = mutableListOf<Propiedad>()
    private var query: String = ""
    private var filtroDepartamento: String? = null
    private var filtroDistrito: String? = null
    private var filtroTipo: String? = null
    private var filtroHabitaciones: Int? = null
    private var soloFavoritos = false
    private var favoritos: Set<String> = emptySet()
    private var latUsuario: Double? = null
    private var lngUsuario: Double? = null
    private var propietariosVerificados: Map<String, Boolean> = emptyMap()
    // Zona por defecto del usuario (desde Mi Perfil)
    private var zonaDepartamento: String? = null
    private var zonaCiudad: String? = null

    private val cacheFotos = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {}

    class ViewHolder(val binding: ItemPropiedadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPropiedadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val binding = holder.binding
        val ctx = binding.root.context

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
                ctx.getString(
                    R.string.prop_distancia_m,
                    (distancia * 1000).roundToInt().toString()
                )
            else ->
                ctx.getString(
                    R.string.prop_distancia_km,
                    String.format("%.1f", distancia)
                )
        }

        // Detalles: habitaciones y superficie (texto secundario / text-muted).
        val hayAmbientes = item.ambientes > 0
        binding.tvAmbientes.visibility = if (hayAmbientes) View.VISIBLE else View.GONE
        binding.ivAmbientesIcon.visibility = if (hayAmbientes) View.VISIBLE else View.GONE
        if (hayAmbientes) {
            binding.tvAmbientes.text = ctx.getString(R.string.prop_ambientes, item.ambientes)
        }

        val haySuperficie = item.superficieM2 > 0
        binding.tvSuperficie.visibility = if (haySuperficie) View.VISIBLE else View.GONE
        binding.ivSuperficieIcon.visibility = if (haySuperficie) View.VISIBLE else View.GONE
        if (haySuperficie) {
            binding.tvSuperficie.text = ctx.getString(
                R.string.prop_superficie,
                String.format(Locale.US, "%.0f", item.superficieM2)
            )
        }

        // Insignia de estado (status-success para "Disponible").
        val estado = item.estadoNormalizado
        val textoEstado = when (estado) {
            "disponible" -> ctx.getString(R.string.prop_estado_disponible)
            "finalizado" -> ctx.getString(R.string.prop_estado_finalizado)
            "under_review" -> ctx.getString(R.string.prop_estado_revision)
            else -> ""
        }
        binding.tvEstadoBadge.visibility = if (textoEstado.isNotEmpty()) View.VISIBLE else View.GONE
        if (textoEstado.isNotEmpty()) {
            binding.tvEstadoBadge.text = textoEstado
            binding.tvEstadoBadge.backgroundTintList = ColorStateList.valueOf(
                ctx.getColor(
                    if (estado == "disponible") R.color.status_success_dark
                    else R.color.text_muted
                )
            )
        }

        // Destacado: borde acento + etiqueta flotante.
        val destacado = item.esDestacado
        binding.tvEtiquetaDestacado.visibility = if (destacado) View.VISIBLE else View.GONE
        binding.root.strokeColor = ctx.getColor(
            if (destacado) R.color.brand_gold else R.color.border_subtle
        )
        binding.root.strokeWidth = if (destacado) dp(2) else dp(1)

        // Propietario verificado: chip de confianza.
        val verificado = propietariosVerificados[item.idPropietario] == true
        binding.rowVerificado.visibility = if (verificado) View.VISIBLE else View.GONE

        // Favorito (corazon). El listado no toca Firestore: pide alternar al
        // contexto (MainActivity) que persiste local + nube y refresca el set.
        val esFav = favoritos.contains(item.id)
        binding.btnFavorito.setImageResource(
            if (esFav) R.drawable.ic_corazon_lleno else R.drawable.ic_corazon
        )
        binding.btnFavorito.setOnClickListener {
            onAlternarFavorito(item)
        }

        binding.btnVerDetalles.setOnClickListener {
                Log.d("AlkilApp", "btnVerDetalles click: ${item.titulo}")
                onClick(item)
            }
            binding.root.setOnClickListener {
                Log.d("AlkilApp", "root click: ${item.titulo}")
                onClick(item)
            }
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

    fun setFiltros(
        departamento: String?,
        distrito: String?,
        tipo: String? = null,
        habitaciones: Int? = null
    ) {
        filtroDepartamento = departamento
        filtroDistrito = distrito
        filtroTipo = tipo
        filtroHabitaciones = habitaciones
        aplicar()
    }

    fun setSoloFavoritos(activo: Boolean) {
        soloFavoritos = activo
        aplicar()
    }

    fun setFavoritos(ids: Set<String>) {
        favoritos = ids
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

    /** Establece la zona por defecto del usuario (departamento + ciudad/distrito).
     * Se aplica cuando no hay filtros explícitos activos. */
    fun setZona(departamento: String?, ciudad: String?) {
        zonaDepartamento = departamento
        zonaCiudad = ciudad
        aplicar()
    }

    private fun dp(valor: Int): Int =
        (valor * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    /** Normaliza el tipo a las 4 claves del filtro (igual que tipoMostrable del MainActivity). */
    private fun tipoClave(tipo: String): String {
        val t = tipo.trim().lowercase()
            .replace("ó", "o").replace("á", "a").replace("'", "")
        return when {
            t.contains("habitacion") || t.contains("cuarto") -> "habitacion"
            t.contains("departamento") || t.contains("depto") -> "departamento"
            t.contains("casa") -> "casa"
            else -> "otro"
        }
    }

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
        // La tarjeta muestra la foto a 150dp; en pantallas xxhdpi son ~420px.
        // Se decodifica en dos pasadas (bounds + inSampleSize) para no pixelar
        // ni inflar memoria, y se ajusta al ancho real (nunca se agranda).
        val objetivo = dp(420)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = factorMuestra(bounds.outWidth, bounds.outHeight, objetivo)
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val escala = objetivo.toFloat() / max(bmp.width, bmp.height)
        val thumb = if (escala >= 1f) bmp else {
            val w = (bmp.width * escala).toInt().coerceAtLeast(1)
            val h = (bmp.height * escala).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
        }
        cacheFotos.put(key, thumb)
        return thumb
    }

    /** Mayor potencia de 2 que deja la imagen decodificada >= `objetivo` px. */
    private fun factorMuestra(ancho: Int, alto: Int, objetivo: Int): Int {
        var muestra = 1
        var mayor = max(ancho, alto)
        while (mayor / 2 >= objetivo) {
            mayor /= 2
            muestra *= 2
        }
        return muestra
    }

    private fun aplicar() {
        items.clear()
        // Filtro efectivo: explícito gana sobre zona por defecto
        val deptoEfectivo = filtroDepartamento ?: zonaDepartamento
        val distritoEfectivo = filtroDistrito ?: (if (filtroDepartamento == null) zonaCiudad else null)

        val filtradas = fullList.filter { p ->
            val buscaOk = query.isEmpty() ||
                listOf(p.titulo, p.direccion, p.barrio, p.tipo, p.ciudad)
                    .any { it.lowercase().contains(query) }
            val deptoOk = deptoEfectivo == null ||
                p.ciudad.equals(deptoEfectivo, ignoreCase = true)
            val distritoOk = distritoEfectivo == null ||
                p.barrio.equals(distritoEfectivo, ignoreCase = true)
            val tipoOk = filtroTipo == null || tipoClave(p.tipo) == filtroTipo
            val habOk = filtroHabitaciones == null ||
                (if (filtroHabitaciones == 4) p.ambientes >= 4 else p.ambientes == filtroHabitaciones)
            val favoritoOk = !soloFavoritos || p.id in favoritos
            buscaOk && deptoOk && distritoOk && tipoOk && habOk && favoritoOk
        }
        // Orden: destacados > verificados > sin verificar; dentro de cada
        // grupo, primero los mas cercanos a la ubicacion del usuario.
        val orden = compareBy<Propiedad> { tier(it) }
        items.addAll(
            if (latUsuario == null || lngUsuario == null) {
                filtradas.sortedWith(orden)
            } else {
                filtradas.sortedWith(orden.thenBy { distanciaKm(it) ?: Double.MAX_VALUE })
            }
        )
        notifyDataSetChanged()
    }

    /** 0 = destacado, 1 = propietario verificado, 2 = sin verificar. */
    private fun tier(p: Propiedad): Int = when {
        p.esDestacado -> 0
        propietariosVerificados[p.idPropietario] == true -> 1
        else -> 2
    }
}