package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.StudioAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onToggleLayers: () -> Unit,
    layersCount: Int,
    showGrid: Boolean,
    onToggleGrid: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Animation,
                    contentDescription = null,
                    tint = StudioAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            // Undo
            IconButton(
                onClick = onUndo,
                modifier = Modifier.testTag("top_undo_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.action_undo),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Redo
            IconButton(
                onClick = onRedo,
                modifier = Modifier.testTag("top_redo_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.action_redo),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Toggle Grid
            IconButton(onClick = onToggleGrid) {
                Icon(
                    if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "شبکه شطرنجی",
                    tint = if (showGrid) StudioAccent else MaterialTheme.colorScheme.outline
                )
            }

            // Save Project
            IconButton(onClick = onSave, modifier = Modifier.testTag("top_save_button")) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.menu_save_project),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Export
            IconButton(onClick = onExport, modifier = Modifier.testTag("top_export_button")) {
                Icon(
                    Icons.Default.IosShare,
                    contentDescription = stringResource(R.string.menu_export),
                    tint = StudioAccent
                )
            }

            // Toggle Layers Panel Button
            IconButton(onClick = onToggleLayers, modifier = Modifier.testTag("top_layers_button")) {
                BadgedBox(
                    badge = {
                        Badge { Text("$layersCount") }
                    }
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = stringResource(R.string.layers_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.testTag("studio_top_bar")
    )
}
