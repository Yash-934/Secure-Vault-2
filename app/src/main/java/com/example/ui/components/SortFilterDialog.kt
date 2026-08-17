package com.example.ui.components

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultFilterTab

enum class VaultSortOption(val displayName: String) {
    DATE_DESC("Date (Newest First)"),
    DATE_ASC("Date (Oldest First)"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest First)"),
    SIZE_ASC("Size (Smallest First)"),
    TYPE("File Type / Extension")
}

private val DarkCapsuleBg = Color(0xFF0C1420)
private val CapsuleBorder = Color(0xFF1B3148)
private val BrightCyan = Color(0xFF00F5D4)
private val MutedText = Color(0xFF94A3B8)

@Composable
fun SortFilterDialog(
    currentSort: VaultSortOption,
    currentFilter: VaultFilterTab,
    onSortSelected: (VaultSortOption) -> Unit,
    onFilterSelected: (VaultFilterTab) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCapsuleBg,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.testTag("sort_filter_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrightCyan.copy(alpha = 0.15f))
                            .border(1.dp, BrightCyan.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            tint = BrightCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SORT & FILTER",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Organize Encrypted Vault",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Sort By
                Text(
                    text = "SORT BY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrightCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaultSortOption.values().forEach { option ->
                        val isSelected = currentSort == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrightCyan.copy(alpha = 0.15f) else Color(0xFF050E17))
                                .border(
                                    1.dp,
                                    if (isSelected) BrightCyan else CapsuleBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSortSelected(option) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BrightCyan else Color.White
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = CapsuleBorder, thickness = 0.8.dp)

                // Section 2: Filter By Category
                Text(
                    text = "FILTER BY TYPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrightCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterTypeButton(
                        text = "ALL",
                        isSelected = currentFilter == VaultFilterTab.ALL,
                        onClick = { onFilterSelected(VaultFilterTab.ALL) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterTypeButton(
                        text = "PHOTOS",
                        isSelected = currentFilter == VaultFilterTab.PHOTOS,
                        onClick = { onFilterSelected(VaultFilterTab.PHOTOS) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterTypeButton(
                        text = "VIDEOS",
                        isSelected = currentFilter == VaultFilterTab.VIDEOS,
                        onClick = { onFilterSelected(VaultFilterTab.VIDEOS) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterTypeButton(
                        text = "DOCS",
                        isSelected = currentFilter == VaultFilterTab.DOCUMENTS,
                        onClick = { onFilterSelected(VaultFilterTab.DOCUMENTS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrightCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("APPLY SORT & FILTER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun FilterTypeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) BrightCyan else Color(0xFF050E17))
            .border(
                1.dp,
                if (isSelected) BrightCyan else CapsuleBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}
