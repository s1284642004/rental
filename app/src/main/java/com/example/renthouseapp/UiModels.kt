package com.example.renthouseapp

import java.time.LocalDate
import java.time.LocalDateTime

data class UiPaymentRecord(
    val id: String,
    val rentalId: String,
    val periodNumber: Int,
    val amount: Int,
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
    val monthlyRent: Int,
    val leaseMonths: Int,
    val paymentFrequency: Int,
    val isCompleted: Boolean,
    val createdBy: String,
    val createdAt: LocalDateTime?,
    val updatedBy: String,
    val updatedAt: LocalDateTime?,
    val paymentSchedule: List<UiPaymentRecord>
) {
    val totalAmount: Int get() = monthlyRent * paymentFrequency
    val nextPaymentDate: LocalDate? get() = paymentSchedule.firstOrNull { !it.isPaid }?.dueDate
    val reminderDate: LocalDate? get() = nextPaymentDate?.minusDays(15)
}

data class EntryFormDraft(
    val propertyName: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val tenantIdCard: String = "",
    val monthlyRent: String = "",
    val leaseMonths: String = "12",
    val rentStartDateText: String = "",
    val contractDateText: String = "",
    val paymentFrequencyValue: Int = 1
)
