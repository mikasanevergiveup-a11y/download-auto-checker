package com.downloadchecker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF070B14),
    surface = Color(0xFF121A2B),
    onPrimary = Color(0xFF042F2E),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    error = Color(0xFFFB7185)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
