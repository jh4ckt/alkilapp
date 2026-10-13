package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.FiltroPublicaciones
import com.alkilapp.compose.HojaOpcionesDestacar
import com.alkilapp.compose.PantallaMisPublicaciones
import com.alkilapp.data.Propiedad
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.HashMap

/** Pantalla estatica "Mis publicaciones": lista los inmuebles del usuario con su estado. */
class MisPublicacionesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }
    private val billingManager by lazy { com.alkilapp.billing.BillingManager(this) }
    private var escuchaInmuebles: ListenerRegistration? = null

    private var inmuebles by mutableStateOf(emptyList<Propiedad>())
    private var filtroActual by mutableStateOf(FiltroPublicaciones.TODAS)
    private var cargando by mutableStateOf(true)
    private var errorTexto by mutableStateOf<String?>(null)
    private var cuentaTexto by mutableStateOf("")
    private var sheetDestacarPara by mutableStateOf<Propiedad?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Billing Manager
        billingManager.initialize { Log.d("AlkilAppBilling", "Billing listo en MisPublicaciones") }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, R.string.mis_pub_sesion, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        cuentaTexto = getString(R.string.mis_pub_cuenta, auth.currentUser?.email ?: uid)

        setContent {
            AlkilTheme {
                PantallaMisPublicaciones(
                    cuenta = cuentaTexto,
                    inmuebles = inmuebles,
                    cargando = cargando,
                    errorTexto = errorTexto,
                    filtroActual = filtroActual,
                    onFiltro = { filtroActual = it },
                    onAtras = { finish() },
                    onAbrirDetalle = { p -> abrirDetalle(p) },
                    onEditar = { p -> editarPublicacion(p) },
                    onToggleDestacado = { p -> toggleDestacado(p) },
                    onPausar = { p -> pausarPublicacion(p) },
                    onReactivar = { p -> reactivarPublicacion(p) },
                    onMarcarAlquilado = { p -> marcarComoAlquilado(p) },
                    onEliminar = { p -> confirmarEliminar(p) }
                )
                sheetDestacarPara?.let { propiedad ->
                    HojaOpcionesDestacar(
                        onOpcion = { dias, precio ->
                            sheetDestacarPara = null
                            iniciarPagoDestacado(propiedad, dias, precio)
                        },
                        onCerrar = { sheetDestacarPara = null }
                    )
                }
            }
        }

        escucharInmuebles(uid)
    }

    private fun escucharInmuebles(uid: String) {
        if (uid.isBlank()) {
            cargando = false
            errorTexto = getString(R.string.mis_pub_error, "UID vacío")
            return
        }
        // Se guarda la suscripcion para poder soltarla en onDestroy: sin remove()
        // el listener de "mis publicaciones" sobrevivia a la pantalla.
        escuchaInmuebles?.remove()
        escuchaInmuebles = db.collection("propiedades")
            .whereEqualTo("idPropietario", uid)
            .addSnapshotListener { snap, error ->
                cargando = false
                if (error != null) {
                    Log.w("AlkilApp", "MisPublicaciones error uid=$uid: ${error.message}")
                    inmuebles = emptyList()
                    errorTexto = getString(R.string.mis_pub_error, error.message ?: "?")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                errorTexto = null
                inmuebles = snap.documents.mapNotNull { Propiedad.desde(it) }
                    .sortedWith(
                        compareByDescending<Propiedad> { it.estadoNormalizado == "disponible" }
                            .thenBy { it.precio }
                    )
                Log.i("AlkilApp", "MisPublicaciones uid=$uid docs=${snap.size()} mostrando=${inmuebles.size}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        escuchaInmuebles?.remove()
        escuchaInmuebles = null
    }

    private fun abrirDetalle(p: Propiedad) {
        Log.i("AlkilApp", "MisPublicaciones: abriendo detalle id=${p.id} titulo=${p.titulo}")
        Toast.makeText(this, getString(R.string.mis_pub_abriendo, p.titulo), Toast.LENGTH_SHORT).show()
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
            // NO pasar fotos base64 por el intent: supera el limite de Binder
            // (TransactionTooLargeException). El detalle las carga por ID desde Firestore.
            putStringArrayListExtra(
                PropiedadDetalleActivity.EXTRA_FOTOS_URL,
                ArrayList(p.photosUrl)
            )
            putExtra(PropiedadDetalleActivity.EXTRA_FEATURED, p.esDestacado)
            putExtra(PropiedadDetalleActivity.EXTRA_ESTADO, p.estado)
        }.also { startActivity(it) }
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
            sheetDestacarPara = p
        }
    }

    private fun iniciarPagoDestacado(p: Propiedad, dias: Int, precio: Double) {
        val sku = when (dias) {
            7 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_7D
            15 -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_15D
            else -> com.alkilapp.billing.BillingManager.SKU_DESTACAR_30D
        }

        // Primero guardamos la solicitud pendiente en Firestore
        db.collection("propiedades").document(p.id)
            .set(
                mapOf(
                    "solicitudDestacar" to true,
                    "destacadoDias" to dias,
                    "destacadoPrecio" to precio,
                    "destacadoSku" to sku,
                    "solicitudDestacarEn" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ), SetOptions.merge()
            )
            .addOnSuccessListener {
                // Lanzar flujo de compra de Google Play Billing
                billingManager.launchPurchaseFlow(sku, object :
                    com.alkilapp.billing.BillingManager.PurchaseCallback {
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
                                Toast.makeText(
                                    this@MisPublicacionesActivity,
                                    getString(R.string.mis_pub_destacado_ok, dias),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this@MisPublicacionesActivity,
                                    getString(R.string.mis_pub_compra_guardar_error, e.message ?: "?"),
                                    Toast.LENGTH_LONG
                                ).show()
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
                        Toast.makeText(
                            this@MisPublicacionesActivity,
                            getString(R.string.mis_pub_compra_error, message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.mis_pub_solicitud_error, e.message ?: "?"),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun quitarDestacado(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .set(
                mapOf(
                    "isFeatured" to false,
                    "featuredUntil" to null,
                    "destacadoDiasRestantes" to 0,
                    "destacadoEstado" to "retirado",
                    "solicitudDestacar" to false
                ), SetOptions.merge()
            )
            .addOnSuccessListener {
                Toast.makeText(this, R.string.mis_pub_destacado_retirado, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.mis_pub_error_generico, e.message ?: "?"),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun pausarPublicacion(p: Propiedad) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mis_pub_pausar_titulo)
            .setMessage(R.string.mis_pub_pausar_msg)
            .setPositiveButton(R.string.mis_pub_menu_pausar) { _, _ ->
                db.collection("propiedades").document(p.id)
                    .set(mapOf("estado" to "pausada"), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.mis_pub_pausar_ok, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.mis_pub_error_generico, e.message ?: "?"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .show()
    }

    private fun reactivarPublicacion(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .set(mapOf("estado" to "disponible"), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, R.string.mis_pub_reactivar_ok, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.mis_pub_error_generico, e.message ?: "?"),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun marcarComoAlquilado(p: Propiedad) {
        val esVenta = p.esVenta
        MaterialAlertDialogBuilder(this)
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
                            getString(
                                R.string.detalle_finalizar_error,
                                e.localizedMessage ?: "?"
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .show()
    }

    private fun confirmarEliminar(p: Propiedad) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mis_pub_eliminar_titulo)
            .setMessage(getString(R.string.mis_pub_eliminar_msg, p.titulo))
            .setPositiveButton(R.string.mis_pub_menu_eliminar) { _, _ -> eliminarPublicacion(p) }
            .setNegativeButton(R.string.detalle_finalizar_no, null)
            .show()
    }

    private fun eliminarPublicacion(p: Propiedad) {
        db.collection("propiedades").document(p.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, R.string.mis_pub_eliminar_ok, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.mis_pub_error_generico, e.message ?: "?"),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
