package com.speedcam.nav.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedcam.nav.data.model.DroppedPinLocation
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPointOptionsSheet(
    isOpen: Boolean,
    droppedPin: DroppedPinLocation?,
    onDismiss: () -> Unit,
    onDirections: () -> Unit,
    onSaveLocation: (name: String, category: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen || droppedPin == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSaveDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.testTag("map_point_options_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Drag pill indicator
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x44FFFFFF))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header: Pin icon + Place title & Subtitle + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pin Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                        .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Dropped Pin",
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = droppedPin.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (droppedPin.isResolvingAddress) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val distStr = droppedPin.distanceMeters?.let { meters ->
                        if (meters < 1000) "$meters m away"
                        else String.format(Locale.getDefault(), "%.1f km away", meters / 1000.0)
                    }

                    val sub = if (distStr != null && droppedPin.subtitle.isNotBlank()) {
                        "${droppedPin.subtitle} • $distStr"
                    } else if (distStr != null) {
                        distStr
                    } else {
                        droppedPin.subtitle
                    }

                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .testTag("map_point_sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Coordinates Badge (Clickable to copy)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        copyCoordinatesToClipboard(context, droppedPin.latitude, droppedPin.longitude)
                    }
                    .testTag("map_point_coordinates_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "COORDINATES",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.6f", droppedPin.latitude)}, ${String.format(Locale.US, "%.6f", droppedPin.longitude)}",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tap to copy",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Coordinates",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0x22FFFFFF))
            Spacer(modifier = Modifier.height(16.dp))

            // 4 Action Buttons in a Grid (Directions, Save, Copy Lat/Lon, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Directions Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0284C7),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDirections() }
                        .testTag("map_point_directions_button")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Directions",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Directions",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Save Location Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showSaveDialog = true }
                        .testTag("map_point_save_button")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Save Location",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Copy Longitude & Latitude Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            copyCoordinatesToClipboard(context, droppedPin.latitude, droppedPin.longitude)
                        }
                        .testTag("map_point_copy_coords_button")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Coordinates",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Copy Coords",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 4. Share Location Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            shareLocation(context, droppedPin)
                        }
                        .testTag("map_point_share_button")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Location",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Share",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // Save Location Dialog
    if (showSaveDialog) {
        var placeName by remember { mutableStateOf(droppedPin.title) }
        var selectedCategory by remember { mutableStateOf("FAVORITE") }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "Save Location",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this pinned location:",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_pinned_location_name_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Category:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("FAVORITE", "Favorite", Icons.Default.Bookmark),
                            Triple("HOME", "Home", Icons.Default.Home),
                            Triple("WORK", "Work", Icons.Default.Work)
                        ).forEach { (catKey, catLabel, icon) ->
                            val isSel = selectedCategory == catKey
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) Color(0xFF0284C7) else Color(0xFF334155),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedCategory = catKey }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSel) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = catLabel,
                                        color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = placeName.ifBlank { droppedPin.title }
                        onSaveLocation(finalName, selectedCategory)
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

private fun copyCoordinatesToClipboard(context: Context, lat: Double, lon: Double) {
    val formatted = "${String.format(Locale.US, "%.6f", lat)}, ${String.format(Locale.US, "%.6f", lon)}"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Coordinates", formatted)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Coordinates copied: $formatted", Toast.LENGTH_SHORT).show()
}

private fun shareLocation(context: Context, pin: DroppedPinLocation) {
    val formattedCoords = "${String.format(Locale.US, "%.6f", pin.latitude)}, ${String.format(Locale.US, "%.6f", pin.longitude)}"
    val mapsUrl = "https://maps.google.com/?q=${pin.latitude},${pin.longitude}"

    val shareText = buildString {
        append("Location: ").append(pin.title)
        if (pin.subtitle.isNotBlank() && pin.subtitle != pin.title) {
            append("\nAddress: ").append(pin.subtitle)
        }
        append("\nCoordinates: ").append(formattedCoords)
        append("\nMap Link: ").append(mapsUrl)
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, pin.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Location via"))
}
