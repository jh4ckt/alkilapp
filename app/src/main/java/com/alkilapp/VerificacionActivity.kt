package com.alkilapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alkilapp.compose.AlkilTheme
import com.alkilapp.compose.PantallaVerificacion
import com.alkilapp.compose.VerifUiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.max

/**
 * Verificacion de identidad: el usuario sube una foto de su documento.
 * El documento va a "verificaciones/{uid}" (lo revisa el panel admin) y el
 * estado resumido a "usuarios/{uid}.verification". UI en Compose.
 */
class VerificacionActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private val tipos = arrayOf("DNI", "Carnet de extranjeria", "Pasaporte")
    private var documentoBase64: String? = null
    private var documentoReversoBase64: String? = null
    private var yaVerificado = false
    private var yaPendiente = false
    private val DNI_RE = Regex("^[0-9]{8}$")

    private var ui by mutableStateOf(VerifUiState(textoBoton = ""))

    private val fotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val base64 = comprimirDocumento(uri)
        if (base64 == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
        } else {
            documentoBase64 = base64
            ui = ui.copy(fotoFrente = decodificar(base64))
        }
    }

    private val fotoReversoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val base64 = comprimirDocumento(uri)
        if (base64 == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
        } else {
            documentoReversoBase64 = base64
            ui = ui.copy(fotoReverso = decodificar(base64))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ui.copy(textoBoton = getString(R.string.verif_enviar))

        setContent {
            AlkilTheme {
                PantallaVerificacion(
                    ui = ui,
                    tipos = tipos.toList(),
                    onTipo = { t ->
                        val numero = if (t == "DNI") ui.numero.filter { it.isDigit() }.take(8) else ui.numero
                        ui = ui.copy(tipo = t, numero = numero)
                    },
                    onNumero = { raw ->
                        val esDni = ui.tipo == "DNI"
                        val limpio = if (esDni) raw.filter { it.isDigit() }.take(8) else raw
                        ui = ui.copy(numero = limpio)
                    },
                    onElegirFrente = { elegirFoto() },
                    onElegirReverso = { elegirFotoReverso() },
                    onEnviar = { enviar() },
                    onAtras = { finish() }
                )
            }
        }

        cargarEstado()
    }

    private fun decodificar(b64: String): Bitmap? {
        val bytes = try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun elegirFoto() {
        if (!ui.fotosHabilitadas) return
        fotoLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    private fun elegirFotoReverso() {
        if (!ui.fotosHabilitadas) return
        fotoReversoLauncher.launch(
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
                        ui = ui.copy(
                            estadoTexto = getString(R.string.verif_estado_verificado),
                            camposHabilitados = false,
                            fotosHabilitadas = false,
                            enviarHabilitado = false,
                            textoBoton = getString(R.string.verif_ya_verificado)
                        )
                    }
                    estado == "pendiente" -> {
                        yaPendiente = true
                        ui = ui.copy(
                            estadoTexto = getString(R.string.verif_estado_pendiente),
                            camposHabilitados = false,
                            fotosHabilitadas = false,
                            enviarHabilitado = false,
                            textoBoton = getString(R.string.verif_en_revision)
                        )
                    }
                    estado == "rechazado" -> {
                        val motivo = (v["motivo"] as? String).orEmpty()
                        ui = ui.copy(
                            estadoTexto = getString(R.string.verif_estado_rechazado, motivo.ifBlank { "-" })
                        )
                    }
                    else -> ui = ui.copy(estadoTexto = null)
                }
                (v["tipoDocumento"] as? String)?.let { tipo ->
                    if (tipos.contains(tipo)) ui = ui.copy(tipo = tipo)
                }
                (v["numeroDocumento"] as? String)?.let { num -> ui = ui.copy(numero = num) }
            }
    }

    private fun enviar() {
        val u = auth.currentUser ?: run {
            Toast.makeText(this, R.string.verif_sesion_requerida, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (yaVerificado) return
        if (yaPendiente) {
            Toast.makeText(this, R.string.verif_estado_pendiente, Toast.LENGTH_LONG).show()
            return
        }
        val tipo = ui.tipo
        val numero = ui.numero.trim()
        if (numero.isBlank()) {
            Toast.makeText(this, R.string.verif_numero_requerido, Toast.LENGTH_SHORT).show()
            return
        }
        if (tipo == "DNI" && !DNI_RE.matches(numero)) {
            Toast.makeText(this, R.string.verif_numero_dni_invalido, Toast.LENGTH_LONG).show()
            return
        }
        val doc = documentoBase64
        if (doc.isNullOrBlank()) {
            Toast.makeText(this, R.string.verif_foto_falta, Toast.LENGTH_SHORT).show()
            return
        }
        val docReverso = documentoReversoBase64
        if (docReverso.isNullOrBlank()) {
            Toast.makeText(this, R.string.verif_foto_reverso_falta, Toast.LENGTH_SHORT).show()
            return
        }

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
            "imagenReverso" to docReverso,
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
        ui = if (bloqueado) {
            ui.copy(
                fotosHabilitadas = false,
                enviarHabilitado = false,
                textoBoton = getString(R.string.auth_procesando)
            )
        } else {
            ui.copy(
                fotosHabilitadas = true,
                enviarHabilitado = true,
                textoBoton = getString(R.string.verif_enviar)
            )
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
