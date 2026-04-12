package com.example.renthouseapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

class RentalViewModel : ViewModel() {
    companion object {
        private const val UNKNOWN_OPERATOR = "unknown"
        private val ID_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }

    private val _rentals = mutableStateListOf<UiRental>()
    val rentals: List<UiRental> get() = _rentals

    private val _availableProperties = mutableStateListOf<String>()
    val availableProperties: List<String> get() = _availableProperties

    private val _availablePayees = mutableStateListOf<String>()
    val availablePayees: List<String> get() = _availablePayees

    var initError by mutableStateOf<String?>(null)
        private set

    var isInitialLoading by mutableStateOf(true)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentLoginUser by mutableStateOf<CurrentLoginUser?>(null)
        private set

    var verifiedLoginName by mutableStateOf<String?>(null)
        private set

    var verifiedLoginCode by mutableStateOf<String?>(null)
        private set

    var loginValidationMessage by mutableStateOf<String?>(null)
        private set

    var isLoggingIn by mutableStateOf(false)
        private set

    var entryFormDraft by mutableStateOf(EntryFormDraft())
        private set

    private var cloudDbManager: CloudDbManager? = null
    private var repository: CloudRentalRepository? = null
    private var sessionStore: LoginSessionStore? = null
    private var initialized = false
    private var latestLoadRequestId = 0

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        sessionStore = LoginSessionStore(context.applicationContext)
        sessionStore?.clear()
        currentLoginUser = null
        resetEntryFormDraft()

        cloudDbManager = CloudDbManager(context.applicationContext)
        repository = CloudRentalRepository(cloudDbManager!!)

