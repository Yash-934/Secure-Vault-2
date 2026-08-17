package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkCapsuleBg = Color(0xFF0C1420)
private val CapsuleBorder = Color(0xFF1B3148)
private val BrightCyan = Color(0xFF00F5D4)
private val MutedText = Color(0xFF94A3B8)

@Composable
fun RecentFilesSection(
    items: List<VaultItem>,
    isThumbnailsEnabled: Boolean,
    onItemClick: (VaultItem) -> Unit,
    onItemLongClick: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentItems = remember(items) {
        items.sortedByDescending { it.addedTimestamp }.take(8)
    }

    if (recentItems.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recent_files_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RECENT FILES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrightCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = "${recentItems.size} LATEST",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MutedText,
                fontFamily = FontFamily.Monospace
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(recentItems, key = { "recent_${it.id}" }) { item ->
                RecentItemCard(
                    item = item,
                    showThumbnails = isThumbnailsEnabled,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
    }
}

@Composable
private fun RecentItemCard(
    item: VaultItem,
    showThumbnails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm • dd MMM", Locale.getDefault()) }
    val formattedTime = remember(item.addedTimestamp) { sdf.format(Date(item.addedTimestamp)) }

    Card(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("recent_vault_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkCapsuleBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, CapsuleBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(75.dp)
                    .background(Color(0xFF040C14)),
                contentAlignment = Alignment.Center
            ) {
                VaultItemThumbnail(
                    item = item,
                    showThumbnails = showThumbnails,
                    modifier = Modifier.fillMaxSize()
                )

                // Encrypted Lock badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC040E18)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BrightCyan,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.originalName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedTime,
                    fontSize = 9.sp,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
