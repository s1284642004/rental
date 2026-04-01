package com.example.renthouseapp


import android.os.Bundle
import android.util.Log // 修复：补上 Log 导入
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.renthouseapp.model.Rental
import com.example.renthouseapp.ui.theme.RentHouseAppTheme

// 引用你写好的模型

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 修正：调用正确的初始化方法名
        CloudDBManager.init(this) {
            Log.i("MainActivity", "云数据库初始化成功")
        }

        enableEdgeToEdge()
        setContent {
            RentHouseAppTheme {
                var isUnlocked by remember { mutableStateOf(false) }
                if (!isUnlocked) {
                    PasscodeScreen(onUnlockSuccess = { isUnlocked = true }) // 保留你的解锁逻辑
                } else {
                    RentHouseAppApp()
                }
            }
        }
    }
}

@Composable
fun RentHouseAppApp() {
    val rentalViewModel: RentalViewModel = viewModel()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.LIST) }
    var selectedRentalId by remember { mutableStateOf<String?>(null) }

    // 修复：明确类型转换，解决 find 报错
    val selectedRental = selectedRentalId?.let { id ->
        rentalViewModel.rentals.find { it.id == id }
    }

    if (selectedRental != null) {
        RentalDetailScreen(
            rental = selectedRental,
            viewModel = rentalViewModel,
            onBackClick = { selectedRentalId = null }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach {
                    item(
                        icon = { Icon(it.icon, contentDescription = it.label) },
                        label = { Text(it.label) },
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.ENTRY -> RentalEntryScreen(viewModel = rentalViewModel)
                        AppDestinations.LIST -> RentalListScreen(
                            viewModel = rentalViewModel,
                            onRentalClick = { clickedRental ->
                                selectedRentalId = clickedRental.id
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    ENTRY("录入", Icons.Default.Add),
    LIST("房源", Icons.Default.Home)
}