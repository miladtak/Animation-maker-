package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R

val StudioColorPalette = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF3898EC, 0xFF64B5F6,
    0xFFFF3366, 0xFFFFB74D, 0xFF33CC66, 0xFF9C27B0,
    0xFFFFEB3B, 0xFFFF5722, 0xFF00BCD4, 0xFF795548,
    0xFF607D8B, 0xFFE91E63, 0xFF4CAF50, 0xFF2196F3
)

@Composable
fun SafeColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    val currentColor = try {
        Color(red = red.coerceIn(0f, 1f), green = green.coerceIn(0f, 1f), blue = blue.coerceIn(0f, 1f), alpha = alpha.coerceIn(0f, 1f))
    } catch (e: Exception) {
        Color.White
    }

    var hexText by remember {
        mutableStateOf(
            String.format(
                "%02X%02X%02X%02X",
                (alpha * 255).toInt().coerceIn(0, 255),
                (red * 255).toInt().coerceIn(0, 255),
                (green * 255).toInt().coerceIn(0, 255),
                (blue * 255).toInt().coerceIn(0, 255)
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("safe_color_picker_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.color_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Current vs New color preview box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(initialColor)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(currentColor)
                    )
                }

                // Preset palette grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(StudioColorPalette) { colorLong ->
                        val swatch = Color(colorLong)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable {
                                    red = swatch.red
                                    green = swatch.green
                                    blue = swatch.blue
                                    alpha = swatch.alpha
                                    hexText = String.format(
                                        "%02X%02X%02X%02X",
                                        (alpha * 255).toInt(),
                                        (red * 255).toInt(),
                                        (green * 255).toInt(),
                                        (blue * 255).toInt()
                                    )
                                }
                        )
                    }
                }

                // Red slider
                ColorSliderRow(label = stringResource(R.string.color_red), value = red, onValueChange = {
                    red = it
                    hexText = updateHex(alpha, red, green, blue)
                }, barColor = Color(0xFFFF5252))

                // Green slider
                ColorSliderRow(label = stringResource(R.string.color_green), value = green, onValueChange = {
                    green = it
                    hexText = updateHex(alpha, red, green, blue)
                }, barColor = Color(0xFF4CAF50))

                // Blue slider
                ColorSliderRow(label = stringResource(R.string.color_blue), value = blue, onValueChange = {
                    blue = it
                    hexText = updateHex(alpha, red, green, blue)
                }, barColor = Color(0xFF2196F3))

                // Alpha slider
                ColorSliderRow(label = stringResource(R.string.color_alpha), value = alpha, onValueChange = {
                    alpha = it
                    hexText = updateHex(alpha, red, green, blue)
                }, barColor = Color.LightGray)

                // Hex input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.color_hex),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { newHex ->
                            val sanitized = newHex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(8)
                            hexText = sanitized
                            if (sanitized.length == 8) {
                                try {
                                    val parsed = sanitized.toLong(16)
                                    alpha = ((parsed shr 24) and 0xFF) / 255f
                                    red = ((parsed shr 16) and 0xFF) / 255f
                                    green = ((parsed shr 8) and 0xFF) / 255f
                                    blue = (parsed and 0xFF) / 255f
                                } catch (_: Exception) {}
                            } else if (sanitized.length == 6) {
                                try {
                                    val parsed = sanitized.toLong(16)
                                    alpha = 1f
                                    red = ((parsed shr 16) and 0xFF) / 255f
                                    green = ((parsed shr 8) and 0xFF) / 255f
                                    blue = (parsed and 0xFF) / 255f
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("color_hex_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        singleLine = true
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("color_cancel_button")
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = { onColorSelected(currentColor) },
                        modifier = Modifier.testTag("color_confirm_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }
}

private fun updateHex(a: Float, r: Float, g: Float, b: Float): String {
    return String.format(
        "%02X%02X%02X%02X",
        (a * 255).toInt().coerceIn(0, 255),
        (r * 255).toInt().coerceIn(0, 255),
        (g * 255).toInt().coerceIn(0, 255),
        (b * 255).toInt().coerceIn(0, 255)
    )
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    barColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = barColor,
                activeTrackColor = barColor
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 255).toInt()}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(32.dp)
        )
    }
}
