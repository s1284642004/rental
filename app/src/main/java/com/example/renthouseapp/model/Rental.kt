package com.example.renthouseapp.model

import java.time.LocalDate
import java.util.UUID

data class PaymentRecord(
    val id: String = UUID.randomUUID().toString(),
    val periodNumber: Int,
    val amount: Int,
    val periodStartDate: LocalDate,
    val periodEndDate: LocalDate,
    val dueDate: LocalDate,
    val reminderDate: LocalDate, // 账单本身的提醒日期
    val isPaid: Boolean = false,
    val payee: String? = null,
    val paymentMethod: String? = null,
    val receiptDate: LocalDate? = null
)

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
    // 【修复报错点】补回 UI 需要的计算属性
    val totalAmount: Int get() = monthlyRent * paymentFrequency

    // 下次缴纳日期：找到第一笔未付账单的截止日期
    val nextPaymentDate: LocalDate?
        get() = paymentSchedule.firstOrNull { !it.isPaid }?.dueDate

    // 整个合同的催款提醒日期：基于下次缴纳日期提前15天
    val reminderDate: LocalDate?
        get() = nextPaymentDate?.minusDays(15)

    val isCompleted: Boolean get() = paymentSchedule.isNotEmpty() && paymentSchedule.all { it.isPaid }

    companion object {
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