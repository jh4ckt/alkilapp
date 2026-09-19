package com.alkilapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivityMapaSeleccionBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Selector de ubicacion sobre el mapa: el usuario toca el mapa para colocar
 * el marcador del inmueble y confirma. Devuelve lat/lng como resultado.
 */
class MapaSeleccionActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val DEFAULT_ZOOM = 16f
        private const val PERU_ZOOM = 12f

        const val EXTRA_LAT_INICIAL = "mapa_sel_lat"
        const val EXTRA_LNG_INICIAL = "mapa_sel_lng"
        const val EXTRA_LAT_RESULTADO = "mapa_sel_result_lat"
        const val EXTRA_LNG_RESULTADO = "mapa_sel_result_lng"
    }

    private lateinit var binding: ActivityMapaSeleccionBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var mMap: GoogleMap? = null
    private var marcador: Marker? = null
    private var latSel = 0.0
    private var lngSel = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaSeleccionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        latSel = intent.getDoubleExtra(EXTRA_LAT_INICIAL, 0.0)
        lngSel = intent.getDoubleExtra(EXTRA_LNG_INICIAL, 0.0)

        binding.btnMapaSelBack.setOnClickListener { finish() }
        binding.btnMapaSelConfirmar.setOnClickListener {
            if (marcador == null) {
                Toast.makeText(this, R.string.mapa_seleccion_sin_punto, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val resultado = Intent().apply {
                putExtra(EXTRA_LAT_RESULTADO, latSel)
                putExtra(EXTRA_LNG_RESULTADO, lngSel)
            }
            setResult(RESULT_OK, resultado)
            finish()
        }
        binding.fabMapaSelMiUbicacion.setOnClickListener { irAMiUbicacion() }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapaSeleccion) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        mMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        /** Tocar el mapa coloca (o mueve) el marcador con la ubicacion elegida. */
        map.setOnMapClickListener { punto ->
            latSel = punto.latitude
            lngSel = punto.longitude
            if (marcador == null) {
                marcador = map.addMarker(MarkerOptions().position(punto))
            } else {
                marcador?.position = punto
            }
        }

        val puntoInicial = if (latSel != 0.0 || lngSel != 0.0) {
            LatLng(latSel, lngSel).also { p ->
                marcador = map.addMarker(MarkerOptions().position(p))
            }
        } else {
            LatLng(-12.0464, -77.0428)
        }
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                puntoInicial,
                if (latSel != 0.0 || lngSel != 0.0) DEFAULT_ZOOM else PERU_ZOOM
            )
        )
    }

    private fun irAMiUbicacion() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.location_error, Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location == null) return@addOnSuccessListener
                val p = LatLng(location.latitude, location.longitude)
                latSel = p.latitude
                lngSel = p.longitude
                if (marcador == null) {
                    marcador = mMap?.addMarker(MarkerOptions().position(p))
                } else {
                    marcador?.position = p
                }
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(p, DEFAULT_ZOOM))
            }
    }
}