package com.example.taskmanager.auth

import android.content.Context
import com.example.taskmanager.data.remote.ApiClient
import com.example.taskmanager.data.remote.CredentialsRequest

class AuthRepository(private val context: Context) {
    private val authApi get() = ApiClient.authApi(context)

    suspend fun register(email: String, password: String) {
        val response = authApi.register(CredentialsRequest(email, password))
        TokenStore.save(context, response.token, response.user.email)
    }

    suspend fun login(email: String, password: String) {
        val response = authApi.login(CredentialsRequest(email, password))
        TokenStore.save(context, response.token, response.user.email)
    }

    fun logout() {
        TokenStore.clear(context)
    }

    fun isLoggedIn(): Boolean = TokenStore.getToken(context) != null

    fun currentEmail(): String? = TokenStore.getEmail(context)
}
