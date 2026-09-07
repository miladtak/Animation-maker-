package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

enum class ExportFormat {
    MP4_VIDEO,
    ANIMATED_GIF,
    PNG_SEQUENCE,
    CURRENT_FRAME_PNG
}

@Composable
fun ExportDialog(
    isExporting: Boolean,
    exportProgress: Float,
    onStartExport: (ExportFormat) -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    exportCompleted: Boolean
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.MP4_VIDEO) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onCancel() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("export_dialog_content"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isExporting) {
                    Text(stringResource(R.string.export_progress), fontSize = 13.sp)
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Text("${(exportProgress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                } else if (exportCompleted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_success), fontSize = 13.sp)
                        }
                    }
                } else {
                    Text("فرمت خروجی مورد نظر را انتخاب نمایید:", fontSize = 13.sp)

                    // MP4 option
                    FormatOptionCard(
                        title = stringResource(R.string.export_mp4),
                        subtitle = "انکودر ویدیو H.264 با صدای همگام (۳۰ یا ۲۴ فریم)",
                        icon = Icons.Default.VideoFile,
                        isSelected = selectedFormat == ExportFormat.MP4_VIDEO,
                        onClick = { selectedFormat = ExportFormat.MP4_VIDEO }
                    )

                    // GIF option
                    FormatOptionCard(
                        title = stringResource(R.string.export_gif),
                        subtitle = "تصویر متحرک بهینه برای پیام‌رسان‌ها و وب",
                        icon = Icons.Default.Gif,
                        isSelected = selectedFormat == ExportFormat.ANIMATED_GIF,
                        onClick = { selectedFormat = ExportFormat.ANIMATED_GIF }
                    )

                    // Current frame PNG
                    FormatOptionCard(
                        title = stringResource(R.string.export_current_frame),
                        subtitle = "عکس با کیفیت بالا و شفافیت آلفا از فریم فعلی",
                        icon = Icons.Default.Image,
                        isSelected = selectedFormat == ExportFormat.CURRENT_FRAME_PNG,
                        onClick = { selectedFormat = ExportFormat.CURRENT_FRAME_PNG }
                    )
                }
            }
        },
        confirmButton = {
            if (exportCompleted) {
                Button(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share))
                }
            } else if (!isExporting) {
                Button(
                    onClick = { onStartExport(selectedFormat) },
                    modifier = Modifier.testTag("start_export_button")
                ) {
                    Text("شروع استخراج")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(if (exportCompleted) stringResource(R.string.action_close) else stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FormatOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Spacer(Modifier.width(6.dp))
            Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
