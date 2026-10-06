package com.alkilapp.ui

import com.alkilapp.data.Propiedad
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Logica de busqueda/filtros/orden del listado de inmuebles, extraida de
 * PropiedadAdapter para poder usarse desde Compose (LazyColumn) sin depender
 * de un RecyclerView. El adapter sigue con su propia copia mientras
 * FavoritosActivity no se migre; despues de eso este store es la unica fuente.
 *
 * Cada mutacion dispara [onChange] (la vista se suscribe) igual que antes lo
 * hacía notifyDataSetChanged(): contador, vacio, marcadores y lista Compose.
 */
class PropiedadesStore {

    /** Se invoca al final de cada aplicar() (nuevos items/filtros calculados). */
    var onChange: (() -> Unit)? = null

    private var fullList: List<Propiedad> = emptyList()
    private var items: List<Propiedad> = emptyList()
    private var query: String = ""
    private var filtroDepartamento: String? = null
    private var filtroDistrito: String? = null
    private var filtroTipo: String? = null
    private var filtroHabitaciones: Int? = null
    private var soloFavoritos = false
    private var favoritos: Set<String> = emptySet()
    private var latUsuario: Double? = null
    private var lngUsuario: Double? = null
    private var radioMaximoKm: Double = 5.0
    private var ignorarRadioPorFiltro = false
    private var propietariosVerificados: Map<String, Boolean> = emptyMap()
    // Zona por defecto del usuario (desde Mi Perfil)
    private var zonaDepartamento: String? = null
    private var zonaCiudad: String? = null

    /** Lista que está mostrando el listado tras aplicar búsqueda y filtros (incluye radio). */
    fun visibles(): List<Propiedad> = items

    /** Cantidad de items visibles (contador "N inmuebles encontrados"). */
    fun cantidad(): Int = items.size

    /** Lista completa SIN filtro de radio (para marcadores del mapa).
     * Siempre reaplica todos los filtros EXCEPTO el de distancia, porque los
     * marcadores deben aparecer en cuanto entran en la vista del mapa,
     * independientemente del radio de 5 km. */
    fun todasParaMapa(): List<Propiedad> {
        return fullList.filter { p -> coincideSinRadio(p) }
            .sortedWith(compareBy<Propiedad> { tier(it) })
    }

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

    /** Radio máximo en km para filtrar por distancia (centro = ubicación usuario).
     * Por defecto 5 km. */
    fun setRadioMaximoKm(radio: Double) {
        radioMaximoKm = radio.coerceAtLeast(0.0)
        aplicar()
    }

    /** Si true, desactiva el filtro de radio (usado cuando hay filtros explícitos activos). */
    fun setIgnorarRadioPorFiltro(ignorar: Boolean) {
        ignorarRadioPorFiltro = ignorar
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

    /** ¿El propietario de esta propiedad tiene badge de confianza? */
    fun verificado(p: Propiedad): Boolean = propietariosVerificados[p.idPropietario] == true

    /** Distancia en km desde la ubicación del usuario hasta la propiedad (null si no hay dato). */
    fun distanciaKm(p: Propiedad): Double? {
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

    private fun coincideSinRadio(p: Propiedad): Boolean {
        val deptoEfectivo = filtroDepartamento ?: zonaDepartamento
        val distritoEfectivo = filtroDistrito
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
        return buscaOk && deptoOk && distritoOk && tipoOk && habOk && favoritoOk
    }

    private fun aplicar() {
        val filtradas = fullList.filter { p ->
            val distanciaOk = ignorarRadioPorFiltro || latUsuario == null || lngUsuario == null ||
                distanciaKm(p) == null || distanciaKm(p)!! <= radioMaximoKm
            coincideSinRadio(p) && distanciaOk
        }
        // Orden: destacados > verificados > sin verificar; dentro de cada
        // grupo, primero los mas cercanos a la ubicacion del usuario.
        val orden = compareBy<Propiedad> { tier(it) }
        items = if (latUsuario == null || lngUsuario == null) {
            filtradas.sortedWith(orden)
        } else {
            filtradas.sortedWith(orden.thenBy { distanciaKm(it) ?: Double.MAX_VALUE })
        }
        onChange?.invoke()
    }

    /** 0 = destacado, 1 = propietario verificado, 2 = sin verificar. */
    private fun tier(p: Propiedad): Int = when {
        p.esDestacado -> 0
        verificado(p) -> 1
        else -> 2
    }
}

/** Normaliza el tipo a las 4 claves del filtro (igual que tipoMostrable del MainActivity). */
fun tipoClave(tipo: String): String {
    val t = tipo.trim().lowercase()
        .replace("ó", "o").replace("á", "a").replace("'", "")
    return when {
        t.contains("habitacion") || t.contains("cuarto") -> "habitacion"
        t.contains("departamento") || t.contains("depto") -> "departamento"
        t.contains("casa") -> "casa"
        else -> "otro"
    }
}
