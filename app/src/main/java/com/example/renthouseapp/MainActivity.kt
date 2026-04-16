package com.example.renthouseapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentHouseAppApp() {
    val rentalViewModel: RentalViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        rentalViewModel.initialize(context)
    }

    val currentLoginUser = rentalViewModel.currentLoginUser
    if (currentLoginUser == null) {
        PasscodeScreen(
            isLoading = rentalViewModel.isLoggingIn,
            isMigrating = rentalViewModel.isMigratingData,
            verifiedLoginName = rentalViewModel.verifiedLoginName,
            validationMessage = rentalViewModel.loginValidationMessage,
            onVerifyCredentials = { loginCode, password ->
                rentalViewModel.verifyLoginCredentials(loginCode, password)
            },
            onLogin = { loginCode, password ->
                rentalViewModel.loginWithVerifiedCredentials(
                    loginCode = loginCode,
                    password = password,
                    onSuccess = {},
                    onError = {}
                )
            },
            onMigrateDatabase = {
                rentalViewModel.cleanupDanglingPaymentRecords(
                    onSuccess = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
        return
    }

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.LIST) }
    var selectedRentalId by remember { mutableStateOf<String?>(null) }
    var autoOpenRenewRentalId by remember { mutableStateOf<String?>(null) }
    val selectedRental = rentalViewModel.rentals.find { it.id == selectedRentalId }

    LaunchedEffect(currentDestination) {
        rentalViewModel.refreshFromTabSwitch()
    }

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
            onBackClick = {
                selectedRentalId = null
                autoOpenRenewRentalId = null
            },
            autoOpenRenewDialog = autoOpenRenewRentalId == selectedRental.id,
            onAutoOpenRenewHandled = { autoOpenRenewRentalId = null }
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
                        title = { Text(currentDestination.label) }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.ENTRY -> RentalEntryScreen(
                            viewModel = rentalViewModel,
                            onLogout = {
                                rentalViewModel.logout()
                                currentDestination = AppDestinations.LIST
                            }
                        )
                        AppDestinations.LIST -> RentalListScreen(
                            viewModel = rentalViewModel,
                            onRentalClick = { clickedRental -> selectedRentalId = clickedRental.id }
                        )
                        AppDestinations.PAYMENT -> PaymentCollectionScreen(
                            viewModel = rentalViewModel
                        )
                        AppDestinations.RENEWAL -> RenewalScheduleScreen(
                            viewModel = rentalViewModel,
                            onRentalClick = { clickedRental -> selectedRentalId = clickedRental.id },
                            onRenewClick = { clickedRental ->
                                selectedRentalId = clickedRental.id
                                autoOpenRenewRentalId = clickedRental.id
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    ENTRY("\u5f55\u5165", Icons.Default.Add),
    LIST("\u623f\u6e90", Icons.Default.Home),
    PAYMENT("\u50ac\u6b3e\u65e5\u7a0b", Icons.Default.DateRange),
    RENEWAL("\u62db\u79df/\u7eed\u79df", Icons.Default.Campaign)
}
