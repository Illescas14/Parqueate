package com.example.parqueate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class AddParkingFragment : Fragment() {

    private lateinit var inputTitle: EditText
    private lateinit var inputDescription: EditText
    private lateinit var inputPricePerHour: EditText
    private lateinit var inputSpaces: EditText
    private lateinit var btnGetLocation: Button
    private lateinit var btnSelectImages: Button
    private lateinit var btnSave: Button
    private lateinit var imageContainer: LinearLayout

    private val selectedImageUris = mutableListOf<Uri>()
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val pickImages = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                if (data.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) {
                        val uri = data.clipData!!.getItemAt(i).uri
                        selectedImageUris.add(uri)
                        val iv = createImageView(uri.toString())
                        imageContainer.addView(iv)
                    }
                } else if (data.data != null) {
                    val uri = data.data!!
                    selectedImageUris.add(uri)
                    val iv = createImageView(uri.toString())
                    imageContainer.addView(iv)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_parking, container, false)

        inputTitle = view.findViewById(R.id.inputTitle)
        inputDescription = view.findViewById(R.id.inputDescription)
        inputPricePerHour = view.findViewById(R.id.inputPricePerHour)
        inputSpaces = view.findViewById(R.id.inputSpaces)
        btnGetLocation = view.findViewById(R.id.btnGetLocation)
        btnSelectImages = view.findViewById(R.id.btnSelectImages)
        btnSave = view.findViewById(R.id.btnSave)
        imageContainer = view.findViewById(R.id.imageContainer)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        btnGetLocation.setOnClickListener { getCurrentLocation() }
        btnSelectImages.setOnClickListener { openImageSelector() }
        btnSave.setOnClickListener { saveParking() }

        return view
    }

    private fun createImageView(imageUri: String): View {
        val view = layoutInflater.inflate(R.layout.item_image, null)
        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val btnRemove = view.findViewById<Button>(R.id.btnRemove)

        Glide.with(this).load(imageUri).into(imageView)

        btnRemove.setOnClickListener {
            selectedImageUris.remove(Uri.parse(imageUri))
            imageContainer.removeView(view)
        }

        view.tag = imageUri
        return view
    }

    private fun openImageSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickImages.launch(intent)
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1000
            )
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    Toast.makeText(requireContext(), "Ubicación obtenida: ($latitude, $longitude)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveParking() {
        val title = inputTitle.text.toString().trim()
        val description = inputDescription.text.toString().trim()
        val pricePerHour = inputPricePerHour.text.toString().toDoubleOrNull() ?: 0.0
        val spaces = inputSpaces.text.toString().toIntOrNull() ?: 0
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isEmpty() || description.isEmpty() || pricePerHour <= 0 || spaces <= 0 || latitude == 0.0 || longitude == 0.0) {
            Toast.makeText(requireContext(), "Completa todos los campos correctamente", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        btnSave.text = "Guardando..."

        val parkingId = firestore.collection("parkings").document().id
        val newImageUrls = mutableListOf<String>()

        if (selectedImageUris.isNotEmpty()) {
            uploadImagesRecursively(selectedImageUris, 0, newImageUrls, parkingId) {
                saveToFirestore(parkingId, userId, title, description, pricePerHour, spaces, newImageUrls)
            }
        } else {
            saveToFirestore(parkingId, userId, title, description, pricePerHour, spaces, newImageUrls)
        }
    }

    private fun uploadImagesRecursively(
        uris: List<Uri>,
        index: Int,
        uploadedUrls: MutableList<String>,
        parkingId: String,
        onComplete: () -> Unit
    ) {
        if (index >= uris.size) {
            onComplete()
            return
        }
        val uri = uris[index]
        val filename = UUID.randomUUID().toString()
        val ref = storage.reference.child("parkings/$parkingId/$filename")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    uploadedUrls.add(downloadUrl.toString())
                    uploadImagesRecursively(uris, index + 1, uploadedUrls, parkingId, onComplete)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al subir imagen", Toast.LENGTH_SHORT).show()
                uploadImagesRecursively(uris, index + 1, uploadedUrls, parkingId, onComplete)
            }
    }

    private fun saveToFirestore(
        parkingId: String,
        userId: String,
        title: String,
        description: String,
        pricePerHour: Double,
        spaces: Int,
        imageUrls: List<String>
    ) {
        val parking = mapOf(
            "userId" to userId,
            "title" to title,
            "description" to description,
            "pricePerHour" to pricePerHour,
            "spaces" to spaces,
            "availableSpaces" to spaces,
            "latitude" to latitude,
            "longitude" to longitude,
            "imageUrls" to imageUrls,
            "averageRating" to 0.0,
            "reviewCount" to 0
        )
        firestore.collection("parkings").document(parkingId).set(parking)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Cochera agregada", Toast.LENGTH_SHORT).show()
                requireActivity().supportFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al agregar cochera", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Guardar"
            }
    }
}