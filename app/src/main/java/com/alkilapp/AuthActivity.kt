package com.alkilapp

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivityAuthBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Pantalla de autenticacion (Ingresar / Crear cuenta / Google).
 * Reemplaza el antiguo dialogo de auth de MainActivity.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private var modoRegistro = false

    private val googleSignInClient by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        val webClientId = getString(R.string.default_web_client_id)
        if (webClientId.isNotBlank()) builder.requestIdToken(webClientId)
        builder.requestEmail()
            .build()
            .let { GoogleSignIn.getClient(this, it) }
    }

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val cuenta = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = cuenta.idToken
            if (idToken == null) {
                mostrarError(getString(R.string.auth_google_error, "sin token"))
                return@registerForActivityResult
            }
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        guardarUsuarioEnBase()
                        Toast.makeText(this, R.string.auth_ok_google, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        mostrarError(getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"))
                    }
                }
        } catch (e: ApiException) {
            mostrarError(getString(R.string.auth_google_error, "${e.statusCode}"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tgAuthModo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            aplicarModo(checkedId == R.id.btnTabCrear)
        }

        binding.btnAuthAccion.setOnClickListener { enviarFormulario() }
        binding.btnAuthGoogle.setOnClickListener { iniciarSesionGoogle() }
        binding.btnAuthOlvide.setOnClickListener { enviarReset() }

        val departamentosRegistro = resources.getStringArray(R.array.departamentos_peru).toList()
        val opcionesDepartamento = listOf(getString(R.string.auth_departamento_selecciona)) + departamentosRegistro
        binding.spAuthDepartamento.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            opcionesDepartamento
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        aplicarModo(false)
    }

    /** Alterna el formulario entre modo "Ingresar" y "Crear cuenta". */
    private fun aplicarModo(registro: Boolean) {
        modoRegistro = registro
        binding.tilAuthNombre.visibility = if (registro) View.VISIBLE else View.GONE
        binding.tilAuthTelefono.visibility = if (registro) View.VISIBLE else View.GONE
        binding.tilAuthConfirmar.visibility = if (registro) View.VISIBLE else View.GONE
        binding.tvAuthDepartamentoLabel.visibility = if (registro) View.VISIBLE else View.GONE
        binding.spAuthDepartamento.visibility = if (registro) View.VISIBLE else View.GONE
        binding.btnAuthAccion.setText(if (registro) R.string.auth_crear_cuenta else R.string.auth_ingresar)
        ocultarError()
    }

    private fun enviarFormulario() {
        val email = binding.etAuthEmail.text.toString().trim()
        val pass = binding.etAuthPassword.text.toString()
        if (email.isBlank() || !email.contains("@")) {
            mostrarError(getString(R.string.auth_email_invalido))
            return
        }
        if (pass.length < 6) {
            mostrarError(getString(R.string.auth_password_corta))
            return
        }
        if (modoRegistro) {
            val nombre = binding.etAuthNombre.text.toString().trim()
            if (nombre.isBlank()) {
                mostrarError(getString(R.string.auth_nombre_requerido))
                return
            }
            val telefono = binding.etAuthTelefono.text.toString().trim()
            if (telefono.filter { it.isDigit() }.length < 9) {
                mostrarError(getString(R.string.auth_telefono_requerido))
                return
            }
            val departamento = binding.spAuthDepartamento.selectedItem as? String
            if (departamento == null || departamento == getString(R.string.auth_departamento_selecciona)) {
                mostrarError(getString(R.string.auth_departamento_requerido))
                return
            }
            if (binding.etAuthConfirmar.text.toString() != pass) {
                mostrarError(getString(R.string.auth_pass_no_coincide))
                return
            }
            crearCuenta(email, pass, nombre, telefono, departamento)
        } else {
            iniciarSesion(email, pass)
        }
    }

    private fun iniciarSesion(email: String, pass: String) {
        ocultarError()
        bloquear(true)
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                bloquear(false)
                if (task.isSuccessful) {
                    guardarUsuarioEnBase()
                    Toast.makeText(this, R.string.auth_ok_login, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    mostrarError(getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"))
                }
            }
    }

    private fun crearCuenta(
        email: String,
        pass: String,
        nombre: String,
        telefono: String,
        departamento: String? = null
    ) {
        ocultarError()
        bloquear(true)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    bloquear(false)
                    mostrarError(getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"))
                    return@addOnCompleteListener
                }
                val perfil = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(nombre)
                    .build()
                task.result?.user?.updateProfile(perfil)
                guardarUsuarioEnBase(nombre, telefono, departamento)
                bloquear(false)
                Toast.makeText(this, R.string.auth_ok_registro, Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun enviarReset() {
        val email = binding.etAuthEmail.text.toString().trim()
        if (email.isBlank() || !email.contains("@")) {
            mostrarError(getString(R.string.auth_reset_falta_email))
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, R.string.auth_reset_enviado, Toast.LENGTH_LONG).show()
                } else {
                    mostrarError(getString(R.string.auth_error, task.exception?.localizedMessage ?: "?"))
                }
            }
    }

    private fun iniciarSesionGoogle() {
        if (getString(R.string.default_web_client_id).isBlank()) {
            Toast.makeText(this, R.string.auth_google_no_config, Toast.LENGTH_LONG).show()
            return
        }
        googleLauncher.launch(googleSignInClient.signInIntent)
    }

    /** Crea/actualiza el documento del usuario sin pisar datos ya existentes. */
    private fun guardarUsuarioEnBase(nombreNuevo: String? = null, telefonoNuevo: String? = null, departamentoNuevo: String? = null) {
        val u = auth.currentUser ?: return
        val ref = db.collection("usuarios").document(u.uid)
        val base = hashMapOf(
            "nombre" to (
                nombreNuevo
                    ?: u.displayName
                    ?: u.email?.substringBefore("@")
                    ?: ""
                ),
            "email" to (u.email ?: ""),
            "uidAuth" to u.uid,
            "activo" to true
        )
        ref.get()
            .addOnSuccessListener { doc ->
                val datos = HashMap<String, Any>(base)
                if (doc.exists()) {
                    val nombreActual = (doc.data?.get("nombre") as? String).orEmpty()
                    // Si ya tenia nombre propio (o se acaba de escribir uno), no lo pisamos.
                    if (nombreActual.isNotBlank() && nombreNuevo == null) datos.remove("nombre")
                    // El celular se setea en el registro; no se pisa con un login posterior.
                    if (telefonoNuevo != null) datos["telefono"] = telefonoNuevo
                } else {
                    datos["telefono"] = telefonoNuevo ?: ""
                    datos["zonaDepartamento"] = departamentoNuevo ?: ""
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

    private fun mostrarError(mensaje: String) {
        binding.tvAuthError.text = mensaje
        binding.tvAuthError.visibility = View.VISIBLE
    }

    private fun ocultarError() {
        binding.tvAuthError.visibility = View.GONE
    }

    private fun bloquear(bloqueado: Boolean) {
        binding.btnAuthAccion.isEnabled = !bloqueado
        binding.btnAuthGoogle.isEnabled = !bloqueado
        binding.btnAuthAccion.text = if (bloqueado) {
            getString(R.string.auth_procesando)
        } else {
            getString(if (modoRegistro) R.string.auth_crear_cuenta else R.string.auth_ingresar)
        }
        if (bloqueado) {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }
}
