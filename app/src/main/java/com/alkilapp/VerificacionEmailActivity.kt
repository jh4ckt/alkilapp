package com.alkilapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivityVerificacionEmailBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Pantalla para verificar el email del usuario.
 * Se muestra cuando:
 * - App inicia y email no verificado
 * - Usuario intenta publicar/chatear sin verificar
 * NO se cierra permanentemente hasta que el email esté verificado.
 */
class VerificacionEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificacionEmailBinding
    private val auth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    // Sondeo en segundo plano (MAX_RETRIES * CADA_MS ~= 2.5 min): si el usuario
    // verifica en el navegador y vuelve, la pantalla avanza sola.
    private var reintentos = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificacionEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnReenviar.setOnClickListener { reenviarVerificacion() }
        binding.btnAbrirCorreo.setOnClickListener { abrirAppCorreo() }
        binding.btnVerificarLuego.setOnClickListener { finish() }

        // Verificación inicial
        verificarEstado()
    }

    override fun onResume() {
        super.onResume()
        // Verificar si el usuario ya validó el email mientras estaba en la app de correo
        verificarEstado()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun verificarEstado() {
        val user = auth.currentUser ?: return
        user.reload()
            .addOnSuccessListener {
                if (user.isEmailVerified) {
                    handler.removeCallbacksAndMessages(null)
                    Toast.makeText(this, R.string.auth_verificacion_ok, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    reprogramarSondeo()
                }
            }
            .addOnFailureListener {
                // Reload falló (red/backoff): no dejar la pantalla muda, reintentar.
                reprogramarSondeo()
            }
    }

    /** Reintenta el sondeo hasta un máximo, con retraso creciente. */
    private fun reprogramarSondeo() {
        if (reintentos >= 25) return
        reintentos++
        handler.postDelayed({ verificarEstado() }, 6000L)
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
            val gmailIntent = packageManager.getLaunchIntentForPackage("com.google.android.gm")
            gmailIntent?.let { startActivity(it) } ?: Toast.makeText(this, "No hay app de correo instalada", Toast.LENGTH_SHORT).show()
        }
    }
}