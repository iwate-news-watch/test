package com.example.taskmanager.data

enum class Priority { LOW, MEDIUM, HIGH }

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val tag: String? = null,
    val dueDate: Long? = null,
    val reminderOffsetMinutes: Int? = null,
    val snoozedUntil: Long? = null,
    val parentTaskId: String? = null,
    val createdAt: Long = 0,
    val subtaskTotal: Int = 0,
    val subtaskCompleted: Int = 0
)

fun Task.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    !isCompleted && dueDate != null && dueDate < now

fun Task.reminderTriggerAt(): Long? {
    snoozedUntil?.let { return it }
    val due = dueDate ?: return null
    val offset = reminderOffsetMinutes ?: return null
    return due - offset * 60_000L
}
