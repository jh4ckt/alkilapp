package com.alkilapp

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.libraries.places.api.Places
import kotlin.math.min

/** Inicializa Places SDK (New) con la misma clave de Maps del AndroidManifest. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val clave = info.metaData?.getString("com.google.android.geo.API_KEY")
            if (!clave.isNullOrBlank()) {
                Places.initialize(this, clave)
                Log.d("AlkilAppBilling", "Places SDK inicializado correctamente con clave: ${clave.substring(0, min(10, clave.length))}...")
            } else {
                Log.w("AlkilAppBilling", "No se encontró API key de Google Maps en manifest")
            }
        } catch (e: Exception) {
            Log.e("AlkilAppBilling", "Error inicializando Places SDK: ${e.message}")
        }
        // Aviso de chats nuevos mientras la app este viva (canal + escucha Firestore).
        MonitorChats.iniciar(this)
    }
}