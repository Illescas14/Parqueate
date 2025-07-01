package com.example.parqueate

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.parqueate.databinding.ActivityIntroBinding
import android.os.Handler
import android.os.Looper
import java.util.*

class IntroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding
    private lateinit var adapter: IntroAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val introItems = listOf(
            IntroItem("Encuentra una cochera fácil", "Parquéate te conecta con cocheras disponibles en tu ciudad", R.drawable.ic_intro_1),
            IntroItem("Renta o publica cocheras", "Tú eliges si quieres buscar o rentar tu espacio", R.drawable.ic_intro_2),
            IntroItem("Confianza y seguridad", "Con perfiles verificados y ubicación exacta", R.drawable.ic_intro_3)
        )

        adapter = IntroAdapter(introItems)
        binding.viewPager.adapter = adapter

        // Auto-slide
        startAutoSlide(introItems.size)

        binding.buttonContinue.setOnClickListener {
            if (binding.viewPager.currentItem + 1 < introItems.size) {
                binding.viewPager.currentItem += 1
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun startAutoSlide(size: Int) {
        val runnable = object : Runnable {
            override fun run() {
                currentPage = (currentPage + 1) % size
                binding.viewPager.setCurrentItem(currentPage, true)
                handler.postDelayed(this, 3000) // cada 3 segundos
            }
        }
        handler.postDelayed(runnable, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

