package com.example.parqueate

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class VerifyEmailActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private lateinit var resendEmailButton: Button
    private var timer: CountDownTimer? = null
    private val resendCooldown = 60_000L // 60 segundos en milisegundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        val continueButton: Button = findViewById(R.id.continueButton)
        resendEmailButton = findViewById(R.id.resendEmailButton)

        continueButton.setOnClickListener {
            checkEmailVerification()
        }

        resendEmailButton.setOnClickListener {
            resendEmailVerification()
        }

        // Iniciar el cronómetro al cargar la actividad
        startResendTimer()
    }

    private fun startResendTimer() {
        resendEmailButton.isEnabled = false
        resendEmailButton.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.darker_gray))
        timer?.cancel() // Cancelar cualquier cronómetro anterior
        timer = object : CountDownTimer(resendCooldown, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                resendEmailButton.text = "Reenviar correo (${secondsRemaining}s)"
            }

            override fun onFinish() {
                resendEmailButton.isEnabled = true
                resendEmailButton.setBackgroundTintList(ContextCompat.getColorStateList(this@VerifyEmailActivity, R.color.primary_blue))
                resendEmailButton.text = "Reenviar correo"
            }
        }.start()
    }

    private fun checkEmailVerification() {
        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (user.isEmailVerified) {
                    Toast.makeText(this, "Correo verificado. Continuando...", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, CreateProfileActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Por favor verifica tu correo antes de continuar.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Error al verificar el estado: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resendEmailVerification() {
        val user = auth.currentUser ?: return
        user.sendEmailVerification()
            .addOnSuccessListener {
                Toast.makeText(this, "Correo de verificación reenviado.", Toast.LENGTH_LONG).show()
                startResendTimer() // Reiniciar el cronómetro después de reenviar
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al reenviar correo: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel() // Cancelar el cronómetro al destruir la actividad
    }
}