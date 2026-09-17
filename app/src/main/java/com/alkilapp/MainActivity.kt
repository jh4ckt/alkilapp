package com.alkilapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityMainBinding
import com.alkilapp.ui.PropiedadAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: PropiedadAdapter

    private val marcadores = mutableListOf<Marker>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaPropiedades: ListenerRegistration? = null

    private var filtroDepartamento: String? = null
    private var filtroDistrito: String? = null
    private var busquedaActual: String = ""
    private var ultimaUbicacion: LatLng? = null

    private val googleSignInClient by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        val webClientId = getString(R.string.default_web_client_id)
        if (webClientId.isNotBlank()) builder.requestIdToken(webClientId)
        builder.requestEmail()
            .build()
            .let { GoogleSignIn.getClient(this, it) }
    }

    private val googleAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val cuenta = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = cuenta.idToken
            if (idToken == null) {
                Toast.makeText(
                    this,
                    getString(R.string.auth_google_error, "sin token"),
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        guardarUsuarioEnBase()
                        actualizarUiSesion()
                        Toast.makeText(this, R.string.auth_ok_google, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        } catch (e: ApiException) {
            Toast.makeText(
                this,
                getString(R.string.auth_google_error, "${e.statusCode}"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
        private const val DEFAULT_CAMERA_ZOOM = 15f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupListaDepartamentos()
        setupBotones()
        configurarMenu()
        configurarBottomSheet()
        actualizarBotonFiltros()
    }

    /** El FAB "mi ubicación" sube junto con el bottomSheet para nunca quedar sobre el listado. */
    private fun configurarBottomSheet() {
        val sheet = binding.bottomSheet
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) = Unit

            override fun onSlide(sheet: View, slideOffset: Float) {
                val delta = sheet.height - behavior.peekHeight
                binding.fabMiUbicacion.translationY = -slideOffset * delta
            }
        })
    }

    /** Botón de menú en la esquina superior: despliega perfil / chat / filtros. */
    private fun configurarMenu() {
        binding.btnMenu.setOnClickListener { mostrarMenuPrincipal() }
    }

    private fun mostrarMenuPrincipal() {
        val popup = android.widget.PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.menu_principal, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuPerfil -> onBotonAuth()
                R.id.menuChat -> abrirChat()
            }
            true
        }
        popup.show()
    }

    override fun onStart() {
        super.onStart()
        actualizarUiSesion()
        escucharPropiedades()
    }

    override fun onStop() {
        super.onStop()
        escuchaPropiedades?.remove()
    }

    private fun setupListaDepartamentos() {
        adapter = PropiedadAdapter { propiedad ->
            abrirDetallePropiedad(propiedad)
        }
        binding.rvDepartamentos.layoutManager = LinearLayoutManager(this)
        binding.rvDepartamentos.adapter = adapter

        binding.etBusqueda.doAfterTextChanged { texto ->
            busquedaActual = texto?.toString().orEmpty()
            adapter.filter(busquedaActual)
            actualizarZonaMapa(false)
        }
        binding.btnFiltroBuscar.setOnClickListener { abrirDialogoFiltros() }
    }

    private fun setupBotones() {
        binding.fabAgregar.setOnClickListener { abrirRegistrarPropiedad() }
        binding.fabMiUbicacion.setOnClickListener { irAMiUbicacion() }
    }

    /** Barra lateral: perfil, chat y filtros ahora son un panel estrecho a la izquierda. */

    private fun abrirRegistrarPropiedad() {
        if (auth.currentUser == null) {
            Toast.makeText(this, R.string.auth_requerido_para_agregar, Toast.LENGTH_LONG).show()
            abrirDialogoAutenticar()
            return
        }
        var punto = ultimaUbicacion
        if (punto == null && ::mMap.isInitialized) punto = mMap.cameraPosition.target
        if (punto == null) punto = LatLng(0.0, 0.0)
        startActivity(
            Intent(this, RegistrarPropiedadActivity::class.java).apply {
                putExtra(RegistrarPropiedadActivity.EXTRA_LAT, punto.latitude)
                putExtra(RegistrarPropiedadActivity.EXTRA_LNG, punto.longitude)
            }
        )
    }

    /** Vuelve a centrar el mapa (y el marcador) en donde está el usuario. */
    private fun irAMiUbicacion() {
        if (!tienePermisoUbicacion()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        if (::mMap.isInitialized) obtenerUbicacionActual()
    }

    private fun abrirChat() {
        if (auth.currentUser == null) {
            Toast.makeText(
                this,
                getString(R.string.chat_requiere_sesion),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        startActivity(Intent(this, ChatListActivity::class.java))
    }

    // ======================================================================
    // Filtros por departamento / distrito (Perú - Lima por ahora)
    // ======================================================================

    private fun abrirDialogoFiltros() {
        val vista = layoutInflater.inflate(R.layout.dialog_filtros, null)
        val spinnerDep = vista.findViewById<Spinner>(R.id.spFiltroDepartamento)
        val spinnerDis = vista.findViewById<Spinner>(R.id.spFiltroDistrito)

        val todos = getString(R.string.filtros_todos)
        val departamentos = resources.getStringArray(R.array.departamentos_peru).toList()
        val distritos = resources.getStringArray(R.array.distritos_lima).toList()
        val distritosConTodos = listOf(todos) + distritos

        spinnerDep.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, listOf(todos) + departamentos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerDis.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, distritosConTodos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val depActual = filtroDepartamento ?: todos
        spinnerDep.setSelection(
            (todos + departamentos).indexOfFirst { it.lowercase() == depActual.lowercase() }.coerceAtLeast(0)
        )
        val disActual = filtroDistrito ?: todos
        spinnerDis.setSelection(
            distritosConTodos.indexOfFirst { it.lowercase() == disActual.lowercase() }.coerceAtLeast(0)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filtros_titulo)
            .setView(vista)
            .setPositiveButton(R.string.filtros_aplicar) { _, _ ->
                val dep = spinnerDep.selectedItem as String
                val dis = spinnerDis.selectedItem as String
                filtroDepartamento = dep.takeUnless { it == todos }
                filtroDistrito = dis.takeUnless { it == todos }
                adapter.setFiltros(filtroDepartamento, filtroDistrito)
                actualizarBotonFiltros()
                actualizarZonaMapa()
            }
            .setNeutralButton(R.string.filtros_limpiar) { _, _ ->
                filtroDepartamento = null
                filtroDistrito = null
                binding.etBusqueda.setText("")
                adapter.setFiltros(null, null)
                actualizarBotonFiltros()
                actualizarZonaMapa()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun actualizarBotonFiltros() {
        val activo = (filtroDistrito ?: filtroDepartamento) != null || busquedaActual.isNotBlank()
        binding.btnFiltroBuscar.imageTintList = ColorStateList.valueOf(
            getColor(if (activo) R.color.alkil_primary else R.color.text_secondary)
        )
    }

    /** Escucha en vivo los inmuebles guardados en Firestore (colección "propiedades"). */
    private fun escucharPropiedades() {
        escuchaPropiedades?.remove()
        escuchaPropiedades = db.collection("propiedades")
            .orderBy("precio")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Toast.makeText(
                        this,
                        getString(R.string.prop_error_cargar, error.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                val lista = snap?.documents?.mapNotNull { Propiedad.desde(it) }
                    ?.filter { it.estado != "under_review" && it.estado != "finalizado" } ?: emptyList()
                adapter.submitList(lista)
                actualizarBadges(lista)
                cargarVerificacionPropietarios(lista)
            }
    }

    /** Busca en "usuarios" el badge de confianza de cada propietario de la lista. */
    private fun cargarVerificacionPropietarios(lista: List<Propiedad>) {
        val uids = lista.map { it.idPropietario }.filter { it.isNotBlank() }.distinct()
        if (uids.isEmpty()) return
        val mapa = LinkedHashMap<String, Boolean>()
        var pendientes = uids.size
        uids.forEach { uid ->
            db.collection("usuarios").document(uid).get()
                .addOnCompleteListener { tarea ->
                    val ok = tarea.isSuccessful && tarea.result?.exists() == true
                    if (ok) mapa[uid] = tarea.result?.get("verificationBadge") == true
                    pendientes--
                    if (pendientes <= 0) adapter.setPropietariosVerificados(mapa)
                }
        }
    }

    // ======================================================================
    // Badges estáticos de disponibilidad por zona sobre el mapa
    // ======================================================================

    private fun configurarBadges() {
        mMap.setOnCameraIdleListener { posicionarBadges() }
    }

    private fun actualizarBadges(lista: List<Propiedad>) {
        binding.overlayBadges.removeAllViews()
        val zonaActiva = filtroDistrito ?: filtroDepartamento
        val filtrada = if (busquedaActual.isNotBlank() || zonaActiva != null) {
            lista.filter { p ->
                val buscaOk = propiedadCoincideTexto(p, busquedaActual)
                val zonaOk = zonaActiva == null ||
                    p.barrio.equals(zonaActiva, ignoreCase = true) ||
                    p.ciudad.equals(zonaActiva, ignoreCase = true)
                buscaOk && zonaOk
            }
        } else lista
        val porZona = filtrada.groupBy { it.barrio.ifEmpty { it.ciudad } }
        porZona.forEach { (zona, props) ->
            if (zona.isBlank()) return@forEach
            val center = centroZona(props)
            val badge = construirBadge(zona, props.size, center)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            badge.layoutParams = params
            badge.tag = center
            binding.overlayBadges.addView(badge)
        }
        binding.overlayBadges.post { posicionarBadges() }
    }

    /** ¿El inmueble coincide con la búsqueda por texto? (mismos campos que el adapter). */
    private fun propiedadCoincideTexto(p: Propiedad, texto: String): Boolean {
        val q = texto.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(p.titulo, p.direccion, p.barrio, p.tipo, p.ciudad)
            .any { it.lowercase().contains(q) }
    }

    private fun posicionarBadges() {
        if (!::mMap.isInitialized) return
        val projection = mMap.projection
        for (i in 0 until binding.overlayBadges.childCount) {
            val badge = binding.overlayBadges.getChildAt(i)
            val center = badge.tag as? LatLng ?: continue
            val w = badge.measuredWidth
            val h = badge.measuredHeight
            if (w <= 0 || h <= 0) continue
            val screen = projection.toScreenLocation(center)
            val lp = badge.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin = screen.x - w / 2
            lp.topMargin = screen.y - h / 2
            badge.layoutParams = lp
        }
    }

    private fun construirBadge(zona: String, count: Int, center: LatLng): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = (14.dp).toFloat()
            cardElevation = (1.dp).toFloat()
            setCardBackgroundColor(getColor(R.color.alkil_primary_soft))
            isClickable = true
            setOnClickListener {
                if (::mMap.isInitialized) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
                }
            }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(10.dp, 5.dp, 10.dp, 5.dp)
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(8.dp, 8.dp).apply { marginEnd = 4.dp }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColor(R.color.alkil_mint))
            }
        }
        inner.addView(dot)
        inner.addView(TextView(this).apply {
            text = "$zona · $count"
            textSize = 11f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(inner)
        return card
    }

    private fun centroZona(props: List<Propiedad>): LatLng {
        val lat = props.sumOf { it.lat } / props.size
        val lng = props.sumOf { it.lng } / props.size
        return LatLng(lat, lng)
    }

    // ======================================================================
    // Mapa
    // ======================================================================

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = false
        configurarBadges()
        verificarPermisosUbicacion()
    }

    private fun verificarPermisosUbicacion() {
        if (tienePermisoUbicacion()) {
            obtenerUbicacionActual()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED ||
                coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun obtenerUbicacionActual() {
        if (!tienePermisoUbicacion()) return

        mMap.isMyLocationEnabled = true

        val aplicarUbicacion = { lat: Double, lng: Double ->
            ultimaUbicacion = LatLng(lat, lng)
            adapter.setUbicacion(lat, lng)
            centrarEn(LatLng(lat, lng), true)
        }

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location != null) aplicarUbicacion(location.latitude, location.longitude)
        }.addOnFailureListener {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    aplicarUbicacion(location.latitude, location.longitude)
                } else {
                    Toast.makeText(this, R.string.location_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun centrarEn(punto: LatLng, esUbicacionPropia: Boolean) {
        limpiarMarcadores()
        if (esUbicacionPropia) {
            marcadores.add(
                mMap.addMarker(
                    MarkerOptions()
                        .position(punto)
                        .title(getString(R.string.my_location_title))
                )!!
            )
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(punto, DEFAULT_CAMERA_ZOOM))
    }

    /** Centra el mapa en los inmuebles visibles tras aplicar búsqueda o filtros. */
    private fun actualizarZonaMapa(conMensaje: Boolean = true) {
        limpiarMarcadores()
        val hayFiltro = filtroDistrito != null || filtroDepartamento != null ||
            busquedaActual.isNotBlank()
        if (!hayFiltro) {
            if (ultimaUbicacion != null) centrarEn(ultimaUbicacion!!, true)
            return
        }
        val visibles = adapter.visibles().filter { it.lat != 0.0 || it.lng != 0.0 }
        if (visibles.isEmpty()) {
            if (conMensaje) {
                Toast.makeText(this, R.string.filtros_sin_resultados, Toast.LENGTH_LONG).show()
            }
            return
        }
        val builder = LatLngBounds.Builder()
        visibles.forEach { p ->
            marcadores.add(
                mMap.addMarker(
                    MarkerOptions()
                        .position(p.ubicacion)
                        .title(p.titulo)
                        .snippet(p.precioFormateado)
                )!!
            )
            builder.include(p.ubicacion)
        }
        if (visibles.size == 1) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(visibles.first().ubicacion, 16f))
        } else {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 90))
        }
    }

    private fun abrirDetallePropiedad(propiedad: Propiedad) {
        if (::mMap.isInitialized) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(propiedad.ubicacion, 16f))
        }
        val intent = Intent(this, PropiedadDetalleActivity::class.java).apply {
            putExtra(PropiedadDetalleActivity.EXTRA_ID, propiedad.id)
            putExtra(PropiedadDetalleActivity.EXTRA_TITULO, propiedad.titulo)
            putExtra(PropiedadDetalleActivity.EXTRA_DESCRIPCION, propiedad.descripcion)
            putExtra(PropiedadDetalleActivity.EXTRA_TIPO, propiedad.tipo)
            putExtra(PropiedadDetalleActivity.EXTRA_OPERACION, propiedad.operacion)
            putExtra(PropiedadDetalleActivity.EXTRA_PRECIO, propiedad.precio)
            putExtra(PropiedadDetalleActivity.EXTRA_MONEDA, propiedad.moneda)
            putExtra(PropiedadDetalleActivity.EXTRA_DIRECCION, propiedad.direccion)
            putExtra(PropiedadDetalleActivity.EXTRA_BARRIO, propiedad.barrio)
            putExtra(PropiedadDetalleActivity.EXTRA_CIUDAD, propiedad.ciudad)
            putExtra(PropiedadDetalleActivity.EXTRA_LAT, propiedad.lat)
            putExtra(PropiedadDetalleActivity.EXTRA_LNG, propiedad.lng)
            putExtra(PropiedadDetalleActivity.EXTRA_ID_PROPIETARIO, propiedad.idPropietario)
            putExtra(PropiedadDetalleActivity.EXTRA_CONTACTO, propiedad.contacto)
            putExtra(PropiedadDetalleActivity.EXTRA_AMBIENTES, propiedad.ambientes)
            putExtra(PropiedadDetalleActivity.EXTRA_SUPERFICIE, propiedad.superficieM2)
            putStringArrayListExtra(
                PropiedadDetalleActivity.EXTRA_COMODIDADES,
                ArrayList(propiedad.comodidades)
            )
            putStringArrayListExtra(PropiedadDetalleActivity.EXTRA_FOTOS, ArrayList(propiedad.fotos))
            putStringArrayListExtra(
                PropiedadDetalleActivity.EXTRA_FOTOS_URL,
                ArrayList(propiedad.photosUrl)
            )
            putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, propiedad.esDestacado)
            putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, propiedad.estado)
        }
        startActivity(intent)
    }

    private fun limpiarMarcadores() {
        marcadores.forEach { it.remove() }
        marcadores.clear()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                obtenerUbicacionActual()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ======================================================================
    // Autenticación (registro de usuarios vía Firebase Auth + colección usuarios)
    // ======================================================================

    private fun onBotonAuth() {
        val usuario = auth.currentUser
        if (usuario != null) {
            startActivity(Intent(this, MiPerfilActivity::class.java))
        } else {
            abrirDialogoAutenticar()
        }
    }

    private fun abrirDialogoAutenticar() {
        val vista = layoutInflater.inflate(R.layout.dialog_auth, null)
        val etEmail = vista.findViewById<EditText>(R.id.etAuthEmail)
        val etPassword = vista.findViewById<EditText>(R.id.etAuthPassword)
        val dialogoSesion = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auth_titulo)
            .setView(vista)
            .setPositiveButton(R.string.auth_ingresar) { _, _ ->
                val email = etEmail.text.toString().trim()
                val pass = etPassword.text.toString()
                if (validarCredenciales(email, pass)) inicioSesion(email, pass)
            }
            .setNeutralButton(R.string.auth_registrarse) { _, _ ->
                val email = etEmail.text.toString().trim()
                val pass = etPassword.text.toString()
                if (validarCredenciales(email, pass)) registrarUsuario(email, pass)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        vista.findViewById<MaterialButton>(R.id.btnGoogleAuth).setOnClickListener {
            dialogoSesion.dismiss()
            iniciarSesionGoogle()
        }
    }

    private fun iniciarSesionGoogle() {
        if (getString(R.string.default_web_client_id).isBlank()) {
            Toast.makeText(this, R.string.auth_google_no_config, Toast.LENGTH_LONG).show()
            return
        }
        googleAuthLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun validarCredenciales(email: String, pass: String): Boolean {
        if (email.isBlank() || !email.contains("@")) {
            Toast.makeText(this, R.string.auth_email_invalido, Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass.length < 6) {
            Toast.makeText(this, R.string.auth_password_corta, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun inicioSesion(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    guardarUsuarioEnBase()
                    actualizarUiSesion()
                    Toast.makeText(this, R.string.auth_ok_login, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun registrarUsuario(email: String, pass: String) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    guardarUsuarioEnBase()
                    actualizarUiSesion()
                    Toast.makeText(this, R.string.auth_ok_registro, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun guardarUsuarioEnBase() {
        val u = auth.currentUser ?: return
        val ref = db.collection("usuarios").document(u.uid)
        val base = hashMapOf(
            "nombre" to (u.displayName ?: u.email?.substringBefore("@") ?: ""),
            "email" to (u.email ?: ""),
            "uidAuth" to u.uid,
            "activo" to true
        )
        ref.get()
            .addOnSuccessListener { doc ->
                val datos = HashMap<String, Any>(base)
                if (doc.exists()) {
                    val nombreActual = (doc.data?.get("nombre") as? String).orEmpty()
                    if (nombreActual.isNotBlank()) datos.remove("nombre")
                } else {
                    datos["telefono"] = ""
                    datos["tipoUsuario"] = "dueno"
                    datos["fechaRegistro"] = FieldValue.serverTimestamp()
                }
                ref.set(datos, SetOptions.merge())
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.auth_usuario_guardado_error, e.localizedMessage ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun actualizarUiSesion() {
        // El botón de menú no muestra estado de sesión.
    }
}

private val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

private fun Int.toDpFloat(): Float = this * Resources.getSystem().displayMetrics.density
