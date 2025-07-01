package com.example.parqueate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class CreateProfileActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = Firebase.auth

    private lateinit var profileImageView: ImageView
    private lateinit var selectImageButton: Button
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var roleSpinner: Spinner
    private lateinit var saveProfileButton: Button

    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        initView()
        setupRoleSpinner()

        auth.addAuthStateListener { firebaseAuth ->
            firebaseAuth.currentUser?.let {
                Log.d("CreateProfile", "Usuario autenticado: ${it.uid}")
            } ?: run {
                Log.d("CreateProfile", "Usuario no autenticado. Redirigiendo a LoginActivity")
                Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun initView() {
        profileImageView = findViewById(R.id.profileImageView)
        selectImageButton = findViewById(R.id.selectImageButton)
        nameInput = findViewById(R.id.nameInput)
        phoneInput = findViewById(R.id.phoneInput)
        roleSpinner = findViewById(R.id.roleSpinner)
        saveProfileButton = findViewById(R.id.saveProfileButton)

        selectImageButton.setOnClickListener { openGallery() }
        saveProfileButton.setOnClickListener { saveProfile() }
    }

    private fun setupRoleSpinner() {
        val roles = arrayOf("Cliente", "Rentador")
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(Intent.createChooser(intent, "Selecciona una imagen"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            imageUri = data.data
            profileImageView.setImageURI(imageUri)
        }
    }

    private fun saveProfile() {
        val user = auth.currentUser ?: run {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val role = roleSpinner.selectedItem.toString()

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Llena todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (!phone.matches(Regex("^[0-9]{10}$"))) {
            Toast.makeText(this, "Ingresa un número de teléfono válido (10 dígitos)", Toast.LENGTH_SHORT).show()
            return
        }

        saveProfileButton.isEnabled = false
        saveProfileButton.text = "Guardando..."

        if (imageUri != null) {
            uploadImageToStorage(user.uid, name, phone, role)
        } else {
            guardarEnFirestore(user.uid, name, phone, role, "")
        }
    }

    private fun uploadImageToStorage(uid: String, name: String, phone: String, role: String) {
        val imageRef = storage.reference.child("profile_images/$uid.jpg")
        imageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    guardarEnFirestore(uid, name, phone, role, uri.toString())
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al subir imagen: ${exception.message}", Toast.LENGTH_LONG).show()
                resetSaveButton()
            }
    }

    private fun guardarEnFirestore(uid: String, name: String, phone: String, role: String, imageUrl: String) {
        val perfil = hashMapOf(
            "name" to name,
            "phone" to phone,
            "role" to role,
            "imageUrl" to imageUrl,
            "email" to auth.currentUser?.email
        )
        Log.d("CreateProfile", "Guardando en Firestore: $uid -> $perfil")

        db.collection("profiles").document(uid).set(perfil)
            .addOnSuccessListener {
                Log.d("CreateProfile", "Perfil guardado correctamente")
                Toast.makeText(this, "Perfil guardado", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { exception ->
                Log.e("CreateProfile", "Error al guardar perfil", exception)
                Toast.makeText(this, "Error al guardar perfil: ${exception.message}", Toast.LENGTH_SHORT).show()
                resetSaveButton()
            }
    }

    private fun resetSaveButton() {
        saveProfileButton.isEnabled = true
        saveProfileButton.text = "Guardar Perfil"
    }
}