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
                    set(payment.reminderDate.year, payment.reminderDate.monthValue - 1, payment.reminderDate.dayOfMonth, 14, 0, 0)
                }

                // 1. 准备谷歌 Android 官方纯净版数据（所有手机都能懂的“普通话”）
                val baseValues = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, cal.timeInMillis)
                    put(CalendarContract.Events.DTEND, cal.timeInMillis + 30 * 60 * 1000)
                    put(CalendarContract.Events.TITLE, "💰 收租提醒：${rental.propertyName}")
                    put(CalendarContract.Events.DESCRIPTION,
                        "【第${payment.periodNumber}期】\n" +
                                "租客：${rental.tenantName} (${rental.tenantPhone})\n" +
                                "应收金额：￥${payment.amount}\n" +
                                "账期：${payment.periodStartDate} 至 ${payment.periodEndDate}")
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    put(CalendarContract.Events.HAS_ALARM, 1) // 官方标准：声明带有提醒
                }

                var uri: android.net.Uri? = null

                try {
                    // 2. 尝试小米专供版（方言）
                    val miuiValues = ContentValues(baseValues).apply {
                        put("need_alarm", 1)
                    }
                    uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, miuiValues)
                } catch (e: Exception) {
                    // 3. 拦截崩溃！如果是华为、荣耀、OV、三星，走到这里会被拦截
                    try {
                        // 迅速换回官方纯净版（普通话）再次插入，完美兼容！
                        uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, baseValues)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }

                // 4. 插入具体的提醒时间（14:00）
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
            val selectionArgs = arrayOf(calendarId.toString(), "💰 收租提醒：%")
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun addAlarmReminder(context: Context, eventId: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0) // 提前 0 分钟，也就是 14:00 准时

            // 🌟 核心兼容加强：使用 METHOD_ALARM (数值4)
            // 华为、荣耀和 OV 的日历底层看到这个标志，会尽可能使用“闹钟通道”来响铃，而不是普通的“通知通道”
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
                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
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