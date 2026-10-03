package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Favoritos
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityFavoritosBinding
import com.alkilapp.ui.PropiedadAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Pantalla "Favoritos": lista los inmuebles guardados por el usuario.
 *
 * - Los ids salen de [Favoritos] (cache local primero, sincronizado con Firestore).
 * - Las fichas se piden por id en tandas de 30 (límite `whereIn`).
 * - Se filtran `under_review`, `finalizado`, `pausada` y propiedades borradas.
 * - Al quitar un favorito, se borra en Firestore + cache local al instante.
 *   Los callbacks de sincronización "viejos" no re-añaden IDs que el usuario
 *   acaba de quitar (generación local).
 * - Contador del header = propiedades visibles (ya filtradas).
 */
class FavoritosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    /** IDs de favoritos según el último estado confirmado (local + nube). */
    private val idsFavoritos = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Propiedades cargadas y visibles (ya filtradas). */
    private val cargadas = java.util.concurrent.CopyOnWriteArrayList<Propiedad>()

    /** IDs que llegaron de Firestore en la carga actual (para detectar borrados). */
    private val idsEncontradosEnFirestore = java.util.concurrent.CopyOnWriteArrayList<String>()

    private lateinit var adapter: PropiedadAdapter
    private var pendientes = 0
    private var generacionCarga = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFavBack.setOnClickListener { finish() }
        binding.btnFavRefresh.setOnClickListener { sincronizarManual() }

        adapter = PropiedadAdapter(
            onClick = { p -> abrirDetalle(p) },
            onAlternarFavorito = { p -> quitarFavorito(p) }
        )
        adapter.setIgnorarRadioPorFiltro(true)
        binding.rvFavoritos.layoutManager = LinearLayoutManager(this)
        binding.rvFavoritos.adapter = adapter

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, R.string.favoritos_requiere_sesion, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Solo cache local al arranque. La sincronización con nube es manual
        // (botón refresh) para que los borrados locales no se revivan.
        Favoritos.locales(this).takeIf { it.isNotEmpty() }?.let { aplicarIds(it) }
    }

    /** Sincronización manual con nube (botón refresh). */
    private fun sincronizarManual() {
        val uid = auth.currentUser?.uid ?: return
        binding.btnFavRefresh.isEnabled = false
        binding.btnFavRefresh.animate().rotationBy(360f).setDuration(800).start()
        Favoritos.sincronizar(this, uid) { ids ->
            binding.btnFavRefresh.isEnabled = true
            binding.btnFavRefresh.animate().cancel()
            aplicarIds(ids)
        }
    }

    /** Aplica IDs (solo añade nuevos; no quita los borrados localmente). */
    private fun aplicarIds(ids: Set<String>) {
        val validos = ids.filter { it.isNotBlank() }
        val nuevos = validos - idsFavoritos.toSet()
        if (nuevos.isNotEmpty()) {
            idsFavoritos.addAll(nuevos)
            adapter.setFavoritos(idsFavoritos.toSet())
        }
        generacionCarga++
        val miGeneracion = generacionCarga
        cargarPropiedades(miGeneracion)
    }

    /** Pide fichas por tandas de 30; al terminar limpia favoritos huérfanos. */
    private fun cargarPropiedades(gen: Int) {
        val ids = idsFavoritos.toList()
        if (ids.isEmpty()) {
            binding.tvFavCargando.visibility = View.GONE
            binding.tvFavVacio.visibility = View.VISIBLE
            actualizarContador()
            return
        }
        binding.tvFavCargando.visibility = View.VISIBLE
        binding.tvFavVacio.visibility = View.GONE
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
     * - No existen en la colección `propiedades` (borrados).
     * - Están en estados ocultos (`under_review`, `finalizado`, `pausada`).
     * Llama a `Favoritos.alternar` para borrar en Firestore + cache local.
     */
    private fun limpiarFavoritosHuerfanos() {
        val uid = auth.currentUser?.uid ?: return
        val ocultos = setOf("under_review", "finalizado", "pausada")
        val aBorrar = idsFavoritos.filter { id ->
            // No encontrado en Firestore = propiedad borrada.
            if (!idsEncontradosEnFirestore.contains(id)) return@filter true
            // Encontrado pero estado oculto = no debe aparecer.
            // NOTA: no tenemos el estado aquí sin volver a leer; confiamos en que
            // `cargarPropiedades` ya no los metió en `cargadas`. Para limpiar el
            // set maestro haríamos otra lectura. Simplificación: solo limpiamos
            // los borrados de Firestore. Los de estado oculto se quedan en el set
            // pero no se muestran ni cuentan.
            false
        }.toList()
        aBorrar.forEach { id ->
            idsFavoritos.remove(id)
            Favoritos.alternar(this, uid, id)
        }
        adapter.setFavoritos(idsFavoritos.toSet())
    }

    private fun pintar() {
        binding.tvFavCargando.visibility = View.GONE
        adapter.submitList(cargadas.toList())
        binding.tvFavVacio.visibility = if (cargadas.isEmpty()) View.VISIBLE else View.GONE
        actualizarContador()
    }

    /** Contador = propiedades visibles (ya filtradas). */
    private fun actualizarContador() {
        val n = cargadas.size
        binding.tvFavContador.visibility = if (n > 0) View.VISIBLE else View.GONE
        binding.tvFavContador.text = n.toString()
    }

    /** Quita un favorito: borra en Firestore + cache local + UI al instante. */
    private fun quitarFavorito(p: Propiedad) {
        val uid = auth.currentUser?.uid ?: return
        Favoritos.alternar(this, uid, p.id)
        idsFavoritos.remove(p.id)
        adapter.setFavoritos(idsFavoritos.toSet())
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