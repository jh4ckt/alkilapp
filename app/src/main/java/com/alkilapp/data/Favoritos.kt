package com.alkilapp.data

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Favoritos del usuario: cache local (SharedPreferences) para lectura rapida
 * y respaldo durable en Firestore (coleccion "favoritos", doc "<uid>__<id>").
 * Sin sesion, el cache local sigue funcionando (favoritos de ese dispositivo);
 * al iniciar sesion, sincronizar() trae los de la nube.
 */
object Favoritos {

    private const val PREFS = "alkilapp_favoritos"
    private const val KEY = "ids"

    fun db(): FirebaseFirestore = FirebaseFirestore.getInstance("alkilappdb")

    fun docId(uid: String, propiedadId: String) = "${uid}__${propiedadId}"

    /** Lectura instantanea del cache local. */
    fun esFavorito(ctx: Context, id: String): Boolean {
        if (id.isBlank()) return false
        return prefs(ctx).getStringSet(KEY, emptySet())?.contains(id) == true
    }

    /** Lista local (sin esperar a la nube). */
    fun locales(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet()

    /**
     * Alterna el favorito local y, si hay sesion, lo replica en Firestore
     * (create/delete segun el nuevo estado). Devuelve el estado nuevo.
     */
    fun alternar(ctx: Context, uid: String?, id: String): Boolean {
        val p = prefs(ctx)
        val set = HashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        val nuevo = if (set.contains(id)) {
            set.remove(id)
            false
        } else {
            set.add(id)
            true
        }
        p.edit().putStringSet(KEY, set).apply()
        if (uid != null) {
            val ref = db().collection("favoritos").document(docId(uid, id))
            if (nuevo) {
                ref.set(
                    mapOf(
                        "uid" to uid,
                        "propiedadId" to id,
                        "creadoEn" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                ref.delete()
            }
        }
        return nuevo
    }

    /** Sobrescribe el cache local con lo que venga de la nube (login/inicio). */
    fun sincronizar(ctx: Context, uid: String, alListo: (Set<String>) -> Unit) {
        db().collection("favoritos").whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { snap ->
                val ids = snap.documents
                    .mapNotNull { it.getString("propiedadId") }
                    .toSet()
                prefs(ctx).edit().putStringSet(KEY, ids).apply()
                alListo(ids)
            }
            .addOnFailureListener {
                alListo(prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet())
            }
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}