package com.example.parqueate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class EditParkingActivity : AppCompatActivity() {

    private lateinit var inputTitle: EditText
    private lateinit var inputDescription: EditText
    private lateinit var inputPricePerHour: EditText
    private lateinit var inputSpaces: EditText
    private lateinit var btnGetLocation: Button
    private lateinit var btnSelectImages: Button
    private lateinit var btnSave: Button
    private lateinit var imageContainer: LinearLayout

    private val selectedImageUris = mutableListOf<Uri>()
    private val imagesToDelete = mutableSetOf<String>()
    private val existingImageUrls = mutableListOf<String>()
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var parkingId: String = ""

    private val PICK_IMAGES_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_parking)

        inputTitle = findViewById(R.id.inputTitle)
        inputDescription = findViewById(R.id.inputDescription)
        inputPricePerHour = findViewById(R.id.inputPricePerHour)
        inputSpaces = findViewById(R.id.inputSpaces)
        btnGetLocation = findViewById(R.id.btnGetLocation)
        btnSelectImages = findViewById(R.id.btnSelectImages)
        btnSave = findViewById(R.id.btnSave)
        imageContainer = findViewById(R.id.imageContainer)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        parkingId = intent.getStringExtra("parkingId") ?: ""

        if (parkingId.isNotEmpty()) {
            loadParkingData()
        }

        btnGetLocation.setOnClickListener {
            getCurrentLocation()
        }

        btnSelectImages.setOnClickListener {
            openImageSelector()
        }

        btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadParkingData() {
        firestore.collection("parkings").document(parkingId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val parking = document.toObject(Parking::class.java)
                    if (parking != null) {
                        inputTitle.setText(parking.title)
                        inputDescription.setText(parking.description)
                        inputPricePerHour.setText(parking.pricePerHour.toString())
                        inputSpaces.setText(parking.spaces.toString())
                        latitude = parking.latitude
                        longitude = parking.longitude
                        existingImageUrls.clear()
                        existingImageUrls.addAll(parking.imageUrls)
                        showExistingImages()
                    }
                } else {
                    Toast.makeText(this, "No se encontró la cochera", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showExistingImages() {
        imageContainer.removeAllViews()
        for (url in existingImageUrls) {
            val imageView = createImageView(url, isExisting = true)
            imageContainer.addView(imageView)
        }
    }

    private fun createImageView(imageUriOrUrl: String, isExisting: Boolean): View {
        val view = layoutInflater.inflate(R.layout.item_image, null)
        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val btnRemove = view.findViewById<Button>(R.id.btnRemove)

        Glide.with(this).load(imageUriOrUrl).into(imageView)

        btnRemove.setOnClickListener {
            if (isExisting) {
                imagesToDelete.add(imageUriOrUrl)
                existingImageUrls.remove(imageUriOrUrl)
            } else {
                selectedImageUris.remove(Uri.parse(imageUriOrUrl))
            }
            imageContainer.removeView(view)
        }

        view.tag = imageUriOrUrl
        return view
    }

    private fun openImageSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, PICK_IMAGES_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGES_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            if (data.clipData != null) {
                for (i in 0 until data.clipData!!.itemCount) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    selectedImageUris.add(uri)
                    val iv = createImageView(uri.toString(), isExisting = false)
                    imageContainer.addView(iv)
                }
            } else if (data.data != null) {
                val uri = data.data!!
                selectedImageUris.add(uri)
                val iv = createImageView(uri.toString(), isExisting = false)
                imageContainer.addView(iv)
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
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
                    Toast.makeText(this, "Ubicación obtenida: ($latitude, $longitude)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveChanges() {
        val title = inputTitle.text.toString().trim()
        val description = inputDescription.text.toString().trim()
        val pricePerHour = inputPricePerHour.text.toString().toDoubleOrNull() ?: 0.0
        val spaces = inputSpaces.text.toString().toIntOrNull() ?: 0

        if (title.isEmpty() || description.isEmpty() || pricePerHour <= 0 || spaces <= 0 || latitude == 0.0 || longitude == 0.0) {
            Toast.makeText(this, "Completa todos los campos correctamente", Toast.LENGTH_SHORT).show()
            return
        }

        for (url in imagesToDelete) {
            storage.getReferenceFromUrl(url).delete()
        }

        val newImageUrls = mutableListOf<String>()
        if (selectedImageUris.isNotEmpty()) {
            uploadImagesRecursively(selectedImageUris, 0, newImageUrls) {
                updateFirestore(title, description, pricePerHour, spaces, existingImageUrls + newImageUrls)
            }
        } else {
            updateFirestore(title, description, pricePerHour, spaces, existingImageUrls)
        }
    }

    private fun uploadImagesRecursively(
        uris: List<Uri>,
        index: Int,
        uploadedUrls: MutableList<String>,
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
                    uploadImagesRecursively(uris, index + 1, uploadedUrls, onComplete)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir imagen", Toast.LENGTH_SHORT).show()
                uploadImagesRecursively(uris, index + 1, uploadedUrls, onComplete)
            }
    }

    private fun updateFirestore(title: String, description: String, pricePerHour: Double, spaces: Int, imageUrls: List<String>) {
        val data = mapOf(
            "title" to title,
            "description" to description,
            "pricePerHour" to pricePerHour,
            "spaces" to spaces,
            "availableSpaces" to spaces,
            "latitude" to latitude,
            "longitude" to longitude,
            "imageUrls" to imageUrls
        )
        firestore.collection("parkings").document(parkingId)
            .update(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Cochera actualizada", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
            }
    }
}