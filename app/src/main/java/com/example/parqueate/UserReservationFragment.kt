package com.example.parqueate


import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class UserReservationFragment : Fragment() {

    private var reservation: Reservation? = null
    private lateinit var tvParkingTitle: TextView
    private lateinit var tvOwnerName: TextView
    private lateinit var ownerImageView: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvHours: TextView
    private lateinit var tvArrivalTime: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var btnCancel: Button
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        fun newInstance(reservation: Reservation): UserReservationFragment {
            val fragment = UserReservationFragment()
            val args = Bundle().apply {
                putSerializable("reservation", reservation)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            reservation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arguments?.getSerializable("reservation", Reservation::class.java)
            } else {
                @Suppress("DEPRECATION")
                arguments?.getSerializable("reservation") as? Reservation
            }
            if (reservation == null) {
                Log.e("UserReservation", "Reservation is null or invalid")
            }
        } catch (e: Exception) {
            Log.e("UserReservation", "Error deserializing reservation", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = try {
            inflater.inflate(R.layout.fragment_user_reservation, container, false)
        } catch (e: Exception) {
            Log.e("UserReservation", "Error inflating layout", e)
            Toast.makeText(requireContext(), "Error en la interfaz", Toast.LENGTH_SHORT).show()
            return null
        }

        try {
            tvParkingTitle = view.findViewById(R.id.tvParkingTitle)
            tvOwnerName = view.findViewById(R.id.tvOwnerName)
            ownerImageView = view.findViewById(R.id.ownerImageView)
            tvStatus = view.findViewById(R.id.tvStatus)
            tvHours = view.findViewById(R.id.tvHours)
            tvArrivalTime = view.findViewById(R.id.tvArrivalTime)
            tvPaymentMethod = view.findViewById(R.id.tvPaymentMethod)
            tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
            btnCancel = view.findViewById(R.id.btnCancel)
        } catch (e: Exception) {
            Log.e("UserReservation", "Error initializing views", e)
            Toast.makeText(requireContext(), "Error en la interfaz", Toast.LENGTH_SHORT).show()
            return null
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reservation = reservation ?: run {
            Log.e("UserReservation", "No reservation data available")
            Toast.makeText(requireContext(), "No hay datos de reserva", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        // Load parking title
        firestore.collection("parkings").document(reservation.parkingId).get()
            .addOnSuccessListener { doc ->
                val title = doc.getString("title") ?: "Cochera"
                tvParkingTitle.text = "Cochera: $title"
            }
            .addOnFailureListener { e ->
                Log.e("UserReservation", "Error loading parking", e)
                tvParkingTitle.text = "Cochera: Error al cargar"
            }

        // Load owner profile
        firestore.collection("profiles").document(reservation.ownerId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: doc.getString("email") ?: "Propietario"
                val imageUrl = doc.getString("imageUrl") ?: ""
                tvOwnerName.text = "Propietario: $name"
                if (imageUrl.isNotEmpty()) {
                    Glide.with(requireContext()).load(imageUrl).into(ownerImageView)
                } else {
                    ownerImageView.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserReservation", "Error loading profile", e)
                tvOwnerName.text = "Propietario: Error al cargar"
                ownerImageView.setImageResource(R.drawable.ic_profile_placeholder)
            }

        tvStatus.text = "Estado: ${when (reservation.status) {
            "pending" -> "Pendiente"
            "accepted" -> "Aceptada"
            "rejected" -> "Rechazada"
            "cancelled" -> "Cancelada"
            "completed" -> "Completada"
            else -> reservation.status
        }}"
        tvHours.text = "Horas: ${reservation.hours}"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        tvArrivalTime.text = reservation.arrivalTime?.let { "Llegada: ${sdf.format(it)}" } ?: "Llegada: No especificada"
        tvPaymentMethod.text = "Método de Pago: ${reservation.paymentMethod.takeIf { it.isNotEmpty() } ?: "No especificado"}"
        tvTotalAmount.text = "Total: $${reservation.totalAmount}"

        // Configure cancel button
        btnCancel.visibility = if (reservation.status == "pending" || reservation.status == "accepted") {
            View.VISIBLE
        } else {
            View.GONE
        }
        btnCancel.setOnClickListener {
            cancelReservation(reservation)
        }
    }

    private fun cancelReservation(reservation: Reservation) {
        firestore.collection("reservations").document(reservation.id)
            .update("status", "cancelled")
            .addOnSuccessListener {
                if (reservation.status == "accepted") {
                    firestore.collection("parkings").document(reservation.parkingId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val availableSpaces = doc.getLong("availableSpaces")?.toInt() ?: 0
                            firestore.collection("parkings").document(reservation.parkingId)
                                .update("availableSpaces", availableSpaces + 1)
                        }
                }

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
                                parentFragmentManager.popBackStack()
                            }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("UserReservation", "Error cancelling reservation", e)
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
                Log.d("UserReservation", "Notification sent: $title")
            }
            .addOnFailureListener { e ->
                Log.e("UserReservation", "Error sending notification", e)
                Toast.makeText(requireContext(), "Error al enviar notificación", Toast.LENGTH_SHORT).show()
            }
    }
}