package com.example.parqueate

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class TestFirestoreActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_firestore)

        val testButton: Button = findViewById(R.id.btnTestFirestore)
        testButton.setOnClickListener {
            escribirDocumentoDePrueba()
        }
    }

    private fun escribirDocumentoDePrueba() {
        val datosDePrueba = hashMapOf(
            "mensaje" to "Prueba directa desde Android",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("pruebas")
            .add(datosDePrueba)
            .addOnSuccessListener { docRef ->
                Log.d("FirestoreTest", "Documento agregado con ID: ${docRef.id}")
                Toast.makeText(this, "Documento guardado: ${docRef.id}", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreTest", "Error al agregar documento", e)
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
