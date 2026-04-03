package com.example.renthouseapp

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalDetailScreen(rental: UiRental, viewModel: RentalViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    var showEditAmountDialog by remember { mutableStateOf(false) }
    var editingPaymentId by remember { mutableStateOf<String?>(null) }
    var newAmountInput by remember { mutableStateOf("") }

    // 收款表单状态
    var showReceiptDialog by remember { mutableStateOf(false) }
    var receiptPaymentId by remember { mutableStateOf<String?>(null) }
    var receiptPayee by remember { mutableStateOf("吴雪梅") }
    var receiptMethod by remember { mutableStateOf("微信") }
    var receiptDate by remember { mutableStateOf(LocalDate.now()) }

    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editIdCard by remember { mutableStateOf("") }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = {
        isRefreshing = true
        viewModel.refreshAllData()
    })
    LaunchedEffect(viewModel.rentals.size) {
        if (isRefreshing) isRefreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("房源详情") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    // 需求7：一键同步至日历按钮
                    IconButton(onClick = {
                        val nextPay = rental.paymentSchedule.firstOrNull { !it.isPaid }
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, "收租提醒：${rental.propertyName}")
                            putExtra(CalendarContract.Events.DESCRIPTION, "租客：${rental.tenantName}\n电话：${rental.tenantPhone}")
                            putExtra(CalendarContract.Events.ALL_DAY, true)
                            nextPay?.let {
                                val cal = Calendar.getInstance().apply { set(it.reminderDate.year, it.reminderDate.monthValue - 1, it.reminderDate.dayOfMonth) }
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                            }
                        }
                        context.startActivity(intent)
                    }) { Icon(Icons.Default.DateRange, "同步日程", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "删除合同", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "【${rental.propertyName}】租约信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { editName = rental.tenantName; editPhone = rental.tenantPhone; editIdCard = rental.tenantIdCard; showEditDialog = true }) { Text("修改信息") }
                    }
                    HorizontalDivider()
                    DetailRow("租客", "${rental.tenantName} (${rental.tenantPhone})")
                    if (rental.tenantIdCard.isNotBlank()) DetailRow("证件号码", rental.tenantIdCard)
                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow("合同签订日", rental.contractDate.toString())
                    DetailRow("租金起始日", rental.rentStartDate.toString())
                    DetailRow("初始月租金", "￥${rental.monthlyRent}")
                    DetailRow("周期", "${rental.leaseMonths}个月 (每${rental.paymentFrequency}个月交)")
                }
            }

            Text("收款计划表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            rental.paymentSchedule.forEach { payment ->
                PaymentRecordCard(
                    payment = payment, propertyName = rental.propertyName, tenantName = rental.tenantName,
                    onReceiptClick = { receiptPaymentId = payment.id; showReceiptDialog = true },
                    onRevokeClick = { viewModel.revokePayment(rental.id, payment.id) },
                    onEditAmountClick = { editingPaymentId = payment.id; newAmountInput = payment.amount.toString(); showEditAmountDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }

        // ====== 需求2：确认收款详尽表单弹窗 ======
        if (showReceiptDialog && receiptPaymentId != null) {
            AlertDialog(
                onDismissRequest = { showReceiptDialog = false },
                title = { Text("确认收款明细") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SingleChoiceDialogField("收款人", listOf("吴雪梅", "罗琪琛", "吴应树", "王彦虎", "王一帆"), receiptPayee) { receiptPayee = it }
                        SingleChoiceDialogField("收款方式", listOf("微信", "银行转账", "支付宝", "现金"), receiptMethod) { receiptMethod = it }
                        NativeDatePickerField("实际收款日期", receiptDate) { receiptDate = it }
                    }
                },
                confirmButton = { Button(onClick = { viewModel.confirmPayment(rental.id, receiptPaymentId!!, receiptPayee, receiptMethod, receiptDate); showReceiptDialog = false }) { Text("确认入账") } },
                dismissButton = { TextButton(onClick = { showReceiptDialog = false }) { Text("取消") } }
            )
        }

        // 需求4：改价弹窗 (不再联动)
        if (showEditAmountDialog && editingPaymentId != null) {
            AlertDialog(
                onDismissRequest = { showEditAmountDialog = false },
                title = { Text("修改收款金额") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("仅修改当前期账单的金额，不影响其他期数。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = newAmountInput, onValueChange = { newAmountInput = it }, label = { Text("新金额 (元)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { Button(onClick = { newAmountInput.toIntOrNull()?.let { if(it >= 0) { viewModel.updatePaymentAmount(rental.id, editingPaymentId!!, it); showEditAmountDialog = false } } }) { Text("保存修改") } },
                dismissButton = { TextButton(onClick = { showEditAmountDialog = false }) { Text("取消") } }
            )
        }

        if (showEditDialog) {
            AlertDialog(onDismissRequest = { showEditDialog = false }, title = { Text("修改租客信息") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("姓名") }); OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("手机号") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(value = editIdCard, onValueChange = { editIdCard = it }, label = { Text("证件号码 (选填)") }) } }, confirmButton = { Button(onClick = { if (editName.isNotBlank() && editPhone.matches(Regex("^\\d{11}$"))) { viewModel.updateTenantInfo(rental.id, editName, editPhone, editIdCard); showEditDialog = false } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("取消") } })
        }
        if (showDeleteDialog) {
            AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text("⚠️ 删除确认", color = MaterialTheme.colorScheme.error) }, text = { Text("确定要删除合同吗？无法恢复！") }, confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { showDeleteDialog = false; onBackClick(); viewModel.deleteRental(rental.id) }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } })
        }
    }
}

