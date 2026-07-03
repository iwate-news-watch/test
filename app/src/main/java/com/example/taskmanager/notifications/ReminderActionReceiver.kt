package com.example.taskmanager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskmanager.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = TaskDatabase.getInstance(context).taskDao()
                val task = dao.getTaskById(taskId) ?: return@launch
                when (intent.action) {
                    ACTION_SNOOZE -> {
                        val updated = task.copy(snoozedUntil = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
                        dao.updateTask(updated)
                        ReminderScheduler.schedule(context, updated)
                    }
                    ACTION_COMPLETE -> {
                        dao.updateTask(task.copy(isCompleted = true))
                        ReminderScheduler.cancel(context, task.id)
                    }
                }
                NotificationHelper.cancel(context, task.id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.example.taskmanager.ACTION_SNOOZE"
        const val ACTION_COMPLETE = "com.example.taskmanager.ACTION_COMPLETE"
        const val SNOOZE_MINUTES = 10
    }
}
