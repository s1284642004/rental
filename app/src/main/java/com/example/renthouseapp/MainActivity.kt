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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var loginContext by remember { mutableStateOf(LoginSessionStore.load(context)) }
    var isUnlocked by rememberSaveable { mutableStateOf(loginContext != null) }

    if (!isUnlocked) {
        PasscodeScreen(
            onUnlockSuccess = { name, phone ->
                LoginSessionStore.save(context, name, phone)
                loginContext = LoginContext(name, phone)
                isUnlocked = true
            }
        )
        return
    }

    val rentalViewModel: RentalViewModel = viewModel()
    val loginName = loginContext?.loginName.orEmpty()
    val loginPhone = loginContext?.phoneNumber.orEmpty()
    val maskedPhone = loginPhone.let { phone ->
        if (phone.length == 11) "${phone.take(3)}****${phone.takeLast(4)}" else phone
    }

    LaunchedEffect(Unit, loginName, loginPhone) {
        rentalViewModel.initialize(context)
        rentalViewModel.setLoginUser(loginName, loginPhone)
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
            if (loginName.isNotBlank()) {
                Text(
                    text = if (maskedPhone.isNotBlank()) "当前登录：$loginName（$maskedPhone）" else "当前登录：$loginName",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    ENTRY("录入", Icons.Default.Add),
    LIST("房源", Icons.Default.Home)
}
