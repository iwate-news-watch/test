package com.example.taskmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority { LOW, MEDIUM, HIGH }

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: Long? = null,
    val reminderOffsetMinutes: Int? = null,
    val snoozedUntil: Long? = null,
    val parentTaskId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

fun Task.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    !isCompleted && dueDate != null && dueDate < now

fun Task.reminderTriggerAt(): Long? {
    snoozedUntil?.let { return it }
    val due = dueDate ?: return null
    val offset = reminderOffsetMinutes ?: return null
    return due - offset * 60_000L
}
