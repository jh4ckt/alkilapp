package com.alkilapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivityMiPerfilBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.max
import android.widget.AdapterView

/** Pantalla "Mi perfil": el usuario autenticado edita sus datos personales. */
class MiPerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMiPerfilBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private var fotoBase64: String? = null
    private val valoresTipo = arrayOf("dueno", "inquilino", "ambos")
    private lateinit var departamentos: List<String>

    private val fotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val base64 = comprimirFoto(uri)
        if (base64 == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
        } else {
            fotoBase64 = base64
            mostrarFoto(base64)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMiPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPerfilBack.setOnClickListener { finish() }
        binding.btnPerfilGuardar.setOnClickListener { guardarPerfil() }
        binding.btnPerfilCerrarSesion.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, R.string.auth_sesion_cerrada, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.spPerfilTipo.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.tipos_usuario)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        departamentos = resources.getStringArray(R.array.departamentos_peru).toList()
        binding.spPerfilZonaDepartamento.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            departamentos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spPerfilZonaDepartamento.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val dep = departamentos[position]
                val ciudades = obtenerCiudades(dep)
                val opciones = if (dep == "Lima") {
                    listOf(getString(R.string.prop_distrito_sin)) + ciudades
                } else {
                    listOf(getString(R.string.prop_distrito_sin), "Otro") + ciudades
                }
                binding.spPerfilZonaCiudad.adapter = ArrayAdapter(
                    this@MiPerfilActivity,
                    android.R.layout.simple_spinner_item,
                    opciones
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnPerfilFoto.setOnClickListener {
            fotoLauncher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        cargarMisDatos()
    }

    private fun cargarMisDatos() {
        val u = auth.currentUser ?: run { finish(); return }
        binding.tvPerfilEmail.text = u.email ?: ""
        db.collection("usuarios").document(u.uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                val d = doc.data ?: return@addOnSuccessListener
                binding.tvPerfilNombre.text = (d["nombre"] as? String).orEmpty()
                binding.etPerfilTelefono.setText((d["telefono"] as? String).orEmpty())
                val tipo = (d["tipoUsuario"] as? String) ?: "dueno"
                binding.spPerfilTipo.setSelection(
                    valoresTipo.indexOfFirst { it == tipo }.coerceAtLeast(0)
                )
                val zonaDep = (d["zonaDepartamento"] as? String)
                if (!zonaDep.isNullOrBlank()) {
                    val depIndex = departamentos.indexOfFirst { it == zonaDep }
                    if (depIndex >= 0) {
                        binding.spPerfilZonaDepartamento.setSelection(depIndex)
                    }
                }
                val zonaCiu = (d["zonaCiudad"] as? String)
                if (!zonaCiu.isNullOrBlank()) {
                    // The ciudad spinner will be populated after departamento selection
                    // We need to wait for the adapter to be set, then find the ciudad
                    binding.spPerfilZonaCiudad.post {
                        val adapter = binding.spPerfilZonaCiudad.adapter as? ArrayAdapter<String>
                        adapter?.let { ad ->
                            val ciuIndex = ad.getPosition(zonaCiu!!)
                            if (ciuIndex >= 0) {
                                binding.spPerfilZonaCiudad.setSelection(ciuIndex)
                            }
                        }
                    }
                }
                val b64 = (d["fotoBase64"] as? String)
                if (!b64.isNullOrBlank()) {
                    fotoBase64 = b64
                    mostrarFoto(b64)
                }
            }
    }

    // Función para obtener ciudades por departamento (copiada de RegistrarPropiedadActivity)
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

    private fun mostrarFoto(b64: String) {
        val bytes = try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        binding.ivPerfilFoto.setImageBitmap(bmp)
        binding.ivPerfilFoto.visibility = View.VISIBLE
        binding.tvPerfilInicial.visibility = View.GONE
    }

    private fun guardarPerfil() {
        val u = auth.currentUser ?: run { finish(); return }
        val telefono = binding.etPerfilTelefono.text.toString().trim()
        if (telefono.filter { it.isDigit() }.length < 9) {
            Toast.makeText(this, R.string.auth_telefono_requerido, Toast.LENGTH_SHORT).show()
            return
        }
        val datos = hashMapOf<String, Any>(
            "telefono" to telefono,
            "tipoUsuario" to valoresTipo[binding.spPerfilTipo.selectedItemPosition],
            "zonaDepartamento" to binding.spPerfilZonaDepartamento.selectedItem?.toString().orEmpty(),
            "zonaCiudad" to binding.spPerfilZonaCiudad.selectedItem?.toString().orEmpty()
        )
        fotoBase64?.let { datos["fotoBase64"] = it }
        db.collection("usuarios").document(u.uid)
            .set(datos, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, R.string.perfil_ok, Toast.LENGTH_SHORT).show()
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