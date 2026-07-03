package com.example.taskmanager.data

data class SubtaskCount(
    val parentTaskId: Long,
    val total: Int,
    val completedCount: Int
)
