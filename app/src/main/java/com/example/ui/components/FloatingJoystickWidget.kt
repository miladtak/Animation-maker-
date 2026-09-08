package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioAccent
import kotlin.math.*

/**
 * 3D Earth Trackball Globe Widget (گوی سه‌بعدی شبه‌کره زمین)
 * Allows intuitive 3D rotation (Pitch / Yaw) and 2D Roll/Rotation for Puppet Warp and Layers.
 * Uses absoluteOffset to guarantee natural left/right dragging with zero RTL inversion.
 */
@Composable
fun FloatingJoystickWidget(
    currentAngle: Float,
    currentPitch: Float = 0f,
    currentYaw: Float = 0f,
    onAngleChange: (Float) -> Unit,
    on3DChange: ((rotation: Float, pitch: Float, yaw: Float) -> Unit)? = null,
    onClose: () -> Unit,
    title: String = "گوی سه‌بعدی پاپت (کره زمین)",
    modifier: Modifier = Modifier
) {
    var widgetOffset by remember { mutableStateOf(Offset(30f, 150f)) }
    var sizeScale by remember { mutableStateOf(1.0f) }

    var pitch by remember(currentPitch) { mutableStateOf(currentPitch) }
    var yaw by remember(currentYaw) { mutableStateOf(currentYaw) }
    var roll by remember(currentAngle) { mutableStateOf(currentAngle) }

    val density = LocalDensity.current
    val baseWidth = (205 * sizeScale).dp
    val sphereRadiusDp = (58 * sizeScale).dp
    val sphereRadiusPx = with(density) { sphereRadiusDp.toPx() }

    Box(
        modifier = modifier
            .absoluteOffset { IntOffset(widgetOffset.x.roundToInt(), widgetOffset.y.roundToInt()) }
            .width(baseWidth)
            .shadow(16.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp).copy(alpha = 0.97f))
            .border(1.5.dp, StudioAccent.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
            .testTag("floating_joystick_widget")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Drag handle, Title, Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            widgetOffset += dragAmount
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "جابه‌جایی گوی",
                    tint = StudioAccent,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = title,
                    fontSize = (11 * sizeScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3D Angle Readout Badges (Pitch: بالا/پایین, Yaw: چپ/راست, Roll: چرخش)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "عمودی: ${pitch.roundToInt()}°",
                        fontSize = (9 * sizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFF9100).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "افقی: ${yaw.roundToInt()}°",
                        fontSize = (9 * sizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9100),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StudioAccent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "چرخش: ${roll.roundToInt()}°",
                        fontSize = (9 * sizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioAccent,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Interactive 3D Sphere / Globe (کره سه‌بعدی تعاملی زمین)
            Box(
                modifier = Modifier
                    .size(sphereRadiusDp * 2)
                    .clip(CircleShape)
                    .background(Color(0xFF090D16))
                    .border(2.dp, Brush.sweepGradient(listOf(StudioAccent, Color(0xFF00E5FF), Color(0xFFFF9100), StudioAccent)), CircleShape)
                    .pointerInput(sphereRadiusPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touchPos = change.position
                            val distFromCenter = (touchPos - center).getDistance()

                            // If dragging near the outer perimeter, control 2D Roll/Rotation
                            if (distFromCenter > sphereRadiusPx * 0.78f) {
                                val angle = Math.toDegrees(atan2((touchPos.y - center.y).toDouble(), (touchPos.x - center.x).toDouble())).toFloat()
                                val normAngle = if (angle < 0f) angle + 360f else angle
                                roll = normAngle
                                onAngleChange(roll)
                                on3DChange?.invoke(roll, pitch, yaw)
                            } else {
                                // Dragging the surface of the globe: X controls Yaw, Y controls Pitch
                                yaw = (yaw + dragAmount.x * 0.9f).coerceIn(-180f, 180f)
                                pitch = (pitch - dragAmount.y * 0.9f).coerceIn(-89f, 89f)
                                on3DChange?.invoke(roll, pitch, yaw)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.width / 2f
                    val center = Offset(radius, radius)

                    // 1. Globe Deep Gradient Background
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617)),
                            center = center - Offset(radius * 0.25f, radius * 0.25f),
                            radius = radius * 1.2f
                        ),
                        radius = radius,
                        center = center
                    )

                    // 2. 3D Latitude Parallels (مدارهای افقی کره زمین که با زاویه Pitch خم می‌شوند)
                    val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
                    val cosPitch = cos(pitchRad)
                    val sinPitch = sin(pitchRad)

                    val latAngles = listOf(-60f, -30f, 0f, 30f, 60f)
                    for (lat in latAngles) {
                        val latRad = Math.toRadians(lat.toDouble()).toFloat()
                        val rLat = radius * cos(latRad)
                        val yOffset = radius * sin(latRad) * cosPitch - (radius * 0.3f * sinPitch)

                        val ellipseH = (rLat * sinPitch.absoluteValue).coerceAtLeast(1.5f)
                        val isEquator = lat == 0f
                        val lineColor = if (isEquator) Color(0xFF00E5FF).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.22f)
                        val lineWidth = if (isEquator) 2.2f else 1.0f

                        drawOval(
                            color = lineColor,
                            topLeft = Offset(center.x - rLat, center.y + yOffset - ellipseH / 2f),
                            size = Size(rLat * 2f, ellipseH),
                            style = Stroke(width = lineWidth)
                        )
                    }

                    // 3. 3D Longitude Meridians (نصف‌النهارهای عمودی که با زاویه Yaw می‌چرخند)
                    val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
                    val lonOffsets = listOf(0f, 45f, 90f, 135f)
                    for (lon in lonOffsets) {
                        val lonRad = Math.toRadians(lon.toDouble()).toFloat()
                        val currentLonYaw = yawRad + lonRad
                        val wLon = radius * cos(currentLonYaw)

                        drawOval(
                            color = Color(0xFFFF9100).copy(alpha = 0.28f),
                            topLeft = Offset(center.x - wLon.absoluteValue, center.y - radius),
                            size = Size(wLon.absoluteValue * 2f, radius * 2f),
                            style = Stroke(width = 1.0f)
                        )
                    }

                    // 4. Center Orientation Reticle & Direction Marker
                    val markerX = center.x + (radius * 0.65f) * sin(yawRad) * cosPitch
                    val markerY = center.y - (radius * 0.65f) * sinPitch

                    // Target line
                    drawLine(
                        color = StudioAccent.copy(alpha = 0.6f),
                        start = center,
                        end = Offset(markerX, markerY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )

                    // Glowing 3D Position Dot
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 6f,
                        center = Offset(markerX, markerY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5f,
                        center = Offset(markerX, markerY)
                    )

                    // 5. Specular atmosphere rim lighting
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(center.x - radius * 0.45f, center.y - radius * 0.45f),
                            radius = radius * 0.6f
                        ),
                        radius = radius * 0.5f,
                        center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Quick 3D Perspective Preset Views
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = {
                        pitch = 0f
                        yaw = 0f
                        roll = 0f
                        onAngleChange(0f)
                        on3DChange?.invoke(0f, 0f, 0f)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("دید روبرو", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                TextButton(
                    onClick = {
                        pitch = 35f
                        on3DChange?.invoke(roll, pitch, yaw)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("دید بالا", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextButton(
                    onClick = {
                        yaw = 45f
                        on3DChange?.invoke(roll, pitch, yaw)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("زاویه‌دار", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Footer controls: Size toggle & Full Reset Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        sizeScale = when {
                            sizeScale < 0.95f -> 1.0f
                            sizeScale < 1.15f -> 1.25f
                            else -> 0.85f
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.AspectRatio,
                        contentDescription = "تغییر اندازه گوی",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                FilledTonalButton(
                    onClick = {
                        pitch = 0f
                        yaw = 0f
                        roll = 0f
                        onAngleChange(0f)
                        on3DChange?.invoke(0f, 0f, 0f)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ریست کامل 3D", fontSize = 10.sp)
                }
            }
        }
    }
}
