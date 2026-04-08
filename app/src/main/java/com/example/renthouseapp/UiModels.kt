package com.example.renthouseapp

import java.time.LocalDate

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
    val createdBy: String? = null,
    val createdAt: LocalDate? = null,
    val updatedBy: String? = null,
    val updatedAt: LocalDate? = null
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
    val paymentSchedule: List<UiPaymentRecord>,
    val createdBy: String? = null,
    val createdAt: LocalDate? = null,
    val updatedBy: String? = null,
    val updatedAt: LocalDate? = null
) {
    val totalAmount: Int get() = monthlyRent * paymentFrequency
    val nextPaymentDate: LocalDate? get() = paymentSchedule.firstOrNull { !it.isPaid }?.dueDate
    val reminderDate: LocalDate? get() = nextPaymentDate?.minusDays(15)
}

data class LoginIdentity(
    val loginName: String,
    val phoneNumber: String
)
