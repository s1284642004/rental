package com.example.renthouseapp

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private const val DEFAULT_PAYEE_NAME = "罗琪琛"

data class UiCollectionItem(
    val rentalId: String,
    val propertyName: String,
    val tenantName: String,
    val tenantPhone: String,
    val payment: UiPaymentRecord
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PaymentCollectionScreen(viewModel: RentalViewModel) {
    val context = LocalContext.current
    val tabs = listOf("\u5f85\u6536\u6b3e", "\u5df2\u6536\u6b3e")
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
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

    val pendingItems = viewModel.rentals
        .flatMap { rental ->
            rental.paymentSchedule
                .filter { !it.isPaid }
                .map { payment ->
                    UiCollectionItem(
                        rentalId = rental.id,
                        propertyName = rental.propertyName,
                        tenantName = rental.tenantName,
                        tenantPhone = rental.tenantPhone,
                        payment = payment
                    )
                }
        }
        .sortedByNearestDate()

    val paidItems = viewModel.rentals
        .flatMap { rental ->
            rental.paymentSchedule
                .filter { it.isPaid }
                .map { payment ->
                    UiCollectionItem(
                        rentalId = rental.id,
                        propertyName = rental.propertyName,
                        tenantName = rental.tenantName,
                        tenantPhone = rental.tenantPhone,
                        payment = payment
                    )
                }
        }
        .sortedByLatestUpdated()

    var showReceiptDialog by remember { mutableStateOf(false) }
    var receiptItem by remember { mutableStateOf<UiCollectionItem?>(null) }
    var receiptPayee by remember { mutableStateOf(defaultPayee) }
    var receiptMethod by remember { mutableStateOf("\u5fae\u4fe1") }
    var receiptDate by remember { mutableStateOf(LocalDate.now()) }
    var showPaymentSuccessDialog by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val displayItems = if (page == 0) pendingItems else paidItems
                val emptyText = if (page == 0) "\u6682\u65e0\u5f85\u6536\u6b3e\u8bb0\u5f55" else "\u6682\u65e0\u5df2\u6536\u6b3e\u8bb0\u5f55"

                if (displayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emptyText)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayItems) { item ->
                            CollectionPaymentCard(
                                item = item,
                                onReceiptClick = {
                                    receiptItem = item
                                    receiptPayee = payeeOptions.firstOrNull { payee -> payee == DEFAULT_PAYEE_NAME }
                                        ?: payeeOptions.firstOrNull()
                                        ?: DEFAULT_PAYEE_NAME
                                    showReceiptDialog = true
                                },
                                onRevokeClick = { viewModel.revokePayment(item.rentalId, item.payment.id) },
                                onSmsUnavailable = {
                                    Toast.makeText(context, "未找到可发送短信的应用", Toast.LENGTH_SHORT).show()
                                },
                                onDialUnavailable = {
                                    Toast.makeText(context, "未找到可拨打电话的应用", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = viewModel.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showReceiptDialog && receiptItem != null) {
        AlertDialog(
            onDismissRequest = { showReceiptDialog = false },
            title = { Text("\u786e\u8ba4\u6536\u6b3e") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${receiptItem!!.propertyName} / ${receiptItem!!.tenantName}",
                        fontWeight = FontWeight.Bold
                    )
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
                    NativeDatePickerField(
                        label = "\u5b9e\u9645\u6536\u6b3e\u65e5\u671f",
                        selectedDate = receiptDate,
                        onDateSelected = { receiptDate = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = receiptPayee.isNotBlank(),
                    onClick = {
                        val item = receiptItem ?: return@Button
                        viewModel.confirmPayment(
                            item.rentalId,
                            item.payment.id,
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
}

private fun List<UiCollectionItem>.sortedByNearestDate(): List<UiCollectionItem> {
    val today = LocalDate.now()
    return sortedWith(
        compareBy<UiCollectionItem> { abs(ChronoUnit.DAYS.between(today, it.payment.dueDate)) }
            .thenBy { it.payment.dueDate }
    )
}

private fun List<UiCollectionItem>.sortedByLatestUpdated(): List<UiCollectionItem> {
    return sortedWith(
        compareByDescending<UiCollectionItem> { it.payment.updatedAt }
            .thenByDescending { it.payment.dueDate }
    )
}
