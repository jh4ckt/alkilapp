package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.ItemLista
import com.alkilapp.compose.PantallaFavoritos
import com.alkilapp.data.Favoritos
import com.alkilapp.data.Propiedad
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Pantalla "Favoritos": lista los inmuebles guardados por el usuario.
 *
 * - Los ids salen de [Favoritos] (cache local primero, sincronizado con Firestore).
 * - Las fichas se piden por id en tandas de 30 (limite `whereIn`).
 * - Se filtran `under_review`, `finalizado`, `pausada` y propiedades borradas.
 * - Al quitar un favorito, se borra en Firestore + cache local al instante.
 *   Los callbacks de sincronizacion "viejos" no re-anaden IDs que el usuario
 *   acaba de quitar (generacion local).
 * - Contador del header = propiedades visibles (ya filtradas).
 */
class FavoritosActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    /** IDs de favoritos segun el ultimo estado confirmado (local + nube). */
    private val idsFavoritos = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Propiedades cargadas y visibles (ya filtradas). */
    private val cargadas = java.util.concurrent.CopyOnWriteArrayList<Propiedad>()

    /** IDs que llegaron de Firestore en la carga actual (para detectar borrados). */
    private val idsEncontradosEnFirestore = java.util.concurrent.CopyOnWriteArrayList<String>()

    private var cargando by mutableStateOf(false)
    private var sincronizando by mutableStateOf(false)
    private var items by mutableStateOf<List<ItemLista>>(emptyList())

    private var pendientes = 0
    private var generacionCarga = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, R.string.favoritos_requiere_sesion, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            AlkilTheme {
                PantallaFavoritos(
                    cargando = cargando,
                    sincronizando = sincronizando,
                    items = items,
                    onAtras = { finish() },
                    onActualizar = { sincronizarManual() },
                    onItem = { p -> abrirDetalle(p) },
                    onQuitarFavorito = { p -> quitarFavorito(p) }
                )
            }
        }

        // Solo cache local al arranque. La sincronizacion con nube es manual
        // (boton refresh) para que los borrados locales no se revivan.
        Favoritos.locales(this).takeIf { it.isNotEmpty() }?.let { aplicarIds(it) }
    }

    /** Sincronizacion manual con nube (boton refresh). */
    private fun sincronizarManual() {
        val uid = auth.currentUser?.uid ?: return
        if (sincronizando) return
        sincronizando = true
        Favoritos.sincronizar(this, uid) { ids ->
            sincronizando = false
            aplicarIds(ids)
        }
    }

    /** Aplica IDs (solo anade nuevos; no quita los borrados localmente). */
    private fun aplicarIds(ids: Set<String>) {
        val validos = ids.filter { it.isNotBlank() }
        val nuevos = validos - idsFavoritos.toSet()
        if (nuevos.isNotEmpty()) {
            idsFavoritos.addAll(nuevos)
        }
        generacionCarga++
        val miGeneracion = generacionCarga
        cargarPropiedades(miGeneracion)
    }

    /** Pide fichas por tandas de 30; al terminar limpia favoritos huerfanos. */
    private fun cargarPropiedades(gen: Int) {
        val ids = idsFavoritos.toList()
        if (ids.isEmpty()) {
            cargando = false
            pintar()
            return
        }
        cargando = true
        idsEncontradosEnFirestore.clear()
        pendientes = 0
        ids.chunked(LOTE).forEach { tanda ->
            pendientes++
            db.collection("propiedades")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), tanda)
                .get()
                .addOnCompleteListener { tarea ->
                    if (gen != generacionCarga) return@addOnCompleteListener // carga supersedida
                    pendientes--
                    val docs = tarea.result?.documents ?: emptyList()
                    docs.forEach { doc ->
                        val pid = doc.id
                        idsEncontradosEnFirestore.add(pid)
                        Propiedad.desde(doc)?.let { p ->
                            if (p.estado != "under_review" && p.estado != "finalizado" &&
                                p.estado != "pausada"
                            ) {
                                cargadas.removeAll { it.id == p.id }
                                cargadas.add(p)
                            }
                        }
                    }
                    if (pendientes == 0) {
                        limpiarFavoritosHuerfanos()
                        pintar()
                    }
                }
        }
    }

    /**
     * Quita de favoritos los IDs que:
     * - No existen en la coleccion `propiedades` (borrados).
     * - Estan en estados ocultos (`under_review`, `finalizado`, `pausada`).
     * Llama a `Favoritos.alternar` para borrar en Firestore + cache local.
     */
    private fun limpiarFavoritosHuerfanos() {
        val uid = auth.currentUser?.uid ?: return
        val aBorrar = idsFavoritos.filter { id ->
            // No encontrado en Firestore = propiedad borrada.
            !idsEncontradosEnFirestore.contains(id)
            // Encontrado pero estado oculto = no debe aparecer.
            // NOTA: no tenemos el estado aqui sin volver a leer; confiamos en que
            // `cargarPropiedades` ya no los metio en `cargadas`. Para limpiar el
            // set maestro hariamos otra lectura. Simplificacion: solo limpiamos
            // los borrados de Firestore. Los de estado oculto se quedan en el set
            // pero no se muestran ni cuentan.
        }.toList()
        aBorrar.forEach { id ->
            idsFavoritos.remove(id)
            Favoritos.alternar(this, uid, id)
        }
    }

    private fun pintar() {
        cargando = false
        items = cargadas
            .sortedWith(compareBy<Propiedad> { if (it.esDestacado) 0 else 1 })
            .map { ItemLista(propiedad = it, verificado = false, favorito = true, distanciaKm = null) }
    }

    /** Quita un favorito: borra en Firestore + cache local + UI al instante. */
    private fun quitarFavorito(p: Propiedad) {
        val uid = auth.currentUser?.uid ?: return
        Favoritos.alternar(this, uid, p.id)
        idsFavoritos.remove(p.id)
        cargadas.removeAll { it.id == p.id }
        pintar()
    }

    private fun abrirDetalle(p: Propiedad) {
        startActivity(Intent(this, PropiedadDetalleActivity::class.java).apply {
            putExtra(PropiedadDetalleActivity.EXTRA_ID, p.id)
            putExtra(PropiedadDetalleActivity.EXTRA_TITULO, p.titulo)
            putExtra(PropiedadDetalleActivity.EXTRA_DESCRIPCION, p.descripcion)
            putExtra(PropiedadDetalleActivity.EXTRA_TIPO, p.tipo)
            putExtra(PropiedadDetalleActivity.EXTRA_OPERACION, p.operacion)
            putExtra(PropiedadDetalleActivity.EXTRA_PRECIO, p.precio)
            putExtra(PropiedadDetalleActivity.EXTRA_MONEDA, p.moneda)
            putExtra(PropiedadDetalleActivity.EXTRA_DIRECCION, p.direccion)
            putExtra(PropiedadDetalleActivity.EXTRA_BARRIO, p.barrio)
            putExtra(PropiedadDetalleActivity.EXTRA_CIUDAD, p.ciudad)
            putExtra(PropiedadDetalleActivity.EXTRA_LAT, p.lat)
            putExtra(PropiedadDetalleActivity.EXTRA_LNG, p.lng)
            putExtra(PropiedadDetalleActivity.EXTRA_ID_PROPIETARIO, p.idPropietario)
            putExtra(PropiedadDetalleActivity.EXTRA_AMBIENTES, p.ambientes)
            putExtra(PropiedadDetalleActivity.EXTRA_SUPERFICIE, p.superficieM2)
            putStringArrayListExtra(
                PropiedadDetalleActivity.EXTRA_COMODIDADES,
                ArrayList(p.comodidades)
            )
            putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, p.esDestacado)
            putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, p.estado)
        })
    }

    companion object {
        private const val LOTE = 30
    }
}
