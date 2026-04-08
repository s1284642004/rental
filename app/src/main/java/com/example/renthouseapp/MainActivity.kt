package com.example.renthouseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.renthouseapp.ui.theme.RentHouseAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RentHouseAppTheme {
                RentHouseAppApp()
            }
        }
    }
}

@Composable
fun RentHouseAppApp() {
    val rentalViewModel: RentalViewModel = viewModel()
    val context = LocalContext.current
    val loginSessionStore = remember(context) { LoginSessionStore(context.applicationContext) }
    var currentLoginUser by remember { mutableStateOf(loginSessionStore.getUser()) }

    // 如果没有登录，强制全屏显示登录界面
    if (currentLoginUser == null) {
        PasscodeScreen(
            onLoginSuccess = { user ->
                loginSessionStore.saveUser(user)
                currentLoginUser = user
            }
        )
        return
    }

    LaunchedEffect(Unit) {
        rentalViewModel.initialize(context)
    }
    LaunchedEffect(currentLoginUser) {
        currentLoginUser?.let { rentalViewModel.setCurrentLoginUser(it) }
    }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.LIST) }
    var selectedRentalId by remember { mutableStateOf<String?>(null) }
    val selectedRental = rentalViewModel.rentals.find { it.id == selectedRentalId }

    BackHandler(enabled = selectedRentalId != null || currentDestination != AppDestinations.LIST) {
        if (selectedRentalId != null) {
            selectedRentalId = null
        } else {
            currentDestination = AppDestinations.LIST
        }
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
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text(
                        text = "当前登录人：${currentLoginUser?.loginName} (${currentLoginUser?.phoneNumber})",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (currentDestination) {
                            AppDestinations.ENTRY -> RentalEntryScreen(viewModel = rentalViewModel)
                            AppDestinations.LIST -> RentalListScreen(
                                viewModel = rentalViewModel,
                                onRentalClick = { clickedRental -> selectedRentalId = clickedRental.id }
                            )
                        }
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
