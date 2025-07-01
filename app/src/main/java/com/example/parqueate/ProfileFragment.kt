package com.example.parqueate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private lateinit var profileImageView: ImageView
    private lateinit var selectImageButton: Button
    private lateinit var tvEmail: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var roleSpinner: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnMyParkings: Button
    private lateinit var btnLogout: Button
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        profileImageView = view.findViewById(R.id.profileImageView)
        selectImageButton = view.findViewById(R.id.selectImageButton)
        tvEmail = view.findViewById(R.id.tvEmail)
        etName = view.findViewById(R.id.etName)
        etPhone = view.findViewById(R.id.etPhone)
        roleSpinner = view.findViewById(R.id.roleSpinner)
        btnSave = view.findViewById(R.id.btnSave)
        btnMyParkings = view.findViewById(R.id.btnMyParkings)
        btnLogout = view.findViewById(R.id.btnLogout)

        setupRoleSpinner()

        selectImageButton.setOnClickListener { openGallery() }
        btnSave.setOnClickListener { saveProfile() }
        btnMyParkings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MyParkingsFragment())
                .addToBackStack(null)
                .commit()
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
            return
        }

        tvEmail.text = user.email
        loadUserProfile(user.uid)
    }

    private fun setupRoleSpinner() {
        val roles = arrayOf("Cliente", "Rentador")
        roleSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
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

    private fun loadUserProfile(userId: String) {
        firestore.collection("profiles").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: ""
                    val phone = doc.getString("phone") ?: ""
                    val role = doc.getString("role") ?: "Cliente"
                    val imageUrl = doc.getString("imageUrl") ?: ""

                    etName.setText(name)
                    etPhone.setText(phone)
                    roleSpinner.setSelection(if (role == "Rentador") 1 else 0)
                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this).load(imageUrl).into(profileImageView)
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val user = auth.currentUser ?: run {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val role = roleSpinner.selectedItem.toString()

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), "Llena todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (!phone.matches(Regex("^[0-9]{10}$"))) {
            Toast.makeText(requireContext(), "Ingresa un número de teléfono válido (10 dígitos)", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        btnSave.text = "Guardando..."

        if (imageUri != null) {
            uploadImageToStorage(user.uid, name, phone, role)
        } else {
            // Obtener la URL de la imagen actual si no se seleccionó una nueva
            firestore.collection("profiles").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val currentImageUrl = doc.getString("imageUrl") ?: ""
                    guardarEnFirestore(user.uid, name, phone, role, currentImageUrl)
                }
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
                Toast.makeText(requireContext(), "Error al subir imagen: ${exception.message}", Toast.LENGTH_LONG).show()
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

        firestore.collection("profiles").document(uid).set(perfil)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show()
                resetSaveButton()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error al guardar perfil: ${exception.message}", Toast.LENGTH_SHORT).show()
                resetSaveButton()
            }
    }

    private fun resetSaveButton() {
        btnSave.isEnabled = true
        btnSave.text = "Guardar Perfil"
    }
}