package com.example.parqueate

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyParkingsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParkingAdapter
    private lateinit var tvEmpty: TextView
    private val parkingsList = mutableListOf<Parking>()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_parkings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerMyParkings)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ParkingAdapter(parkingsList) { selectedParking ->
            val intent = Intent(requireContext(), EditParkingActivity::class.java)
            intent.putExtra("parkingId", selectedParking.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        loadUserParkings()
    }

    private fun loadUserParkings() {
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("parkings")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                parkingsList.clear()
                for (doc in result) {
                    val parking = doc.toObject(Parking::class.java).copy(id = doc.id)
                    parkingsList.add(parking)
                }
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (parkingsList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (parkingsList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar cocheras", Toast.LENGTH_SHORT).show()
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }
}
