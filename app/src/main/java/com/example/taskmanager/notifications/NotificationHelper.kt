package com.example.taskmanager.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.taskmanager.MainActivity
import com.example.taskmanager.R
import com.example.taskmanager.data.Task

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "タスクのリマインダー",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "設定した時刻にタスクの期限をお知らせします"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showReminder(context: Context, task: Task, offsetMinutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = ReminderScheduler.requestCode(task.id, offsetMinutes)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePendingIntent = actionPendingIntent(context, task.id, offsetMinutes, ReminderActionReceiver.ACTION_SNOOZE, 1)
        val completePendingIntent = actionPendingIntent(context, task.id, offsetMinutes, ReminderActionReceiver.ACTION_COMPLETE, 2)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(task.description.ifBlank { leadTimeLabel(offsetMinutes) })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(0, "10分後に再通知", snoozePendingIntent)
            .addAction(0, "完了にする", completePendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /** Dismisses every notification that could be showing for this task, across all lead times. */
    fun cancelAll(context: Context, taskId: String) {
        val manager = NotificationManagerCompat.from(context)
        (ReminderScheduler.KNOWN_OFFSETS + ReminderScheduler.SNOOZE_SLOT).forEach { offset ->
            manager.cancel(ReminderScheduler.requestCode(taskId, offset))
        }
    }

    private fun leadTimeLabel(offsetMinutes: Int): String = when (offsetMinutes) {
        0 -> "期限の時刻です"
        10 -> "期限の10分前です"
        30 -> "期限の30分前です"
        60 -> "期限の1時間前です"
        1440 -> "期限の前日です"
        else -> "期限が近づいています"
    }

    private fun actionPendingIntent(
        context: Context,
        taskId: String,
        offsetMinutes: Int,
        action: String,
        salt: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            ReminderScheduler.requestCode(taskId, offsetMinutes) * 10 + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
