package com.example.renthouseapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PasscodeScreen(onUnlockSuccess: () -> Unit) {
    // 状态管理
    var passcode by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // 正确的专属密码
    val correctPasscode = "740506"

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
                imageVector = Icons.Default.Lock,
                contentDescription = "安全锁",
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
                text = "请输入 6 位专属访问密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            OutlinedTextField(
                value = passcode,
                onValueChange = { input ->
                    // 限制只能输入6位数字
                    if (input.length <= 6 && input.all { it.isDigit() }) {
                        passcode = input
                        isError = false // 重新输入时清除错误状态

                        // 当输入满 6 位时，自动校验
                        if (input.length == 6) {
                            if (input == correctPasscode) {
                                onUnlockSuccess() // 密码正确，触发解锁回调
                            } else {
                                isError = true // 密码错误，变红提示
                            }
                        }
                    }
                },
                isError = isError,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(), // 变成小黑点
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            if (isError) {
                Text(
                    text = "密码错误，请重新输入",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}