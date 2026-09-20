package com.alkilapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Base64
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Favoritos
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityMainBinding
import com.alkilapp.ui.InsetsUtils
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: PropiedadAdapter

    private val marcadores = mutableListOf<Marker>()
    private var marcadorSeleccionado: Marker? = null
    private var idMarcadorSeleccionado: String? = null
    private lateinit var iconoDefault: BitmapDescriptor
    private lateinit var iconoSeleccionado: BitmapDescriptor

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private var escuchaPropiedades: ListenerRegistration? = null

    private val cargadorNavFoto by lazy { Executors.newSingleThreadExecutor() }
    private val handlerMain = Handler(Looper.getMainLooper())
    private var callbackChatNoLeidos: ((Int) -> Unit)? = null

    private var filtroDepartamento: String? = null
    private var filtroDistrito: String? = null
    private var filtroTipo: String? = null
    private var filtroHabitaciones: Int? = null
    private var busquedaActual: String = ""
    private var soloFavoritos = false
    private var favoritosSet: Set<String> = emptySet()
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
        private const val NOTIFICATIONS_PERMISSION_REQUEST_CODE = 1001
        private const val DEFAULT_CAMERA_ZOOM = 15f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATIONS_PERMISSION_REQUEST_CODE
            )
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupListaDepartamentos()
        setupBotones()
        configurarMenu()
        configurarBottomSheet()
        configurarInsetsSistema()
        actualizarBotonFiltros()

        // Badge de chats sin leer en el panel lateral.
        callbackChatNoLeidos = { total -> runOnUiThread { actualizarBadgeChats(total) } }
        ChatNoLeidos.suscribir(callbackChatNoLeidos!!)

        // Verificar permiso de ubicación para usuarios nuevos
        verificarPermisoUbicacion()
    }

    override fun onDestroy() {
        super.onDestroy()
        callbackChatNoLeidos?.let { ChatNoLeidos.desuscribir(it) }
        callbackChatNoLeidos = null
        cargadorNavFoto.shutdown()
    }

    private fun verificarPermisoUbicacion() {
        val prefs = getSharedPreferences("alkilapp_prefs", MODE_PRIVATE)
        val yaMostrado = prefs.getBoolean("location_permission_shown", false)
        if (!yaMostrado) {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Activar ubicaci\u00F3n")
                    .setMessage("AlkilApp necesita tu ubicaci\u00F3n para mostrar inmuebles cercanos, calcular distancias y centrar el mapa en tu zona. Por favor, activa el permiso de ubicaci\u00F3n en los ajustes.")
                    .setPositiveButton("Activar ahora") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", packageName, null)
                        startActivity(intent)
                    }
                    .setNegativeButton("M\u00E1s tarde", null)
                    .setOnDismissListener {
                        prefs.edit().putBoolean("location_permission_shown", true).apply()
                    }
                    .show()
            } else {
                prefs.edit().putBoolean("location_permission_shown", true).apply()
            }
        }
    }

    /**
     * Configura el Bottom Sheet colapsable/expandible SIN BottomSheetBehavior
     * (la biblioteca dejaba el sheet fuera de pantalla por offsets/saved-state
     * que sobrevivían al install -r). Implementacion manual: el sheet tiene una
     * altura fija = 60% de la pantalla y se minimiza desplazándolo hacia abajo
     * con translationY (solo queda visible el peek de 100dp = handle + título).
     */
    private fun configurarBottomSheet() {
        val sheet = binding.bottomSheet
        val altoPantalla = resources.displayMetrics.heightPixels
        val alturaExpandida = (altoPantalla * 0.6f).toInt()
        val peek = 100.dp
        val maxOffset = (alturaExpandida - peek).toFloat()

        // Altura total del sheet = contenido expandido (60%). El RecyclerView
        // llena el resto con layout_weight.
        sheet.layoutParams = (sheet.layoutParams as ViewGroup.LayoutParams).apply {
            height = alturaExpandida
        }

        fun progreso(): Float = 1f - sheet.translationY / maxOffset

        fun actualizarOverlays(p: Float) {
            // p: 0 = minimizado (peek), 1 = expandido (60%)
            val fab = binding.fabMiUbicacion
            val params = fab.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = (108.dp + p * (alturaExpandida - 100.dp)).toInt()
            fab.layoutParams = params

            if (::mMap.isInitialized) {
                val topInset = binding.filaTop.measuredHeight + 16.dp
                val bottomInset = (100.dp + p * (alturaExpandida - 100.dp)) + 16.dp
                mMap.setPadding(0, topInset, 0, bottomInset.toInt())
            }
        }

        fun animarA(targetOffset: Float) {
            sheet.animate().cancel()
            sheet.animate()
                .translationY(targetOffset)
                .setDuration(220)
                .setUpdateListener { actualizarOverlays(progreso()) }
                .start()
        }

        // Arrastre vertical desde la cabecera (handle + título).
        var inicioY = 0f
        var inicioTranslation = 0f
        var arrastrado = false
        binding.sheetHeader.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    inicioY = event.rawY
                    inicioTranslation = sheet.translationY
                    arrastrado = false
                    sheet.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - inicioY
                    if (kotlin.math.abs(delta) > 8f) arrastrado = true
                    if (arrastrado) {
                        sheet.translationY =
                            (inicioTranslation + delta).coerceIn(0f, maxOffset)
                        actualizarOverlays(progreso())
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!arrastrado) {
                        // Tap en la cabecera: alternar expandido/minimizado
                        animarA(if (sheet.translationY > maxOffset * 0.4f) 0f else maxOffset)
                    } else {
                        // Soltar: quedarse del lado más próximo
                        animarA(if (sheet.translationY > maxOffset * 0.4f) maxOffset else 0f)
                    }
                    true
                }
                else -> false
            }
        }

        // Estado inicial: minimizado de entrada (solo peek, se ve el mapa).
        sheet.translationY = maxOffset
        sheet.post {
            sheet.translationY = maxOffset
            actualizarOverlays(0f)
        }
    }

    /**
     * Insets de las barras del sistema: la fila superior (menú + búsqueda + botón publicar)
     * queda por debajo de la barra de estado, y el bottomSheet reserva el espacio de la barra
     * de navegación para que sus últimos controles se puedan presionar.
     */
    private fun configurarInsetsSistema() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val arriba = InsetsUtils.arriba(insets)
            val abajo = InsetsUtils.abajo(insets)

            binding.filaTop.layoutParams =
                (binding.filaTop.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    topMargin = 8.dp + arriba
                }
            binding.bottomSheet.setPadding(0, 0, 0, abajo)

            // El panel lateral se dibuja detras de la barra de estado: solo se
            // desplaza el contenido (cabecera y filas) para no taparlo.
            binding.panelMenu.panelMenuRoot.setPadding(0, 0, 0, abajo)
            binding.panelMenu.llNavHeader.setPadding(20.dp, 24.dp + arriba, 20.dp, 22.dp)

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /** Botón de menú en la esquina superior: abre el panel lateral. */
    private fun configurarMenu() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        val panel = binding.panelMenu
        panel.llNavHeader.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            onBotonAuth()
        }
        panel.btnNavPerfil.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            onBotonAuth()
        }
        panel.btnNavChat.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            abrirChat()
        }
        panel.btnNavFiltros.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            abrirDialogoFiltros()
        }
        panel.btnNavPublicar.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            abrirRegistrarPropiedad()
        }
        panel.btnNavMisPublicaciones.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, R.string.mis_pub_sesion, Toast.LENGTH_LONG).show()
                abrirDialogoAutenticar()
                return@setOnClickListener
            }
            startActivity(Intent(this, MisPublicacionesActivity::class.java))
        }
        panel.btnNavVerificar.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            if (auth.currentUser == null) {
                Toast.makeText(this, R.string.verif_sesion_requerida, Toast.LENGTH_LONG).show()
                abrirDialogoAutenticar()
                return@setOnClickListener
            }
            startActivity(Intent(this, VerificacionActivity::class.java))
        }
        panel.btnNavSalir.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            cerrarSesion()
        }
    }

    private fun cerrarSesion() {
        auth.signOut()
        favoritosSet = Favoritos.locales(this)
        soloFavoritos = false
        adapter.setFavoritos(favoritosSet)
        adapter.setSoloFavoritos(false)
        actualizarUiSesion()
        Toast.makeText(this, R.string.auth_sesion_cerrada, Toast.LENGTH_SHORT).show()
    }

    /** Carga los favoritos: de la nube si hay sesion, si no usa el cache local. */
    private fun cargarFavoritos() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            Favoritos.sincronizar(this, uid) { ids ->
                favoritosSet = ids
                if (::adapter.isInitialized) adapter.setFavoritos(ids)
            }
        } else {
            favoritosSet = Favoritos.locales(this)
            if (::adapter.isInitialized) adapter.setFavoritos(favoritosSet)
        }
    }

    /** Corazon de la tarjeta: requiere sesion, persiste local + Firestore. */
    private fun alternarFavorito(p: Propiedad) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, R.string.favoritos_requiere_sesion, Toast.LENGTH_LONG).show()
            abrirDialogoAutenticar()
            return
        }
        val nuevo = Favoritos.alternar(this, uid, p.id)
        favoritosSet = if (nuevo) favoritosSet + p.id else favoritosSet - p.id
        adapter.setFavoritos(favoritosSet)
    }

    override fun onStart() {
        super.onStart()
        actualizarUiSesion()
        cargarFavoritos()
        escucharPropiedades()
    }

    override fun onStop() {
        super.onStop()
        escuchaPropiedades?.remove()
    }

    private fun setupListaDepartamentos() {
        adapter = PropiedadAdapter(
            onClick = { propiedad -> abrirDetallePropiedad(propiedad) },
            onAlternarFavorito = { p -> alternarFavorito(p) }
        )
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
        val sheet = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.bottom_sheet_filtros, null)
        sheet.setContentView(vista)
        sheet.dismissWithAnimation = true
        val spinnerDep = vista.findViewById<Spinner>(R.id.spFiltroDepartamento)
        val spinnerDis = vista.findViewById<Spinner>(R.id.spFiltroDistrito)
        val spinnerTipo = vista.findViewById<Spinner>(R.id.spFiltroTipo)
        val spinnerHab = vista.findViewById<Spinner>(R.id.spFiltroHabitaciones)
        val cbFavoritos = vista.findViewById<android.widget.CheckBox>(R.id.cbSoloFavoritos)
        cbFavoritos.isChecked = soloFavoritos

        val todos = getString(R.string.filtros_todos)
        val departamentos = resources.getStringArray(R.array.departamentos_peru).toList()
        val distritosLima = resources.getStringArray(R.array.distritos_lima).toList()
        val distritosGenerico = listOf(todos, "Otro")
        val distritosConTodos = listOf(todos) + distritosLima
        val tipos = resources.getStringArray(R.array.tipos_inmueble)
        val opcionesHab = listOf(todos) + resources.getStringArray(R.array.opciones_habitaciones).toList()

        fun llenar(sp: Spinner, opciones: List<String>) {
            sp.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_item, opciones
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        llenar(spinnerDep, listOf(todos) + departamentos)
        llenar(spinnerDis, distritosConTodos)
        llenar(spinnerTipo, listOf(todos) + tipos)
        llenar(spinnerHab, opcionesHab)

        // Actualizar distritos según departamento seleccionado
        spinnerDep.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val dep = (listOf(todos) + departamentos)[position]
                val nuevosDistritos = if (dep == "Lima") distritosConTodos else distritosGenerico
                spinnerDis.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    nuevosDistritos
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val depActual = filtroDepartamento ?: todos
        spinnerDep.setSelection(
            (todos + departamentos).indexOfFirst { it.lowercase() == depActual.lowercase() }.coerceAtLeast(0)
        )
        val disActual = filtroDistrito ?: todos
        spinnerDis.setSelection(
            distritosConTodos.indexOfFirst { it.lowercase() == disActual.lowercase() }.coerceAtLeast(0)
        )
        val tipoActual = filtroTipo ?: todos
        spinnerTipo.setSelection(
            (todos + tipos).indexOfFirst { it.lowercase() == tipoActual.lowercase() }.coerceAtLeast(0)
        )
        val habPorMostrar = filtroHabitaciones?.let { if (it == 4) "4 o más" else it.toString() } ?: todos
        spinnerHab.setSelection(opcionesHab.indexOfFirst { it == habPorMostrar }.coerceAtLeast(0))

        val aplicar = View.OnClickListener {
            val dep = spinnerDep.selectedItem as String
            val dis = spinnerDis.selectedItem as String
            val tipo = spinnerTipo.selectedItem as String
            val habSel = spinnerHab.selectedItem as String
            val quiereFavoritos = cbFavoritos.isChecked
            if (quiereFavoritos && auth.currentUser == null) {
                soloFavoritos = false
                Toast.makeText(
                    this, R.string.favoritos_requiere_sesion, Toast.LENGTH_LONG
                ).show()
            } else {
                soloFavoritos = quiereFavoritos
            }
            filtroDepartamento = dep.takeUnless { it == todos }
            filtroDistrito = dis.takeUnless { it == todos }
            filtroTipo = tipo.takeUnless { it == todos }
            filtroHabitaciones = when (habSel) {
                todos -> null
                "4 o más" -> 4
                else -> habSel.toIntOrNull()
            }
            adapter.setFiltros(
                filtroDepartamento, filtroDistrito, filtroTipo, filtroHabitaciones
            )
            adapter.setSoloFavoritos(soloFavoritos)
            actualizarBotonFiltros()
            actualizarZonaMapa()
            sheet.dismiss()
        }

        val limpiar = View.OnClickListener {
            filtroDepartamento = null
            filtroDistrito = null
            filtroTipo = null
            filtroHabitaciones = null
            soloFavoritos = false
            binding.etBusqueda.setText("")
            adapter.setFiltros(null, null)
            adapter.setSoloFavoritos(false)
            actualizarBotonFiltros()
            actualizarZonaMapa()
            sheet.dismiss()
        }

        vista.findViewById<MaterialButton>(R.id.btnFiltrosAplicar).setOnClickListener(aplicar)
        vista.findViewById<MaterialButton>(R.id.btnFiltrosLimpiar).setOnClickListener(limpiar)
        sheet.show()
    }

    private fun actualizarBotonFiltros() {
        val activo = (filtroDistrito ?: filtroDepartamento) != null ||
            (filtroTipo != null) || (filtroHabitaciones != null) ||
            soloFavoritos || busquedaActual.isNotBlank()
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
                    if (ok) {
                        val d = tarea.result
                        val identidadVerificada =
                            (d?.get("verification") as? Map<*, *>)?.get("identityVerified") == true
                        val badge = d?.get("verificationBadge") == true
                        mapa[uid] = badge || identidadVerificada
                    }
                    pendientes--
                    if (pendientes <= 0) adapter.setPropietariosVerificados(mapa)
                }
        }
    }

    // ======================================================================
    // Badges estáticos de disponibilidad por zona sobre el mapa
    // ======================================================================

    private fun configurarBadges() {
        mMap.setOnCameraIdleListener {
            posicionarBadges()
            actualizarMarcadoresEnZonaVisible()
        }
    }

    private fun initIconosMarcadores() {
        iconoDefault = cargarIconoRes(R.drawable.ic_marker_inmueble)
        iconoSeleccionado = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    }

    /** Rasteriza un recurso de dibujo (vector) en un Bitmap para usarlo como icono de marcador. */
    private fun cargarIconoRes(resId: Int): BitmapDescriptor {
        val icono = AppCompatResources.getDrawable(this, resId) ?: return BitmapDescriptorFactory.defaultMarker()
        val bitmap = Bitmap.createBitmap(icono.intrinsicWidth, icono.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        icono.setBounds(0, 0, icono.intrinsicWidth, icono.intrinsicHeight)
        icono.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /** Añade/actualiza marcadores solo para inmuebles dentro de la vista actual del mapa. */
    private fun actualizarMarcadoresEnZonaVisible() {
        if (!::mMap.isInitialized) return
        if (!::iconoDefault.isInitialized) initIconosMarcadores()
        val bounds = mMap.projection.visibleRegion.latLngBounds
        limpiarMarcadores()
        val visibles = adapter.visibles().filter { p ->
            p.lat != 0.0 && p.lng != 0.0 && bounds.contains(p.ubicacion)
        }
        visibles.forEach { p ->
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(p.ubicacion)
                    .title(p.titulo)
                    .snippet(p.precioFormateado)
                    .icon(iconoDefault)
            )!!
            marker.tag = p.id
            marcadores.add(marker)
        }
        // Reaplicar la selección previa si el marcador sigue en la zona visible
        if (idMarcadorSeleccionado != null && marcadorSeleccionado == null) {
            val markerSel = marcadores.firstOrNull { it.tag == idMarcadorSeleccionado }
            if (markerSel != null) {
                markerSel.setIcon(iconoSeleccionado)
                markerSel.showInfoWindow()
                marcadorSeleccionado = markerSel
            } else {
                idMarcadorSeleccionado = null
            }
        }
        mMap.setOnMarkerClickListener { marker ->
            // Resetear el anterior
            marcadorSeleccionado?.setIcon(iconoDefault)
            // Seleccionar el nuevo
            marker.setIcon(iconoSeleccionado)
            marcadorSeleccionado = marker
            idMarcadorSeleccionado = marker.tag as? String
            // Centrar el mapa en el punto seleccionado (manteniendo el zoom)
            mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
            // Mostrar info window y mantenerla abierta
            marker.showInfoWindow()
            true // consumimos el click
        }
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

        // Padding para que el mapa visible quede arriba del bottom sheet.
        // Inicialmente el sheet está colapsado (peek 100dp); al deslizarlo,
        // onSlide() actualiza este padding en tiempo real.
        mMap.setOnMapLoadedCallback {
            val topInset = binding.filaTop.measuredHeight + 16.dp
            val bottomInset = 100.dp + 16.dp
            mMap.setPadding(0, topInset, 0, bottomInset)
        }

        // InfoWindow estilo Booking con precio
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View {
                val p = propiedadDesdeMarcador(marker) ?: return View(this@MainActivity)
                return construirInfoWindow(p)
            }

            override fun getInfoContents(marker: Marker): View? = null
        })
        mMap.setOnInfoWindowClickListener { marker ->
            propiedadDesdeMarcador(marker)?.let { abrirDetallePropiedad(it) }
        }
