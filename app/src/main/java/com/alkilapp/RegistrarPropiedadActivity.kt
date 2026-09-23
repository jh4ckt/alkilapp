package com.alkilapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlin.math.max

/**
 * Formulario de alta de inmueble en pantalla completa (reemplaza al pop-up).
 * La ubicacion se toma de la posicion actual del usuario (o la pasada por MainActivity).
 */
class RegistrarPropiedadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrarPropiedadBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

        spinnerTipo.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, tipos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerOperacion.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, operaciones
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerMoneda.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, monedas
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerDepartamento.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, departamentos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerDistrito.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, distritosPublicar
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

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

        spinnerTipo.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, tipos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerOperacion.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, operaciones
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerMoneda.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, monedas
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerDepartamento.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, departamentos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerDistrito.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, distritosPublicar
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

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
                spinnerDistrito.adapter = ArrayAdapter(
                    this@RegistrarPropiedadActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
                renderizarSugerencias(res.autocompletePredictions)
            }
            .addOnFailureListener { e ->
                if (token != consultaBusquedaActual) return@addOnFailureListener
                binding.llSugerencias.visibility = View.GONE
                binding.tvPropBuscarEspera.text =
                    getString(R.string.prop_buscar_dir_error, e.localizedMessage ?: "?")
                binding.tvPropBuscarEspera.visibility = View.VISIBLE
            }
    }

    private fun renderizarSugerencias(predicciones: List<AutocompletePrediction>) {
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

    private fun seleccionarLugar(pred: AutocompletePrediction) {
        val pedido = FetchPlaceRequest.builder(
            pred.placeId,
            listOf(Place.Field.ADDRESS, Place.Field.LAT_LNG)
        ).build()
        placesClient.fetchPlace(pedido)
            .addOnSuccessListener { resp ->
                val lugar = resp.place
                val direccion = lugar.address
                val latlng = lugar.latLng
                if (direccion != null) {
                    binding.etPropDireccion.setText(direccion)
                }
                if (latlng != null) {
                    latAgregar = latlng.latitude
                    lngAgregar = latlng.longitude
                    binding.tvPropUbicacionInfo.text = getString(
                        R.string.prop_dir_seleccionada,
                        "%.6f".format(latlng.latitude),
                        "%.6f".format(latlng.longitude)
                    )
                }
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

    private fun ocultarTeclado() {
        val ime = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        ime.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.root.clearFocus()
    }

    /**
     * Traduce las coordenadas elegidas en el mapa a una direccion (geocodificacion inversa)
     * y la rellena en el campo de direccion si este sigue vacio.
     */
    private fun rellenarDireccionDesdeMapa(lat: Double, lng: Double) {
        Thread {
            val direccion = try {
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (!direccion.isNullOrBlank() &&
                    binding.etPropDireccion.text?.toString()?.isBlank() != false
                ) {
                    binding.etPropDireccion.setText(direccion)
                }
            }
        }.start()
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
                fun s(k: String): String = d[k] as? String ?: ""
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
                spinnerDistrito.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    nuevosDistritos
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinnerDistrito.setSelection(
                    nuevosDistritos.indexOfFirst { it == distritoSel }.coerceAtLeast(0)
                )

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
            Toast.makeText(this, R.string.prop_titulo_requerido, Toast.LENGTH_SHORT).show()
            return
        }
        val precio = binding.etPropPrecio.text.toString().trim().toDoubleOrNull() ?: 0.0
        val u = auth.currentUser ?: run {
            Toast.makeText(this, R.string.auth_requerido_para_agregar, Toast.LENGTH_LONG).show()
            finish()
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

        val comodidades = binding.cgPropComodidades.checkedChipIds.mapNotNull { id ->
            (binding.cgPropComodidades.findViewById<com.google.android.material.chip.Chip>(id))
                ?.text?.toString()
        }

        val datos = hashMapOf<String, Any>(
            "titulo" to titulo,
            "descripcion" to binding.etPropDescripcion.text.toString().trim(),
            "tipo" to tipos[binding.spPropTipo.selectedItemPosition],
            "operacion" to operaciones[binding.spPropOperacion.selectedItemPosition],
            "precio" to precio,
            "moneda" to codigoMoneda,
            "direccion" to binding.etPropDireccion.text.toString().trim(),
            "barrio" to barrio,
            "ciudad" to departamento,
            "lat" to latAgregar,
            "lng" to lngAgregar,
            "imagenUrl" to emptyList<String>(),
            "fotos" to fotosFormulario.toList(),
            "idPropietario" to u.uid,
            "ambientes" to (binding.etPropAmbientes.text.toString().trim().toLongOrNull() ?: 0L),
            "superficieM2" to (binding.etPropSuperficie.text.toString().trim().toDoubleOrNull()
                ?: 0.0),
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
}