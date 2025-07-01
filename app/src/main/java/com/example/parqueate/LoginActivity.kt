package com.example.parqueate

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var currentPhoneNumber: String? = null
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val googleSignInButton = findViewById<ImageButton>(R.id.googleSignInButton)
        val cellSignInButton = findViewById<ImageButton>(R.id.cellSignInButton)
        val goToRegister = findViewById<TextView>(R.id.goToRegister)
        val forgotPassword = findViewById<TextView>(R.id.forgotPassword)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Iniciando sesión..."
            Log.d(TAG, "Intentando iniciar sesión con email: $email")

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Log.d(TAG, "Inicio de sesión exitoso para $email")
                    handleLoginSuccess(auth.currentUser!!)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al iniciar sesión: ${exception.message}", exception)
                    Toast.makeText(this, "Error al iniciar sesión: ${exception.message}", Toast.LENGTH_LONG).show()
                    loginButton.isEnabled = true
                    loginButton.text = "Iniciar sesión"
                }
        }

        googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        cellSignInButton.setOnClickListener {
            showPhoneInputDialog()
        }

        goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        forgotPassword.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Escribe tu correo", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Revisa tu correo para recuperar tu contraseña", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al enviar el correo", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                firebaseAuthWithGoogle(task.result)
            } else {
                Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                handleLoginSuccess(auth.currentUser!!)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error con Firebase", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPhoneInputDialog() {
        val dialog = PhoneInputDialogFragment { phoneNumber ->
            currentPhoneNumber = phoneNumber
            verifyPhoneNumber(phoneNumber)
        }
        dialog.show(supportFragmentManager, "PhoneInputDialog")
    }

    fun verifyPhoneNumber(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@LoginActivity, "Verificación fallida: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    storedVerificationId = verificationId
                    resendToken = token
                    showOtpDialog()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun showOtpDialog() {
        val dialog = OtpInputDialogFragment(currentPhoneNumber!!, storedVerificationId!!) { otp ->
            val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, otp)
            signInWithPhoneCredential(credential)
        }
        dialog.show(supportFragmentManager, "OtpInputDialog")
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                handleLoginSuccess(auth.currentUser!!)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al iniciar sesión con teléfono", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleLoginSuccess(user: FirebaseUser) {
        val uid = user.uid
        Log.d(TAG, "Verificando perfil para UID: $uid")
        val db = FirebaseFirestore.getInstance()
        db.collection("profiles").document(uid).get()
            .addOnSuccessListener { document ->
                Log.d(TAG, "Perfil encontrado: ${document.exists()}")
                if (document.exists()) {
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    val intent = Intent(this, CreateProfileActivity::class.java)
                    intent.putExtra("isNewUser", true)
                    startActivity(intent)
                }
                finish()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al verificar perfil: ${exception.message}", exception)
                Toast.makeText(this, "Error al verificar perfil: ${exception.message}", Toast.LENGTH_LONG).show()
            }

        // Verificación de correo si aplica
        if (user.providerData.any { it.providerId == "password" || it.providerId == "email" } && !user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnSuccessListener {
                    Log.d(TAG, "Correo de verificación enviado a ${user.email}")
                    Toast.makeText(this, "Verifica tu correo en tu bandeja de entrada.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, VerifyEmailActivity::class.java))
                    finish()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al enviar verificación de correo: ${exception.message}", exception)
                    Toast.makeText(this, "Error al enviar verificación de correo", Toast.LENGTH_LONG).show()
                }
        }
    }
}