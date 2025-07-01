package com.example.parqueate

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class OtpInputDialogFragment(
    private val phoneNumber: String,
    private val verificationId: String,
    private val onOtpEntered: (String) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_otp_input, container, false)

        // Configurar el mensaje con el número parcial
        val otpSentMessage = view.findViewById<TextView>(R.id.otpSentMessage)
        val maskedNumber = maskPhoneNumber(phoneNumber)
        otpSentMessage.text = "Hemos enviado un código al número $maskedNumber"

        // Referencias a los campos OTP
        val otpDigit1 = view.findViewById<EditText>(R.id.otpDigit1)
        val otpDigit2 = view.findViewById<EditText>(R.id.otpDigit2)
        val otpDigit3 = view.findViewById<EditText>(R.id.otpDigit3)
        val otpDigit4 = view.findViewById<EditText>(R.id.otpDigit4)
        val otpDigit5 = view.findViewById<EditText>(R.id.otpDigit5)
        val otpDigit6 = view.findViewById<EditText>(R.id.otpDigit6)
        val verifyButton = view.findViewById<Button>(R.id.verifyOtpButton)

        // Auto-foco entre campos
        setupOtpInput(otpDigit1, otpDigit2)
        setupOtpInput(otpDigit2, otpDigit3)
        setupOtpInput(otpDigit3, otpDigit4)
        setupOtpInput(otpDigit4, otpDigit5)
        setupOtpInput(otpDigit5, otpDigit6)

        // Acción del botón "Verificar"
        verifyButton.setOnClickListener {
            val otp = "${otpDigit1.text}${otpDigit2.text}${otpDigit3.text}${otpDigit4.text}${otpDigit5.text}${otpDigit6.text}"
            if (otp.length != 6) {
                otpDigit1.error = "Ingresa el código completo"
                return@setOnClickListener
            }
            onOtpEntered(otp)
            dismiss()
        }

        return view
    }

    private fun maskPhoneNumber(phone: String): String {
        // Ejemplo: +521234567890 -> +52****7890
        if (phone.length < 10) return phone
        val lastFour = phone.takeLast(4)
        val countryCode = phone.substring(0, 3) // ej. +52
        return "$countryCode****$lastFour"
    }

    private fun setupOtpInput(current: EditText, next: EditText?) {
        current.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) {
                    next?.requestFocus()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}