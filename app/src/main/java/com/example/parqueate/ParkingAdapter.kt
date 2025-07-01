package com.example.parqueate
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.parqueate.Parking
import com.example.parqueate.R

class ParkingAdapter(
    private val parkings: List<Parking>,
    private val onClick: (Parking) -> Unit
) : RecyclerView.Adapter<ParkingAdapter.ParkingViewHolder>() {

    class ParkingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivParkingImage)
        val tvTitle: TextView = itemView.findViewById(R.id.tvParkingTitle)
        val tvPrice: TextView = itemView.findViewById(R.id.tvParkingPrice)
        val tvSpaces: TextView = itemView.findViewById(R.id.tvParkingSpaces)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parking, parent, false)
        return ParkingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParkingViewHolder, position: Int) {
        val parking = parkings[position]
        holder.tvTitle.text = parking.title
        holder.tvPrice.text = "$${parking.pricePerHour}/h"
        holder.tvSpaces.text = "${parking.availableSpaces}/${parking.spaces} espacios"
        if (parking.imageUrls.isNotEmpty()) {
            Glide.with(holder.ivImage.context)
                .load(parking.imageUrls[0])
                .placeholder(R.drawable.ic_parking_placeholder)
                .into(holder.ivImage)
        } else {
            holder.ivImage.setImageResource(R.drawable.ic_parking_placeholder)
        }
        holder.itemView.setOnClickListener { onClick(parking) }
    }

    override fun getItemCount(): Int = parkings.size
}