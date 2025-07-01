package com.example.parqueate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.parqueate.databinding.ItemIntroBinding

class IntroAdapter(private val items: List<IntroItem>) :
    RecyclerView.Adapter<IntroAdapter.IntroViewHolder>() {

    inner class IntroViewHolder(val binding: ItemIntroBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroViewHolder {
        val binding = ItemIntroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IntroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IntroViewHolder, position: Int) {
        val item = items[position]
        holder.binding.introImage.setImageResource(item.imageRes)
        holder.binding.introTitle.text = item.title
        holder.binding.introDescription.text = item.description
    }

    override fun getItemCount(): Int = items.size
}
