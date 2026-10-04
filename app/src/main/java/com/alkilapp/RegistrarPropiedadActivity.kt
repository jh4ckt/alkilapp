package com.alkilapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.alkilapp.databinding.ActivityRegistrarPropiedadBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.AddressComponent
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.annotation.SuppressLint
import android.view.Gravity
import java.text.Normalizer
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Formulario de alta de inmueble en pantalla completa (reemplaza al pop-up).
 * La ubicacion se toma de la posicion actual del usuario (o la pasada por MainActivity).
 */
class RegistrarPropiedadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrarPropiedadBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var paginasPublicacion: List<View>
    private var pasoPublicacion = 0

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private val fotosFormulario = mutableListOf<String>()
    private var latAgregar = 0.0
    private var lngAgregar = 0.0

    private val placesClient by lazy { Places.createClient(this) }
    private val debounceBusqueda = Handler(Looper.getMainLooper())
    private var consultaBusquedaActual = 0
    private val rectPeru = LatLngBounds(
        LatLng(-18.6, -81.5),
        LatLng(-0.5, -68.0)
    )

    private val fotosLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        val cupo = MAX_FOTOS - fotosFormulario.size
        if (cupo <= 0) {
            Toast.makeText(this, R.string.prop_fotos_llena, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        for (uri in uris.take(cupo)) {
            val base64 = comprimirFoto(uri)
            if (base64 == null) {
                Toast.makeText(this, R.string.prop_fotos_error, Toast.LENGTH_SHORT).show()
            } else {
                fotosFormulario.add(base64)
            }
        }
        renderizarPreviewsFotos(false)
    }

    private val mapaSeleccionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != RESULT_OK) return@registerForActivityResult
        val data = resultado.data ?: return@registerForActivityResult
        val lat = data.getDoubleExtra(MapaSeleccionActivity.EXTRA_LAT_RESULTADO, 0.0)
        val lng = data.getDoubleExtra(MapaSeleccionActivity.EXTRA_LNG_RESULTADO, 0.0)
        if (lat != 0.0 || lng != 0.0) {
            latAgregar = lat
            lngAgregar = lng
            binding.tvPropUbicacionInfo.text = getString(
                R.string.prop_ubicacion_mapa_seleccionada,
                "%.6f".format(lat),
                "%.6f".format(lng)
            )
            rellenarDireccionDesdeMapa(lat, lng)
        }
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_EDIT_MODE = "edit_mode"
        const val EXTRA_PROPIEDAD_ID = "propiedad_id"
        const val EXTRA_TITULO = "extra_titulo"
        const val EXTRA_DESCRIPCION = "extra_descripcion"
        const val EXTRA_TIPO = "extra_tipo"
        const val EXTRA_OPERACION = "extra_operacion"
        const val EXTRA_PRECIO = "extra_precio"
        const val EXTRA_MONEDA = "extra_moneda"
        const val EXTRA_DIRECCION = "extra_direccion"
        const val EXTRA_BARRIO = "extra_barrio"
        const val EXTRA_CIUDAD = "extra_ciudad"
        const val EXTRA_AMBIENTES = "extra_ambientes"
        const val EXTRA_SUPERFICIE = "extra_superficie"
        const val EXTRA_COMODIDADES = "extra_comodidades"
        const val EXTRA_FOTOS = "extra_fotos"
        const val EXTRA_FOTOS_URL = "extra_fotos_url"
        private const val MAX_FOTOS = 7
        private const val LADO_PREVIEW_PX = 128
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrarPropiedadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        latAgregar = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        lngAgregar = intent.getDoubleExtra(EXTRA_LNG, 0.0)

        val editMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        val propiedadId = intent.getStringExtra(EXTRA_PROPIEDAD_ID)?.orEmpty() ?: ""

        binding.btnRegistrarBack.setOnClickListener { finish() }
        binding.btnRegistrarGuardar.setOnClickListener { guardarPropiedad(editMode, propiedadId) }
        paginasPublicacion = listOf(
            binding.pagePublicarFotos,
            binding.pagePublicarDatos,
            binding.pagePublicarUbicacion,
            binding.pagePublicarCaracteristicas,
            binding.pagePublicarDescripcion
        )
        binding.btnPublicarSiguiente.setOnClickListener { cambiarPasoPublicacion(pasoPublicacion + 1) }
        binding.btnPublicarAnterior.setOnClickListener { cambiarPasoPublicacion(pasoPublicacion - 1) }
        actualizarPasoPublicacion(0, animar = false)
        binding.btnPropElegirMapa.setOnClickListener {
            val origen = Intent(this, MapaSeleccionActivity::class.java).apply {
                putExtra(MapaSeleccionActivity.EXTRA_LAT_INICIAL, latAgregar)
                putExtra(MapaSeleccionActivity.EXTRA_LNG_INICIAL, lngAgregar)
            }
            mapaSeleccionLauncher.launch(origen)
        }

        val spinnerTipo = binding.spPropTipo
        val spinnerOperacion = binding.spPropOperacion
        val spinnerMoneda = binding.spPropMoneda
        val spinnerDepartamento = binding.spPropDepartamento
        val spinnerDistrito = binding.spPropDistrito

        val tipos = resources.getStringArray(R.array.tipos_inmueble)
        val operaciones = resources.getStringArray(R.array.operaciones)
        val monedas = resources.getStringArray(R.array.monedas)
        val departamentos = resources.getStringArray(R.array.departamentos_peru).toList()
        val distritosLima = resources.getStringArray(R.array.distritos_lima).toList()
        val distritosGenerico = listOf(getString(R.string.prop_distrito_sin), "Otro")
        val distritosPublicar = listOf(getString(R.string.prop_distrito_sin)) + distritosLima

        spinnerTipo.adapter = crearAdapterSpinner(tipos.toList())
        spinnerOperacion.adapter = crearAdapterSpinner(operaciones.toList())
        spinnerMoneda.adapter = crearAdapterSpinner(monedas.toList())
        spinnerDepartamento.adapter = crearAdapterSpinner(departamentos)
        spinnerDistrito.adapter = crearAdapterSpinner(distritosPublicar)

        // Función para obtener ciudades por departamento
        fun obtenerCiudades(departamento: String): List<String> {
            return when (departamento) {
                "Amazonas" -> resources.getStringArray(R.array.ciudades_amazonas).toList()
                "Ancash" -> resources.getStringArray(R.array.ciudades_ancash).toList()
                "Apurímac" -> resources.getStringArray(R.array.ciudades_apurimac).toList()
                "Arequipa" -> resources.getStringArray(R.array.ciudades_arequipa).toList()
                "Ayacucho" -> resources.getStringArray(R.array.ciudades_ayacucho).toList()
                "Cajamarca" -> resources.getStringArray(R.array.ciudades_cajamarca).toList()
                "Callao" -> resources.getStringArray(R.array.ciudades_callao).toList()
                "Cusco" -> resources.getStringArray(R.array.ciudades_cusco).toList()
                "Huancavelica" -> resources.getStringArray(R.array.ciudades_huancavelica).toList()
                "Huánuco" -> resources.getStringArray(R.array.ciudades_huanuco).toList()
                "Ica" -> resources.getStringArray(R.array.ciudades_ica).toList()
                "Junín" -> resources.getStringArray(R.array.ciudades_junin).toList()
                "La Libertad" -> resources.getStringArray(R.array.ciudades_lalibertad).toList()
                "Lambayeque" -> resources.getStringArray(R.array.ciudades_lambayeque).toList()
                "Lima" -> resources.getStringArray(R.array.distritos_lima).toList()
                "Loreto" -> resources.getStringArray(R.array.ciudades_loreto).toList()
                "Madre de Dios" -> resources.getStringArray(R.array.ciudades_madrededios).toList()
                "Moquegua" -> resources.getStringArray(R.array.ciudades_moquegua).toList()
                "Pasco" -> resources.getStringArray(R.array.ciudades_pasco).toList()
                "Piura" -> resources.getStringArray(R.array.ciudades_piura).toList()
                "Puno" -> resources.getStringArray(R.array.ciudades_puno).toList()
                "San Martín" -> resources.getStringArray(R.array.ciudades_sanmartin).toList()
                "Tacna" -> resources.getStringArray(R.array.ciudades_tacna).toList()
                "Tumbes" -> resources.getStringArray(R.array.ciudades_tumbes).toList()
                "Ucayali" -> resources.getStringArray(R.array.ciudades_ucayali).toList()
                else -> listOf(getString(R.string.prop_distrito_sin), "Otro")
            }
        }

        spinnerTipo.adapter = crearAdapterSpinner(tipos.toList())
        spinnerOperacion.adapter = crearAdapterSpinner(operaciones.toList())
        spinnerMoneda.adapter = crearAdapterSpinner(monedas.toList())
        spinnerDepartamento.adapter = crearAdapterSpinner(departamentos)
        spinnerDistrito.adapter = crearAdapterSpinner(distritosPublicar)

        // Actualizar ciudades según departamento seleccionado
        spinnerDepartamento.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val dep = departamentos[position]
                val ciudades = obtenerCiudades(dep)
                val opciones = if (dep == "Lima") {
                    listOf(getString(R.string.prop_distrito_sin)) + ciudades
                } else {
                    listOf(getString(R.string.prop_distrito_sin), "Otro") + ciudades
                }
                spinnerDistrito.adapter = crearAdapterSpinner(opciones)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (editMode && propiedadId.isNotBlank()) {
            cargarDatosParaEditar(propiedadId, spinnerTipo, spinnerOperacion, spinnerMoneda, spinnerDepartamento, spinnerDistrito)
        }

        binding.btnPropAgregarFoto.setOnClickListener {
            fotosLauncher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        for (comodidad in resources.getStringArray(R.array.comodidades_disponibles)) {
            binding.cgPropComodidades.addView(
                com.google.android.material.chip.Chip(this).apply {
                    text = comodidad
                    isCheckable = true
                }
            )
        }

        configurarBuscadorDireccion()

        actualizarUbicacionSiPosible()
    }

    private fun crearAdapterSpinner(opciones: List<String>): ArrayAdapter<String> =
        spinnerAdapter(this, opciones)

    private fun cambiarPasoPublicacion(destino: Int) {
        if (destino !in paginasPublicacion.indices) return
        if (destino > pasoPublicacion && !validarPasoPublicacion(pasoPublicacion)) return

        val origen = pasoPublicacion
        val direccion = if (destino > origen) 1f else -1f
        val distancia = (binding.contenedorPasosPublicar.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels).toFloat()
        val paginaActual = paginasPublicacion[origen]
        val paginaSiguiente = paginasPublicacion[destino]

        paginaSiguiente.visibility = View.VISIBLE
        paginaSiguiente.translationX = direccion * distancia
        paginaSiguiente.animate().translationX(0f).setDuration(220).start()
        paginaActual.animate()
            .translationX(-direccion * distancia)
            .setDuration(220)
            .withEndAction {
                paginaActual.visibility = View.GONE
                paginaActual.translationX = 0f
            }
            .start()
        actualizarPasoPublicacion(destino, animar = true)
    }

    private fun actualizarPasoPublicacion(paso: Int, animar: Boolean) {
        pasoPublicacion = paso
        if (!animar) {
            paginasPublicacion.forEachIndexed { index, pagina ->
                pagina.visibility = if (index == paso) View.VISIBLE else View.GONE
                pagina.translationX = 0f
            }
        }
        binding.tvPublicarPaso.text = getString(
            R.string.prop_paso_contador, paso + 1, paginasPublicacion.size
        )
        binding.tvPublicarTituloPaso.setText(
            when (paso) {
                0 -> R.string.prop_paso_fotos
                1 -> R.string.prop_paso_datos
                2 -> R.string.prop_paso_ubicacion
                3 -> R.string.prop_paso_caracteristicas
                else -> R.string.prop_paso_descripcion
            }
        )
        binding.progressPublicar.progress = paso + 1
        binding.btnPublicarAnterior.visibility = if (paso == 0) View.GONE else View.VISIBLE
        binding.btnPublicarSiguiente.visibility = if (paso == paginasPublicacion.lastIndex) View.GONE else View.VISIBLE
        binding.btnRegistrarGuardar.visibility = if (paso == paginasPublicacion.lastIndex) View.VISIBLE else View.GONE
        binding.scrollPublicarForm.post { binding.scrollPublicarForm.scrollTo(0, 0) }
    }

    private fun validarPasoPublicacion(paso: Int): Boolean {
        fun enfocar(view: View): Boolean {
            view.requestFocus()
            return false
        }
        return when (paso) {
            0 -> if (fotosFormulario.isEmpty()) {
                mostrarError(R.string.prop_fotos_requeridas)
                enfocar(binding.btnPropAgregarFoto)
            } else true
            1 -> {
                if (binding.etPropTitulo.text.isNullOrBlank()) {
                    mostrarError(R.string.prop_titulo_requerido)
                    enfocar(binding.etPropTitulo)
                } else {
                    val precioTexto = binding.etPropPrecio.text?.toString()?.trim().orEmpty()
                    val precio = precioTexto.toDoubleOrNull() ?: 0.0
                    if (precio <= 1.0) {
                        mostrarError(if (precioTexto.isEmpty()) R.string.prop_precio_requerido else R.string.prop_precio_mayor_uno)
                        enfocar(binding.etPropPrecio)
                    } else true
                }
            }
            2 -> {
                val departamento = binding.spPropDepartamento.selectedItem?.toString().orEmpty()
                val distrito = binding.spPropDistrito.selectedItem?.toString().orEmpty()
                when {
                    binding.etPropDireccion.text.isNullOrBlank() -> {
                        mostrarError(R.string.prop_direccion_requerida)
                        enfocar(binding.etPropDireccion)
                    }
                    departamento.isBlank() || departamento == getString(R.string.prop_departamento_sin) -> {
                        mostrarError(R.string.prop_departamento_requerido)
                        enfocar(binding.spPropDepartamento)
                    }
                    distrito.isBlank() || distrito == getString(R.string.prop_distrito_sin) || distrito == "Otro" -> {
                        mostrarError(R.string.prop_distrito_requerido)
                        enfocar(binding.spPropDistrito)
                    }
                    else -> true
                }
            }
            3 -> {
                val habitaciones = binding.etPropAmbientes.text?.toString()?.toIntOrNull() ?: 0
                val area = binding.etPropSuperficie.text?.toString()?.toDoubleOrNull() ?: 0.0
                when {
                    habitaciones <= 0 -> {
                        mostrarError(R.string.prop_ambientes_requerido)
                        enfocar(binding.etPropAmbientes)
                    }
                    area <= 0 -> {
                        mostrarError(R.string.prop_superficie_requerido)
                        enfocar(binding.etPropSuperficie)
                    }
                    else -> true
                }
            }
            4 -> if (binding.etPropDescripcion.text.isNullOrBlank()) {
                mostrarError(R.string.prop_descripcion_requerida)
                enfocar(binding.etPropDescripcion)
            } else true
            else -> true
        }
    }

    /** Autocomplete de direcciones vía Google (Places SDK). */
    private fun configurarBuscadorDireccion() {
        val correrBusqueda = Runnable {
            val texto = binding.etPropBuscarDir.text.toString().trim()
            binding.llSugerencias.removeAllViews()
            binding.tvPropBuscarEspera.visibility = View.GONE
            if (texto.length >= 3) {
                val token = ++consultaBusquedaActual
                binding.tvPropBuscarEspera.visibility = View.GONE
                binding.llSugerencias.visibility = View.VISIBLE
                buscarSugerencias(texto, token)
            } else {
                binding.llSugerencias.visibility = View.GONE
                binding.tvPropBuscarEspera.text = getString(R.string.prop_buscar_dir_espera)
                binding.tvPropBuscarEspera.visibility = View.VISIBLE
            }
        }
        binding.etPropBuscarDir.doAfterTextChanged {
            debounceBusqueda.removeCallbacks(correrBusqueda)
            debounceBusqueda.postDelayed(correrBusqueda, 450L)
        }
    }

    private fun buscarSugerencias(texto: String, token: Int) {
        Log.d("AlkilAppBilling", "buscarSugerencias: texto='$texto', token=$token")
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(texto)
            .setCountries(listOf("PE"))
            .setLocationRestriction(
                com.google.android.libraries.places.api.model.RectangularBounds.newInstance(rectPeru)
            )
            .build()
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { res ->
                if (token != consultaBusquedaActual) return@addOnSuccessListener
                Log.d("AlkilAppBilling", "buscarSugerencias: ${res.autocompletePredictions.size} predicciones")
                renderizarSugerencias(res.autocompletePredictions)
            }
            .addOnFailureListener { e ->
                if (token != consultaBusquedaActual) return@addOnFailureListener
                Log.e("AlkilAppBilling", "buscarSugerencias error: ${e.message}")
                binding.llSugerencias.visibility = View.GONE
                binding.tvPropBuscarEspera.text =
                    getString(R.string.prop_buscar_dir_error, e.localizedMessage ?: "?")
                binding.tvPropBuscarEspera.visibility = View.VISIBLE
            }
    }

    private fun renderizarSugerencias(predicciones: List<AutocompletePrediction>) {
        Log.d("AlkilAppBilling", "renderizarSugerencias: ${predicciones.size} items")
        val densidad = resources.displayMetrics.density
        binding.llSugerencias.removeAllViews()
        binding.tvPropBuscarEspera.visibility = View.GONE
        if (predicciones.isEmpty()) {
            binding.tvPropBuscarEspera.text = getString(R.string.prop_buscar_dir_vacio)
            binding.tvPropBuscarEspera.visibility = View.VISIBLE
            return
        }
        for (pred in predicciones) {
            val item = TextView(this).apply {
                text = pred.getFullText(null)
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@RegistrarPropiedadActivity, R.color.text_primary))
                setPadding(
                    (12 * densidad).toInt(), (12 * densidad).toInt(),
                    (12 * densidad).toInt(), (12 * densidad).toInt()
                )
                setBackgroundResource(R.drawable.bg_input_detalle)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6 * densidad).toInt()
                }
            }
            binding.llSugerencias.addView(item)
            item.setOnClickListener { seleccionarLugar(pred) }
        }
        binding.llSugerencias.visibility = View.VISIBLE
    }

    /**
     * Elige una sugerencia de Google Places.
     *
     * Places devuelve la direccion COMPLETA ("Av. Jose Larco 1234, Miraflores,
     * Lima, Peru"), pero el formulario ya tiene sus propios campos de
     * departamento y distrito. Guardarla tal cual hacia que la ficha del
     * inmueble mostrara la misma zona tres veces, asi que aqui solo se toma la
     * calle y la zona deducida se vuelca en los dos selectores.
     */
    private fun seleccionarLugar(pred: AutocompletePrediction) {
        val pedido = FetchPlaceRequest.builder(
            pred.placeId,
            listOf(Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.ADDRESS_COMPONENTS)
        ).build()
        placesClient.fetchPlace(pedido)
            .addOnSuccessListener { resp ->
                val lugar = resp.place
                val latlng = lugar.latLng
                val porTipo = mapaTiposDeAddressComponent(lugar.addressComponents?.asList())
                Log.d("AlkilAppBilling", "seleccionarLugar: porTipo=$porTipo")

                if (latlng != null) {
                    latAgregar = latlng.latitude
                    lngAgregar = latlng.longitude
                    binding.tvPropUbicacionInfo.text = getString(
                        R.string.prop_dir_seleccionada,
                        "%.6f".format(latlng.latitude),
                        "%.6f".format(latlng.longitude)
                    )
                }

                val calle = calleDesdeTipos(porTipo)
                    // Si Places no trae "route" (esquina, plaza o punto de
                    // interes) se recorta la direccion completa quitandole el
                    // departamento, el distrito y el pais, que ya van aparte.
                    ?: recortarZonaDeDireccion(lugar.address, porTipo)
                if (!calle.isNullOrBlank()) {
                    binding.etPropDireccion.setText(calle)
                } else {
                    Toast.makeText(this, R.string.prop_direccion_sin_calle, Toast.LENGTH_LONG).show()
                }

                completarZona(
                    departamento = porTipo["administrative_area_level_1"],
                    distrito = porTipo["sublocality_level_1"]
                        ?: porTipo["sublocality"]
                        ?: porTipo["administrative_area_level_2"]
                        ?: porTipo["locality"]
                )

                ocultarTeclado()
                binding.llSugerencias.removeAllViews()
                binding.llSugerencias.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.prop_buscar_dir_error, e.localizedMessage ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /** Tipos de la direccion ("route", "administrative_area_level_1"...) -> nombre. */
    private fun mapaTiposDeAddressComponent(componentes: List<AddressComponent>?): Map<String, String> {
        val mapa = HashMap<String, String>()
        for (componente in componentes.orEmpty()) {
            val nombre = componente.name ?: continue
            for (tipo in componente.types.orEmpty()) {
                mapa.putIfAbsent(tipo, nombre)
            }
        }
        return mapa
    }

    /**
     * Arma "Av. Jose Larco 1234" con los tipos estructurados. Devuelve null si
     * no hay ninguna via (esquina o punto de interes) para que el que llama
     * aplique su propio recurso.
     */
    private fun calleDesdeTipos(porTipo: Map<String, String>): String? {
        val via = porTipo["route"]?.trim().orEmpty()
        val numero = porTipo["street_number"]?.trim().orEmpty()
        val calle = listOf(via, numero).filter { it.isNotEmpty() }.joinToString(" ")
        if (calle.isNotEmpty()) return calle
        return porTipo["subpremise"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: porTipo["premise"]?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Limpia "Av. Jose Larco 1234, Miraflores, Lima, Peru" -> "Av. Jose Larco
     * 1234" quitando las partes que ya viven en los campos de zona. Se usa solo
     * cuando la direccion estructurada no trae la calle.
     */
    private fun recortarZonaDeDireccion(
        direccion: String?,
        porTipo: Map<String, String>
    ): String? {
        if (direccion.isNullOrBlank()) return null
        val zonas = listOfNotNull(
            porTipo["sublocality_level_1"],
            porTipo["sublocality"],
            porTipo["administrative_area_level_2"],
            porTipo["locality"],
            porTipo["administrative_area_level_1"],
            porTipo["country"]
        ).map { normalizarZona(it) }.filter { it.isNotEmpty() }.toSet()
        if (zonas.isEmpty()) return direccion.trim()

        // Nos quedamos con el tramo inicial hasta el primer fragmento que sea
        // una zona conocida: "Av. X 123, Miraflores, Lima" -> "Av. X 123".
        val partes = direccion.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val corte = partes.indexOfFirst { normalizarZona(it) in zonas }
        val queda = if (corte > 0) partes.take(corte) else partes
        return queda.joinToString(", ").trim().takeIf { it.isNotEmpty() }
    }

    /** Quita acentos y parentesis para comparar nombres de zona. */
    private fun normalizarZona(s: String): String =
        s.lowercase().replace("(", " ").replace(")", " ")
            .replace(Regex("[áàä]"), "a").replace(Regex("[éèë]"), "e")
            .replace(Regex("[íìï]"), "i").replace(Regex("[óòö]"), "o")
            .replace(Regex("[úùü]"), "u").replace(Regex("\\s+"), " ").trim()

    /** Elementos que hay ahora mismo en un Spinner. */
    private fun itemsDelSpinner(spinner: android.widget.Spinner): List<String> {
        val adapter = spinner.adapter as? ArrayAdapter<*> ?: return emptyList()
        return (0 until adapter.count).map { adapter.getItem(it).toString() }
    }

    /**
     * Primer candidato que exista en la lista. Primero compara exacto y luego
     * por inclusion, porque Google devuelve variantes ("Distrito de
     * Miraflores", "Santiago de Surco (Lima)") y la lista usa el nombre corto.
     */
    private fun primerValorEn(candidatos: List<String?>, lista: List<String>): String? {
        for (candidato in candidatos) {
            val n = normalizarZona(candidato.orEmpty())
            if (n.isEmpty()) continue
            val exacto = lista.firstOrNull { normalizarZona(it) == n }
            if (exacto != null) return exacto
        }
        for (candidato in candidatos) {
            val n = normalizarZona(candidato.orEmpty())
            if (n.isEmpty()) continue
            val parcial = lista.firstOrNull {
                val l = normalizarZona(it)
                l.isNotEmpty() && (n.startsWith(l) || n.contains(l) || l.contains(n))
            }
            if (parcial != null) return parcial
        }
        return null
    }

    /**
     * Vuelca el departamento y el distrito deducidos en los selectores y avisa
     * que se rellenaron solos. El distrito se fija DESPUES del departamento,
     * porque el listener de este ultimo repuebla la lista de distritos.
     */
    private fun completarZona(departamento: String?, distrito: String?) {
        val deps = itemsDelSpinner(binding.spPropDepartamento)
        val depElegido = primerValorEn(listOf(departamento), deps)
        if (depElegido == null) {
            Log.w("AlkilAppBilling", "No se pudo deducir el departamento: '$departamento'")
            mostrarAvisoZona(getString(R.string.prop_zona_no_detectada))
            return
        }
        binding.spPropDepartamento.setSelection(deps.indexOf(depElegido))

        // El listener del departamento cambia el adaptador del distrito en el
        // siguiente ciclo, por eso el setSelection va con post.
        binding.spPropDistrito.postDelayed({
            val disponibles = itemsDelSpinner(binding.spPropDistrito)
            val distElegido = primerValorEn(listOf(distrito), disponibles)
            if (distElegido != null) {
                binding.spPropDistrito.setSelection(disponibles.indexOf(distElegido))
                mostrarAvisoZona(getString(R.string.prop_zona_autocompletada))
            } else {
                Log.w("AlkilAppBilling", "Distrito '$distrito' no esta en ${disponibles.size} opciones")
                mostrarAvisoZona(getString(R.string.prop_zona_no_detectada))
            }
        }, 150)
    }

    private fun mostrarAvisoZona(texto: String) {
        binding.tvPropZonaAuto.text = texto
        binding.tvPropZonaAuto.visibility = View.VISIBLE
    }

    private fun ocultarTeclado() {
        val ime = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        ime.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.root.clearFocus()
    }

    /**
     * Traduce las coordenadas elegidas en el mapa a la calle del inmueble
     * (geocodificacion inversa) y rellena el campo de direccion (siempre
     * actualiza al seleccionar en el mapa). El Geocoder devuelve la direccion
     * completa con distrito, ciudad y pais, asi que aqui se separan: la calle
     * va al campo de direccion y la zona a los selectores de departamento y
     * distrito, para que la ficha no repita la misma zona tres veces.
     */
    private fun rellenarDireccionDesdeMapa(lat: Double, lng: Double) {
        Thread {
            val address = try {
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
            } catch (e: Exception) {
                Log.w("AlkilAppBilling", "Error en geocodificacion inversa: ${e.message}")
                null
            }
            if (address == null) {
                runOnUiThread {
                    Toast.makeText(this, R.string.prop_direccion_sin_calle, Toast.LENGTH_LONG).show()
                }
                return@Thread
            }

            val calle = calleDesdeGeocoder(address)
            val departamento = address.adminArea
            // Para Lima el distrito llega en subLocality; en el resto del pais
            // el Geocoder suele dar la ciudad o la provincia, que es lo que
            // guarda la app en el mismo campo.
            val distrito = listOfNotNull(
                address.subLocality,
                address.locality,
                address.subAdminArea
            ).firstOrNull { !it.isNullOrBlank() }

            Log.d(
                "AlkilAppBilling",
                "rellenarDireccionDesdeMapa: lat=$lat, lng=$lng, calle=$calle, " +
                    "departamento=$departamento, distrito=$distrito"
            )

            runOnUiThread {
                if (calle.isNotBlank()) {
                    binding.etPropDireccion.setText(calle)
                } else {
                    Toast.makeText(this, R.string.prop_direccion_sin_calle, Toast.LENGTH_LONG).show()
                }
                binding.tvPropUbicacionInfo.text = getString(
                    R.string.prop_ubicacion_mapa_seleccionada,
                    "%.6f".format(lat), "%.6f".format(lng)
                )
                completarZona(departamento, distrito)
            }
        }.start()
    }

    /**
     * "Av. Jose Larco 1234" a partir del Address del Geocoder. Si no hay via
     * (un punto en medio de un parque, por ejemplo) devuelve cadena vacia para
     * que el que llama avise en vez de inventar una direccion.
     */
    private fun calleDesdeGeocoder(address: Address): String {
        // El Geocoder de Android separa la via (thoroughfare) del numero o
        // interior (subThoroughfare), pero a veces los deja pegados, asi que
        // se evita repetir el numero si ya viene al final de la via.
        val via = address.thoroughfare?.trim().orEmpty()
        val extra = address.subThoroughfare?.trim().orEmpty()
        if (via.isEmpty()) {
            return address.premises?.trim().orEmpty()
                .ifEmpty { address.featureName?.trim().orEmpty() }
        }
        if (extra.isEmpty() || via.endsWith(extra, ignoreCase = true)) return via
        return "$via $extra"
    }


    /** Si hay permiso, refresca la ubicacion fresca del usuario (si falla, queda la pasada). */
    private fun actualizarUbicacionSiPosible() {
        if (!tienePermisoUbicacion()) return
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    latAgregar = location.latitude
                    lngAgregar = location.longitude
                }
            }
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED ||
                coarse == PackageManager.PERMISSION_GRANTED
    }

    /** Comprime la imagen elegida de la galeria y la devuelve en base64 (max ~900px, JPEG q55). */
    private fun comprimirFoto(uri: Uri): String? {
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                val stream = contentResolver.openInputStream(uri) ?: return null
                stream.use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            return null
        }
        val maxLado = 900.0f
        val escala = if (max(bmp.width, bmp.height) > maxLado) {
            maxLado / max(bmp.width, bmp.height)
        } else {
            1f
        }
        val w = (bmp.width * escala).toInt().coerceAtLeast(1)
        val h = (bmp.height * escala).toInt().coerceAtLeast(1)
        val escalado = if (w == bmp.width && h == bmp.height) bmp else {
            Bitmap.createScaledBitmap(bmp, w, h, true)
        }
        val bytes = java.io.ByteArrayOutputStream()
        escalado.compress(Bitmap.CompressFormat.JPEG, 55, bytes)
        if (escalado !== bmp) bmp.recycle()
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    /** Muestra miniaturas de las fotos elegidas; tocar una la quita. */
    private fun renderizarPreviewsFotos(editMode: Boolean = false) {
        val contenedor = binding.llPropFotos
        contenedor.removeAllViews()
        val ladoPx = (56 * resources.displayMetrics.density).toInt()
        val paddingPx = (2 * resources.displayMetrics.density).toInt()
        val delPx = (24 * resources.displayMetrics.density).toInt()
        fotosFormulario.forEachIndexed { idx, foto ->
            val bytes = try {
                Base64.decode(foto, Base64.NO_WRAP)
            } catch (_: IllegalArgumentException) {
                null
            }
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            val thumb = bmp?.let {
                val escala = LADO_PREVIEW_PX.toFloat() / max(it.width, it.height)
                val w = (it.width * escala).toInt().coerceAtLeast(1)
                val h = (it.height * escala).toInt().coerceAtLeast(1)
                if (w == it.width && h == it.height) it else Bitmap.createScaledBitmap(it, w, h, true)
            }
            val frame = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ladoPx, ladoPx).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
            }
            val iv = ImageView(this).apply {
                id = View.generateViewId()
                setImageBitmap(thumb)
                contentDescription = getString(R.string.prop_foto_thumb_cd)
                layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                    ladoPx, ladoPx
                ).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                setBackgroundResource(R.drawable.bg_foto_thumb)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            }
            iv.setOnClickListener {
                fotosFormulario.removeAt(idx)
                renderizarPreviewsFotos(editMode)
            }
            frame.addView(iv)
            if (editMode) {
                val btnDel = ImageView(this).apply {
                    setImageResource(R.drawable.ic_basurero)
                    setColorFilter(getColor(R.color.alkil_rojo))
                    layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(delPx, delPx).apply {
                        topToTop = iv.id
                        endToEnd = iv.id
                        topMargin = (2 * resources.displayMetrics.density).toInt()
                        marginEnd = (2 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundResource(R.drawable.bg_favorito)
                    setPadding((4 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt(),
                        (4 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt())
                    setOnClickListener {
                        fotosFormulario.removeAt(idx)
                        renderizarPreviewsFotos(editMode)
                    }
                    contentDescription = "Eliminar foto"
                }
                frame.addView(btnDel)
            }
            contenedor.addView(frame)
        }
    }

    private fun cargarDatosParaEditar(
        propiedadId: String,
        spinnerTipo: android.widget.Spinner,
        spinnerOperacion: android.widget.Spinner,
        spinnerMoneda: android.widget.Spinner,
        spinnerDepartamento: android.widget.Spinner,
        spinnerDistrito: android.widget.Spinner
    ) {
        binding.tvRegistrarTitulo.text = "Editar publicacion"
        binding.btnRegistrarGuardar.text = "Guardar cambios"

        db.collection("propiedades").document(propiedadId).get()
            .addOnSuccessListener { doc ->
                val d = doc.data ?: return@addOnSuccessListener
                fun s(k: String): String = when (val v = d[k]) {
                    is String -> v
                    is Number -> v.toString()
                    else -> ""
                }
                fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
                fun l(k: String): List<String> = (d[k] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                binding.etPropTitulo.setText(s("titulo"))
                binding.etPropDescripcion.setText(s("descripcion"))
                binding.etPropDireccion.setText(s("direccion"))
                binding.etPropAmbientes.setText(s("ambientes"))
                binding.etPropSuperficie.setText(s("superficieM2"))
                binding.etPropPrecio.setText(n("precio").toString())

                val tipo = s("tipo")
                val operacion = s("operacion")
                val moneda = s("moneda")
                val barrio = s("barrio")
                val departamentoSel = if (s("ciudad").isBlank()) "Lima" else s("ciudad")
                val distritoSel = if (barrio.isBlank()) getString(R.string.prop_distrito_sin) else barrio

                val tiposArr = resources.getStringArray(R.array.tipos_inmueble)
                val operArr = resources.getStringArray(R.array.operaciones)
                val monArr = resources.getStringArray(R.array.monedas)
                val depArr = resources.getStringArray(R.array.departamentos_peru)

                spinnerTipo.setSelection(tiposArr.indexOfFirst { it == tipo }.coerceAtLeast(0))
                spinnerOperacion.setSelection(operArr.indexOfFirst { it == operacion }.coerceAtLeast(0))
                spinnerMoneda.setSelection(monArr.indexOfFirst { it.contains(moneda, ignoreCase = true) }.coerceAtLeast(0))
                spinnerDepartamento.setSelection(depArr.indexOfFirst { it == departamentoSel }.coerceAtLeast(0))

// Actualizar distritos según departamento antes de setear
                val distritosLima = resources.getStringArray(R.array.distritos_lima).toList()
                val distritosGenerico = listOf(getString(R.string.prop_distrito_sin), "Otro")
                val nuevosDistritos = if (departamentoSel == "Lima") {
                    listOf(getString(R.string.prop_distrito_sin)) + distritosLima
                } else distritosGenerico
                spinnerDistrito.adapter = crearAdapterSpinner(nuevosDistritos)

                // Normalizar el distrito guardado para comparar sin distinción de mayúsculas/minúsculas ni espacios
                val distritoNormalizado = distritoSel.trim().lowercase()
                val indiceDistrito = nuevosDistritos.indexOfFirst { it.trim().lowercase() == distritoNormalizado }
                spinnerDistrito.setSelection(indiceDistrito.coerceAtLeast(0))

                // Comodidades
                val comodidadesExistentes = l("comodidades").toSet()
                for (i in 0 until binding.cgPropComodidades.childCount) {
                    val chip = binding.cgPropComodidades.getChildAt(i) as com.google.android.material.chip.Chip
                    if (comodidadesExistentes.contains(chip.text.toString())) {
                        chip.isChecked = true
                    }
                }

                // Fotos base64
                val fotosBase64 = l("fotos")
                fotosFormulario.clear()
                fotosFormulario.addAll(fotosBase64)
                renderizarPreviewsFotos(true)

                latAgregar = n("lat")
                lngAgregar = n("lng")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error cargando datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun guardarPropiedad(editMode: Boolean, propiedadId: String = "") {
        val titulo = binding.etPropTitulo.text.toString().trim()
        if (titulo.isEmpty()) {
            mostrarError(R.string.prop_titulo_requerido)
            return
        }
        val descripcion = binding.etPropDescripcion.text.toString().trim()
        if (descripcion.isEmpty()) {
            mostrarError(R.string.prop_descripcion_requerida)
            return
        }
        val precioStr = binding.etPropPrecio.text.toString().trim()
        if (precioStr.isEmpty()) {
            mostrarError(R.string.prop_precio_requerido)
            return
        }
        val precio = precioStr.toDoubleOrNull() ?: 0.0
        if (precio <= 1) {
            mostrarError(R.string.prop_precio_mayor_uno)
            return
        }
        val u = auth.currentUser ?: run {
            mostrarError(R.string.auth_requerido_para_agregar, true)
            finish()
            return
        }
        // Verificar límite de publicaciones antes de continuar (solo para nuevas)
        if (!editMode) {
            verificarLimitePublicaciones(u.uid) { puede ->
                if (puede) runOnUiThread { continuarGuardado(u, propiedadId) }
            }
            return
        }
        val tipos = resources.getStringArray(R.array.tipos_inmueble)
        val operaciones = resources.getStringArray(R.array.operaciones)
        val monedas = resources.getStringArray(R.array.monedas)
        val codigoMoneda =
            if (monedas[binding.spPropMoneda.selectedItemPosition].contains("USD")) "USD" else "PEN"
        val departamento = binding.spPropDepartamento.selectedItem as String
        val distrito = binding.spPropDistrito.selectedItem as String
        val barrio = if (distrito == getString(R.string.prop_distrito_sin) || distrito == "Otro") "" else distrito

        // Validaciones obligatorias
        val tipoIdx = binding.spPropTipo.selectedItemPosition
        if (tipoIdx < 0 || tipoIdx >= tipos.size) {
            mostrarError(R.string.prop_tipo_requerido)
            return
        }
        val operIdx = binding.spPropOperacion.selectedItemPosition
        if (operIdx < 0 || operIdx >= resources.getStringArray(R.array.operaciones).size) {
            mostrarError(R.string.prop_operacion_requerido)
            return
        }
        val monedaIdx = binding.spPropMoneda.selectedItemPosition
        if (monedaIdx < 0 || monedaIdx >= monedas.size) {
            mostrarError(R.string.prop_moneda_requerido)
            return
        }
        if (departamento.isEmpty() || departamento == getString(R.string.prop_departamento_sin)) {
            mostrarError(R.string.prop_departamento_requerido)
            return
        }
        if (distrito.isEmpty() || distrito == getString(R.string.prop_distrito_sin) || distrito == "Otro") {
            mostrarError(R.string.prop_distrito_requerido)
            return
        }
        val ambientesStr = binding.etPropAmbientes.text.toString().trim()
        if (ambientesStr.isEmpty()) {
            mostrarError(R.string.prop_ambientes_requerido)
            return
        }
        val ambientes = ambientesStr.toIntOrNull() ?: 0
        if (ambientes <= 0) {
            mostrarError(R.string.prop_ambientes_requerido)
            return
        }
        val superficieStr = binding.etPropSuperficie.text.toString().trim()
        if (superficieStr.isEmpty()) {
            mostrarError(R.string.prop_superficie_requerido)
            return
        }
        val superficie = superficieStr.toDoubleOrNull() ?: 0.0
        if (superficie <= 0) {
            mostrarError(R.string.prop_superficie_requerido)
            return
        }
        val direccion = binding.etPropDireccion.text.toString().trim()
        if (direccion.isEmpty()) {
            mostrarError(R.string.prop_direccion_requerida)
            return
        }
        if (fotosFormulario.isEmpty()) {
            mostrarError(R.string.prop_fotos_requeridas)
            return
        }

        val comodidades = binding.cgPropComodidades.checkedChipIds.mapNotNull { id ->
            (binding.cgPropComodidades.findViewById<com.google.android.material.chip.Chip>(id))
                ?.text?.toString()
        }

        val datos = hashMapOf<String, Any>(
            "titulo" to titulo,
            "descripcion" to descripcion,
            "tipo" to tipos[binding.spPropTipo.selectedItemPosition],
            "operacion" to operaciones[binding.spPropOperacion.selectedItemPosition],
            "precio" to precio,
            "moneda" to codigoMoneda,
            "direccion" to direccion,
            "barrio" to barrio,
            "ciudad" to departamento,
            "lat" to latAgregar,
            "lng" to lngAgregar,
            "imagenUrl" to emptyList<String>(),
            "fotos" to fotosFormulario.toList(),
            "idPropietario" to u.uid,
            "ambientes" to ambientes,
            "superficieM2" to superficie,
            "comodidades" to comodidades,
            "publicadoEn" to FieldValue.serverTimestamp()
        )

        if (editMode && propiedadId.isNotBlank()) {
            // En modo edición, actualizar documento existente (mantener estado actual)
            db.collection("propiedades").document(propiedadId).update(datos)
                .addOnSuccessListener {
                    Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.prop_error_guardado, e.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
        } else {
            // Nueva publicación
            val datosNueva = datos.toMutableMap()
            datosNueva["estado"] = "under_review"
            db.collection("propiedades").add(datosNueva)
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.prop_ok_guardado, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.prop_error_guardado, e.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

    }

    /** Continuación del guardado tras verificar límite de publicaciones. */
    private fun continuarGuardado(u: com.google.firebase.auth.FirebaseUser, propiedadId: String = "") {
        val titulo = binding.etPropTitulo.text.toString().trim()
        val descripcion = binding.etPropDescripcion.text.toString().trim()
        val precioStr = binding.etPropPrecio.text.toString().trim()
        val precio = precioStr.toDoubleOrNull() ?: 0.0
        val tipos = resources.getStringArray(R.array.tipos_inmueble)
        val operaciones = resources.getStringArray(R.array.operaciones)
        val monedas = resources.getStringArray(R.array.monedas)
        val codigoMoneda =
            if (monedas[binding.spPropMoneda.selectedItemPosition].contains("USD")) "USD" else "PEN"
        val departamento = binding.spPropDepartamento.selectedItem as String
        val distrito = binding.spPropDistrito.selectedItem as String
        val barrio = if (distrito == getString(R.string.prop_distrito_sin) || distrito == "Otro") "" else distrito

        // Validaciones obligatorias
        val tipoIdx = binding.spPropTipo.selectedItemPosition
        if (tipoIdx < 0 || tipoIdx >= tipos.size) {
            mostrarError(R.string.prop_tipo_requerido)
            return
        }
        val operIdx = binding.spPropOperacion.selectedItemPosition
        if (operIdx < 0 || operIdx >= resources.getStringArray(R.array.operaciones).size) {
            mostrarError(R.string.prop_operacion_requerido)
            return
        }
        val monedaIdx = binding.spPropMoneda.selectedItemPosition
        if (monedaIdx < 0 || monedaIdx >= monedas.size) {
            mostrarError(R.string.prop_moneda_requerido)
            return
        }
        if (departamento.isEmpty() || departamento == getString(R.string.prop_departamento_sin)) {
            mostrarError(R.string.prop_departamento_requerido)
            return
        }
        if (distrito.isEmpty() || distrito == getString(R.string.prop_distrito_sin) || distrito == "Otro") {
            mostrarError(R.string.prop_distrito_requerido)
            return
        }
        val ambientesStr = binding.etPropAmbientes.text.toString().trim()
        if (ambientesStr.isEmpty()) {
            mostrarError(R.string.prop_ambientes_requerido)
            return
        }
        val ambientes = ambientesStr.toIntOrNull() ?: 0
        if (ambientes <= 0) {
            mostrarError(R.string.prop_ambientes_requerido)
            return
        }
        val superficieStr = binding.etPropSuperficie.text.toString().trim()
        if (superficieStr.isEmpty()) {
            mostrarError(R.string.prop_superficie_requerido)
            return
        }
        val superficie = superficieStr.toDoubleOrNull() ?: 0.0
        if (superficie <= 0) {
            mostrarError(R.string.prop_superficie_requerido)
            return
        }
        val direccion = binding.etPropDireccion.text.toString().trim()
        if (direccion.isEmpty()) {
            mostrarError(R.string.prop_direccion_requerida)
            return
        }
        if (fotosFormulario.isEmpty()) {
            mostrarError(R.string.prop_fotos_requeridas)
            return
        }

        val comodidades = binding.cgPropComodidades.checkedChipIds.mapNotNull { id ->
            (binding.cgPropComodidades.findViewById<com.google.android.material.chip.Chip>(id))
                ?.text?.toString()
        }

        val datos = hashMapOf<String, Any>(
            "titulo" to titulo,
            "descripcion" to descripcion,
            "tipo" to tipos[binding.spPropTipo.selectedItemPosition],
            "operacion" to operaciones[binding.spPropOperacion.selectedItemPosition],
            "precio" to precio,
            "moneda" to codigoMoneda,
            "direccion" to direccion,
            "barrio" to barrio,
            "ciudad" to departamento,
            "lat" to latAgregar,
            "lng" to lngAgregar,
            "imagenUrl" to emptyList<String>(),
            "fotos" to fotosFormulario.toList(),
            "idPropietario" to u.uid,
            "ambientes" to ambientes,
            "superficieM2" to superficie,
            "comodidades" to comodidades,
            "publicadoEn" to FieldValue.serverTimestamp()
        )

        if (propiedadId.isNotBlank()) {
            // Modo edición, actualizar documento existente (mantener estado actual)
            db.collection("propiedades").document(propiedadId).update(datos)
                .addOnSuccessListener {
                    Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.prop_error_guardado, e.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
        } else {
            // Nueva publicación
            val datosNueva = datos.toMutableMap()
            datosNueva["estado"] = "under_review"
            db.collection("propiedades").add(datosNueva)
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.prop_ok_guardado, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.prop_error_guardado, e.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    /**
     * Verifica si el usuario puede crear una nueva publicación.
     * Usuarios no verificados: máximo 1 publicación activa.
     * Usuarios verificados: máximo 5 publicaciones activas simultáneas.
     * Llama al callback con true si puede publicar, false si alcanzó el límite (muestra toast).
     */
    private fun verificarLimitePublicaciones(uid: String, callback: (Boolean) -> Unit) {
        val MAX_NO_VERIFICADO = 1
        val MAX_VERIFICADO = 5

        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val verificado = userDoc.get("verification.identityVerified") == true
                val maxPermitido = if (verificado) MAX_VERIFICADO else MAX_NO_VERIFICADO

                db.collection("propiedades")
                    .whereEqualTo("idPropietario", uid)
                    .whereEqualTo("estado", "disponible")
                    .get()
                    .addOnSuccessListener { snap ->
                        val activas = snap.size()
                        if (activas >= maxPermitido) {
                            val msg = if (verificado) {
                                getString(R.string.prop_limite_verificado, maxPermitido)
                            } else {
                                getString(R.string.prop_limite_no_verificado)
                            }
                            runOnUiThread { mostrarError(msg) }
                            callback(false)
                        } else {
                            callback(true)
                        }
                    }
                    .addOnFailureListener { e ->
                        runOnUiThread {
                            mostrarError(getString(R.string.prop_error_limite, e.localizedMessage ?: "?"))
                            callback(false)
                        }
                    }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    mostrarError(getString(R.string.prop_error_limite, e.localizedMessage ?: "?"))
                    callback(false)
                }
            }
    }

    @SuppressLint("ShowToast")
    private fun mostrarError(@StringRes resId: Int, finishActivity: Boolean = false) {
        val toast = Toast.makeText(this, resId, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.CENTER, 0, (-80 * resources.displayMetrics.density).toInt())
        toast.show()
        if (finishActivity) finish()
    }

    /** Sobrecarga para mostrar un mensaje de error arbitrario (no recurso).
     * Usa AlertDialog para textos largos (límite de publicaciones) para que se lean completos. */
    private fun mostrarError(mensaje: String) {
        // Si el mensaje es largo (> 100 chars), usar diálogo para que se lea completo
        if (mensaje.length > 100) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(mensaje)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        } else {
            val toast = Toast.makeText(this, mensaje, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER, 0, (-80 * resources.displayMetrics.density).toInt())
            toast.show()
        }
    }
}
