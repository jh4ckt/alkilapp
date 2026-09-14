package com.alkilapp.data

import com.google.android.gms.maps.model.LatLng

data class Departamento(
    val id: Int,
    val titulo: String,
    val direccion: String,
    val precio: String,
    val lat: Double,
    val lng: Double
) {
    val ubicacion: LatLng
        get() = LatLng(lat, lng)

    companion object {
        fun muestras(): List<Departamento> = listOf(
            Departamento(1, "Departamento en Palermo", "Thames 1500, CABA", "USD 900 / mes", -34.5885, -58.4273),
            Departamento(2, "Monoambiente en Recoleta", "Av. Callao 1200, CABA", "USD 650 / mes", -34.5944, -58.3867),
            Departamento(3, "Departamento en Belgrano", "Av. Cabildo 1400, CABA", "USD 1050 / mes", -34.5567, -58.4547),
            Departamento(4, "Loft en San Telmo", "Defensa 700, CABA", "USD 820 / mes", -34.6186, -58.3707)
        )
    }
}