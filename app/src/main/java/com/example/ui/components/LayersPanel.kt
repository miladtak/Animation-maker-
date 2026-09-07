package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*

@Composable
fun LayersPanel(
    layers: List<Layer>,
    activeLayerId: String,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onBlendModeChange: (String, BlendModeType) -> Unit,
    onAddLayer: (LayerType) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onMoveLayerUp: (Int) -> Unit,
    onMoveLayerDown: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .testTag("layers_panel"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.layers_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(4.dp))
                    Badge { Text("${layers.size}") }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add Layer Menu Button
            var showAddMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showAddMenu = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_layer_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.layer_add), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_add_raster)) },
                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null) },
                        onClick = {
                            onAddLayer(LayerType.RASTER)
                            showAddMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_add_shape)) },
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                        onClick = {
                            onAddLayer(LayerType.SHAPE)
                            showAddMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_add_text)) },
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                        onClick = {
                            onAddLayer(LayerType.TEXT)
                            showAddMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_add_image)) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            onAddLayer(LayerType.IMAGE)
                            showAddMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_add_video_ref)) },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                        onClick = {
                            onAddLayer(LayerType.VIDEO_REF)
                            showAddMenu = false
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Selected Layer Properties (Opacity & Blend Mode)
            val selectedLayer = layers.find { it.id == activeLayerId }
            if (selectedLayer != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "شفافیت: ${(selectedLayer.opacity * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            var showBlendMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { showBlendMenu = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(selectedLayer.blendMode.name, fontSize = 11.sp)
                                }
                                DropdownMenu(
                                    expanded = showBlendMenu,
                                    onDismissRequest = { showBlendMenu = false }
                                ) {
                                    BlendModeType.entries.forEach { bm ->
                                        DropdownMenuItem(
                                            text = { Text(bm.name) },
                                            onClick = {
                                                onBlendModeChange(selectedLayer.id, bm)
                                                showBlendMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Slider(
                            value = selectedLayer.opacity,
                            onValueChange = { onOpacityChange(selectedLayer.id, it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Layers List (reversed so top layer in stack is at top visually)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(layers.reversed()) { revIdx, layer ->
                    val actualIndex = layers.size - 1 - revIdx
                    val isSelected = layer.id == activeLayerId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectLayer(layer.id) }
                            .testTag("layer_item_${layer.id}"),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reorder controls
                            Column {
                                IconButton(
                                    onClick = { onMoveLayerUp(actualIndex) },
                                    enabled = actualIndex < layers.size - 1,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { onMoveLayerDown(actualIndex) },
                                    enabled = actualIndex > 0,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }

                            // Visibility Icon
                            IconButton(
                                onClick = { onToggleVisibility(layer.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(R.string.layer_visible),
                                    tint = if (layer.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Layer Type Icon
                            val icon = when (layer.type) {
                                LayerType.RASTER -> Icons.Default.Brush
                                LayerType.SHAPE -> Icons.Default.Category
                                LayerType.TEXT -> Icons.Default.TextFields
                                LayerType.IMAGE -> Icons.Default.Image
                                LayerType.VIDEO_REF -> Icons.Default.Videocam
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)

                            Spacer(Modifier.width(6.dp))

                            // Name
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Duplicate
                            IconButton(
                                onClick = { onDuplicateLayer(layer.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_duplicate), modifier = Modifier.size(14.dp))
                            }

                            // Delete
                            IconButton(
                                onClick = { onDeleteLayer(layer.id) },
                                enabled = layers.size > 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = if (layers.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
