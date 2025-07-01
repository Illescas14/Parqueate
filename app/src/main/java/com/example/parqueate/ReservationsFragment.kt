package com.example.parqueate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ReservationsFragment : Fragment() {

    private lateinit var parking: Parking
    private lateinit var spinnerPaymentMethod: Spinner
    private lateinit var etHours: EditText
    private lateinit var etArrivalTime: EditText
    private lateinit var btnConfirmReservation: Button
    private lateinit var tvTotalCost: TextView
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        fun newInstance(parking: Parking): ReservationsFragment {
            val fragment = ReservationsFragment()
            val args = Bundle()
            args.putSerializable("parking", parking)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parking = arguments?.getSerializable("parking") as Parking?
            ?: throw IllegalStateException("Parking required")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reservations, container, false)

        spinnerPaymentMethod = view.findViewById(R.id.spinnerPaymentMethod)
        etHours = view.findViewById(R.id.etHours)
        etArrivalTime = view.findViewById(R.id.etArrivalTime)
        btnConfirmReservation = view.findViewById(R.id.btnConfirmReservation)
        tvTotalCost = view.findViewById(R.id.tvTotalCost)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup payment method spinner
        val paymentMethods = arrayOf("Tarjeta", "Efectivo")
        spinnerPaymentMethod.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, paymentMethods)

        // Update total cost when hours change
        etHours.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateTotalCost()
            }
        })

        // Setup arrival time picker
        etArrivalTime.setOnClickListener {
            showTimePicker()
        }

        // Handle reservation confirmation
        btnConfirmReservation.setOnClickListener {
            handleReservation()
        }

        updateTotalCost()
    }

    private fun updateTotalCost() {
        val hoursStr = etHours.text.toString()
        if (hoursStr.isNotEmpty()) {
            val hours = hoursStr.toIntOrNull()
            if (hours != null && hours > 0) {
                val total = hours * parking.pricePerHour
                tvTotalCost.text = "Total: $${total} por $hours horas"
            } else {
                tvTotalCost.text = "Total: $0"
            }
        } else {
            tvTotalCost.text = "Total: $0"
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePicker = android.app.TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                val time = String.format("%02d:%02d", selectedHour, selectedMinute)
                etArrivalTime.setText(time)
            },
            hour,
            minute,
            true
        )
        timePicker.show()
    }

    private fun handleReservation() {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Debes iniciar sesión para reservar", Toast.LENGTH_SHORT).show()
            return
        }

        val hoursStr = etHours.text.toString()
        val hours = hoursStr.toIntOrNull()
        val arrivalTime = etArrivalTime.text.toString()
        val paymentMethod = spinnerPaymentMethod.selectedItem.toString()

        if (hours == null || hours <= 0) {
            Toast.makeText(requireContext(), "Ingresa un número válido de horas", Toast.LENGTH_SHORT).show()
            return
        }

        if (arrivalTime.isEmpty()) {
            Toast.makeText(requireContext(), "Selecciona una hora de llegada", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse arrival time
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val arrivalDate = try {
            sdf.parse(arrivalTime)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Formato de hora inválido", Toast.LENGTH_SHORT).show()
            return
        }
        val calendar = Calendar.getInstance()
        val currentTime = calendar.time
        calendar.time = arrivalDate
        calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR))
        calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH))
        calendar.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
        if (calendar.time.before(currentTime)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Get user profile for requester name
        firestore.collection("profiles").document(userId).get()
            .addOnSuccessListener { doc ->
                val requesterName = doc.getString("name") ?: doc.getString("email") ?: "Anónimo"
                val reservation = hashMapOf(
                    "userId" to userId,
                    "parkingId" to parking.id,
                    "ownerId" to parking.userId,
                    "hours" to hours,
                    "arrivalTime" to calendar.time,
                    "timestamp" to Date(),
                    "status" to "pending",
                    "paymentMethod" to paymentMethod,
                    "totalAmount" to (hours * parking.pricePerHour)
                )

                firestore.collection("reservations").add(reservation)
                    .addOnSuccessListener { doc ->
                        val reservationId = doc.id
                        // Notify owner
                        sendNotification(
                            parking.userId,
                            "Nueva solicitud de reserva",
                            "$requesterName solicitó reservar ${parking.title} por $hours horas.",
                            reservationId,
                            requesterName
                        )
                        // Notify renter
                        sendNotification(
                            userId,
                            "Reserva enviada",
                            "Tu solicitud para ${parking.title} por $hours horas ha sido enviada.",
                            reservationId,
                            requesterName
                        )
                        Toast.makeText(requireContext(), "Solicitud de reserva enviada", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Error al enviar la solicitud", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show()
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
    }
}