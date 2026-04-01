package com.example.renthouseapp

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
// 【精准对齐】引用 model 包下的 Rental，解决冲突
import com.example.renthouseapp.model.Rental
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalEntryScreen(viewModel: RentalViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 你写好的所有状态变量，一个没少
    var propertyName by remember { mutableStateOf("") }
    var tenantName by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }
    var tenantIdCard by remember { mutableStateOf("") }
    var monthlyRent by remember { mutableStateOf("") }
    var leaseMonths by remember { mutableStateOf("") }
    var paymentFrequency by remember { mutableStateOf("") }

    // 你的日期处理状态
    var contractDate by remember { mutableStateOf(LocalDate.now()) }
    var rentStartDate by remember { mutableStateOf(LocalDate.now()) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- 你的原始 UI 布局逻辑 ---
            OutlinedTextField(value = propertyName, onValueChange = { propertyName = it }, label = { Text("房屋名称") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = tenantPhone, onValueChange = { tenantPhone = it }, label = { Text("联系电话") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = tenantIdCard, onValueChange = { tenantIdCard = it }, label = { Text("身份证号/证件号") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            // 你的日历处理逻辑：合同日期
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "合同签订日期: ${contractDate.format(dateFormatter)}")
                Button(onClick = {
                    DatePickerDialog(context, { _, y, m, d ->
                        contractDate = LocalDate.of(y, m + 1, d)
                    }, contractDate.year, contractDate.monthValue - 1, contractDate.dayOfMonth).show()
                }) { Text("选择日期") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 你的日历处理逻辑：起租日期
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "租期开始日期: ${rentStartDate.format(dateFormatter)}")
                Button(onClick = {
                    DatePickerDialog(context, { _, y, m, d ->
                        rentStartDate = LocalDate.of(y, m + 1, d)
                    }, rentStartDate.year, rentStartDate.monthValue - 1, rentStartDate.dayOfMonth).show()
                }) { Text("选择日期") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = monthlyRent, onValueChange = { monthlyRent = it }, label = { Text("月租金金额") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = leaseMonths, onValueChange = { leaseMonths = it }, label = { Text("租赁总月数") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = paymentFrequency, onValueChange = { paymentFrequency = it }, label = { Text("支付频率(几个月付一次，0为一次性)") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // 你的校验逻辑，一行没删
                    if (propertyName.isBlank() || tenantName.isBlank() || monthlyRent.isBlank() || leaseMonths.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("必填项不能为空！") }
                        return@Button
                    }

                    try {
                        // 【字段对齐点】补齐 model/Rental.kt 要求的全部 10 个字段
                        val newRental = Rental(
                            propertyName = propertyName,
                            tenantName = tenantName,
                            tenantPhone = tenantPhone,
                            tenantIdCard = tenantIdCard,
                            contractDate = contractDate,
                            rentStartDate = rentStartDate,
                            monthlyRent = monthlyRent.toInt(),
                            leaseMonths = leaseMonths.toInt(),
                            paymentFrequency = paymentFrequency.toIntOrNull() ?: 0
                        )

                        viewModel.addRental(newRental)

                        // 你的成功提示逻辑
                        scope.launch { snackbarHostState.showSnackbar("租约信息已成功同步至云端") }

                        // 你的重置字段逻辑，全部保留
                        propertyName = ""; tenantName = ""; tenantPhone = ""; tenantIdCard = ""
                        monthlyRent = ""; leaseMonths = ""; paymentFrequency = ""
                        contractDate = LocalDate.now()
                        rentStartDate = LocalDate.now()

                    } catch (e: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("保存失败：请检查数字输入格式") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认并上传租约")
            }
        }
    }
}