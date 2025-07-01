package com.example.parqueate

import android.location.Location
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

data class Parking(
    val id: String = "",
    @PropertyName("userId") val userId: String = "", // ID del usuario que creó la cochera
    @PropertyName("title") val title: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("pricePerHour") val pricePerHour: Double = 0.0,
    @PropertyName("spaces") val spaces: Int = 0,
    @PropertyName("availableSpaces") val availableSpaces: Int = 0,
    @PropertyName("latitude") val latitude: Double = 0.0,
    @PropertyName("longitude") val longitude: Double = 0.0,
    @PropertyName("imageUrls") val imageUrls: List<String> = emptyList(),
    @PropertyName("averageRating") val averageRating: Double = 0.0, // Promedio de calificaciones
    @PropertyName("reviewCount") val reviewCount: Int = 0 // Contador de reseñas
) : Serializable {
    fun distanceFromUserKm(userLocation: Location?): Double {
        return if (userLocation == null) Double.MAX_VALUE else {
            val result = FloatArray(1)
            Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                this.latitude, this.longitude,
                result
            )
            result[0] / 1000.0 // Metros a kilómetros
        }
    }
}