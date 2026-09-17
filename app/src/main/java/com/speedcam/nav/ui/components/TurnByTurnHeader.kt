package com.speedcam.nav.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedcam.nav.data.model.ManeuverType
import com.speedcam.nav.data.model.NavigationStep

@Composable
fun TurnByTurnHeader(
    isNavigating: Boolean,
    currentStep: NavigationStep?,
    nextStep: NavigationStep?,
    distanceToManeuverMeters: Double?,
    isApproachingTurnOrExit: Boolean,
    onExitNavigation: () -> Unit,
    onRecalculateRoute: () -> Unit = {},
    isUpdatingRoute: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isNavigating,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val step = currentStep ?: NavigationStep(
            maneuverType = ManeuverType.STRAIGHT,
            instruction = "Proceed on current road",
            streetName = "Route Ahead",
            distanceMeters = 500.0,
            latitude = 0.0,
            longitude = 0.0
        )

        val navGreen = Color(0xFF0F5132)
        val alertGreen = Color(0xFF198754)
        val containerColor = if (isApproachingTurnOrExit) alertGreen else navGreen

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("turn_by_turn_header"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Turn / Maneuver Icon inside circular badge
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getManeuverIcon(step.maneuverType),
                                contentDescription = step.instruction,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Distance & Maneuver Instruction
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Formatted Distance
                        val distFormatted = formatDistance(distanceToManeuverMeters ?: step.distanceMeters)
                        Text(
                            text = distFormatted,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )

                        Text(
                            text = step.instruction,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Update / Recalculate Route from Current Location Button
                    IconButton(
                        onClick = onRecalculateRoute,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .testTag("recalculate_route_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Update Route Directions",
                            tint = if (isUpdatingRoute) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Exit Navigation Button
                    IconButton(
                        onClick = onExitNavigation,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .testTag("exit_navigation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Navigation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Approaching Turn / Exit Highlight Banner
                if (isApproachingTurnOrExit) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (step.maneuverType == ManeuverType.EXIT) "TAKE EXIT SOON" else "PREPARE TO TURN",
                                    color = Color(0xFFFFD700),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Map Zoomed In",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (nextStep != null) {
                    // Next step preview
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "Then: ",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Icon(
                            imageVector = getManeuverIcon(nextStep.maneuverType),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = nextStep.instruction,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String {
    return when {
        meters < 25 -> "Now"
        meters < 1000 -> "${(meters / 10).toInt() * 10} m"
        else -> String.format("%.1f km", meters / 1000.0)
    }
}

private fun getManeuverIcon(type: ManeuverType): ImageVector {
    return when (type) {
        ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        ManeuverType.SLIGHT_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        ManeuverType.SLIGHT_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        ManeuverType.EXIT -> Icons.AutoMirrored.Filled.ExitToApp
        ManeuverType.ROUNDABOUT -> Icons.Default.Refresh
        ManeuverType.U_TURN -> Icons.Default.Refresh
        ManeuverType.ARRIVE -> Icons.Default.Flag
        ManeuverType.DEPART -> Icons.Default.Navigation
        ManeuverType.STRAIGHT -> Icons.Default.Straight
    }
}
