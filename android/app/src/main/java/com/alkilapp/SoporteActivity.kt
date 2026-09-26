package com.alkilapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivitySoporteBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SoporteActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoporteBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSoporteBack.setOnClickListener { finish() }
        binding.btnSoporteEnviar.setOnClickListener { enviarSoporte() }

        // Pre-llenar email si hay usuario autenticado
        auth.currentUser?.email?.let { binding.etSoporteEmail.setText(it) }
    }

    private fun enviarSoporte() {
        val asunto = binding.etSoporteAsunto.text.toString().trim()
        val mensaje = binding.etSoporteMensaje.text.toString().trim()
        val email = binding.etSoporteEmail.text.toString().trim()

        if (asunto.isBlank() || mensaje.isBlank() || email.isBlank()) {
            Toast.makeText(this, R.string.soporte_campos_requeridos, Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.soporte_email_invalido, Toast.LENGTH_SHORT).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"
        
        binding.btnSoporteEnviar.isEnabled = false
        binding.btnSoporteEnviar.text = getString(R.string.soporte_enviando)
        binding.progressBar.visibility = View.VISIBLE

        val datos = hashMapOf<String, Any>(
            "usuarioId" to uid,
            "emailContacto" to email,
            "asunto" to asunto,
            "mensaje" to mensaje,
            "estado" to "pendiente",
            "fechaCreacion" to FieldValue.serverTimestamp()
        )

        // Guardar en Firestore
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance("alkilappdb")
        db.collection("soporte")
            .add(datos)
            .addOnSuccessListener { documentReference ->
                Log.d("SoporteActivity", "Ticket creado con ID: ${documentReference.id}")
                
                // Abrir cliente de correo para enviar a alkilapp2026@gmail.com
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:alkilapp2026@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "[Soporte AlkilApp] $asunto")
                    putExtra(Intent.EXTRA_TEXT, "De: $email\n\n$mensaje")
                }
                
                try {
                    startActivity(Intent.createChooser(emailIntent, getString(R.string.soporte_enviar_chooser)))
                    Toast.makeText(this, R.string.soporte_enviado, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.soporte_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                } finally {
                    binding.btnSoporteEnviar.isEnabled = true
                    binding.btnSoporteEnviar.text = getString(R.string.soporte_enviar)
                    binding.progressBar.visibility = View.GONE
                }
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("SoporteActivity", "Error guardando ticket", e)
                Toast.makeText(this, getString(R.string.soporte_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                binding.btnSoporteEnviar.isEnabled = true
                binding.btnSoporteEnviar.text = getString(R.string.soporte_enviar)
                binding.progressBar.visibility = View.GONE
            }
    }
}