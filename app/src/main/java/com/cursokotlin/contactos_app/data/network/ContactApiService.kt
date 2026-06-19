package com.cursokotlin.contactos_app.data.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ContactApiService {

    @GET("api/users")
    suspend fun getContacts(): Response<ApiResponse<List<ContactApiModel>>>

    @GET("api/users/{id}")
    suspend fun getContact(@Path("id") id: Long): Response<ApiResponse<ContactApiModel>>

    @Multipart
    @POST("api/users")
    suspend fun createContact(
        @Part("name") name: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("email") email: RequestBody?,
        @Part image: MultipartBody.Part?
    ): Response<ApiResponse<ContactApiModel>>

    @Multipart
    @POST("api/users/{id}")
    suspend fun updateContact(
        @Path("id") id: Long,
        @Part("name") name: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("email") email: RequestBody?,
        @Part("_method") method: RequestBody,
        @Part image: MultipartBody.Part?
    ): Response<ApiResponse<ContactApiModel>>

    @DELETE("api/users/{id}")
    suspend fun deleteContact(@Path("id") id: Long): Response<Unit>
}