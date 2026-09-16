package com.speedcam.nav.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedcam.nav.data.model.CameraType
import com.speedcam.nav.data.model.SpeedCameraNode

@Composable
fun CameraAlertCard(
    camera: SpeedCameraNode?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = camera != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (camera == null) return@AnimatedVisibility

        val (icon, badgeColor, title) = when (camera.cameraType) {
            CameraType.SPEED -> Triple(
                Icons.Default.Speed,
                Color(0xFFFF334B),
                "Speed Camera Ahead"
            )
            CameraType.RED_LIGHT -> Triple(
                Icons.Default.Traffic,
                Color(0xFFFF4D4D),
                "Red Light Camera"
            )
            CameraType.SEATBELT_PHONE -> Triple(
                Icons.Default.PhoneAndroid,
                Color(0xFFFF9500),
                "Seatbelt & Phone Radar"
            )
            CameraType.AVERAGE_SPEED -> Triple(
                Icons.Default.Speed,
                Color(0xFFFFB300),
                "Average Speed Zone"
            )
            CameraType.POLICE_MOBILE -> Triple(
                Icons.Default.Warning,
                Color(0xFFFF334B),
                "Mobile Radar Patrol"
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(22.dp))
                .testTag("camera_alert_card"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF20F172A)
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = 1.5.dp,
                        color = badgeColor.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                badgeColor.copy(alpha = 0.22f),
                                Color(0x000F172A)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left Icon Badge
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.2f))
                            .border(1.dp, badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = badgeColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Center Details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (camera.speedLimit != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(2.dp, Color.Red, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${camera.speedLimit}",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Text(
                            text = camera.roadName ?: camera.cameraType.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Distance Pill Countdown
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${camera.distanceMeters} m",
                            color = badgeColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "AHEAD",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dismiss_camera_alert")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
