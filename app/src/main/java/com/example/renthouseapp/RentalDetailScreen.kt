package com.example.renthouseapp

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

private const val DEFAULT_PAYEE_NAME = "罗琪琛"
private val DEPOSIT_STATUS_OPTIONS = listOf("未支付", "已支付", "已退还", "已扣除")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun RentalDetailScreen(rental: UiRental, viewModel: RentalViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val payeeOptions = remember(viewModel.availablePayees, viewModel.currentLoginUser) {
        val queriedPayees = viewModel.availablePayees
        val currentLoginName = viewModel.currentLoginUser?.loginName?.trim().orEmpty()
        when {
            queriedPayees.isNotEmpty() && currentLoginName.isNotEmpty() && queriedPayees.contains(currentLoginName) -> queriedPayees
            queriedPayees.isNotEmpty() && currentLoginName.isNotEmpty() -> listOf(currentLoginName) + queriedPayees
            queriedPayees.isNotEmpty() -> queriedPayees
            currentLoginName.isNotEmpty() -> listOf(currentLoginName)
            else -> emptyList()
        }
    }
    val defaultPayee = payeeOptions.firstOrNull { it == DEFAULT_PAYEE_NAME }
        ?: payeeOptions.firstOrNull()
        ?: DEFAULT_PAYEE_NAME
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showEditAmountDialog by remember { mutableStateOf(false) }
    var editingPaymentId by remember { mutableStateOf<String?>(null) }
    var newAmountInput by remember { mutableStateOf("") }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var receiptPaymentId by remember { mutableStateOf<String?>(null) }
    var receiptPayee by remember { mutableStateOf(defaultPayee) }
    var receiptMethod by remember { mutableStateOf("\u5fae\u4fe1") }
    var receiptDate by remember { mutableStateOf(LocalDate.now()) }
    var showPaymentSuccessDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editIdCard by remember { mutableStateOf("") }
    var editMonthlyRent by remember { mutableStateOf("") }
    var editPropertyFee by remember { mutableStateOf("") }
    var editDepositAmount by remember { mutableStateOf("") }
    var editDepositStatus by remember { mutableStateOf("\u672a\u652f\u4ed8") }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isRefreshing,
        onRefresh = {
            viewModel.refreshFromUser(
                onSuccess = { isEmpty ->
                    Toast.makeText(context, if (isEmpty) "\u6682\u65e0\u6570\u636e" else "\u6570\u636e\u5237\u65b0\u6210\u529f", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(context, "\u6570\u636e\u83b7\u53d6\u5931\u8d25", Toast.LENGTH_SHORT).show()
                }
            )
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u5408\u540c\u8be6\u60c5") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val nextPay = rental.paymentSchedule.firstOrNull { !it.isPaid }
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, "\u6536\u79df\u63d0\u9192\uff1a${rental.propertyName}")
                            putExtra(CalendarContract.Events.DESCRIPTION, "\u79df\u5ba2\uff1a${rental.tenantName}\n\u7535\u8bdd\uff1a${rental.tenantPhone}")
                            putExtra(CalendarContract.Events.ALL_DAY, true)
                            nextPay?.let {
                                val cal = Calendar.getInstance().apply {
                                    set(it.reminderDate.year, it.reminderDate.monthValue - 1, it.reminderDate.dayOfMonth)
                                }
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                            }
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.DateRange, contentDescription = "\u540c\u6b65\u65e5\u5386", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "\u5220\u9664\u5408\u540c", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u3010${rental.propertyName}\u3011\u79df\u7ea6\u4fe1\u606f",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = {
                                editName = rental.tenantName
                                editPhone = rental.tenantPhone
                                editIdCard = rental.tenantIdCard
                                editMonthlyRent = rental.monthlyRent.toString()
                                editPropertyFee = rental.propertyFee.toString()
                                editDepositAmount = rental.depositAmount.toString()
                                editDepositStatus = rental.depositStatus
                                showEditDialog = true
                            }) {
                                Text("\u4fee\u6539\u4fe1\u606f")
                            }
                        }
                        HorizontalDivider()
                        DetailRow("\u79df\u5ba2", "${rental.tenantName} (${rental.tenantPhone})")
                        if (rental.tenantIdCard.isNotBlank()) {
                            DetailRow("\u8bc1\u4ef6\u53f7\u7801", rental.tenantIdCard)
                        }
                        DetailRow("\u5408\u540c\u7b7e\u8ba2\u65e5", rental.contractDate.toString())
                        DetailRow("\u79df\u91d1\u8d77\u59cb\u65e5", rental.rentStartDate.toString())
                        DetailRow("\u521d\u59cb\u6708\u79df\u91d1", "\u00a5${rental.monthlyRent}")
                        DetailRow("\u6bcf\u6708\u7269\u4e1a\u8d39", "\u00a5${rental.propertyFee}")
                        DetailRow("\u62bc\u91d1", "\u00a5${rental.depositAmount}")
                        DetailRow("\u62bc\u91d1\u72b6\u6001", rental.depositStatus)
                        DetailRow("\u5468\u671f", "${rental.leaseMonths}\u4e2a\u6708\uff08\u6bcf${rental.paymentFrequency}\u4e2a\u6708\u4e00\u4ed8\uff09")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DetailRow("\u521b\u5efa\u4eba", rental.createdBy.ifBlank { "unknown" })
                        DetailRow("\u521b\u5efa\u65f6\u95f4", rental.createdAt.formatOrDash())
                        DetailRow("\u66f4\u65b0\u4eba", rental.updatedBy.ifBlank { "unknown" })
                        DetailRow("\u66f4\u65b0\u65f6\u95f4", rental.updatedAt.formatOrDash())
                    }
                }

                Text("\u6536\u6b3e\u8ba1\u5212\u8868", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                rental.paymentSchedule.forEach { payment ->
                    PaymentRecordCard(
                        payment = payment,
                        propertyName = rental.propertyName,
                        tenantName = rental.tenantName,
                        onReceiptClick = {
                            receiptPaymentId = payment.id
                            receiptPayee = payeeOptions.firstOrNull { payee -> payee == DEFAULT_PAYEE_NAME }
                                ?: payeeOptions.firstOrNull()
                                ?: DEFAULT_PAYEE_NAME
                            showReceiptDialog = true
                        },
                        onRevokeClick = { viewModel.revokePayment(rental.id, payment.id) },
                        onEditAmountClick = {
                            editingPaymentId = payment.id
                            newAmountInput = payment.amount.toString()
                            showEditAmountDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            PullRefreshIndicator(
                refreshing = viewModel.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showReceiptDialog && receiptPaymentId != null) {
        AlertDialog(
            onDismissRequest = { showReceiptDialog = false },
            title = { Text("\u786e\u8ba4\u6536\u6b3e\u660e\u7ec6") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (payeeOptions.isEmpty()) {
                        Text("\u672a\u67e5\u8be2\u5230\u6536\u6b3e\u4eba\uff0c\u8bf7\u5148\u68c0\u67e5 loginUser \u6570\u636e")
                    } else {
                        SingleChoiceDialogField(
                            label = "\u6536\u6b3e\u4eba",
                            options = payeeOptions,
                            selectedOption = receiptPayee,
                            onOptionSelected = { receiptPayee = it }
                        )
                    }
                    SingleChoiceDialogField(
                        label = "\u6536\u6b3e\u65b9\u5f0f",
                        options = listOf("\u5fae\u4fe1", "\u94f6\u884c\u8f6c\u8d26", "\u652f\u4ed8\u5b9d", "\u73b0\u91d1"),
                        selectedOption = receiptMethod,
                        onOptionSelected = { receiptMethod = it }
                    )
                    NativeDatePickerField("\u5b9e\u9645\u6536\u6b3e\u65e5\u671f", receiptDate) { receiptDate = it }
                }
            },
            confirmButton = {
                Button(
                    enabled = receiptPayee.isNotBlank(),
                    onClick = {
                        viewModel.confirmPayment(
                            rental.id,
                            receiptPaymentId!!,
                            receiptPayee,
                            receiptMethod,
                            receiptDate,
                            onSuccess = {
                                showReceiptDialog = false
                                showPaymentSuccessDialog = true
                            }
                        )
                    }
                ) {
                    Text("\u786e\u8ba4\u5165\u8d26")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceiptDialog = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }

    if (showEditAmountDialog && editingPaymentId != null) {
        AlertDialog(
            onDismissRequest = { showEditAmountDialog = false },
            title = { Text("\u4fee\u6539\u6536\u6b3e\u91d1\u989d") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "\u4ec5\u4fee\u6539\u5f53\u524d\u671f\u8d26\u5355\u91d1\u989d\uff0c\u4e0d\u5f71\u54cd\u5176\u4ed6\u671f\u6570\u3002",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newAmountInput,
                        onValueChange = { newAmountInput = it },
                        label = { Text("\u65b0\u91d1\u989d") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    newAmountInput.toIntOrNull()?.let {
                        if (it >= 0) {
                            viewModel.updatePaymentAmount(rental.id, editingPaymentId!!, it)
                            showEditAmountDialog = false
                        }
                    }
                }) {
                    Text("\u4fdd\u5b58")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAmountDialog = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("\u4fee\u6539\u5408\u540c\u4fe1\u606f") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("\u59d3\u540d") })
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("\u624b\u673a\u53f7") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editIdCard,
                        onValueChange = { editIdCard = it },
                        label = { Text("\u8bc1\u4ef6\u53f7\u7801\uff08\u9009\u586b\uff09") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editMonthlyRent,
                            onValueChange = { editMonthlyRent = it },
                            label = { Text("\u6708\u79df\u91d1(\u5143)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = editPropertyFee,
                            onValueChange = { editPropertyFee = it },
                            label = { Text("\u7269\u4e1a\u8d39(\u5143)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editDepositAmount,
                            onValueChange = { editDepositAmount = it },
                            label = { Text("\u62bc\u91d1(\u5143)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        SingleChoiceDialogField(
                            label = "\u62bc\u91d1\u72b6\u6001",
                            options = DEPOSIT_STATUS_OPTIONS,
                            selectedOption = editDepositStatus,
                            onOptionSelected = { editDepositStatus = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val monthlyRent = editMonthlyRent.toIntOrNull()
                    val propertyFee = editPropertyFee.toIntOrNull()
                    val depositAmount = editDepositAmount.toIntOrNull()
                    if (
                        editName.isNotBlank() &&
                        editPhone.matches(Regex("^\\d{11}$")) &&
                        monthlyRent != null &&
                        propertyFee != null &&
                        depositAmount != null
                    ) {
                        viewModel.updateRentalContractInfo(
                            rentalId = rental.id,
                            newName = editName,
                            newPhone = editPhone,
                            newIdCard = editIdCard,
                            newMonthlyRent = monthlyRent,
                            newPropertyFee = propertyFee,
                            newDepositAmount = depositAmount,
                            newDepositStatus = editDepositStatus,
                            onSuccess = { showUpdateSuccessDialog = true }
                        )
                        showEditDialog = false
                    }
                }) {
                    Text("\u4fdd\u5b58")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("\u5220\u9664\u5408\u540c", color = MaterialTheme.colorScheme.error) },
            text = { Text("\u786e\u5b9a\u8981\u6c38\u4e45\u5220\u9664\u8fd9\u4efd\u5408\u540c\u5417\uff1f") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteDialog = false
                        onBackClick()
                        viewModel.deleteRental(rental.id)
                    }
                ) {
                    Text("\u786e\u8ba4\u5220\u9664")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }

    if (showPaymentSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("\u63d0\u793a") },
            text = { Text("\u6536\u6b3e\u8bb0\u5f55\u6210\u529f") },
            confirmButton = {
                TextButton(onClick = { showPaymentSuccessDialog = false }) {
                    Text("\u786e\u5b9a")
                }
            }
        )
    }

    if (showUpdateSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateSuccessDialog = false },
            title = { Text("\u63d0\u793a") },
            text = { Text("\u66f4\u6539\u4fe1\u606f\u6210\u529f") },
            confirmButton = {
                TextButton(onClick = { showUpdateSuccessDialog = false }) {
                    Text("\u786e\u5b9a")
                }
            }
        )
    }
}

@Composable
fun PaymentRecordCard(
    payment: UiPaymentRecord,
    propertyName: String,
    tenantName: String,
    onReceiptClick: () -> Unit,
    onRevokeClick: () -> Unit,
    onEditAmountClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val containerColor = if (payment.isPaid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (payment.isPaid) 0.dp else 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u7b2c${payment.periodNumber}\u671f\u6536\u6b3e", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (payment.isPaid) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text("\u5df2\u7ed3\u6e05", modifier = Modifier.padding(4.dp))
                    }
                } else {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("\u5f85\u6536\u6b3e", modifier = Modifier.padding(4.dp))
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DetailRow("\u79df\u671f", "${payment.periodStartDate} \u81f3 ${payment.periodEndDate}")
            DetailRow("\u5e94\u6536\u91d1\u989d", "\u00a5${payment.amount}", isHighlight = !payment.isPaid, showEditIcon = !payment.isPaid, onEditClick = onEditAmountClick)
            DetailRow("\u79df\u91d1", "\u00a5${payment.rentAmount}")
            if (payment.propertyFeeAmount > 0) {
                DetailRow("\u7269\u4e1a\u8d39", "\u00a5${payment.propertyFeeAmount}")
            }
            if (!payment.isPaid) {
                DetailRow("\u63d0\u9192\u65e5\u671f", "${payment.reminderDate}\uff08\u63d0\u524d15\u5929\uff09", color = MaterialTheme.colorScheme.error)
            }
            DetailRow("\u5e94\u7f34\u65e5\u671f", payment.dueDate.toString())

            if (payment.isPaid) {
                Text(
                    "\u5165\u8d26\u4fe1\u606f\uff1a${payment.receiptDate} | ${payment.payee} | ${payment.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!payment.isPaid) {
                    OutlinedButton(
                        onClick = {
                            val msg = buildCollectionReminderMessage(tenantName, propertyName, payment)
                            clipboardManager.setText(AnnotatedString(msg))
                            Toast.makeText(context, "\u50ac\u6536\u77ed\u4fe1\u5df2\u590d\u5236", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("\u590d\u5236\u50ac\u6536\u77ed\u4fe1")
                    }
                    Button(onClick = onReceiptClick) {
                        Text("\u786e\u8ba4\u6536\u6b3e")
                    }
                } else {
                    TextButton(onClick = onRevokeClick) {
                        Text("\u64a4\u9500\u786e\u8ba4", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showEditIcon: Boolean = false,
    onEditClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
                color = if (isHighlight) MaterialTheme.colorScheme.primary else color
            )
            if (showEditIcon) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "\u4fee\u6539",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun LocalDateTime?.formatOrDash(): String {
    return this?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "-"
}

private fun buildCollectionReminderMessage(
    tenantName: String,
    propertyName: String,
    payment: UiPaymentRecord
): String {
    val propertyFeeText = if (payment.propertyFeeAmount > 0) {
        "，其中物业费￥${payment.propertyFeeAmount}"
    } else {
        ""
    }
    return "你好 $tenantName，【$propertyName】（${payment.periodStartDate} 至 ${payment.periodEndDate}）第${payment.periodNumber}期应缴费用￥${payment.amount}$propertyFeeText，将于 ${payment.dueDate} 到期，请按时缴纳，谢谢！"
}

@Composable
fun CollectionPaymentCard(
    item: UiCollectionItem,
    onReceiptClick: () -> Unit,
    onRevokeClick: () -> Unit,
    onCopySuccess: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val payment = item.payment
    val containerColor = if (payment.isPaid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (payment.isPaid) 0.dp else 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.propertyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (payment.isPaid) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text("\u5df2\u7ed3\u6e05", modifier = Modifier.padding(4.dp))
                    }
                } else {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("\u5f85\u6536\u6b3e", modifier = Modifier.padding(4.dp))
                    }
                }
            }
            DetailRow("\u79df\u5ba2", item.tenantName)
            DetailRow("\u79df\u671f", "${payment.periodStartDate} \u81f3 ${payment.periodEndDate}")
            DetailRow("\u5e94\u6536\u91d1\u989d", "\u00a5${payment.amount}", isHighlight = !payment.isPaid)
            DetailRow("\u79df\u91d1", "\u00a5${payment.rentAmount}")
            if (payment.propertyFeeAmount > 0) {
                DetailRow("\u7269\u4e1a\u8d39", "\u00a5${payment.propertyFeeAmount}")
            }
            DetailRow("\u5e94\u7f34\u65e5\u671f", payment.dueDate.toString())
            DetailRow("\u63d0\u9192\u65e5\u671f", payment.reminderDate.toString(), color = MaterialTheme.colorScheme.error)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!payment.isPaid) {
                    OutlinedButton(
                        onClick = {
                            val msg = buildCollectionReminderMessage(item.tenantName, item.propertyName, payment)
                            clipboardManager.setText(AnnotatedString(msg))
                            onCopySuccess()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("\u590d\u5236\u50ac\u6536\u77ed\u4fe1")
                    }
                    Button(onClick = onReceiptClick) {
                        Text("\u786e\u8ba4\u6536\u6b3e")
                    }
                } else {
                    TextButton(onClick = onRevokeClick) {
                        Text("\u64a4\u9500\u786e\u8ba4", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
