package com.example.taskmanager

import android.app.Application
import com.example.taskmanager.notifications.NotificationHelper

class TaskManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
