package com.example.renthouseapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

class RentalViewModel : ViewModel() {
    private val _rentals = mutableStateListOf<UiRental>()
    val rentals: List<UiRental> get() = _rentals

    private val _availableProperties = mutableStateListOf<String>()
    val availableProperties: List<String> get() = _availableProperties

    var initError by mutableStateOf<String?>(null)
        private set

    var isInitialLoading by mutableStateOf(true)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isEmpty: Boolean
        get() = rentals.isEmpty()

    private var cloudDbManager: CloudDbManager? = null
    private var repository: CloudRentalRepository? = null
    private var initialized = false
    private var latestLoadRequestId = 0


    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        cloudDbManager = CloudDbManager(context.applicationContext)
        repository = CloudRentalRepository(cloudDbManager!!)

        cloudDbManager?.init(
            onSuccess = { loadData(userRefresh = false, onSuccess = {}, onError = {}) },
            onError = {
                val msg = it.message ?: "Cloud DB 初始化失败"
                initError = msg
                errorMessage = msg
                isInitialLoading = false
                isRefreshing = false
            }
        )
    }

    fun refreshAllData() {
        loadData(userRefresh = false, onSuccess = {}, onError = {})
    }

    fun refreshFromUser(
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        loadData(userRefresh = true, onSuccess = onSuccess, onError = onError)
    }

    private fun loadData(
        userRefresh: Boolean,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val repo = repository ?: run {
            val msg = "Cloud DB 尚未初始化"
            errorMessage = msg
            isInitialLoading = false
            isRefreshing = false
            onError(msg)
            return
        }

        val requestId = ++latestLoadRequestId
        if (userRefresh) {
            // 用户主动刷新只显示顶部刷新态，不清空当前内容。
            isRefreshing = true
        } else {
            isInitialLoading = rentals.isEmpty()
        }

        fun finishSuccess(isEmptyResult: Boolean) {
            if (requestId != latestLoadRequestId) return
            isInitialLoading = false
            isRefreshing = false
            errorMessage = null
            onSuccess(isEmptyResult)
        }

        fun finishError(message: String) {
            if (requestId != latestLoadRequestId) return
            isInitialLoading = false
            isRefreshing = false
            errorMessage = message
            onError(message)
        }

        repo.queryAllProperties(onSuccess = { properties ->
            _availableProperties.clear()
            _availableProperties.addAll(properties.filter { it.isAvailable != false }.mapNotNull { it.propertyName }.sorted())

            repo.queryAllRentalRecords(onSuccess = { records ->
                repo.queryAllPaymentRecords(onSuccess = { payments ->
                    val grouped = payments.groupBy { it.rentalId }
                    val uiList = records.map { record ->
                        val uiPayments = grouped[record.id].orEmpty()
                            .map { it.toUiPayment() }
                            .sortedBy { it.periodNumber }
                        record.toUiRental(uiPayments)
                    }
                    _rentals.clear()
                    _rentals.addAll(uiList)
                    finishSuccess(uiList.isEmpty())
                }, onError = {
                    val msg = it.message ?: "支付记录获取失败"
                    initError = msg
                    finishError(msg)
                })
            }, onError = {
                val msg = it.message ?: "租约获取失败"
                initError = msg
                finishError(msg)
            })
        }, onError = {
            val msg = it.message ?: "房源获取失败"
            initError = msg
            finishError(msg)
        })
    }

    fun addRental(
        propertyName: String,
        tenantName: String,
        tenantPhone: String,
        tenantIdCard: String,
        contractDate: LocalDate,
        rentStartDate: LocalDate,
        monthlyRent: Int,
        leaseMonths: Int,
        paymentFrequency: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val repo = repository ?: return onError("Cloud DB 尚未初始化")

        ensureProperty(propertyName,
            onResolved = { property ->
                val rentalId = UUID.randomUUID().toString()
                val rental = RentalRecord().apply {
                    id = rentalId
                    propertyId = property.id
                    this.propertyName = property.propertyName
                    this.tenantName = tenantName
                    this.tenantPhone = tenantPhone
                    this.tenantIdCard = tenantIdCard
                    this.contractDate = contractDate.toDate()
                    this.rentStartDate = rentStartDate.toDate()
                    this.monthlyRent = monthlyRent
                    this.leaseMonths = leaseMonths
                    this.paymentFrequency = paymentFrequency
                    isCompleted = false
                }

                repo.upsertRentalRecord(rental, onSuccess = {
                    val schedules = generateSchedule(rentalId, rentStartDate, monthlyRent, leaseMonths, paymentFrequency)
                    upsertSchedules(repo, schedules, 0,
                        onDone = {
                            property.isAvailable = false
                            repo.upsertProperty(property, onSuccess = {
                                refreshAllData()
                                onSuccess()
                            }, onError = { onError(it.message ?: "房源写入失败") })
                        },
                        onError = { onError(it.message ?: "账单写入失败") }
                    )
                }, onError = { onError(it.message ?: "合同写入失败") })
            },
            onError = onError
        )
    }

    fun deleteRental(rentalId: String) {
        val repo = repository ?: return
        val target = _rentals.firstOrNull { it.id == rentalId } ?: return

        repo.queryPaymentRecordsByRentalId(rentalId, onSuccess = { payments ->
            deletePaymentsSequentially(repo, payments, 0) {
                val record = RentalRecord().apply {
                    id = target.id
                    propertyId = target.propertyId
                    propertyName = target.propertyName
                }
                repo.deleteRentalRecord(record, onSuccess = {
                    // 删除合同后房源可再次出租。
                    repo.queryAllProperties(onSuccess = { properties ->
                        val property = properties.firstOrNull { it.id == target.propertyId }
                            ?: properties.firstOrNull { it.propertyName == target.propertyName }

                        if (property != null) {
                            property.isAvailable = true
                            repo.upsertProperty(property, onSuccess = { refreshAllData() }, onError = { initError = it.message })
                        } else {
                            // 兼容历史脏数据：若未找到对应Property，补写一条可用房源以确保合同删除后可再次录入。
                            val fallback = Property().apply {
                                id = if (target.propertyId.isNotBlank()) target.propertyId else UUID.randomUUID().toString()
                                propertyName = target.propertyName
                                isAvailable = true
                            }
                            repo.upsertProperty(fallback, onSuccess = { refreshAllData() }, onError = { initError = it.message })
                        }
                    }, onError = { initError = it.message })
                }, onError = { initError = it.message })
            }
        }, onError = { initError = it.message })
    }

    fun updateTenantInfo(rentalId: String, newName: String, newPhone: String, newIdCard: String) {
        val repo = repository ?: return
        val target = _rentals.firstOrNull { it.id == rentalId } ?: return
        val record = RentalRecord().apply {
            id = target.id
            propertyId = target.propertyId
            propertyName = target.propertyName
            tenantName = newName
            tenantPhone = newPhone
            tenantIdCard = newIdCard
            contractDate = target.contractDate.toDate()
            rentStartDate = target.rentStartDate.toDate()
            monthlyRent = target.monthlyRent
            leaseMonths = target.leaseMonths
            paymentFrequency = target.paymentFrequency
            isCompleted = target.isCompleted
        }
        repo.upsertRentalRecord(record, onSuccess = { refreshAllData() }, onError = { initError = it.message })
    }

    fun updatePaymentAmount(rentalId: String, paymentId: String, newAmount: Int) {
        updatePayment(rentalId, paymentId) { payment -> payment.amount = newAmount }
    }

    fun confirmPayment(rentalId: String, paymentId: String, payee: String, method: String, date: LocalDate) {
        updatePayment(rentalId, paymentId) { payment ->
            payment.isPaid = true
            payment.payee = payee
            payment.paymentMethod = method
            payment.receiptDate = date.toDate()
        }
    }

    fun revokePayment(rentalId: String, paymentId: String) {
        updatePayment(rentalId, paymentId) { payment ->
            payment.isPaid = false
            payment.payee = null
            payment.paymentMethod = null
            payment.receiptDate = null
        }
    }

    private fun updatePayment(rentalId: String, paymentId: String, mutate: (PaymentRecord) -> Unit) {
        val repo = repository ?: return
        repo.queryPaymentRecordsByRentalId(rentalId, onSuccess = { payments ->
            val payment = payments.firstOrNull { it.id == paymentId } ?: return@queryPaymentRecordsByRentalId
            mutate(payment)
            repo.upsertPaymentRecord(payment, onSuccess = {
                syncRentalCompletionFromCloud(rentalId)
            }, onError = { initError = it.message })
        }, onError = { initError = it.message })
    }

    private fun syncRentalCompletionFromCloud(rentalId: String) {
        val repo = repository ?: return
        repo.queryPaymentRecordsByRentalId(rentalId, onSuccess = { cloudPayments ->
            val shouldCompleted = cloudPayments.isNotEmpty() && cloudPayments.all { it.isPaid == true }
            val rental = _rentals.firstOrNull { it.id == rentalId } ?: return@queryPaymentRecordsByRentalId
            if (rental.isCompleted == shouldCompleted) {
                refreshAllData()
                return@queryPaymentRecordsByRentalId
            }

            val record = RentalRecord().apply {
                id = rental.id
                propertyId = rental.propertyId
                propertyName = rental.propertyName
                tenantName = rental.tenantName
                tenantPhone = rental.tenantPhone
                tenantIdCard = rental.tenantIdCard
                contractDate = rental.contractDate.toDate()
                rentStartDate = rental.rentStartDate.toDate()
                monthlyRent = rental.monthlyRent
                leaseMonths = rental.leaseMonths
                paymentFrequency = rental.paymentFrequency
                isCompleted = shouldCompleted
            }
            repo.upsertRentalRecord(record, onSuccess = { refreshAllData() }, onError = { initError = it.message })
        }, onError = { initError = it.message })
    }


    private fun ensureProperty(
        propertyName: String,
        onResolved: (Property) -> Unit,
        onError: (String) -> Unit
    ) {
        val repo = repository ?: return onError("Cloud DB 尚未初始化")

        repo.queryAllProperties(onSuccess = { properties ->
            val existing = properties.firstOrNull { it.propertyName == propertyName }
            if (existing != null) {
                onResolved(existing)
                return@queryAllProperties
            }

            val property = Property().apply {
                id = UUID.randomUUID().toString()
                this.propertyName = propertyName
                isAvailable = true
            }
            repo.upsertProperty(property, onSuccess = {
                onResolved(property)
            }, onError = {
                onError(it.message ?: "房源写入失败")
            })
        }, onError = { onError(it.message ?: "房源查询失败") })
    }

    private fun upsertSchedules(
        repo: CloudRentalRepository,
        records: List<PaymentRecord>,
        index: Int,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (index >= records.size) {
            onDone()
            return
        }
        repo.upsertPaymentRecord(records[index], onSuccess = {
            upsertSchedules(repo, records, index + 1, onDone, onError)
        }, onError = onError)
    }

    private fun deletePaymentsSequentially(
        repo: CloudRentalRepository,
        records: List<PaymentRecord>,
        index: Int,
        onDone: () -> Unit
    ) {
        if (index >= records.size) {
            onDone()
            return
        }
        repo.deletePaymentRecord(records[index], onSuccess = {
            deletePaymentsSequentially(repo, records, index + 1, onDone)
        }, onError = {
            initError = it.message
            onDone()
        })
    }

    private fun generateSchedule(
        rentalId: String,
        start: LocalDate,
        rent: Int,
        months: Int,
        freq: Int
    ): List<PaymentRecord> {
        val schedule = mutableListOf<PaymentRecord>()
        val actualFreq = if (freq == 0) months else freq
        val totalPeriods = if (actualFreq > 0) months / actualFreq else 1
        val amountPerPeriod = rent * actualFreq

        for (i in 0 until totalPeriods) {
            val periodStart = start.plusMonths((i * actualFreq).toLong())
            val periodEnd = start.plusMonths(((i + 1) * actualFreq).toLong()).minusDays(1)
            schedule.add(
                PaymentRecord().apply {
                    id = UUID.randomUUID().toString()
                    this.rentalId = rentalId
                    periodNumber = i + 1
                    amount = amountPerPeriod
                    periodStartDate = periodStart.toDate()
                    periodEndDate = periodEnd.toDate()
                    dueDate = periodStart.toDate()
                    reminderDate = periodStart.minusDays(15).toDate()
                    isPaid = false
                }
            )
        }
        return schedule
    }

    private fun RentalRecord.toUiRental(payments: List<UiPaymentRecord>): UiRental {
        return UiRental(
            id = id,
            propertyId = propertyId.orEmpty(),
            propertyName = propertyName.orEmpty(),
            tenantName = tenantName.orEmpty(),
            tenantPhone = tenantPhone.orEmpty(),
            tenantIdCard = tenantIdCard.orEmpty(),
            contractDate = contractDate?.toLocalDate() ?: LocalDate.now(),
            rentStartDate = rentStartDate?.toLocalDate() ?: LocalDate.now(),
            monthlyRent = monthlyRent ?: 0,
            leaseMonths = leaseMonths ?: 0,
            paymentFrequency = paymentFrequency ?: 0,
            isCompleted = isCompleted == true,
            paymentSchedule = payments
        )
    }

    private fun PaymentRecord.toUiPayment(): UiPaymentRecord {
        return UiPaymentRecord(
            id = id,
            rentalId = rentalId.orEmpty(),
            periodNumber = periodNumber ?: 0,
            amount = amount ?: 0,
            periodStartDate = periodStartDate?.toLocalDate() ?: LocalDate.now(),
            periodEndDate = periodEndDate?.toLocalDate() ?: LocalDate.now(),
            dueDate = dueDate?.toLocalDate() ?: LocalDate.now(),
            reminderDate = reminderDate?.toLocalDate() ?: LocalDate.now(),
            isPaid = isPaid == true,
            payee = payee,
            paymentMethod = paymentMethod,
            receiptDate = receiptDate?.toLocalDate()
        )
    }

    private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
    private fun Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}