configurarBadges()
        verificarPermisosUbicacion()
    }

    /** Busca la propiedad asociada a un marcador (etiquetado con su id). */
    private fun propiedadDesdeMarcador(marker: Marker): Propiedad? {
        val id = marker.tag as? String ?: return null
        if (id.isBlank()) return null
        return adapter.visibles().firstOrNull { it.id == id }
    }

    /** Normaliza el tipo a 4 opciones: habitacion, departamento, casa u otros. */
    private fun tipoMostrable(tipo: String): String = when (tipo.trim().lowercase()) {
        "habitacion", "habitación", "cuarto" -> "Habitacion"
        "departamento", "depto" -> "Departamento"
        "casa" -> "Casa"
        else -> "Otros"
    }

    /** Construye el InfoWindow estilo Booking con foto, título, precio y botón ver. */
    private fun construirInfoWindow(p: Propiedad): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = (12.dp).toFloat()
            cardElevation = (4.dp).toFloat()
            setCardBackgroundColor(getColor(R.color.white))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        }
        // Tipo de inmueble (pedido del padre: en el popup se ve el tipo, no el titulo)
        root.addView(TextView(this).apply {
            text = tipoMostrable(p.tipo)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.text_primary))
        })
        // Precio grande
        root.addView(TextView(this).apply {
            text = p.precioFormateado
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.alkil_primary))
        })
        // Zona
        root.addView(TextView(this).apply {
            text = p.barrio.ifEmpty { p.ciudad }
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
        })
        // Botón "Ver"
        root.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "Ver propiedad"
            textSize = 12f
            insetTop = 0.dp
            insetBottom = 0.dp
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.alkil_primary))
            setTextColor(getColor(R.color.white))
            setOnClickListener { abrirDetallePropiedad(p) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
        })
        card.addView(root)
        return card
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
            soloFavoritos || busquedaActual.isNotBlank()
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
                        .title(p.tipo)
                        .snippet(p.precioFormateado)
                )!!.apply { tag = p.id }
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
        try {
            if (::mMap.isInitialized && propiedad.lat != 0.0 && propiedad.lng != 0.0) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(propiedad.ubicacion, 16f))
            }
        } catch (e: Exception) {
            // Ignorar errores de animación del mapa
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
            putExtra(PropiedadDetalleActivity.EXTRA_AMBIENTES, propiedad.ambientes)
            putExtra(PropiedadDetalleActivity.EXTRA_SUPERFICIE, propiedad.superficieM2)
            putStringArrayListExtra(
                PropiedadDetalleActivity.EXTRA_COMODIDADES,
                ArrayList(propiedad.comodidades)
            )
            // NO pasar fotos base64 por el intent: supera el límite de Binder
            // (TransactionTooLargeException). El detalle las carga por ID desde Firestore.
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
    // Se limpia la referencia pero se conserva idMarcadorSeleccionado para
    // poder volver a mostrarla cuando se reconstruyan los marcadores.
    marcadorSeleccionado = null
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
        startActivity(Intent(this, AuthActivity::class.java))
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
        val usuario = auth.currentUser
        val panel = binding.panelMenu
        if (usuario != null) {
            val nombre = usuario.displayName?.takeIf { it.isNotBlank() }
                ?: usuario.email?.substringBefore('@').orEmpty()
            panel.tvNavNombre.text = nombre
            panel.tvNavEmail.text = usuario.email.orEmpty()
            panel.tvNavAvatar.text = nombre.take(1).uppercase()
            panel.tvNavSesionHint.visibility = View.GONE
            panel.tvNavEmail.visibility = View.VISIBLE
            panel.btnNavSalir.visibility = View.VISIBLE
            panel.ivNavFoto.visibility = View.GONE
            panel.tvNavAvatar.visibility = View.VISIBLE
            cargarFotoNav()
        } else {
            panel.tvNavNombre.text = getString(R.string.nav_invitado)
            panel.tvNavEmail.text = ""
            panel.tvNavEmail.visibility = View.GONE
            panel.tvNavAvatar.text = "?"
            panel.tvNavSesionHint.visibility = View.VISIBLE
            panel.btnNavSalir.visibility = View.GONE
            panel.ivNavFoto.visibility = View.GONE
            panel.tvNavAvatar.visibility = View.VISIBLE
        }
        actualizarBadgeChats(ChatNoLeidos.totalActual())
    }

    /** Muestra la foto de perfil (fotoBase64 o profilePicture) en la cabecera del panel. */
    private fun cargarFotoNav() {
        val u = auth.currentUser ?: return
        db.collection("usuarios").document(u.uid).get()
            .addOnSuccessListener { doc ->
                val d = doc.data ?: return@addOnSuccessListener
                val b64 = d["fotoBase64"] as? String
                val url = d["profilePicture"] as? String
                cargadorNavFoto.execute {
                    val bmp = when {
                        !b64.isNullOrBlank() -> descodificarB64(b64)
                        !url.isNullOrBlank() -> descargarBitmap(url)
                        else -> null
                    }
                    handlerMain.post {
                        if (bmp != null) {
                            binding.panelMenu.ivNavFoto.setImageBitmap(bmp)
                            binding.panelMenu.ivNavFoto.visibility = View.VISIBLE
                            binding.panelMenu.tvNavAvatar.visibility = View.GONE
                        }
                    }
                }
            }
            .addOnFailureListener { }
    }

    private fun descodificarB64(b64: String): Bitmap? {
        val bytes = try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun descargarBitmap(urlString: String): Bitmap? {
        return try {
            val conexion = URL(urlString).openConnection() as HttpURLConnection
            conexion.connectTimeout = 10000
            conexion.readTimeout = 10000
            val stream = conexion.inputStream
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            conexion.disconnect()
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /** Badge rojo de chats sin leer en la fila "Mis chats" del panel lateral. */
    private fun actualizarBadgeChats(total: Int) {
        val badge = binding.panelMenu.tvNavChatBadge
        if (total > 0) {
            badge.text = if (total > 99) "99+" else total.toString()
            badge.contentDescription = getString(R.string.nav_chats_sin_leer, total)
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }
}

private val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

private fun Int.toDpFloat(): Float = this * Resources.getSystem().displayMetrics.density
