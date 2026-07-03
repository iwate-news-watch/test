package com.example.taskmanager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskmanager.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val offsetMinutes = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, ReminderScheduler.SNOOZE_SLOT)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = TaskRepository(context).getTask(taskId)
                if (!task.isCompleted) {
                    NotificationHelper.showReminder(context, task, offsetMinutes)
                }
            } catch (e: Exception) {
                // Task may have been deleted, or the device may be offline; either
                // way there's nothing useful to notify about right now.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
