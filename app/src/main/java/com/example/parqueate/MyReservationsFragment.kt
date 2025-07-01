package com.example.parqueate

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MyReservationsFragment : Fragment() {

    private lateinit var recyclerReservations: RecyclerView
    private lateinit var tvNoReservations: TextView
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val reservationsList = mutableListOf<Reservation>()
    private lateinit var reservationsAdapter: ReservationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_reservations, container, false)

        recyclerReservations = view.findViewById(R.id.recyclerReservations)
        tvNoReservations = view.findViewById(R.id.tvNoReservations)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadReservations()
    }

    private fun setupRecyclerView() {
        recyclerReservations.layoutManager = LinearLayoutManager(requireContext())
        reservationsAdapter = ReservationsAdapter(reservationsList) { reservation, action ->
            when (action) {
                "details" -> showReservationDetails(reservation)
                "cancel" -> cancelReservation(reservation)
            }
        }
        recyclerReservations.adapter = reservationsAdapter
    }

    private fun loadReservations() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("reservations")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                reservationsList.clear()
                for (doc in documents) {
                    val reservation = doc.toObject(Reservation::class.java).copy(id = doc.id)
                    reservationsList.add(reservation)
                    Log.d("MyReservations", "Loaded reservation: ${reservation.id}, status: ${reservation.status}")
                }
                reservationsAdapter.notifyDataSetChanged()
                recyclerReservations.visibility = if (reservationsList.isEmpty()) View.GONE else View.VISIBLE
                tvNoReservations.visibility = if (reservationsList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Log.e("MyReservations", "Error loading reservations", e)
                Toast.makeText(requireContext(), "Error al cargar reservas", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showReservationDetails(reservation: Reservation) {
        try {
            val fragment = UserReservationFragment.newInstance(reservation)
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
        } catch (e: Exception) {
            Log.e("MyReservations", "Error navigating to UserReservationFragment", e)
            Toast.makeText(requireContext(), "Error al abrir detalles", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelReservation(reservation: Reservation) {
        if (reservation.status != "pending" && reservation.status != "accepted") {
            Toast.makeText(requireContext(), "No se puede cancelar esta reserva", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("reservations").document(reservation.id)
            .update("status", "cancelled")
            .addOnSuccessListener {
                // Update availableSpaces for accepted reservations
                if (reservation.status == "accepted") {
                    firestore.collection("parkings").document(reservation.parkingId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val availableSpaces = doc.getLong("availableSpaces")?.toInt() ?: 0
                            firestore.collection("parkings").document(reservation.parkingId)
                                .update("availableSpaces", availableSpaces + 1)
                                .addOnSuccessListener {
                                    Log.d("MyReservations", "Espacios disponibles actualizados: ${availableSpaces + 1}")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MyReservations", "Error updating availableSpaces", e)
                                    Toast.makeText(requireContext(), "Error al actualizar espacios", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("MyReservations", "Error getting parking", e)
                            Toast.makeText(requireContext(), "Error al obtener cochera", Toast.LENGTH_SHORT).show()
                        }
                }

                // Notify owner
                firestore.collection("profiles").document(auth.currentUser?.uid ?: return@addOnSuccessListener).get()
                    .addOnSuccessListener { doc ->
                        val requesterName = doc.getString("name") ?: doc.getString("email") ?: "Anónimo"
                        firestore.collection("parkings").document(reservation.parkingId).get()
                            .addOnSuccessListener { parkingDoc ->
                                val parkingTitle = parkingDoc.getString("title") ?: "Cochera"
                                sendNotification(
                                    reservation.ownerId,
                                    "Reserva cancelada",
                                    "$requesterName canceló la reserva para $parkingTitle por ${reservation.hours} horas.",
                                    reservation.id,
                                    requesterName
                                )
                                Toast.makeText(requireContext(), "Reserva cancelada", Toast.LENGTH_SHORT).show()
                                loadReservations() // Refresh the list
                            }
                            .addOnFailureListener { e ->
                                Log.e("MyReservations", "Error getting parking title", e)
                                Toast.makeText(requireContext(), "Error al obtener título de cochera", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MyReservations", "Error getting profile", e)
                        Toast.makeText(requireContext(), "Error al obtener perfil", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("MyReservations", "Error cancelling reservation", e)
                Toast.makeText(requireContext(), "Error al cancelar reserva", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendNotification(userId: String, title: String, message: String, reservationId: String?, requesterName: String?) {
        val notification = hashMapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "timestamp" to Date(),
            "read" to false,
            "reservationId" to reservationId,
            "requesterName" to requesterName
        )
        firestore.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d("MyReservations", "Notificación enviada: $title")
            }
            .addOnFailureListener { e ->
                Log.e("MyReservations", "Error sending notification", e)
                Toast.makeText(requireContext(), "Error al enviar notificación", Toast.LENGTH_SHORT).show()
            }
    }
}

class ReservationsAdapter(
    private val reservations: List<Reservation>,
    private val onAction: (Reservation, String) -> Unit
) : RecyclerView.Adapter<ReservationsAdapter.ReservationViewHolder>() {

    class ReservationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvParkingTitle: TextView = itemView.findViewById(R.id.tvParkingTitle)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvHours: TextView = itemView.findViewById(R.id.tvHours)
        val tvArrivalTime: TextView = itemView.findViewById(R.id.tvArrivalTime)
        val tvPaymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
        val tvTotalAmount: TextView = itemView.findViewById(R.id.tvTotalAmount)
        val btnDetails: Button = itemView.findViewById(R.id.btnDetails)
        val btnCancel: Button = itemView.findViewById(R.id.btnCancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reservation, parent, false)
        return ReservationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        val reservation = reservations[position]
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Load parking title
        FirebaseFirestore.getInstance().collection("parkings").document(reservation.parkingId).get()
            .addOnSuccessListener { doc ->
                val title = doc.getString("title") ?: "Cochera"
                holder.tvParkingTitle.text = "Cochera: $title"
            }
            .addOnFailureListener { e ->
                Log.e("ReservationsAdapter", "Error loading parking title", e)
                holder.tvParkingTitle.text = "Cochera: Desconocida"
            }

        holder.tvStatus.text = "Estado: ${when (reservation.status) {
            "pending" -> "Pendiente"
            "accepted" -> "Aceptada"
            "rejected" -> "Rechazada"
            "cancelled" -> "Cancelada"
            "completed" -> "Completada"
            else -> reservation.status
        }}"
        holder.tvHours.text = "Horas: ${reservation.hours}"
        holder.tvArrivalTime.text = reservation.arrivalTime?.let {
            "Llegada: ${sdf.format(it)}"
        } ?: "Llegada: No especificada"
        holder.tvPaymentMethod.text = "Método de Pago: ${reservation.paymentMethod.takeIf { it.isNotEmpty() } ?: "No especificado"}"
        holder.tvTotalAmount.text = "Total: $${reservation.totalAmount}"

        holder.btnDetails.setOnClickListener {
            onAction(reservation, "details")
        }

        holder.btnCancel.setOnClickListener {
            onAction(reservation, "cancel")
        }

        // Disable cancel button for non-cancelable states
        holder.btnCancel.isEnabled = reservation.status == "pending" || reservation.status == "accepted"
    }

    override fun getItemCount(): Int = reservations.size
}