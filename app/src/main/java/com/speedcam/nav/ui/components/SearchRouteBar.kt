package com.speedcam.nav.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedcam.nav.data.local.SavedLocationEntity
import com.speedcam.nav.data.model.NavigationRoute
import com.speedcam.nav.data.model.SearchLocation

@Composable
fun SearchRouteBar(
    originText: String,
    destinationText: String,
    onDestinationChange: (String) -> Unit,
    searchResults: List<SearchLocation>,
    onSelectDestination: (SearchLocation) -> Unit,
    savedLocations: List<SavedLocationEntity>,
    onOpenSavedLocations: () -> Unit,
    currentCity: String? = null,
    currentCountry: String? = null,
    isNearMeFilterEnabled: Boolean = true,
    onToggleNearMeFilter: () -> Unit = {},
    currentRoute: NavigationRoute?,
    isNavigating: Boolean,
    isLoadingRoute: Boolean,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, shape = RoundedCornerShape(26.dp))
            .testTag("search_route_bar"),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xEE111827) // Translucent deep modern slate
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(26.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Main Route Bar Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Navigation / Pin Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isNavigating) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isNavigating) Icons.Default.Navigation else Icons.Default.Directions,
                        contentDescription = "Directions",
                        tint = if (isNavigating) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Input Area
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Origin Pill (if expanded or navigating)
                    if (isExpanded || isNavigating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "From",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = originText,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Destination Input
                    TextField(
                        value = destinationText,
                        onValueChange = {
                            isExpanded = true
                            onDestinationChange(it)
                        },
                        placeholder = {
                            Text(
                                text = "Search destination (address, landmark)",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00E5FF),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("destination_input")
                    )
                }

                // Loading Indicator or Clear/Action Button
                if (isLoadingRoute) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF00E5FF)
                    )
                } else if (isNavigating || destinationText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            isExpanded = false
                            onClearRoute()
                        },
                        modifier = Modifier.testTag("clear_route_button")
                    ) {
                        Icon(
                            imageVector = if (isNavigating) Icons.Default.Stop else Icons.Default.Clear,
                            contentDescription = "Clear Route",
                            tint = if (isNavigating) Color(0xFFFF334B) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Quick Filter & Saved Locations Chip Row
            if (!isNavigating) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current City / Country Search Bias Chip
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isNearMeFilterEnabled) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF1E293B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggleNearMeFilter() }
                            .testTag("search_filter_near_me_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (isNearMeFilterEnabled) Icons.Default.NearMe else Icons.Default.Public,
                                contentDescription = null,
                                tint = if (isNearMeFilterEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isNearMeFilterEnabled) {
                                    val city = currentCity ?: "Near Me"
                                    val country = currentCountry?.let { " • $it" } ?: ""
                                    "$city$country"
                                } else {
                                    "Worldwide"
                                },
                                color = if (isNearMeFilterEnabled) Color.White else Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // "Saved Places" Chip button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenSavedLocations() }
                            .testTag("open_saved_locations_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Saved Places (${savedLocations.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // "Paste Link / Coords" Chip
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F766E).copy(alpha = 0.35f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    isExpanded = true
                                    onDestinationChange(clipText)
                                } else {
                                    Toast.makeText(context, "Clipboard empty. Copy a Google Maps link or coordinates to paste.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .testTag("paste_map_link_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Color(0xFF2DD4BF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Paste Link",
                                color = Color(0xFF2DD4BF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Show up to 3 quick saved locations
                    for (loc in savedLocations.take(3)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x33FFFFFF),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    isExpanded = false
                                    onSelectDestination(
                                        SearchLocation(
                                            title = loc.title,
                                            subtitle = loc.subtitle,
                                            latitude = loc.latitude,
                                            longitude = loc.longitude
                                        )
                                    )
                                }
                                .padding(end = 6.dp)
                                .testTag("quick_saved_chip_${loc.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = when (loc.category.uppercase()) {
                                        "HOME" -> Icons.Default.Home
                                        "WORK" -> Icons.Default.Work
                                        else -> Icons.Default.Place
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = loc.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Autocomplete Search Results dropdown
            AnimatedVisibility(
                visible = searchResults.isNotEmpty() && isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(max = 260.dp)
                ) {
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    LazyColumn {
                        items(searchResults) { loc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isExpanded = false
                                        onSelectDestination(loc)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .testTag("search_result_item")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loc.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    if (loc.subtitle.isNotBlank()) {
                                        Text(
                                            text = loc.subtitle,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (loc.distanceMeters != null) {
                                    val formattedDist = if (loc.distanceMeters < 1000) {
                                        "${loc.distanceMeters} m"
                                    } else {
                                        String.format(java.util.Locale.getDefault(), "%.1f km", loc.distanceMeters / 1000.0)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formattedDist,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
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
