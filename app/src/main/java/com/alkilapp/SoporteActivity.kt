package com.alkilapp

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivitySoporteBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Módulo de Soporte e Incidentes.
 * Formulario nativo (sin abrir el cliente de correo externo):
 * crea un ticket en la colección "soporte" de Firestore y la Cloud Function
 * `notificarNuevoTicketSoporte` se encarga de enviar el email a alkilapp2026@gmail.com.
 */
class SoporteActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoporteBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    // IMPORTANTE: base nombrada (el proyecto no tiene (default))
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSoporteBack.setOnClickListener { finish() }
        binding.btnSoporteEnviar.setOnClickListener { enviarTicket() }

        // UID y Email vienen SIEMPRE de FirebaseAuth (no los escribe el usuario)
        val usuario = auth.currentUser
        if (usuario?.email.isNullOrBlank()) {
            // Sin sesión no hay cómo identificar el ticket
            Toast.makeText(this, R.string.soporte_requiere_sesion, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        binding.etSoporteEmail.setText(usuario?.email)
        binding.etSoporteEmail.isEnabled = false
    }

    private fun enviarTicket() {
        val usuario = auth.currentUser
        val uid = usuario?.uid
        val email = usuario?.email

        if (uid.isNullOrBlank() || email.isNullOrBlank()) {
            Toast.makeText(this, R.string.soporte_requiere_sesion, Toast.LENGTH_LONG).show()
            return
        }

        val asunto = binding.etSoporteAsunto.text.toString().trim()
        val mensaje = binding.etSoporteMensaje.text.toString().trim()

        // Validaciones de campos
        if (asunto.isBlank() || mensaje.isBlank()) {
            Toast.makeText(this, R.string.soporte_campos_requeridos, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.soporte_email_invalido, Toast.LENGTH_SHORT).show()
            return
        }
        if (asunto.length > 150) {
            Toast.makeText(this, R.string.soporte_asunto_largo, Toast.LENGTH_SHORT).show()
            return
        }
        if (mensaje.length > 4000) {
            Toast.makeText(this, R.string.soporte_mensaje_largo, Toast.LENGTH_SHORT).show()
            return
        }

        // Estado de carga: botón deshabilitado + ProgressBar visible
        binding.btnSoporteEnviar.isEnabled = false
        binding.btnSoporteEnviar.text = getString(R.string.soporte_enviando)
        binding.progressBarSoporte.visibility = View.VISIBLE

        // Esquema del ticket (colección "soporte")
        val ticket = hashMapOf<String, Any>(
            "usuarioId" to uid,
            "emailContacto" to email,
            "asunto" to asunto,
            "mensaje" to mensaje,
            "estado" to "pendiente", // pendiente | en_proceso | resuelto
            "fechaCreacion" to FieldValue.serverTimestamp()
        )

        db.collection("soporte")
            .add(ticket)
            .addOnSuccessListener { ref ->
                Log.i(TAG, "Ticket de soporte creado: ${ref.id}")
                Toast.makeText(this, R.string.soporte_enviado, Toast.LENGTH_SHORT).show()
                restaurarBoton()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creando ticket de soporte", e)
                Toast.makeText(
                    this,
                    getString(R.string.soporte_error, e.localizedMessage ?: "desconocido"),
                    Toast.LENGTH_LONG
                ).show()
                restaurarBoton()
            }
    }

    private fun restaurarBoton() {
        binding.progressBarSoporte.visibility = View.GONE
        binding.btnSoporteEnviar.isEnabled = true
        binding.btnSoporteEnviar.text = getString(R.string.soporte_enviar)
    }

    companion object {
        private const val TAG = "SoporteActivity"
    }
}
