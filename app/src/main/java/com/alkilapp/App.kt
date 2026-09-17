package com.alkilapp

import android.app.Application
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places

/** Inicializa Places SDK (New) con la misma clave de Maps del AndroidManifest. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val clave = info.metaData?.getString("com.google.android.geo.API_KEY")
            if (!clave.isNullOrBlank()) {
                Places.initialize(this, clave)
            }
        } catch (_: Exception) {
            // Sin Places no hay autocomplete de direcciones; el alta manual sigue funcionando.
        }
    }
}