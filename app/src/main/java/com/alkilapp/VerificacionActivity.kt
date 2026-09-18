package com.alkilapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.alkilapp.databinding.ActivityVerificacionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.max

/**
 * Verificacion de identidad: el usuario sube una foto de su documento.
 * El documento va a "verificaciones/{uid}" (lo revisa el panel admin) y el
 * estado resumido a "usuarios/{uid}.verification".
 */
class VerificacionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificacionBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private val tipos = arrayOf("DNI", "Carnet de extranjeria", "Pasaporte")
    private var documentoBase64: String? = null
    private var yaVerificado = false

    private val fotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val base64 = comprimirDocumento(uri)
        if (base64 == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
        } else {
            documentoBase64 = base64
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                binding.ivVerifDoc.setImageBitmap(bmp)
                binding.ivVerifDoc.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVerifBack.setOnClickListener { finish() }
        binding.btnVerifFoto.setOnClickListener { elegirFoto() }
        binding.btnVerifEnviar.setOnClickListener { enviar() }

        binding.spVerifTipo.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            tipos
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        cargarEstado()
    }

    private fun elegirFoto() {
        fotoLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    /** Lee el estado actual de verificacion del usuario. */
    private fun cargarEstado() {
        val u = auth.currentUser
        if (u == null) {
            Toast.makeText(this, R.string.verif_sesion_requerida, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        db.collection("usuarios").document(u.uid).get()
            .addOnSuccessListener { doc ->
                val v = doc.get("verification") as? Map<*, *> ?: return@addOnSuccessListener
                val verificado = v["identityVerified"] == true
                val estado = (v["status"] as? String).orEmpty()
                when {
                    verificado -> {
                        yaVerificado = true
                        binding.tvVerifEstado.setText(R.string.verif_estado_verificado)
                        binding.tvVerifEstado.visibility = View.VISIBLE
                        bloquearEnvio(true)
                    }
                    estado == "pendiente" -> {
                        binding.tvVerifEstado.setText(R.string.verif_estado_pendiente)
                        binding.tvVerifEstado.visibility = View.VISIBLE
                    }
                    estado == "rechazado" -> {
                        val motivo = (v["motivo"] as? String).orEmpty()
                        binding.tvVerifEstado.text =
                            getString(R.string.verif_estado_rechazado, motivo.ifBlank { "-" })
                        binding.tvVerifEstado.visibility = View.VISIBLE
                    }
                    else -> binding.tvVerifEstado.visibility = View.GONE
                }
                (v["tipoDocumento"] as? String)?.let { tipo ->
                    val idx = tipos.indexOfFirst { it == tipo }
                    if (idx >= 0) binding.spVerifTipo.setSelection(idx)
                }
                (v["numeroDocumento"] as? String)?.let { binding.etVerifNumero.setText(it) }
            }
    }

    private fun enviar() {
        val u = auth.currentUser ?: run {
            Toast.makeText(this, R.string.verif_sesion_requerida, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (yaVerificado) return
        val numero = binding.etVerifNumero.text.toString().trim()
        if (numero.isBlank()) {
            Toast.makeText(this, R.string.verif_numero_requerido, Toast.LENGTH_SHORT).show()
            return
        }
        val doc = documentoBase64
        if (doc.isNullOrBlank()) {
            Toast.makeText(this, R.string.verif_foto_falta, Toast.LENGTH_SHORT).show()
            return
        }
        val tipo = tipos[binding.spVerifTipo.selectedItemPosition]

        bloquearEnvio(true)

        val resumen = mapOf(
            "status" to "pendiente",
            "documentoPendiente" to true,
            "tipoDocumento" to tipo,
            "numeroDocumento" to numero,
            "enviadoEn" to FieldValue.serverTimestamp()
        )
        val detalle = hashMapOf<String, Any>(
            "uid" to u.uid,
            "nombre" to (u.displayName ?: ""),
            "email" to (u.email ?: ""),
            "tipoDocumento" to tipo,
            "numeroDocumento" to numero,
            "imagen" to doc,
            "estado" to "pendiente",
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("verificaciones").document(u.uid).set(detalle)
            .addOnSuccessListener {
                db.collection("usuarios").document(u.uid)
                    .set(mapOf("verification" to resumen), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.verif_ok, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener { e -> fallo(e.localizedMessage) }
            }
            .addOnFailureListener { e -> fallo(e.localizedMessage) }
    }

    private fun fallo(mensaje: String?) {
        bloquearEnvio(false)
        Toast.makeText(
            this,
            getString(R.string.verif_error, mensaje ?: "?"),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun bloquearEnvio(bloqueado: Boolean) {
        binding.btnVerifEnviar.isEnabled = !bloqueado
        binding.btnVerifFoto.isEnabled = !bloqueado
        binding.btnVerifEnviar.text = if (bloqueado) {
            getString(R.string.auth_procesando)
        } else {
            getString(R.string.verif_enviar)
        }
    }

    /** Comprime el documento a base64 (max ~1000px, JPEG q75). */
    private fun comprimirDocumento(uri: Uri): String? {
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
        val maxLado = 1000.0f
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
        escalado.compress(Bitmap.CompressFormat.JPEG, 75, bytes)
        if (escalado !== bmp) bmp.recycle()
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
