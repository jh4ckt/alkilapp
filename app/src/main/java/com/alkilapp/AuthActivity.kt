package com.alkilapp

import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.Patterns
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Pantalla de autenticación (Ingresar / Crear cuenta / Google).
 * Reemplaza el antiguo diálogo de auth de MainActivity.
 */
class AuthActivity : AppCompatActivity(), CoroutineScope {

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
                        guardarUsuarioEnBase { finish() }
                        Toast.makeText(this, R.string.auth_ok_google, Toast.LENGTH_SHORT).show()
                    } else {
                        mostrarErrorFirebase(task.exception)
                    }
                }
} catch (e: ApiException) {
            mostrarError(getString(R.string.auth_google_error, "${e.statusCode}"))
        }
    }

    override val coroutineContext = Dispatchers.Main

    /** Verifica que el dominio del email tenga registros MX (puede recibir correo).
     * Usa Google DNS-over-HTTPS (gratis, sin API key). */
    private suspend fun verificarDominioMX(email: String): Boolean = withContext(Dispatchers.IO) {
        val dominio = email.substringAfterLast("@")
        try {
            val url = URL("https://dns.google/resolve?name=$dominio&type=MX")
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val inputStream = connection.getInputStream()
            val json = inputStream.bufferedReader().readText()
            inputStream.close()
            val obj = JSONObject(json)
            val answer = obj.optJSONArray("Answer")
            answer != null && answer.length() > 0
        } catch (e: Exception) {
            // Si falla la consulta DNS, permitimos continuar (no bloquear al usuario)
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modoRegistro = savedInstanceState?.getBoolean(ESTADO_MODO_REGISTRO) ?: false
        binding.tgAuthModo.check(if (modoRegistro) R.id.btnTabCrear else R.id.btnTabIngresar)

        binding.tgAuthModo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            aplicarModo(checkedId == R.id.btnTabCrear)
        }

        binding.btnAuthAccion.setOnClickListener { enviarFormulario() }
        binding.btnAuthGoogle.setOnClickListener { iniciarSesionGoogle() }
        binding.btnAuthOlvide.setOnClickListener { enviarReset() }

        // Celular: solo dígitos, máximo 9
        binding.etAuthTelefono.filters = arrayOf(
            InputFilter.LengthFilter(9),
            SoloDigitosFilter()
        )

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
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ESTADO_MODO_REGISTRO, modoRegistro)
    }

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

        // Validación email
        if (email.isBlank()) {
            mostrarError(getString(R.string.auth_email_vacio))
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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
            // Celular: exactamente 9 dígitos
            val telefono = binding.etAuthTelefono.text.toString().trim()
            if (telefono.length != 9) {
                mostrarError(getString(R.string.auth_telefono_9_digitos))
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
            // Check de dominio MX (opcional, no bloqueante si falla)
            launch {
                val mxOk = verificarDominioMX(email)
                if (!mxOk) {
                    runOnUiThread { mostrarError(getString(R.string.auth_dominio_invalido)) }
                    return@launch
                }
                // Verificar email duplicado ANTES de crear (evita excepción fea)
                runOnUiThread {
                    verificarEmailDisponible(email) { disponible ->
                        if (disponible) {
                            crearCuenta(email, pass, nombre, telefono, departamento)
                        } else {
                            mostrarError(getString(R.string.auth_email_duplicado))
                        }
                    }
                }
            }
            return
        } else {
            iniciarSesion(email, pass)
        }
    }

    /** Verifica en Firestore si el email ya está registrado. */
    private fun verificarEmailDisponible(email: String, callback: (Boolean) -> Unit) {
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.isEmpty())
            }
            .addOnFailureListener {
                // Si falla la consulta, permitimos intentar crear (Firebase dirá si duplicado)
                callback(true)
            }
    }

    private fun iniciarSesion(email: String, pass: String) {
        ocultarError()
        bloquear(true)
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                bloquear(false)
                if (task.isSuccessful) {
                    guardarUsuarioEnBase { finish() }
                    Toast.makeText(this, R.string.auth_ok_login, Toast.LENGTH_SHORT).show()
                } else {
                    mostrarErrorFirebase(task.exception)
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
                    mostrarErrorFirebase(task.exception)
                    return@addOnCompleteListener
                }
                val user = task.result?.user
                val perfil = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(nombre)
                    .build()
                user?.updateProfile(perfil)
                // Enviar verificación de email
                user?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        Toast.makeText(this, R.string.auth_verificacion_enviada, Toast.LENGTH_LONG).show()
                    }
                    ?.addOnFailureListener {
                        Toast.makeText(this, R.string.auth_verificacion_error, Toast.LENGTH_LONG).show()
                    }
                guardarUsuarioEnBase(nombre, telefono, departamento) {
                    Toast.makeText(this, R.string.auth_ok_registro, Toast.LENGTH_SHORT).show()
                    finish()
                }
                bloquear(false)
            }
    }

    private fun enviarReset() {
        val email = binding.etAuthEmail.text.toString().trim()
        if (email.isBlank()) {
            mostrarError(getString(R.string.auth_reset_falta_email))
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mostrarError(getString(R.string.auth_email_invalido))
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, R.string.auth_reset_enviado, Toast.LENGTH_LONG).show()
                } else {
                    mostrarErrorFirebase(task.exception)
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
    private fun guardarUsuarioEnBase(
        nombreNuevo: String? = null,
        telefonoNuevo: String? = null,
        departamentoNuevo: String? = null,
        alTerminar: (() -> Unit)? = null
    ) {
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
                    if (nombreActual.isNotBlank() && nombreNuevo == null) datos.remove("nombre")
                    if (telefonoNuevo != null) datos["telefono"] = telefonoNuevo
                } else {
                    datos["telefono"] = telefonoNuevo ?: ""
                    datos["zonaDepartamento"] = departamentoNuevo ?: ""
                    datos["tipoUsuario"] = "dueno"
                    datos["fechaRegistro"] = FieldValue.serverTimestamp()
                }
                ref.set(datos, SetOptions.merge())
                    .addOnCompleteListener { alTerminar?.invoke() }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.auth_usuario_guardado_error, e.localizedMessage ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
                alTerminar?.invoke()
            }
    }

    /** Convierte excepciones de Firebase a mensajes en español amigables. */
    private fun mostrarErrorFirebase(e: Exception?) {
        val msg = when {
            e == null -> getString(R.string.auth_error_desconocido)
            e.message?.contains("EMAIL_EXISTS") == true -> getString(R.string.auth_email_duplicado)
            e.message?.contains("INVALID_EMAIL") == true -> getString(R.string.auth_email_invalido)
            e.message?.contains("WEAK_PASSWORD") == true -> getString(R.string.auth_password_corta)
            e.message?.contains("USER_NOT_FOUND") == true -> getString(R.string.auth_usuario_no_encontrado)
            e.message?.contains("WRONG_PASSWORD") == true -> getString(R.string.auth_password_incorrecta)
            e.message?.contains("TOO_MANY_REQUESTS") == true -> getString(R.string.auth_demasiados_intentos)
            e.message?.contains("NETWORK_ERROR") == true -> getString(R.string.auth_error_red)
            else -> getString(R.string.auth_error, e.localizedMessage ?: "?")
        }
        mostrarError(msg)
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

    /** Filtro que solo permite dígitos (0-9). */
    private class SoloDigitosFilter : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            return if (source.all { it.isDigit() }) null else ""
        }
    }

    private companion object {
        const val ESTADO_MODO_REGISTRO = "estado_modo_registro"
    }
}
