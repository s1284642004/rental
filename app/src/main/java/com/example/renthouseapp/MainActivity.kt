package com.example.renthouseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    val context = LocalContext.current
    val sessionStore = remember { LoginSessionStore(context) }
    var currentLogin by rememberSaveable {
        mutableStateOf(sessionStore.get() ?: LoginSession(loginName = "", phoneNumber = ""))
    }
    var isUnlocked by rememberSaveable { mutableStateOf(currentLogin.phoneNumber.isNotBlank()) }

    if (!isUnlocked) {
        PasscodeScreen(
            onLoginSuccess = { loginName, phoneNumber ->
                val session = LoginSession(loginName, phoneNumber)
                sessionStore.save(session)
                currentLogin = session
                isUnlocked = true
            }
        )
        return
    }
    val rentalViewModel: RentalViewModel = viewModel()

    LaunchedEffect(currentLogin.phoneNumber) {
        rentalViewModel.initialize(context, currentLogin)
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
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text("当前登录：${currentLogin.loginName}（${currentLogin.phoneNumber.maskPhone()}）")
                        }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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

private fun String.maskPhone(): String {
    if (length < 7) return this
    return replaceRange(3, 7, "****")
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    ENTRY("录入", Icons.Default.Add),
    LIST("房源", Icons.Default.Home)
}
