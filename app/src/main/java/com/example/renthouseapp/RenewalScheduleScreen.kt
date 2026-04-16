package com.example.renthouseapp

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class RenewalScheduleItem(
    val rental: UiRental,
    val contractEndDate: LocalDate,
    val daysUntilExpiry: Long,
    val isPendingEffective: Boolean,
    val hasRenewed: Boolean,
    val canRenew: Boolean
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RenewalScheduleScreen(
    viewModel: RentalViewModel,
    onRentalClick: (UiRental) -> Unit,
    onRenewClick: (UiRental) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val activeRentals = viewModel.rentals
        .filter { !it.isCompleted }
        .map { rental ->
            val isPendingEffective = rental.rentStartDate.isAfter(today)
            val hasOtherUnfinishedContract = viewModel.rentals.any {
                it.propertyName == rental.propertyName && !it.isCompleted && it.id != rental.id
            }
            val hasRenewed = !isPendingEffective && hasOtherUnfinishedContract
            RenewalScheduleItem(
                rental = rental,
                contractEndDate = rental.rentEndDate,
                daysUntilExpiry = ChronoUnit.DAYS.between(today, rental.rentEndDate),
                isPendingEffective = isPendingEffective,
                hasRenewed = hasRenewed,
                canRenew = !isPendingEffective && !hasOtherUnfinishedContract
            )
        }
        .sortedWith(
            compareBy<RenewalScheduleItem> { it.daysUntilExpiry }
                .thenBy { it.contractEndDate }
                .thenBy { it.rental.propertyName }
        )

    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isRefreshing,
        onRefresh = {
            viewModel.refreshFromUser(
                onSuccess = { isEmpty ->
                    Toast.makeText(
                        context,
                        if (isEmpty) "暂无数据" else "数据刷新成功",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onError = {
                    Toast.makeText(context, "数据获取失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
    )

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        if (activeRentals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无已出租的房源")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeRentals, key = { it.rental.id }) { item ->
                    RenewalScheduleCard(
                        item = item,
                        onClick = { onRentalClick(item.rental) },
                        onRenewClick = { onRenewClick(item.rental) }
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = viewModel.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun RenewalScheduleCard(
    item: RenewalScheduleItem,
    onClick: () -> Unit,
    onRenewClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.rental.propertyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                OutlinedButton(
                    onClick = onRenewClick,
                    enabled = item.canRenew,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("续租")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("租客: ${item.rental.tenantName}")
                Text("电话: ${item.rental.tenantPhone}")
                Text("租期: ${item.rental.rentStartDate} 至 ${item.contractEndDate}")
                Text(
                    text = formatExpiryText(item.daysUntilExpiry),
                    color = expiryColor(item.daysUntilExpiry),
                    fontWeight = FontWeight.Bold
                )
            }

            if (item.hasRenewed) {
                Text(
                    text = "该房源已续租，请至待生效合同中查看",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun expiryColor(daysUntilExpiry: Long) = when {
    daysUntilExpiry < 0 -> MaterialTheme.colorScheme.error
    daysUntilExpiry <= 30 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

private fun formatExpiryText(daysUntilExpiry: Long): String = when {
    daysUntilExpiry < 0 -> "已到期 ${kotlin.math.abs(daysUntilExpiry)} 天"
    daysUntilExpiry == 0L -> "今天到期"
    else -> "离到期还有 $daysUntilExpiry 天"
}
