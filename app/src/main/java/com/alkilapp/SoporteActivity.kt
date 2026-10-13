package com.alkilapp

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.PantallaSoporte
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

    private val auth by lazy { FirebaseAuth.getInstance() }
    // IMPORTANTE: base nombrada (el proyecto no tiene (default))
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private var email by mutableStateOf("")
    private var asunto by mutableStateOf("")
    private var mensaje by mutableStateOf("")
    private var enviando by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlkilTheme {
                PantallaSoporte(
                    email = email,
                    asunto = asunto,
                    mensaje = mensaje,
                    enviando = enviando,
                    onAsunto = { asunto = it },
                    onMensaje = { mensaje = it },
                    onEnviar = { enviarTicket() },
                    onAtras = { finish() }
                )
            }
        }

        // UID y Email vienen SIEMPRE de FirebaseAuth (no los escribe el usuario)
        val usuario = auth.currentUser
        if (usuario?.email.isNullOrBlank()) {
            // Sin sesión no hay cómo identificar el ticket
            Toast.makeText(this, R.string.soporte_requiere_sesion, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        email = usuario?.email.orEmpty()
    }

    private fun enviarTicket() {
        val usuario = auth.currentUser
        val uid = usuario?.uid
        val correo = usuario?.email

        if (uid.isNullOrBlank() || correo.isNullOrBlank()) {
            Toast.makeText(this, R.string.soporte_requiere_sesion, Toast.LENGTH_LONG).show()
            return
        }

        val asuntoTxt = asunto.trim()
        val mensajeTxt = mensaje.trim()

        // Validaciones de campos
        if (asuntoTxt.isBlank() || mensajeTxt.isBlank()) {
            Toast.makeText(this, R.string.soporte_campos_requeridos, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            Toast.makeText(this, R.string.soporte_email_invalido, Toast.LENGTH_SHORT).show()
            return
        }
        if (asuntoTxt.length > 150) {
            Toast.makeText(this, R.string.soporte_asunto_largo, Toast.LENGTH_SHORT).show()
            return
        }
        if (mensajeTxt.length > 4000) {
            Toast.makeText(this, R.string.soporte_mensaje_largo, Toast.LENGTH_SHORT).show()
            return
        }

        enviando = true

        // Esquema del ticket (colección "soporte")
        val ticket = hashMapOf<String, Any>(
            "usuarioId" to uid,
            "emailContacto" to correo,
            "asunto" to asuntoTxt,
            "mensaje" to mensajeTxt,
            "estado" to "pendiente", // pendiente | en_proceso | resuelto
            "fechaCreacion" to FieldValue.serverTimestamp()
        )

        db.collection("soporte")
            .add(ticket)
            .addOnSuccessListener { ref ->
                Log.i(TAG, "Ticket de soporte creado: ${ref.id}")
                Toast.makeText(this, R.string.soporte_enviado, Toast.LENGTH_SHORT).show()
                enviando = false
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creando ticket de soporte", e)
                Toast.makeText(
                    this,
                    getString(R.string.soporte_error, e.localizedMessage ?: "desconocido"),
                    Toast.LENGTH_LONG
                ).show()
                enviando = false
            }
    }

    companion object {
        private const val TAG = "SoporteActivity"
    }
}
