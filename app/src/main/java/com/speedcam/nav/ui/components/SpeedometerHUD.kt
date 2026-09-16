package com.speedcam.nav.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedometerHUD(
    currentSpeedKmh: Int,
    speedLimitKmh: Int?,
    isOverspeed: Boolean,
    overspeedDelta: Int,
    modifier: Modifier = Modifier,
    onSpeedLimitClick: () -> Unit = {}
) {
    // Pulse animation when exceeding speed limit
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOverspeed) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = if (isOverspeed) Color(0xFFFF2A4D) else Color(0x33FFFFFF),
        animationSpec = tween(300),
        label = "border_color"
    )

    Surface(
        modifier = modifier
            .testTag("speedometer_hud")
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xEE0D1B2A) // Sleek cockpit deep obsidian/navy
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = if (isOverspeed) 2.dp else 1.dp,
                    color = cardBorderColor,
                    shape = RoundedCornerShape(26.dp)
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isOverspeed) Color(0x33FF2A4D) else Color(0x221E293B),
                            Color(0xEE0B132B)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Digital Speedometer Display
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text(
                        text = "SPEED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.2.sp
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$currentSpeedKmh",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = if (isOverspeed) Color(0xFFFF334B) else Color(0xFF00E5FF),
                            modifier = Modifier.testTag("current_speed_value")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km/h",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (isOverspeed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x44FF2A4D))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Overspeed Warning",
                                tint = Color(0xFFFF2A4D),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+$overspeedDelta km/h",
                                color = Color(0xFFFF4D6D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .width(1.dp)
                        .background(Color(0x33FFFFFF))
                )

                Spacer(modifier = Modifier.width(16.dp))

                // European Standard Style Speed Limit Sign
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .scale(pulseScale)
                        .clickable { onSpeedLimitClick() }
                        .testTag("speed_limit_badge")
                ) {
                    Text(
                        text = "LIMIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(width = 5.dp, color = Color(0xFFE50000), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = speedLimitKmh?.toString() ?: "--",
                            fontSize = if ((speedLimitKmh ?: 0) >= 100) 18.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }
    }
}
