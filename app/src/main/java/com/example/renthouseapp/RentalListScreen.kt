package com.example.renthouseapp

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.renthouseapp.ui.theme.CalendarGreen
import com.example.renthouseapp.ui.theme.White
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RentalListScreen(viewModel: RentalViewModel, onRentalClick: (UiRental) -> Unit) {
    val rentals = viewModel.rentals
    val context = LocalContext.current
    val tabs = listOf("全部", "待生效", "进行中", "已完成")
    val pagerState = rememberPagerState(initialPage = 2, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false

        if (readGranted && writeGranted) {
            val count = CalendarHelper.syncAllUnpaidToCalendar(context, rentals)
            if (count > 0) {
                Toast.makeText(context, "成功将 $count 条待收款提醒同步至日历", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "暂无需要同步的待收款记录", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "需要日历权限才能同步", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
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

            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CalendarGreen,
                    contentColor = White
                )
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "同步日历", modifier = Modifier.padding(end = 8.dp))
                Text("一键同步至日历", fontWeight = FontWeight.Bold)
            }

            if (viewModel.isInitialLoading && rentals.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val today = LocalDate.now()
                    val displayRentals = when (page) {
                        1 -> rentals.filter { !it.isCompleted && it.rentStartDate.isAfter(today) }
                        2 -> rentals.filter { !it.isCompleted && !it.rentStartDate.isAfter(today) }
                        3 -> rentals.filter { it.isCompleted }
                        else -> rentals
                    }

                    if (displayRentals.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (rentals.isEmpty()) "目前还没有合同数据，下拉刷新后再试"
                                else "该分类下暂无数据"
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayRentals, key = { it.id }) { rental ->
                                RentalCard(rental = rental, onClick = { onRentalClick(rental) })
                            }
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
}

@Composable
fun RentalCard(rental: UiRental, onClick: () -> Unit) {
    val isPendingEffective = !rental.isCompleted && rental.rentStartDate.isAfter(LocalDate.now())
    val containerColor = if (rental.isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (rental.isCompleted) 0.dp else 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = rental.propertyName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (rental.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                when {
                    rental.isCompleted -> Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text("已完成", modifier = Modifier.padding(4.dp))
                    }

                    isPendingEffective -> Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                        Text("待生效", modifier = Modifier.padding(4.dp))
                    }

                    else -> Text(
                        text = "￥${rental.monthlyRent + rental.propertyFee}/月",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(text = "租客: ${rental.tenantName}")
            Text(text = "电话: ${rental.tenantPhone}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (rental.isCompleted) {
                Text(
                    text = "该合同账单已全部结清",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (isPendingEffective) {
                Text("生效日期: ${rental.rentStartDate}", fontWeight = FontWeight.Bold)
                Text("租金到期日: ${rental.rentEndDate}")
                Text("催收提前: ${rental.reminderDaysBeforeDue} 天", color = MaterialTheme.colorScheme.primary)
            } else {
                Text("本次应缴金额: ￥${rental.totalAmount}", fontWeight = FontWeight.Bold)
                Text("下次缴纳日期: ${rental.nextPaymentDate ?: "合同已完成"}")
                Text("催款提醒日期: ${rental.reminderDate ?: "-"}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
