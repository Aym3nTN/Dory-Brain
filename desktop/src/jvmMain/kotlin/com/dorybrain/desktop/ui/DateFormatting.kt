package com.dorybrain.desktop.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

fun timeOfDay(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

/** "Today" / "Yesterday" / "MMM d, yyyy" for the day separators. */
fun dayLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }

    if (sameDay(now, then)) return "Today"

    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Yesterday"

    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}

fun fullTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val time = timeOfDay(timestamp)

    if (sameDay(now, then)) return "Today, $time"

    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Yesterday, $time"

    return SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(timestamp))
}
