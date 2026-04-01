package com.example.renthouseapp

import java.time.LocalDate
import java.util.UUID

data class PaymentRecord(
    val id: String = UUID.randomUUID().toString(),
    val periodNumber: Int,
    val amount: Int,
    // 需求1：账单对应的起止日期
    val periodStartDate: LocalDate,
    val periodEndDate: LocalDate,
    val dueDate: LocalDate,
    val reminderDate: LocalDate,
    val isPaid: Boolean = false,
    // 需求2：备查证信息
    val payee: String? = null,
    val paymentMethod: String? = null,
    val receiptDate: LocalDate? = null
)

data class Rental(
    val id: String = UUID.randomUUID().toString(),
    val propertyName: String,
    val tenantName: String,
    val tenantPhone: String,
    val tenantIdCard: String, // 需求6：改为证件号，非必填
    val contractDate: LocalDate,
    val rentStartDate: LocalDate,
    val monthlyRent: Int,
    val leaseMonths: Int,
    val paymentFrequency: Int,
    val paymentSchedule: List<PaymentRecord> = generateSchedule(rentStartDate, monthlyRent, leaseMonths, paymentFrequency)
) {
    val totalAmount: Int get() = monthlyRent * paymentFrequency
    val nextPaymentDate: LocalDate? get() = paymentSchedule.firstOrNull { !it.isPaid }?.dueDate
    val reminderDate: LocalDate? get() = nextPaymentDate?.minusDays(15)
    val isCompleted: Boolean get() = paymentSchedule.isNotEmpty() && paymentSchedule.all { it.isPaid }

    companion object {
        fun generateSchedule(start: LocalDate, rent: Int, months: Int, freq: Int): List<PaymentRecord> {
            val schedule = mutableListOf<PaymentRecord>()
            // 需求5：如果 freq == 0，代表一次性付清，实际频率就是整个租赁周期
            val actualFreq = if (freq == 0) months else freq
            val totalPeriods = if (actualFreq > 0) months / actualFreq else 1
            val amountPerPeriod = rent * actualFreq

            for (i in 0 until totalPeriods) {
                val periodStart = start.plusMonths((i * actualFreq).toLong())
                val periodEnd = start.plusMonths(((i + 1) * actualFreq).toLong()).minusDays(1)

                schedule.add(
                    PaymentRecord(
                        periodNumber = i + 1,
                        amount = amountPerPeriod,
                        periodStartDate = periodStart,
                        periodEndDate = periodEnd,
                        dueDate = periodStart,
                        reminderDate = periodStart.minusDays(15)
                    )
                )
            }
            return schedule
        }
    }
}