package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = StudioAccent,
  onPrimary = Color.White,
  primaryContainer = StudioSurfaceVariant,
  onPrimaryContainer = StudioTextPrimary,
  secondary = StudioAccentLight,
  onSecondary = Color.Black,
  tertiary = StudioAccentAmber,
  background = StudioDarkBg,
  onBackground = StudioTextPrimary,
  surface = StudioSurface,
  onSurface = StudioTextPrimary,
  surfaceVariant = StudioSurfaceVariant,
  onSurfaceVariant = StudioTextSecondary,
  outline = StudioBorder
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}

