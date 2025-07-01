package com.example.parqueate

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Notification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Date? = null,
    val read: Boolean = false,
    val reservationId: String? = null,
    val requesterName: String? = null
)

class NotificationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationsAdapter
    private val notificationsList = mutableListOf<Notification>()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerNotifications)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = NotificationsAdapter(notificationsList) { notification ->
            markAsRead(notification)
            handleNotificationClick(notification)
        }
        recyclerView.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                notificationsList.clear()
                for (doc in result) {
                    val notification = doc.toObject(Notification::class.java).copy(id = doc.id)
                    notificationsList.add(notification)
                    Log.d("Notifications", "Loaded: ${notification.title}, reservationId: ${notification.reservationId}")
                }
                notificationsList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
                view?.findViewById<TextView>(R.id.tvNoNotifications)?.visibility =
                    if (notificationsList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (notificationsList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener { e ->
                Log.e("Notifications", "Error loading notifications", e)
                Toast.makeText(requireContext(), "Error al cargar notificaciones", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markAsRead(notification: Notification) {
        if (notification.read) return
        db.collection("notifications").document(notification.id)
            .update("read", true)
            .addOnSuccessListener {
                Log.d("Notifications", "Marked as read: ${notification.title}")
                loadNotifications()
            }
            .addOnFailureListener { e ->
                Log.e("Notifications", "Error marking notification as read", e)
                Toast.makeText(requireContext(), "Error al marcar como leída", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleNotificationClick(notification: Notification) {
        // Skip navigation for non-actionable notifications
        when (notification.title) {
            "Estado de reserva", "Reserva cancelada" -> {
                Log.d("Notifications", "Non-actionable notification clicked: ${notification.title}")
                Toast.makeText(requireContext(), "${notification.title} marcada como leída", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (notification.reservationId.isNullOrEmpty()) {
            Log.w("Notifications", "No reservationId for notification: ${notification.title}")
            Toast.makeText(requireContext(), "No hay detalles de reserva disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("reservations").document(notification.reservationId)
            .get()
            .addOnSuccessListener { doc ->
                val reservation = doc.toObject(Reservation::class.java)?.copy(id = doc.id)
                if (reservation == null) {
                    Log.w("Notifications", "Reservation not found for ID: ${notification.reservationId}")
                    Toast.makeText(requireContext(), "Reserva no encontrada", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                try {
                    val userId = auth.currentUser?.uid
                    val fragment = if (userId == reservation.ownerId && reservation.status == "pending") {
                        ReservationDetailFragment.newInstance(reservation)
                    } else {
                        UserReservationFragment.newInstance(reservation)
                    }

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
                    Log.e("Notifications", "Error navigating to fragment for ${notification.title}", e)
                    Toast.makeText(requireContext(), "Error al abrir detalles", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Notifications", "Error loading reservation: ${notification.reservationId}", e)
                Toast.makeText(requireContext(), "Error al cargar reserva", Toast.LENGTH_SHORT).show()
            }
    }
}

class NotificationsAdapter(
    private val notifications: List<Notification>,
    private val onClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.tvTitle.text = notification.title
        holder.tvMessage.text = if (notification.requesterName.isNullOrEmpty()) {
            notification.message
        } else {
            "${notification.requesterName}: ${notification.message}"
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.tvTimestamp.text = notification.timestamp?.let { sdf.format(it) } ?: "Sin fecha"

        notification.reservationId?.let { reservationId ->
            FirebaseFirestore.getInstance().collection("reservations").document(reservationId).get()
                .addOnSuccessListener { doc ->
                    val reservation = doc.toObject(Reservation::class.java)
                    if (reservation != null) {
                        FirebaseFirestore.getInstance().collection("parkings").document(reservation.parkingId).get()
                            .addOnSuccessListener { parkingDoc ->
                                val parkingTitle = parkingDoc.getString("title") ?: "Cochera"
                                holder.tvDetails.text = "Cochera: $parkingTitle, Horas: ${reservation.hours}"
                            }
                            .addOnFailureListener { e ->
                                Log.e("NotificationsAdapter", "Error loading parking details", e)
                                holder.tvDetails.text = "Detalles no disponibles"
                            }
                    } else {
                        holder.tvDetails.text = "Reserva no encontrada"
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("NotificationsAdapter", "Error loading reservation details", e)
                    holder.tvDetails.text = "Error al cargar detalles"
                }
        } ?: run {
            holder.tvDetails.text = "Sin detalles de reserva"
        }

        holder.itemView.setOnClickListener { onClick(notification) }
        holder.itemView.setBackgroundColor(
            if (notification.read) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
        )
    }

    override fun getItemCount(): Int = notifications.size
}