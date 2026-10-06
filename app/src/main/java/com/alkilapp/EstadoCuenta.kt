package com.alkilapp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Estado de la cuenta segun el panel de administracion.
 *
 * El backend escribe `usuarios/{uid}.estado` con los valores
 * `activo`, `suspendido` o `desactivado` (ver `POST /api/usuarios/:id/estado`
 * en backend/admin/server.js). Ese campo NO esta en la whitelist de escritura
 * del cliente en firestore.rules, asi que solo el admin puede ponerlo.
 *
 * El cliente nunca lo leia: por eso una cuenta desactivada podia entrar
 * normal. Este helper concentra la consulta y el cierre de sesion.
 */
object EstadoCuenta {

    private val db by lazy { FirebaseFirestore.getInstance("alkilappdb") }

    private const val ESTADO_DESACTIVADO = "desactivado"

    fun esDesactivado(estado: String?): Boolean =
        estado?.trim()?.lowercase() == ESTADO_DESACTIVADO

    /**
     * Consulta `usuarios/{uid}.estado` de la sesion actual y responde por
     * callback si la cuenta esta desactivada.
     *
     * Ante cualquier error de lectura se responde `false` (permitido): no
     * dejar al usuario fuera por un fallo de red es preferible a bloquearle
     * el acceso a su propio anuncio.
     */
    fun consultarDesactivado(callback: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            callback(false)
            return
        }
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                callback(esDesactivado(doc.getString("estado")))
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    /** Cierra la sesion de Firebase. El callback de UI lo decide la Activity. */
    fun cerrarSesion() {
        FirebaseAuth.getInstance().signOut()
    }
}