package com.example.parqueate

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class ParqueateFirebaseMessagingService : FirebaseMessagingService() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Almacenar el token FCM en el perfil del usuario
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("profiles").document(userId)
            .update("fcmToken", token)
            .addOnFailureListener {
                // Manejar error (opcional)
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Las notificaciones son manejadas por FCM automáticamente
        // Si necesitas personalizar, puedes generar una notificación manual aquí
    }
}