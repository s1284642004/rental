package com.example.renthouseapp

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Calendar

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RentalEntryScreen(
    viewModel: RentalViewModel,
    onLogout: () -> Unit
) {
    val draft = viewModel.entryFormDraft
    val propertyName = draft.propertyName
    val tenantName = draft.tenantName
    val tenantPhone = draft.tenantPhone
    val tenantIdCard = draft.tenantIdCard
    val monthlyRent = draft.monthlyRent
    val leaseMonths = draft.leaseMonths
    val rentStartDateText = draft.rentStartDateText.ifBlank { LocalDate.now().toString() }
    val contractDateText = draft.contractDateText.ifBlank { LocalDate.now().toString() }
    val rentStartDate = LocalDate.parse(rentStartDateText)
    val contractDate = LocalDate.parse(contractDateText)

    val paymentOptions = listOf(
        1 to "\u6bcf\u6708\u4e00\u4ed8",
        3 to "\u5b63\u4ed8(3\u4e2a\u6708)",
        6 to "\u534a\u5e74\u4ed8",
        12 to "\u5e74\u4ed8",
        0 to "\u4e00\u6b21\u6027\u4ed8\u6e05"
    )
    val selectedFreqValue = draft.paymentFrequencyValue
    val selectedFreq = paymentOptions.firstOrNull { it.first == selectedFreqValue } ?: paymentOptions[0]

    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val currentLoginUser = viewModel.currentLoginUser

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

    LaunchedEffect(viewModel.availableProperties) {
        if (propertyName.isBlank() && viewModel.availableProperties.isNotEmpty()) {
            viewModel.updateEntryFormDraft { it.copy(propertyName = viewModel.availableProperties.first()) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        if (viewModel.isInitialLoading && viewModel.availableProperties.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u6b22\u8fce\u4f60 ${currentLoginUser?.loginName.orEmpty()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("\u9000\u51fa\u767b\u5f55")
                    }
                }

                viewModel.initError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                SingleChoiceDialogField(
                    label = "\u9009\u62e9\u623f\u6e90 *",
                    options = viewModel.availableProperties,
                    selectedOption = propertyName,
                    onOptionSelected = { selected ->
                        viewModel.updateEntryFormDraft { draftState -> draftState.copy(propertyName = selected) }
                    }
                )

                OutlinedTextField(
                    value = tenantName,
                    onValueChange = { viewModel.updateEntryFormDraft { draftState -> draftState.copy(tenantName = it) } },
                    label = { Text("\u79df\u5ba2\u59d3\u540d *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tenantPhone,
                    onValueChange = { viewModel.updateEntryFormDraft { draftState -> draftState.copy(tenantPhone = it) } },
                    label = { Text("\u624b\u673a\u53f7(11\u4f4d) *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = tenantIdCard,
                    onValueChange = { viewModel.updateEntryFormDraft { draftState -> draftState.copy(tenantIdCard = it) } },
                    label = { Text("\u8bc1\u4ef6\u53f7\u7801\uff08\u9009\u586b\uff09") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyRent,
                        onValueChange = { viewModel.updateEntryFormDraft { draftState -> draftState.copy(monthlyRent = it) } },
                        label = { Text("\u6708\u79df\u91d1(\u5143) *") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = leaseMonths,
                        onValueChange = { viewModel.updateEntryFormDraft { draftState -> draftState.copy(leaseMonths = it) } },
                        label = { Text("\u5468\u671f(\u6708) *") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                NativeDatePickerField(
                    label = "\u5408\u540c\u7b7e\u8ba2\u65e5\u671f *",
                    selectedDate = contractDate,
                    onDateSelected = { selectedDate ->
                        viewModel.updateEntryFormDraft { draftState -> draftState.copy(contractDateText = selectedDate.toString()) }
                    }
                )
                NativeDatePickerField(
                    label = "\u79df\u91d1\u8d77\u59cb\u65e5 *",
                    selectedDate = rentStartDate,
                    onDateSelected = { selectedDate ->
                        viewModel.updateEntryFormDraft { draftState -> draftState.copy(rentStartDateText = selectedDate.toString()) }
                    }
                )

                SingleChoiceDialogField(
                    label = "\u4ea4\u79df\u65b9\u5f0f *",
                    options = paymentOptions.map { it.second },
                    selectedOption = selectedFreq.second,
                    onOptionSelected = { selectedName ->
                        viewModel.updateEntryFormDraft { draftState ->
                            draftState.copy(paymentFrequencyValue = paymentOptions.first { it.second == selectedName }.first)
                        }
                    }
                )

                Button(
                    onClick = {
                        val rentInt = monthlyRent.toIntOrNull()
                        val leaseInt = leaseMonths.toIntOrNull()

                        if (viewModel.availableProperties.isEmpty()) {
                            errorMessage = viewModel.initError ?: "\u6682\u65e0\u53ef\u7528\u623f\u6e90\uff0c\u8bf7\u5148\u786e\u8ba4 Cloud DB \u521d\u59cb\u5316"
                            return@Button
                        }
                        if (tenantName.isBlank() || rentInt == null || leaseInt == null || propertyName.isBlank()) {
                            errorMessage = "\u8bf7\u5b8c\u6574\u586b\u5199\u5fc5\u586b\u9879\uff0c\u4e14\u79df\u91d1\u5fc5\u987b\u4e3a\u6570\u5b57"
                            return@Button
                        }
                        if (!tenantPhone.matches(Regex("^\\d{11}$"))) {
                            errorMessage = "\u624b\u673a\u53f7\u5fc5\u987b\u4e3a11\u4f4d\u6570\u5b57"
                            return@Button
                        }

                        val hasOngoingContract = viewModel.rentals.any {
                            it.propertyName == propertyName && !it.isCompleted
                        }
                        if (hasOngoingContract) {
                            errorMessage = "\u8be5\u623f\u6e90\u5f53\u524d\u4ecd\u6709\u672a\u7ed3\u6e05\u5408\u540c"
                            return@Button
                        }

                        viewModel.addRental(
                            propertyName = propertyName,
                            tenantName = tenantName,
                            tenantPhone = tenantPhone,
                            tenantIdCard = tenantIdCard,
                            contractDate = contractDate,
                            rentStartDate = rentStartDate,
                            monthlyRent = rentInt,
                            leaseMonths = leaseInt,
                            paymentFrequency = selectedFreq.first,
                            onSuccess = { showSuccessDialog = true },
                            onError = { errorMessage = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("\u786e\u8ba4\u5e76\u5f55\u5165")
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        PullRefreshIndicator(
            refreshing = viewModel.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (errorMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { errorMessage = "" },
            title = { Text("\u5f55\u5165\u5931\u8d25") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { errorMessage = "" }) {
                    Text("\u4fee\u6539")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("\u63d0\u793a") },
            text = { Text("\u5df2\u6210\u529f\u5f55\u5165") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.resetEntryFormDraft()
                    }
                ) {
                    Text("\u786e\u5b9a")
                }
            }
        )
    }
}

@Composable
fun NativeDatePickerField(label: String, selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
    }
    val dialog = DatePickerDialog(
        context,
        { _: DatePicker, y: Int, m: Int, d: Int -> onDateSelected(LocalDate.of(y, m + 1, d)) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Box(modifier = Modifier.fillMaxWidth().clickable { dialog.show() }) {
        OutlinedTextField(
            value = selectedDate.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "") },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun SingleChoiceDialogField(label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().clickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("\u8bf7\u9009\u62e9 $label") },
            text = {
                LazyColumn {
                    items(options) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(option)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == selectedOption, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }
}
