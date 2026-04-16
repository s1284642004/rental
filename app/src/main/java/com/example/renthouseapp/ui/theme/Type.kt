package com.example.renthouseapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

val Typography = Typography()

val SeniorTypography = Typography(
    displaySmall = Typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 46.sp),
    headlineMedium = Typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 40.sp),
    headlineSmall = Typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 36.sp),
    titleLarge = Typography.titleLarge.copy(fontSize = 23.sp, lineHeight = 32.sp),
    titleMedium = Typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 30.sp),
    bodyLarge = Typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 29.sp),
    bodyMedium = Typography.bodyMedium.copy(fontSize = 17.sp, lineHeight = 27.sp),
    bodySmall = Typography.bodySmall.copy(fontSize = 15.sp, lineHeight = 23.sp),
    labelLarge = Typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
)
