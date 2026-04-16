package com.example.renthouseapp

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone

object CalendarHelper {

    fun syncAllUnpaidToCalendar(context: Context, rentals: List<UiRental>): Int {
        val calendarId = getDefaultCalendarId(context) ?: return -1
        clearOldRentEvents(context, calendarId)

        val today = LocalDate.now()
        var syncCount = 0

        for (rental in rentals) {
            val unpaidPayments = rental.paymentSchedule.filter { !it.isPaid }

            for (payment in unpaidPayments) {
                val description = buildString {
                    append("【第${payment.periodNumber}期】\n")
                    append("租客：${rental.tenantName}\n")
                    append("电话：${rental.tenantPhone}\n")
                    append("本期应收总金额：¥${payment.amount}\n")
                    append("账期：${payment.periodStartDate} 至 ${payment.periodEndDate}")
                }
                if (insertCalendarEvent(
                        context = context,
                        calendarId = calendarId,
                        title = "催租提醒：${rental.propertyName}",
                        description = description,
                        date = payment.reminderDate
                    )
                ) {
                    syncCount++
                }
            }

            val renewInquiryDate = rental.rentEndDate.minusMonths(1)
            if (!renewInquiryDate.isBefore(today)) {
                val renewDescription = buildString {
                    append("房源：${rental.propertyName}\n")
                    append("租客：${rental.tenantName}\n")
                    append("电话：${rental.tenantPhone}\n")
                    append("合同租期：${rental.rentStartDate} 至 ${rental.rentEndDate}\n")
                    append("请提前与租客确认是否续租。")
                }
                if (insertCalendarEvent(
                        context = context,
                        calendarId = calendarId,
                        title = "续租提醒：${rental.propertyName}",
                        description = renewDescription,
                        date = renewInquiryDate
                    )
                ) {
                    syncCount++
                }
            }
        }

        return syncCount
    }

    private fun insertCalendarEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String,
        date: LocalDate
    ): Boolean {
        val cal = Calendar.getInstance().apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val baseValues = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, cal.timeInMillis)
            put(CalendarContract.Events.DTEND, cal.timeInMillis + 30 * 60 * 1000)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val uri = try {
            val miuiValues = ContentValues(baseValues).apply {
                put("need_alarm", 1)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, miuiValues)
                ?: context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, baseValues)
        } catch (_: Exception) {
            try {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, baseValues)
            } catch (_: Exception) {
                null
            }
        }

        val eventId = uri?.lastPathSegment?.toLongOrNull() ?: return false
        addAlarmReminder(context, eventId)
        return true
    }

    private fun clearOldRentEvents(context: Context, calendarId: Long) {
        try {
            val selection =
                "${CalendarContract.Events.CALENDAR_ID} = ? AND (" +
                    "${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.TITLE} LIKE ?)"
            val selectionArgs = arrayOf(
                calendarId.toString(),
                "催租提醒：%",
                "续租提醒：%"
            )
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
