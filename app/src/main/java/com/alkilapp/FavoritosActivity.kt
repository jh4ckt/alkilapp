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
 * Los ids salen de [Favoritos] (cache local primero, sincronizado con Firestore)
 * y las fichas se piden por id, en tandas de 30 porque `whereIn` no admite mas.
 * Se repiten los listeners de la ficha para que, al volver, el corazon y la
 * estrella de destacado esten al dia.
 */
class FavoritosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    /** Ids que el usuario tiene marcados como favorito (semaforo). */
    private val idsFavoritos = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Propiedades ya cargadas, en el orden en que se fueron Trayendo. */
    private val cargadas = java.util.concurrent.CopyOnWriteArrayList<Propiedad>()

    private lateinit var adapter: PropiedadAdapter
    private var pendientes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFavBack.setOnClickListener { finish() }

        adapter = PropiedadAdapter(
            onClick = { p -> abrirDetalle(p) },
            onAlternarFavorito = { p -> quitarFavorito(p) }
        )
        // Sin filtro de radio: un favorito se guarda porque interesa, no porque
        // este cerca ahora mismo.
        adapter.setIgnorarRadioPorFiltro(true)
        binding.rvFavoritos.layoutManager = LinearLayoutManager(this)
        binding.rvFavoritos.adapter = adapter

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, R.string.favoritos_requiere_sesion, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Pinta al instante con el cache local y luego corrige con la nube.
        Favoritos.locales(this).takeIf { it.isNotEmpty() }?.let { aplicarIds(it) }
        Favoritos.sincronizar(this, uid) { ids -> aplicarIds(ids) }
    }

    /** Recibe el set de ids y (re)pide las fichas correspondientes. */
    private fun aplicarIds(ids: Set<String>) {
        val validos = ids.filter { it.isNotBlank() }
        idsFavoritos.clear()
        idsFavoritos.addAll(validos)
        adapter.setFavoritos(idsFavoritos.toSet())
        actualizarContador()
        cargarPropiedades()
    }

    /** Pide las fichas por tandas de 30 y filtra los estados que no se publican. */
    private fun cargarPropiedades() {
        val ids = idsFavoritos.toList()
        if (ids.isEmpty()) {
            binding.tvFavCargando.visibility = View.GONE
            binding.tvFavVacio.visibility = View.VISIBLE
            return
        }
        binding.tvFavCargando.visibility = View.VISIBLE
        binding.tvFavVacio.visibility = View.GONE
        pendientes = 0
        ids.chunked(LOTE).forEach { tanda ->
            pendientes++
            db.collection("propiedades")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), tanda)
                .get()
                .addOnCompleteListener { tarea ->
                    pendientes--
                    val docs = tarea.result?.documents ?: emptyList()
                    docs.forEach { doc ->
                        Propiedad.desde(doc)?.let { p ->
                            if (p.estado != "under_review" && p.estado != "finalizado" &&
                                p.estado != "pausada"
                            ) {
                                cargadas.removeAll { it.id == p.id }
                                cargadas.add(p)
                            }
                        }
                    }
                    if (pendientes == 0) pintar()
                }
        }
    }

    private fun pintar() {
        binding.tvFavCargando.visibility = View.GONE
        adapter.submitList(cargadas.toList())
        binding.tvFavVacio.visibility =
            if (cargadas.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun actualizarContador() {
        val n = idsFavoritos.size
        binding.tvFavContador.visibility = if (n > 0) View.VISIBLE else View.GONE
        binding.tvFavContador.text = n.toString()
    }

    /** Quita un favorito: se borra en Firestore y del listado al instante. */
    private fun quitarFavorito(p: Propiedad) {
        val uid = auth.currentUser?.uid ?: return
        Favoritos.alternar(this, uid, p.id)
        idsFavoritos.remove(p.id)
        adapter.setFavoritos(idsFavoritos.toSet())
        cargadas.removeAll { it.id == p.id }
        pintar()
        actualizarContador()
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
            // NO pasar fotos base64 por el intent: supera el límite de Binder
            // (TransactionTooLargeException). El detalle las carga por ID desde Firestore.
        })
    }

    companion object {
        /** Firestore no acepta mas de 30 valores en un `whereIn`. */
        private const val LOTE = 30
    }
}
