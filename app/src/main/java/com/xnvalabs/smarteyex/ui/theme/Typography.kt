package com.xnvalabs.smarteyex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SmartEyeXTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp),
    )
}
