package com.cursokotlin.contactos_app.data.network

data class ContactApiModel(
    val id: Long = 0,
    val name: String = "",
    val email: String? = null,
    val phone: String = "",
    val created_at: String? = null,
    val updated_at: String? = null,
    // Viene del index (ligero)
    val image_count: Int? = null,
    val thumbnail: String? = null,
    // Viene del show (completo)
    val images: List<ImageApiModel>? = null
)

data class ImageApiModel(
    val id: Long = 0,
    val url: String = ""
)

data class ApiResponse<T>(
    val data: T
)