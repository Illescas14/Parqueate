package com.example.parqueate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout

class PhoneInputDialogFragment(private val onPhoneEntered: (String) -> Unit) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_phone_input, container, false)

        val phoneInputLayout = view.findViewById<TextInputLayout>(R.id.phoneInputLayout)
        val phoneInput = view.findViewById<EditText>(R.id.phoneInput)
        val sendButton = view.findViewById<Button>(R.id.sendPhoneButton)

        sendButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (phoneNumber.isEmpty() || !phoneNumber.startsWith("+")) {
                phoneInputLayout.error = "Ingresa un número válido con código de país (ej. +52...)"
                return@setOnClickListener
            }
            onPhoneEntered(phoneNumber)
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}