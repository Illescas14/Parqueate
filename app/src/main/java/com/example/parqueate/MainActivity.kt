package com.example.parqueate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Fragmento por defecto
        loadFragment(HomeFragment())

        // Listener de navegación
        bottomNavigationView.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_reservations -> MyReservationsFragment()
                R.id.nav_notifications -> NotificationsFragment()
                R.id.nav_add -> AddParkingFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.enter_from_right, // Entrada
                R.anim.exit_to_left,    // Salida
                R.anim.enter_from_left, // Entrada desde atrás
                R.anim.exit_to_right    // Salida hacia atrás
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}