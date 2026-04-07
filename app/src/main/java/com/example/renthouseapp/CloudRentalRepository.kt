package com.example.renthouseapp

/**
 * Repository保留现有架构分层，仅把底层存取切换到Cloud DB。
 */
class CloudRentalRepository(private val cloudDbManager: CloudDbManager) {

    fun upsertProperty(property: Property, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.insertOrUpdateProperty(property, onSuccess, onError)
    }

    fun queryAllProperties(onSuccess: (List<Property>) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.queryAllProperties(onSuccess, onError)
    }

    fun deleteProperty(property: Property, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.deleteProperty(property, onSuccess, onError)
    }

    fun upsertLoginUser(loginUser: LoginUser, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.insertOrUpdateLoginUser(loginUser, onSuccess, onError)
    }

    fun upsertRentalRecord(record: RentalRecord, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.insertOrUpdateRentalRecord(record, onSuccess, onError)
    }

    fun queryAllRentalRecords(onSuccess: (List<RentalRecord>) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.queryAllRentalRecords(onSuccess, onError)
    }

    fun deleteRentalRecord(record: RentalRecord, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.deleteRentalRecord(record, onSuccess, onError)
    }

    fun upsertPaymentRecord(record: PaymentRecord, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.insertOrUpdatePaymentRecord(record, onSuccess, onError)
    }

    fun queryAllPaymentRecords(onSuccess: (List<PaymentRecord>) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.queryAllPaymentRecords(onSuccess, onError)
    }

    fun queryPaymentRecordsByRentalId(rentalId: String, onSuccess: (List<PaymentRecord>) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.queryPaymentRecordsByRentalId(rentalId, onSuccess, onError)
    }

    fun deletePaymentRecord(record: PaymentRecord, onSuccess: (Int) -> Unit, onError: (Throwable) -> Unit) {
        cloudDbManager.deletePaymentRecord(record, onSuccess, onError)
    }
}
