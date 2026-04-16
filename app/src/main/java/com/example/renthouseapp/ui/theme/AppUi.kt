package com.example.renthouseapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppDimensions(
    val screenPadding: Dp,
    val cardPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val buttonHeight: Dp,
    val compactButtonHeight: Dp,
    val fieldMinHeight: Dp
)

val DefaultDimensions = AppDimensions(
    screenPadding = 16.dp,
    cardPadding = 16.dp,
    sectionSpacing = 16.dp,
    itemSpacing = 12.dp,
    buttonHeight = 48.dp,
    compactButtonHeight = 52.dp,
    fieldMinHeight = 56.dp
)

val SeniorDimensions = AppDimensions(
    screenPadding = 20.dp,
    cardPadding = 20.dp,
    sectionSpacing = 20.dp,
    itemSpacing = 16.dp,
    buttonHeight = 56.dp,
    compactButtonHeight = 52.dp,
    fieldMinHeight = 64.dp
)

val LocalAppDimensions = staticCompositionLocalOf { DefaultDimensions }
val LocalIsSeniorMode = staticCompositionLocalOf { false }
