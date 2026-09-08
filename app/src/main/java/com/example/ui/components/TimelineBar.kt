package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Timeline
import com.example.model.TimelineMode
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioKeyframeDiamond

@Composable
fun TimelineBar(
    timeline: Timeline,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleOnionSkin: () -> Unit,
    onFpsChange: (Int) -> Unit,
    onOpenNewTimelineDialog: () -> Unit,
    onAddAudioTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDrawerOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Blender-style Drawer Pull Header (همیشه قابل لمس برای باز/بستن کشو و دارای دکمه پخش فوری)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDrawerOpen = !isDrawerOpen }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isDrawerOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isDrawerOpen) "بستن کشوی تایم‌لاین" else "باز کردن کشوی تایم‌لاین",
                        tint = StudioAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "کشوی تایم‌لاین (فریم ${timeline.currentFrame + 1} از ${timeline.totalFrames} - ${timeline.fps} FPS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // دکمه پخش و مکث سریع در سربرگ کشو
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "پخش/مکث",
                            tint = if (isPlaying) StudioAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isDrawerOpen) "بستن کشو ▼" else "باز کردن تایم‌لاین ▲",
                        fontSize = 10.sp,
                        color = StudioAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(
                visible = isDrawerOpen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    // Upper control row: Play/Pause, Stop, Loop, Onion skin, FPS, New Timeline, Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                // Playback Buttons
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_play_pause_button")
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.timeline_pause else R.string.timeline_play),
                            tint = if (isPlaying) StudioAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "توقف", modifier = Modifier.size(18.dp))
                    }

                    // Loop Toggle
                    IconButton(onClick = onToggleLoop, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.timeline_loop),
                            tint = if (timeline.isLooping) StudioAccent else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Onion Skin Toggle
                    FilterChip(
                        selected = timeline.isOnionSkinEnabled,
                        onClick = onToggleOnionSkin,
                        label = { Text("پوست پیازی", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (timeline.isOnionSkinEnabled) StudioAccent else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("timeline_onion_skin_chip")
                    )
                }

                // FPS & Frame count info (kept in LTR for clean technical numbers)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${timeline.currentFrame + 1} / ${timeline.totalFrames}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        var showFpsMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { showFpsMenu = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("${timeline.fps} FPS", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            DropdownMenu(expanded = showFpsMenu, onDismissRequest = { showFpsMenu = false }) {
                                listOf(12, 24, 30, 60).forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text("$f FPS") },
                                        onClick = {
                                            onFpsChange(f)
                                            showFpsMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Add frame / duplicate / delete
                        IconButton(onClick = onAddFrame, modifier = Modifier.size(28.dp).testTag("timeline_add_frame_button")) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.timeline_add_frame), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDuplicateFrame, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "تکثیر فریم", modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onDeleteFrame, enabled = timeline.totalFrames > 1, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.timeline_delete_frame),
                                modifier = Modifier.size(16.dp),
                                tint = if (timeline.totalFrames > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                        }

                        // Audio Track Button
                        IconButton(onClick = onAddAudioTrack, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (timeline.audioTrack != null) Icons.Default.MusicNote else Icons.Default.Audiotrack,
                                contentDescription = stringResource(R.string.timeline_add_audio),
                                tint = if (timeline.audioTrack != null) StudioKeyframeDiamond else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // New Timeline Button
                        AssistChip(
                            onClick = onOpenNewTimelineDialog,
                            label = { Text("تایم‌لاین جدید", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.testTag("new_timeline_button")
                        )
                    }
                }
            }

            // Audio track wave preview if audio is present
            if (timeline.audioTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = StudioAccent)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${timeline.audioTrack.name} (همگام با تایم‌لاین)",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Frame scrubber strip (LTR layout: frames go left-to-right 1, 2, 3...)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (fIdx in 0 until timeline.totalFrames) {
                        val isCurrent = fIdx == timeline.currentFrame
                        val isKeyframe = fIdx % 4 == 0 // simulated keyframe markers

                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isCurrent) StudioAccent.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 0.5.dp,
                                    color = if (isCurrent) StudioAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectFrame(fIdx) }
                                .testTag("frame_cell_$fIdx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${fIdx + 1}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) StudioAccent else MaterialTheme.colorScheme.onSurface
                                )
                                if (isKeyframe) {
                                    Icon(
                                        Icons.Default.Diamond,
                                        contentDescription = null,
                                        tint = StudioKeyframeDiamond,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

@Composable
fun NewTimelineDialog(
    onSelectMode: (TimelineMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(TimelineMode.DUPLICATE) }
    var showBlankWarning by remember { mutableStateOf(false) }

    if (showBlankWarning) {
        AlertDialog(
            onDismissRequest = { showBlankWarning = false },
            title = { Text("تأیید ایجاد تایم‌لاین خالی") },
            text = { Text(stringResource(R.string.timeline_blank_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBlankWarning = false
                        onSelectMode(TimelineMode.BLANK)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlankWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_new_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حالت ایجاد تایم‌لاین را انتخاب کنید:")

                // Duplicate Current Option (Recommended)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = TimelineMode.DUPLICATE },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == TimelineMode.DUPLICATE)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (selectedMode == TimelineMode.DUPLICATE)
                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == TimelineMode.DUPLICATE,
                            onClick = { selectedMode = TimelineMode.DUPLICATE }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.timeline_mode_duplicate), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("کپی کامل تمام لایه‌ها، فریم‌ها و پین‌های فعلی بدون حذف محتوا", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Linked Option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = TimelineMode.LINKED },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == TimelineMode.LINKED)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (selectedMode == TimelineMode.LINKED)
                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == TimelineMode.LINKED,
                            onClick = { selectedMode = TimelineMode.LINKED }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.timeline_mode_linked), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("ارجاع وابسته به صحنه فعلی به عنوان صحنه تو در تو", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Blank Option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = TimelineMode.BLANK },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == TimelineMode.BLANK)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (selectedMode == TimelineMode.BLANK)
                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                    else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == TimelineMode.BLANK,
                            onClick = { selectedMode = TimelineMode.BLANK }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.timeline_mode_blank), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            Text("شروع مجدد با یک فریم کاملاً سفید/خالی (نیاز به تأیید)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == TimelineMode.BLANK) {
                        showBlankWarning = true
                    } else {
                        onSelectMode(selectedMode)
                    }
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
