package com.alkilapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.data.Propiedad
import com.alkilapp.databinding.ActivityMisPublicacionesBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.HashMap
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Pantalla estática "Mis publicaciones": lista los inmuebles del usuario con su estado. */
class MisPublicacionesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMisPublicacionesBinding
    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private val billingManager by lazy { com.alkilapp.billing.BillingManager(this) }
    private val fotosExecutor = Executors.newFixedThreadPool(2)
    private var escuchaInmuebles: ListenerRegistration? = null
    private var inmuebles = emptyList<Propiedad>()
    private var filtroActual = FiltroPublicaciones.TODAS

    private enum class FiltroPublicaciones { TODAS, DISPONIBLES, FINALIZADAS, PAUSADAS, REVISION }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisPublicacionesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMisPubBack.setOnClickListener { finish() }

        // Inicializar Billing Manager
        billingManager.initialize { Log.d("AlkilAppBilling", "Billing listo en MisPublicaciones") }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, R.string.mis_pub_sesion, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.tvMisPubCuenta.text =
            getString(R.string.mis_pub_cuenta, auth.currentUser?.email ?: uid)
        escucharInmuebles(uid)
    }

    private fun escucharInmuebles(uid: String) {
        if (uid.isBlank()) {
            binding.tvMisPubCargando.visibility = View.GONE
            binding.tvMisPubVacio.visibility = View.VISIBLE
            binding.tvMisPubVacio.text = getString(R.string.mis_pub_error, "UID vacío")
            return
        }
        // Se guarda la suscripcion para poder soltarla en onDestroy: sin remove()
        // el listener de "mis publicaciones" sobrevivia a la pantalla.
        escuchaInmuebles?.remove()
        escuchaInmuebles = db.collection("propiedades")
            .whereEqualTo("idPropietario", uid)
            .addSnapshotListener { snap, error ->
                binding.tvMisPubCargando.visibility = View.GONE
                if (error != null) {
                    Log.w("AlkilApp", "MisPublicaciones error uid=$uid: ${error.message}")
                    binding.llMisPublicaciones.removeAllViews()
                    binding.tvMisPubVacio.visibility = View.VISIBLE
                    binding.tvMisPubVacio.text =
                        getString(R.string.mis_pub_error, error.message ?: "?")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                inmuebles = snap.documents.mapNotNull { Propiedad.desde(it) }
                    .sortedWith(
                        compareByDescending<Propiedad> { it.estadoNormalizado == "disponible" }
                            .thenBy { it.precio }
                    )
                Log.i("AlkilApp", "MisPublicaciones uid=$uid docs=${snap.size()} mostrando=${inmuebles.size}")
                actualizarPublicaciones()
            }
    }

    private fun actualizarPublicaciones() {
        binding.tvMisPubTotal.text = inmuebles.size.toString()
        binding.tvMisPubDisponibles.text = inmuebles.count { it.estadoNormalizado == "disponible" }.toString()
        construirFiltros()
        val visibles = inmuebles.filter { propiedad ->
            when (filtroActual) {
                FiltroPublicaciones.TODAS -> true
                FiltroPublicaciones.DISPONIBLES -> propiedad.estadoNormalizado == "disponible"
                FiltroPublicaciones.FINALIZADAS -> propiedad.estadoNormalizado == "finalizado"
                FiltroPublicaciones.PAUSADAS -> propiedad.estadoNormalizado == "pausada"
                FiltroPublicaciones.REVISION -> propiedad.estadoNormalizado !in
                    setOf("disponible", "finalizado", "pausada")
            }
        }
        binding.llMisPublicaciones.removeAllViews()
        if (visibles.isEmpty()) {
            binding.tvMisPubVacio.visibility = View.VISIBLE
            binding.tvMisPubVacio.text = if (inmuebles.isEmpty()) {
                getString(R.string.mis_pub_vacio)
            } else getString(R.string.mis_pub_filtro_sin_resultados)
            return
        }
        binding.tvMisPubVacio.visibility = View.GONE
        visibles.forEach { propiedad -> binding.llMisPublicaciones.addView(construirInmueble(propiedad)) }
    }

    private fun construirFiltros() {
        val opciones = listOf(
            Triple(FiltroPublicaciones.TODAS, R.string.mis_pub_filtro_todas, inmuebles.size),
            Triple(FiltroPublicaciones.DISPONIBLES, R.string.mis_pub_filtro_disponibles,
                inmuebles.count { it.estadoNormalizado == "disponible" }),
            Triple(FiltroPublicaciones.FINALIZADAS, R.string.mis_pub_filtro_finalizadas,
                inmuebles.count { it.estadoNormalizado == "finalizado" }),
            Triple(FiltroPublicaciones.PAUSADAS, R.string.mis_pub_filtro_pausadas,
                inmuebles.count { it.estadoNormalizado == "pausada" }),
            Triple(FiltroPublicaciones.REVISION, R.string.mis_pub_filtro_revision,
                inmuebles.count { it.estadoNormalizado !in setOf("disponible", "finalizado", "pausada") })
        )
        binding.llMisPubFiltros.removeAllViews()
        opciones.forEach { (filtro, titulo, cantidad) ->
            val chip = TextView(this).apply {
                text = "${getString(titulo)}  $cantidad"
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(14.dp, 9.dp, 14.dp, 9.dp)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24.dp.toFloat()
                    val activo = filtro == filtroActual
                    setColor(getColor(if (activo) R.color.alkil_primary else R.color.surface))
                    setStroke(1.dp, getColor(if (activo) R.color.alkil_primary else R.color.divider))
                }
                setTextColor(getColor(if (filtro == filtroActual) R.color.white else R.color.text_secondary))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    filtroActual = filtro
                    actualizarPublicaciones()
                }
            }
            binding.llMisPubFiltros.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        escuchaInmuebles?.remove()
        escuchaInmuebles = null
        fotosExecutor.shutdownNow()
    }

    private fun construirInmueble(p: Propiedad): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
            radius = 17.dp.toFloat()
            cardElevation = 2.dp.toFloat()
            setCardBackgroundColor(getColor(R.color.surface))
            strokeWidth = 1.dp
            strokeColor = getColor(R.color.divider)
            isClickable = true
            isFocusable = true
            setOnClickListener { abrirDetalle(p) }
        }

        val cont = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 12.dp, 12.dp, 11.dp)
        }

        val filaPrincipal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val marcoFoto = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(112.dp, 116.dp)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 13.dp.toFloat()
                setColor(getColor(R.color.alkil_primary_soft))
            }
            clipToOutline = true
        }
        val foto = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.mis_pub_foto_cd, p.titulo)
            setBackgroundColor(getColor(R.color.alkil_primary_soft))
            setImageResource(R.drawable.ic_launcher_foreground)
            tag = p.id
        }
        marcoFoto.addView(foto)
        marcoFoto.addView(estadoChip(p).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.START
            ).apply { setMargins(7.dp, 7.dp, 0, 0) }
            setPadding(8.dp, 4.dp, 8.dp, 4.dp)
            textSize = 10f
        })
        if (p.esDestacado) {
            marcoFoto.addView(TextView(this).apply {
                text = "★"
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.white))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(getColor(R.color.alkil_gold))
                }
                layoutParams = FrameLayout.LayoutParams(25.dp, 25.dp,
                    android.view.Gravity.BOTTOM or android.view.Gravity.END).apply {
                    setMargins(0, 0, 7.dp, 7.dp)
                }
            })
        }
        cargarFoto(foto, p)
        filaPrincipal.addView(marcoFoto)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 1.dp, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = tipoMostrable(p.tipo).uppercase()
            textSize = 10f
            letterSpacing = 0.06f
            setTextColor(getColor(R.color.coral_text))
            setTypeface(null, Typeface.BOLD)
        })
        header.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_more_vert)
            setColorFilter(getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(36.dp, 36.dp)
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.alkil_primary_light)), null, null
            )
            setScaleType(ImageView.ScaleType.CENTER)
            setOnClickListener { v -> mostrarMenuOpciones(v, p) }
            contentDescription = "Más opciones"
        })
        info.addView(header)

        info.addView(TextView(this).apply {
            text = p.titulo
            textSize = 14f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 2.dp, 0, 0)
        })
        info.addView(TextView(this).apply {
            text = "⌖  ${p.barrio.ifEmpty { p.ciudad }}"
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 4.dp, 0, 0)
        })
        info.addView(TextView(this).apply {
            text = p.precioFormateado
            textSize = 17f
            setTextColor(getColor(R.color.alkil_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 9.dp, 0, 0)
        })
        filaPrincipal.addView(info)
        cont.addView(filaPrincipal)

        cont.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.divider))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1.dp
            ).apply { topMargin = 10.dp }
        })

        val acciones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 9.dp, 0, 0)
        }
        val permiteDestacar = p.estadoNormalizado != "finalizado"
        if (permiteDestacar) {
            acciones.addView(botonAccion(
                texto = getString(if (p.esDestacado) R.string.mis_pub_destacada else R.string.mis_pub_destacar),
                fondo = if (p.esDestacado) R.color.alkil_gold else R.color.alkil_gold_soft,
                textoColor = if (p.esDestacado) R.color.white else R.color.gold_text,
                icono = R.drawable.ic_star
            ) { toggleDestacado(p) }, LinearLayout.LayoutParams(0, 42.dp, 1f).apply { marginEnd = 6.dp })
        }
        acciones.addView(botonAccion(
            texto = getString(R.string.mis_pub_editar),
            fondo = R.color.alkil_coral_soft,
            textoColor = R.color.coral_text,
            icono = R.drawable.ic_editar
        ) { editarPublicacion(p) }, LinearLayout.LayoutParams(
            0, 42.dp, if (permiteDestacar) 1f else 2f
        ).apply { marginStart = if (permiteDestacar) 6.dp else 0 })
        cont.addView(acciones)

        card.addView(cont)
        return card
    }

    private fun botonAccion(
        texto: String,
        fondo: Int,
        textoColor: Int,
        icono: Int,
        onClick: () -> Unit
    ): MaterialButton = MaterialButton(this).apply {
        text = texto
        textSize = 12f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        setTextColor(getColor(textoColor))
        backgroundTintList = ColorStateList.valueOf(getColor(fondo))
        icon = getDrawable(icono)
        iconTint = ColorStateList.valueOf(getColor(textoColor))
        iconPadding = 5.dp
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        insetTop = 0
        insetBottom = 0
        cornerRadius = 11.dp
        setOnClickListener { onClick() }
    }

    private fun cargarFoto(image: ImageView, propiedad: Propiedad) {
        val base64 = propiedad.fotos.firstOrNull()
        val url = propiedad.photosUrl.firstOrNull()
        if (base64.isNullOrBlank() && url.isNullOrBlank()) return
        fotosExecutor.execute {
            val bitmap = if (!base64.isNullOrBlank()) {
                try {
                    Base64.decode(base64, Base64.NO_WRAP).let(::decodificarMiniatura)
                } catch (_: IllegalArgumentException) {
                    null
                }
            } else {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 6000
                    connection.readTimeout = 8000
                    connection.instanceFollowRedirects = true
                    connection.inputStream.use { decodificarMiniatura(it.readBytes()) }
                } catch (e: Exception) {
                    Log.w("AlkilApp", "No se pudo cargar miniatura id=${propiedad.id}: ${e.message}")
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            if (bitmap != null) runOnUiThread {
                if (image.tag == propiedad.id && !isFinishing) image.setImageBitmap(bitmap)
            }
        }
    }

    private fun decodificarMiniatura(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val objetivo = (resources.displayMetrics.density * 420).toInt()
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= objetivo) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun tipoMostrable(tipo: String): String = when (tipo.trim().lowercase()) {
        "habitacion", "habitación", "cuarto" -> "Habitacion"
        "departamento", "depto" -> "Departamento"
        "casa" -> "Casa"
        else -> "Otros"
    }

    private fun estadoChip(p: Propiedad): TextView {
        val (etiqueta, colorText, colorBg) = when (p.estadoNormalizado) {
            "disponible" -> Triple(
                getString(R.string.prop_estado_disponible),
                R.color.success_text,
                R.color.alkil_mint_soft
            )
            "finalizado" -> Triple(
                getString(R.string.prop_estado_finalizado),
                R.color.text_secondary,
                R.color.alkil_gray_soft
            )
            "pausada" -> Triple(
                getString(R.string.prop_estado_pausada),
                R.color.text_secondary,
                R.color.alkil_gray_soft
            )
            else -> Triple(
                getString(R.string.prop_estado_revision),
                R.color.gold_text,
                R.color.alkil_gold_soft
            )
        }
        return TextView(this).apply {
            text = etiqueta
            textSize = 12f
            setTextColor(getColor(colorText))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_chip_info)
            backgroundTintList = ColorStateList.valueOf(getColor(colorBg))
            setPadding(10.dp, 3.dp, 10.dp, 3.dp)
        }
    }

    private fun abrirDetalle(p: Propiedad) {
        Log.i("AlkilApp", "MisPublicaciones: abriendo detalle id=${p.id} titulo=${p.titulo}")
        Toast.makeText(this, "Abriendo ${p.titulo}", Toast.LENGTH_SHORT).show()
        Intent(this, PropiedadDetalleActivity::class.java).apply {
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
            // NO pasar fotos base64 por el intent: supera el límite de Binder
            // (TransactionTooLargeException). El detalle las carga por ID desde Firestore.
            putStringArrayListExtra(
                PropiedadDetalleActivity.EXTRA_FOTOS_URL,
                ArrayList(p.photosUrl)
            )
            putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, p.esDestacado)
            putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, p.estado)
        }.also { startActivity(it) }
    }

    private fun mostrarMenuOpciones(anchor: View, p: Propiedad) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_mis_publicaciones, popup.menu)
        val menu = popup.menu
        val estadoNorm = p.estadoNormalizado
        // Editar y destacar ya aparecen como botones principales en cada tarjeta.
        menu.findItem(R.id.menu_editar).isVisible = false
        // Mostrar/ocultar opciones según estado
        // Pausar: solo si disponible
        menu.findItem(R.id.menu_pausar).isVisible = estadoNorm == "disponible"
        // Reactivar: solo si pausada por usuario (estado "pausada")
        menu.findItem(R.id.menu_reactivar).isVisible = estadoNorm == "pausada"
        // Marcar como alquilado/vendido: solo si disponible o pausada
        menu.findItem(R.id.menu_marcar_alquilado).isVisible = estadoNorm == "disponible" || estadoNorm == "pausada"
        menu.findItem(R.id.menu_marcar_alquilado).title =
            getString(if (p.esVenta) R.string.prop_marcar_vendido else R.string.prop_marcar_alquilado)
        // Destacar: solo si la publicación no está finalizada; cambia título según estado
        menu.findItem(R.id.menu_destacar).isVisible = estadoNorm != "finalizado"
        menu.findItem(R.id.menu_destacar).title = if (p.esDestacado) "Quitar destacado" else "Destacar publicacion"
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_editar -> {
                    editarPublicacion(p)
                    true
                }
                R.id.menu_destacar -> {
                    toggleDestacado(p)
                    true
                }
                R.id.menu_pausar -> {
                    pausarPublicacion(p)
                    true
                }
                R.id.menu_reactivar -> {
                    reactivarPublicacion(p)
                    true
                }
                R.id.menu_marcar_alquilado -> {
                    marcarComoAlquilado(p)
                    true
                }
                R.id.menu_eliminar -> {
                    confirmarEliminar(p)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun editarPublicacion(p: Propiedad) {
        Intent(this, RegistrarPropiedadActivity::class.java).apply {
            putExtra(RegistrarPropiedadActivity.EXTRA_EDIT_MODE, true)
            putExtra(RegistrarPropiedadActivity.EXTRA_PROPIEDAD_ID, p.id)
        }.also { startActivity(it) }
    }

    private fun toggleDestacado(p: Propiedad) {
        if (p.esDestacado) {
            quitarDestacado(p)
        } else {
            mostrarDialogoDestacar(p)
        }
    }

    private fun mostrarDialogoDestacar(p: Propiedad) {
        val opciones = listOf(
            Pair(7, 6.90),
            Pair(15, 12.90),
            Pair(30, 24.90)
        )
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_destacar_opciones, null)
        dialog.setContentView(view)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvOpcionesDestacar)
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recycler.adapter = OpcionesDestacarAdapter(opciones) { dias, precio ->
            dialog.dismiss()
            iniciarPagoDestacado(p, dias, precio)
        }
        dialog.show()
    }

    /** Adapter para las opciones de destacado en BottomSheet. */
    private inner class OpcionesDestacarAdapter(
        private val items: List<Pair<Int, Double>>,
        private val onClick: (Int, Double) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<OpcionesDestacarAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvTexto: android.widget.TextView = view.findViewById(R.id.tvOpcionDestacar)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_opcion_destacar, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (dias, precio) = items[position]
            holder.tvTexto.text = "$dias días - S/ $precio"
            holder.itemView.setOnClickListener { onClick(dias, precio) }
        }

        override fun getItemCount() = items.size
    }

    private fun iniciarPagoDestacado(p: Propiedad, dias: Int, precio: Double) {
        val sku = when (dias) {
            7 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_7D
            15 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_15D
            else -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_30D
        }

        // Primero guardamos la solicitud pendiente en Firestore
        db.collection("propiedades").document(p.id)
            .set(mapOf(
                "solicitudDestacar" to true,
                "destacadoDias" to dias,
                "destacadoPrecio" to precio,
                "destacadoSku" to sku,
                "solicitudDestacarEn" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ), SetOptions.merge())
            .addOnSuccessListener {
                // Lanzar flujo de compra de Google Play Billing
                billingManager.launchPurchaseFlow(sku, object : com.alkilapp.billing.BillingManager.PurchaseCallback {
                    override fun onSuccess(productId: String, purchaseToken: String, orderId: String?) {
                        // Compra exitosa: activar destacado en Firestore
                        val hasta = java.util.Date(System.currentTimeMillis() + dias * 24L * 3600 * 1000)
                        val datosDestacado = HashMap<String, Any>().apply {
                                put("isFeatured", true)
                                put("featuredUntil", hasta)
                                put("destacadoDias", dias)
                                put("destacadoDiasRestantes", dias)
                                put("destacadoEstado", "aprobado")
                                put("solicitudDestacar", false)
                                put("destacadoAprobadoEn", com.google.firebase.firestore.FieldValue.serverTimestamp())
                                put("destacadoAprobadoPor", "google-play-billing")
                                put("purchaseToken", purchaseToken)
                                put("orderId", orderId ?: "")
                            }
                            db.collection("propiedades").document(p.id)
                                .set(datosDestacado, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(this@MisPublicacionesActivity, "¡Destacado activado por $dias días!", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this@MisPublicacionesActivity, "Compra OK pero error guardando: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }

                    override fun onError(message: String) {
                        // Error en compra: limpiar solicitud pendiente
                        val datosLimpieza = HashMap<String, Any?>().apply {
                            put("solicitudDestacar", false)
                            put("destacadoSku", null)
                        }
                        db.collection("propiedades").document(p.id)
                            .set(datosLimpieza, SetOptions.merge())
                        Toast.makeText(this@MisPublicacionesActivity, "Error en compra: $message", Toast.LENGTH_LONG).show()
                    }
                })
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error guardando solicitud: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun quitarDestacado(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .set(mapOf(
                "isFeatured" to false,
                "featuredUntil" to null,
                "destacadoDiasRestantes" to 0,
                "destacadoEstado" to "retirado",
                "solicitudDestacar" to false
            ), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Destacado retirado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun pausarPublicacion(p: Propiedad) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pausar publicacion")
            .setMessage("La publicacion dejara de mostrarse en el listado. Podras reactivarla cuando quieras.")
            .setPositiveButton("Pausar") { _, _ ->
                db.collection("propiedades").document(p.id)
                    .set(mapOf("estado" to "pausada"), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Publicacion pausada", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun reactivarPublicacion(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .set(mapOf("estado" to "disponible"), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Publicacion reactivada (disponible)", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun marcarComoAlquilado(p: Propiedad) {
        val esVenta = p.esVenta
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(
                if (esVenta) R.string.detalle_finalizar_confirm_titulo_venta
                else R.string.detalle_finalizar_confirm_titulo
            )
            .setMessage(R.string.detalle_finalizar_confirm_msg)
            .setPositiveButton(R.string.detalle_finalizar_si) { _, _ ->
                db.collection("propiedades").document(p.id)
                    .set(mapOf("estado" to "finalizado"), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.detalle_finalizar_ok, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.detalle_finalizar_error, e.localizedMessage ?: "?"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .show()
    }

    private fun confirmarEliminar(p: Propiedad) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar publicacion")
            .setMessage("¿Eliminar definitivamente \"${p.titulo}\"? Esta accion no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> eliminarPublicacion(p) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarPublicacion(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Publicacion eliminada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
