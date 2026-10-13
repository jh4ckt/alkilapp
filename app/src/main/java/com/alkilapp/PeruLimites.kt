package com.alkilapp

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Límites del territorio peruano. AlkilApp opera solo en Perú: ni la selección
 * en el mapa ni la ubicación del dispositivo pueden quedar fuera del país.
 *
 * `BOUNDS` es el rectángulo envolvente (para restringir la cámara del mapa y la
 * búsqueda de Places). La validación real de «dentro de Perú» usa [POLIGONO],
 * el contorno del país a resolución baja (Natural Earth 110m), porque el mero
 * rectángulo incluiría partes de Ecuador, Colombia, Brasil, Bolivia y Chile.
 */
object PeruLimites {
    val BOUNDS = LatLngBounds(
        LatLng(-18.35, -81.42),
        LatLng(-0.05, -68.66)
    )

    /** Centro geográfico aproximado de Perú, para arrancar la cámara mostrando el país. */
    val CENTRO = LatLng(-9.19, -75.02)

    /** Zoom de vista de país (cubre todo Perú sin mostrar continentes vecinos). */
    const val ZOOM_INICIAL = 5f

    /** Contorno de Perú (Natural Earth 110m): 74 vértices en orden circular, desde la costa (Chile) subiendo por el Pacífico. */
    val POLIGONO: List<LatLng> = listOf(
        LatLng(-18.347975, -70.372572),
        LatLng(-17.773799, -71.37525),
        LatLng(-17.363488, -71.462041),
        LatLng(-16.359363, -73.44453),
        LatLng(-15.265683, -75.237883),
        LatLng(-14.649286, -76.009205),
        LatLng(-13.823187, -76.423469),
        LatLng(-13.535039, -76.259242),
        LatLng(-12.222716, -77.106192),
        LatLng(-10.377712, -78.092153),
        LatLng(-8.386568, -79.036953),
        LatLng(-7.930833, -79.44592),
        LatLng(-7.194341, -79.760578),
        LatLng(-6.541668, -80.537482),
        LatLng(-6.136834, -81.249996),
        LatLng(-5.690557, -80.926347),
        LatLng(-4.736765, -81.410943),
        LatLng(-4.036394, -81.09967),
        LatLng(-3.404856, -80.302561),
        LatLng(-3.821162, -80.184015),
        LatLng(-4.059287, -80.469295),
        LatLng(-4.425724, -80.442242),
        LatLng(-4.346091, -80.028908),
        LatLng(-4.454198, -79.624979),
        LatLng(-4.959129, -79.205289),
        LatLng(-4.547784, -78.639897),
        LatLng(-3.873097, -78.450684),
        LatLng(-3.003021, -77.837905),
        LatLng(-2.608678, -76.635394),
        LatLng(-1.56161, -75.544996),
        LatLng(-0.911417, -75.233723),
        LatLng(-0.152032, -75.373223),
        LatLng(-0.057205, -75.106625),
        LatLng(-0.53082, -74.441601),
        LatLng(-1.002833, -74.122395),
        LatLng(-1.260491, -73.659504),
        LatLng(-2.308954, -73.070392),
        LatLng(-2.434218, -72.325787),
        LatLng(-2.16979, -71.774761),
        LatLng(-2.342802, -71.413646),
        LatLng(-2.256865, -70.813476),
        LatLng(-2.725156, -70.047709),
        LatLng(-3.742872, -70.692682),
        LatLng(-3.766591, -70.394044),
        LatLng(-4.298187, -69.893635),
        LatLng(-4.251265, -70.794769),
        LatLng(-4.401591, -70.928843),
        LatLng(-4.593983, -71.748406),
        LatLng(-5.274561, -72.891928),
        LatLng(-5.741251, -72.964507),
        LatLng(-6.089189, -73.219711),
        LatLng(-6.629931, -73.120027),
        LatLng(-6.918595, -73.724487),
        LatLng(-7.340999, -73.723401),
        LatLng(-7.52383, -73.987235),
        LatLng(-8.424447, -73.571059),
        LatLng(-9.032833, -73.015383),
        LatLng(-9.462213, -73.226713),
        LatLng(-9.520194, -72.563033),
        LatLng(-10.053598, -72.184891),
        LatLng(-10.079436, -71.302412),
        LatLng(-9.490118, -70.481894),
        LatLng(-11.009147, -70.548686),
        LatLng(-11.123972, -70.093752),
        LatLng(-10.951734, -69.529678),
        LatLng(-12.5613, -68.66508),
        LatLng(-12.899729, -68.88008),
        LatLng(-13.602684, -68.929224),
        LatLng(-14.453639, -68.948887),
        LatLng(-14.953195, -69.339535),
        LatLng(-15.323974, -69.160347),
        LatLng(-15.660129, -69.389764),
        LatLng(-16.500698, -68.959635),
        LatLng(-17.580012, -69.590424),
        LatLng(-18.092694, -69.858444)
    )

    /**
     * `true` si el punto está dentro del territorio peruano. Primero el
     * rectángulo envolvente (corte rápido) y luego point-in-polygon sobre el
     * contorno real (ray casting).
     */
    fun dentroDe(lat: Double, lng: Double): Boolean {
        if (!BOUNDS.contains(LatLng(lat, lng))) return false
        return dentroDePoligono(lat, lng)
    }

    private fun dentroDePoligono(lat: Double, lng: Double): Boolean {
        var dentro = false
        var j = POLIGONO.lastIndex
        for (i in POLIGONO.indices) {
            val vi = POLIGONO[i]
            val vj = POLIGONO[j]
            if ((vi.latitude > lat) != (vj.latitude > lat) &&
                lng < (vj.longitude - vi.longitude) * (lat - vi.latitude) / (vj.latitude - vi.latitude) + vi.longitude
            ) {
                dentro = !dentro
            }
            j = i
        }
        return dentro
    }
}