package com.alkilapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alkilapp.databinding.ActivityUbicacionInmuebleBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class UbicacionInmuebleActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityUbicacionInmuebleBinding

    private var lat = 0.0
    private var lng = 0.0
    private var titulo = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUbicacionInmuebleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty()

        binding.tvUbiTitulo.text = titulo
        binding.btnUbiBack.setOnClickListener { finish() }
        binding.btnComoLlegar.setOnClickListener { abrirNavegacion() }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapaInmueble) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        if (lat == 0.0 && lng == 0.0) return
        val punto = LatLng(lat, lng)
        map.uiSettings.isZoomControlsEnabled = true
        map.addMarker(MarkerOptions().position(punto).title(titulo.ifBlank { getString(R.string.ubicacion_titulo) }))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, 16f))
    }

    private fun abrirNavegacion() {
        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(this, R.string.ubicacion_sin_coordenadas, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("google.navigation:q=$lat,$lng")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.ubicacion_navegacion_error, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_LAT = "ubi_lat"
        const val EXTRA_LNG = "ubi_lng"
        const val EXTRA_TITULO = "ubi_titulo"
    }
}