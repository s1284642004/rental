package com.example.renthouseapp

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RentalListScreen(viewModel: RentalViewModel, onRentalClick: (UiRental) -> Unit) {
    val rentals = viewModel.rentals
    val context = LocalContext.current

    val tabs = listOf("全部", "进行中", "已完成")
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = {
        isRefreshing = true
        viewModel.refreshAllData()
    })

    LaunchedEffect(rentals.size) {
        if (isRefreshing) isRefreshing = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false

        if (readGranted && writeGranted) {
            val count = CalendarHelper.syncAllUnpaidToCalendar(context, rentals)
            if (count > 0) {
                Toast.makeText(context, "成功将 $count 条待收款提醒同步至日历！", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "暂无需要同步的待收款记录", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "需要日历权限才能同步哦~", Toast.LENGTH_SHORT).show()
        }
    }

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

            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "同步", modifier = Modifier.padding(end = 8.dp))
                Text("一键同步至日历", fontWeight = FontWeight.Bold)
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val displayRentals = when (page) {
                    1 -> rentals.filter { !it.isCompleted }
                    2 -> rentals.filter { it.isCompleted }
                    else -> rentals
                }

                if (displayRentals.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (rentals.isEmpty()) "目前还没有房源信息，下拉刷新后再试" else "该分类下暂无数据")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayRentals) { rental -> RentalCard(rental = rental, onClick = { onRentalClick(rental) }) }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
fun RentalCard(rental: UiRental, onClick: () -> Unit) {
    val containerColor = if (rental.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(if (rental.isCompleted) 0.dp else 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = rental.propertyName, style = MaterialTheme.typography.titleLarge, color = if (rental.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                if (rental.isCompleted) Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("已完成", modifier = Modifier.padding(4.dp)) }
                else Text(text = "￥${rental.monthlyRent}/月", color = MaterialTheme.colorScheme.primary)
            }
            Text(text = "租客: ${rental.tenantName} (${rental.tenantPhone})")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (rental.isCompleted) {
                Text("该合同账单已全部结清", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("本次应缴金额: ￥${rental.totalAmount}", fontWeight = FontWeight.Bold)
                Text("下次缴纳日期: ${rental.nextPaymentDate}")
                Text("催款提醒日期: ${rental.reminderDate}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
