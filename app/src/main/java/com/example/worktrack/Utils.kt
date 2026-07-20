package com.example.worktrack

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Long.formatDate(locale: Locale = Locale.getDefault()): String = 
    SimpleDateFormat("dd.MM.yyyy", locale).format(Date(this))

fun Long.formatNumber(locale: Locale = Locale.getDefault()): String = 
    "%,d".format(locale, this).replace(',', ' ').replace('.', ' ')

fun Long.money(locale: Locale = Locale.getDefault()): String = "${formatNumber(locale)} \u20AC"

fun todayMillis(): Long = System.currentTimeMillis().startOfDay()

fun Long.startOfDay(): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@startOfDay }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun Long.endOfDay(): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@endOfDay }
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    return calendar.timeInMillis
}
