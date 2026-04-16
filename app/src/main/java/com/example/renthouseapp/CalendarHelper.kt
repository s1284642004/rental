package com.example.renthouseapp

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

object CalendarHelper {

    fun syncAllUnpaidToCalendar(context: Context, rentals: List<UiRental>): Int {
        val calendarId = getDefaultCalendarId(context) ?: return -1

        clearOldRentEvents(context, calendarId)

        var syncCount = 0
        val activeRentals = rentals.filter { !it.isCompleted }

        for (rental in activeRentals) {
            val unpaidPayments = rental.paymentSchedule.filter { !it.isPaid }

            for (payment in unpaidPayments) {
                val cal = Calendar.getInstance().apply {
                    set(
                        payment.reminderDate.year,
                        payment.reminderDate.monthValue - 1,
                        payment.reminderDate.dayOfMonth,
                        14,
                        0,
                        0
                    )
                }

                val baseValues = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, cal.timeInMillis)
                    put(CalendarContract.Events.DTEND, cal.timeInMillis + 30 * 60 * 1000)
                    put(CalendarContract.Events.TITLE, "收租提醒：${rental.propertyName}")
                    put(
                        CalendarContract.Events.DESCRIPTION,
                        "【第${payment.periodNumber}期】\n" +
                            "租客：${rental.tenantName}\n" +
                            "电话：${rental.tenantPhone}\n" +
                            "应收金额：￥${payment.amount}\n" +
                            "账期：${payment.periodStartDate} 至 ${payment.periodEndDate}"
                    )
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    put(CalendarContract.Events.HAS_ALARM, 1)
                }

                var uri: android.net.Uri? = null

                try {
                    val miuiValues = ContentValues(baseValues).apply {
                        put("need_alarm", 1)
                    }
                    uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, miuiValues)
                } catch (_: Exception) {
                    try {
                        uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, baseValues)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (uri != null) {
                    val eventId = uri.lastPathSegment?.toLong()
                    if (eventId != null) {
                        addAlarmReminder(context, eventId)
                        syncCount++
                    }
                }
            }
        }
        return syncCount
    }

    private fun clearOldRentEvents(context: Context, calendarId: Long) {
        try {
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} LIKE ?"
            val selectionArgs = arrayOf(calendarId.toString(), "收租提醒：%")
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun addAlarmReminder(context: Context, eventId: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALARM)
        }
        try {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        var calendarId: Long? = null

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(0)
                        val isPrimary = it.getInt(1)
                        if (isPrimary == 1) return id
                        if (calendarId == null) calendarId = id
                    } while (it.moveToNext())
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return calendarId
    }
}
