package com.example.parqueate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

data class Reservation(
    val id: String = "",
    val userId: String = "",
    val parkingId: String = "",
    val ownerId: String = "",
    val hours: Int = 0,
    val arrivalTime: Date? = null,
    val timestamp: Date? = null,
    val status: String = "",
    val paymentMethod: String = "",
    val totalAmount: Double = 0.0
) : Serializable

class ReservationDetailFragment : Fragment() {

    private lateinit var reservation: Reservation
    private lateinit var tvUserName: TextView
    private lateinit var userImageView: ImageView
    private lateinit var tvParkingTitle: TextView
    private lateinit var tvHours: TextView
    private lateinit var tvArrivalTime: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var btnAccept: Button
    private lateinit var btnReject: Button
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        fun newInstance(reservation: Reservation): ReservationDetailFragment {
            val fragment = ReservationDetailFragment()
            val args = Bundle()
            args.putSerializable("reservation", reservation)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reservation = arguments?.getSerializable("reservation") as? Reservation
            ?: throw IllegalStateException("Reservation required")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reservation_detail, container, false)

        tvUserName = view.findViewById(R.id.tvUserName)
        userImageView = view.findViewById(R.id.userImageView)
        tvParkingTitle = view.findViewById(R.id.tvParkingTitle)
        tvHours = view.findViewById(R.id.tvHours)
        tvArrivalTime = view.findViewById(R.id.tvArrivalTime)
        tvPaymentMethod = view.findViewById(R.id.tvPaymentMethod)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        btnAccept = view.findViewById(R.id.btnAccept)
        btnReject = view.findViewById(R.id.btnReject)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load user profile
        firestore.collection("profiles").document(reservation.userId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: doc.getString("email") ?: "Anónimo"
                val imageUrl = doc.getString("imageUrl") ?: ""
                tvUserName.text = "Solicitante: $name"
                if (imageUrl.isNotEmpty()) {
                    Glide.with(requireContext()).load(imageUrl).into(userImageView)
                } else {
                    userImageView.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
            .addOnFailureListener {
                tvUserName.text = "Solicitante: Error al cargar"
                userImageView.setImageResource(R.drawable.ic_profile_placeholder)
                Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show()
            }

        // Load parking title
        firestore.collection("parkings").document(reservation.parkingId).get()
            .addOnSuccessListener { doc ->
                val title = doc.getString("title") ?: "Cochera"
                tvParkingTitle.text = "Cochera: $title"
            }
            .addOnFailureListener {
                tvParkingTitle.text = "Cochera: Error al cargar"
                Toast.makeText(requireContext(), "Error al cargar cochera", Toast.LENGTH_SHORT).show()
            }

        tvHours.text = "Horas: ${reservation.hours}"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        tvArrivalTime.text = if (reservation.arrivalTime != null) {
            "Llegada: ${sdf.format(reservation.arrivalTime)}"
        } else {
            "Llegada: No especificada"
        }
        tvPaymentMethod.text = "Método de Pago: ${reservation.paymentMethod}"
        tvTotalAmount.text = "Total: $${reservation.totalAmount}"

        if (reservation.status != "pending") {
            btnAccept.isEnabled = false
            btnReject.isEnabled = false
        }

        btnAccept.setOnClickListener {
            handleReservationAction("accepted")
        }

        btnReject.setOnClickListener {
            handleReservationAction("rejected")
        }
    }

    private fun handleReservationAction(status: String) {
        firestore.collection("reservations").document(reservation.id)
            .update("status", status)
            .addOnSuccessListener {
                if (status == "accepted") {
                    firestore.collection("parkings").document(reservation.parkingId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val availableSpaces = doc.getLong("availableSpaces")?.toInt() ?: 0
                            if (availableSpaces > 0) {
                                firestore.collection("parkings").document(reservation.parkingId)
                                    .update("availableSpaces", availableSpaces - 1)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Error al actualizar espacios", Toast.LENGTH_SHORT).show()
                        }
                }

                // Notify renter
                firestore.collection("profiles").document(reservation.ownerId).get()
                    .addOnSuccessListener { doc ->
                        val ownerName = doc.getString("name") ?: doc.getString("email") ?: "Propietario"
                        val message = if (status == "accepted") {
                            "Tu reserva para ${tvParkingTitle.text} fue aceptada por $ownerName."
                        } else {
                            "Tu reserva para ${tvParkingTitle.text} fue rechazada por $ownerName."
                        }
                        sendNotification(
                            reservation.userId,
                            "Estado de Reserva",
                            message,
                            reservation.id,
                            ownerName
                        )
                        Toast.makeText(requireContext(), "Reserva $status", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Error al cargar perfil del propietario", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al actualizar reserva", Toast.LENGTH_SHORT).show()
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
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al enviar notificación", Toast.LENGTH_SHORT).show()
            }
    }
}