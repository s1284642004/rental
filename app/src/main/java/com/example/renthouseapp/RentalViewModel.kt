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

    var isMigratingData by mutableStateOf(false)
        private set

    var entryFormDraft by mutableStateOf(EntryFormDraft())
        private set

    private var cloudDbManager: CloudDbManager? = null
    private var repository: CloudRentalRepository? = null
    private var sessionStore: LoginSessionStore? = null
    private var initialized = false
    private var isCloudDbReady = false
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
        isCloudDbReady = false

        cloudDbManager?.init(
            onSuccess = {
                isCloudDbReady = true
                refreshPayees()
                loadData(userRefresh = false, onSuccess = {}, onError = {})
            },
            onError = {
                isCloudDbReady = false
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
        if (!isCloudDbReady) {
            loginValidationMessage = "Cloud DB 尚未就绪，请稍后再试"
            return
        }

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
        if (!isCloudDbReady) return
        refreshPayees()
        loadData(userRefresh = false, onSuccess = {}, onError = {})
    }

    fun refreshFromUser(
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isCloudDbReady) {
            onError("Cloud DB 尚未就绪")
            return
        }
        refreshPayees()
        loadData(userRefresh = true, onSuccess = onSuccess, onError = onError)
    }

    fun refreshFromTabSwitch() {
        if (!isCloudDbReady) return
        refreshPayees()
        loadData(userRefresh = true, onSuccess = {}, onError = {})
    }

    fun migrateDatabaseSchema(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isCloudDbReady) {
            onError("Cloud DB 尚未就绪")
            return
        }
        val repo = repository ?: run {
            onError("Cloud DB 尚未初始化")
            return
        }
        if (isMigratingData) return
        isMigratingData = true

        repo.queryAllRentalRecords(
            onSuccess = { rentals ->
                repo.queryAllPaymentRecords(
                    onSuccess = { payments ->
                        val groupedPayments = payments.groupBy { it.rentalId.orEmpty() }
                        val updatedRentals = rentals.map { rental ->
                            val startDate = rental.rentStartDate?.toLocalDate() ?: LocalDate.now()
                            val leaseMonths = rental.leaseMonths ?: 0
                            val rentalPayments = groupedPayments[rental.id.orEmpty()].orEmpty()
                            rental.apply {
                                if (rentEndDate == null) {
                                    rentEndDate = calculateRentEndDate(startDate, leaseMonths).toDate()
                                }
                                if (reminderDaysBeforeDue == null || reminderDaysBeforeDue!! < 0) {
                                    reminderDaysBeforeDue = 15
                                }
                                if (remark == null) {
                                    remark = ""
                                }
                                if (tenantPhone.isNullOrBlank()) {
                                    tenantPhone = ""
                                }
                                if (isCompleted == null) {
                                    isCompleted = rentalPayments.isNotEmpty() && rentalPayments.all { it.isPaid == true }
                                }
                                applyUpdateAudit(this)
                            }
                        }

                        val updatedPayments = payments.map { payment ->
                            val rental = rentals.firstOrNull { it.id == payment.rentalId }
                            val monthsInPeriod = (payment.monthsInPeriod
                                ?: resolveMonthsPerPeriod(
                                    paymentFrequency = rental?.paymentFrequency ?: 1,
                                    leaseMonths = rental?.leaseMonths ?: 1
                                )).coerceAtLeast(1)
                            val monthlyRentSnapshot = payment.monthlyRentSnapshot
                                ?: ((payment.rentAmount ?: payment.amount ?: 0) / monthsInPeriod)
                            val dueDate = payment.dueDate?.toLocalDate() ?: LocalDate.now()
                            val reminderDays = (rental?.reminderDaysBeforeDue ?: 15).coerceAtLeast(0)

                            payment.apply {
                                this.monthsInPeriod = monthsInPeriod
                                this.monthlyRentSnapshot = monthlyRentSnapshot
                                if (remark == null) {
                                    remark = ""
                                }
                                if (rentAmount == null) {
                                    rentAmount = monthlyRentSnapshot * monthsInPeriod
                                }
                                if (propertyFeeAmount == null) {
                                    propertyFeeAmount = ((amount ?: 0) - (rentAmount ?: 0)).coerceAtLeast(0)
                                }
                                if (amount == null) {
                                    amount = (rentAmount ?: 0) + (propertyFeeAmount ?: 0)
                                }
                                if (reminderDate == null) {
                                    reminderDate = dueDate.minusDays(reminderDays.toLong()).toDate()
                                }
                                applyUpdateAudit(this)
                            }
                        }

                        upsertRentalMigrations(
                            repo = repo,
                            rentals = updatedRentals,
                            index = 0,
                            onDone = {
                                upsertPaymentMigrations(
                                    repo = repo,
                                    payments = updatedPayments,
                                    index = 0,
                                    onDone = {
                                        isMigratingData = false
                                        refreshAllData()
                                        onSuccess("数据库更新完成，旧数据已兼容到当前结构")
                                    },
                                    onError = { error ->
                                        isMigratingData = false
                                        val msg = error.message ?: "收款计划迁移失败"
                                        initError = msg
                                        onError(msg)
                                    }
                                )
                            },
                            onError = { error ->
                                isMigratingData = false
                                val msg = error.message ?: "合同迁移失败"
                                initError = msg
                                onError(msg)
                            }
                        )
                    },
                    onError = {
                        isMigratingData = false
                        val msg = it.message ?: "收款计划读取失败"
                        initError = msg
                        onError(msg)
                    }
                )
            },
            onError = {
                isMigratingData = false
                val msg = it.message ?: "合同读取失败"
                initError = msg
                onError(msg)
            }
        )
    }

    fun cleanupDanglingPaymentRecords(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isCloudDbReady) {
            onError("Cloud DB 灏氭湭灏辩华")
            return
        }
        val repo = repository ?: run {
            onError("Cloud DB 灏氭湭鍒濆鍖?")
            return
        }
        if (isMigratingData) return
        isMigratingData = true

        repo.queryAllRentalRecords(
            onSuccess = { rentals ->
                val validRentalIds = rentals.map { it.id }.toSet()
                repo.queryAllPaymentRecords(
                    onSuccess = { payments ->
                        val danglingPayments = payments.filter { payment ->
                            payment.rentalId.isNullOrBlank() || payment.rentalId !in validRentalIds
                        }

                        deletePaymentsSequentially(
                            repo = repo,
                            records = danglingPayments,
                            index = 0,
                            onError = {
                                isMigratingData = false
                                val msg = initError ?: "垃圾收款记录清理失败"
                                onError(msg)
                            },
                            onDone = {
                                isMigratingData = false
                                refreshAllData()
                                onSuccess(
                                    "合同表共 ${rentals.size} 条，已清理 ${danglingPayments.size} 条无效收款记录"
                                )
                            }
                        )
                    },
                    onError = {
                        isMigratingData = false
                        val msg = it.message ?: "收款记录读取失败"
                        initError = msg
                        onError(msg)
                    }
                )
            },
            onError = {
                isMigratingData = false
                val msg = it.message ?: "合同读取失败"
                initError = msg
                onError(msg)
            }
        )
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
                repo.queryAllRentalRecords(
                    onSuccess = { records ->
                        repo.queryAllPaymentRecords(
                            onSuccess = { payments ->
                                val occupiedPropertyNames = records
                                    .filter { it.isCompleted != true }
                                    .mapNotNull { it.propertyName }
                                    .toSet()
                                val grouped = payments.groupBy { it.rentalId }
                                val uiList = records.map { record ->
                                    val uiPayments = grouped[record.id].orEmpty()
                                        .map { it.toUiPayment() }
                                        .sortedBy { it.periodNumber }
                                    record.toUiRental(uiPayments)
                                }
                                _availableProperties.clear()
                                _availableProperties.addAll(
                                    properties
                                        .mapNotNull { it.propertyName }
                                        .filter { it !in occupiedPropertyNames }
                                        .distinct()
                                        .sorted()
                                )
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
        propertyFee: Int,
        depositAmount: Int,
        depositStatus: String,
        leaseMonths: Int,
        paymentFrequency: Int,
        remark: String,
        reminderDaysBeforeDue: Int,
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
                    rentalId = rentalId,
                    propertyId = property.id,
                    contractDate = contractDate,
                    start = rentStartDate,
                    rent = monthlyRent,
                    propertyFee = propertyFee,
                    months = leaseMonths,
                    freq = paymentFrequency,
                    reminderDaysBeforeDue = reminderDaysBeforeDue
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
                            this.propertyFee = propertyFee
                            this.depositAmount = depositAmount
                            this.depositStatus = depositStatus
                            this.leaseMonths = leaseMonths
                            this.paymentFrequency = paymentFrequency
                            this.remark = remark
                            this.rentEndDate = calculateRentEndDate(rentStartDate, leaseMonths).toDate()
                            this.reminderDaysBeforeDue = reminderDaysBeforeDue
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

/*    fun updateRentalContractInfo(
        rentalId: String,
        newPropertyName: String,
        newName: String,
        newPhone: String,
        newIdCard: String,
        newContractDate: LocalDate,
        newRentStartDate: LocalDate,
        newMonthlyRent: Int,
        newPropertyFee: Int,
        newDepositAmount: Int,
        newDepositStatus: String,
        newLeaseMonths: Int,
        newPaymentFrequency: Int,
        newRemark: String,
        newReminderDaysBeforeDue: Int,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val repo = repository ?: return
        repo.queryRentalRecordById(
            rentalId,
            onSuccess = { record ->
                val existing = record ?: return@queryRentalRecordById
                repo.queryPaymentRecordsByRentalId(
                    rentalId,
                    onSuccess = { payments ->
                        ensureProperty(
                            propertyName = newPropertyName,
                            onResolved = { targetProperty ->
                                val oldRentalId = existing.id
                                val newRentalId = buildRentalRecordId(targetProperty.id, newContractDate)
                                val allPaymentsCompleted =
                                    payments.isNotEmpty() && payments.all { it.isPaid == true }
                                val allPaymentsCompleted =
                                    payments.isNotEmpty() && payments.all { it.isPaid == true }
                                repo.queryRentalRecordById(
                                    newRentalId,
                                    onSuccess = { conflict ->
                                        if (newRentalId != oldRentalId && conflict != null) {
                                            onError("已存在相同房源和签约日期的合同")
                                            return@queryRentalRecordById
                                        }

                                        val updatedRental = RentalRecord().apply {
                                            id = newRentalId
                                            propertyId = targetProperty.id
                                            propertyName = targetProperty.propertyName
                                            tenantName = newName
                                            tenantPhone = newPhone
                                            tenantIdCard = newIdCard
                                            contractDate = newContractDate.toDate()
                                            rentStartDate = newRentStartDate.toDate()
                                            rentEndDate = calculateRentEndDate(newRentStartDate, newLeaseMonths).toDate()
                                            monthlyRent = newMonthlyRent
                                            propertyFee = newPropertyFee
                                            depositAmount = newDepositAmount
                                            depositStatus = newDepositStatus
                                            leaseMonths = newLeaseMonths
                                            paymentFrequency = newPaymentFrequency
                                            remark = newRemark
                                            reminderDaysBeforeDue = newReminderDaysBeforeDue
                                            isCompleted = allPaymentsCompleted
                                            createdBy = existing.createdBy
                                            createdAt = existing.createdAt
                                        }
                                        applyUpdateAudit(updatedRental)

                                        val regeneratedPayments = generateSchedule(
                                            rentalId = newRentalId,
                                            propertyId = targetProperty.id,
                                            contractDate = newContractDate,
                                            start = newRentStartDate,
                                            rent = newMonthlyRent,
                                            propertyFee = newPropertyFee,
                                            months = newLeaseMonths,
                                            freq = newPaymentFrequency,
                                            reminderDaysBeforeDue = newReminderDaysBeforeDue
                                        ).map { newPayment ->
                                            val matchedOldPayment = payments.firstOrNull {
                                                (it.periodNumber ?: 0) == (newPayment.periodNumber ?: 0)
                                            }
                                            if (matchedOldPayment != null) {
                                                newPayment.isPaid = matchedOldPayment.isPaid
                                                newPayment.payee = matchedOldPayment.payee
                                                newPayment.paymentMethod = matchedOldPayment.paymentMethod
                                                newPayment.receiptDate = matchedOldPayment.receiptDate
                                                newPayment.createdBy = matchedOldPayment.createdBy
                                                newPayment.createdAt = matchedOldPayment.createdAt
                                                if (matchedOldPayment.isPaid == true) {
                                                    newPayment.updatedBy = matchedOldPayment.updatedBy
                                                    newPayment.updatedAt = matchedOldPayment.updatedAt
                                                }
                                            }
                                            newPayment
                                        }

                                        val finalizeProperties = {
                                            syncEditedProperties(
                                                oldRentalId = oldRentalId.orEmpty(),
                                                oldPropertyId = existing.propertyId,
                                                oldPropertyName = existing.propertyName,
                                                newProperty = targetProperty
                                            )
                                            refreshAllData()
                                            onSuccess()
                                        }

                                        val persistRentalAndPayments = {
                                            repo.upsertRentalRecord(
                                                updatedRental,
                                                onSuccess = {
                                                    upsertSchedules(
                                                        repo = repo,
                                                        records = regeneratedPayments,
                                                        index = 0,
                                                        onDone = {
                                                            if (newRentalId == oldRentalId) {
                                                                finalizeProperties()
                                                            } else {
                                                                deletePaymentsSequentially(repo, payments, 0) {
                                                                    val oldRental = RentalRecord().apply {
                                                                        id = oldRentalId
                                                                        propertyId = existing.propertyId
                                                                        propertyName = existing.propertyName
                                                                    }
                                                                    repo.deleteRentalRecord(
                                                                        oldRental,
                                                                        onSuccess = { finalizeProperties() },
                                                                        onError = {
                                                                            val msg = it.message ?: "旧合同删除失败"
                                                                            initError = msg
                                                                            onError(msg)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onError = {
                                                            val msg = it.message ?: "收款计划保存失败"
                                                            initError = msg
                                                            onError(msg)
                                }
                                                    )
                                                },
                                                onError = {
                                                    val msg = it.message ?: "合同保存失败"
                                                    initError = msg
                                                    onError(msg)
                                                }
                                            )
                                        }

                                        if (newRentalId == oldRentalId) {
                                            deletePaymentsSequentially(repo, payments, 0) {
                                                persistRentalAndPayments()
                                            }
                                        } else {
                                            persistRentalAndPayments()
                                        }
                                    },
                                    onError = {
                                        val msg = it.message ?: "合同校验失败"
                                        initError = msg
                                        onError(msg)
                                    }
                                )
                            },
                            onError = onError
                        )
                    },
                    onError = {
                        val msg = it.message ?: "收款计划获取失败"
                        initError = msg
                        onError(msg)
                    }
                )
            },
            onError = {
                val msg = it.message ?: "合同获取失败"
                initError = msg
                onError(msg)
            }
        )
    }

*/

    fun updateRentalContractInfo(
        rentalId: String,
        newPropertyName: String,
        newName: String,
        newPhone: String,
        newIdCard: String,
        newContractDate: LocalDate,
        newRentStartDate: LocalDate,
        newMonthlyRent: Int,
        newPropertyFee: Int,
        newDepositAmount: Int,
        newDepositStatus: String,
        newLeaseMonths: Int,
        newPaymentFrequency: Int,
        newRemark: String,
        newReminderDaysBeforeDue: Int,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val repo = repository ?: return
        repo.queryRentalRecordById(
            rentalId,
            onSuccess = { record ->
                val existing = record ?: return@queryRentalRecordById
                repo.queryPaymentRecordsByRentalId(
                    rentalId,
                    onSuccess = { payments ->
                        ensureProperty(
                            propertyName = newPropertyName,
                            onResolved = { targetProperty ->
                                val oldRentalId = existing.id.orEmpty()
                                val newRentalId = buildRentalRecordId(targetProperty.id, newContractDate)
                                val allPaymentsCompleted =
                                    payments.isNotEmpty() && payments.all { it.isPaid == true }
                                repo.queryRentalRecordById(
                                    newRentalId,
                                    onSuccess = { conflict ->
                                        if (newRentalId != oldRentalId && conflict != null) {
                                            onError("已存在相同房源和签约日期的合同")
                                            return@queryRentalRecordById
                                        }

                                        val updatedRental = RentalRecord().apply {
                                            id = newRentalId
                                            propertyId = targetProperty.id
                                            propertyName = targetProperty.propertyName
                                            tenantName = newName
                                            tenantPhone = newPhone
                                            tenantIdCard = newIdCard
                                            contractDate = newContractDate.toDate()
                                            rentStartDate = newRentStartDate.toDate()
                                            rentEndDate = calculateRentEndDate(newRentStartDate, newLeaseMonths).toDate()
                                            monthlyRent = newMonthlyRent
                                            propertyFee = newPropertyFee
                                            depositAmount = newDepositAmount
                                            depositStatus = newDepositStatus
                                            leaseMonths = newLeaseMonths
                                            paymentFrequency = newPaymentFrequency
                                            remark = newRemark
                                            reminderDaysBeforeDue = newReminderDaysBeforeDue
                                            isCompleted = allPaymentsCompleted
                                            createdBy = existing.createdBy
                                            createdAt = existing.createdAt
                                        }
                                        applyUpdateAudit(updatedRental)

                                        val regeneratedPayments = generateSchedule(
                                            rentalId = newRentalId,
                                            propertyId = targetProperty.id,
                                            contractDate = newContractDate,
                                            start = newRentStartDate,
                                            rent = newMonthlyRent,
                                            propertyFee = newPropertyFee,
                                            months = newLeaseMonths,
                                            freq = newPaymentFrequency,
                                            reminderDaysBeforeDue = newReminderDaysBeforeDue
                                        ).map { newPayment ->
                                            val oldPayment = payments.firstOrNull {
                                                (it.periodNumber ?: 0) == (newPayment.periodNumber ?: 0)
                                            }
                                            if (oldPayment != null) {
                                                newPayment.isPaid = oldPayment.isPaid
                                                newPayment.payee = oldPayment.payee
                                                newPayment.paymentMethod = oldPayment.paymentMethod
                                                newPayment.receiptDate = oldPayment.receiptDate
                                                newPayment.createdBy = oldPayment.createdBy
                                                newPayment.createdAt = oldPayment.createdAt
                                                if (oldPayment.isPaid == true) {
                                                    newPayment.updatedBy = oldPayment.updatedBy
                                                    newPayment.updatedAt = oldPayment.updatedAt
                                                }
                                            }
                                            newPayment
                                        }
                                        updatedRental.isCompleted =
                                            regeneratedPayments.isNotEmpty() && regeneratedPayments.all { it.isPaid == true }

                                        val finishUpdate = {
                                            syncEditedProperties(
                                                oldRentalId = oldRentalId,
                                                oldPropertyId = existing.propertyId,
                                                oldPropertyName = existing.propertyName,
                                                newProperty = targetProperty
                                            )
                                            refreshAllData()
                                            onSuccess()
                                        }

                                        val persistNewData = {
                                            repo.upsertRentalRecord(
                                                updatedRental,
                                                onSuccess = {
                                                    upsertSchedules(
                                                        repo = repo,
                                                        records = regeneratedPayments,
                                                        index = 0,
                                                        onDone = {
                                                            if (newRentalId == oldRentalId) {
                                                                finishUpdate()
                                                            } else {
                                                                deletePaymentsSequentially(repo, payments, 0) {
                                                                    val oldRental = RentalRecord().apply {
                                                                        id = oldRentalId
                                                                        propertyId = existing.propertyId
                                                                        propertyName = existing.propertyName
                                                                    }
                                                                    repo.deleteRentalRecord(
                                                                        oldRental,
                                                                        onSuccess = { finishUpdate() },
                                                                        onError = {
                                                                            val msg = it.message ?: "旧合同删除失败"
                                                                            initError = msg
                                                                            onError(msg)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onError = {
                                                            val msg = it.message ?: "收款计划保存失败"
                                                            initError = msg
                                                            onError(msg)
                                                        }
                                                    )
                                                },
                                                onError = {
                                                    val msg = it.message ?: "合同保存失败"
                                                    initError = msg
                                                    onError(msg)
                                                }
                                            )
                                        }

                                        if (newRentalId == oldRentalId) {
                                            deletePaymentsSequentially(repo, payments, 0) { persistNewData() }
                                        } else {
                                            persistNewData()
                                        }
                                    },
                                    onError = {
                                        val msg = it.message ?: "合同校验失败"
                                        initError = msg
                                        onError(msg)
                                    }
                                )
                            },
                            onError = onError
                        )
                    },
                    onError = {
                        val msg = it.message ?: "收款计划获取失败"
                        initError = msg
                        onError(msg)
                    }
                )
            },
            onError = {
                val msg = it.message ?: "合同获取失败"
                initError = msg
                onError(msg)
            }
        )
    }

    fun updatePaymentAmount(rentalId: String, paymentId: String, newMonthlyRent: Int) {
        updatePayment(rentalId, paymentId, onSuccess = {}) { payment ->
            val monthsInPeriod = payment.monthsInPeriod ?: 1
            val propertyFeeAmount = payment.propertyFeeAmount ?: 0
            payment.monthlyRentSnapshot = newMonthlyRent
            payment.rentAmount = newMonthlyRent * monthsInPeriod
            payment.amount = (payment.rentAmount ?: 0) + propertyFeeAmount
        }
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
                            syncPropertyAvailabilityForRental(existing)
                            refreshAllData()
                            return@queryRentalRecordById
                        }

                        existing.isCompleted = shouldCompleted
                        applyUpdateAudit(existing)
                        repo.upsertRentalRecord(
                            existing,
                            onSuccess = {
                                syncPropertyAvailabilityForRental(existing)
                                refreshAllData()
                            },
                            onError = { initError = it.message }
                        )
                    },
                    onError = { initError = it.message }
                )
            },
            onError = { initError = it.message }
        )
    }

    private fun syncPropertyAvailabilityForRental(rental: RentalRecord) {
        val repo = repository ?: return
        val propertyId = rental.propertyId.orEmpty()
        val propertyName = rental.propertyName.orEmpty()
        if (propertyId.isBlank() && propertyName.isBlank()) return

        repo.queryAllRentalRecords(
            onSuccess = { rentals ->
                val hasUnfinishedContract = rentals.any {
                    it.id != rental.id &&
                        it.isCompleted != true &&
                        (
                            (propertyId.isNotBlank() && it.propertyId == propertyId) ||
                                (propertyName.isNotBlank() && it.propertyName == propertyName)
                            )
                }
                val shouldAvailable = rental.isCompleted == true && !hasUnfinishedContract
                repo.queryAllProperties(
                    onSuccess = { properties ->
                        val property = properties.firstOrNull {
                            (propertyId.isNotBlank() && it.id == propertyId) ||
                                (propertyName.isNotBlank() && it.propertyName == propertyName)
                        } ?: return@queryAllProperties
                        if (property.isAvailable == shouldAvailable) return@queryAllProperties

                        property.isAvailable = shouldAvailable
                        repo.upsertProperty(property, onSuccess = {}, onError = { initError = it.message })
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

    private fun syncEditedProperties(
        oldRentalId: String,
        oldPropertyId: String?,
        oldPropertyName: String?,
        newProperty: Property
    ) {
        val repo = repository ?: return
        newProperty.isAvailable = false
        repo.upsertProperty(newProperty, onSuccess = {}, onError = { initError = it.message })

        val oldId = oldPropertyId.orEmpty()
        val oldName = oldPropertyName.orEmpty()
        if (oldId == newProperty.id && oldName == newProperty.propertyName) return

        val stillOccupied = _rentals.any {
            it.id != oldRentalId &&
                !it.isCompleted &&
                (it.propertyId == oldId || it.propertyName == oldName)
        }
        if (stillOccupied) return

        repo.queryAllProperties(
            onSuccess = { properties ->
                val oldProperty = properties.firstOrNull {
                    (oldId.isNotBlank() && it.id == oldId) ||
                        (oldName.isNotBlank() && it.propertyName == oldName)
                } ?: return@queryAllProperties

                oldProperty.isAvailable = true
                repo.upsertProperty(oldProperty, onSuccess = {}, onError = { initError = it.message })
            },
            onError = { initError = it.message }
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

    private fun upsertRentalMigrations(
        repo: CloudRentalRepository,
        rentals: List<RentalRecord>,
        index: Int,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (index >= rentals.size) {
            onDone()
            return
        }
        repo.upsertRentalRecord(
            rentals[index],
            onSuccess = { upsertRentalMigrations(repo, rentals, index + 1, onDone, onError) },
            onError = onError
        )
    }

    private fun upsertPaymentMigrations(
        repo: CloudRentalRepository,
        payments: List<PaymentRecord>,
        index: Int,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (index >= payments.size) {
            onDone()
            return
        }
        repo.upsertPaymentRecord(
            payments[index],
            onSuccess = { upsertPaymentMigrations(repo, payments, index + 1, onDone, onError) },
            onError = onError
        )
    }

    private fun deletePaymentsSequentially(
        repo: CloudRentalRepository,
        records: List<PaymentRecord>,
        index: Int,
        onError: (() -> Unit)? = null,
        onDone: () -> Unit
    ) {
        if (index >= records.size) {
            onDone()
            return
        }
        repo.deletePaymentRecord(
            records[index],
            onSuccess = { deletePaymentsSequentially(repo, records, index + 1, onError, onDone) },
            onError = {
                initError = it.message
                onError?.invoke()
            }
        )
    }

    private fun generateSchedule(
        rentalId: String,
        propertyId: String,
        contractDate: LocalDate,
        start: LocalDate,
        rent: Int,
        propertyFee: Int,
        months: Int,
        freq: Int,
        reminderDaysBeforeDue: Int
    ): List<PaymentRecord> {
        val schedule = mutableListOf<PaymentRecord>()
        val actualFreq = resolveMonthsPerPeriod(freq, months)
        val totalPeriods = if (actualFreq > 0) months / actualFreq else 1
        val rentAmountPerPeriod = rent * actualFreq
        val propertyFeeAmountPerPeriod = propertyFee * actualFreq
        val amountPerPeriod = rentAmountPerPeriod + propertyFeeAmountPerPeriod

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
                    this.rentAmount = rentAmountPerPeriod
                    this.propertyFeeAmount = propertyFeeAmountPerPeriod
                    this.monthlyRentSnapshot = rent
                    this.monthsInPeriod = actualFreq
                    this.remark = null
                    amount = amountPerPeriod
                    periodStartDate = periodStart.toDate()
                    periodEndDate = periodEnd.toDate()
                    dueDate = periodStart.toDate()
                    reminderDate = periodStart.minusDays(reminderDaysBeforeDue.toLong()).toDate()
                    isPaid = false
                    applyCreateAudit(this)
                }
            )
        }
        return schedule
    }

    private fun resolveMonthsPerPeriod(paymentFrequency: Int, leaseMonths: Int): Int {
        return if (paymentFrequency == 0) leaseMonths else paymentFrequency
    }

    private fun calculateRentEndDate(rentStartDate: LocalDate, leaseMonths: Int): LocalDate {
        return rentStartDate.plusMonths(leaseMonths.toLong()).minusDays(1)
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
            rentEndDate = rentEndDate?.toLocalDate()
                ?: calculateRentEndDate(rentStartDate?.toLocalDate() ?: LocalDate.now(), leaseMonths ?: 0),
            monthlyRent = monthlyRent ?: 0,
            propertyFee = propertyFee ?: 0,
            depositAmount = depositAmount ?: 0,
            depositStatus = depositStatus.orEmpty().ifBlank { "\u672a\u652f\u4ed8" },
            remark = remark.orEmpty(),
            reminderDaysBeforeDue = (reminderDaysBeforeDue ?: 15).coerceAtLeast(0),
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
            rentAmount = rentAmount ?: (amount ?: 0),
            propertyFeeAmount = propertyFeeAmount ?: 0,
            monthlyRentSnapshot = monthlyRentSnapshot ?: ((rentAmount ?: amount ?: 0) / (monthsInPeriod ?: 1).coerceAtLeast(1)),
            monthsInPeriod = (monthsInPeriod ?: 1).coerceAtLeast(1),
            remark = remark.orEmpty(),
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
