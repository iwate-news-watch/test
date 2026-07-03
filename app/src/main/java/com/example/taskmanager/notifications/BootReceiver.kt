package com.example.taskmanager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskmanager.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskRepository(context).listTasks()
                    .filter { !it.isCompleted && it.dueDate != null && it.reminderOffsetMinutes != null }
                    .forEach { ReminderScheduler.schedule(context, it) }
            } catch (e: Exception) {
                // Not logged in yet, or no network right after boot; reminders
                // will still get rescheduled next time the app is opened.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
