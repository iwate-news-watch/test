package com.example.taskmanager.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast

private const val DEFAULT_EVENT_DURATION_MILLIS = 60 * 60 * 1000L

fun openInCalendar(
    context: Context,
    title: String,
    description: String,
    startMillis: Long,
    endMillis: Long = startMillis + DEFAULT_EVENT_DURATION_MILLIS
) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "カレンダーアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
    }
}
