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
import com.example.renthouseapp.ui.theme.LocalAppDimensions

private sealed interface RenewalListItem {
    val propertyName: String

    data class RenewalContract(
        val rental: UiRental,
        val hasRenewed: Boolean,
        val canRenew: Boolean
    ) : RenewalListItem {
        override val propertyName: String = rental.propertyName
    }

    data class Vacancy(
        override val propertyName: String
    ) : RenewalListItem
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RenewalScheduleScreen(
    viewModel: RentalViewModel,
    onRentalClick: (UiRental) -> Unit,
    onRenewClick: (UiRental) -> Unit
) {
    val ui = LocalAppDimensions.current
    val context = LocalContext.current

    val completedRenewItems = viewModel.rentals
        .filter { it.isCompleted }
        .map { rental ->
            val hasOtherUnfinishedContract = viewModel.rentals.any {
                it.propertyName == rental.propertyName && !it.isCompleted && it.id != rental.id
            }
            RenewalListItem.RenewalContract(
                rental = rental,
                hasRenewed = hasOtherUnfinishedContract,
                canRenew = !hasOtherUnfinishedContract
            )
        }
        .associateBy { it.propertyName }

    val items = buildList {
        addAll(completedRenewItems.values)
        addAll(
            viewModel.availableProperties
                .filter { it !in completedRenewItems.keys }
                .map { RenewalListItem.Vacancy(it) }
        )
    }.sortedBy { it.propertyName }

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
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无可招租或续租的房源")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ui.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ui.itemSpacing)
            ) {
                items(items, key = { item ->
                    when (item) {
                        is RenewalListItem.RenewalContract -> item.rental.id
                        is RenewalListItem.Vacancy -> "vacancy_${item.propertyName}"
                    }
                }) { item ->
                    when (item) {
                        is RenewalListItem.RenewalContract -> RenewalContractCard(
                            item = item,
                            onClick = { onRentalClick(item.rental) },
                            onRenewClick = { onRenewClick(item.rental) }
                        )
                        is RenewalListItem.Vacancy -> VacancyCard(propertyName = item.propertyName)
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
}

@Composable
private fun RenewalContractCard(
    item: RenewalListItem.RenewalContract,
    onClick: () -> Unit,
    onRenewClick: () -> Unit
) {
    val ui = LocalAppDimensions.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(ui.cardPadding),
            verticalArrangement = Arrangement.spacedBy(ui.itemSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.rental.propertyName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                OutlinedButton(
                    onClick = onRenewClick,
                    enabled = item.canRenew,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .height(ui.compactButtonHeight)
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
                Text("租期: ${item.rental.rentStartDate} 至 ${item.rental.rentEndDate}")
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
private fun VacancyCard(propertyName: String) {
    val ui = LocalAppDimensions.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(ui.cardPadding),
            verticalArrangement = Arrangement.spacedBy(ui.itemSpacing / 2)
        ) {
            Text(
                text = propertyName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "当前未出租，可在录入页直接新建合同",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
