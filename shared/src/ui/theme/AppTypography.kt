package com.mrboomdev.scrollix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.google_sans_flex_bold
import com.mrboomdev.scrollix.resources.google_sans_flex_medium
import com.mrboomdev.scrollix.resources.google_sans_flex_regular

@Composable
fun appTypography(): Typography {
    val googleSansFlex = FontFamily(
        Font(
            resource = Res.font.google_sans_flex_regular,
            weight = FontWeight.Normal,
            style = FontStyle.Normal
        ),
        Font(
            resource = Res.font.google_sans_flex_medium,
            weight = FontWeight.Medium,
            style = FontStyle.Normal
        ),
        Font(
            resource = Res.font.google_sans_flex_bold,
            weight = FontWeight.Bold,
            style = FontStyle.Normal
        )
    )

    val base = Typography()

    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = googleSansFlex),
        displayMedium = base.displayMedium.copy(fontFamily = googleSansFlex),
        displaySmall = base.displaySmall.copy(fontFamily = googleSansFlex),
        headlineLarge = base.headlineLarge.copy(fontFamily = googleSansFlex),
        headlineMedium = base.headlineMedium.copy(fontFamily = googleSansFlex),
        headlineSmall = base.headlineSmall.copy(fontFamily = googleSansFlex),
        titleLarge = base.titleLarge.copy(fontFamily = googleSansFlex),
        titleMedium = base.titleMedium.copy(fontFamily = googleSansFlex),
        titleSmall = base.titleSmall.copy(fontFamily = googleSansFlex),
        bodyLarge = base.bodyLarge.copy(fontFamily = googleSansFlex),
        bodyMedium = base.bodyMedium.copy(fontFamily = googleSansFlex),
        bodySmall = base.bodySmall.copy(fontFamily = googleSansFlex),
        labelLarge = base.labelLarge.copy(fontFamily = googleSansFlex),
        labelMedium = base.labelMedium.copy(fontFamily = googleSansFlex),
        labelSmall = base.labelSmall.copy(fontFamily = googleSansFlex)
    )
}
