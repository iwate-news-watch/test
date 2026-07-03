package com.example.taskmanager.data

import android.content.Context
import com.example.taskmanager.data.remote.ApiClient
import com.example.taskmanager.data.remote.CompletedRequest
import com.example.taskmanager.data.remote.CreateTaskRequest
import com.example.taskmanager.data.remote.SnoozeRequest
import com.example.taskmanager.data.remote.UpdateTaskRequest

class TaskRepository(context: Context) {
    private val taskApi = ApiClient.taskApi(context)

    suspend fun listTasks(): List<Task> = taskApi.listTasks()

    suspend fun getTask(id: String): Task = taskApi.getTask(id)

    suspend fun listSubtasks(parentId: String): List<Task> = taskApi.listSubtasks(parentId)

    suspend fun createTask(
        title: String,
        description: String = "",
        priority: Priority = Priority.MEDIUM,
        dueDate: Long? = null,
        reminderOffsetMinutes: Int? = null,
        parentTaskId: String? = null
    ): Task = taskApi.createTask(
        CreateTaskRequest(
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate,
            reminderOffsetMinutes = reminderOffsetMinutes,
            parentTaskId = parentTaskId
        )
    )

    suspend fun updateTask(
        id: String,
        title: String,
        description: String,
        priority: Priority,
        dueDate: Long?,
        reminderOffsetMinutes: Int?
    ): Task = taskApi.updateTask(id, UpdateTaskRequest(title, description, priority, dueDate, reminderOffsetMinutes))

    suspend fun setCompleted(id: String, completed: Boolean): Task =
        taskApi.setCompleted(id, CompletedRequest(completed))

    suspend fun snooze(id: String, snoozedUntil: Long?): Task =
        taskApi.snooze(id, SnoozeRequest(snoozedUntil))

    suspend fun deleteTask(id: String) = taskApi.deleteTask(id)
}
