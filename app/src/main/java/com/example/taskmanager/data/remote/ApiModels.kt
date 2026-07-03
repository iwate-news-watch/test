package com.example.taskmanager.data.remote

import com.example.taskmanager.data.Priority

data class CredentialsRequest(val email: String, val password: String)

data class AuthUser(val id: String, val email: String)

data class AuthResponse(val token: String, val user: AuthUser)

data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: Priority? = null,
    val dueDate: Long? = null,
    val reminderOffsetMinutes: Int? = null,
    val parentTaskId: String? = null
)

data class UpdateTaskRequest(
    val title: String,
    val description: String,
    val priority: Priority,
    val dueDate: Long?,
    val reminderOffsetMinutes: Int?
)

data class CompletedRequest(val isCompleted: Boolean)

data class SnoozeRequest(val snoozedUntil: Long?)
