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
 * NO se cierra hasta que el email esté verificado.
 * Reaparece periódicamente si el usuario la cierra sin verificar.
 */
class VerificacionEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificacionEmailBinding
    private val auth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val CHECK_INTERVAL_MS = 30000L // 30 segundos
    private var checkRunnable: Runnable? = null
    private var userDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificacionEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnReenviar.setOnClickListener { reenviarVerificacion() }
        binding.btnAbrirCorreo.setOnClickListener { abrirAppCorreo() }
        binding.btnVerificarLuego.setOnClickListener { 
            // Solo oculta temporalmente; reaparecerá en CHECK_INTERVAL_MS
            userDismissed = true
            moverATareaSegundoPlano()
        }

        // Verificación inicial
        verificarEstado()
        iniciarChequeoPeriodico()
    }

    override fun onResume() {
        super.onResume()
        userDismissed = false
        verificarEstado()
    }

    override fun onPause() {
        super.onPause()
        if (userDismissed) {
            // Si el usuario la cerró, reprogramar reapertura
            handler.postDelayed({ 
                if (!isFinishing && !isDestroyed && auth.currentUser?.isEmailVerified != true) {
                    // La actividad sigue en stack, traerla al frente
                    val intent = Intent(this, VerificacionEmailActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                }
            }, CHECK_INTERVAL_MS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun verificarEstado() {
        auth.currentUser?.reload()?.addOnSuccessListener {
            if (auth.currentUser?.isEmailVerified == true) {
                Toast.makeText(this, R.string.auth_verificacion_ok, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun iniciarChequeoPeriodico() {
        checkRunnable = Runnable { 
            if (!isFinishing && !isDestroyed && auth.currentUser?.isEmailVerified != true) {
                verificarEstado()
                handler.postDelayed(checkRunnable!!, CHECK_INTERVAL_MS)
            }
        }
        handler.postDelayed(checkRunnable!!, CHECK_INTERVAL_MS)
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

    private fun moverATareaSegundoPlano() {
        moveTaskToBack(true)
    }
}