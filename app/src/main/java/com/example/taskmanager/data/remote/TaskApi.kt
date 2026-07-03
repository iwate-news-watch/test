package com.example.taskmanager.data.remote

import com.example.taskmanager.data.Task
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface TaskApi {
    @GET("tasks")
    suspend fun listTasks(): List<Task>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Task

    @GET("tasks/{id}/subtasks")
    suspend fun listSubtasks(@Path("id") id: String): List<Task>

    @POST("tasks")
    suspend fun createTask(@Body body: CreateTaskRequest): Task

    @PATCH("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body body: UpdateTaskRequest): Task

    @PATCH("tasks/{id}")
    suspend fun setCompleted(@Path("id") id: String, @Body body: CompletedRequest): Task

    @PATCH("tasks/{id}")
    suspend fun snooze(@Path("id") id: String, @Body body: SnoozeRequest): Task

    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)
}
