package com.jp.applocker

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF1E6F68), onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F0E6), onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFD2E8E4), onSecondaryContainer = Color(0xFF0B1F1D),
    background = Color(0xFFF6FAF9), surface = Color(0xFFF6FAF9),
    surfaceContainer = Color(0xFFE9F0EE), surfaceContainerHigh = Color(0xFFE3EAE8),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8CD4CB), onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFA8F0E6),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFD2E8E4),
    background = Color(0xFF0E1514), surface = Color(0xFF0E1514),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
