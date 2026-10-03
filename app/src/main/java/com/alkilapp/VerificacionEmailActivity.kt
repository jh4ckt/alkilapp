package com.alkilapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivityVerificacionEmailBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Pantalla para verificar el email del usuario.
 * Se muestra tras login/registro si el email no está verificado.
 */
class VerificacionEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificacionEmailBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificacionEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnReenviar.setOnClickListener { reenviarVerificacion() }
        binding.btnAbrirCorreo.setOnClickListener { abrirAppCorreo() }
        binding.btnVerificarLuego.setOnClickListener { finish() }

        // Si ya está verificado (por si recargó), cerrar
        auth.currentUser?.reload()?.addOnSuccessListener {
            if (auth.currentUser?.isEmailVerified == true) {
                Toast.makeText(this, R.string.auth_verificacion_ok, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun reenviarVerificacion() {
        binding.btnReenviar.isEnabled = false
        binding.btnReenviar.text = getString(R.string.auth_enviando)
        auth.currentUser?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                binding.btnReenviar.isEnabled = true
                binding.btnReenviar.text = getString(R.string.auth_reenviar_verificacion)
                if (task.isSuccessful) {
                    Toast.makeText(this, R.string.auth_verificacion_reenviada, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.auth_verificacion_error, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun abrirAppCorreo() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: abrir Gmail
            val gmailIntent = packageManager.getLaunchIntentForPackage("com.google.android.gm")
            gmailIntent?.let { startActivity(it) } ?: Toast.makeText(this, "No hay app de correo instalada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar si el usuario ya validó el email mientras estaba en la app de correo
        auth.currentUser?.reload()?.addOnSuccessListener {
            if (auth.currentUser?.isEmailVerified == true) {
                Toast.makeText(this, R.string.auth_verificacion_ok, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}