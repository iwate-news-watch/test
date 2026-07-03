package com.example.taskmanager.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: CredentialsRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: CredentialsRequest): AuthResponse
}
