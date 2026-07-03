package com.example.taskmanager.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.taskmanager.data.Task

object ReminderScheduler {
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"

    // Only these lead times are selectable in the UI, so it's safe to treat
    // this as the full universe of alarms that might need cancelling for a
    // task, even without knowing what was previously scheduled.
    val KNOWN_OFFSETS = listOf(0, 10, 30, 60, 1440)
    const val SNOOZE_SLOT = -1

    fun schedule(context: Context, task: Task) {
        cancelAll(context, task.id)

        val snoozeAt = task.snoozedUntil
        if (snoozeAt != null) {
            if (snoozeAt > System.currentTimeMillis()) {
                scheduleOne(context, task.id, SNOOZE_SLOT, snoozeAt)
            }
            return
        }

        val due = task.dueDate ?: return
        task.reminderOffsetsMinutes.forEach { offset ->
            val triggerAt = due - offset * 60_000L
            if (triggerAt > System.currentTimeMillis()) {
                scheduleOne(context, task.id, offset, triggerAt)
            }
        }
    }

    fun cancelAll(context: Context, taskId: String) {
        (KNOWN_OFFSETS + SNOOZE_SLOT).forEach { offset -> cancelOne(context, taskId, offset) }
    }

    private fun scheduleOne(context: Context, taskId: String, offsetMinutes: Int, triggerAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context, taskId, offsetMinutes)
        )
    }

    private fun cancelOne(context: Context, taskId: String, offsetMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, taskId, offsetMinutes))
    }

    fun requestCode(taskId: String, offsetMinutes: Int): Int = taskId.hashCode() * 10_000 + offsetMinutes

    private fun pendingIntent(context: Context, taskId: String, offsetMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_OFFSET_MINUTES, offsetMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, offsetMinutes),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
