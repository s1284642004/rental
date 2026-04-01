package com.example.renthouseapp.model

import java.time.LocalDate
import java.util.UUID

// 你的原始账单逻辑，一行没删
data class PaymentRecord(
    val id: String = UUID.randomUUID().toString(),
    val periodNumber: Int,
    val amount: Int,
    val periodStartDate: LocalDate,
    val periodEndDate: LocalDate,
    val dueDate: LocalDate,
    val reminderDate: LocalDate,
    val isPaid: Boolean = false,
    val payee: String? = null,
    val paymentMethod: String? = null,
    val receiptDate: LocalDate? = null
)

// 你的完整租约逻辑，所有 11 个字段都在
data class Rental(
    val id: String = UUID.randomUUID().toString(),
    val propertyName: String,
    val tenantName: String,
    val tenantPhone: String,
    val tenantIdCard: String,
    val contractDate: LocalDate,
    val rentStartDate: LocalDate,
    val monthlyRent: Int,
    val leaseMonths: Int,
    val paymentFrequency: Int,
    val paymentSchedule: List<PaymentRecord> = generateSchedule(rentStartDate, monthlyRent, leaseMonths, paymentFrequency)
) {
    val totalAmount: Int get() = monthlyRent * paymentFrequency
    val nextPaymentDate: LocalDate? get() = paymentSchedule.firstOrNull { !it.isPaid }?.dueDate
    val isCompleted: Boolean get() = paymentSchedule.isNotEmpty() && paymentSchedule.all { it.isPaid }

    companion object {
        // 这是你之前写好的生成算法，原样搬回
        fun generateSchedule(start: LocalDate, rent: Int, months: Int, freq: Int): List<PaymentRecord> {
            val schedule = mutableListOf<PaymentRecord>()
            val actualFreq = if (freq == 0) months else freq
            val totalPeriods = if (actualFreq > 0) months / actualFreq else 1
            val amountPerPeriod = rent * actualFreq

            for (i in 0 until totalPeriods) {
                val pStart = start.plusMonths((i * actualFreq).toLong())
                val pEnd = start.plusMonths(((i + 1) * actualFreq).toLong()).minusDays(1)
                schedule.add(
                    PaymentRecord(
                        periodNumber = i + 1,
                        amount = amountPerPeriod,
                        periodStartDate = pStart,
                        periodEndDate = pEnd,
                        dueDate = pStart,
                        reminderDate = pStart.minusDays(15)
                    )
                )
            }
            return schedule
        }
    }
}