        cloudDbManager?.init(
            onSuccess = {
                refreshPayees()
                loadData(userRefresh = false, onSuccess = {}, onError = {})
            },
            onError = {
                val msg = it.message ?: "Cloud DB 初始化失败"
                initError = msg
                errorMessage = msg
                isInitialLoading = false
                isRefreshing = false
            }
        )
    }

    fun verifyLoginCredentials(loginCode: String, password: String) {
        val normalizedCode = loginCode.trim()
        val normalizedPassword = password.trim()
        verifiedLoginName = null
        verifiedLoginCode = null
        loginValidationMessage = null

        if (normalizedCode.length != 11 || normalizedPassword.isBlank()) return

        val repo = repository ?: run {
            loginValidationMessage = "Cloud DB 尚未初始化"
            return
        }

        isLoggingIn = true
        repo.queryLoginUserByPhoneNumber(
            phoneNumber = normalizedCode,
            onSuccess = { loginUser ->
                isLoggingIn = false
                if (loginUser == null) {
                    loginValidationMessage = "未找到对应登录人"
                } else if (loginUser.passWord != normalizedPassword) {
                    loginValidationMessage = "登录码或密码错误"
                } else {
                    verifiedLoginCode = normalizedCode
                    verifiedLoginName = loginUser.loginName
                }
            },
            onError = {
                isLoggingIn = false
                loginValidationMessage = it.message ?: "登录校验失败"
            }
        )
    }

    fun loginWithVerifiedCredentials(
        loginCode: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedCode = loginCode.trim()
        val normalizedPassword = password.trim()
        val loginName = verifiedLoginName
        if (normalizedCode.isBlank()) {
            onError("请输入登录码")
            return
        }
        if (normalizedPassword.isBlank()) {
            onError("请输入密码")
            return
        }
        if (verifiedLoginCode != normalizedCode || loginName.isNullOrBlank()) {
            onError("请先输入正确的登录码和密码")
            return
        }

        currentLoginUser = CurrentLoginUser(
            loginName = loginName,
            phoneNumber = normalizedCode
        )
        onSuccess()
    }

    fun logout() {
        currentLoginUser = null
        verifiedLoginName = null
        verifiedLoginCode = null
        loginValidationMessage = null
        sessionStore?.clear()
    }

    fun updateEntryFormDraft(transform: (EntryFormDraft) -> EntryFormDraft) {
        entryFormDraft = transform(entryFormDraft)
    }

    fun resetEntryFormDraft() {
        entryFormDraft = EntryFormDraft(
            rentStartDateText = LocalDate.now().toString(),
            contractDateText = LocalDate.now().toString()
        )
    }

    fun refreshAllData() {
        refreshPayees()
        loadData(userRefresh = false, onSuccess = {}, onError = {})
    }

    fun refreshFromUser(
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        refreshPayees()
        loadData(userRefresh = true, onSuccess = onSuccess, onError = onError)
    }

    fun refreshFromTabSwitch() {
        refreshPayees()
        loadData(userRefresh = true, onSuccess = {}, onError = {})
    }

    private fun refreshPayees() {
        val repo = repository ?: return
        repo.queryAllLoginUsers(
            onSuccess = { loginUsers ->
                _availablePayees.clear()
                _availablePayees.addAll(
                    loginUsers
                        .mapNotNull { it.loginName?.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                )
            },
            onError = { error ->
                initError = error.message
            }
        )
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

        repo.queryAllProperties(
            onSuccess = { properties ->
                _availableProperties.clear()
                _availableProperties.addAll(
                    properties
                        .filter { it.isAvailable != false }
                        .mapNotNull { it.propertyName }
                        .sorted()
                )

                repo.queryAllRentalRecords(
                    onSuccess = { records ->
                        repo.queryAllPaymentRecords(
                            onSuccess = { payments ->
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
                            },
                            onError = {
                                val msg = it.message ?: "收款记录获取失败"
                                initError = msg
                                finishError(msg)
                            }
                        )
                    },
                    onError = {
                        val msg = it.message ?: "租约获取失败"
                        initError = msg
                        finishError(msg)
                    }
                )
            },
            onError = {
                val msg = it.message ?: "房源获取失败"
                initError = msg
                finishError(msg)
            }
        )
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

        ensureProperty(
            propertyName = propertyName,
            onResolved = { property ->
                val rentalId = buildRentalRecordId(
                    propertyId = property.id,
                    contractDate = contractDate
                )
                val schedules = generateSchedule(
                    propertyId = property.id,
                    contractDate = contractDate,
                    start = rentStartDate,
                    rent = monthlyRent,
                    months = leaseMonths,
                    freq = paymentFrequency
                )

                repo.queryRentalRecordById(
                    rentalId,
                    onSuccess = { existingRecord ->
                        if (existingRecord != null) {
                            onError("已存在相同房源和签约日期的合同，无法重复创建")
                            return@queryRentalRecordById
                        }

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
                        applyCreateAudit(rental)

                        repo.upsertRentalRecord(
                            rental,
                            onSuccess = {
                                upsertSchedules(
                                    repo = repo,
                                    records = schedules,
                                    index = 0,
                                    onDone = {
                                        property.isAvailable = false
                                        repo.upsertProperty(
                                            property,
                                            onSuccess = {
                                                refreshAllData()
                                                onSuccess()
                                            },
                                            onError = { onError(it.message ?: "房源写入失败") }
                                        )
                                    },
                                    onError = { onError(it.message ?: "账单写入失败") }
                                )
                            },
                            onError = { onError(it.message ?: "合同写入失败") }
                        )
                    },
                    onError = { onError(it.message ?: "合同查询失败") }
                )
            },
            onError = onError
        )
    }

    fun deleteRental(rentalId: String) {
        val repo = repository ?: return
        val target = _rentals.firstOrNull { it.id == rentalId } ?: return

        repo.queryPaymentRecordsByRentalId(
            rentalId,
            onSuccess = { payments ->
                deletePaymentsSequentially(repo, payments, 0) {
                    val record = RentalRecord().apply {
                        id = target.id
                        propertyId = target.propertyId
                        propertyName = target.propertyName
                    }
                    repo.deleteRentalRecord(
                        record,
                        onSuccess = {
                            repo.queryAllProperties(
                                onSuccess = { properties ->
                                    val property = properties.firstOrNull { it.id == target.propertyId }
                                        ?: properties.firstOrNull { it.propertyName == target.propertyName }

                                    if (property != null) {
                                        property.isAvailable = true
                                        repo.upsertProperty(
                                            property,
                                            onSuccess = { refreshAllData() },
                                            onError = { initError = it.message }
                                        )
                                    } else {
                                        val fallback = Property().apply {
                                            id = if (target.propertyId.isNotBlank()) target.propertyId else UUID.randomUUID().toString()
                                            propertyName = target.propertyName
                                            isAvailable = true
                                        }
                                        repo.upsertProperty(
                                            fallback,
                                            onSuccess = { refreshAllData() },
                                            onError = { initError = it.message }
                                        )
                                    }
                                },
                                onError = { initError = it.message }
                            )
                        },
                        onError = { initError = it.message }
                    )
                }
            },
            onError = { initError = it.message }
        )
    }

    fun updateTenantInfo(rentalId: String, newName: String, newPhone: String, newIdCard: String) {
        val repo = repository ?: return
        repo.queryRentalRecordById(
            rentalId,
            onSuccess = { record ->
                val existing = record ?: return@queryRentalRecordById
                existing.tenantName = newName
                existing.tenantPhone = newPhone
                existing.tenantIdCard = newIdCard
                applyUpdateAudit(existing)
                repo.upsertRentalRecord(
                    existing,
                    onSuccess = { refreshAllData() },
                    onError = { initError = it.message }
                )
            },
            onError = { initError = it.message }
        )
    }

    fun updatePaymentAmount(rentalId: String, paymentId: String, newAmount: Int) {
        updatePayment(rentalId, paymentId, onSuccess = {}) { payment -> payment.amount = newAmount }
    }

    fun confirmPayment(
        rentalId: String,
        paymentId: String,
        payee: String,
        method: String,
        date: LocalDate,
        onSuccess: () -> Unit = {}
    ) {
        updatePayment(rentalId, paymentId, onSuccess = onSuccess) { payment ->
            payment.isPaid = true
            payment.payee = payee
            payment.paymentMethod = method
            payment.receiptDate = date.toDate()
        }
    }

    fun revokePayment(rentalId: String, paymentId: String) {
        updatePayment(rentalId, paymentId, onSuccess = {}) { payment ->
            payment.isPaid = false
            payment.payee = null
            payment.paymentMethod = null
            payment.receiptDate = null
        }
    }

    private fun updatePayment(
        rentalId: String,
        paymentId: String,
        onSuccess: () -> Unit,
        mutate: (PaymentRecord) -> Unit
    ) {
        val repo = repository ?: return
        repo.queryPaymentRecordsByRentalId(
            rentalId,
            onSuccess = { payments ->
                val payment = payments.firstOrNull { it.id == paymentId } ?: return@queryPaymentRecordsByRentalId
                mutate(payment)
                applyUpdateAudit(payment)
                repo.upsertPaymentRecord(
                    payment,
                    onSuccess = {
                        onSuccess()
                        syncRentalCompletionFromCloud(rentalId)
                    },
                    onError = { initError = it.message }
                )
            },
            onError = { initError = it.message }
        )
    }

    private fun syncRentalCompletionFromCloud(rentalId: String) {
        val repo = repository ?: return
        repo.queryPaymentRecordsByRentalId(
            rentalId,
            onSuccess = { cloudPayments ->
                val shouldCompleted = cloudPayments.isNotEmpty() && cloudPayments.all { it.isPaid == true }
                repo.queryRentalRecordById(
                    rentalId,
                    onSuccess = { rental ->
                        val existing = rental ?: return@queryRentalRecordById
                        if (existing.isCompleted == shouldCompleted) {
                            refreshAllData()
                            return@queryRentalRecordById
                        }

                        existing.isCompleted = shouldCompleted
                        applyUpdateAudit(existing)
                        repo.upsertRentalRecord(
                            existing,
                            onSuccess = { refreshAllData() },
                            onError = { initError = it.message }
                        )
                    },
                    onError = { initError = it.message }
                )
            },
            onError = { initError = it.message }
        )
    }

    private fun ensureProperty(
        propertyName: String,
        onResolved: (Property) -> Unit,
        onError: (String) -> Unit
    ) {
        val repo = repository ?: return onError("Cloud DB 尚未初始化")

        repo.queryAllProperties(
            onSuccess = { properties ->
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
                repo.upsertProperty(
                    property,
                    onSuccess = { onResolved(property) },
                    onError = { onError(it.message ?: "房源写入失败") }
                )
            },
            onError = { onError(it.message ?: "房源查询失败") }
        )
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
        repo.upsertPaymentRecord(
            records[index],
            onSuccess = { upsertSchedules(repo, records, index + 1, onDone, onError) },
            onError = onError
        )
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
        repo.deletePaymentRecord(
            records[index],
            onSuccess = { deletePaymentsSequentially(repo, records, index + 1, onDone) },
            onError = {
                initError = it.message
                onDone()
            }
        )
    }

    private fun generateSchedule(
        propertyId: String,
        contractDate: LocalDate,
        start: LocalDate,
        rent: Int,
        months: Int,
        freq: Int
    ): List<PaymentRecord> {
        val schedule = mutableListOf<PaymentRecord>()
        val actualFreq = if (freq == 0) months else freq
        val totalPeriods = if (actualFreq > 0) months / actualFreq else 1
        val amountPerPeriod = rent * actualFreq
        val rentalId = buildRentalRecordId(propertyId, contractDate)

        for (i in 0 until totalPeriods) {
            val periodNumber = i + 1
            val periodStart = start.plusMonths((i * actualFreq).toLong())
            val periodEnd = start.plusMonths((periodNumber * actualFreq).toLong()).minusDays(1)
            schedule.add(
                PaymentRecord().apply {
                    id = buildPaymentRecordId(
                        propertyId = propertyId,
                        contractDate = contractDate,
                        periodNumber = periodNumber
                    )
                    this.rentalId = rentalId
                    this.periodNumber = periodNumber
                    amount = amountPerPeriod
                    periodStartDate = periodStart.toDate()
                    periodEndDate = periodEnd.toDate()
                    dueDate = periodStart.toDate()
                    reminderDate = periodStart.minusDays(15).toDate()
                    isPaid = false
                    applyCreateAudit(this)
                }
            )
        }
        return schedule
    }

    private fun buildRentalRecordId(
        propertyId: String,
        contractDate: LocalDate
    ): String {
        return "${propertyId.trim()}-${contractDate.format(ID_DATE_FORMATTER)}"
    }

    private fun buildPaymentRecordId(
        propertyId: String,
        contractDate: LocalDate,
        periodNumber: Int
    ): String {
        return "PAY-${propertyId.trim()}-${contractDate.format(ID_DATE_FORMATTER)}-$periodNumber"
    }

    private fun applyCreateAudit(record: RentalRecord) {
        val now = Date()
        val operator = currentOperatorName()
        record.createdBy = operator
        record.createdAt = now
        record.updatedBy = operator
        record.updatedAt = now
    }

    private fun applyUpdateAudit(record: RentalRecord) {
        record.updatedBy = currentOperatorName()
        record.updatedAt = Date()
        if (record.createdBy.isNullOrBlank()) {
            record.createdBy = currentOperatorName()
        } else if (record.createdBy == currentLoginUser?.phoneNumber) {
            record.createdBy = currentOperatorName()
        }
        if (record.createdAt == null) {
            record.createdAt = record.updatedAt
        }
    }

    private fun applyCreateAudit(record: PaymentRecord) {
        val now = Date()
        val operator = currentOperatorName()
        record.createdBy = operator
        record.createdAt = now
        record.updatedBy = operator
        record.updatedAt = now
    }

    private fun applyUpdateAudit(record: PaymentRecord) {
        record.updatedBy = currentOperatorName()
        record.updatedAt = Date()
        if (record.createdBy.isNullOrBlank()) {
            record.createdBy = currentOperatorName()
        } else if (record.createdBy == currentLoginUser?.phoneNumber) {
            record.createdBy = currentOperatorName()
        }
        if (record.createdAt == null) {
            record.createdAt = record.updatedAt
        }
    }

    private fun currentOperatorName(): String {
        return currentLoginUser?.loginName?.takeIf { it.isNotBlank() } ?: UNKNOWN_OPERATOR
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
            createdBy = createdBy.orEmpty(),
            createdAt = createdAt?.toLocalDateTime(),
            updatedBy = updatedBy.orEmpty(),
            updatedAt = updatedAt?.toLocalDateTime(),
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
            receiptDate = receiptDate?.toLocalDate(),
            updatedAt = updatedAt?.toLocalDateTime()
        )
    }

    private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
    private fun Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    private fun Date.toLocalDateTime() = toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
}
