package com.alkilapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.alkilapp.data.Departamento
import com.alkilapp.databinding.ActivityMainBinding
import com.alkilapp.ui.DepartamentoAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: DepartamentoAdapter

    private val marcadores = mutableListOf<Marker>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
        private const val DEFAULT_CAMERA_ZOOM = 15f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupListaDepartamentos()
    }

    private fun setupListaDepartamentos() {
        adapter = DepartamentoAdapter { depto ->
            mostrarDepartamentoEnMapa(depto)
        }
        binding.rvDepartamentos.layoutManager = LinearLayoutManager(this)
        binding.rvDepartamentos.adapter = adapter
        adapter.submitList(Departamento.muestras())

        binding.etBusqueda.doAfterTextChanged { texto ->
            adapter.filter(texto?.toString().orEmpty())
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
        verificarPermisosUbicacion()
    }

    private fun verificarPermisosUbicacion() {
        if (tienePermisoUbicacion()) {
            obtenerUbicacionActual()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED ||
                coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun obtenerUbicacionActual() {
        if (!tienePermisoUbicacion()) return

        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                centrarEn(LatLng(location.latitude, location.longitude), true)
            } else {
                Toast.makeText(this, R.string.location_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun centrarEn(punto: LatLng, esUbicacionPropia: Boolean) {
        limpiarMarcadores()
        if (esUbicacionPropia) {
            marcadores.add(
                mMap.addMarker(
                    com.google.android.gms.maps.model.MarkerOptions()
                        .position(punto)
                        .title(getString(R.string.my_location_title))
                )!!
            )
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(punto, DEFAULT_CAMERA_ZOOM))
    }

    private fun mostrarDepartamentoEnMapa(depto: Departamento) {
        limpiarMarcadores()
        marcadores.add(
            mMap.addMarker(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(depto.ubicacion)
                    .title(depto.titulo)
                    .snippet(depto.precio)
            )!!
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(depto.ubicacion, DEFAULT_CAMERA_ZOOM))
    }

    private fun limpiarMarcadores() {
        marcadores.forEach { it.remove() }
        marcadores.clear()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                obtenerUbicacionActual()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }
}