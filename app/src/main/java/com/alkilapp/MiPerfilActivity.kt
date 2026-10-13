package com.alkilapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.MiPerfilUiState
import com.alkilapp.compose.PantallaMiPerfil
import com.alkilapp.data.Favoritos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.max

/** Pantalla "Mi perfil": el usuario autenticado edita sus datos personales. */
class MiPerfilActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private var fotoBase64: String? = null
    private val valoresTipo = arrayOf("dueno", "inquilino", "ambos")
    private lateinit var departamentos: List<String>
    private lateinit var etiquetasTipo: List<String>

    private var ui by mutableStateOf(MiPerfilUiState())
    private var ciudades by mutableStateOf<List<String>>(emptyList())

    private var telefonoOriginal = ""
    private var tipoOriginal = 0
    private var zonaDepOriginal = ""
    private var zonaCiuOriginal = ""

    private val fotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val base64 = comprimirFoto(uri)
        if (base64 == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
        } else {
            fotoBase64 = base64
            ui = ui.copy(foto = decodificar(base64))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlkilTheme {
                PantallaMiPerfil(
                    ui = ui,
                    opcionesTipo = valoresTipo.toList(),
                    departamentos = departamentos,
                    ciudades = ciudades,
                    etiquetaTipo = ::etiquetaTipo,
                    onTelefono = { ui = ui.copy(telefono = it) },
                    onTipo = { ui = ui.copy(tipo = it) },
                    onDepartamento = { dep ->
                        zonaCiuSeleccionada("")
                        ui = ui.copy(zonaDep = dep, zonaCiudad = "")
                        actualizarCiudades(dep)
                    },
                    onCiudad = { ciudad -> ui = ui.copy(zonaCiudad = ciudad) },
                    onEditar = { activarModoEdicion() },
                    onGuardar = { guardarPerfil() },
                    onCancelar = { cancelarEdicion() },
                    onFoto = {
                        fotoLauncher.launch(
                            PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                .build()
                        )
                    },
                    onCerrarSesion = {
                        auth.signOut()
                        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                            this,
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                        ).signOut()
                        Favoritos.limpiarLocales(this)
                        Toast.makeText(this, R.string.auth_sesion_cerrada, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onAtras = { finish() }
                )
            }
        }

        etiquetasTipo = resources.getStringArray(R.array.tipos_usuario).toList()
        departamentos = resources.getStringArray(R.array.departamentos_peru).toList()
        ui = ui.copy(zonaDep = departamentos.first(), zonaCiudad = "")
        actualizarCiudades(departamentos.first())
        cargarMisDatos()
    }

    private fun etiquetaTipo(t: String): String =
        etiquetasTipo[valoresTipo.indexOf(t).coerceAtLeast(0)]

    private fun actualizarCiudades(dep: String) {
        val base = obtenerCiudades(dep)
        ciudades = if (dep == "Lima") {
            listOf(getString(R.string.prop_distrito_sin)) + base
        } else {
            listOf(getString(R.string.prop_distrito_sin), "Otro") + base
        }
    }

    private fun zonaCiuSeleccionada(valor: String) {
        ui = ui.copy(zonaCiudad = valor)
    }

    private fun activarModoEdicion() {
        telefonoOriginal = ui.telefono
        tipoOriginal = valoresTipo.indexOf(ui.tipo).coerceAtLeast(0)
        zonaDepOriginal = ui.zonaDep
        zonaCiuOriginal = ui.zonaCiudad
        ui = ui.copy(enEdicion = true)
    }

    private fun cancelarEdicion() {
        ui = ui.copy(
            telefono = telefonoOriginal,
            tipo = valoresTipo[tipoOriginal],
            zonaDep = zonaDepOriginal,
            zonaCiudad = zonaCiuOriginal,
            enEdicion = false
        )
        actualizarCiudades(zonaDepOriginal)
    }

    private fun cargarMisDatos() {
        val u = auth.currentUser ?: run { finish(); return }
        ui = ui.copy(email = u.email ?: "", emailVerificado = u.isEmailVerified)
        db.collection("usuarios").document(u.uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                val d = doc.data ?: return@addOnSuccessListener
                val tipo = (d["tipoUsuario"] as? String) ?: "dueno"
                val zonaDep = (d["zonaDepartamento"] as? String)
                val zonaCiu = (d["zonaCiudad"] as? String)
                val depUsar = if (!zonaDep.isNullOrBlank() && departamentos.contains(zonaDep)) zonaDep
                else ui.zonaDep
                actualizarCiudades(depUsar)
                ui = ui.copy(
                    nombre = (d["nombre"] as? String).orEmpty(),
                    telefono = (d["telefono"] as? String).orEmpty(),
                    tipo = tipo,
                    zonaDep = depUsar,
                    zonaCiudad = zonaCiu.orEmpty().takeIf { ciudades.contains(it) }
                        ?: ui.zonaCiudad
                )
                val b64 = (d["fotoBase64"] as? String)
                if (!b64.isNullOrBlank()) {
                    fotoBase64 = b64
                    ui = ui.copy(foto = decodificar(b64))
                }
            }
    }

    private fun obtenerCiudades(departamento: String): List<String> {
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
            "Lambayeque" -> resources.getStringArray(R.array.ciudades_lambayeque).toList()
            "La Libertad" -> resources.getStringArray(R.array.ciudades_lalibertad).toList()
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

    private fun decodificar(b64: String): Bitmap? {
        val bytes = try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun guardarPerfil() {
        val u = auth.currentUser ?: run { finish(); return }
        val telefono = ui.telefono.trim()
        if (telefono.filter { it.isDigit() }.length < 9) {
            Toast.makeText(this, R.string.auth_telefono_requerido, Toast.LENGTH_SHORT).show()
            return
        }
        val datos = hashMapOf<String, Any>(
            "telefono" to telefono,
            "tipoUsuario" to ui.tipo,
            "zonaDepartamento" to ui.zonaDep,
            "zonaCiudad" to ui.zonaCiudad
        )
        fotoBase64?.let { datos["fotoBase64"] = it }
        db.collection("usuarios").document(u.uid)
            .set(datos, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, R.string.perfil_ok, Toast.LENGTH_SHORT).show()
                ui = ui.copy(enEdicion = false)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.perfil_error, e.localizedMessage ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /** Comprime la foto elegida a base64 (max ~500px, JPEG q70) para el avatar. */
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
        val maxLado = 500.0f
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
        escalado.compress(Bitmap.CompressFormat.JPEG, 70, bytes)
        if (escalado !== bmp) bmp.recycle()
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
