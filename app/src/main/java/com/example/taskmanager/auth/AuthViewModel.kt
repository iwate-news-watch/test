package com.example.taskmanager.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application)

    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    val currentEmail: String?
        get() = repository.currentEmail()

    suspend fun login(email: String, password: String) {
        repository.login(email, password)
        _isLoggedIn.value = true
    }

    suspend fun register(email: String, password: String) {
        repository.register(email, password)
        _isLoggedIn.value = true
    }

    fun logout() {
        repository.logout()
        _isLoggedIn.value = false
    }
}
