package com.example.parqueate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

data class Review(
    val id: String = "",
    val parkingId: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    val timestamp: Date = Date()
)

class ParkingDetailFragment : Fragment() {

    private lateinit var parking: Parking
    private lateinit var tvTitle: TextView
    private lateinit var tvOwner: TextView
    private lateinit var ownerImageView: ImageView
    private lateinit var tvPrice: TextView
    private lateinit var tvSpaces: TextView
    private lateinit var tvDescription: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingCount: TextView
    private lateinit var imageContainer: LinearLayout
    private lateinit var recyclerReviews: RecyclerView
    private lateinit var btnReserve: Button
    private lateinit var btnAddReview: Button
    private lateinit var etReviewComment: EditText
    private lateinit var ratingBarReview: RatingBar
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val reviewsList = mutableListOf<Review>()
    private lateinit var reviewsAdapter: ReviewsAdapter

    companion object {
        fun newInstance(parking: Parking): ParkingDetailFragment {
            val fragment = ParkingDetailFragment()
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
        val view = inflater.inflate(R.layout.fragment_parking_detail, container, false)

        tvTitle = view.findViewById(R.id.tvTitle)
        tvOwner = view.findViewById(R.id.tvOwner)
        ownerImageView = view.findViewById(R.id.ownerImageView)
        tvPrice = view.findViewById(R.id.tvPrice)
        tvSpaces = view.findViewById(R.id.tvSpaces)
        tvDescription = view.findViewById(R.id.tvDescription)
        ratingBar = view.findViewById(R.id.ratingBar)
        tvRatingCount = view.findViewById(R.id.tvRatingCount)
        imageContainer = view.findViewById(R.id.imageContainer)
        recyclerReviews = view.findViewById(R.id.recyclerReviews)
        btnReserve = view.findViewById(R.id.btnReserve)
        btnAddReview = view.findViewById(R.id.btnAddReview)
        etReviewComment = view.findViewById(R.id.etReviewComment)
        ratingBarReview = view.findViewById(R.id.ratingBarReview)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle.text = parking.title
        tvPrice.text = "$${parking.pricePerHour}/h"
        tvSpaces.text = "${parking.availableSpaces}/${parking.spaces} espacios disponibles"
        tvDescription.text = parking.description
        ratingBar.rating = parking.averageRating.toFloat()
        tvRatingCount.text = "(${parking.reviewCount} reseñas)"

        loadOwnerInfo()
        loadImages()
        setupReviews()
        setupReserveButton()
        setupAddReview()
    }

    private fun loadOwnerInfo() {
        firestore.collection("profiles").document(parking.userId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: doc.getString("email") ?: "Propietario desconocido"
                val imageUrl = doc.getString("imageUrl") ?: ""
                tvOwner.text = "Propietario: $name"
                if (imageUrl.isNotEmpty()) {
                    Glide.with(this).load(imageUrl).into(ownerImageView)
                } else {
                    ownerImageView.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
            .addOnFailureListener {
                tvOwner.text = "Propietario: Desconocido"
                ownerImageView.setImageResource(R.drawable.ic_profile_placeholder)
            }
    }

    private fun loadImages() {
        imageContainer.removeAllViews()
        parking.imageUrls.forEach { url ->
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                    setMargins(8, 8, 8, 8)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(this).load(url).into(this)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
            }
            imageContainer.addView(imageView)
        }
        if (parking.imageUrls.isEmpty()) {
            val placeholder = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                    setMargins(8, 8, 8, 8)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.ic_parking_placeholder)
            }
            imageContainer.addView(placeholder)
        }
    }

    private fun setupReviews() {
        recyclerReviews.layoutManager = LinearLayoutManager(requireContext())
        reviewsAdapter = ReviewsAdapter(reviewsList)
        recyclerReviews.adapter = reviewsAdapter

        firestore.collection("reviews")
            .whereEqualTo("parkingId", parking.id)
            .get()
            .addOnSuccessListener { documents ->
                reviewsList.clear()
                for (doc in documents) {
                    val review = doc.toObject(Review::class.java).copy(id = doc.id)
                    reviewsList.add(review)
                }
                reviewsAdapter.notifyDataSetChanged()
                recyclerReviews.visibility = if (reviewsList.isEmpty()) View.GONE else View.VISIBLE
                view?.findViewById<TextView>(R.id.tvNoReviews)?.visibility =
                    if (reviewsList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar reseñas", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupReserveButton() {
        btnReserve.setOnClickListener {
            val userId = auth.currentUser?.uid ?: run {
                Toast.makeText(requireContext(), "Debes iniciar sesión para reservar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (parking.availableSpaces <= 0) {
                Toast.makeText(requireContext(), "No hay espacios disponibles", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fragment = ReservationsFragment.newInstance(parking)
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
        }
    }

    private fun setupAddReview() {
        btnAddReview.setOnClickListener {
            val userId = auth.currentUser?.uid ?: run {
                Toast.makeText(requireContext(), "Debes iniciar sesión para reseñar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val comment = etReviewComment.text.toString().trim()
            val rating = ratingBarReview.rating

            if (comment.isEmpty() || rating == 0f) {
                Toast.makeText(requireContext(), "Ingresa un comentario y una calificación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firestore.collection("profiles").document(userId).get()
                .addOnSuccessListener { doc ->
                    val userName = doc.getString("name") ?: doc.getString("email") ?: "Anónimo"
                    val review = mapOf(
                        "parkingId" to parking.id,
                        "userId" to userId,
                        "userName" to userName,
                        "rating" to rating,
                        "comment" to comment,
                        "timestamp" to Date()
                    )

                    firestore.collection("reviews").add(review)
                        .addOnSuccessListener {
                            firestore.collection("reviews")
                                .whereEqualTo("parkingId", parking.id)
                                .get()
                                .addOnSuccessListener { docs ->
                                    val ratings = docs.documents.mapNotNull { it.getDouble("rating") }
                                    val averageRating = if (ratings.isNotEmpty()) ratings.average() else 0.0
                                    val reviewCount = ratings.size
                                    firestore.collection("parkings").document(parking.id)
                                        .update(
                                            "averageRating", averageRating,
                                            "reviewCount", reviewCount
                                        )
                                        .addOnSuccessListener {
                                            Toast.makeText(requireContext(), "Reseña enviada", Toast.LENGTH_SHORT).show()
                                            etReviewComment.setText("")
                                            ratingBarReview.rating = 0f
                                            setupReviews()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(requireContext(), "Error al actualizar calificación", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(requireContext(), "Error al cargar reseñas", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Error al enviar reseña", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun sendNotification(userId: String, title: String, message: String) {
        val notification = mapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "timestamp" to Date(),
            "read" to false
        )
        firestore.collection("notifications").add(notification)
    }
}

class ReviewsAdapter(
    private val reviews: List<Review>
) : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUser: TextView = itemView.findViewById(R.id.tvUser)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]
        holder.tvUser.text = review.userName
        holder.ratingBar.rating = review.rating
        holder.tvComment.text = review.comment
        holder.tvDate.text = android.text.format.DateFormat.format("dd/MM/yyyy", review.timestamp)
    }

    override fun getItemCount(): Int = reviews.size
}