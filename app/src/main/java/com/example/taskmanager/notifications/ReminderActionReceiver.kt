package com.example.taskmanager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskmanager.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TaskRepository(context)
                when (intent.action) {
                    ACTION_SNOOZE -> {
                        val updated = repository.snooze(taskId, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
                        ReminderScheduler.schedule(context, updated)
                    }
                    ACTION_COMPLETE -> {
                        repository.setCompleted(taskId, true)
                        ReminderScheduler.cancelAll(context, taskId)
                    }
                }
                NotificationHelper.cancelAll(context, taskId)
            } catch (e: Exception) {
                // Offline or the task is gone; nothing more we can do here.
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
