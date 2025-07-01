package com.example.parqueate

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var map: GoogleMap
    private lateinit var edtSearch: EditText
    private lateinit var btnFilters: ImageButton
    private lateinit var spinnerSort: Spinner
    private lateinit var btnApplyFilters: Button
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        edtSearch = view.findViewById(R.id.edtSearch)
        btnFilters = view.findViewById(R.id.btnFilters)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        btnApplyFilters = view.findViewById(R.id.btnApplyFilters)

        val sortOptions = arrayOf("Distancia", "Precio", "Calificación")
        spinnerSort.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortOptions)

        btnFilters.setOnClickListener {
            spinnerSort.visibility = if (spinnerSort.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            btnApplyFilters.visibility = if (btnApplyFilters.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnApplyFilters.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    loadParkings(location)
                }
            } else {
                loadParkings(null)
            }
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        return view
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            loadParkings(null)
            return
        }

        try {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                    loadParkings(location)
                } else {
                    Toast.makeText(requireContext(), "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                    loadParkings(null)
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
                loadParkings(null)
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Permiso de ubicación requerido", Toast.LENGTH_SHORT).show()
            loadParkings(null)
        }

        map.setOnMarkerClickListener { marker ->
            val parking = marker.tag as? Parking
            parking?.let {
                openParkingDetail(it)
            }
            false // Return false to show the info window and allow navigation
        }
    }

    private fun loadParkings(userLocation: Location?) {
        firestore.collection("parkings")
            .whereGreaterThan("availableSpaces", 0)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(requireContext(), "No se encontraron cocheras disponibles", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val parkings = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Parking::class.java).copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedParkings = when (spinnerSort.selectedItem.toString()) {
                    "Precio" -> parkings.sortedBy { it.pricePerHour }
                    "Calificación" -> parkings.sortedByDescending { it.averageRating }
                    else -> parkings.sortedBy { it.distanceFromUserKm(userLocation) }
                }
                map.clear()
                sortedParkings.forEach { parking ->
                    if (parking.latitude != 0.0 && parking.longitude != 0.0) {
                        val latLng = LatLng(parking.latitude, parking.longitude)
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("${parking.title} - $${parking.pricePerHour}/h")
                                .snippet("Calificación: ${parking.averageRating} (${parking.reviewCount} reseñas)")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        )
                        marker?.tag = parking
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al cargar cocheras: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openParkingDetail(parking: Parking) {
        val fragment = ParkingDetailFragment.newInstance(parking)
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    map.isMyLocationEnabled = true
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val userLatLng = LatLng(location.latitude, location.longitude)
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                            loadParkings(location)
                        }
                    }.addOnFailureListener {
                        Toast.makeText(requireContext(), "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
                        loadParkings(null)
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(requireContext(), "Permiso de ubicación requerido", Toast.LENGTH_SHORT).show()
                    loadParkings(null)
                }
            }
        } else {
            Toast.makeText(requireContext(), "Se requiere permiso de ubicación", Toast.LENGTH_LONG).show()
            loadParkings(null)
        }
    }
}

fun Parking.distanceFromUserKm(userLocation: Location?): Double {
    if (userLocation == null || latitude == 0.0 || longitude == 0.0) return Double.MAX_VALUE
    val parkingLocation = Location("").apply {
        latitude = this@distanceFromUserKm.latitude
        longitude = this@distanceFromUserKm.longitude
    }
    return userLocation.distanceTo(parkingLocation) / 1000.0
}