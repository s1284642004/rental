package com.example.renthouseapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PasscodeScreen(onLoginSuccess: (loginName: String, phoneNumber: String) -> Unit) {
    var loginName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "登录",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "收租管家",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "请输入登录姓名和手机号",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            OutlinedTextField(
                value = loginName,
                onValueChange = {
                    loginName = it
                    isError = false
                },
                label = { Text("登录人姓名") },
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    if (it.all(Char::isDigit) && it.length <= 11) {
                        phoneNumber = it
                        isError = false
                    }
                },
                label = { Text("手机号") },
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (loginName.isBlank() || !phoneNumber.matches(Regex("^\\d{11}$"))) {
                        isError = true
                        errorText = "请输入姓名且手机号为11位数字"
                    } else {
                        onLoginSuccess(loginName.trim(), phoneNumber)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("登录")
            }

            if (isError) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
