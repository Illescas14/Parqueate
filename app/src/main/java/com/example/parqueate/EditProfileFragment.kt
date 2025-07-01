package com.example.parqueate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class EditProfileFragment : Fragment() {

    private lateinit var imageProfile: ImageView
    private lateinit var editName: EditText
    private lateinit var editPhone: EditText
    private lateinit var spinnerRole: Spinner
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private var imageUri: Uri? = null
    private val storageRef = FirebaseStorage.getInstance().reference
    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        imageProfile = view.findViewById(R.id.imageProfile)
        editName = view.findViewById(R.id.editName)
        editPhone = view.findViewById(R.id.editPhone)
        spinnerRole = view.findViewById(R.id.spinnerRole)
        btnSave = view.findViewById(R.id.btnSave)
        progressBar = view.findViewById(R.id.progressBar)

        val roles = arrayOf("Usuario", "Propietario")
        spinnerRole.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)

        loadUserData()

        imageProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 100)
        }

        btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadUserData() {
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("profiles").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: ""
                    val phone = doc.getString("phone") ?: ""
                    val role = doc.getString("role") ?: "Usuario"
                    val imageUrl = doc.getString("imageUrl") ?: ""

                    editName.setText(name)
                    editPhone.setText(phone)
                    spinnerRole.setSelection(if (role == "Propietario") 1 else 0)

                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this).load(imageUrl).into(imageProfile)
                    }
                } else {
                    Toast.makeText(requireContext(), "Perfil no encontrado", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val name = editName.text.toString().trim()
        val phone = editPhone.text.toString().trim()
        val role = spinnerRole.selectedItem.toString()

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        if (imageUri != null) {
            val ref = storageRef.child("profileImages/$userId/${UUID.randomUUID()}.jpg")
            ref.putFile(imageUri!!)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { uri ->
                        updateUserData(name, phone, role, uri.toString())
                    }
                }
                .addOnFailureListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error al subir imagen", Toast.LENGTH_SHORT).show()
                }
        } else {
            updateUserData(name, phone, role, null)
        }
    }

    private fun updateUserData(name: String, phone: String, role: String, imageUrl: String?) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "role" to role
        )
        if (imageUrl != null) updates["imageUrl"] = imageUrl

        db.collection("profiles").document(userId)
            .set(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show()
                requireActivity().supportFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            imageProfile.setImageURI(imageUri)
        }
    }
}
