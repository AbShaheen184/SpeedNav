package com.speedcam.nav.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigationHudControls(
    isFollowMode: Boolean,
    onToggleFollowMode: () -> Unit,
    isDarkMapTheme: Boolean,
    onToggleDarkMapTheme: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isSimulating: Boolean,
    onToggleSimulation: () -> Unit,
    onCycleSpeedLimit: () -> Unit,
    onOpenSavedLocations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val simBgColor by animateColorAsState(
        targetValue = if (isSimulating) Color(0xFF10B981) else Color(0xDD0F172A),
        label = "sim_color"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        // Saved Locations Button
        Surface(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .testTag("saved_locations_hud_button"),
            shape = CircleShape,
            color = Color(0xDD0F172A)
        ) {
            IconButton(
                onClick = onOpenSavedLocations,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Saved Locations",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Quick Speed Limit Override Tester Button (useful for road safety speed tests)
        Surface(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .testTag("test_speed_limit_button"),
            shape = CircleShape,
            color = Color(0xDD0F172A)
        ) {
            IconButton(
                onClick = onCycleSpeedLimit,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Cycle Speed Limit",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Dark / Light Map Tiles Toggle
        Surface(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .testTag("toggle_dark_mode_button"),
            shape = CircleShape,
            color = Color(0xDD0F172A)
        ) {
            IconButton(
                onClick = onToggleDarkMapTheme,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = if (isDarkMapTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Map Tile Theme",
                    tint = if (isDarkMapTheme) Color(0xFFFFD54F) else Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Mute / Unmute Sound Audio Alerts
        Surface(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .testTag("toggle_mute_button"),
            shape = CircleShape,
            color = Color(0xDD0F172A)
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Audio Alerts",
                    tint = if (isMuted) Color(0xFFEF4444) else Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Follow Vehicle Re-center
        Surface(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .testTag("toggle_follow_button"),
            shape = CircleShape,
            color = if (isFollowMode) Color(0xFF00E5FF) else Color(0xDD0F172A)
        ) {
            IconButton(
                onClick = onToggleFollowMode,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFollowMode) Icons.Default.Navigation else Icons.Default.MyLocation,
                    contentDescription = "Re-center / Follow",
                    tint = if (isFollowMode) Color(0xFF0F172A) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Simulation Drive Button
        Surface(
            onClick = onToggleSimulation,
            shape = RoundedCornerShape(24.dp),
            color = simBgColor,
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(24.dp))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(24.dp))
                .testTag("toggle_simulation_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "Simulate Drive",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSimulating) "STOP SIM" else "TEST DRIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
