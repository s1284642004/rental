package com.example.renthouseapp

import java.time.LocalDate
import java.time.LocalDateTime

data class UiPaymentRecord(
    val id: String,
    val rentalId: String,
    val periodNumber: Int,
    val amount: Int,
    val rentAmount: Int,
    val propertyFeeAmount: Int,
    val monthlyRentSnapshot: Int,
    val monthsInPeriod: Int,
    val remark: String,
    val periodStartDate: LocalDate,
    val periodEndDate: LocalDate,
    val dueDate: LocalDate,
    val reminderDate: LocalDate,
    val isPaid: Boolean,
    val payee: String? = null,
    val paymentMethod: String? = null,
    val receiptDate: LocalDate? = null,
    val updatedAt: LocalDateTime? = null
)

data class UiRental(
    val id: String,
    val propertyId: String,
    val propertyName: String,
    val tenantName: String,
    val tenantPhone: String,
    val tenantIdCard: String,
    val contractDate: LocalDate,
    val rentStartDate: LocalDate,
    val rentEndDate: LocalDate,
    val monthlyRent: Int,
    val propertyFee: Int,
    val depositAmount: Int,
    val depositStatus: String,
    val remark: String,
    val reminderDaysBeforeDue: Int,
    val leaseMonths: Int,
    val paymentFrequency: Int,
    val isCompleted: Boolean,
    val createdBy: String,
    val createdAt: LocalDateTime?,
    val updatedBy: String,
    val updatedAt: LocalDateTime?,
    val paymentSchedule: List<UiPaymentRecord>
) {
    val totalAmount: Int get() = paymentSchedule.firstOrNull { !it.isPaid }?.amount ?: (monthlyRent + propertyFee)
    val nextPaymentDate: LocalDate? get() = paymentSchedule.firstOrNull { !it.isPaid }?.dueDate
    val reminderDate: LocalDate? get() = nextPaymentDate?.minusDays(15)
}

data class EntryFormDraft(
    val propertyName: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val tenantIdCard: String = "",
    val monthlyRent: String = "",
    val propertyFee: String = "",
    val depositAmount: String = "",
    val depositStatus: String = "\u672a\u652f\u4ed8",
    val leaseMonths: String = "12",
    val remark: String = "",
    val reminderDaysBeforeDue: String = "15",
    val rentStartDateText: String = "",
    val contractDateText: String = "",
    val paymentFrequencyValue: Int = 1
)
