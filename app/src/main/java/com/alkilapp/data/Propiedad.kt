package com.alkilapp.data

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentSnapshot

data class Propiedad(
    val id: String = "",
    val titulo: String = "",
    val descripcion: String = "",
    val tipo: String = "departamento",
    val operacion: String = "alquiler",
    val precio: Double = 0.0,
    val moneda: String = "USD",
    val direccion: String = "",
    val barrio: String = "",
    val ciudad: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val idPropietario: String = "",
    val contacto: String = "",
    val estado: String = "disponible",
    val ambientes: Int = 0,
    val superficieM2: Double = 0.0,
    val comodidades: List<String> = emptyList(),
    val fotos: List<String> = emptyList(),
    val photosUrl: List<String> = emptyList(),
    val isFeatured: Boolean = false,
    val featuredUntil: Long = 0L
) {
    val ubicacion: LatLng
        get() = LatLng(lat, lng)

    /** Destacado solo si la flag es true y la fecha de expiración aún no pasó. */
    val esDestacado: Boolean
        get() = isFeatured && (featuredUntil == 0L || featuredUntil > System.currentTimeMillis())

    val precioFormateado: String
        get() {
            if (precio <= 0) return ""
            val monto = if (precio == precio.toLong().toDouble()) {
                precio.toLong().toString()
            } else {
                precio.toString()
            }
            val simbolo = if (moneda == "PEN") "S/ " else "$ "
            return if (operacion == "venta") "$simbolo$monto" else "$simbolo$monto / mes"
        }

    companion object {
        fun desde(doc: DocumentSnapshot): Propiedad? {
            val d = doc.data ?: return null
            fun s(k: String): String = d[k] as? String ?: ""
            fun n(k: String): Double = (d[k] as? Number)?.toDouble() ?: 0.0
            @Suppress("UNCHECKED_CAST")
            fun l(k: String): List<String> = (d[k] as? List<String>) ?: emptyList()
            return Propiedad(
                id = doc.id,
                titulo = s("titulo"),
                descripcion = s("descripcion"),
                tipo = s("tipo"),
                operacion = s("operacion"),
                precio = n("precio"),
                moneda = s("moneda"),
                direccion = s("direccion"),
                barrio = s("barrio"),
                ciudad = s("ciudad"),
                lat = n("lat"),
                lng = n("lng"),
                idPropietario = s("idPropietario"),
                contacto = s("contacto"),
                estado = s("estado"),
                ambientes = n("ambientes").toInt(),
                superficieM2 = n("superficieM2"),
                comodidades = l("comodidades"),
                fotos = (d["fotos"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                photosUrl = (d["photos"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                isFeatured = d["isFeatured"] as? Boolean ?: false,
                featuredUntil = (d["featuredUntil"] as? com.google.firebase.Timestamp)
                    ?.toDate()?.time ?: (d["featuredUntil"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}