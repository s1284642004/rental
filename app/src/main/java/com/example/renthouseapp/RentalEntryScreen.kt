package com.example.renthouseapp

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Calendar

@Composable
fun RentalEntryScreen(viewModel: RentalViewModel) {
    var propertyName by remember { mutableStateOf("") }
    var tenantName by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }
    var tenantIdCard by remember { mutableStateOf("") }
    var monthlyRent by remember { mutableStateOf("") }
    var leaseMonths by remember { mutableStateOf("12") }
    var rentStartDate by remember { mutableStateOf(LocalDate.now()) }
    var contractDate by remember { mutableStateOf(LocalDate.now()) }

    // 需求5：交租方式，0代表一次性付清
    val paymentOptions = listOf(1 to "每月一付", 3 to "季付(3个月)", 6 to "半年付", 12 to "年付", 0 to "一次性付清")
    var selectedFreq by remember { mutableStateOf(paymentOptions[0]) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.availableProperties) {
        if (propertyName.isBlank() && viewModel.availableProperties.isNotEmpty()) {
            propertyName = viewModel.availableProperties.first()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("录入新房源", style = MaterialTheme.typography.headlineMedium)

        // 需求8：统一采用单选弹窗
        SingleChoiceDialogField(label = "选择房源 *", options = viewModel.availableProperties, selectedOption = propertyName, onOptionSelected = { propertyName = it })

        OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名 *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tenantPhone, onValueChange = { tenantPhone = it }, label = { Text("手机号 (11位) *") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        // 需求6：证件号非必输
        OutlinedTextField(value = tenantIdCard, onValueChange = { tenantIdCard = it }, label = { Text("证件号码 (选填)") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = monthlyRent, onValueChange = { monthlyRent = it }, label = { Text("月租金(元) *") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = leaseMonths, onValueChange = { leaseMonths = it }, label = { Text("周期(月) *") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        NativeDatePickerField(label = "合同签订日期 *", selectedDate = contractDate, onDateSelected = { contractDate = it })
        NativeDatePickerField(label = "租金起始日 *", selectedDate = rentStartDate, onDateSelected = { rentStartDate = it })

        SingleChoiceDialogField(label = "交租方式 *", options = paymentOptions.map { it.second }, selectedOption = selectedFreq.second, onOptionSelected = { selectedName -> selectedFreq = paymentOptions.first { it.second == selectedName } })

        Button(
            onClick = {
                val rentInt = monthlyRent.toIntOrNull()
                val leaseInt = leaseMonths.toIntOrNull()

                if (tenantName.isBlank() || rentInt == null || leaseInt == null || propertyName.isBlank()) { errorMessage = "请完整填写必输项，且租金必须为数字"; return@Button }
                if (!tenantPhone.matches(Regex("^\\d{11}$"))) { errorMessage = "手机号必须为11位数字"; return@Button }

                val hasOngoingContract = viewModel.rentals.any { it.propertyName == propertyName && !it.isCompleted }
                if (hasOngoingContract) { errorMessage = "录入拦截：房源【$propertyName】当前仍有未结清的合同！"; return@Button }

                viewModel.addRental(
                    propertyName = propertyName, tenantName = tenantName, tenantPhone = tenantPhone,
                    tenantIdCard = tenantIdCard, contractDate = contractDate, rentStartDate = rentStartDate,
                    monthlyRent = rentInt, leaseMonths = leaseInt, paymentFrequency = selectedFreq.first,
                    onSuccess = { showSuccessDialog = true },
                    onError = { errorMessage = it }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("确认并录入") }
        Spacer(modifier = Modifier.height(20.dp))
    }

    if (errorMessage.isNotEmpty()) AlertDialog(onDismissRequest = { errorMessage = "" }, title = { Text("录入失败") }, text = { Text(errorMessage) }, confirmButton = { TextButton(onClick = { errorMessage = "" }) { Text("修改") } })
    if (showSuccessDialog) AlertDialog(onDismissRequest = { }, title = { Text("提示") }, text = { Text("已成功录入！") }, confirmButton = { TextButton(onClick = { showSuccessDialog = false; tenantName = ""; tenantPhone = ""; tenantIdCard = ""; monthlyRent = "" }) { Text("确定") } })
}

// 提取的可复用组件：原生日期选择
@Composable
fun NativeDatePickerField(label: String, selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth) }
    val dialog = DatePickerDialog(context, { _: DatePicker, y: Int, m: Int, d: Int -> onDateSelected(LocalDate.of(y, m + 1, d)) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    Box(modifier = Modifier.fillMaxWidth().clickable { dialog.show() }) {
        OutlinedTextField(value = selectedDate.toString(), onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { Icon(Icons.Default.DateRange, "") }, enabled = false, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant))
    }
}

// 提取的可复用组件：单选弹窗菜单
@Composable
fun SingleChoiceDialogField(label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().clickable { showDialog = true }) {
        OutlinedTextField(value = selectedOption, onValueChange = {}, readOnly = true, label = { Text(label) }, enabled = false, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("请选择 $label") },
            text = {
                LazyColumn {
                    items(options) { option ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onOptionSelected(option); showDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option == selectedOption, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }
}