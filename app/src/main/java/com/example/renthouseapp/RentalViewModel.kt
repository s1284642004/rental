package com.example.renthouseapp

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import java.time.LocalDate

class RentalViewModel : ViewModel() {
    private val _rentals = mutableStateListOf<Rental>()
    val rentals: List<Rental> get() = _rentals

    // 需求8：新的房源列表
    val availableProperties = listOf(
        "簇锦家园201", "滨河苑30号", "南苑五星城904", "优博818",
        "优博819", "诗锦苑1-1-204", "诗锦苑6-2-1103", "诗锦苑7-1-616", "诗锦苑1-1-1007"
    )

    fun addRental(rental: Rental) { _rentals.add(rental) }
    fun deleteRental(rentalId: String) { _rentals.removeAll { it.id == rentalId } }

    fun updateTenantInfo(rentalId: String, newName: String, newPhone: String, newIdCard: String) {
        val index = _rentals.indexOfFirst { it.id == rentalId }
        if (index != -1) _rentals[index] = _rentals[index].copy(tenantName = newName, tenantPhone = newPhone, tenantIdCard = newIdCard)
    }

    // 需求4：仅修改当前账单金额，不影响其他
    fun updatePaymentAmount(rentalId: String, paymentId: String, newAmount: Int) {
        updateRecord(rentalId, paymentId) { it.copy(amount = newAmount) }
    }

    // 需求2：确认收款及撤销
    fun confirmPayment(rentalId: String, paymentId: String, payee: String, method: String, date: LocalDate) {
        updateRecord(rentalId, paymentId) {
            it.copy(isPaid = true, payee = payee, paymentMethod = method, receiptDate = date)
        }
    }

    fun revokePayment(rentalId: String, paymentId: String) {
        updateRecord(rentalId, paymentId) {
            it.copy(isPaid = false, payee = null, paymentMethod = null, receiptDate = null)
        }
    }

    private fun updateRecord(rentalId: String, paymentId: String, transform: (PaymentRecord) -> PaymentRecord) {
        val rentalIndex = _rentals.indexOfFirst { it.id == rentalId }
        if (rentalIndex != -1) {
            val oldRental = _rentals[rentalIndex]
            val newSchedule = oldRental.paymentSchedule.map {
                if (it.id == paymentId) transform(it) else it
            }
            _rentals[rentalIndex] = oldRental.copy(paymentSchedule = newSchedule)
        }
    }
}