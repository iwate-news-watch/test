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
    val reminderOffsetsMinutes: List<Int> = emptyList(),
    val snoozedUntil: Long? = null,
    val isRecurring: Boolean = false,
    val parentTaskId: String? = null,
    val createdAt: Long = 0,
    val subtaskTotal: Int = 0,
    val subtaskCompleted: Int = 0
)

fun Task.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    !isCompleted && dueDate != null && dueDate < now

/** All upcoming reminder trigger times for this task. A snooze overrides the configured offsets. */
fun Task.reminderTriggerAts(): List<Long> {
    snoozedUntil?.let { return listOf(it) }
    val due = dueDate ?: return emptyList()
    return reminderOffsetsMinutes.map { offset -> due - offset * 60_000L }
}