@Composable
fun PaymentRecordCard(payment: UiPaymentRecord, propertyName: String, tenantName: String, onReceiptClick: () -> Unit, onRevokeClick: () -> Unit, onEditAmountClick: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val containerColor = if (payment.isPaid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(if (payment.isPaid) 0.dp else 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("第 ${payment.periodNumber} 次收款", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (payment.isPaid) Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("已结清", modifier = Modifier.padding(4.dp)) }
                else Badge(containerColor = MaterialTheme.colorScheme.error) { Text("待收款", modifier = Modifier.padding(4.dp)) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 需求1：日期范围
            DetailRow("租期", "${payment.periodStartDate} 至 ${payment.periodEndDate}")
            DetailRow("应收金额", "￥${payment.amount}", isHighlight = !payment.isPaid, showEditIcon = !payment.isPaid, onEditClick = onEditAmountClick)

            if (!payment.isPaid) DetailRow("催收时间", "${payment.reminderDate} (提前15天)", color = MaterialTheme.colorScheme.error)
            DetailRow("应缴时间", payment.dueDate.toString())

            if (payment.isPaid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("入账信息：${payment.receiptDate} | ${payment.payee} | ${payment.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!payment.isPaid) {
                    OutlinedButton(
                        onClick = {
                            // 需求1：包含日期的短信模板
                            val msg = "你好 $tenantName，【$propertyName】（${payment.periodStartDate} 至 ${payment.periodEndDate}）的第${payment.periodNumber}期租金（￥${payment.amount}）将于 ${payment.dueDate} 到期，请按时缴纳，谢谢！"
                            clipboardManager.setText(AnnotatedString(msg))
                            Toast.makeText(context, "短信已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("复制催收短信") }
                    Button(onClick = onReceiptClick) { Text("确认收到款") }
                } else {
                    TextButton(onClick = onRevokeClick) { Text("撤销/取消确认", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isHighlight: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface, showEditIcon: Boolean = false, onEditClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal, color = if (isHighlight) MaterialTheme.colorScheme.primary else color)
            if (showEditIcon) IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp).padding(start = 4.dp)) { Icon(Icons.Default.Edit, contentDescription = "修改", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
        }
    }
}